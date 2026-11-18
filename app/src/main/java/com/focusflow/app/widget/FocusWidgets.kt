package com.focusflow.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Button
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.MainActivity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.SessionDraft
import com.focusflow.app.data.repo.StartResult
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.service.FocusSessionService
import kotlinx.coroutines.flow.first

private val PresetIdKey = ActionParameters.Key<Long>("preset_id")

class FocusQuickStartWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FocusFlowApp
        val firstPreset = app.container.presets.allPresets.first().firstOrNull()
        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("FOCUS FLOW")
                Text("One tap to begin your saved study setup.")
                Spacer(GlanceModifier.height(8.dp))
                if (firstPreset == null) {
                    Button(text = "Create a focus profile", onClick = actionStartActivity<MainActivity>())
                } else {
                    Button(
                        text = "Start ${firstPreset.name}",
                        onClick = actionRunCallback<StartPresetAction>(
                            actionParametersOf(PresetIdKey to firstPreset.id),
                        ),
                    )
                }
            }
        }
    }
}

class FocusProfilesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FocusFlowApp
        val profiles = app.container.presets.allPresets.first().take(4)
        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("YOUR FOCUS PROFILES")
                Spacer(GlanceModifier.height(6.dp))
                if (profiles.isEmpty()) {
                    Text("Create a saved profile in Focus Flow first.")
                    Button(text = "Open Focus Flow", onClick = actionStartActivity<MainActivity>())
                } else {
                    profiles.forEach { profile ->
                        Button(
                            text = "${profile.name} · " + if (profile.mode == SessionMode.OPEN_ENDED) "Open-ended" else "${profile.durationMinutes} min",
                            onClick = actionRunCallback<StartPresetAction>(
                                actionParametersOf(PresetIdKey to profile.id),
                            ),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

class FocusAnalyticsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as FocusFlowApp
        val now = System.currentTimeMillis()
        val weekStart = now - 7L * 24 * 60 * 60 * 1000
        val sessions = app.container.database.sessionDao().getAll()
        val week = sessions.filter { it.startedAt >= weekStart }
        val focusMs = week.sumOf { SessionClock.focusElapsedMs(it.clock(), now) }
        val active = sessions.firstOrNull { it.status.name == "ACTIVE" }
        val minutes = (focusMs / 60_000L).coerceAtLeast(0)
        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("STUDY ANALYTICS")
                Spacer(GlanceModifier.height(6.dp))
                Text("Last 7 days")
                Text("${minutes / 60}h ${minutes % 60}m focused")
                Text("${week.size} sessions started")
                Text(if (active == null) "No session running" else "In focus: ${active.subjectName}")
                Spacer(GlanceModifier.height(6.dp))
                Button(text = "Open statistics", onClick = actionStartActivity<MainActivity>())
            }
        }
    }
}

class StartPresetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val presetId = parameters[PresetIdKey] ?: return
        val app = context.applicationContext as FocusFlowApp
        val profile = app.container.presets.load(presetId) ?: return
        val subjectId = profile.subjectId
        val subject = subjectId?.let { app.container.database.subjectDao().getById(it) }
        if (subject == null) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        when (
            app.container.sessions.start(
                SessionDraft(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    chapterIds = profile.chapterIds,
                    description = "",
                    mode = profile.mode,
                    plannedMinutes = if (profile.mode == SessionMode.TIMED) profile.durationMinutes else null,
                    strict = profile.strict,
                    presetId = profile.id,
                    tasks = emptyList(),
                ),
            )
        ) {
            is StartResult.Started -> {
                FocusSessionService.start(context)
                if (profile.launchSnapGpt) {
                    val snapIntent = context.packageManager.getLaunchIntentForPackage("com.snapgpt.assistant")
                    if (snapIntent != null) {
                        snapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(snapIntent)
                    }
                }
            }
            StartResult.AlreadyActive -> {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        FocusQuickStartWidget().updateAll(context)
        FocusProfilesWidget().updateAll(context)
        FocusAnalyticsWidget().updateAll(context)
    }
}

class FocusQuickStartReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusQuickStartWidget()
}

class FocusProfilesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusProfilesWidget()
}

class FocusAnalyticsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusAnalyticsWidget()
}
