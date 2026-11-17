package com.focusflow.app.service

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps that are never blocked, whatever the user selects, so a strict session can never get in the
 * way of emergency calls, answering a call, the launcher, typing, or the system UI.
 */
object EssentialApps {
    private val fixed = setOf(
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    fun resolve(context: Context): Set<String> {
        val out = fixed.toMutableSet()
        out += context.packageName
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it }
        }
        runCatching {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .forEach { out += it.activityInfo.packageName }
            pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
                .forEach { out += it.activityInfo.packageName }
        }
        runCatching {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
                ?.forEach { out += it.packageName }
        }
        return out
    }
}
