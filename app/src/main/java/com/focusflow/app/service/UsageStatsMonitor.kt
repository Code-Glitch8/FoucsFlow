package com.focusflow.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Fallback foreground-app detection for when the accessibility service is not enabled.
 * Reads only "which package moved to the foreground", from Android's usage-stats API.
 */
class UsageStatsMonitor(private val context: Context) {
    private var lastPackage: String? = null
    private var lastQuery = 0L

    /** Returns the foreground package if it changed since the last call, else null. */
    fun poll(now: Long = System.currentTimeMillis()): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val from = if (lastQuery == 0L) now - 10_000 else lastQuery - 1_000
        lastQuery = now
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = e.packageName
            }
        }
        if (latest != null && latest != lastPackage) {
            lastPackage = latest
            return latest
        }
        return null
    }
}
