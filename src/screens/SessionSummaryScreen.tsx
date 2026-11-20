import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pencil, Sparkles, Trash2 } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { AiService } from '../ai/AiService';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { SessionStatus } from '../types/models';
import { formatMinutes, formatWhen, statusLabel } from '../domain/Format';
import {
  AppCard,
  ChipButton,
  ConfirmDialog,
  SectionLabel,
  StatTile,
  SubScreenHeader,
} from '../components/ui/Common';

export const SessionSummaryScreen: React.FC<{
  sessionId: number;
  onBack: () => void;
  onStartNext: () => void;
}> = ({ sessionId, onBack, onStartNext }) => {
  const { db, now, getReadyProvider, saveSessionSummary, updateSession, deleteSession } =
    useFocusFlow();
  const [working, setWorking] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [editSubject, setEditSubject] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editMinutes, setEditMinutes] = useState('');
  const [editStatus, setEditStatus] = useState<SessionStatus>('COMPLETED');
  const autoTriedSessionRef = useRef<number | null>(null);

  const session = useMemo(
    () => db.sessions.find((s) => s.id === sessionId) ?? null,
    [db.sessions, sessionId],
  );

  const sessionTasks = useMemo(() => {
    const links = db.sessionTasks
      .filter((st) => st.sessionId === sessionId)
      .sort((a, b) => a.position - b.position);
    return links
      .map((link) => {
        const task = db.tasks.find((t) => t.id === link.taskId);
        return task ? { link, title: task.title } : null;
      })
      .filter((x): x is { link: (typeof links)[number]; title: string } => x !== null);
  }, [db.sessionTasks, db.tasks, sessionId]);

  const chapterNames = useMemo(() => {
    const ids = new Set(
      db.sessionChapters.filter((sc) => sc.sessionId === sessionId).map((sc) => sc.chapterId),
    );
    return db.chapters.filter((c) => ids.has(c.id)).map((c) => c.name);
  }, [db.chapters, db.sessionChapters, sessionId]);

  const savedSummary = useMemo(
    () => db.summaries.find((sum) => sum.sessionId === sessionId) ?? null,
    [db.summaries, sessionId],
  );

  const generate = useCallback(async () => {
    if (working || !session) return;
    setWorking(true);
    setNote(null);
    try {
      const end = session.endedAt ?? Date.now();
      const clock = sessionToClock(session);
      const r = await AiService.summarize(getReadyProvider(), {
        subject: session.subjectName,
        chapters: chapterNames,
        planned: session.description,
        status: session.status,
        focusMinutes: Math.floor(SessionClock.focusElapsedMs(clock, end) / 60_000),
        breakMinutes: Math.floor(SessionClock.breakElapsedMs(clock, end) / 60_000),
        completedTasks: sessionTasks.filter((t) => t.link.completed).map((t) => t.title),
        unfinishedTasks: sessionTasks.filter((t) => !t.link.completed).map((t) => t.title),
      });
      if (r.text.trim()) {
        saveSessionSummary(sessionId, r.text, r.source);
      }
      setNote(r.note);
    } finally {
      setWorking(false);
    }
  }, [chapterNames, getReadyProvider, saveSessionSummary, session, sessionId, sessionTasks, working]);

  // Written once when a finished session without a summary is first viewed.
  useEffect(() => {
    if (!session) return;
    if (session.status === 'ACTIVE') return;
    if (savedSummary != null) return;
    if (autoTriedSessionRef.current === sessionId) return;
    autoTriedSessionRef.current = sessionId;
    void generate();
  }, [generate, savedSummary, session, sessionId]);

  if (!session) {
    return (
      <div className="flex flex-col gap-4 pb-10">
        <SubScreenHeader title="Session summary" onBack={onBack} />
        <AppCard>
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            This session could not be found.
          </p>
        </AppCard>
      </div>
    );
  }

  const end = session.endedAt ?? now;
  const clock = sessionToClock(session);
  const focusMs = SessionClock.focusElapsedMs(clock, end);
  const breakMs = SessionClock.breakElapsedMs(clock, end);
  const doneCount = sessionTasks.filter((t) => t.link.completed).length;
  const unfinishedCount = sessionTasks.filter((t) => !t.link.completed).length;

  const summaryLabel =
    savedSummary?.source === 'EXTERNAL_AI'
      ? 'WRITTEN BY API AI'
      : savedSummary?.source === 'ON_DEVICE_AI'
        ? 'LEGACY ON-DEVICE AI SUMMARY'
        : savedSummary?.source === 'TEMPLATE'
          ? 'LEGACY PLAIN SUMMARY'
          : 'SUMMARY';

  const openEditModal = () => {
    setEditSubject(session.subjectName);
    setEditDesc(session.description);
    setEditMinutes(
      session.plannedDurationMs ? String(Math.round(session.plannedDurationMs / 60_000)) : '',
    );
    setEditStatus(session.status);
    setEditing(true);
  };

  return (
    <div className="flex flex-col gap-4 pb-10">
      <SubScreenHeader
        title="Session summary"
        onBack={onBack}
        right={
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={openEditModal}
              aria-label="Edit session"
              className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--ff-outline)]/45 px-3 py-2 text-xs font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              <Pencil className="h-3.5 w-3.5" />
              Edit
            </button>
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              aria-label="Delete session"
              className="p-2 rounded-xl border border-[var(--ff-outline)]/45 text-[var(--ff-error)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        }
      />

      <AppCard>
        <h2 className="text-xl font-semibold text-[var(--ff-on-bg)]">
          {session.subjectName}
          {chapterNames.length > 0 ? ` · ${chapterNames.join(', ')}` : ''}
        </h2>
        <div className="text-sm text-[var(--ff-on-surface-variant)]">
          {formatWhen(session.startedAt)} · {statusLabel(session.status)}
        </div>
        <div className="grid grid-cols-3 gap-4 pt-2">
          <StatTile label="focus" value={formatMinutes(focusMs)} />
          <StatTile label="breaks" value={formatMinutes(breakMs)} />
          <StatTile label="tasks done" value={`${doneCount}/${sessionTasks.length}`} />
        </div>
        {session.mode === 'TIMED' && session.plannedDurationMs != null && (
          <div className="text-xs text-[var(--ff-on-surface-variant)]">
            Planned {formatMinutes(session.plannedDurationMs)}
          </div>
        )}
      </AppCard>

      <AppCard>
        <SectionLabel>{summaryLabel}</SectionLabel>
        <p className="text-sm md:text-base text-[var(--ff-on-bg)] leading-relaxed whitespace-pre-wrap">
          {savedSummary?.text ?? (working ? 'Writing summary…' : 'No summary yet.')}
        </p>
        {note && <p className="text-xs text-[var(--ff-on-surface-variant)]">{note}</p>}
        {session.status !== 'ACTIVE' && (
          <div>
            <button
              type="button"
              disabled={working}
              onClick={() => void generate()}
              className="inline-flex items-center gap-2 rounded-xl border border-[var(--ff-outline)]/45 px-4 py-2 text-sm font-medium text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-45 cursor-pointer"
            >
              <Sparkles className="h-4 w-4 text-[var(--ff-primary)]" />
              {savedSummary ? 'Regenerate' : 'Generate'}
            </button>
          </div>
        )}
      </AppCard>

      {sessionTasks.length > 0 && (
        <AppCard>
          <SectionLabel>TASKS</SectionLabel>
          <div className="flex flex-col gap-1.5 text-sm text-[var(--ff-on-bg)]">
            {sessionTasks.map((t) => (
              <div key={t.link.id} className="flex items-start gap-2">
                <span className="font-mono-tabular">
                  {t.link.completed ? '✓' : '○'}
                </span>
                <span className={t.link.completed ? 'text-[var(--ff-on-surface-variant)]' : ''}>
                  {t.title}
                </span>
              </div>
            ))}
          </div>
        </AppCard>
      )}

      {unfinishedCount > 0 && (
        <AppCard>
          <SectionLabel>UNFINISHED</SectionLabel>
          <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
            {unfinishedCount} task(s) are still open on your Tasks list, with their subject and
            chapter. Add them to a new session to continue; they won&apos;t be duplicated.
          </p>
          <div>
            <button
              type="button"
              onClick={onStartNext}
              className="rounded-xl bg-[var(--ff-primary)] px-4 py-2 text-sm font-semibold text-[var(--ff-on-primary)] hover:opacity-90 cursor-pointer"
            >
              Plan next session
            </button>
          </div>
        </AppCard>
      )}

      {editing && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
          onClick={() => setEditing(false)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 flex flex-col gap-3.5 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">Edit session</h3>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-[var(--ff-on-surface-variant)]">Subject label</span>
              <input
                type="text"
                value={editSubject}
                onChange={(e) => setEditSubject(e.target.value)}
                className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-xs text-[var(--ff-on-surface-variant)]">Notes / plan</span>
              <textarea
                rows={3}
                value={editDesc}
                onChange={(e) => setEditDesc(e.target.value)}
                className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] p-3 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
              />
            </label>
            {session.mode === 'TIMED' && (
              <label className="flex flex-col gap-1">
                <span className="text-xs text-[var(--ff-on-surface-variant)]">
                  Planned duration (minutes)
                </span>
                <input
                  type="number"
                  min={1}
                  max={600}
                  value={editMinutes}
                  onChange={(e) => setEditMinutes(e.target.value.replace(/\D/g, '').slice(0, 3))}
                  className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2 text-sm font-mono-tabular text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
                />
              </label>
            )}
            <div className="flex flex-col gap-1.5">
              <span className="text-xs text-[var(--ff-on-surface-variant)]">Status</span>
              <div className="flex flex-wrap gap-2">
                {(['COMPLETED', 'ENDED_EARLY', 'EMERGENCY_EXIT'] as SessionStatus[]).map((st) => (
                  <ChipButton
                    key={st}
                    selected={editStatus === st}
                    onClick={() => setEditStatus(st)}
                    className="px-3 py-1.5 text-xs"
                  >
                    {statusLabel(st)}
                  </ChipButton>
                ))}
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="px-4 py-2 rounded-xl text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!editSubject.trim()}
                onClick={() => {
                  const mins = Number(editMinutes);
                  updateSession(sessionId, {
                    subjectName: editSubject,
                    description: editDesc,
                    status: editStatus,
                    ...(session.mode === 'TIMED' && Number.isFinite(mins) && mins >= 1
                      ? { plannedDurationMs: mins * 60_000 }
                      : {}),
                  });
                  setEditing(false);
                }}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] disabled:opacity-45 cursor-pointer"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Delete this session?"
        message="This permanently removes this session and its summary from history."
        confirmLabel="Delete"
        destructive
        onDismiss={() => setConfirmDelete(false)}
        onConfirm={() => {
          deleteSession(sessionId);
          setConfirmDelete(false);
          onBack();
        }}
      />
    </div>
  );
};
