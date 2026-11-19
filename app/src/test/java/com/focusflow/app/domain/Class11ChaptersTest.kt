package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Class11ChaptersTest {
    @Test fun `chapter counts match the current NCERT books`() {
        assertEquals(14, Class11Chapters.mathematics.size)
        assertEquals(14, Class11Chapters.physics.size)
        assertEquals(9, Class11Chapters.chemistry.size)
    }

    @Test fun `no blank or case-insensitive duplicate names within a subject`() {
        Class11Chapters.bySubject.forEach { (subject, list) ->
            assertTrue("$subject has blank chapter", list.none { it.isBlank() })
            assertEquals("$subject has duplicates", list.size, list.map { it.lowercase() }.toSet().size)
        }
    }

    @Test fun `subjects use the built in names`() {
        assertEquals(listOf("Mathematics", "Physics", "Chemistry"), Class11Chapters.bySubject.keys.toList())
    }
}
