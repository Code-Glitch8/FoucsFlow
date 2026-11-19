package com.focusflow.app.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetSize
import com.focusflow.app.data.db.WidgetType

/** Icon and colour choices are stored as short string keys so the database never depends on UI classes. */
object WidgetStyle {
    val icons: Map<String, ImageVector> = linkedMapOf(
        "timer" to Icons.Outlined.Timer,
        "play" to Icons.Outlined.PlayArrow,
        "book" to Icons.Outlined.MenuBook,
        "calculate" to Icons.Outlined.Calculate,
        "science" to Icons.Outlined.Science,
        "biotech" to Icons.Outlined.Biotech,
        "bolt" to Icons.Outlined.Bolt,
        "edit" to Icons.Outlined.Edit,
        "star" to Icons.Outlined.Star,
        "school" to Icons.Outlined.School,
        "alarm" to Icons.Outlined.Alarm,
        "bookmark" to Icons.Outlined.Bookmark,
        "psychology" to Icons.Outlined.Psychology,
        "code" to Icons.Outlined.Code,
        "language" to Icons.Outlined.Language,
        "trophy" to Icons.Outlined.EmojiEvents,
    )

    val colors: Map<String, Color> = linkedMapOf(
        "indigo" to Color(0xFF5C6BC0),
        "teal" to Color(0xFF26A69A),
        "amber" to Color(0xFFFFB300),
        "rose" to Color(0xFFEC407A),
        "green" to Color(0xFF66BB6A),
        "slate" to Color(0xFF78909C),
    )

    fun icon(key: String): ImageVector = icons[key] ?: Icons.Outlined.Timer
    fun color(key: String): Color = colors[key] ?: colors.getValue("indigo")

    fun typeLabel(t: WidgetType) = when (t) {
        WidgetType.TIMER -> "Timer"
        WidgetType.OPEN_ENDED -> "Open-ended"
        WidgetType.HOMEWORK -> "Homework"
        WidgetType.SUBJECT -> "Subject"
        WidgetType.PRESET -> "Saved setup"
        WidgetType.CUSTOM -> "Custom"
    }

    fun actionLabel(a: WidgetAction) = when (a) {
        WidgetAction.START_SESSION -> "Start a session"
        WidgetAction.OPEN_PREPARE -> "Open session setup"
        WidgetAction.OPEN_TASKS -> "Open tasks"
        WidgetAction.OPEN_HISTORY -> "Open history"
        WidgetAction.OPEN_STATISTICS -> "Open statistics"
    }

    /** One-line description shown on the tile. */
    fun subtitle(w: DashboardWidgetEntity, subjectName: String?, presetName: String?): String = when (w.type) {
        WidgetType.TIMER -> "${w.durationMinutes} min"
        WidgetType.OPEN_ENDED -> "Open-ended"
        WidgetType.HOMEWORK, WidgetType.SUBJECT ->
            listOfNotNull(subjectName, if (w.mode == SessionMode.TIMED) "${w.durationMinutes} min" else "Open-ended").joinToString(" · ")
        WidgetType.PRESET -> presetName ?: "Preset missing"
        WidgetType.CUSTOM ->
            if (w.action == WidgetAction.START_SESSION) {
                listOfNotNull(subjectName, if (w.mode == SessionMode.TIMED) "${w.durationMinutes} min" else "Open-ended").joinToString(" · ")
            } else actionLabel(w.action)
    }

    /** Groups visible widgets into dashboard rows: two small tiles share a row, a large tile has its own. */
    fun rows(widgets: List<DashboardWidgetEntity>): List<List<DashboardWidgetEntity>> {
        val out = mutableListOf<List<DashboardWidgetEntity>>()
        var pending: DashboardWidgetEntity? = null
        for (w in widgets) {
            if (w.size == WidgetSize.LARGE) {
                pending?.let { out += listOf(it) }; pending = null
                out += listOf(w)
            } else if (pending == null) {
                pending = w
            } else {
                out += listOf(pending, w); pending = null
            }
        }
        pending?.let { out += listOf(it) }
        return out
    }
}
