package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.BreakConfigRepository
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.domain.BreakConfig
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettings,
    private val breaks: BreakConfigRepository,
) : ViewModel() {
    val floatingEnabled = settings.floatingEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setFloating(v: Boolean) { viewModelScope.launch { settings.setFloatingEnabled(v) } }
    fun resetBubble() { viewModelScope.launch { settings.resetBubblePosition() } }
    val breakConfig = breaks.global.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreakConfig())
    fun saveBreakConfig(c: BreakConfig) { viewModelScope.launch { breaks.save(c) } }

    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DARK)
    val defaultMode = settings.defaultMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionMode.TIMED)
    val defaultDuration = settings.defaultDurationMin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 25)

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settings.setThemeMode(m) } }
    fun setDefaultMode(m: SessionMode) { viewModelScope.launch { settings.setDefaultMode(m) } }
    fun setDefaultDuration(min: Int) { viewModelScope.launch { settings.setDefaultDurationMin(min) } }
}

private val DurationChoices = listOf(15, 25, 30, 45, 60, 90)

@Composable
fun SettingsScreen(onOpenBlocking: () -> Unit, onOpenAi: () -> Unit, onOpenData: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it.settings, it.breakConfig) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val mode by vm.defaultMode.collectAsStateWithLifecycle()
    val duration by vm.defaultDuration.collectAsStateWithLifecycle()
    val breaks by vm.breakConfig.collectAsStateWithLifecycle()
    val floating by vm.floatingEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Settings") }

        item {
            AppCard {
                SectionLabel("THEME")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries) { m ->
                        FilterChip(
                            selected = theme == m,
                            onClick = { vm.setTheme(m) },
                            label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }

        item {
            AppCard {
                SectionLabel("DEFAULT FOCUS MODE")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SessionMode.entries) { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { vm.setDefaultMode(m) },
                            label = { Text(if (m == SessionMode.TIMED) "Timed" else "Open-ended") },
                        )
                    }
                }
                SectionLabel("DEFAULT SESSION LENGTH (TIMED)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DurationChoices) { d ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { vm.setDefaultDuration(d) },
                            label = { Text("$d min") },
                        )
                    }
                }
            }
        }

        item { SnapGptSettingsCard() }

        item {
            AppCard {
                SectionLabel("EARNED BREAKS")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Earn breaks by completing tasks", modifier = Modifier.weight(1f))
                    Switch(checked = breaks.enabled, onCheckedChange = { vm.saveBreakConfig(breaks.copy(enabled = it)) })
                }
                if (breaks.enabled) {
                    Stepper("Tasks for a short break", breaks.tasksForShortBreak, 1, 20) {
                        vm.saveBreakConfig(breaks.copy(tasksForShortBreak = it))
                    }
                    Stepper("Short break (minutes)", breaks.shortBreakMinutes, 1, 60) {
                        vm.saveBreakConfig(breaks.copy(shortBreakMinutes = it))
                    }
                    Stepper("Tasks for a long break", breaks.tasksForLongBreak, 1, 40) {
                        vm.saveBreakConfig(breaks.copy(tasksForLongBreak = it))
                    }
                    Stepper("Long break (minutes)", breaks.longBreakMinutes, 1, 120) {
                        vm.saveBreakConfig(breaks.copy(longBreakMinutes = it))
                    }
                    Text(
                        "When one completed task qualifies for both, you get the long break only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            AppCard {
                SectionLabel("FLOATING ASSISTANT")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show bubble during sessions", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A draggable bubble over the apps you can use, with your timer, tasks and breaks. " +
                                "Needs the \"display over other apps\" permission (see Blocking).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = floating, onCheckedChange = vm::setFloating)
                }
                OutlinedButton(onClick = vm::resetBubble) { Text("Reset bubble position") }
            }
        }

        item {
            AppCard {
                SectionLabel("BLOCKING & YOUTUBE")
                Text(
                    "Choose which apps are blocked during sessions, block YouTube Shorts and review permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenBlocking) { Text("Open blocking settings") }
            }
        }

        item {
            AppCard {
                SectionLabel("OFFLINE AI & VOICE")
                Text(
                    "Download or import an on-device model, and choose how voice input behaves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenAi) { Text("Open AI & voice settings") }
            }
        }

        item {
            AppCard {
                SectionLabel("DATA, PRESETS & PRIVACY")
                Text(
                    "Manage presets, export or import a backup, and reset your data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenData) { Text("Open data settings") }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text("$value", modifier = Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}
