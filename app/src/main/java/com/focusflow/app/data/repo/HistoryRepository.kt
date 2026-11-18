package com.focusflow.app.data.repo

import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.SessionChapterEntity
import com.focusflow.app.data.db.SessionSummaryEntity
import com.focusflow.app.data.db.SessionTaskEntity
import com.focusflow.app.data.db.StudySessionEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.domain.SessionFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything the history and statistics screens read, as one consistent snapshot. */
data class StudyData(
    val sessions: List<StudySessionEntity>,
    val sessionChapters: List<SessionChapterEntity>,
    val sessionTasks: List<SessionTaskEntity>,
    val chapters: List<ChapterEntity>,
    val subjects: List<SubjectEntity>,
    val tasks: List<TaskEntity>,
) {
    private val chaptersBySession: Map<Long, Set<Long>> =
        sessionChapters.groupBy { it.sessionId }.mapValues { e -> e.value.map { it.chapterId }.toSet() }

    fun chapterIdsOf(sessionId: Long): Set<Long> = chaptersBySession[sessionId] ?: emptySet()

    fun focusMs(s: StudySessionEntity): Long = SessionClock.focusElapsedMs(s.clock(), s.endedAt ?: System.currentTimeMillis())

    fun facts(): List<SessionFact> = sessions.map {
        SessionFact(it.id, it.subjectId, it.startedAt, focusMs(it), chapterIdsOf(it.id))
    }
}

class HistoryRepository(private val db: FocusFlowDatabase) {
    private val sessionDao = db.sessionDao()

    /** Finished sessions only; the running one is shown separately and counted once it ends. */
    fun observe(): Flow<StudyData> = combine(
        sessionDao.observeFinished(),
        sessionDao.observeAllSessionChapters(),
        sessionDao.observeAllSessionTasks(),
        db.chapterDao().observeAll(),
        db.subjectDao().observeAll(),
        db.taskDao().observeAll(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        StudyData(
            values[0] as List<StudySessionEntity>,
            values[1] as List<SessionChapterEntity>,
            values[2] as List<SessionTaskEntity>,
            values[3] as List<ChapterEntity>,
            values[4] as List<SubjectEntity>,
            values[5] as List<TaskEntity>,
        )
    }

    fun observeSession(id: Long) = sessionDao.observeById(id)
    fun observeSessionTasks(id: Long) = sessionDao.observeSessionTasks(id)
    fun observeSummary(id: Long) = sessionDao.observeSummary(id)
    fun observeChapterIds(id: Long) = sessionDao.observeChapterIds(id)

    suspend fun saveSummary(sessionId: Long, text: String, source: com.focusflow.app.data.db.SummarySource) {
        sessionDao.upsertSummary(SessionSummaryEntity(sessionId = sessionId, text = text, source = source))
    }
}
