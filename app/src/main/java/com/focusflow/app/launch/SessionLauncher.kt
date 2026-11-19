package com.focusflow.app.launch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionDraft
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.StartResult
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.repo.WidgetRepository
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.service.FocusSessionService
import com.focusflow.app.service.Permissions
import kotlinx.coroutines.flow.first

enum class Requirement { OVERLAY, NOTIFICATIONS }

sealed interface LaunchOutcome {
    data class Started(val sessionId: Long, val notes: List<String>) : LaunchOutcome
    data class AlreadyActive(val sessionId: Long?) : LaunchOutcome
    data class Invalid(val reason: String) : LaunchOutcome
    data class Navigate(val action: WidgetAction) : LaunchOutcome
}

/**
 * The single place that starts a session. The preparation screen, dashboard widgets and presets all
 * go through [start]: it creates the session in the database (one active session at most), starts the
 * foreground service (timer/notification, blocking and the floating assistant react to the stored
 * session) and performs the optional SnapGPT hand-off.
 */
class SessionLauncher(
    private val context: Context,
    private val sessions: SessionRepository,
    private val presets: PresetRepository,
    private val subjects: SubjectRepository,
    private val tasks: TaskRepository,
    private val widgets: WidgetRepository,
    private val settings: AppSettings,
) {
    /** Shared start path. [floatingOverride] = false starts this one session without the bubble. */
    suspend fun start(draft: SessionDraft, floatingOverride: Boolean? = null): LaunchOutcome {
        val effective = if (floatingOverride == false) draft.copy(floatingAssistant = false) else draft
        return when (val r = sessions.start(effective)) {
            StartResult.AlreadyActive -> LaunchOutcome.AlreadyActive(null)
            is StartResult.Started -> {
                FocusSessionService.start(context)
                handOffToSnapGpt(r.sessionId)
                LaunchOutcome.Started(r.sessionId, emptyList())
            }
        }
    }

    /** Runs a widget: loads its configuration, then uses [start]. Navigation widgets just report where to go. */
    suspend fun runWidget(widgetId: String, floatingOverride: Boolean? = null): LaunchOutcome {
        if (sessions.hasActive()) return LaunchOutcome.AlreadyActive(null)
        val w = widgets.get(widgetId) ?: return LaunchOutcome.Invalid("That widget no longer exists.")
        return when (val plan = WidgetDraftBuilder.plan(w, loadContext(w))) {
            is WidgetPlan.Navigate -> LaunchOutcome.Navigate(plan.action)
            is WidgetPlan.Invalid -> LaunchOutcome.Invalid(plan.reason)
            is WidgetPlan.Start -> when (val o = start(plan.draft, floatingOverride)) {
                is LaunchOutcome.Started -> o.copy(notes = plan.notes)
                else -> o
            }
        }
    }

    /** Permissions this widget's session would want that are missing right now. */
    suspend fun requirements(widgetId: String): List<Requirement> {
        val w = widgets.get(widgetId) ?: return emptyList()
        if (w.type == WidgetType.CUSTOM && w.action != WidgetAction.START_SESSION) return emptyList()
        val out = mutableListOf<Requirement>()
        val wantsBubble = settings.floatingEnabled.first() &&
            (if (w.type == WidgetType.PRESET) w.presetId?.let { presets.load(it)?.floatingAssistant } ?: true else true)
        if (wantsBubble && !Permissions.overlayGranted(context)) out += Requirement.OVERLAY
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) out += Requirement.NOTIFICATIONS
        return out
    }

    suspend fun hasActive(): Boolean = sessions.hasActive()

    private suspend fun loadContext(w: DashboardWidgetEntity): WidgetContext {
        val subjectList = subjects.subjects.first()
        val subjectName = w.subjectId?.let { id -> subjectList.firstOrNull { it.id == id }?.name }
        val taskList = w.taskIdList().mapNotNull { tasks.get(it) }
        val preset = if (w.type == WidgetType.PRESET) w.presetId?.let { presets.load(it) } else null
        val presetSubject = preset?.subjectId?.let { id -> subjectList.firstOrNull { it.id == id }?.name }
        return WidgetContext(subjectName, taskList, preset, presetSubject)
    }

    private suspend fun handOffToSnapGpt(sessionId: Long) {
        val enabled = settings.snapGptEnabled.first()
        if (!SnapGptLauncher.shouldLaunch(enabled, sessionId, settings.snapGptLastSession.first())) return
        settings.setSnapGptLastSession(sessionId)
        SnapGptLauncher.launch(context, settings.snapGptPackage.first())
    }
}
