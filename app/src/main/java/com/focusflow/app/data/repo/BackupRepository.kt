package com.focusflow.app.data.repo

import android.content.Context
import android.net.Uri
import com.focusflow.app.data.db.BackupFile
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BackupResult {
    data class Done(val message: String) : BackupResult
    data class Failed(val reason: String) : BackupResult
}

/**
 * Export copies the whole study database (subjects, chapters, tasks, sessions, summaries,
 * presets, blocking rules) to a file the user picks. Import stages a chosen file and applies it
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
