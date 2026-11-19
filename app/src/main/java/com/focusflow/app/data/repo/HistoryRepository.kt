package com.focusflow.app.data.repo

import androidx.room.withTransaction
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.SessionChapterEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.db.SessionSummaryEntity
import com.focusflow.app.data.db.SessionTaskEntity
import com.focusflow.app.data.db.StudySessionEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.sync.LocalSyncRepository
import com.focusflow.app.data.sync.SyncedSession
import com.focusflow.app.data.sync.SyncedTask
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.domain.SessionFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

data class StudyData(
    val sessions: List<StudySessionEntity>,
    val sessionChapters: List<SessionChapterEntity>,
    val sessionTasks: List<SessionTaskEntity>,
    val chapters: List<ChapterEntity>,
    val subjects: List<SubjectEntity>,
    val tasks: List<TaskEntity>,
    val remoteSessionIds: Set<Long> = emptySet(),
) {
    private val chaptersBySession: Map<Long, Set<Long>> =
        sessionChapters.groupBy { it.sessionId }.mapValues { e -> e.value.map { it.chapterId }.toSet() }

    fun chapterIdsOf(sessionId: Long): Set<Long> = chaptersBySession[sessionId] ?: emptySet()
    fun isSyncedSession(sessionId: Long): Boolean = sessionId in remoteSessionIds

    fun focusMs(s: StudySessionEntity): Long =
        SessionClock.focusElapsedMs(s.clock(), s.endedAt ?: System.currentTimeMillis())

    fun facts(): List<SessionFact> = sessions.map {
        SessionFact(it.id, it.subjectId, it.startedAt, focusMs(it), chapterIdsOf(it.id))
    }
}

class HistoryRepository(
    private val db: FocusFlowDatabase,
    private val sync: LocalSyncRepository,
) {
    private val sessionDao = db.sessionDao()

    /** Combines this phone's Room history with the latest peer records cached locally. */
    fun observe(): Flow<StudyData> = combine(
        sessionDao.observeFinished(),
        sessionDao.observeAllSessionChapters(),
        sessionDao.observeAllSessionTasks(),
        db.chapterDao().observeAll(),
        db.subjectDao().observeAll(),
        db.taskDao().observeAll(),
        sync.records,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val localSessions = values[0] as List<StudySessionEntity>
        val localSessionChapters = values[1] as List<SessionChapterEntity>
        val localSessionTasks = values[2] as List<SessionTaskEntity>
        val localChapters = values[3] as List<ChapterEntity>
        val localSubjects = values[4] as List<SubjectEntity>
        val localTasks = values[5] as List<TaskEntity>
        val remoteRecords = values[6] as List<SyncedSession>

        val subjectByName = localSubjects.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val remoteSubjects = mutableListOf<SubjectEntity>()
        val remoteChapters = mutableListOf<ChapterEntity>()
        val remoteSessionChapters = mutableListOf<SessionChapterEntity>()
        val remoteSessionTasks = mutableListOf<SessionTaskEntity>()
        val remoteSessions = mutableListOf<StudySessionEntity>()
        val remoteIds = mutableSetOf<Long>()

        remoteRecords.forEach { record ->
            // A peer relays records it has already received. Do not render this phone's own
            // sessions again as negative-ID remote entries when they are echoed back.
            if (record.status == "ACTIVE" || record.globalId.isBlank() ||
                record.globalId.startsWith("${sync.deviceId}:")
            ) return@forEach
            val sessionId = stableNegativeId("session:${record.globalId}")
            remoteIds += sessionId
            val subjectKey = record.subjectName.trim().lowercase()
            val subjectId = subjectByName[subjectKey]?.id ?: stableNegativeId("subject:$subjectKey").also { id ->
                if (subjectByName[subjectKey] == null) {
                    val entity = SubjectEntity(id = id, name = record.subjectName.ifBlank { "Other" })
                    subjectByName[subjectKey] = entity
                    remoteSubjects += entity
                }
            }
            val chapterIds = record.chapterNames.distinct().map { name ->
                val local = localChapters.firstOrNull {
                    it.subjectId == subjectId && it.name.equals(name, ignoreCase = true)
                }
                local?.id ?: stableNegativeId("chapter:${subjectId}:$name").also { chapterId ->
                    if (remoteChapters.none { it.id == chapterId }) {
                        remoteChapters += ChapterEntity(id = chapterId, subjectId = subjectId, name = name)
                    }
                }
            }
            chapterIds.forEach { remoteSessionChapters += SessionChapterEntity(sessionId, it) }
            record.tasks.forEachIndexed { index, task ->
                val taskId = stableNegativeId("task:${record.globalId}:$index")
                remoteSessionTasks += SessionTaskEntity(
                    id = stableNegativeId("session-task:${record.globalId}:$index"),
                    sessionId = sessionId,
                    taskId = taskId,
                    position = index,
                    completed = task.completed,
                    completedAt = task.completedAt,
                )
            }
            remoteSessions += StudySessionEntity(
                id = sessionId,
                subjectId = subjectId,
                subjectName = record.subjectName.ifBlank { "Other" },
                mode = enumValueOr(record.mode, SessionMode.OPEN_ENDED),
                plannedDurationMs = record.plannedDurationMs,
                startedAt = record.startedAt,
                endedAt = record.endedAt.takeIf { it > 0 } ?: record.startedAt,
                status = enumValueOr(record.status, SessionStatus.COMPLETED),
                strict = record.strict,
                description = record.description,
                breakMsCompleted = record.breakMsCompleted,
                breakStartedAt = record.breakStartedAt,
                breakEndsAt = record.breakEndsAt,
                shortBreaksUsed = record.shortBreaksUsed,
                longBreaksUsed = record.longBreaksUsed,
            )
        }

        StudyData(
            sessions = localSessions + remoteSessions,
            sessionChapters = localSessionChapters + remoteSessionChapters,
            sessionTasks = localSessionTasks + remoteSessionTasks,
            chapters = localChapters + remoteChapters.distinctBy { it.id },
            subjects = localSubjects + remoteSubjects.distinctBy { it.id },
            tasks = localTasks,
            remoteSessionIds = remoteIds,
        )
    }

    /** Exports completed sessions only; active session state is never copied between phones. */
    suspend fun exportSyncRecords(deviceId: String): List<SyncedSession> = withContext(Dispatchers.IO) {
        val chapters = db.chapterDao().getAll()
        sessionDao.getAll()
            .filter { it.status != SessionStatus.ACTIVE }
            .map { session ->
                val chapterIds = sessionDao.chapterIdsForSession(session.id).toSet()
                val chapterNames = chapters.filter { it.id in chapterIds }.map { it.name }
                val tasks = sessionDao.getSessionTasks(session.id).map { row ->
                    SyncedTask(
                        title = row.title,
                        completed = row.link.completed,
                        estimatedMinutes = row.estimatedMinutes,
                        completedAt = row.link.completedAt,
                    )
                }
                SyncedSession(
                    globalId = "$deviceId:${session.id}",
                    subjectName = session.subjectName,
                    mode = session.mode.name,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt ?: session.startedAt,
                    status = session.status.name,
                    plannedDurationMs = session.plannedDurationMs,
                    strict = session.strict,
                    description = session.description,
                    breakMsCompleted = session.breakMsCompleted,
                    breakStartedAt = session.breakStartedAt,
                    breakEndsAt = session.breakEndsAt,
                    shortBreaksUsed = session.shortBreaksUsed,
                    longBreaksUsed = session.longBreaksUsed,
                    chapterNames = chapterNames,
                    tasks = tasks,
                )
            }
    }

    fun observeSession(id: Long) = sessionDao.observeById(id)
    fun observeSessionTasks(id: Long) = sessionDao.observeSessionTasks(id)
    fun observeSummary(id: Long) = sessionDao.observeSummary(id)
    fun observeChapterIds(id: Long) = sessionDao.observeChapterIds(id)

    suspend fun saveSummary(sessionId: Long, text: String, source: com.focusflow.app.data.db.SummarySource) {
        sessionDao.upsertSummary(SessionSummaryEntity(sessionId = sessionId, text = text, source = source))
    }
}

private fun stableNegativeId(key: String): Long {
    val positive = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).mostSignificantBits and Long.MAX_VALUE
    return -(positive.coerceAtLeast(1L))
}

private inline fun <reified T : Enum<T>> enumValueOr(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
