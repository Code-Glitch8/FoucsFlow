package com.focusflow.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedTaskExtractorTest {
    private fun titles(s: String) = RuleBasedTaskExtractor.extract(s).map { it.title }

    @Test
    fun `the example from the spec splits into three tasks`() {
        assertEquals(
            listOf("Revise quadratic equations", "Solve 20 questions", "Review the questions I got wrong"),
            titles("I want to revise quadratic equations, solve 20 questions and review the questions I got wrong."),
        )
    }

    @Test
    fun `and between nouns does not split`() {
        assertEquals(listOf("Revise sine and cosine rules"), titles("revise sine and cosine rules"))
    }

    @Test
    fun `sentences and newlines separate tasks`() {
        assertEquals(
            listOf("Read chapter 3", "Do exercise 3.1"),
            titles("Read chapter 3. Do exercise 3.1"),
        )
        assertEquals(listOf("Read chapter 3", "Make notes"), titles("- read chapter 3\n- make notes"))
    }

    @Test
    fun `filler words are removed but meaning is kept`() {
        assertEquals(listOf("Finish the worksheet"), titles("Today I need to finish the worksheet"))
    }

    @Test
    fun `question counts produce a rough estimate rounded to 5`() {
        val t = RuleBasedTaskExtractor.extract("solve 20 questions").single()
        assertEquals(60, t.minutes)
        assertEquals(15, RuleBasedTaskExtractor.extract("solve 5 questions").single().minutes)
    }

    @Test
    fun `no estimate is invented when no number is given`() {
        assertEquals(null, RuleBasedTaskExtractor.extract("revise optics").single().minutes)
    }

    @Test
    fun `duplicates and empty input are handled`() {
        assertEquals(listOf("Revise optics"), titles("revise optics. Revise optics."))
        assertTrue(titles("   ").isEmpty())
    }

    @Test
    fun `never adds words the user did not say`() {
        val out = titles("revise thermodynamics and solve past papers")
        assertEquals(listOf("Revise thermodynamics", "Solve past papers"), out)
    }
}
