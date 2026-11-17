package com.focusflow.app.domain

/**
 * Pure timing maths for a session, kept free of Android so it can be unit tested.
 *
 * Everything is derived from persisted timestamps, never from an in-memory counter, so an
 * app restart or process death cannot lose or skew the session. Break time is excluded from
 * focus time.
 */
data class ClockState(
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Planned focus length for timed sessions, null for open-ended. */
    val plannedMs: Long? = null,
    val breakMsCompleted: Long = 0,
    val breakStartedAt: Long? = null,
    val breakEndsAt: Long? = null,
)

object SessionClock {
    /** A break is running only while now is inside [breakStartedAt, breakEndsAt). */
    fun isOnBreak(s: ClockState, now: Long): Boolean {
        val start = s.breakStartedAt ?: return false
        val end = s.breakEndsAt ?: return false
        return s.endedAt == null && now >= start && now < end
    }

    /** Milliseconds of break time that have elapsed (finished breaks plus the current one). */
    fun breakElapsedMs(s: ClockState, now: Long): Long {
        val clockNow = s.endedAt ?: now
        val start = s.breakStartedAt
        val end = s.breakEndsAt
        val current = if (start != null && end != null) {
            (minOf(clockNow, end) - start).coerceAtLeast(0)
        } else 0L
        return s.breakMsCompleted + current
    }

    fun focusElapsedMs(s: ClockState, now: Long): Long {
        val clockNow = s.endedAt ?: now
        return (clockNow - s.startedAt - breakElapsedMs(s, now)).coerceAtLeast(0)
    }

    /** Remaining focus time for timed sessions, or null for open-ended ones. */
    fun remainingMs(s: ClockState, now: Long): Long? {
        val planned = s.plannedMs ?: return null
        return (planned - focusElapsedMs(s, now)).coerceAtLeast(0)
    }

    fun isTimedFinished(s: ClockState, now: Long): Boolean =
        s.plannedMs != null && (remainingMs(s, now) ?: 0L) <= 0L

    fun breakRemainingMs(s: ClockState, now: Long): Long {
        val end = s.breakEndsAt ?: return 0
        return if (isOnBreak(s, now)) (end - now).coerceAtLeast(0) else 0
    }

    /** Folds a finished break into [ClockState.breakMsCompleted]. No-op while a break runs. */
    fun settleBreak(s: ClockState, now: Long): ClockState {
        val start = s.breakStartedAt ?: return s
        val end = s.breakEndsAt ?: return s
        if (now < end) return s
        return s.copy(
            breakMsCompleted = s.breakMsCompleted + (end - start).coerceAtLeast(0),
            breakStartedAt = null,
            breakEndsAt = null,
        )
    }

    /** Starts a break of [durationMs] at [now]. Any already-finished break is settled first. */
    fun startBreak(s: ClockState, now: Long, durationMs: Long): ClockState {
        val settled = settleBreak(s, now)
        require(!isOnBreak(settled, now)) { "A break is already running" }
        return settled.copy(breakStartedAt = now, breakEndsAt = now + durationMs)
    }

    /** Ends the running break early; only the time actually used counts. */
    fun endBreakEarly(s: ClockState, now: Long): ClockState {
        val start = s.breakStartedAt ?: return s
        if (!isOnBreak(s, now)) return settleBreak(s, now)
        return s.copy(
            breakMsCompleted = s.breakMsCompleted + (now - start).coerceAtLeast(0),
            breakStartedAt = null,
            breakEndsAt = null,
        )
    }
}
