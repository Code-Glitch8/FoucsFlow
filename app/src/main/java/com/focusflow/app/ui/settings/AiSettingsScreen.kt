package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.ai.AiService
import com.focusflow.app.data.settings.ApiConfigStore
import com.focusflow.app.data.settings.ApiProfile
import com.focusflow.app.data.settings.ApiProvider
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSettingsViewModel(
    val configs: ApiConfigStore,
    private val settings: AppSettings,
    private val ai: AiService,
) : ViewModel() {
    val voiceOffline = settings.voiceOfflineOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val status = MutableStateFlow<String?>(null)
    val testing = MutableStateFlow(false)

    fun setVoiceOffline(v: Boolean) { viewModelScope.launch { settings.setVoiceOfflineOnly(v) } }

    fun save(profile: ApiProfile) {
        if (profile.name.isBlank() || profile.model.isBlank() || profile.apiKey.isBlank()) {
            status.value = "Enter a profile name, model and API key."
            return
        }
        if (profile.provider == ApiProvider.OPENAI_COMPATIBLE &&
            (!profile.baseUrl.startsWith("https://") || profile.baseUrl.isBlank())
        ) {
            status.value = "Enter a valid HTTPS base URL for this provider."
            return
        }
        configs.save(profile, activate = true)
        status.value = "API configuration saved securely and activated."
    }

    fun activate(id: String) {
        configs.setActive(id)
        status.value = "Active API changed."
    }

    fun delete(id: String) {
        configs.delete(id)
        status.value = "API configuration removed."
    }

    fun test() {
        if (testing.value) return
        testing.value = true
        status.value = "Testing the active API…"
        viewModelScope.launch {
            try {
                val result = ai.selfTest()
                status.value = if (result.tasks.isNotEmpty()) {
                    "Connection works. The API returned ${result.tasks.size} grounded task suggestions."
                } else {
                    result.note ?: "The API did not return usable task suggestions."
                }
            } finally {
                testing.value = false
            }
        }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { AiSettingsViewModel(it.apiConfigs, it.settings, it.ai) }
    val profiles by vm.configs.profiles.collectAsStateWithLifecycle()
    val activeId by vm.configs.activeId.collectAsStateWithLifecycle()
    val voiceOffline by vm.voiceOffline.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()

    var profileId by remember { mutableStateOf(ApiConfigStore.newId()) }
    var name by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(ApiProvider.GEMINI) }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    fun clearForm() {
        profileId = ApiConfigStore.newId()
        name = ""
        provider = ApiProvider.GEMINI
        baseUrl = ""
        model = ""
        apiKey = ""
        showKey = false
    }

    fun loadProfile(p: ApiProfile) {
        profileId = p.id
        name = p.name
        provider = p.provider
        baseUrl = p.baseUrl
        model = p.model
        apiKey = p.apiKey
        showKey = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("AI API & voice")
            }
        }
        item {
            AppCard {
                SectionLabel("API CONFIGURATION")
                Text(
                    "AI task generation and session summaries use the API profile you select here. There is no built-in model or rule-based task generator. " +
                        "Your API keys are encrypted on this phone with Android Keystore and are not included in backups or Wi-Fi sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel("PROVIDER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = provider == ApiProvider.GEMINI,
                        onClick = { provider = ApiProvider.GEMINI },
                        label = { Text("Gemini") },
                    )
                    FilterChip(
                        selected = provider == ApiProvider.OPENAI_COMPATIBLE,
                        onClick = {
                            provider = ApiProvider.OPENAI_COMPATIBLE
                            if (baseUrl.isBlank()) baseUrl = "https://api.openai.com/v1"
                        },
                        label = { Text("OpenAI-compatible") },
                    )
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    label = { Text("Configuration name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it.trim() },
                    label = { Text(if (provider == ApiProvider.GEMINI) "Base URL (optional)" else "API base URL") },
                    placeholder = { Text(if (provider == ApiProvider.GEMINI) "Default Gemini endpoint" else "https://api.openai.com/v1") },
                    supportingText = {
                        Text(
                            if (provider == ApiProvider.GEMINI) "Leave blank to use Google's Gemini API endpoint."
                            else "Use the provider's HTTPS OpenAI-compatible API base URL.",
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it.trim().take(120) },
                    label = { Text("Model ID") }, placeholder = { Text("Enter the model ID from your provider") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it.take(4096) },
                    label = { Text("API key") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.save(ApiProfile(profileId, name.trim(), provider, baseUrl.trim(), model.trim(), apiKey.trim()))
                        },
                    ) { Text("Save & activate") }
                    OutlinedButton(onClick = ::clearForm) { Text("Clear") }
                }
                status?.let {
                    Text(it, color = if (it.contains("failed", true) || it.contains("enter", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AppCard {
                SectionLabel("SAVED CONFIGURATIONS")
                if (profiles.isEmpty()) {
                    Text("No API profiles saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    profiles.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = activeId == p.id, onClick = { vm.activate(p.id) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${p.provider.name.replace('_', ' ')} · ${p.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { loadProfile(p) }) { Text("Edit") }
                            TextButton(onClick = { vm.delete(p.id) }) { Text("Delete") }
                        }
                    }
                }
                OutlinedButton(onClick = vm::test, enabled = activeId != null && !testing) {
                    Text(if (testing) "Testing…" else "Test active API")
                }
            }
        }
        item {
            AppCard {
                SectionLabel("VOICE INPUT")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only use offline speech recognition", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "On: voice works only if this phone has an on-device recognizer. Off: Android's default recognizer may send audio to its speech provider. Typing always works.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(checked = voiceOffline, onCheckedChange = vm::setVoiceOffline)
                }
            }
        }
    }
}
