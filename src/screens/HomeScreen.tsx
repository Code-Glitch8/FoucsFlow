import React, { useEffect, useRef, useState } from 'react';
import { Mic, Pencil, Play, Volume2 } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { StudyStats, SessionFact, startOfLocalDay } from '../domain/StudyStats';
import { ProgressStats } from '../domain/ProgressStats';
import { PlanAnalyzer } from '../domain/PlanAnalyzer';
import { WidgetDraftBuilder } from '../domain/WidgetDraftBuilder';
import { WebVoiceRecognizer } from '../speech/VoiceRecognizer';
import { formatMinutes } from '../domain/Format';
import {
  AppCard,
  BarChart,
  ChipButton,
  ProgressBar,
  ScreenTitle,
  SectionLabel,
  SessionRowCard,
  StatTile,
} from '../components/ui/Common';
import { WidgetStyle, WidgetTile } from '../components/widgets/WidgetStyle';
import { StudyQuoteCard } from '../components/StudyQuoteCard';
import {
  DashboardWidget,
  DraftTask,
  ProgressRange,
  SessionDraft,
  WidgetAction,
} from '../types/models';

export const HomeScreen: React.FC<{
  onStartSession: () => void;
  onOpenFocus: () => void;
  onOpenSession: (sessionId: number) => void;
  onOpenHistory: () => void;
  onEditWidgets: () => void;
  onOpenTasks: () => void;
  onOpenStatistics: () => void;
}> = ({
  onStartSession,
  onOpenFocus,
  onOpenSession,
  onOpenHistory,
  onEditWidgets,
  onOpenTasks,
  onOpenStatistics,
}) => {
  const {
    db,
    settings,
    activeSession,
    now,
    runWidget,
    startSession,
    updateWidgetProgressRange,
  } = useFocusFlow();
  const [bannerMsg, setBannerMsg] = useState<{ text: string; offerOpenSession?: boolean } | null>(
    null,
  );
  const [voiceModalWidget, setVoiceModalWidget] = useState<DashboardWidget | null | 'QUICK'>(null);

  const finishedSessions = db.sessions.filter((s) => s.status !== 'ACTIVE');
  const facts: SessionFact[] = finishedSessions.map((s) => ({
    id: s.id,
    subjectId: s.subjectId,
    startedAt: s.startedAt,
    focusMs: SessionClock.focusElapsedMs(sessionToClock(s), s.endedAt ?? now),
    chapterIds: db.sessionChapters.filter((sc) => sc.sessionId === s.id).map((sc) => sc.chapterId),
  }));

  const today = new Date(now);
  const startTodayMs = startOfLocalDay(today).getTime();
  const activeFocusMs = activeSession
    ? SessionClock.focusElapsedMs(sessionToClock(activeSession.session), now)
    : 0;
  const factsWithActive: SessionFact[] = activeSession
    ? [
        ...facts,
        {
          id: activeSession.session.id,
          subjectId: activeSession.session.subjectId,
          startedAt: activeSession.session.startedAt,
          focusMs: activeFocusMs,
          chapterIds: activeSession.chapterIds,
        },
      ]
    : facts;

  const todayFocus = StudyStats.focusOnDay(facts, today) + activeFocusMs;
  const doneToday = new Set(
    db.sessionTasks
      .filter((st) => st.completed && (st.completedAt ?? 0) >= startTodayMs)
      .map((st) => st.taskId),
  ).size;
  const streak = StudyStats.streak(facts, today);

  const visibleWidgets = [...db.widgets]
    .filter((w) => w.visible)
    .sort((a, b) => a.position - b.position);
  const widgetRows = WidgetStyle.rows(visibleWidgets);

  const weekStartMs = startOfLocalDay(new Date(now - 6 * 86_400_000)).getTime();
  const recentFacts = facts.filter((f) => f.startedAt >= weekStartMs);
  const completedByChapter = new Map<number, number>();
  const openByChapter = new Map<number, number>();
  for (const t of db.tasks) {
    if (t.chapterId == null) continue;
    if (t.status === 'DONE') {
      completedByChapter.set(t.chapterId, (completedByChapter.get(t.chapterId) ?? 0) + 1);
    } else if (t.status === 'OPEN') {
      openByChapter.set(t.chapterId, (openByChapter.get(t.chapterId) ?? 0) + 1);
    }
  }
  const chapterStats = StudyStats.chapterStats(
    db.chapters.map((c) => c.id),
    recentFacts,
    completedByChapter,
    openByChapter,
  )
    .filter((st) => st.focusMs > 0)
    .sort((a, b) => b.focusMs - a.focusMs)
    .slice(0, 4);

  const handleWidgetTap = (w: DashboardWidget) => {
    if (w.type === 'PROGRESS') {
      onOpenStatistics();
      return;
    }
    if (w.voiceAutoStart) {
      if (activeSession !== null) {
        setBannerMsg({ text: 'A session is already running.', offerOpenSession: true });
        return;
      }
      setVoiceModalWidget(w);
      return;
    }

    const outcome = runWidget(w.id);
    switch (outcome.kind) {
      case 'started':
        if (outcome.launchMinimized) {
          setBannerMsg({
            text: `Focus started in floating circle${outcome.snapGptTriggered ? ' · SnapGPT triggered' : ''}.`,
            offerOpenSession: true,
          });
        } else {
          if (outcome.notes.length > 0) {
            setBannerMsg({ text: outcome.notes[0] });
          }
          onOpenFocus();
        }
        break;
      case 'already_active':
        setBannerMsg({ text: 'A session is already running.', offerOpenSession: true });
        break;
      case 'invalid':
        setBannerMsg({ text: outcome.reason });
        break;
      case 'navigate':
        handleNavigate(outcome.action);
        break;
      case 'progress':
        onOpenStatistics();
        break;
    }
  };

  const handleNavigate = (action: WidgetAction) => {
    switch (action) {
      case 'OPEN_PREPARE':
        onStartSession();
        break;
      case 'OPEN_TASKS':
        onOpenTasks();
        break;
      case 'OPEN_HISTORY':
        onOpenHistory();
        break;
      case 'OPEN_STATISTICS':
        onOpenStatistics();
        break;
      case 'START_SESSION':
        break;
    }
  };

  const resolveWidgetSubjectNames = (w: DashboardWidget): string[] => {
    if (w.subjectPlans && w.subjectPlans.length > 0) {
      return w.subjectPlans
        .map((sp) => db.subjects.find((s) => s.id === sp.subjectId)?.name)
        .filter((n): n is string => Boolean(n));
    }
    if (w.subjectId != null) {
      const n = db.subjects.find((s) => s.id === w.subjectId)?.name;
      return n ? [n] : [];
    }
    return [];
  };

  return (
    <div className="flex flex-col gap-4 pb-8">
      <div className="flex items-center justify-between gap-4">
        <ScreenTitle>Home</ScreenTitle>
        <button
          type="button"
          onClick={onEditWidgets}
          className="px-3.5 py-2 rounded-xl text-sm font-medium text-[var(--ff-primary)] hover:bg-[var(--ff-surface-variant)] transition-colors whitespace-nowrap shrink-0 cursor-pointer"
        >
          Edit dashboard
        </button>
      </div>

      {bannerMsg && (
        <div className="rounded-xl bg-[var(--ff-surface-variant)] border border-[var(--ff-outline)]/40 px-4 py-3 flex items-center justify-between gap-3 text-sm">
          <span className="text-[var(--ff-on-bg)]">{bannerMsg.text}</span>
          <div className="flex items-center gap-2 shrink-0">
            {bannerMsg.offerOpenSession && (
              <button
                type="button"
                onClick={() => {
                  setBannerMsg(null);
                  onOpenFocus();
                }}
                className="text-xs font-semibold text-[var(--ff-primary)] hover:underline cursor-pointer"
              >
                Open session
              </button>
            )}
            <button
              type="button"
              onClick={() => setBannerMsg(null)}
              className="text-xs text-[var(--ff-on-surface-variant)] hover:text-[var(--ff-on-bg)] cursor-pointer"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      <StudyQuoteCard />

      {activeSession && (
        <AppCard onClick={onOpenFocus} className="border-[var(--ff-primary)]/50">
          <SectionLabel>SESSION IN PROGRESS</SectionLabel>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <div className="text-xl font-semibold text-[var(--ff-on-bg)]">
                {activeSession.session.subjectName}
              </div>
              <div className="text-sm text-[var(--ff-on-surface-variant)] tabular-nums">
                {formatMinutes(activeFocusMs)} focused ·{' '}
                {activeSession.tasks.filter((t) => t.link.completed).length}/
                {activeSession.tasks.length} tasks
              </div>
            </div>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onOpenFocus();
              }}
              className="px-4 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/50 text-[var(--ff-primary)] hover:bg-[var(--ff-surface-variant)] whitespace-nowrap shrink-0 cursor-pointer"
            >
              Open session
            </button>
          </div>
        </AppCard>
      )}

      <AppCard>
        <SectionLabel>TODAY</SectionLabel>
        <div className="grid grid-cols-3 gap-4 pt-1">
          <StatTile label="focused" value={formatMinutes(todayFocus)} />
          <StatTile label="tasks done" value={`${doneToday}`} />
          <StatTile label="day streak" value={`${streak}`} />
        </div>
      </AppCard>

      <div className="flex flex-col gap-3 pt-1">
        <SectionLabel>WIDGETS</SectionLabel>
        {visibleWidgets.length === 0 ? (
          <AppCard>
            <div className="text-base font-medium text-[var(--ff-on-bg)]">
              Your dashboard has no widgets.
            </div>
            <p className="text-sm text-[var(--ff-on-surface-variant)]">
              Add timer, multi-subject, voice-start, or progress chart widgets to your Home screen.
            </p>
            <div>
              <button
                type="button"
                onClick={onEditWidgets}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
              >
                Add widgets
              </button>
            </div>
          </AppCard>
        ) : (
          <div className="flex flex-col gap-3">
            {widgetRows.map((row) => (
              <div
                key={row.map((w) => w.id).join('-')}
                className="grid grid-cols-1 sm:grid-cols-2 gap-3"
              >
                {row.map((w) => {
                  if (w.type === 'PROGRESS') {
                    const range: ProgressRange = w.progressRange ?? 'DAYS';
                    const summary = ProgressStats.compute(factsWithActive, now, range);
                    return (
                      <div key={w.id} className={w.size === 'LARGE' ? 'sm:col-span-2' : ''}>
                        <AppCard>
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div>
                              <SectionLabel>{w.title.toUpperCase()}</SectionLabel>
                              <div className="text-base font-semibold text-[var(--ff-on-bg)] tabular-nums">
                                {formatMinutes(summary.totalMs)} in {summary.title.toLowerCase()}
                              </div>
                            </div>
                            <div className="flex items-center gap-1.5">
                              <button
                                type="button"
                                onClick={onEditWidgets}
                                aria-label={`Edit ${w.title} widget`}
                                className="p-2 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                              >
                                <Pencil className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                onClick={onOpenStatistics}
                                className="text-xs font-medium text-[var(--ff-primary)] hover:underline px-2 py-1 cursor-pointer"
                              >
                                Full stats
                              </button>
                            </div>
                          </div>

                          <div className="flex flex-wrap gap-1.5">
                            {(
                              [
                                ['DAYS', 'Past days'],
                                ['WEEKS', 'Past weeks'],
                                ['MONTH', 'Past month'],
                              ] as Array<[ProgressRange, string]>
                            ).map(([r, label]) => (
                              <ChipButton
                                key={r}
                                selected={range === r}
                                onClick={() => updateWidgetProgressRange(w.id, r)}
                                className="px-3 py-1 text-xs"
                              >
                                {label}
                              </ChipButton>
                            ))}
                          </div>

                          <BarChart
                            bars={summary.buckets.map((b) => ({
                              label: b.label,
                              value: b.valueMs,
                              valueLabel: b.valueLabel,
                            }))}
                          />
                        </AppCard>
                      </div>
                    );
                  }

                  const subName =
                    db.subjects.find((s) => s.id === w.subjectId)?.name ?? null;
                  const presetName =
                    db.presets.find((p) => p.id === w.presetId)?.name ?? null;
                  const allNames = resolveWidgetSubjectNames(w);
                  const colSpan = w.size === 'LARGE' ? 'sm:col-span-2' : '';
                  return (
                    <div key={w.id} className={colSpan}>
                      <WidgetTile
                        widget={w}
                        subtitle={WidgetStyle.subtitle(w, subName, presetName, allNames)}
                        onClick={() => handleWidgetTap(w)}
                        dimmed={activeSession !== null}
                      />
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
          <button
            type="button"
            disabled={activeSession !== null}
            onClick={onStartSession}
            className="w-full py-2.5 px-4 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] disabled:opacity-45 disabled:cursor-not-allowed transition-colors cursor-pointer"
          >
            {activeSession === null ? 'Custom session…' : 'Session running'}
          </button>
          <button
            type="button"
            disabled={activeSession !== null}
            onClick={() => setVoiceModalWidget('QUICK')}
            className="w-full py-2.5 px-4 rounded-xl text-sm font-medium border border-[var(--ff-primary)]/45 bg-[var(--ff-primary-container)]/30 text-[var(--ff-on-bg)] hover:bg-[var(--ff-primary-container)]/50 disabled:opacity-45 disabled:cursor-not-allowed transition-colors inline-flex items-center justify-center gap-2 cursor-pointer"
          >
            <Mic className="h-4 w-4 text-[var(--ff-primary)]" />
            <span>Speak to start (circle mode)</span>
          </button>
        </div>
      </div>

      {chapterStats.length > 0 && (
        <AppCard>
          <SectionLabel>CHAPTERS THIS WEEK</SectionLabel>
          <div className="flex flex-col gap-3">
            {(() => {
              const maxFocus = Math.max(1, chapterStats[0].focusMs);
              return chapterStats.map((st) => {
                const ch = db.chapters.find((c) => c.id === st.chapterId);
                if (!ch) return null;
                return (
                  <div key={st.chapterId} className="flex flex-col gap-1.5">
                    <div className="text-sm text-[var(--ff-on-bg)] tabular-nums">
                      <span className="font-medium">{ch.name}</span> — {formatMinutes(st.focusMs)}{' '}
                      · {st.completedTasks} done, {st.unfinishedTasks} open
                    </div>
                    <ProgressBar progress={st.focusMs / maxFocus} />
                  </div>
                );
              });
            })()}
          </div>
        </AppCard>
      )}

      <div className="flex flex-col gap-3 pt-1">
        <SectionLabel>RECENT SESSIONS</SectionLabel>
        {finishedSessions.length === 0 ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">No sessions yet.</p>
        ) : (
          <>
            <div className="flex flex-col gap-2.5">
              {finishedSessions.slice(0, 3).map((s) => {
                const chIds = db.sessionChapters
                  .filter((sc) => sc.sessionId === s.id)
                  .map((sc) => sc.chapterId);
                const chNames = db.chapters
                  .filter((c) => chIds.includes(c.id))
                  .map((c) => c.name);
                const links = db.sessionTasks.filter((st) => st.sessionId === s.id);
                return (
                  <SessionRowCard
                    key={s.id}
                    session={s}
                    chapterNames={chNames}
                    completedTasks={links.filter((l) => l.completed).length}
                    totalTasks={links.length}
                    onClick={() => onOpenSession(s.id)}
                  />
                );
              })}
            </div>
            <div>
              <button
                type="button"
                onClick={onOpenHistory}
                className="px-4 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                See all history
              </button>
            </div>
          </>
        )}
      </div>

      {voiceModalWidget !== null && (
        <VoiceQuickStartDialog
          widget={voiceModalWidget === 'QUICK' ? null : voiceModalWidget}
          voiceOfflineOnly={settings.voiceOfflineOnly}
          onDismiss={() => setVoiceModalWidget(null)}
          onStartFromVoice={(spokenText) => {
            const w = voiceModalWidget === 'QUICK' ? null : voiceModalWidget;
            const analysis = PlanAnalyzer.analyze(spokenText, db.subjects, db.chapters);

            let baseDraft: SessionDraft | null = null;
            let stayMinimized = true;
            if (w) {
              stayMinimized = w.launchMinimized !== false;
              const subName = db.subjects.find((s) => s.id === w.subjectId)?.name ?? null;
              const wTaskIds = w.taskIds
                .split(',')
                .map((x) => Number(x.trim()))
                .filter((n) => Number.isInteger(n) && n > 0);
              const wTasks = wTaskIds
                .map((id) => db.tasks.find((t) => t.id === id))
                .filter((t): t is NonNullable<typeof t> => Boolean(t));
              const plan = WidgetDraftBuilder.plan(w, {
                subjectName: subName,
                subjects: db.subjects,
                tasks: wTasks,
                preset: null,
                presetSubjectName: null,
              });
              if (plan.kind === 'start') {
                baseDraft = plan.draft;
              }
            }

            const detectedPlans = analysis.detectedSubjects.map((sp) => ({
              subjectId: sp.subjectId,
              subjectName: sp.subjectName,
              chapterIds: sp.chapterIds,
            }));

            const plans =
              detectedPlans.length > 0
                ? detectedPlans
                : baseDraft?.subjectPlans && baseDraft.subjectPlans.length > 0
                  ? baseDraft.subjectPlans
                  : [];

            const subjectName =
              plans.length > 0
                ? plans.map((p) => p.subjectName).join(' + ')
                : baseDraft?.subjectName ?? 'Focus session';

            const allChapterIds = Array.from(
              new Set([
                ...(baseDraft?.chapterIds ?? []),
                ...plans.flatMap((p) => p.chapterIds),
              ]),
            );

            const mergedTasks: DraftTask[] = [...(baseDraft?.tasks ?? [])];
            for (const st of analysis.suggestedTasks) {
              mergedTasks.push(st);
            }

            const minutes =
              w && analysis.detectedSubjects.length === 0
                ? w.durationMinutes
                : analysis.estimatedMinutes;

            const finalDraft: SessionDraft = {
              subjectId: plans[0]?.subjectId ?? baseDraft?.subjectId ?? null,
              subjectName,
              subjectPlans: plans.length > 0 ? plans : undefined,
              chapterIds: allChapterIds,
              description: spokenText.trim(),
              mode: baseDraft?.mode ?? 'TIMED',
              plannedMinutes: (baseDraft?.mode ?? 'TIMED') === 'TIMED' ? minutes : null,
              strict: baseDraft?.strict ?? true,
              presetId: baseDraft?.presetId ?? null,
              tasks: mergedTasks,
              floatingAssistant: true,
              blockShorts: true,
            };

            const outcome = startSession(finalDraft);
            setVoiceModalWidget(null);
            if (outcome.kind === 'started') {
              if (stayMinimized) {
                setBannerMsg({
                  text: `Started "${subjectName}" (${minutes} min) in the floating circle${
                    outcome.snapGptTriggered ? ' · SnapGPT triggered' : ''
                  }.`,
                  offerOpenSession: true,
                });
              } else {
                onOpenFocus();
              }
            } else if (outcome.kind === 'already_active') {
              setBannerMsg({ text: 'A session is already running.', offerOpenSession: true });
            }
          }}
        />
      )}
    </div>
  );
};

const VoiceQuickStartDialog: React.FC<{
  widget: DashboardWidget | null;
  voiceOfflineOnly: boolean;
  onDismiss: () => void;
  onStartFromVoice: (spokenText: string) => void;
}> = ({ widget, voiceOfflineOnly, onDismiss, onStartFromVoice }) => {
  const { db } = useFocusFlow();
  const [text, setText] = useState('');
  const [listening, setListening] = useState(false);
  const [partial, setPartial] = useState('');
  const [voiceAvail, setVoiceAvail] = useState(false);
  const recRef = useRef<WebVoiceRecognizer | null>(null);
  const committedRef = useRef(false);

  useEffect(() => {
    const rec = new WebVoiceRecognizer();
    recRef.current = rec;
    const avail = rec.availability() === 'AVAILABLE';
    setVoiceAvail(avail);
    if (avail) {
      rec.start(voiceOfflineOnly, {
        onListening: () => {
          setListening(true);
          setPartial('');
        },
        onPartial: (p) => setPartial(p),
        onFinal: (finalText) => {
          setListening(false);
          setPartial('');
          if (finalText.trim() && !committedRef.current) {
            committedRef.current = true;
            onStartFromVoice(finalText);
          }
        },
        onError: () => {
          setListening(false);
        },
      });
    }
    return () => rec.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const livePreview = PlanAnalyzer.analyze(
    [text, partial].filter(Boolean).join(' '),
    db.subjects,
    db.chapters,
  );

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-lg rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-2">
          <div>
            <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">
              {widget ? `Voice start · ${widget.title}` : 'Speak to start session'}
            </h3>
            <p className="text-xs text-[var(--ff-on-surface-variant)]">
              When you finish speaking, FocusFlow detects subjects, chapters, and time, then
              starts the floating circle without leaving Home.
            </p>
          </div>
          {listening && (
            <div className="h-9 w-9 rounded-full bg-[var(--ff-error)]/15 text-[var(--ff-error)] flex items-center justify-center shrink-0">
              <Volume2 className="h-4 w-4" />
            </div>
          )}
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-[var(--ff-on-surface-variant)]">
            {listening ? 'Listening… or type your plan below' : 'Speak or type your plan'}
          </span>
          <textarea
            rows={3}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="e.g. I am doing sets and trigonometry for math and atomic structure for chemistry"
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] p-3 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>

        {partial && (
          <div className="text-xs text-[var(--ff-primary)]">Hearing: “{partial}”</div>
        )}

        <div className="rounded-xl bg-[var(--ff-bg)] border border-[var(--ff-outline)]/35 p-3 flex flex-col gap-1">
          <div className="text-xs font-semibold uppercase tracking-wider text-[var(--ff-on-surface-variant)]">
            Live detection preview
          </div>
          <div className="text-sm font-medium text-[var(--ff-on-bg)]">{livePreview.reason}</div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
          {voiceAvail ? (
            <button
              type="button"
              onClick={() => {
                if (!recRef.current) return;
                if (listening) {
                  recRef.current.finishListening();
                } else {
                  recRef.current.start(voiceOfflineOnly, {
                    onListening: () => setListening(true),
                    onPartial: (p) => setPartial(p),
                    onFinal: (f) => {
                      setListening(false);
                      if (f.trim() && !committedRef.current) {
                        committedRef.current = true;
                        onStartFromVoice(f);
                      }
                    },
                    onError: () => setListening(false),
                  });
                }
              }}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              <Mic className="h-4 w-4 text-[var(--ff-primary)]" />
              {listening ? 'Finish speaking' : 'Listen again'}
            </button>
          ) : (
            <span className="text-xs text-[var(--ff-on-surface-variant)]">
              Type your plan to auto-detect &amp; start
            </span>
          )}

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onDismiss}
              className="px-4 py-2 rounded-xl text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => {
                if (committedRef.current) return;
                committedRef.current = true;
                onStartFromVoice(text || (widget?.title ?? 'Focus session'));
              }}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-semibold bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
            >
              <Play className="h-4 w-4" />
              Start in circle
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
