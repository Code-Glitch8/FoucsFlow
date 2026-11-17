package com.focusflow.app

import android.content.Context
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.repo.BlockRulesRepository
import com.focusflow.app.data.repo.BreakConfigRepository
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.service.BlockStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; small enough that a DI framework would be overhead. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FocusFlowDatabase = FocusFlowDatabase.build(context)
    val settings = AppSettings(appContext)
    val subjects = SubjectRepository(database.subjectDao(), database.chapterDao())
    val tasks = TaskRepository(database.taskDao())
    val presets = PresetRepository(database)
    val breakConfig = BreakConfigRepository(database)
    val sessions = SessionRepository(database, tasks)
    val blockRules = BlockRulesRepository(database)
    val blockState = BlockStateHolder(sessions, blockRules, settings, appScope)
}
