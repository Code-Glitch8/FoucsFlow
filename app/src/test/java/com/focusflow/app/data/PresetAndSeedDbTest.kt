package com.focusflow.app.data

import com.focusflow.app.data.db.AppCategory
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.AllowedAppRule
import com.focusflow.app.data.repo.BlockedAppRule
import com.focusflow.app.data.repo.DraftTask
import com.focusflow.app.data.repo.PresetDraft
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.domain.BreakConfig
import com.focusflow.app.domain.Class11Chapters
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
class PresetAndSeedDbTest : RoomTestBase() {

    private fun full(subjectId: Long?, chapterIds: Set<Long>) = PresetDraft(
        name = "JEE Maths", subjectId = subjectId, chapterIds = chapterIds, mode = SessionMode.TIMED, durationMinutes = 50,
        strict = false, blockYoutubeShorts = false, floatingAssistant = false,
        blockedApps = listOf(BlockedAppRule("com.insta", "Instagram", AppCategory.SOCIAL, blockedDuringBreak = true)),
        allowedApps = listOf(AllowedAppRule("com.calc", "Calculator")),
        breakConfig = BreakConfig(enabled = true, tasksForShortBreak = 2, shortBreakMinutes = 7, tasksForLongBreak = 4, longBreakMinutes = 20),
        tasks = listOf(DraftTask("Solve 20 questions", estimatedMinutes = 30, subtasks = listOf("Easy", "Hard"))),
    )

    private suspend fun saved(d: PresetDraft) = (presets.save(d) as PresetRepository.SaveResult.Saved).id

    @Test fun `saving then loading restores every field`() = runBlocking {
        subjects.ensureBuiltIns()
        val math = subjects.subjects.first().first { it.name == "Mathematics" }
        val ch = subjects.addChapterForTest(db, math.id, "Limits")
        val id = saved(full(math.id, setOf(ch)))

        val p = presets.load(id)!!
        assertEquals("JEE Maths", p.name)
        assertEquals(math.id, p.subjectId)
        assertEquals(setOf(ch), p.chapterIds)
        assertEquals(50, p.durationMinutes)
        assertEquals(false, p.strict)
        assertEquals(false, p.blockYoutubeShorts)
        assertEquals(false, p.floatingAssistant)
        assertEquals(listOf("com.insta"), p.blockedApps.map { it.packageName })
        assertTrue(p.blockedApps.single().blockedDuringBreak)
        assertEquals(listOf("com.calc"), p.allowedApps.map { it.packageName })
        assertEquals(7, p.breakConfig!!.shortBreakMinutes)
        assertEquals(20, p.breakConfig!!.longBreakMinutes)
        assertEquals("Solve 20 questions", p.tasks.single().title)
        assertEquals(30, p.tasks.single().estimatedMinutes)
        assertEquals(listOf("Easy", "Hard"), p.tasks.single().subtasks)
    }

    @Test fun `updating one field keeps everything else`() = runBlocking {
        val id = saved(full(null, emptySet()))
        val loaded = presets.load(id)!!
        presets.save(loaded.copy(durationMinutes = 75))
        val after = presets.load(id)!!
        assertEquals(75, after.durationMinutes)
        assertEquals(1, after.blockedApps.size)
        assertEquals(1, after.allowedApps.size)
        assertEquals(7, after.breakConfig!!.shortBreakMinutes)
        assertEquals(1, after.tasks.size)
        assertEquals(1, count("study_presets"))
    }

    @Test fun `preset without break config inherits global rules (no row stored)`() = runBlocking {
        val id = saved(PresetDraft(name = "Plain"))
        assertNull(presets.load(id)!!.breakConfig)
    }

    @Test fun `duplicate name is refused and delete removes the preset and its tasks`() = runBlocking {
        val id = saved(full(null, emptySet()))
        assertEquals(PresetRepository.SaveResult.DuplicateName, presets.save(full(null, emptySet())))
        presets.delete(id)
        assertNull(presets.load(id))
        assertEquals(0, count("preset_tasks"))
    }

    // ---- Class 11 chapter seeding ----

    @Test fun `seeding adds all default chapters once with no duplicates`() = runBlocking {
        subjects.ensureBuiltIns()
        val first = subjects.seedChapters(Class11Chapters.bySubject)
        assertEquals(14 + 14 + 9, first)
        assertEquals(0, subjects.seedChapters(Class11Chapters.bySubject))
        val all = subjects.allChapters.first()
        assertEquals(37, all.size)
        assertEquals(all.size, all.map { it.subjectId to it.name.lowercase() }.toSet().size)
    }

    @Test fun `seeding keeps user chapters and does not duplicate a user chapter that matches a default`() = runBlocking {
        subjects.ensureBuiltIns()
        val math = subjects.subjects.first().first { it.name == "Mathematics" }
        val own = subjects.addChapterForTest(db, math.id, "my own revision")
        val clash = subjects.addChapterForTest(db, math.id, "SETS")
        subjects.seedChapters(Class11Chapters.bySubject)
        val mathChapters = subjects.allChapters.first().filter { it.subjectId == math.id }
        assertEquals(14 + 1, mathChapters.size)
        assertNotNull(mathChapters.firstOrNull { it.id == own })
        assertEquals("SETS", mathChapters.first { it.id == clash }.name) // user's spelling untouched
    }

    @Test fun `version gate runs the seed once and does not resurrect a deleted default chapter`() = runBlocking {
        val settings = com.focusflow.app.data.settings.AppSettings(context)
        settings.setClass11SeedVersion(0)
        subjects.ensureBuiltIns()
        assertEquals(37, subjects.ensureClass11Chapters(settings))
        val sets = subjects.allChapters.first().first { it.name == "Sets" }
        db.chapterDao().delete(sets)
        assertEquals(0, subjects.ensureClass11Chapters(settings))
        assertTrue(subjects.allChapters.first().none { it.name == "Sets" })
    }

    private suspend fun com.focusflow.app.data.repo.SubjectRepository.addChapterForTest(
        db: com.focusflow.app.data.db.FocusFlowDatabase, subjectId: Long, name: String,
    ): Long = db.chapterDao().insert(ChapterEntity(subjectId = subjectId, name = name))
}
