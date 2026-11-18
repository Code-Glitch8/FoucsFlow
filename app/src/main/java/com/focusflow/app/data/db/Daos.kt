package com.focusflow.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY builtIn DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY builtIn DESC, name COLLATE NOCASE")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE subjectId = :subjectId ORDER BY name COLLATE NOCASE")
    fun observeForSubject(subjectId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(chapter: ChapterEntity): Long

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Delete
    suspend fun delete(chapter: ChapterEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecentlyDone(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status = 'OPEN' AND dedupeKey = :key LIMIT 1")
    suspend fun findOpenByKey(key: String): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: TaskStatus, completedAt: Long?)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    fun observeSubtasks(taskId: Long): Flow<List<SubtaskEntity>>

    @Insert
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)
}

/** A session task joined with the fields of its underlying task needed for display. */
data class SessionTaskRow(
    @Embedded val link: SessionTaskEntity,
    val title: String,
    val estimatedMinutes: Int?,
    val chapterId: Long?,
    val subjectId: Long?,
    val origin: TaskOrigin,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: StudySessionEntity): Long

    @Update
    suspend fun update(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun getById(id: Long): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<StudySessionEntity?>

    @Query("SELECT * FROM study_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<StudySessionEntity?>

    @Query("SELECT * FROM study_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE status != 'ACTIVE' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<StudySessionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionChapters(links: List<SessionChapterEntity>)

    @Query("SELECT * FROM session_chapters")
    fun observeAllSessionChapters(): Flow<List<SessionChapterEntity>>

    @Query("SELECT * FROM session_chapters")
    suspend fun getAllSessionChapters(): List<SessionChapterEntity>

    @Query("SELECT chapterId FROM session_chapters WHERE sessionId = :sessionId")
    suspend fun chapterIdsForSession(sessionId: Long): List<Long>

    @Query("SELECT chapterId FROM session_chapters WHERE sessionId = :sessionId")
    fun observeChapterIds(sessionId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionTask(link: SessionTaskEntity): Long

    @Query("UPDATE session_tasks SET completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setSessionTaskCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("DELETE FROM session_tasks WHERE id = :id")
    suspend fun deleteSessionTask(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM session_tasks WHERE sessionId = :sessionId")
    suspend fun nextPosition(sessionId: Long): Int

    @Query(
        """
        SELECT st.*, t.title AS title, t.estimatedMinutes AS estimatedMinutes,
               t.chapterId AS chapterId, t.subjectId AS subjectId, t.origin AS origin
        FROM session_tasks st JOIN tasks t ON t.id = st.taskId
        WHERE st.sessionId = :sessionId ORDER BY st.position, st.id
        """,
    )
    fun observeSessionTasks(sessionId: Long): Flow<List<SessionTaskRow>>

    @Query(
        """
        SELECT st.*, t.title AS title, t.estimatedMinutes AS estimatedMinutes,
               t.chapterId AS chapterId, t.subjectId AS subjectId, t.origin AS origin
        FROM session_tasks st JOIN tasks t ON t.id = st.taskId
        WHERE st.sessionId = :sessionId ORDER BY st.position, st.id
        """,
    )
    suspend fun getSessionTasks(sessionId: Long): List<SessionTaskRow>

    @Query(
        """
        SELECT sub.* FROM subtasks sub JOIN session_tasks st ON st.taskId = sub.taskId
        WHERE st.sessionId = :sessionId ORDER BY sub.position, sub.id
        """,
    )
    fun observeSubtasksForSession(sessionId: Long): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM session_tasks")
    fun observeAllSessionTasks(): Flow<List<SessionTaskEntity>>

    @Query("SELECT * FROM session_tasks")
    suspend fun getAllSessionTasks(): List<SessionTaskEntity>

    @Query("SELECT * FROM session_summaries WHERE sessionId = :sessionId")
    fun observeSummary(sessionId: Long): Flow<SessionSummaryEntity?>

    @Query("SELECT * FROM session_summaries")
    suspend fun getAllSummaries(): List<SessionSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: SessionSummaryEntity): Long
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM study_presets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StudyPresetEntity>>

    @Query("SELECT * FROM study_presets ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<StudyPresetEntity>

    @Query("SELECT * FROM study_presets WHERE id = :id")
    suspend fun getById(id: Long): StudyPresetEntity?

    @Insert
    suspend fun insert(preset: StudyPresetEntity): Long

    @Update
    suspend fun update(preset: StudyPresetEntity)

    @Query("DELETE FROM study_presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT chapterId FROM preset_chapters WHERE presetId = :presetId")
    suspend fun chapterIds(presetId: Long): List<Long>

    @Query("DELETE FROM preset_chapters WHERE presetId = :presetId")
    suspend fun clearChapters(presetId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapters(links: List<PresetChapterEntity>)

    @Query("SELECT * FROM preset_chapters")
    suspend fun getAllPresetChapters(): List<PresetChapterEntity>
}

@Dao
interface AppRuleDao {
    // presetId IS :presetId does not work for NULL in a parameter; two explicit forms are used.
    @Query("SELECT * FROM blocked_applications WHERE presetId IS NULL ORDER BY label COLLATE NOCASE")
    fun observeGlobalBlocked(): Flow<List<BlockedApplicationEntity>>

    @Query("SELECT * FROM blocked_applications WHERE presetId IS NULL")
    suspend fun getGlobalBlocked(): List<BlockedApplicationEntity>

    @Query("SELECT * FROM blocked_applications WHERE presetId = :presetId")
    suspend fun getBlockedForPreset(presetId: Long): List<BlockedApplicationEntity>

    @Query("SELECT * FROM blocked_applications")
    suspend fun getAllBlocked(): List<BlockedApplicationEntity>

    @Query("DELETE FROM blocked_applications WHERE presetId IS NULL")
    suspend fun clearGlobalBlocked()

    @Query("DELETE FROM blocked_applications WHERE presetId = :presetId")
    suspend fun clearBlockedForPreset(presetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocked(rules: List<BlockedApplicationEntity>)

    @Query("DELETE FROM blocked_applications WHERE presetId IS NULL AND packageName = :packageName")
    suspend fun deleteGlobalBlocked(packageName: String)

    @Query("DELETE FROM allowed_applications WHERE presetId IS NULL AND packageName = :packageName")
    suspend fun deleteGlobalAllowed(packageName: String)

    @Query("SELECT * FROM allowed_applications WHERE presetId IS NULL ORDER BY label COLLATE NOCASE")
    fun observeGlobalAllowed(): Flow<List<AllowedApplicationEntity>>

    @Query("SELECT * FROM allowed_applications WHERE presetId IS NULL")
    suspend fun getGlobalAllowed(): List<AllowedApplicationEntity>

    @Query("SELECT * FROM allowed_applications WHERE presetId = :presetId")
    suspend fun getAllowedForPreset(presetId: Long): List<AllowedApplicationEntity>

    @Query("SELECT * FROM allowed_applications")
    suspend fun getAllAllowed(): List<AllowedApplicationEntity>

    @Query("DELETE FROM allowed_applications WHERE presetId IS NULL")
    suspend fun clearGlobalAllowed()

    @Query("DELETE FROM allowed_applications WHERE presetId = :presetId")
    suspend fun clearAllowedForPreset(presetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllowed(rules: List<AllowedApplicationEntity>)

    @Query("SELECT * FROM break_configurations WHERE presetId IS NULL LIMIT 1")
    fun observeGlobalBreakConfig(): Flow<BreakConfigurationEntity?>

    @Query("SELECT * FROM break_configurations WHERE presetId IS NULL LIMIT 1")
    suspend fun getGlobalBreakConfig(): BreakConfigurationEntity?

    @Query("SELECT * FROM break_configurations WHERE presetId = :presetId LIMIT 1")
    suspend fun getBreakConfigForPreset(presetId: Long): BreakConfigurationEntity?

    @Query("SELECT * FROM break_configurations")
    suspend fun getAllBreakConfigs(): List<BreakConfigurationEntity>

    @Query("DELETE FROM break_configurations WHERE presetId IS NULL")
    suspend fun clearGlobalBreakConfig()

    @Query("DELETE FROM break_configurations WHERE presetId = :presetId")
    suspend fun clearBreakConfigForPreset(presetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakConfig(config: BreakConfigurationEntity): Long
}
