package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {
    private val base = PolicyInput(
        sessionActive = true,
        onBreak = false,
        blocked = mapOf(
            "com.game" to BlockRule("com.game"),
            "com.social" to BlockRule("com.social", blockedDuringBreak = true),
        ),
        allowed = emptySet(),
        essentials = setOf("com.dialer"),
    )

    @Test
    fun `listed app is blocked during a session`() {
        assertTrue(BlockPolicy.shouldBlockApp("com.game", base))
    }

    @Test
    fun `nothing is blocked outside a session`() {
        assertFalse(BlockPolicy.shouldBlockApp("com.game", base.copy(sessionActive = false)))
    }

    @Test
    fun `unlisted apps are never blocked`() {
        assertFalse(BlockPolicy.shouldBlockApp("com.notes", base))
    }

    @Test
    fun `essential apps are never blocked even if listed`() {
        val input = base.copy(blocked = base.blocked + ("com.dialer" to BlockRule("com.dialer")))
        assertFalse(BlockPolicy.shouldBlockApp("com.dialer", input))
    }

    @Test
    fun `allowed list overrides blocked list`() {
        assertFalse(BlockPolicy.shouldBlockApp("com.game", base.copy(allowed = setOf("com.game"))))
    }

    @Test
    fun `break lifts the block except for apps kept blocked through breaks`() {
        val onBreak = base.copy(onBreak = true)
        assertFalse(BlockPolicy.shouldBlockApp("com.game", onBreak))
        assertTrue(BlockPolicy.shouldBlockApp("com.social", onBreak))
    }

    @Test
    fun `null or empty package is ignored`() {
        assertFalse(BlockPolicy.shouldBlockApp(null, base))
        assertFalse(BlockPolicy.shouldBlockApp("", base))
    }

    @Test
    fun `shorts modes`() {
        assertFalse(BlockPolicy.shortsEnabled(ShortsMode.OFF, sessionActive = true, onBreak = false))
        assertTrue(BlockPolicy.shortsEnabled(ShortsMode.ALWAYS, sessionActive = false, onBreak = false))
        assertTrue(BlockPolicy.shortsEnabled(ShortsMode.ALWAYS, sessionActive = true, onBreak = true))
        assertTrue(BlockPolicy.shortsEnabled(ShortsMode.FOCUS_ONLY, sessionActive = true, onBreak = false))
        assertFalse(BlockPolicy.shortsEnabled(ShortsMode.FOCUS_ONLY, sessionActive = false, onBreak = false))
        assertFalse(BlockPolicy.shortsEnabled(ShortsMode.FOCUS_ONLY, sessionActive = true, onBreak = true))
    }

    @Test
    fun `home redirect needs both its toggle and an active scope`() {
        assertFalse(BlockPolicy.homeRedirectEnabled(false, ShortsMode.ALWAYS, true, false))
        assertTrue(BlockPolicy.homeRedirectEnabled(true, ShortsMode.ALWAYS, false, false))
        assertFalse(BlockPolicy.homeRedirectEnabled(true, ShortsMode.OFF, true, false))
        assertEquals(
            false,
            BlockPolicy.homeRedirectEnabled(true, ShortsMode.FOCUS_ONLY, sessionActive = false, onBreak = false),
        )
    }
}
