package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val voiceOffline = settings.voiceOfflineOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setVoiceOffline(value: Boolean) {
        viewModelScope.launch { settings.setVoiceOfflineOnly(value) }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit, onOpenProviders: () -> Unit = {}) {
    val vm = containerViewModel { AiSettingsViewModel(it.settings) }
    val voiceOffline by vm.voiceOffline.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                ScreenTitle("AI & voice")
            }
        }
        item {
            AppCard {
                SectionLabel("API-POWERED STUDY AI")
                Text(
                    "FocusFlow uses the API provider you configure for task suggestions and session summaries. " +
                        "There is no downloaded-model or rule-based AI fallback. Without a working API profile, " +
                        "you can still add and manage tasks manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenProviders, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage API providers")
                }
            }
        }
        item {
            AppCard {
                SectionLabel("VOICE INPUT PRIVACY")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only use offline speech recognition", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "On: voice works only if this phone has an on-device recognizer, so audio stays on the phone. " +
                                "Off: Android's default recognizer may send audio to its speech provider. Typed plans always work.",
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
