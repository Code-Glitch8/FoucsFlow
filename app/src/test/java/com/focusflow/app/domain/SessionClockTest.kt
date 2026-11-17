package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionClockTest {
    private val min = 60_000L
    private val t0 = 1_000_000L

    private fun timed(planned: Long = 25 * min) = ClockState(startedAt = t0, plannedMs = planned)

    @Test
    fun `focus elapsed equals wall time with no breaks`() {
        assertEquals(10 * min, SessionClock.focusElapsedMs(timed(), t0 + 10 * min))
    }

    @Test
    fun `remaining counts down and clamps at zero`() {
        val s = timed()
        assertEquals(15 * min, SessionClock.remainingMs(s, t0 + 10 * min))
        assertEquals(0L, SessionClock.remainingMs(s, t0 + 99 * min))
        assertTrue(SessionClock.isTimedFinished(s, t0 + 25 * min))
        assertFalse(SessionClock.isTimedFinished(s, t0 + 24 * min))
    }

    @Test
    fun `open ended session has no remaining time and never auto finishes`() {
        val s = ClockState(startedAt = t0)
        assertNull(SessionClock.remainingMs(s, t0 + 500 * min))
        assertFalse(SessionClock.isTimedFinished(s, t0 + 500 * min))
    }

    @Test
    fun `break time is excluded from focus time and pauses the countdown`() {
        val onBreak = SessionClock.startBreak(timed(), now = t0 + 10 * min, durationMs = 5 * min)
        // 3 minutes into the break: focus is still 10 minutes
        assertTrue(SessionClock.isOnBreak(onBreak, t0 + 13 * min))
        assertEquals(10 * min, SessionClock.focusElapsedMs(onBreak, t0 + 13 * min))
        assertEquals(2 * min, SessionClock.breakRemainingMs(onBreak, t0 + 13 * min))
        // after the break ends, focus resumes from 10 minutes
        assertFalse(SessionClock.isOnBreak(onBreak, t0 + 15 * min))
        assertEquals(12 * min, SessionClock.focusElapsedMs(onBreak, t0 + 17 * min))
    }

    @Test
    fun `settling a finished break folds it into completed break time`() {
        val onBreak = SessionClock.startBreak(timed(), t0 + 10 * min, 5 * min)
        val settled = SessionClock.settleBreak(onBreak, t0 + 20 * min)
        assertEquals(5 * min, settled.breakMsCompleted)
        assertNull(settled.breakStartedAt)
        assertNull(settled.breakEndsAt)
        assertEquals(15 * min, SessionClock.focusElapsedMs(settled, t0 + 20 * min))
    }

    @Test
    fun `settling a running break does nothing`() {
        val onBreak = SessionClock.startBreak(timed(), t0 + 10 * min, 5 * min)
        assertEquals(onBreak, SessionClock.settleBreak(onBreak, t0 + 12 * min))
    }

    @Test
    fun `ending a break early counts only the time used`() {
        val onBreak = SessionClock.startBreak(timed(), t0 + 10 * min, 10 * min)
        val ended = SessionClock.endBreakEarly(onBreak, t0 + 14 * min)
        assertEquals(4 * min, ended.breakMsCompleted)
        assertFalse(SessionClock.isOnBreak(ended, t0 + 14 * min))
        assertEquals(10 * min, SessionClock.focusElapsedMs(ended, t0 + 14 * min))
    }

    @Test
    fun `a second break can start after the first finished`() {
        val first = SessionClock.startBreak(timed(), t0 + 5 * min, 5 * min)
        val second = SessionClock.startBreak(first, t0 + 20 * min, 5 * min)
        assertEquals(5 * min, second.breakMsCompleted)
        assertTrue(SessionClock.isOnBreak(second, t0 + 22 * min))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot start a break while one is running`() {
        val first = SessionClock.startBreak(timed(), t0 + 5 * min, 5 * min)
        SessionClock.startBreak(first, t0 + 7 * min, 5 * min)
    }

    @Test
    fun `an ended session freezes its clock`() {
        val s = timed().copy(endedAt = t0 + 20 * min)
        assertEquals(20 * min, SessionClock.focusElapsedMs(s, t0 + 999 * min))
        assertFalse(SessionClock.isOnBreak(s, t0 + 999 * min))
    }

    @Test
    fun `a clock reading earlier than start never goes negative`() {
        assertEquals(0L, SessionClock.focusElapsedMs(timed(), t0 - 5 * min))
    }
}
