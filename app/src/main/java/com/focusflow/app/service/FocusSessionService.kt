package com.focusflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.MainActivity
import com.focusflow.app.R
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.repo.ActiveSession
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a study session alive while the user is in other apps. The session itself lives in the
 * database; this service only (a) keeps the process alive, (b) shows an ongoing notification and
 * (c) finishes timed sessions and ends breaks on time. If the process is killed, the service is
 * restarted (START_STICKY) and re-derives everything from the database.
 */
class FocusSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go to the foreground promptly after startForegroundService().
        enterForeground(basicNotification("Focus session", "Resuming…"))
        if (job == null) job = scope.launch { run() }
        return START_STICKY
    }

    private fun enterForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private suspend fun run() {
        val repo = (application as FocusFlowApp).container.sessions
        repo.observeActive().collectLatest { active ->
            if (active == null) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collectLatest
            }
            notify(buildNotification(active))
            val usage = UsageStatsMonitor(this@FocusSessionService)
            val essentials = EssentialApps.resolve(this@FocusSessionService)
            // Re-checked every second: cheap, and robust to clock changes and doze.
            while (scope.isActive) {
                runUsageStatsFallback(usage, essentials)
                val status = repo.tick(active.session.id)
                if (status == SessionStatus.COMPLETED) {
                    postCompletion(active)
                    return@collectLatest
                }
                if (status != SessionStatus.ACTIVE) return@collectLatest
                delay(1_000)
            }
        }
    }

    /**
     * Only used when the accessibility service is off. Needs usage access plus "display over other
     * apps" (which is what lets Android show the block screen from the background).
     */
    private fun runUsageStatsFallback(usage: UsageStatsMonitor, essentials: Set<String>) {
        if (FocusAccessibilityService.connected) return
        if (!Permissions.usageAccessGranted(this) || !Permissions.overlayGranted(this)) return
        val pkg = usage.poll() ?: return
        val container = (application as FocusFlowApp).container
        val now = System.currentTimeMillis()
        val snap = container.blockState.snapshot.value
        if (com.focusflow.app.domain.BlockPolicy.shouldBlockApp(pkg, snap.policyInput(now, essentials))) {
            BlockActivity.show(this, pkg)
        }
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun basicNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildNotification(a: ActiveSession): Notification {
        val now = System.currentTimeMillis()
        val clock = a.session.clock()
        val onBreak = SessionClock.isOnBreak(clock, now)
        val b = NotificationCompat.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent())
            .setUsesChronometer(true)
            .setShowWhen(true)

        val tasksText = if (a.tasks.isEmpty()) "No tasks" else "${a.completedCount}/${a.tasks.size} tasks done"
        when {
            onBreak -> {
                b.setContentTitle("On break · ${a.session.subjectName}")
                b.setWhen(clock.breakEndsAt ?: now)
                b.setChronometerCountDown(true)
            }
            a.session.mode == SessionMode.TIMED -> {
                b.setContentTitle("Studying ${a.session.subjectName}")
                b.setWhen(now + (SessionClock.remainingMs(clock, now) ?: 0L))
                b.setChronometerCountDown(true)
            }
            else -> {
                b.setContentTitle("Studying ${a.session.subjectName}")
                b.setWhen(now - SessionClock.focusElapsedMs(clock, now))
                b.setChronometerCountDown(false)
            }
        }
        b.setContentText(tasksText)
        return b.build()
    }

    private fun postCompletion(a: ActiveSession) {
        val n = NotificationCompat.Builder(this, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Session complete")
            .setContentText("${a.session.subjectName}: ${a.completedCount}/${a.tasks.size} tasks done")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_DONE, n)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_SESSION = "focus_session"
        const val CHANNEL_EVENTS = "focus_events"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_DONE = 1002

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SESSION, "Active study session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows your running focus session and keeps it alive"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_EVENTS, "Session events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Session finished"
                },
            )
        }

        /** Safe to call repeatedly; the service ignores a second start while already running. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FocusSessionService::class.java))
            } catch (e: Exception) {
                // Android refuses foreground-service starts from the background in some states.
                // The session is unaffected (it lives in the database); the next app open retries.
            }
        }
    }
}
