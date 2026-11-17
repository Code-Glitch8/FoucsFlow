package com.focusflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `clock formats minutes and seconds`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("01:23", formatClock(83_000))
        assertEquals("25:00", formatClock(25 * 60_000L))
    }

    @Test
    fun `clock shows hours only when needed`() {
        assertEquals("1:02:03", formatClock(3_723_000))
        assertEquals("59:59", formatClock(3_599_000))
    }

    @Test
    fun `clock clamps negatives`() {
        assertEquals("00:00", formatClock(-5_000))
    }

    @Test
    fun `minutes formatting`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("25m", formatMinutes(25 * 60_000L))
        assertEquals("1h 30m", formatMinutes(90 * 60_000L))
    }
}
