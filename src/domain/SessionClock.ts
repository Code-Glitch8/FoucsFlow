import { StudySession } from '../types/models';

export interface ClockState {
  startedAt: number;
  endedAt: number | null;
  plannedMs: number | null;
  breakMsCompleted: number;
  breakStartedAt: number | null;
  breakEndsAt: number | null;
}

export function sessionToClock(s: StudySession): ClockState {
  return {
    startedAt: s.startedAt,
    endedAt: s.endedAt,
    plannedMs: s.plannedDurationMs,
    breakMsCompleted: s.breakMsCompleted,
    breakStartedAt: s.breakStartedAt,
    breakEndsAt: s.breakEndsAt,
  };
}

export const SessionClock = {
  isOnBreak(s: ClockState, now: number): boolean {
    if (s.breakStartedAt == null || s.breakEndsAt == null) return false;
    return s.endedAt == null && now >= s.breakStartedAt && now < s.breakEndsAt;
  },

  breakElapsedMs(s: ClockState, now: number): number {
    const clockNow = s.endedAt ?? now;
    const current =
      s.breakStartedAt != null && s.breakEndsAt != null
        ? Math.max(0, Math.min(clockNow, s.breakEndsAt) - s.breakStartedAt)
        : 0;
    return s.breakMsCompleted + current;
  },

  focusElapsedMs(s: ClockState, now: number): number {
    const clockNow = s.endedAt ?? now;
    return Math.max(0, clockNow - s.startedAt - SessionClock.breakElapsedMs(s, now));
  },

  remainingMs(s: ClockState, now: number): number | null {
    if (s.plannedMs == null) return null;
    return Math.max(0, s.plannedMs - SessionClock.focusElapsedMs(s, now));
  },

  isTimedFinished(s: ClockState, now: number): boolean {
    if (s.plannedMs == null) return false;
    return (SessionClock.remainingMs(s, now) ?? 0) <= 0;
  },

  breakRemainingMs(s: ClockState, now: number): number {
    if (s.breakEndsAt == null) return 0;
    return SessionClock.isOnBreak(s, now) ? Math.max(0, s.breakEndsAt - now) : 0;
  },

  settleBreak(s: ClockState, now: number): ClockState {
    if (s.breakStartedAt == null || s.breakEndsAt == null) return s;
    if (now < s.breakEndsAt) return s;
    return {
      ...s,
      breakMsCompleted: s.breakMsCompleted + Math.max(0, s.breakEndsAt - s.breakStartedAt),
      breakStartedAt: null,
      breakEndsAt: null,
    };
  },

  startBreak(s: ClockState, now: number, durationMs: number): ClockState {
    const settled = SessionClock.settleBreak(s, now);
    if (SessionClock.isOnBreak(settled, now)) {
      throw new Error('A break is already running');
    }
    return {
      ...settled,
      breakStartedAt: now,
      breakEndsAt: now + durationMs,
    };
  },

  endBreakEarly(s: ClockState, now: number): ClockState {
    if (s.breakStartedAt == null) return s;
    if (!SessionClock.isOnBreak(s, now)) return SessionClock.settleBreak(s, now);
    return {
      ...s,
      breakMsCompleted: s.breakMsCompleted + Math.max(0, now - s.breakStartedAt),
      breakStartedAt: null,
      breakEndsAt: null,
    };
  },
};
