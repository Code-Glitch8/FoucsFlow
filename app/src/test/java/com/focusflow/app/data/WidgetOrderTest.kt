package com.focusflow.app.data

import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.WidgetSize
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.WidgetOrder
import com.focusflow.app.ui.widgets.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetOrderTest {
    @Test fun `move shifts one item and clamps at the ends`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), WidgetOrder.move(ids, "a", 1))
        assertEquals(listOf("a", "c", "b"), WidgetOrder.move(ids, "c", -1))
        assertEquals(ids, WidgetOrder.move(ids, "a", -1))
        assertEquals(ids, WidgetOrder.move(ids, "c", 1))
        assertEquals(ids, WidgetOrder.move(ids, "zzz", 1))
    }

    @Test fun `id csv helpers drop junk and duplicates`() {
        assertEquals(setOf(1L, 2L), DashboardWidgetEntity.parseIds("1, 2,x,,2"))
        assertEquals("1,2", DashboardWidgetEntity.joinIds(listOf(1, 2, 1)))
    }

    private fun w(id: String, size: WidgetSize) = DashboardWidgetEntity(id = id, type = WidgetType.TIMER, title = id, size = size)

    @Test fun `dashboard rows pair small tiles and give large tiles their own row`() {
        val rows = WidgetStyle.rows(listOf(w("a", WidgetSize.SMALL), w("b", WidgetSize.SMALL), w("c", WidgetSize.LARGE), w("d", WidgetSize.SMALL)))
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d")), rows.map { r -> r.map { it.id } })
    }
}
