package com.focusflow.app.data

import android.app.Application
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.WidgetSaveResult
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.launch.LaunchOutcome
import com.focusflow.app.launch.SessionLauncher
import com.focusflow.app.service.FocusSessionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The one shared start path: database session + foreground service, and the duplicate-tap guard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionLauncherTest : RoomTestBase() {
    private fun launcher() = SessionLauncher(context, sessions, presets, subjects, tasks, widgets, AppSettings(context))

    private suspend fun add(w: DashboardWidgetEntity) = (widgets.save(w) as WidgetSaveResult.Saved).id

    @Test fun `tapping a timer widget starts the session and the focus service immediately`() = runBlocking {
        val id = add(DashboardWidgetEntity(type = WidgetType.TIMER, title = "Focus 45", durationMinutes = 45))
        val out = launcher().runWidget(id)
        assertTrue(out is LaunchOutcome.Started)
        assertEquals(45 * 60_000L, db.sessionDao().getActive()!!.plannedDurationMs)
        val started = shadowOf(context as Application).nextStartedService
        assertNotNull(started)
        assertEquals(FocusSessionService::class.java.name, started.component!!.className)
    }

    @Test fun `a second tap reports the active session instead of starting another`() = runBlocking {
        val a = add(DashboardWidgetEntity(type = WidgetType.TIMER, title = "A"))
        val b = add(DashboardWidgetEntity(type = WidgetType.OPEN_ENDED, title = "B"))
        val l = launcher()
        assertTrue(l.runWidget(a) is LaunchOutcome.Started)
        assertTrue(l.runWidget(b) is LaunchOutcome.AlreadyActive)
        assertEquals(1, count("study_sessions"))
    }

    @Test fun `navigation custom widget does not start a session`() = runBlocking {
        val id = add(DashboardWidgetEntity(type = WidgetType.CUSTOM, title = "Stats", action = WidgetAction.OPEN_STATISTICS))
        val out = launcher().runWidget(id)
        assertEquals(WidgetAction.OPEN_STATISTICS, (out as LaunchOutcome.Navigate).action)
        assertNull(db.sessionDao().getActive())
    }

    @Test fun `unknown widget and missing preset are reported not crashed`() = runBlocking {
        assertTrue(launcher().runWidget("nope") is LaunchOutcome.Invalid)
        val id = add(DashboardWidgetEntity(type = WidgetType.PRESET, title = "P", presetId = 12345))
        assertTrue(launcher().runWidget(id) is LaunchOutcome.Invalid)
        assertNull(db.sessionDao().getActive())
    }
}
