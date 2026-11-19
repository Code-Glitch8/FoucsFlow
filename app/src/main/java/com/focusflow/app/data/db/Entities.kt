package com.focusflow.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All timestamps are epoch milliseconds (UTC), written via System.currentTimeMillis().

enum class SessionMode { TIMED, OPEN_ENDED }

enum class SessionStatus { ACTIVE, COMPLETED, ENDED_EARLY, EMERGENCY_EXIT }

enum class TaskStatus { OPEN, DONE, DISMISSED }

/** Where a task came from, so AI output and fallback output are never confused. */
enum class TaskOrigin { MANUAL, ON_DEVICE_AI, RULE_BASED, EXTERNAL_AI }

enum class AppCategory { GAME, SOCIAL, ENTERTAINMENT, CUSTOM }

enum class SummarySource { ON_DEVICE_AI, TEMPLATE, EXTERNAL_AI }

@Entity(tableName = "subjects", indices = [Index(value = ["name"], unique = true)])
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["subjectId", "name"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Optional self-reported confidence 1..5. Kept separate from time and task metrics. */
    val selfConfidence: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("subjectId"), Index("chapterId"), Index("status"), Index("dedupeKey")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val estimatedMinutes: Int? = null,
    val origin: TaskOrigin = TaskOrigin.MANUAL,
    val status: TaskStatus = TaskStatus.OPEN,
    /** Normalised title + chapter, used to stop the same open task being created twice. */
    val dedupeKey: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("subjectId"), Index("status"), Index("startedAt")],
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    /** Snapshot so history stays readable if the subject is later deleted. */
    val subjectName: String,
    val mode: SessionMode,
    val plannedDurationMs: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val strict: Boolean = true,
    val description: String = "",
    val presetId: Long? = null,
    /** Total length of finished breaks. Break time is not counted as focus time. */
    val breakMsCompleted: Long = 0,
    val breakStartedAt: Long? = null,
    val breakEndsAt: Long? = null,
    val shortBreaksUsed: Int = 0,
    val longBreaksUsed: Int = 0,
    /** Per-session switches copied from the preset/widget that started it (schema v2). */
    val floatingAssistant: Boolean = true,
    val blockShorts: Boolean = true,
)

@Entity(
    tableName = "session_chapters",
    primaryKeys = ["sessionId", "chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class SessionChapterEntity(
    val sessionId: Long,
    val chapterId: Long,
)

/**
 * A task attached to a session. The same [TaskEntity] can be linked to several sessions
 * (that is how unfinished work moves forward without being duplicated).
 */
@Entity(
    tableName = "session_tasks",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "taskId"], unique = true), Index("taskId")],
)
data class SessionTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long,
    val position: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "study_presets",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("subjectId"), Index(value = ["name"], unique = true)],
)
data class StudyPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val subjectId: Long? = null,
    val mode: SessionMode = SessionMode.TIMED,
    val durationMinutes: Int = 25,
    val strict: Boolean = true,
    val blockYoutubeShorts: Boolean = true,
    val floatingAssistant: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "preset_chapters",
    primaryKeys = ["presetId", "chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = StudyPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class PresetChapterEntity(
    val presetId: Long,
    val chapterId: Long,
)

/** presetId == null means the global default list used when no preset overrides it. */
@Entity(
    tableName = "blocked_applications",
    foreignKeys = [
        ForeignKey(
            entity = StudyPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "packageName"], unique = true)],
)
data class BlockedApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long? = null,
    val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.CUSTOM,
    /** If true the app stays blocked while a break is running. */
    val blockedDuringBreak: Boolean = false,
)

@Entity(
    tableName = "allowed_applications",
    foreignKeys = [
        ForeignKey(
            entity = StudyPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "packageName"], unique = true)],
)
data class AllowedApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long? = null,
    val packageName: String,
    val label: String,
)

@Entity(
    tableName = "break_configurations",
    foreignKeys = [
        ForeignKey(
            entity = StudyPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId"], unique = true)],
)
data class BreakConfigurationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long? = null,
    val enabled: Boolean = true,
    val tasksForShortBreak: Int = 1,
    val shortBreakMinutes: Int = 5,
    val tasksForLongBreak: Int = 3,
    val longBreakMinutes: Int = 15,
)

@Entity(
    tableName = "session_summaries",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"], unique = true)],
)
data class SessionSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val text: String,
    val source: SummarySource,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A task template stored in a preset. Applied through the normal de-duplicating task path when the preset starts. */
@Entity(
    tableName = "preset_tasks",
    foreignKeys = [
        ForeignKey(
            entity = StudyPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class PresetTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val title: String,
    val estimatedMinutes: Int? = null,
    val chapterId: Long? = null,
    /** Subtask titles, one per line. */
    val subtasks: String = "",
    val position: Int = 0,
)

enum class WidgetType { TIMER, OPEN_ENDED, HOMEWORK, SUBJECT, PRESET, CUSTOM }

enum class WidgetSize { SMALL, LARGE }

/** What a CUSTOM widget does when tapped. Every other widget type always starts a session. */
enum class WidgetAction { START_SESSION, OPEN_PREPARE, OPEN_TASKS, OPEN_HISTORY, OPEN_STATISTICS }

/**
 * A configurable dashboard widget. The id is a stable UUID so reordering, editing and backups
 * never change a widget's identity. Chapter and task lists are comma separated ids.
 */
@Entity(tableName = "dashboard_widgets", indices = [Index("position")])
data class DashboardWidgetEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val type: WidgetType,
    val title: String,
    val icon: String = "timer",
    val colorKey: String = "indigo",
    val size: WidgetSize = WidgetSize.SMALL,
    val position: Int = 0,
    val visible: Boolean = true,
    val mode: SessionMode = SessionMode.TIMED,
    val durationMinutes: Int = 25,
    val strict: Boolean = true,
    val subjectId: Long? = null,
    val chapterIds: String = "",
    val taskIds: String = "",
    val presetId: Long? = null,
    val action: WidgetAction = WidgetAction.START_SESSION,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun chapterIdSet(): Set<Long> = parseIds(chapterIds)
    fun taskIdList(): List<Long> = parseIds(taskIds).toList()

    companion object {
        fun parseIds(csv: String): Set<Long> =
            csv.split(',').mapNotNull { it.trim().toLongOrNull() }.toCollection(LinkedHashSet())

        fun joinIds(ids: Collection<Long>): String = ids.distinct().joinToString(",")
    }
}
