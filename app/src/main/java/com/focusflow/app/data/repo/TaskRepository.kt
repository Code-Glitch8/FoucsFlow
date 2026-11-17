package com.focusflow.app.data.repo

import com.focusflow.app.data.db.TaskDao
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.db.TaskStatus
import com.focusflow.app.domain.TaskNormalizer
import kotlinx.coroutines.flow.Flow

sealed interface AddTaskResult {
    data class Added(val taskId: Long) : AddTaskResult

    /** An identical open task already exists; it is reused instead of creating a duplicate. */
    data class AlreadyOpen(val taskId: Long) : AddTaskResult

    data object Blank : AddTaskResult
}

class TaskRepository(private val dao: TaskDao) {
    val openTasks: Flow<List<TaskEntity>> = dao.observeOpen()

    fun recentlyDone(limit: Int = 20): Flow<List<TaskEntity>> = dao.observeRecentlyDone(limit)

    suspend fun add(
        title: String,
        subjectId: Long?,
        chapterId: Long?,
        estimatedMinutes: Int? = null,
        origin: TaskOrigin = TaskOrigin.MANUAL,
    ): AddTaskResult {
        val clean = TaskNormalizer.clean(title)
        if (clean.isEmpty()) return AddTaskResult.Blank
        val key = TaskNormalizer.dedupeKey(clean, chapterId)
        dao.findOpenByKey(key)?.let { return AddTaskResult.AlreadyOpen(it.id) }
        val id = dao.insert(
            TaskEntity(
                title = clean,
                subjectId = subjectId,
                chapterId = chapterId,
                estimatedMinutes = estimatedMinutes?.takeIf { it > 0 },
                origin = origin,
                dedupeKey = key,
            ),
        )
        return AddTaskResult.Added(id)
    }

    suspend fun get(id: Long): TaskEntity? = dao.getById(id)

    /** Only ever called from an explicit user action; nothing completes tasks automatically. */
    suspend fun markDone(id: Long, now: Long = System.currentTimeMillis()) =
        dao.setStatus(id, TaskStatus.DONE, now)

    suspend fun reopen(id: Long) = dao.setStatus(id, TaskStatus.OPEN, null)

    suspend fun dismiss(id: Long) = dao.setStatus(id, TaskStatus.DISMISSED, null)

    suspend fun delete(task: TaskEntity) = dao.delete(task)
}
