package com.focusflow.app.data.repo

import androidx.room.withTransaction
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.WidgetSize
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.domain.TaskNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Pure list helpers so ordering rules can be unit tested without a database. */
object WidgetOrder {
    /** Moves the element with [id] by [delta] places (clamped). Returns ids in the new order. */
    fun move(ids: List<String>, id: String, delta: Int): List<String> {
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return ids
        return ids.toMutableList().also { it.add(to, it.removeAt(from)) }
    }
}

sealed interface WidgetSaveResult {
    data class Saved(val id: String) : WidgetSaveResult
    data object BlankTitle : WidgetSaveResult
}

class WidgetRepository(private val db: FocusFlowDatabase) {
    private val dao = db.widgetDao()

    val widgets: Flow<List<DashboardWidgetEntity>> = dao.observeAll()

    suspend fun get(id: String): DashboardWidgetEntity? = dao.getById(id)

    /** Creates (new id, appended) or updates (same id, same position) a widget. */
    suspend fun save(widget: DashboardWidgetEntity): WidgetSaveResult {
        val title = TaskNormalizer.clean(widget.title)
        if (title.isEmpty()) return WidgetSaveResult.BlankTitle
        val clean = widget.copy(
            title = title,
            durationMinutes = widget.durationMinutes.coerceIn(1, 600),
        )
        db.withTransaction {
            val existing = dao.getById(clean.id)
            if (existing == null) {
                dao.upsert(clean.copy(position = dao.nextPosition()))
            } else {
                dao.upsert(clean.copy(position = existing.position, createdAt = existing.createdAt))
            }
        }
        return WidgetSaveResult.Saved(clean.id)
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setVisible(id: String, visible: Boolean) = dao.setVisible(id, visible)

    /** Rewrites positions 0..n-1 following [orderedIds]; ids not listed keep their relative order after them. */
    suspend fun reorder(orderedIds: List<String>) {
        db.withTransaction {
            val all = dao.getAll().map { it.id }
            val finalOrder = orderedIds.filter { it in all } + all.filter { it !in orderedIds }
            finalOrder.forEachIndexed { index, id -> dao.setPosition(id, index) }
        }
    }

    suspend fun move(id: String, delta: Int) {
        val ids = dao.getAll().map { it.id }
        reorder(WidgetOrder.move(ids, id, delta))
    }

    /**
     * Creates a small starter set once. Never runs again after that (even if the user deletes every
     * widget), and never touches a table that already has widgets (e.g. after a backup import).
     */
    suspend fun seedDefaults(settings: AppSettings, subjects: SubjectRepository) {
        if (settings.widgetsSeeded.first()) return
        if (dao.count() == 0) {
            val subjectList = subjects.subjects.first()
            fun idOf(name: String) = subjectList.firstOrNull { it.name.equals(name, true) }?.id
            val defaults = listOf(
                DashboardWidgetEntity(type = WidgetType.TIMER, title = "Focus 25", icon = "timer", colorKey = "indigo", durationMinutes = 25),
                DashboardWidgetEntity(type = WidgetType.OPEN_ENDED, title = "Open focus", icon = "play", colorKey = "teal", mode = SessionMode.OPEN_ENDED),
                DashboardWidgetEntity(type = WidgetType.SUBJECT, title = "Mathematics", icon = "calculate", colorKey = "amber", durationMinutes = 60, subjectId = idOf("Mathematics")),
                DashboardWidgetEntity(type = WidgetType.SUBJECT, title = "Physics", icon = "science", colorKey = "rose", durationMinutes = 60, subjectId = idOf("Physics")),
            )
            db.withTransaction {
                defaults.forEachIndexed { i, w -> dao.upsert(w.copy(position = i)) }
            }
        }
        settings.setWidgetsSeeded(true)
    }
}
