package com.focusflow.app

import com.focusflow.app.domain.SessionFact
import com.focusflow.app.domain.StudyStats
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyStatsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 17)
    private fun at(d: LocalDate, hour: Int = 10) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun s(day: LocalDate, minutes: Long, vararg ch: Long, id: Long = 1) =
        SessionFact(id, null, at(day), minutes * 60_000, ch.toSet())

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val list = listOf(s(today, 30), s(today.minusDays(1), 20, id = 2), s(today.minusDays(2), 10, id = 3))
        assertEquals(3, StudyStats.streak(list, today, zone))
    }

    @Test fun streakSurvivesTodayNotStartedYet() {
        val list = listOf(s(today.minusDays(1), 20), s(today.minusDays(2), 20, id = 2))
        assertEquals(2, StudyStats.streak(list, today, zone))
    }

    @Test fun streakBreaksOnGap() {
        val list = listOf(s(today, 20), s(today.minusDays(2), 20, id = 2))
        assertEquals(1, StudyStats.streak(list, today, zone))
        assertEquals(0, StudyStats.streak(listOf(s(today.minusDays(3), 20)), today, zone))
    }

    @Test fun veryShortSessionsDoNotCountForStreak() {
        assertEquals(0, StudyStats.streak(listOf(SessionFact(1, null, at(today), 10_000, emptySet())), today, zone))
    }

    @Test fun dailyFocusFillsEmptyDaysOldestFirst() {
        val out = StudyStats.dailyFocus(listOf(s(today, 30), s(today, 15, id = 2)), today, 3, zone)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), out.map { it.day })
        assertEquals(listOf(0L, 0L, 45 * 60_000L), out.map { it.focusMs })
    }

    @Test fun chapterTimeIsSharedBetweenChaptersOfOneSession() {
        val list = listOf(s(today, 60, 1, 2), s(today.minusDays(1), 30, 1, id = 2))
        val stats = StudyStats.chapterStats(listOf(1, 2, 3), list, mapOf(1L to 2), mapOf(2L to 1))
        assertEquals(60 * 60_000L, stats[0].focusMs)
        assertEquals(2, stats[0].sessions)
        assertEquals(2, stats[0].completedTasks)
        assertEquals(30 * 60_000L, stats[1].focusMs)
        assertEquals(1, stats[1].unfinishedTasks)
        assertEquals(0L, stats[2].focusMs)
        assertNull(stats[2].lastStudiedAt)
        assertEquals(at(today), stats[0].lastStudiedAt)
    }
}
