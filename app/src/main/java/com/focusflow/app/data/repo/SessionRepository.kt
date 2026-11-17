package com.focusflow.app.data.repo

import androidx.room.withTransaction
import com.focusflow.app.data.db.BreakConfigurationEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.SessionChapterEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.db.SessionTaskEntity
import com.focusflow.app.data.db.SessionTaskRow
import com.focusflow.app.data.db.StudySessionEntity
import com.focusflow.app.data.db.SubtaskEntity
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.db.TaskStatus
import com.focusflow.app.domain.BreakConfig
import com.focusflow.app.domain.BreakCredits
import com.focusflow.app.domain.BreakRules
import com.focusflow.app.domain.ClockState
import com.focusflow.app.domain.SessionClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

fun StudySessionEntity.clock() = ClockState(
    startedAt = startedAt,
    endedAt = endedAt,
    plannedMs = plannedDurationMs,
    breakMsCompleted = breakMsCompleted,
    breakStartedAt = breakStartedAt,
    breakEndsAt = breakEndsAt,
)

fun StudySessionEntity.withClock(c: ClockState) = copy(
    endedAt = c.endedAt,
    breakMsCompleted = c.breakMsCompleted,
    breakStartedAt = c.breakStartedAt,
    breakEndsAt = c.breakEndsAt,
)

data class DraftTask(
    val title: String,
    val estimatedMinutes: Int? = null,
    val origin: TaskOrigin = TaskOrigin.MANUAL,
    val chapterId: Long? = null,
    /** Set when the task already exists (e.g. resumed unfinished work) so it is linked, not copied. */
    val existingTaskId: Long? = null,
    val subtasks: List<String> = emptyList(),
)

data class SessionDraft(
    val subjectId: Long?,
    val subjectName: String,
    val chapterIds: Set<Long>,
    val description: String,
    val mode: SessionMode,
    val plannedMinutes: Int?,
    val strict: Boolean,
    val presetId: Long?,
    val tasks: List<DraftTask>,
)

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult
    data object AlreadyActive : StartResult
}

enum class BreakKind { SHORT, LONG }

/** Everything the UI and the foreground service need to know about the running session. */
data class ActiveSession(
    val session: StudySessionEntity,
    val tasks: List<SessionTaskRow>,
    val chapterIds: List<Long>,
    val subtasks: List<SubtaskEntity>,
    val breakConfig: BreakConfig,
) {
    val completedCount: Int get() = tasks.count { it.link.completed }
    val allTasksDone: Boolean get() = tasks.all { it.link.completed }
    fun credits(): BreakCredits =
        BreakRules.available(completedCount, session.shortBreaksUsed, session.longBreaksUsed, breakConfig)
}

class SessionRepository(
    private val db: FocusFlowDatabase,
    private val tasks: TaskRepository,
) {
    private val sessionDao = db.sessionDao()
    private val taskDao = db.taskDao()
    private val presetDao = db.presetDao()
    private val ruleDao = db.appRuleDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActive(): Flow<ActiveSession?> = sessionDao.observeActive().flatMapLatest { s ->
        if (s == null) {
            flowOf(null)
        } else {
            combine(
                sessionDao.observeSessionTasks(s.id),
                sessionDao.observeChapterIds(s.id),
                sessionDao.observeSubtasksForSession(s.id),
                ruleDao.observeGlobalBreakConfig(),
            ) { rows, chapterIds, subs, globalCfg ->
                ActiveSession(s, rows, chapterIds, subs, resolveBreakConfig(s, globalCfg))
            }
        }
    }

    /** Lightweight stream of just the running session row (no tasks), for the blocking engine. */
    fun observeActiveSession(): Flow<StudySessionEntity?> = sessionDao.observeActive()

    suspend fun hasActive(): Boolean = sessionDao.getActive() != null

    private suspend fun resolveBreakConfig(s: StudySessionEntity, global: BreakConfigurationEntity?): BreakConfig {
        val preset = s.presetId?.let { ruleDao.getBreakConfigForPreset(it) }
        return (preset ?: global)?.toConfig() ?: BreakConfig()
    }

    /** Creates the session, its chapter links and its tasks atomically. Only one session may be active. */
    suspend fun start(draft: SessionDraft, now: Long = System.currentTimeMillis()): StartResult =
        db.withTransaction {
            if (sessionDao.getActive() != null) return@withTransaction StartResult.AlreadyActive

            val sessionId = sessionDao.insert(
                StudySessionEntity(
                    subjectId = draft.subjectId,
                    subjectName = draft.subjectName,
                    mode = draft.mode,
                    plannedDurationMs = if (draft.mode == SessionMode.TIMED) {
                        (draft.plannedMinutes ?: 25).coerceAtLeast(1) * 60_000L
                    } else null,
                    startedAt = now,
                    strict = draft.strict,
                    description = draft.description.trim(),
                    presetId = draft.presetId,
                ),
            )

            val singleChapter = draft.chapterIds.singleOrNull()
            val linkedTaskIds = mutableSetOf<Long>()
            val chapterIds = draft.chapterIds.toMutableSet()
            var position = 0

            for (t in draft.tasks) {
                val taskId: Long
                var isNew = false
                if (t.existingTaskId != null) {
                    val existing = taskDao.getById(t.existingTaskId) ?: continue
                    if (existing.status != TaskStatus.OPEN) continue
                    taskId = existing.id
                    existing.chapterId?.let { chapterIds += it }
                } else {
                    when (
                        val r = tasks.add(
                            title = t.title,
                            subjectId = draft.subjectId,
                            chapterId = t.chapterId ?: singleChapter,
                            estimatedMinutes = t.estimatedMinutes,
                            origin = t.origin,
                        )
                    ) {
                        is AddTaskResult.Added -> { taskId = r.taskId; isNew = true }
                        is AddTaskResult.AlreadyOpen -> taskId = r.taskId
                        AddTaskResult.Blank -> continue
                    }
                }
                if (!linkedTaskIds.add(taskId)) continue // never link the same task twice
                sessionDao.insertSessionTask(
                    SessionTaskEntity(sessionId = sessionId, taskId = taskId, position = position++),
                )
                if (isNew) {
                    t.subtasks.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, title ->
                        taskDao.insertSubtask(SubtaskEntity(taskId = taskId, title = title, position = i))
                    }
                }
            }

            sessionDao.insertSessionChapters(chapterIds.map { SessionChapterEntity(sessionId, it) })
            StartResult.Started(sessionId)
        }

    /** Adds a task to a running session (new, or an existing open one) without duplicating it. */
    suspend fun addTask(sessionId: Long, title: String, estimatedMinutes: Int? = null): Boolean =
        db.withTransaction {
            val s = sessionDao.getById(sessionId) ?: return@withTransaction false
            val chapters = sessionDao.chapterIdsForSession(sessionId)
            val taskId = when (
                val r = tasks.add(title, s.subjectId, chapters.singleOrNull(), estimatedMinutes)
            ) {
                is AddTaskResult.Added -> r.taskId
                is AddTaskResult.AlreadyOpen -> r.taskId
                AddTaskResult.Blank -> return@withTransaction false
            }
            val position = sessionDao.nextPosition(sessionId)
            sessionDao.insertSessionTask(SessionTaskEntity(sessionId = sessionId, taskId = taskId, position = position)) != -1L
        }

    suspend fun removeTask(sessionTaskId: Long) = sessionDao.deleteSessionTask(sessionTaskId)

    /** Called only from an explicit user action (checkbox). Nothing completes tasks automatically. */
    suspend fun setTaskCompleted(
        sessionTaskId: Long,
        taskId: Long,
        completed: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        db.withTransaction {
            sessionDao.setSessionTaskCompleted(sessionTaskId, completed, if (completed) now else null)
            taskDao.setStatus(taskId, if (completed) TaskStatus.DONE else TaskStatus.OPEN, if (completed) now else null)
        }
    }

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean) = taskDao.setSubtaskDone(subtaskId, done)

    /** Starts an earned break. Returns false if none is available or one is already running. */
    suspend fun startBreak(sessionId: Long, kind: BreakKind, now: Long = System.currentTimeMillis()): Boolean =
        db.withTransaction {
            val s = sessionDao.getById(sessionId) ?: return@withTransaction false
            if (s.status != SessionStatus.ACTIVE) return@withTransaction false
            val cfg = resolveBreakConfig(s, ruleDao.getGlobalBreakConfig())
            val completed = sessionDao.getSessionTasks(sessionId).count { it.link.completed }
            val settled = SessionClock.settleBreak(s.clock(), now)
            if (SessionClock.isOnBreak(settled, now)) return@withTransaction false
            val credits = BreakRules.available(completed, s.shortBreaksUsed, s.longBreaksUsed, cfg)
            val minutes = when (kind) {
                BreakKind.SHORT -> if (credits.shortAvailable > 0) cfg.shortBreakMinutes else return@withTransaction false
                BreakKind.LONG -> if (credits.longAvailable > 0) cfg.longBreakMinutes else return@withTransaction false
            }
            val started = SessionClock.startBreak(settled, now, minutes * 60_000L)
            sessionDao.update(
                s.withClock(started).copy(
                    shortBreaksUsed = s.shortBreaksUsed + if (kind == BreakKind.SHORT) 1 else 0,
                    longBreaksUsed = s.longBreaksUsed + if (kind == BreakKind.LONG) 1 else 0,
                ),
            )
            true
        }

    suspend fun endBreakEarly(sessionId: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val s = sessionDao.getById(sessionId) ?: return@withTransaction
            sessionDao.update(s.withClock(SessionClock.endBreakEarly(s.clock(), now)))
        }
    }

    /**
     * Housekeeping run by the foreground service (and the UI): folds a finished break into the
     * stored totals, and completes a timed session whose focus time has run out.
     */
    suspend fun tick(sessionId: Long, now: Long = System.currentTimeMillis()): SessionStatus? =
        db.withTransaction {
            val s = sessionDao.getById(sessionId) ?: return@withTransaction null
            if (s.status != SessionStatus.ACTIVE) return@withTransaction s.status
            var c = SessionClock.settleBreak(s.clock(), now)
            if (c != s.clock()) sessionDao.update(s.withClock(c))
            if (SessionClock.isTimedFinished(c, now)) {
                // Record the moment focus time ran out, not "now", in case we were asleep.
                val overshoot = SessionClock.focusElapsedMs(c, now) - (c.plannedMs ?: 0L)
                val finishAt = (now - overshoot.coerceAtLeast(0)).coerceAtLeast(s.startedAt)
                finish(sessionId, SessionStatus.COMPLETED, finishAt)
                SessionStatus.COMPLETED
            } else {
                SessionStatus.ACTIVE
            }
        }

    suspend fun endSession(
        sessionId: Long,
        status: SessionStatus,
        now: Long = System.currentTimeMillis(),
    ) {
        db.withTransaction { finish(sessionId, status, now) }
    }

    private suspend fun finish(sessionId: Long, status: SessionStatus, endAt: Long) {
        val s = sessionDao.getById(sessionId) ?: return
        if (s.status != SessionStatus.ACTIVE) return
        var c = SessionClock.settleBreak(s.clock(), endAt)
        if (SessionClock.isOnBreak(c, endAt)) c = SessionClock.endBreakEarly(c, endAt)
        sessionDao.update(s.withClock(c.copy(endedAt = endAt)).copy(status = status))
        // Tasks the user did not finish stay OPEN in the task pool: nothing is lost or auto-completed.
    }
}
