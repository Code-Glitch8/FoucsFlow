import React, { useState } from 'react';
import { Pencil, Plus, X } from 'lucide-react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { BreakRules, hasAnyCredits } from '../domain/BreakRules';
import { formatClock } from '../domain/Format';
import { AppGuardBar } from '../components/AppGuardBar';
import {
  AppCard,
  ConfirmDialog,
  ProgressBar,
  ScreenTitle,
  SectionLabel,
  TextInputDialog,
} from '../components/ui/Common';

export const FocusScreen: React.FC<{ onStartSession: () => void }> = ({ onStartSession }) => {
  const {
    db,
    activeSession,
    now,
    setSessionTaskCompleted,
    setSubtaskDone,
    addSessionTask,
    removeSessionTask,
    updateActivePlannedMinutes,
    startBreak,
    endBreakEarly,
    endSession,
  } = useFocusFlow();

  const [showAddTask, setShowAddTask] = useState(false);
  const [showEditDuration, setShowEditDuration] = useState(false);
  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const [showEmergency, setShowEmergency] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  if (!activeSession) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center text-center px-4 gap-4">
        <ScreenTitle>Focus</ScreenTitle>
        <p className="max-w-md text-base text-[var(--ff-on-surface-variant)]">
          Pick a subject and chapters, say what you want to accomplish, and start a timed or
          open-ended session.
        </p>
        <button
          type="button"
          onClick={onStartSession}
          className="px-6 py-3.5 rounded-2xl text-sm font-semibold bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
        >
          Start studying
        </button>
      </div>
    );
  }

  const s = activeSession.session;
  const clock = sessionToClock(s);
  const onBreak = SessionClock.isOnBreak(clock, now);
  const focusElapsedMs = SessionClock.focusElapsedMs(clock, now);
  const remainingMs = SessionClock.remainingMs(clock, now);
  const breakRemainingMs = SessionClock.breakRemainingMs(clock, now);

  const completedCount = activeSession.tasks.filter((t) => t.link.completed).length;
  const allTasksDone = activeSession.tasks.every((t) => t.link.completed);
  const canFinishNormally =
    !s.strict || (s.mode === 'OPEN_ENDED' && allTasksDone);

  const credits = BreakRules.available(
    completedCount,
    s.shortBreaksUsed,
    s.longBreaksUsed,
    activeSession.breakConfig,
  );
  const cfg = activeSession.breakConfig;
  const chapters = db.chapters.filter((c) => activeSession.chapterIds.includes(c.id));

  const timerLabel = onBreak
    ? 'ON BREAK'
    : s.mode === 'TIMED'
      ? 'REMAINING'
      : 'FOCUSED';
  const timerValue = onBreak
    ? formatClock(breakRemainingMs)
    : s.mode === 'TIMED'
      ? formatClock(remainingMs ?? 0)
      : formatClock(focusElapsedMs);

  const handleFinishNormally = () => {
    const status =
      s.mode === 'TIMED' && (remainingMs ?? 0) > 0 ? 'ENDED_EARLY' : 'COMPLETED';
    endSession(s.id, status);
  };

  return (
    <div className="flex flex-col gap-4 pb-8">
      <div>
        <h1 className="text-2xl font-semibold text-[var(--ff-on-bg)]">{s.subjectName}</h1>
        {chapters.length > 0 && (
          <p className="text-sm text-[var(--ff-on-surface-variant)] pt-0.5">
            {chapters.map((c) => c.name).join(' · ')}
          </p>
        )}
      </div>

      {toast && (
        <div className="rounded-xl bg-[var(--ff-surface-variant)] px-4 py-2.5 text-sm text-[var(--ff-on-bg)] flex justify-between">
          <span>{toast}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            className="text-xs text-[var(--ff-on-surface-variant)] cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Timer Card */}
      <AppCard>
        <SectionLabel>{timerLabel}</SectionLabel>
        <div className="text-5xl md:text-6xl font-semibold text-center font-mono-tabular py-2 text-[var(--ff-on-bg)]">
          {timerValue}
        </div>
        {s.mode === 'TIMED' && s.plannedDurationMs != null && s.plannedDurationMs > 0 && (
          <>
            <ProgressBar progress={focusElapsedMs / s.plannedDurationMs} />
            <div className="text-sm text-[var(--ff-on-surface-variant)] font-mono-tabular">
              Focused {formatClock(focusElapsedMs)} of {formatClock(s.plannedDurationMs)}
            </div>
          </>
        )}
        {s.mode === 'OPEN_ENDED' && onBreak && (
          <div className="text-sm text-[var(--ff-on-surface-variant)] font-mono-tabular">
            Focused so far: {formatClock(focusElapsedMs)}
          </div>
        )}
        <div className="flex items-center justify-between gap-2 text-xs text-[var(--ff-on-surface-variant)]">
          <span>{s.strict ? 'Strict session' : 'Relaxed session'}</span>
          {!s.strict && s.mode === 'TIMED' && (
            <button
              type="button"
              onClick={() => setShowEditDuration(true)}
              className="inline-flex items-center gap-1 text-xs font-medium text-[var(--ff-primary)] hover:underline cursor-pointer"
            >
              <Pencil className="h-3.5 w-3.5" />
              Edit duration
            </button>
          )}
        </div>
      </AppCard>

      <AppGuardBar />

      {/* Earned Breaks Card */}
      <AppCard>
        <SectionLabel>BREAKS</SectionLabel>
        {!cfg.enabled ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            Breaks are turned off in Settings.
          </p>
        ) : onBreak ? (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-[var(--ff-on-bg)]">
              Enjoy your break. Focus resumes automatically when it ends.
            </p>
            <div>
              <button
                type="button"
                onClick={() => endBreakEarly(s.id)}
                className="px-4 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] cursor-pointer"
              >
                End break now
              </button>
            </div>
          </div>
        ) : !hasAnyCredits(credits) ? (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            Complete {cfg.tasksForShortBreak} task{cfg.tasksForShortBreak === 1 ? '' : 's'} to earn
            a {cfg.shortBreakMinutes}-minute break.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {credits.shortAvailable > 0 && (
              <button
                type="button"
                onClick={() => {
                  if (!startBreak(s.id, 'SHORT')) setToast('No break available right now');
                }}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
              >
                {cfg.shortBreakMinutes}-min break ({credits.shortAvailable})
              </button>
            )}
            {credits.longAvailable > 0 && (
              <button
                type="button"
                onClick={() => {
                  if (!startBreak(s.id, 'LONG')) setToast('No break available right now');
                }}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
              >
                {cfg.longBreakMinutes}-min break ({credits.longAvailable})
              </button>
            )}
          </div>
        )}
      </AppCard>

      {/* Tasks */}
      <SectionLabel className="pt-1">
        TASKS · {completedCount}/{activeSession.tasks.length} done
      </SectionLabel>
      {activeSession.tasks.length === 0 ? (
        <p className="text-sm text-[var(--ff-on-surface-variant)]">
          No tasks planned. Add one to start earning breaks.
        </p>
      ) : (
        <div className="flex flex-col gap-2.5">
          {activeSession.tasks.map((row) => {
            const subs = activeSession.subtasks.filter((sub) => sub.taskId === row.link.taskId);
            return (
              <AppCard key={row.link.id}>
                <div className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={row.link.completed}
                    onChange={(e) =>
                      setSessionTaskCompleted(row.link.id, row.link.taskId, e.target.checked)
                    }
                    className="h-5 w-5 accent-[var(--ff-primary)] cursor-pointer shrink-0"
                  />
                  <div className="min-w-0 flex-1">
                    <div
                      className={`text-base font-medium text-[var(--ff-on-bg)] ${
                        row.link.completed ? 'line-through opacity-65' : ''
                      }`}
                    >
                      {row.title}
                    </div>
                    {row.estimatedMinutes != null && (
                      <div className="text-xs text-[var(--ff-on-surface-variant)]">
                        ~{row.estimatedMinutes} min
                      </div>
                    )}
                  </div>
                  {!row.link.completed && (
                    <button
                      type="button"
                      aria-label={`Remove ${row.title} from this session`}
                      onClick={() => removeSessionTask(row.link.id)}
                      className="p-1.5 rounded-lg text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  )}
                </div>
                {subs.length > 0 && (
                  <div className="flex flex-col gap-1.5 pl-8">
                    {subs.map((sub) => (
                      <label
                        key={sub.id}
                        className="flex items-center gap-2 text-sm text-[var(--ff-on-bg)] cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          checked={sub.done}
                          onChange={(e) => setSubtaskDone(sub.id, e.target.checked)}
                          className="h-4 w-4 accent-[var(--ff-primary)] cursor-pointer"
                        />
                        <span className={sub.done ? 'line-through opacity-60' : ''}>
                          {sub.title}
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </AppCard>
            );
          })}
        </div>
      )}

      <div>
        <button
          type="button"
          onClick={() => setShowAddTask(true)}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-medium border border-[var(--ff-outline)]/45 hover:bg-[var(--ff-surface-variant)] cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          Add task
        </button>
      </div>

      {/* End Session Controls */}
      <div className="flex flex-col gap-2 pt-2">
        {canFinishNormally ? (
          <button
            type="button"
            onClick={() => setShowEndConfirm(true)}
            className="w-full py-3.5 px-6 rounded-2xl text-sm font-semibold bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
          >
            {s.strict ? 'Finish session' : 'End session'}
          </button>
        ) : (
          <p className="text-sm text-[var(--ff-on-surface-variant)]">
            {s.mode === 'TIMED'
              ? 'Strict session: it ends when the timer does.'
              : 'Strict session: finish your remaining tasks to end it.'}
          </p>
        )}

        {s.strict && (
          <div>
            <button
              type="button"
              onClick={() => setShowEmergency(true)}
              className="text-xs font-medium text-[var(--ff-error)] hover:underline py-2 cursor-pointer"
            >
              Emergency exit
            </button>
          </div>
        )}
      </div>

      <TextInputDialog
        open={showAddTask}
        title="Add task"
        label="What do you want to do?"
        confirmLabel="Add"
        onDismiss={() => setShowAddTask(false)}
        onConfirm={(val) => {
          if (!addSessionTask(s.id, val)) {
            setToast('That task is already in this session');
          }
          setShowAddTask(false);
        }}
      />

      <TextInputDialog
        open={showEditDuration}
        title="Edit session duration"
        label="Planned minutes (1–600)"
        confirmLabel="Save"
        initial={s.plannedDurationMs ? String(Math.round(s.plannedDurationMs / 60_000)) : '25'}
        onDismiss={() => setShowEditDuration(false)}
        onConfirm={(val) => {
          const mins = Number(val);
          if (Number.isFinite(mins) && mins >= 1) {
            updateActivePlannedMinutes(s.id, mins);
          }
          setShowEditDuration(false);
        }}
      />

      <ConfirmDialog
        open={showEndConfirm}
        title="End this session?"
        message="Unfinished tasks stay on your task list so you can pick them up later."
        confirmLabel="End session"
        onDismiss={() => setShowEndConfirm(false)}
        onConfirm={() => {
          handleFinishNormally();
          setShowEndConfirm(false);
        }}
      />

      {showEmergency && (
        <EmergencyExitDialog
          onDismiss={() => setShowEmergency(false)}
          onConfirm={() => {
            endSession(s.id, 'EMERGENCY_EXIT');
            setShowEmergency(false);
          }}
        />
      )}
    </div>
  );
};

const EmergencyExitDialog: React.FC<{
  onDismiss: () => void;
  onConfirm: () => void;
}> = ({ onDismiss, onConfirm }) => {
  const [text, setText] = useState('');
  const confirmed = text.trim().toUpperCase() === 'EXIT';
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onDismiss}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/45 p-6 flex flex-col gap-4 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-[var(--ff-on-bg)]">Emergency exit</h3>
        <p className="text-sm text-[var(--ff-on-surface-variant)] leading-relaxed">
          This ends your strict session now and is recorded as an emergency exit in your history.
          Use it only if you really need to. Phone calls and system features are never blocked.
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs text-[var(--ff-on-surface-variant)]">Type EXIT to confirm</span>
          <input
            autoFocus
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="w-full rounded-xl border border-[var(--ff-outline)]/50 bg-[var(--ff-bg)] px-3.5 py-2.5 text-sm text-[var(--ff-on-bg)] focus:border-[var(--ff-primary)] focus:outline-none"
          />
        </label>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-xl text-sm font-medium text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
          >
            Keep studying
          </button>
          <button
            type="button"
            disabled={!confirmed}
            onClick={onConfirm}
            className="px-4 py-2 rounded-xl text-sm font-medium bg-[var(--ff-error)] text-white disabled:opacity-40 cursor-pointer"
          >
            End session
          </button>
        </div>
      </div>
    </div>
  );
};
