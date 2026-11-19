package com.focusflow.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.launch.SnapGptLauncher
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/** Opt-in SnapGPT hand-off. Off by default; FocusFlow works the same when SnapGPT is missing. */
@Composable
fun SnapGptSettingsCard() {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by container.settings.snapGptEnabled.collectAsStateWithLifecycle(initialValue = false)
    val pkg by container.settings.snapGptPackage.collectAsStateWithLifecycle(initialValue = SnapGptLauncher.DEFAULT_PACKAGE)
    var draft by remember(pkg) { mutableStateOf(pkg) }
    val installed = remember(pkg) { SnapGptLauncher.isInstalled(context, pkg) }

    AppCard {
        SectionLabel("SNAPGPT")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Open SnapGPT when a session starts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Launches SnapGPT once per session (its floating assistant if the installed version supports it, otherwise the app). " +
                        "The focus timer keeps running; switch back to FocusFlow any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { v -> scope.launch { container.settings.setSnapGptEnabled(v) } })
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            label = { Text("SnapGPT package name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (installed) "SnapGPT is installed." else "SnapGPT was not found under this package name. Nothing will be launched.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft != pkg) OutlinedButton(onClick = { scope.launch { container.settings.setSnapGptPackage(draft) } }) { Text("Save package name") }
    }
}
