package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TaskNormalizerTest {
    @Test
    fun `clean trims and collapses whitespace`() {
        assertEquals("Solve 20 questions", TaskNormalizer.clean("  Solve   20\tquestions \n"))
    }

    @Test
    fun `same task with different case spacing and trailing punctuation shares a key`() {
        val a = TaskNormalizer.dedupeKey("Solve 20 questions.", 7)
        val b = TaskNormalizer.dedupeKey("  solve  20   QUESTIONS ", 7)
        assertEquals(a, b)
    }

    @Test
    fun `same words in different chapters are different tasks`() {
        assertNotEquals(
            TaskNormalizer.dedupeKey("Review mistakes", 1),
            TaskNormalizer.dedupeKey("Review mistakes", 2),
        )
    }

    @Test
    fun `no chapter is distinct from a chapter`() {
        assertNotEquals(
            TaskNormalizer.dedupeKey("Review mistakes", null),
            TaskNormalizer.dedupeKey("Review mistakes", 1),
        )
    }

    @Test
    fun `different wording is not merged`() {
        assertNotEquals(
            TaskNormalizer.dedupeKey("Solve 20 questions", 1),
            TaskNormalizer.dedupeKey("Solve 30 questions", 1),
        )
    }
}
