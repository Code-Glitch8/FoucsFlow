package com.focusflow.app.launch

import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.db.TaskStatus
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.DraftTask
import com.focusflow.app.data.repo.PresetDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDraftBuilderTest {
    private fun start(w: DashboardWidgetEntity, ctx: WidgetContext = WidgetContext(null)) =
        WidgetDraftBuilder.plan(w, ctx) as WidgetPlan.Start

    @Test fun `timer widget makes a timed session of its duration`() {
        val d = start(DashboardWidgetEntity(type = WidgetType.TIMER, title = "Focus 45", durationMinutes = 45, strict = false)).draft
        assertEquals(SessionMode.TIMED, d.mode)
        assertEquals(45, d.plannedMinutes)
        assertFalse(d.strict)
        assertNull(d.subjectId)
    }

    @Test fun `open ended widget has no planned duration even if a duration is stored`() {
        val d = start(DashboardWidgetEntity(type = WidgetType.OPEN_ENDED, title = "Open", durationMinutes = 90, mode = SessionMode.TIMED)).draft
        assertEquals(SessionMode.OPEN_ENDED, d.mode)
        assertNull(d.plannedMinutes)
    }

    @Test fun `subject widget carries subject chapters and mode`() {
        val w = DashboardWidgetEntity(type = WidgetType.SUBJECT, title = "Maths", subjectId = 3, chapterIds = "10,11", durationMinutes = 60)
        val d = start(w, WidgetContext("Mathematics")).draft
        assertEquals(3L, d.subjectId)
        assertEquals("Mathematics", d.subjectName)
        assertEquals(setOf(10L, 11L), d.chapterIds)
        assertEquals(60, d.plannedMinutes)
        assertTrue(d.strict)
    }

    @Test fun `homework widget uses homework name without a subject and honours open ended mode`() {
        val w = DashboardWidgetEntity(type = WidgetType.HOMEWORK, title = "HW", mode = SessionMode.OPEN_ENDED)
        val d = start(w).draft
        assertEquals(WidgetDraftBuilder.HOMEWORK_NAME, d.subjectName)
        assertEquals(SessionMode.OPEN_ENDED, d.mode)
    }

    @Test fun `saved tasks are linked by id and finished ones are skipped with a note`() {
        val open = TaskEntity(id = 1, title = "Do ex 1", dedupeKey = "k1")
        val done = TaskEntity(id = 2, title = "Do ex 2", status = TaskStatus.DONE, dedupeKey = "k2")
        val w = DashboardWidgetEntity(type = WidgetType.HOMEWORK, title = "HW", taskIds = "1,2,3")
        val plan = start(w, WidgetContext(null, tasks = listOf(open, done)))
        assertEquals(listOf(1L), plan.draft.tasks.map { it.existingTaskId })
        assertTrue(plan.notes.isNotEmpty())
    }

    @Test fun `deleted subject falls back to a generic session with a note`() {
        val w = DashboardWidgetEntity(type = WidgetType.SUBJECT, title = "Gone", subjectId = 99, chapterIds = "1")
        val plan = start(w, WidgetContext(subjectName = null))
        assertNull(plan.draft.subjectId)
        assertTrue(plan.draft.chapterIds.isEmpty())
        assertTrue(plan.notes.isNotEmpty())
    }

    @Test fun `preset widget restores every saved preset field`() {
        val preset = PresetDraft(
            id = 7, name = "JEE", subjectId = 2, chapterIds = setOf(4L, 5L), mode = SessionMode.TIMED, durationMinutes = 50,
            strict = false, blockYoutubeShorts = false, floatingAssistant = false,
            tasks = listOf(DraftTask("Solve 20 questions", 30, chapterId = 4)),
        )
        val w = DashboardWidgetEntity(type = WidgetType.PRESET, title = "JEE", presetId = 7)
        val d = start(w, WidgetContext(null, preset = preset, presetSubjectName = "Physics")).draft
        assertEquals(7L, d.presetId)
        assertEquals(2L, d.subjectId)
        assertEquals("Physics", d.subjectName)
        assertEquals(setOf(4L, 5L), d.chapterIds)
        assertEquals(50, d.plannedMinutes)
        assertFalse(d.strict)
        assertFalse(d.blockShorts)
        assertFalse(d.floatingAssistant)
        assertEquals(1, d.tasks.size)
    }

    @Test fun `open ended preset has no planned minutes`() {
        val preset = PresetDraft(id = 1, name = "P", mode = SessionMode.OPEN_ENDED, durationMinutes = 25)
        val d = start(DashboardWidgetEntity(type = WidgetType.PRESET, title = "P", presetId = 1), WidgetContext(null, preset = preset)).draft
        assertNull(d.plannedMinutes)
    }

    @Test fun `missing preset is reported instead of starting`() {
        val plan = WidgetDraftBuilder.plan(DashboardWidgetEntity(type = WidgetType.PRESET, title = "P", presetId = 1), WidgetContext(null))
        assertTrue(plan is WidgetPlan.Invalid)
    }

    @Test fun `custom widget can navigate instead of starting`() {
        val nav = WidgetDraftBuilder.plan(DashboardWidgetEntity(type = WidgetType.CUSTOM, title = "Stats", action = WidgetAction.OPEN_STATISTICS), WidgetContext(null))
        assertEquals(WidgetAction.OPEN_STATISTICS, (nav as WidgetPlan.Navigate).action)
        val go = WidgetDraftBuilder.plan(DashboardWidgetEntity(type = WidgetType.CUSTOM, title = "Go", action = WidgetAction.START_SESSION, durationMinutes = 20), WidgetContext(null))
        assertEquals(20, (go as WidgetPlan.Start).draft.plannedMinutes)
    }
}
