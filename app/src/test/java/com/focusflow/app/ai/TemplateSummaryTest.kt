package com.focusflow.app.ai

import com.focusflow.app.data.db.SessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateSummaryTest {
    private val base = SummaryFacts(
        subject = "Mathematics",
        chapters = listOf("Quadratics"),
        planned = "revise quadratics and solve 20 questions",
        status = SessionStatus.COMPLETED,
        focusMinutes = 42,
        breakMinutes = 5,
        completedTasks = listOf("Revise quadratics"),
        unfinishedTasks = listOf("Solve 20 questions"),
    )

    @Test
    fun `reports time tasks and a next step`() {
        val s = TemplateSummary.session(base)
        assertTrue("42 min" in s)
        assertTrue("5 min of breaks" in s)
        assertTrue("Completed 1 of 2 tasks" in s)
        assertTrue("Still to do" in s && "Solve 20 questions" in s)
        assertTrue(s.contains("Next: pick up \"Solve 20 questions\""))
    }

    @Test
    fun `never claims mastery`() {
        val s = TemplateSummary.session(base).lowercase()
        assertFalse("master" in s)
    }

    @Test
    fun `no tasks planned is stated plainly`() {
        val s = TemplateSummary.session(base.copy(completedTasks = emptyList(), unfinishedTasks = emptyList()))
        assertTrue("No tasks were planned" in s)
    }

    @Test
    fun `emergency exit is recorded as such`() {
        assertTrue("emergency exit" in TemplateSummary.session(base.copy(status = SessionStatus.EMERGENCY_EXIT)))
    }

    @Test
    fun `unfinished overview`() {
        assertTrue("no unfinished" in TemplateSummary.unfinished(emptyList()))
        assertTrue("2 unfinished tasks" in TemplateSummary.unfinished(listOf("a", "b")))
    }
}
