package com.focusflow.app.data.repo

import android.content.Context
import android.net.Uri
import com.focusflow.app.ai.external.ProviderJson
import com.focusflow.app.data.db.BackupFile
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface BackupResult {
    data class Done(val message: String) : BackupResult
    data class Failed(val reason: String) : BackupResult
}

/**
 * Export copies the whole study database (subjects, chapters, tasks, sessions, summaries,
 * presets, dashboard widgets, blocking rules) to a file the user picks. Import stages a chosen file and applies it
 * the next time the app starts. Settings such as theme are not part of the backup. Nothing
 * leaves the phone except into the file the user selects.
 */
class BackupRepository(
    private val context: Context,
    private val db: FocusFlowDatabase,
    private val sessions: SessionRepository,
    private val settings: AppSettings,
    private val subjects: SubjectRepository,
) {
    suspend fun export(target: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            // Fold the write-ahead log into the main file so the copy is complete.
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val file = context.getDatabasePath("focusflow.db")
            context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return@withContext BackupResult.Failed("Could not write to that location.")
            BackupResult.Done("Backup saved.")
        } catch (e: Exception) {
            BackupResult.Failed("Export failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun stageImport(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        if (sessions.hasActive()) return@withContext BackupResult.Failed("End the running session before importing.")
        val pending = context.getDatabasePath(FocusFlowDatabase.PENDING_IMPORT)
        try {
            pending.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input -> pending.outputStream().use { input.copyTo(it) } }
                ?: return@withContext BackupResult.Failed("Could not open that file.")
            if (!BackupFile.isAcceptable(pending, FocusFlowDatabase.CURRENT_VERSION)) {
                pending.delete()
                return@withContext BackupResult.Failed("That is not a FocusFlow backup, or it comes from a newer version of the app.")
            }
            BackupResult.Done("Backup ready. Close and reopen FocusFlow to finish. Your current data is replaced when it reopens.")
        } catch (e: Exception) {
            pending.delete()
            BackupResult.Failed("Import failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Settings (theme, defaults, blocking/assistant switches, SnapGPT, provider configs without keys) as JSON. */
    suspend fun exportSettings(target: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val snap = SettingsSnapshot(
                theme = settings.themeMode.first().name,
                defaultMode = settings.defaultMode.first().name,
                defaultDurationMin = settings.defaultDurationMin.first(),
                shortsMode = settings.shortsMode.first().name,
                homeRedirect = settings.homeRedirect.first(),
                floatingEnabled = settings.floatingEnabled.first(),
                snapGptEnabled = settings.snapGptEnabled.first(),
                snapGptPackage = settings.snapGptPackage.first(),
                providers = ProviderJson.fromJson(settings.aiProvidersJson.first()),
                selectedProvider = settings.aiSelectedProvider.first(),
            )
            context.contentResolver.openOutputStream(target, "w")?.use { it.write(SettingsBackup.toJson(snap).toByteArray(Charsets.UTF_8)) }
                ?: return@withContext BackupResult.Failed("Could not write to that location.")
            BackupResult.Done("Settings saved. API keys are never included; re-enter them after restoring.")
        } catch (e: Exception) {
            BackupResult.Failed("Export failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun importSettings(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext BackupResult.Failed("Could not open that file.")
            when (val r = SettingsBackup.parse(text)) {
                is SettingsParse.Rejected -> BackupResult.Failed(r.reason)
                is SettingsParse.Ok -> {
                    val s = r.snapshot
                    s.theme?.let { n -> com.focusflow.app.ui.theme.ThemeMode.entries.firstOrNull { it.name == n }?.let { settings.setThemeMode(it) } }
                    s.defaultMode?.let { n -> com.focusflow.app.data.db.SessionMode.entries.firstOrNull { it.name == n }?.let { settings.setDefaultMode(it) } }
                    s.defaultDurationMin?.let { settings.setDefaultDurationMin(it) }
                    s.shortsMode?.let { n -> com.focusflow.app.domain.ShortsMode.entries.firstOrNull { it.name == n }?.let { settings.setShortsMode(it) } }
                    s.homeRedirect?.let { settings.setHomeRedirect(it) }
                    s.floatingEnabled?.let { settings.setFloatingEnabled(it) }
                    s.snapGptEnabled?.let { settings.setSnapGptEnabled(it) }
                    s.snapGptPackage?.let { settings.setSnapGptPackage(it) }
                    if (s.providers.isNotEmpty()) {
                        val merged = SettingsBackup.mergeProviders(ProviderJson.fromJson(settings.aiProvidersJson.first()), s.providers)
                        settings.setAiProvidersJson(ProviderJson.toJson(merged))
                    }
                    BackupResult.Done("Settings restored. Add your API keys again in AI providers.")
                }
            }
        } catch (e: Exception) {
            BackupResult.Failed("Import failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun resetHistory(): BackupResult {
        if (sessions.hasActive()) return BackupResult.Failed("End the running session first.")
        withContext(Dispatchers.IO) { db.sessionDao().deleteFinished() }
        return BackupResult.Done("Session history deleted. Tasks, subjects and presets were kept.")
    }

    suspend fun resetEverything(): BackupResult {
        if (sessions.hasActive()) return BackupResult.Failed("End the running session first.")
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clearAll()
        subjects.ensureBuiltIns()
        return BackupResult.Done("Everything was reset. Downloaded AI models were kept; delete them in AI settings.")
    }
}
