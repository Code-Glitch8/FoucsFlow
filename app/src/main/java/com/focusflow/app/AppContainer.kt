package com.focusflow.app

import android.content.Context
import com.focusflow.app.ai.AiService
import com.focusflow.app.ai.LocalLlm
import com.focusflow.app.ai.ModelManager
import com.focusflow.app.ai.external.ProviderRepository
import com.focusflow.app.data.security.KeystoreSecretStore
import com.focusflow.app.data.security.SecretStore
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.repo.BlockRulesRepository
import com.focusflow.app.data.repo.BackupRepository
import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.repo.BreakConfigRepository
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.repo.WidgetRepository
import com.focusflow.app.launch.SessionLauncher
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.data.sync.LocalSyncRepository
import com.focusflow.app.service.WifiPeerSyncManager
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
    val widgets = WidgetRepository(database)
    val launcher = SessionLauncher(appContext, sessions, presets, subjects, tasks, widgets, settings)
    val sync = LocalSyncRepository(appContext)
    val history = HistoryRepository(database, sync)
    val wifiSync = WifiPeerSyncManager(sync, history)
    val backup = BackupRepository(appContext, database, sessions, settings, subjects)
    val blockState = BlockStateHolder(sessions, blockRules, settings, appScope)

    val models = ModelManager(appContext, appScope, settings)
    val llm = LocalLlm(appContext, appScope)
    val secrets: SecretStore = KeystoreSecretStore(appContext)
    val aiProviders = ProviderRepository(settings, secrets)
    val ai = AiService(models, llm, settings, aiProviders)
}
