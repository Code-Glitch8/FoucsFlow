package com.focusflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationTest {
    @Test
    fun `bottom navigation has the five required tabs in order`() {
        assertEquals(
            listOf("Home", "Tasks", "Focus", "Statistics", "Settings"),
            Destination.entries.map { it.label },
        )
    }

    @Test
    fun `routes are unique and resolve back to their destination`() {
        val routes = Destination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        Destination.entries.forEach { assertEquals(it, Destination.fromRoute(it.route)) }
    }

    @Test
    fun `unknown route resolves to null`() {
        assertNull(Destination.fromRoute("nope"))
        assertNull(Destination.fromRoute(null))
    }
}
