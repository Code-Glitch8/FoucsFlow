package com.focusflow.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.StudyPresetEntity
import com.focusflow.app.data.repo.BackupRepository
import com.focusflow.app.data.repo.BackupResult
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ConfirmDialog
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DataSettingsViewModel(private val backup: BackupRepository, private val presetRepo: PresetRepository) : ViewModel() {
    val presets = presetRepo.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val status = MutableStateFlow<String?>(null)
    val importStaged = MutableStateFlow(false)

    private fun report(r: BackupResult) { status.value = when (r) { is BackupResult.Done -> r.message; is BackupResult.Failed -> r.reason } }

    fun export(uri: android.net.Uri) { viewModelScope.launch { report(backup.export(uri)) } }
    fun import(uri: android.net.Uri) { viewModelScope.launch { val r = backup.stageImport(uri); importStaged.value = r is BackupResult.Done; report(r) } }
    fun resetHistory() { viewModelScope.launch { report(backup.resetHistory()) } }
    fun resetEverything() { viewModelScope.launch { report(backup.resetEverything()) } }
    fun deletePreset(p: StudyPresetEntity) { viewModelScope.launch { presetRepo.delete(p.id) } }
}

private enum class Confirm { HISTORY, EVERYTHING, IMPORT }

@Composable
fun DataSettingsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DataSettingsViewModel(it.backup, it.presets) }
    val presets by vm.presets.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val staged by vm.importStaged.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var deletePreset by remember { mutableStateOf<StudyPresetEntity?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.export(uri) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { pendingImport = uri; confirm = Confirm.IMPORT } }

    when (confirm) {
        Confirm.HISTORY -> ConfirmDialog("Delete session history?", "All finished sessions and their summaries are deleted. Tasks, subjects, chapters and presets stay. This cannot be undone.", "Delete", { confirm = null }) { vm.resetHistory(); confirm = null }
        Confirm.EVERYTHING -> ConfirmDialog("Reset everything?", "Deletes all subjects, chapters, tasks, sessions, presets, blocking choices and settings. This cannot be undone. Export a backup first if unsure.", "Reset", { confirm = null }) { vm.resetEverything(); confirm = null }
        Confirm.IMPORT -> ConfirmDialog("Replace your data with this backup?", "When you reopen the app, everything currently stored is replaced by the backup's contents.", "Import", { confirm = null }) { pendingImport?.let(vm::import); confirm = null }
        null -> Unit
    }
    deletePreset?.let { p ->
        ConfirmDialog("Delete preset?", "\"${p.name}\" will be deleted.", "Delete", { deletePreset = null }) { vm.deletePreset(p); deletePreset = null }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Data & presets")
            }
        }
        item {
            AppCard {
                SectionLabel("PRIVACY")
                Text(
                    "Everything stays on this phone. FocusFlow has no accounts, analytics, ads or uploads. " +
                        "The only network use is downloading an AI model when you press Download.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionLabel("PRESETS") }
        if (presets.isEmpty()) {
            item { Text("No presets yet. Save one from the session setup screen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(presets.size) { i ->
            val p = presets[i]
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { deletePreset = p }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete preset ${p.name}") }
                }
            }
        }
        item {
            AppCard {
                SectionLabel("BACKUP")
                Text(
                    "Export saves your subjects, chapters, tasks, sessions, summaries, presets and blocking choices to a file you pick. " +
                        "Theme and other settings are not included.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exporter.launch("focusflow-backup.db") }) { Text("Export") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import") }
                }
                if (staged) OutlinedButton(onClick = { activity?.finishAffinity() }) { Text("Close app now") }
                status?.let { Text(it) }
            }
        }
        item {
            AppCard {
                SectionLabel("RESET")
                OutlinedButton(onClick = { confirm = Confirm.HISTORY }) { Text("Delete session history") }
                OutlinedButton(onClick = { confirm = Confirm.EVERYTHING }) { Text("Reset everything") }
            }
        }
    }
}
