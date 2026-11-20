import React, { useEffect, useRef, useState } from 'react';
import { useFocusFlow } from '../store/FocusFlowStore';
import { SessionClock, sessionToClock } from '../domain/SessionClock';
import { BreakRules, hasAnyCredits } from '../domain/BreakRules';
import { formatClock } from '../domain/Format';

export const FloatingAssistant: React.FC<{ onOpenFocus: () => void }> = ({ onOpenFocus }) => {
  const {
    activeSession,
    settings,
    db,
    now,
    updateSettings,
    setSessionTaskCompleted,
    startBreak,
    endBreakEarly,
  } = useFocusFlow();

  const [expanded, setExpanded] = useState(false);
  const [pos, setPos] = useState<{ x: number; y: number }>(() => {
    if (settings.bubblePosition) return settings.bubblePosition;
    return {
      x: typeof window !== 'undefined' ? Math.max(16, window.innerWidth - 84) : 24,
      y: typeof window !== 'undefined' ? Math.max(24, window.innerHeight - 144) : 120,
    };
  });

  const dragRef = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
    moved: boolean;
  }>({ active: false, startX: 0, startY: 0, origX: 0, origY: 0, moved: false });

  useEffect(() => {
    if (settings.bubblePosition) {
      setPos(settings.bubblePosition);
    } else if (typeof window !== 'undefined') {
      setPos({
        x: Math.max(16, window.innerWidth - 84),
        y: Math.max(24, window.innerHeight - 144),
      });
    }
  }, [settings.bubblePosition]);

  if (!activeSession || !settings.floatingEnabled || !activeSession.session.floatingAssistant) {
    return null;
  }

  const s = activeSession.session;
  const clock = sessionToClock(s);
  const onBreak = SessionClock.isOnBreak(clock, now);
  const completedCount = activeSession.tasks.filter((t) => t.link.completed).length;
  const credits = BreakRules.available(
    completedCount,
    s.shortBreaksUsed,
    s.longBreaksUsed,
    activeSession.breakConfig,
  );
  const chapterNames = db.chapters
    .filter((c) => activeSession.chapterIds.includes(c.id))
    .map((c) => c.name);

  const bubbleText = onBreak
    ? formatClock(SessionClock.breakRemainingMs(clock, now))
    : s.mode === 'TIMED'
      ? formatClock(SessionClock.remainingMs(clock, now) ?? 0)
      : formatClock(SessionClock.focusElapsedMs(clock, now));

  const clampPos = (x: number, y: number, isExpanded: boolean) => {
    const w = typeof window !== 'undefined' ? window.innerWidth : 1200;
    const h = typeof window !== 'undefined' ? window.innerHeight : 800;
    const maxX = isExpanded ? Math.max(12, w - 340) : Math.max(12, w - 72);
    const maxY = isExpanded ? Math.max(12, h - 380) : Math.max(12, h - 96);
    return {
      x: Math.max(12, Math.min(maxX, x)),
      y: Math.max(12, Math.min(maxY, y)),
    };
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    dragRef.current = {
      active: true,
      startX: e.clientX,
      startY: e.clientY,
      origX: pos.x,
      origY: pos.y,
      moved: false,
    };
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!dragRef.current.active) return;
    const dx = e.clientX - dragRef.current.startX;
    const dy = e.clientY - dragRef.current.startY;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
      dragRef.current.moved = true;
    }
    setPos(clampPos(dragRef.current.origX + dx, dragRef.current.origY + dy, expanded));
  };

  const handlePointerUp = () => {
    if (!dragRef.current.active) return;
    dragRef.current.active = false;
    if (dragRef.current.moved) {
      updateSettings({ bubblePosition: pos });
    } else if (!expanded) {
      setExpanded(true);
    }
  };

  const clamped = clampPos(pos.x, pos.y, expanded);

  if (!expanded) {
    return (
      <div
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        title="Drag or tap to expand FocusFlow assistant"
        style={{ left: clamped.x, top: clamped.y, touchAction: 'none' }}
        className={`fixed z-40 h-14 w-14 rounded-full flex items-center justify-center shadow-xl cursor-grab active:cursor-grabbing select-none font-mono-tabular text-xs font-semibold transition-colors ${
          onBreak
            ? 'bg-[var(--ff-secondary)] text-[var(--ff-on-secondary)]'
            : 'bg-[var(--ff-primary)] text-[var(--ff-on-primary)]'
        }`}
      >
        {bubbleText}
      </div>
    );
  }

  return (
    <div
      style={{ left: clamped.x, top: clamped.y }}
      className="fixed z-40 w-[320px] max-w-[calc(100vw-24px)] rounded-2xl bg-[var(--ff-surface)] border border-[var(--ff-outline)]/50 p-4 shadow-2xl flex flex-col gap-2.5"
    >
      <div
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        style={{ touchAction: 'none' }}
        className="flex items-start justify-between gap-2 cursor-grab active:cursor-grabbing select-none"
      >
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold text-[var(--ff-on-bg)] truncate">{s.subjectName}</div>
          {chapterNames.length > 0 && (
            <div className="text-xs text-[var(--ff-on-surface-variant)] line-clamp-2">
              {chapterNames.join(' · ')}
            </div>
          )}
        </div>
        <button
          type="button"
          onClick={() => setExpanded(false)}
          className="text-xs font-medium text-[var(--ff-primary)] hover:underline px-2 py-1 cursor-pointer"
        >
          Collapse
        </button>
      </div>

      <div className="text-xl font-semibold font-mono-tabular text-[var(--ff-on-bg)]">
        {onBreak
          ? `On break · ${formatClock(SessionClock.breakRemainingMs(clock, now))} left`
          : s.mode === 'TIMED'
            ? `${formatClock(SessionClock.remainingMs(clock, now) ?? 0)} left`
            : `Focused ${formatClock(SessionClock.focusElapsedMs(clock, now))}`}
      </div>

      <div className="text-xs font-medium text-[var(--ff-on-surface-variant)]">
        Tasks · {completedCount}/{activeSession.tasks.length}
      </div>

      <div className="max-h-40 overflow-y-auto flex flex-col gap-1.5 pr-1">
        {activeSession.tasks.length === 0 ? (
          <div className="text-xs text-[var(--ff-on-surface-variant)]">No tasks planned.</div>
        ) : (
          activeSession.tasks.map((row) => (
            <label
              key={row.link.id}
              className="flex items-center gap-2 text-sm text-[var(--ff-on-bg)] cursor-pointer"
            >
              <input
                type="checkbox"
                checked={row.link.completed}
                onChange={(e) =>
                  setSessionTaskCompleted(row.link.id, row.link.taskId, e.target.checked)
                }
                className="h-4 w-4 accent-[var(--ff-primary)] rounded cursor-pointer"
              />
              <span className={row.link.completed ? 'line-through opacity-60' : ''}>
                {row.title}
              </span>
            </label>
          ))
        )}
      </div>

      {activeSession.breakConfig.enabled && (
        <div className="pt-1">
          {onBreak ? (
            <button
              type="button"
              onClick={() => endBreakEarly(s.id)}
              className="px-3 py-1.5 rounded-xl text-xs font-medium border border-[var(--ff-outline)]/50 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
            >
              End break
            </button>
          ) : hasAnyCredits(credits) ? (
            <div className="flex flex-wrap gap-2">
              {credits.shortAvailable > 0 && (
                <button
                  type="button"
                  onClick={() => startBreak(s.id, 'SHORT')}
                  className="px-3 py-1.5 rounded-xl text-xs font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
                >
                  {activeSession.breakConfig.shortBreakMinutes}-min break
                </button>
              )}
              {credits.longAvailable > 0 && (
                <button
                  type="button"
                  onClick={() => startBreak(s.id, 'LONG')}
                  className="px-3 py-1.5 rounded-xl text-xs font-medium bg-[var(--ff-primary)] text-[var(--ff-on-primary)] cursor-pointer"
                >
                  {activeSession.breakConfig.longBreakMinutes}-min break
                </button>
              )}
            </div>
          ) : (
            <div className="text-xs text-[var(--ff-on-surface-variant)]">
              Complete {activeSession.breakConfig.tasksForShortBreak} task
              {activeSession.breakConfig.tasksForShortBreak === 1 ? '' : 's'} to earn a break.
            </div>
          )}
        </div>
      )}

      <button
        type="button"
        onClick={() => {
          onOpenFocus();
          setExpanded(false);
        }}
        className="w-full py-2 rounded-xl text-xs font-medium border border-[var(--ff-outline)]/50 text-[var(--ff-on-bg)] hover:bg-[var(--ff-surface-variant)] cursor-pointer"
      >
        Open FocusFlow
      </button>
    </div>
  );
};
