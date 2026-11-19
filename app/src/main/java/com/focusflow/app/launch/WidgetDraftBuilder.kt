package com.focusflow.app.launch

import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.db.TaskStatus
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.DraftTask
import com.focusflow.app.data.repo.PresetDraft
import com.focusflow.app.data.repo.SessionDraft

/** Everything [WidgetDraftBuilder] needs, already loaded, so building is a pure function. */
data class WidgetContext(
    val subjectName: String?,
    /** Tasks referenced by the widget, in the order the widget lists them. Missing ones are simply absent. */
    val tasks: List<TaskEntity> = emptyList(),
    val preset: PresetDraft? = null,
    val presetSubjectName: String? = null,
)

sealed interface WidgetPlan {
    data class Start(val draft: SessionDraft, val notes: List<String> = emptyList()) : WidgetPlan
    data class Navigate(val action: WidgetAction) : WidgetPlan
    data class Invalid(val reason: String) : WidgetPlan
}

/**
 * Turns a widget's saved configuration into the [SessionDraft] that the one shared start path
 * ([SessionLauncher]) consumes. Widgets never talk to the session tables themselves.
 */
object WidgetDraftBuilder {
    const val GENERIC_NAME = "Focus session"
    const val HOMEWORK_NAME = "Homework"

    fun plan(w: DashboardWidgetEntity, ctx: WidgetContext): WidgetPlan {
        if (w.type == WidgetType.CUSTOM && w.action != WidgetAction.START_SESSION) {
            return WidgetPlan.Navigate(w.action)
        }
        if (w.type == WidgetType.PRESET) return fromPreset(w, ctx)

        val notes = mutableListOf<String>()
        val mode = when (w.type) {
            WidgetType.OPEN_ENDED -> SessionMode.OPEN_ENDED
            WidgetType.TIMER -> SessionMode.TIMED
            else -> w.mode
        }
        val name = ctx.subjectName ?: if (w.type == WidgetType.HOMEWORK) HOMEWORK_NAME else GENERIC_NAME
        val subjectId = if (ctx.subjectName != null) w.subjectId else null
        if (w.subjectId != null && ctx.subjectName == null) notes += "The widget's subject no longer exists; started without one."

        val usable = ctx.tasks.filter { it.status == TaskStatus.OPEN }
        if (usable.size < w.taskIdList().size) notes += "Some saved tasks are finished or deleted and were skipped."

        return WidgetPlan.Start(
            SessionDraft(
                subjectId = subjectId,
                subjectName = name,
                chapterIds = if (subjectId != null) w.chapterIdSet() else emptySet(),
                description = "",
                mode = mode,
                plannedMinutes = if (mode == SessionMode.TIMED) w.durationMinutes else null,
                strict = w.strict,
                presetId = null,
                tasks = usable.map { DraftTask(it.title, it.estimatedMinutes, it.origin, it.chapterId, existingTaskId = it.id) },
            ),
            notes,
        )
    }

    private fun fromPreset(w: DashboardWidgetEntity, ctx: WidgetContext): WidgetPlan {
        val p = ctx.preset ?: return WidgetPlan.Invalid("The preset for \"${w.title}\" no longer exists. Edit the widget and choose another.")
        val name = ctx.presetSubjectName ?: if (p.subjectId != null) GENERIC_NAME else GENERIC_NAME
        return WidgetPlan.Start(
            SessionDraft(
                subjectId = p.subjectId,
                subjectName = name,
                chapterIds = p.chapterIds,
                description = "",
                mode = p.mode,
                plannedMinutes = if (p.mode == SessionMode.TIMED) p.durationMinutes else null,
                strict = p.strict,
                presetId = p.id,
                tasks = p.tasks,
                floatingAssistant = p.floatingAssistant,
                blockShorts = p.blockYoutubeShorts,
            ),
        )
    }
}
