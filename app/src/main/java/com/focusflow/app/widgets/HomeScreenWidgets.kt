
package com.focusflow.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.MainActivity
import com.focusflow.app.R
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.StudyPresetEntity
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.launch.LaunchOutcome
import com.focusflow.app.data.repo.WidgetSaveResult
import com.focusflow.app.domain.DayTotal
import com.focusflow.app.domain.StudyStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private object HomeWidgetScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

private fun presetWidgetRecordId(appWidgetId: Int) = "android-launcher-$appWidgetId"

/**
 * A real Android launcher widget. Each instance is configured with one saved FocusFlow preset;
 * tapping Start goes through the same SessionLauncher path as the in-app dashboard.
 */
class PresetFocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        HomeWidgetScope.io.launch {
            try {
                ids.forEach { updateWidget(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        HomeWidgetScope.io.launch {
            try {
                val container = (context.applicationContext as FocusFlowApp).container
                appWidgetIds.forEach { container.widgets.delete(presetWidgetRecordId(it)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun updateWidget(context: Context, appWidgetId: Int) {
            val app = context.applicationContext
            val container = (app as FocusFlowApp).container
            val record = container.widgets.get(presetWidgetRecordId(appWidgetId))
            val preset = record?.presetId?.let { container.presets.load(it) }
            val views = RemoteViews(context.packageName, R.layout.widget_focus_preset)

            if (preset == null) {
                views.setTextViewText(R.id.focus_widget_title, "Choose a focus preset")
                views.setTextViewText(R.id.focus_widget_details, "Tap here to select a saved setup")
                views.setTextViewText(R.id.focus_widget_start, "CHOOSE PRESET")
                val choose = Intent(context, PresetWidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                val choosePending = PendingIntent.getActivity(
                    context, 30_000 + appWidgetId, choose,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.focus_widget_root, choosePending)
                views.setOnClickPendingIntent(R.id.focus_widget_start, choosePending)
            } else {
                val details = buildString {
                    append(if (preset.mode == SessionMode.TIMED) "${preset.durationMinutes} min" else "Open-ended")
                    append(if (preset.strict) " · Strict" else " · Normal")
                }
                views.setTextViewText(R.id.focus_widget_title, preset.name)
                views.setTextViewText(R.id.focus_widget_details, details)
                views.setTextViewText(R.id.focus_widget_start, "START FOCUS")

                val start = Intent(context, PresetWidgetActionReceiver::class.java)
                    .setAction(PresetWidgetActionReceiver.ACTION_START_PRESET)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                val startPending = PendingIntent.getBroadcast(
                    context, 40_000 + appWidgetId, start,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val choose = Intent(context, PresetWidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                val choosePending = PendingIntent.getActivity(
                    context, 30_000 + appWidgetId, choose,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.focus_widget_root, choosePending)
                views.setOnClickPendingIntent(R.id.focus_widget_start, startPending)
            }
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }
}

/** Shows the user's saved presets when the launcher asks to add a FocusFlow preset widget. */
class PresetWidgetConfigureActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = (application as FocusFlowApp).container
        setContent {
            var presets by remember { mutableStateOf<List<StudyPresetEntity>?>(null) }
            var saving by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                presets = runCatching { container.presets.allPresets.first() }.getOrDefault(emptyList())
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Choose a saved focus setup", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "The widget will start this exact preset immediately when tapped.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        when {
                            presets == null -> Text("Loading saved presets…")
                            presets!!.isEmpty() -> Text(
                                "You haven't saved a preset yet. Create one in FocusFlow, then add this widget again.",
                            )
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(presets!!, key = { it.id }) { preset ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                if (preset.mode == SessionMode.TIMED) "${preset.durationMinutes} minutes" else "Open-ended",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Button(
                                                enabled = !saving,
                                                onClick = {
                                                    saving = true
                                                    lifecycleScope.launch {
                                                        val row = DashboardWidgetEntity(
                                                            id = presetWidgetRecordId(widgetId),
                                                            type = WidgetType.PRESET,
                                                            title = preset.name,
                                                            icon = "play",
                                                            colorKey = "teal",
                                                            visible = false,
                                                            presetId = preset.id,
                                                        )
                                                        when (container.widgets.save(row)) {
                                                            is WidgetSaveResult.Saved -> {
                                                                PresetFocusWidgetProvider.updateWidget(this@PresetWidgetConfigureActivity, widgetId)
                                                                setResult(
                                                                    RESULT_OK,
                                                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                                                )
                                                                finish()
                                                            }
                                                            WidgetSaveResult.BlankTitle -> {
                                                                saving = false
                                                                Toast.makeText(this@PresetWidgetConfigureActivity, "Please give the preset a name.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                            ) { Text("Use this preset") }
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                startActivity(Intent(this@PresetWidgetConfigureActivity, MainActivity::class.java))
                                finish()
                            },
                        ) { Text("Open FocusFlow") }
                    }
                }
            }
        }
    }
}

/** Handles the launcher widget's explicit Start tap. */
class PresetWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_START_PRESET) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        HomeWidgetScope.io.launch {
            try {
                val app = context.applicationContext as FocusFlowApp
                val result = app.container.launcher.runWidget(presetWidgetRecordId(widgetId))
                withContext(Dispatchers.Main) {
                    val message = when (result) {
                        is LaunchOutcome.Started -> "Focus session started"
                        is LaunchOutcome.AlreadyActive -> "A focus session is already running"
                        is LaunchOutcome.Invalid -> result.reason
                        is LaunchOutcome.Navigate -> "Open FocusFlow to continue"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not start this preset. Open FocusFlow and try again.", Toast.LENGTH_LONG).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START_PRESET = "com.focusflow.app.action.START_PRESET_WIDGET"
    }
}

/** Launcher widget with the same last-seven-days calculation as the Statistics screen. */
class WeeklyAnalyticsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        HomeWidgetScope.io.launch {
            try {
                ids.forEach { updateWidget(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val component = ComponentName(context, WeeklyAnalyticsWidgetProvider::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
            if (ids.isEmpty()) return
            HomeWidgetScope.io.launch { ids.forEach { updateWidget(context, it) } }
        }

        suspend fun updateWidget(context: Context, appWidgetId: Int) {
            val container = (context.applicationContext as FocusFlowApp).container
            val data = container.history.observe().first()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val daily = StudyStats.dailyFocus(data.facts(), today, 7, zone)
            val totalMs = daily.sumOf { it.focusMs }
            val views = RemoteViews(context.packageName, R.layout.widget_weekly_analytics)
            views.setTextViewText(R.id.analytics_widget_total, formatDuration(totalMs))
            views.setTextViewText(R.id.analytics_widget_caption, "FOCUS · LAST 7 DAYS")
            views.setImageViewBitmap(R.id.analytics_widget_chart, drawWeeklyChart(daily))
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openPending = PendingIntent.getActivity(
                context, 50_000 + appWidgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.analytics_widget_root, openPending)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun formatDuration(ms: Long): String {
            val totalMinutes = ms / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                totalMinutes > 0 -> "${totalMinutes} min"
                else -> "0 min"
            }
        }

        private fun drawWeeklyChart(days: List<DayTotal>): Bitmap {
            val width = 560
            val height = 230
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(56, 67, 82)
                strokeWidth = 2f
            }
            val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(93, 205, 177)
                style = Paint.Style.FILL
            }
            val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(190, 199, 211)
                textSize = 22f
                textAlign = Paint.Align.CENTER
            }
            val maxMs = max(60_000L, days.maxOfOrNull { it.focusMs } ?: 0L).toFloat()
            val left = 22f
            val right = width - 22f
            val top = 18f
            val bottom = 174f
            val plotHeight = bottom - top
            val groupWidth = (right - left) / 7f

            for (line in 0..2) {
                val y = top + plotHeight * line / 2f
                canvas.drawLine(left, y, right, y, guide)
            }
            days.forEachIndexed { index, day ->
                val center = left + groupWidth * (index + 0.5f)
                val barHeight = plotHeight * (day.focusMs.toFloat() / maxMs)
                val bar = RectF(center - 19f, bottom - barHeight, center + 19f, bottom)
                canvas.drawRoundRect(bar, 10f, 10f, bars)
                val dayLabel = day.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
                canvas.drawText(dayLabel, center, 211f, labels)
            }
            return bitmap
        }
    }
}
