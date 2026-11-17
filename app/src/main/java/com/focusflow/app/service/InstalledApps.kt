package com.focusflow.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focusflow.app.data.db.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val category: AppCategory)

/**
 * Lists launchable apps. Uses a <queries> launcher-intent declaration rather than the broad
 * QUERY_ALL_PACKAGES permission. The list never leaves the device.
 */
object InstalledApps {
    private val knownSocial = setOf(
        "com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.snapchat.android",
        "com.twitter.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.reddit.frontpage",
        "com.pinterest", "com.tumblr", "com.discord", "com.instagram.barcelona",
    )
    private val knownEntertainment = setOf(
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "in.startv.hotstar",
        "com.disney.disneyplus", "tv.twitch.android.app", "com.jio.media.ondemand", "com.google.android.apps.youtube.music",
        "com.hbo.hbonow", "com.mxtech.videoplayer.ad", "com.sonyliv", "com.graymatrix.did",
    )

    fun categoryOf(packageName: String, info: ApplicationInfo?): AppCategory {
        if (packageName in knownSocial) return AppCategory.SOCIAL
        if (packageName in knownEntertainment) return AppCategory.ENTERTAINMENT
        if (info != null) {
            @Suppress("DEPRECATION")
            if (info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
                return AppCategory.GAME
            }
            when (info.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
            }
        }
        return AppCategory.CUSTOM
    }

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map {
                InstalledApp(
                    packageName = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    category = categoryOf(it.packageName, it.applicationInfo),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
