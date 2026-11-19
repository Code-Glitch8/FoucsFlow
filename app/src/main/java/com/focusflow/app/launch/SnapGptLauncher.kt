package com.focusflow.app.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Optional SnapGPT hand-off.
 *
 * What is actually possible from outside another app is limited to its *exported* components:
 *  1. If SnapGPT exposes an activity for [ACTION_SHOW_FLOATING] (a contract SnapGPT would have to
 *     add - see README), FocusFlow starts that, which is the way to ask for its floating interface.
 *  2. Otherwise FocusFlow starts SnapGPT's normal launcher activity (public, set by SnapGPT's own
 *     manifest). It does not touch any private component.
 *  3. If SnapGPT is not installed, nothing happens and the session is unaffected.
 *
 * Android's package-visibility rules need the <queries> entries declared in the manifest.
 */
object SnapGptLauncher {
    const val DEFAULT_PACKAGE = "com.snapgpt.assistant"
    const val ACTION_SHOW_FLOATING = "com.snapgpt.action.SHOW_FLOATING"

    enum class Result { NOT_INSTALLED, LAUNCHED_FLOATING, LAUNCHED_APP, FAILED }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(context: Context, pkg: String): Result {
        if (pkg.isBlank() || !isInstalled(context, pkg)) return Result.NOT_INSTALLED
        return try {
            val floating = Intent(ACTION_SHOW_FLOATING).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (floating.resolveActivity(context.packageManager) != null) {
                context.startActivity(floating)
                Result.LAUNCHED_FLOATING
            } else {
                val main = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: return Result.FAILED
                context.startActivity(main)
                Result.LAUNCHED_APP
            }
        } catch (e: Exception) {
            // Background-activity-start limits, missing exported component, etc. Never fatal.
            Result.FAILED
        }
    }

    /** Launch at most once per session, so reopening FocusFlow never re-launches SnapGPT. */
    fun shouldLaunch(enabled: Boolean, sessionId: Long, lastLaunchedSession: Long): Boolean =
        enabled && sessionId != lastLaunchedSession
}
