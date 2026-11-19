package com.focusflow.app.domain

/** When YouTube Shorts should be blocked. */
enum class ShortsMode { OFF, FOCUS_ONLY, ALWAYS }

data class BlockRule(val packageName: String, val blockedDuringBreak: Boolean = false)

/** Everything the blocking decision depends on, as plain data so it can be unit tested. */
data class PolicyInput(
    val sessionActive: Boolean,
    val onBreak: Boolean,
    /** User-chosen blocked apps. */
    val blocked: Map<String, BlockRule>,
    /** User-chosen allowed apps: these override the blocked list. */
    val allowed: Set<String>,
    /** Apps that are never blocked: this app, phone/emergency, launcher, keyboards, system UI. */
    val essentials: Set<String>,
    /** Explicit setting to block the entire YouTube app during a focus session. */
    val blockYoutubeApp: Boolean = false,
)

object BlockPolicy {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /**
     * An app is blocked only while a focus session is active, only if selected or covered by the
     * full-YouTube setting, never if it is essential or allowed, and not during a break unless its
     * own rule says it should stay blocked through breaks.
     */
    fun shouldBlockApp(packageName: String?, input: PolicyInput): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (!input.sessionActive) return false
        if (packageName in input.essentials) return false
        if (packageName in input.allowed) return false

        val rule = input.blocked[packageName]
        if (packageName == YOUTUBE_PACKAGE && input.blockYoutubeApp) {
            return !input.onBreak || rule?.blockedDuringBreak == true
        }
        if (rule == null) return false
        return !input.onBreak || rule.blockedDuringBreak
    }

    fun shortsEnabled(mode: ShortsMode, sessionActive: Boolean, onBreak: Boolean): Boolean = when (mode) {
        ShortsMode.OFF -> false
        ShortsMode.ALWAYS -> true
        // During a break the user has earned some slack; outside a session there is nothing to enforce.
        ShortsMode.FOCUS_ONLY -> sessionActive && !onBreak
    }

    fun homeRedirectEnabled(
        enabled: Boolean,
        mode: ShortsMode,
        sessionActive: Boolean,
        onBreak: Boolean,
    ): Boolean = enabled && shortsEnabled(mode, sessionActive, onBreak)
}
