package com.focusflow.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A finished session reduced to what statistics need. [focusMs] already excludes breaks. */
data class SessionFact(
    val id: Long,
    val subjectId: Long?,
    val startedAt: Long,
    val focusMs: Long,
    val chapterIds: Set<Long>,
)

data class DayTotal(val day: LocalDate, val focusMs: Long)

data class ChapterStat(
    val chapterId: Long,
    /** Time only. It says how long you spent, never how well you know the chapter. */
    val focusMs: Long,
    val sessions: Int,
    val completedTasks: Int,
    val unfinishedTasks: Int,
    val lastStudiedAt: Long?,
)

/**
 * Pure statistics. A session counts toward the local day it started on. When a session covers
 * several chapters its focus time is shared equally between them, so totals are never inflated.
 */
object StudyStats {
    private const val MIN_STREAK_FOCUS_MS = 60_000L

    fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun focusOnDay(sessions: List<SessionFact>, day: LocalDate, zone: ZoneId): Long =
        sessions.filter { dayOf(it.startedAt, zone) == day }.sumOf { it.focusMs }

    /** The last [days] days ending at [today], oldest first, including days with no study. */
    fun dailyFocus(sessions: List<SessionFact>, today: LocalDate, days: Int, zone: ZoneId): List<DayTotal> {
        val byDay = sessions.groupBy { dayOf(it.startedAt, zone) }.mapValues { e -> e.value.sumOf { it.focusMs } }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayTotal(d, byDay[d] ?: 0L)
        }
    }

    /**
     * Consecutive days with at least a minute of focus. Today not having started yet does not
     * break a streak that was alive yesterday.
     */
    fun streak(sessions: List<SessionFact>, today: LocalDate, zone: ZoneId): Int {
        val studied = sessions.filter { it.focusMs >= MIN_STREAK_FOCUS_MS }
            .map { dayOf(it.startedAt, zone) }.toSet()
        var day = if (today in studied) today else today.minusDays(1)
        var count = 0
        while (day in studied) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun chapterStats(
        chapterIds: List<Long>,
        sessions: List<SessionFact>,
        completedByChapter: Map<Long, Int>,
        openByChapter: Map<Long, Int>,
    ): List<ChapterStat> = chapterIds.map { id ->
        val touching = sessions.filter { id in it.chapterIds }
        ChapterStat(
            chapterId = id,
            focusMs = touching.sumOf { it.focusMs / it.chapterIds.size },
            sessions = touching.size,
            completedTasks = completedByChapter[id] ?: 0,
            unfinishedTasks = openByChapter[id] ?: 0,
            lastStudiedAt = touching.maxOfOrNull { it.startedAt },
        )
    }
}
