package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakRulesTest {
    private val default = BreakConfig(tasksForShortBreak = 1, tasksForLongBreak = 3)

    @Test
    fun `no completed tasks earns nothing`() {
        assertEquals(0 to 0, BreakRules.earned(0, default))
        assertFalse(BreakRules.available(0, 0, 0, default).any)
    }

    @Test
    fun `completing one task earns a short break`() {
        assertEquals(1 to 0, BreakRules.earned(1, default))
    }

    @Test
    fun `third task earns a long break that replaces the short one`() {
        // tasks 1,2 -> short; task 3 -> long (not short as well)
        assertEquals(2 to 1, BreakRules.earned(3, default))
        // tasks 4,5 -> short; task 6 -> long
        assertEquals(4 to 2, BreakRules.earned(6, default))
    }

    @Test
    fun `used breaks are subtracted and never go negative`() {
        val c = BreakRules.available(completedTasks = 3, shortUsed = 1, longUsed = 0, config = default)
        assertEquals(1, c.shortAvailable)
        assertEquals(1, c.longAvailable)
        val over = BreakRules.available(completedTasks = 1, shortUsed = 5, longUsed = 5, config = default)
        assertEquals(0, over.shortAvailable)
        assertEquals(0, over.longAvailable)
    }

    @Test
    fun `disabled breaks never unlock`() {
        val off = default.copy(enabled = false)
        assertEquals(0 to 0, BreakRules.earned(10, off))
    }

    @Test
    fun `custom thresholds are respected`() {
        val cfg = BreakConfig(tasksForShortBreak = 2, tasksForLongBreak = 5)
        // k=2,4 short; k=5 long
        assertEquals(2 to 1, BreakRules.earned(5, cfg))
        assertTrue(BreakRules.available(2, 0, 0, cfg).shortAvailable == 1)
    }

    @Test
    fun `nonsensical thresholds are clamped instead of crashing`() {
        val cfg = BreakConfig(tasksForShortBreak = 0, tasksForLongBreak = 0)
        BreakRules.earned(4, cfg) // must not throw (division by zero)
    }
}
