package com.focusflow.app.data

import androidx.room.Room
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.StartResult
import com.focusflow.app.data.repo.WidgetRepository
import com.focusflow.app.data.repo.WidgetSaveResult
import com.focusflow.app.launch.WidgetContext
import com.focusflow.app.launch.WidgetDraftBuilder
import com.focusflow.app.launch.WidgetPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetAndSessionDbTest : RoomTestBase() {

    private fun widget(title: String, type: WidgetType = WidgetType.TIMER) = DashboardWidgetEntity(type = type, title = title)

    @Test fun `create edit delete widget`() = runBlocking {
        val id = (widgets.save(widget("Focus 25")) as WidgetSaveResult.Saved).id
        val created = widgets.get(id)!!
        assertEquals("Focus 25", created.title)

        widgets.save(created.copy(title = "Deep work", durationMinutes = 90))
        val edited = widgets.get(id)!!
        assertEquals("Deep work", edited.title)
        assertEquals(90, edited.durationMinutes)
        assertEquals(created.position, edited.position)
        assertEquals(1, count("dashboard_widgets"))

        widgets.delete(id)
        assertNull(widgets.get(id))
    }

    @Test fun `blank title is rejected`() = runBlocking {
        assertEquals(WidgetSaveResult.BlankTitle, widgets.save(widget("   ")))
        assertEquals(0, count("dashboard_widgets"))
    }

    @Test fun `reorder and hide keep ids stable`() = runBlocking {
        val a = (widgets.save(widget("A")) as WidgetSaveResult.Saved).id
        val b = (widgets.save(widget("B")) as WidgetSaveResult.Saved).id
        val c = (widgets.save(widget("C")) as WidgetSaveResult.Saved).id
        assertEquals(listOf(a, b, c), widgets.widgets.first().map { it.id })

        widgets.move(c, -2)
        assertEquals(listOf(c, a, b), widgets.widgets.first().map { it.id })
        widgets.reorder(listOf(b, c, a))
        assertEquals(listOf(b, c, a), widgets.widgets.first().map { it.id })

        widgets.setVisible(a, false)
        assertEquals(false, widgets.get(a)!!.visible)
        assertEquals(3, count("dashboard_widgets"))
    }

    @Test fun `widgets persist after the database is closed and reopened`() = runBlocking {
        db.close()
        val name = "persist-test.db"
        context.deleteDatabase(name)
        var file = Room.databaseBuilder(context, FocusFlowDatabase::class.java, name).allowMainThreadQueries().build()
        val id = (WidgetRepository(file).save(widget("Mathematics", WidgetType.SUBJECT).copy(durationMinutes = 60, chapterIds = "1,2")) as WidgetSaveResult.Saved).id
        file.close()
        file = Room.databaseBuilder(context, FocusFlowDatabase::class.java, name).allowMainThreadQueries().build()
        val back = WidgetRepository(file).get(id)
        file.close()
        context.deleteDatabase(name)
        assertNotNull(back)
        assertEquals(60, back!!.durationMinutes)
        assertEquals("1,2", back.chapterIds)
        // reopen the shared in-memory db so @After can close it
        db = Room.inMemoryDatabaseBuilder(context, FocusFlowDatabase::class.java).allowMainThreadQueries().build()
    }

    @Test fun `default widgets are seeded once and never over existing ones`() = runBlocking {
        val settings = com.focusflow.app.data.settings.AppSettings(context)
        settings.setWidgetsSeeded(false)
        subjects.ensureBuiltIns()
        widgets.seedDefaults(settings, subjects)
        val first = widgets.widgets.first()
        assertEquals(4, first.size)
        assertTrue(first.first { it.title == "Mathematics" }.subjectId != null)
        widgets.seedDefaults(settings, subjects)
        assertEquals(4, count("dashboard_widgets"))
        // deleting everything must not bring the defaults back
        first.forEach { widgets.delete(it.id) }
        widgets.seedDefaults(settings, subjects)
        assertEquals(0, count("dashboard_widgets"))
    }

    // ---- sessions started from widgets ----

    private suspend fun startFrom(w: DashboardWidgetEntity, subjectName: String? = null): StartResult {
        val ctx = WidgetContext(subjectName, tasks = w.taskIdList().mapNotNull { tasks.get(it) })
        val plan = WidgetDraftBuilder.plan(w, ctx) as WidgetPlan.Start
        return sessions.start(plan.draft)
    }

    @Test fun `timed subject widget stores duration subject chapters tasks and strict flag`() = runBlocking {
        subjects.ensureBuiltIns()
        subjects.seedChapters(com.focusflow.app.domain.Class11Chapters.bySubject)
        val math = subjects.subjects.first().first { it.name == "Mathematics" }
        val chapters = subjects.allChapters.first().filter { it.subjectId == math.id }.take(2)
        val taskId = (tasks.add("Solve exercise 1", math.id, chapters[0].id) as com.focusflow.app.data.repo.AddTaskResult.Added).taskId

        val w = DashboardWidgetEntity(
            type = WidgetType.SUBJECT, title = "Mathematics", subjectId = math.id, durationMinutes = 60,
            strict = false, chapterIds = chapters.joinToString(",") { it.id.toString() }, taskIds = taskId.toString(),
        )
        val started = startFrom(w, "Mathematics") as StartResult.Started
        val s = db.sessionDao().getActive()!!
        assertEquals(started.sessionId, s.id)
        assertEquals(60 * 60_000L, s.plannedDurationMs)
        assertEquals(SessionMode.TIMED, s.mode)
        assertEquals(math.id, s.subjectId)
        assertEquals("Mathematics", s.subjectName)
        assertEquals(false, s.strict)
        assertEquals(chapters.map { it.id }.toSet(), db.sessionDao().chapterIdsForSession(s.id).toSet())
        assertEquals(1, count("session_tasks"))
    }

    @Test fun `open ended widget has no planned duration`() = runBlocking {
        startFrom(DashboardWidgetEntity(type = WidgetType.OPEN_ENDED, title = "Open"))
        val s = db.sessionDao().getActive()!!
        assertEquals(SessionMode.OPEN_ENDED, s.mode)
        assertNull(s.plannedDurationMs)
    }

    @Test fun `second widget tap while a session is active does not create another session`() = runBlocking {
        assertTrue(startFrom(widget("One")) is StartResult.Started)
        assertEquals(StartResult.AlreadyActive, startFrom(widget("Two")))
        assertEquals(1, count("study_sessions"))
    }

    @Test fun `per session switches from a preset are stored on the session`() = runBlocking {
        val draft = com.focusflow.app.data.repo.PresetDraft(name = "Quiet", floatingAssistant = false, blockYoutubeShorts = false)
        val id = (presets.save(draft) as com.focusflow.app.data.repo.PresetRepository.SaveResult.Saved).id
        val widget = DashboardWidgetEntity(type = WidgetType.PRESET, title = "Quiet", presetId = id)
        val plan = WidgetDraftBuilder.plan(widget, WidgetContext(null, preset = presets.load(id))) as WidgetPlan.Start
        sessions.start(plan.draft)
        val s = db.sessionDao().getActive()!!
        assertEquals(false, s.floatingAssistant)
        assertEquals(false, s.blockShorts)
        assertEquals(id, s.presetId)
    }
}
