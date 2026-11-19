package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.ai.external.ExternalAiClient
import com.focusflow.app.ai.external.ProviderConfig
import com.focusflow.app.ai.external.ProviderRepository
import com.focusflow.app.ai.external.ProviderType
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ConfirmDialog
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiProvidersViewModel(private val repo: ProviderRepository) : ViewModel() {
    private val client = ExternalAiClient()
    val providers = repo.providers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selected = repo.selectedId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val status = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun hasKey(id: String) = repo.hasKey(id)

    fun save(config: ProviderConfig, key: String?) {
        viewModelScope.launch {
            val ok = repo.save(config, key)
            status.value = if (ok) "Saved." else "Could not store the API key securely on this phone, so nothing was saved."
        }
    }

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
    fun select(id: String?) { viewModelScope.launch { repo.select(id) } }
    fun setEnabled(c: ProviderConfig, v: Boolean) { viewModelScope.launch { repo.save(c.copy(enabled = v), null) } }

    /** Tests the saved config, or an unsaved draft with the key typed in the form. */
    fun test(config: ProviderConfig, typedKey: String?) {
        if (busy.value) return
        val key = typedKey?.takeIf { it.isNotBlank() } ?: repo.apiKey(config.id)
        if (key.isNullOrBlank()) { status.value = "Enter an API key first."; return }
        busy.value = true
        status.value = "Testing connection…"
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { client.test(config, key) }
            status.value = r.fold({ "Connection works." }, { "Test failed: ${it.message}" })
            busy.value = false
        }
    }
}

@Composable
fun AiProvidersScreen(onBack: () -> Unit) {
    val vm = containerViewModel { AiProvidersViewModel(it.aiProviders) }
    val providers by vm.providers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var deleting by remember { mutableStateOf<ProviderConfig?>(null) }

    editing?.let { cfg ->
        ProviderEditor(
            initial = cfg,
            hasKey = vm.hasKey(cfg.id),
            busy = busy,
            onTest = { c, k -> vm.test(c, k) },
            onSave = { c, k -> vm.save(c, k); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { cfg ->
        ConfirmDialog("Delete ${cfg.name}?", "The provider and its stored API key are removed from this phone.", "Delete",
            onDismiss = { deleting = null }) { vm.delete(cfg.id); deleting = null }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("AI providers")
            }
        }
        item {
            AppCard {
                Text(
                    "A selected, working API provider is required for AI task suggestions and session summaries. Your study plan and " +
                        "session facts are sent to that provider when you request AI help. API keys are encrypted with the Android " +
                        "Keystore and are never written to logs. If the API is unavailable, tasks can still be added manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == null, onClick = { vm.select(null) })
                    Text("No API provider (AI features unavailable; manual tasks still work)")
                }
            }
        }
        items(providers, key = { it.id }) { p ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == p.id, onClick = { vm.select(p.id) }, enabled = p.enabled)
                    Text("${p.name} · ${p.type.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = p.enabled, onCheckedChange = { vm.setEnabled(p, it) })
                }
                Text("${p.model} · ${if (vm.hasKey(p.id)) "key saved" else "no key yet"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = p }) { Text("Edit") }
                    OutlinedButton(onClick = { vm.test(p, null) }, enabled = !busy) { Text("Test") }
                    TextButton(onClick = { deleting = p }) { Text("Delete") }
                }
            }
        }
        item { SectionLabel("ADD A PROVIDER") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProviderType.entries.forEach { t ->
                    OutlinedButton(onClick = { editing = ProviderConfig.default(t) }) { Text(t.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    initial: ProviderConfig,
    hasKey: Boolean,
    busy: Boolean,
    onTest: (ProviderConfig, String?) -> Unit,
    onSave: (ProviderConfig, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var type by remember { mutableStateOf(initial.type) }
    var key by remember { mutableStateOf("") }
    fun draft() = initial.copy(name = name, endpoint = endpoint, model = model, type = type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI provider") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = {
                            // Only replace the defaults the user has not changed.
                            if (endpoint == type.defaultEndpoint) endpoint = t.defaultEndpoint
                            if (model == type.defaultModel) model = t.defaultModel
                            if (name == type.label) name = t.label
                            type = t
                        }, label = { Text(t.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key, { key = it },
                    label = { Text(if (hasKey) "API key (leave empty to keep the saved key)" else "API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onTest(draft(), key) }, enabled = !busy) { Text("Test connection") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft(), key.ifBlank { null }) }, enabled = endpoint.isNotBlank() && model.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
