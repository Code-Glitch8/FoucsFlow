package com.focusflow.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.ai.AiService
import com.focusflow.app.ai.DownloadState
import com.focusflow.app.ai.ImportResult
import com.focusflow.app.ai.LocalLlm
import com.focusflow.app.ai.ModelCatalog
import com.focusflow.app.ai.ModelManager
import com.focusflow.app.ai.ModelSpec
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiSettingsViewModel(
    private val settings: AppSettings,
    val models: ModelManager,
    private val llm: LocalLlm,
    private val ai: AiService,
) : ViewModel() {
    val enabled = settings.aiEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val selected = settings.aiModelFile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val voiceOffline = settings.voiceOfflineOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val status = MutableStateFlow<String?>(null)
    val testing = MutableStateFlow(false)

    fun setEnabled(v: Boolean) { viewModelScope.launch { settings.setAiEnabled(v) } }
    fun setVoiceOffline(v: Boolean) { viewModelScope.launch { settings.setVoiceOfflineOnly(v) } }
    fun select(f: String) { viewModelScope.launch { models.select(f) } }
    fun delete(f: String) { viewModelScope.launch { llm.release(); models.delete(f) } }
    fun import(uri: android.net.Uri) {
        viewModelScope.launch {
            status.value = when (val r = models.import(uri)) {
                is ImportResult.Imported -> "Imported ${r.fileName}."
                is ImportResult.Rejected -> r.reason
            }
        }
    }

    fun test() {
        if (testing.value) return
        testing.value = true
        status.value = "Running the model… this can take a minute on the first run."
        viewModelScope.launch {
            val r = ai.selfTest()
            status.update {
                if (r.source == TaskOrigin.ON_DEVICE_AI) "The model works: it produced ${r.tasks.size} tasks from a sample plan."
                else "The model did not run. ${r.note ?: ""}".trim()
            }
            testing.value = false
        }
    }
}

private fun mb(b: Long) = if (b >= 1024L * 1024 * 1024) "%.1f GB".format(b / 1073741824.0) else "${b / (1024 * 1024)} MB"

@Composable
fun AiSettingsScreen(onBack: () -> Unit, onOpenProviders: () -> Unit = {}) {
    val vm = containerViewModel { AiSettingsViewModel(it.settings, it.models, it.llm, it.ai) }
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val voiceOffline by vm.voiceOffline.collectAsStateWithLifecycle()
    val installed by vm.models.installed.collectAsStateWithLifecycle()
    val download by vm.models.download.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ModelSpec?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }

    confirm?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Download ${spec.name}?") },
            text = {
                Text(
                    "This downloads about ${mb(spec.approxBytes)} from Hugging Face over your current connection. " +
                        "Apart from optional external AI providers you set up yourself, it is the only time FocusFlow uses the internet. After that, the AI runs entirely on your phone.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.models.startDownload(spec); confirm = null }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Offline AI & voice")
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use on-device AI", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Turns your description into tasks and writes summaries. No account, no API key, nothing leaves your phone. " +
                                "Without a model, simple built-in rules are used and labelled as such.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = vm::setEnabled)
                }
            }
        }
        item {
            AppCard {
                Text("External AI providers (optional)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use your own OpenAI-compatible, OpenRouter or Gemini account. When one is selected it is tried first, " +
                        "then the on-device model, then simple rules. Results always say which one produced them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenProviders) { Text("Manage providers") }
            }
        }
        item { SectionLabel("MODELS") }
        items(ModelCatalog.all.size) { i ->
            val spec = ModelCatalog.all[i]
            val have = installed.firstOrNull { it.fileName == spec.fileName }
            val dl = download as? DownloadState.Downloading
            val active = dl?.modelId == spec.id
            val failed = (download as? DownloadState.Failed)?.takeIf { it.modelId == spec.id }
            AppCard {
                Text(spec.name, style = MaterialTheme.typography.titleMedium)
                Text(spec.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    have != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == have.fileName || (selected == null && installed.first().fileName == have.fileName), onClick = { vm.select(have.fileName) })
                        Text("Use this (${mb(have.sizeBytes)})", modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.delete(have.fileName) }) { Text("Delete") }
                    }
                    active -> {
                        LinearProgressIndicator(progress = { if (dl!!.total > 0) (dl.bytes.toFloat() / dl.total).coerceIn(0f, 1f) else 0f })
                        Text("${mb(dl!!.bytes)} of ${mb(dl.total)}")
                        OutlinedButton(onClick = vm.models::cancelDownload) { Text("Pause") }
                    }
                    else -> {
                        failed?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { vm.models.dismissFailure(); confirm = spec }, enabled = download !is DownloadState.Downloading) { Text("Download") }
                    }
                }
            }
        }
        item {
            AppCard {
                Text("Already have a model file?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "If a model needs you to accept a license in your browser, download its .litertlm file there and import it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import model file") }
                OutlinedButton(onClick = vm::test, enabled = !testing && installed.isNotEmpty()) { Text("Test model") }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item {
            AppCard {
                SectionLabel("VOICE")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only use offline speech recognition", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "On: voice works only if your phone has an on-device recognizer, so audio never leaves the phone. " +
                                "Off: Android's default recognizer may be used, which on many phones sends audio to Google. Typing always works.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = voiceOffline, onCheckedChange = vm::setVoiceOffline)
                }
            }
        }
    }
}
