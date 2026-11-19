package com.focusflow.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.repo.WidgetRepository
import org.junit.After
import org.junit.Before

/** Real Room (SQLite via Robolectric) with the app's real repositories. */
abstract class RoomTestBase {
    protected lateinit var context: Context
    protected lateinit var db: FocusFlowDatabase
    protected lateinit var subjects: SubjectRepository
    protected lateinit var tasks: TaskRepository
    protected lateinit var sessions: SessionRepository
    protected lateinit var presets: PresetRepository
    protected lateinit var widgets: WidgetRepository

    @Before fun openDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusFlowDatabase::class.java).allowMainThreadQueries().build()
        wire()
    }

    protected fun wire() {
        subjects = SubjectRepository(db.subjectDao(), db.chapterDao())
        tasks = TaskRepository(db.taskDao())
        sessions = SessionRepository(db, tasks)
        presets = PresetRepository(db)
        widgets = WidgetRepository(db)
    }

    @After fun closeDb() { db.close() }

    protected fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
