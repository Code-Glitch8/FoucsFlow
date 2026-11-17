package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DARK)
    val defaultMode = settings.defaultMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionMode.TIMED)
    val defaultDuration = settings.defaultDurationMin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 25)

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settings.setThemeMode(m) } }
    fun setDefaultMode(m: SessionMode) { viewModelScope.launch { settings.setDefaultMode(m) } }
    fun setDefaultDuration(min: Int) { viewModelScope.launch { settings.setDefaultDurationMin(min) } }
}

private val DurationChoices = listOf(15, 25, 30, 45, 60, 90)

@Composable
fun SettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it.settings) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val mode by vm.defaultMode.collectAsStateWithLifecycle()
    val duration by vm.defaultDuration.collectAsStateWithLifecycle()

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

        item {
            AppCard {
                Text("More settings", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    "Break rules, blocked apps, YouTube Shorts, floating assistant, offline AI model, " +
                        "voice and data export/import are added in later phases.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
