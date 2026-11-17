package com.focusflow.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTaskParserTest {
    @Test
    fun `parses clean json`() {
        val r = AiTaskParser.parse(
            """{"tasks":[{"title":"Revise quadratic equations theory","minutes":20,"subtasks":["Discriminant","Factoring"]},{"title":"Solve 20 practice questions","minutes":45}]}""",
        )!!
        assertEquals(2, r.size)
        assertEquals(20, r[0].minutes)
        assertEquals(listOf("Discriminant", "Factoring"), r[0].subtasks)
    }

    @Test
    fun `tolerates chatter, code fences and think blocks`() {
        val raw = "<think>hmm let me think</think>\nSure! ```json\n{\"tasks\":[{\"title\":\"Read chapter 3\"}]}\n``` hope that helps"
        assertEquals(listOf("Read chapter 3"), AiTaskParser.parse(raw)!!.map { it.title })
    }

    @Test
    fun `accepts a bare array and plain strings`() {
        assertEquals(listOf("A task", "Another"), AiTaskParser.parse("""["a task","another"]""")!!.map { it.title })
    }

    @Test
    fun `garbage is rejected so the caller can fall back`() {
        assertNull(AiTaskParser.parse("I cannot help with that"))
        assertNull(AiTaskParser.parse("{ not json"))
    }

    @Test
    fun `empty task list parses as empty not null`() {
        assertTrue(AiTaskParser.parse("""{"tasks":[]}""")!!.isEmpty())
    }

    @Test
    fun `out of range minutes are dropped and titles are bounded`() {
        val r = AiTaskParser.parse("""{"tasks":[{"title":"x${"y".repeat(300)}","minutes":99999}]}""")!!.single()
        assertNull(r.minutes)
        assertTrue(r.title.length <= 120)
    }

    @Test
    fun `duplicates are removed and count is capped`() {
        val many = (1..30).joinToString(",") { """{"title":"Task $it"}""" }
        assertEquals(12, AiTaskParser.parse("""{"tasks":[$many]}""")!!.size)
        assertEquals(1, AiTaskParser.parse("""{"tasks":[{"title":"Same"},{"title":"same"}]}""")!!.size)
    }

    @Test
    fun `invented topics are dropped but reasonable decomposition is kept`() {
        val tasks = listOf(
            ExtractedTask("Revise quadratic equations theory"),
            ExtractedTask("Solve 20 practice questions"),
            ExtractedTask("Review incorrect answers"),
            ExtractedTask("Learn organic chemistry reactions"),
        )
        val (kept, dropped) = AiTaskParser.keepGrounded(
            tasks,
            "I want to revise quadratic equations, solve 20 questions and review the questions I got wrong",
            listOf("Mathematics", "Quadratics"),
        )
        assertEquals(1, dropped)
        assertEquals(3, kept.size)
        assertNotNull(kept.firstOrNull { it.title.startsWith("Review") })
        assertTrue(kept.none { "organic" in it.title.lowercase() })
    }
}
