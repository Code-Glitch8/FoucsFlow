package com.focusflow.app.ui.blocking

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.data.db.AppCategory
import com.focusflow.app.domain.ShortsMode
import com.focusflow.app.service.Permissions
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel

@Composable
fun BlockingScreen(onBack: () -> Unit, onTestDetector: () -> Unit) {
    val context = LocalContext.current
    val vm = containerViewModel { BlockingViewModel(it.appContext, it.blockRules, it.settings) }
    val ui by vm.state.collectAsStateWithLifecycle()

    var accessibilityOn by remember { mutableStateOf(false) }
    var usageOn by remember { mutableStateOf(false) }
    var overlayOn by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        accessibilityOn = Permissions.accessibilityEnabled(context)
        usageOn = Permissions.usageAccessGranted(context)
        overlayOn = Permissions.overlayGranted(context)
        onPauseOrDispose { }
    }
    var explain by remember { mutableStateOf<Explain?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Blocking")
            }
        }

        item {
            Text(
                "Blocking works only while a session runs. Android doesn't let a normal app lock the phone, " +
                    "so a determined user can still switch it off in system settings; emergency calls and " +
                    "essential system apps are never blocked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- permissions ----
        item {
            AppCard {
                SectionLabel("PERMISSIONS")
                PermissionRow(
                    "Accessibility service", accessibilityOn,
                    "Required to block apps and YouTube Shorts.",
                ) { explain = Explain.Accessibility }
                PermissionRow(
                    "Usage access", usageOn,
                    "Optional fallback for app blocking if the accessibility service is off.",
                ) { explain = Explain.Usage }
                PermissionRow(
                    "Display over other apps", overlayOn,
                    "Lets the block screen appear reliably, and powers the floating assistant.",
                ) { explain = Explain.Overlay }
            }
        }

        // ---- YouTube ----
        item {
            AppCard {
                SectionLabel("YOUTUBE")
                Text("Block Shorts (normal videos, search and subscriptions keep working)", style = MaterialTheme.typography.bodyLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ShortsMode.entries) { m ->
                        FilterChip(
                            selected = ui.shortsMode == m,
                            onClick = { vm.setShortsMode(m) },
                            label = {
                                Text(when (m) {
                                    ShortsMode.OFF -> "Off"
                                    ShortsMode.FOCUS_ONLY -> "During sessions"
                                    ShortsMode.ALWAYS -> "Always"
                                })
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip the Home feed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "When YouTube opens on Home, jump to Subscriptions instead. This reduces recommendations; " +
                                "it cannot remove them from other screens such as a video's suggestions. " +
                                "Follows the setting above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.homeRedirect, onCheckedChange = vm::setHomeRedirect)
                }
                OutlinedButton(onClick = onTestDetector) { Text("Test the YouTube detector") }
                Text(
                    "YouTube has no official way to do this, so detection reads the screen's structure and may " +
                        "need adjusting after a YouTube update. English tab names are used as a second signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- quick add ----
        item {
            AppCard {
                SectionLabel("QUICK ADD")
                Text(
                    "Adds every installed app of that kind to your blocked list (you can undo any below).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(AppCategory.GAME to "Games", AppCategory.SOCIAL to "Social media", AppCategory.ENTERTAINMENT to "Entertainment")) { (cat, name) ->
                        val n = ui.countIn(cat)
                        FilterChip(
                            selected = false,
                            enabled = n > 0,
                            onClick = { vm.blockAll(cat) },
                            label = { Text("$name ($n)") },
                        )
                    }
                }
            }
        }

        // ---- app list ----
        item {
            SectionLabel("APPS · ${ui.blocked.size} blocked · ${ui.allowed.size} allowed")
            Text(
                "Blocked apps close during a session. Allowed apps are never blocked, even if a category was added.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.loading) item { Text("Loading apps…") }
        items(ui.visible, key = { it.packageName }) { app ->
            val choice = ui.choice(app.packageName)
            AppCard {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = choice == AppChoice.BLOCK, onClick = { vm.choose(app, if (choice == AppChoice.BLOCK) AppChoice.NONE else AppChoice.BLOCK) }, label = { Text("Block") })
                    FilterChip(selected = choice == AppChoice.ALLOW, onClick = { vm.choose(app, if (choice == AppChoice.ALLOW) AppChoice.NONE else AppChoice.ALLOW) }, label = { Text("Allow") })
                }
                ui.blocked[app.packageName]?.let { rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rule.blockedDuringBreak, onCheckedChange = { vm.setBlockedDuringBreak(rule, it) })
                        Text("Stay blocked during breaks", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    explain?.let { e ->
        AlertDialog(
            onDismissRequest = { explain = null },
            title = { Text(e.title) },
            text = { Text(e.body) },
            confirmButton = {
                TextButton(onClick = { launch(context, e); explain = null }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { explain = null }) { Text("Not now") } },
        )
    }
}

private enum class Explain(val title: String, val body: String) {
    Accessibility(
        "Enable the accessibility service",
        "FocusFlow uses Android's accessibility service to see which app is in front, so it can close apps you " +
            "blocked, and to recognise YouTube's Shorts screen. It only looks at the app's name, and, inside " +
            "YouTube only, at screen structure such as tab names. It does not read messages or text you type, " +
            "and nothing is stored or sent anywhere.\n\nIn the next screen, open FocusFlow and turn it on.",
    ),
    Usage(
        "Grant usage access",
        "Usage access lets FocusFlow see which app was opened most recently. It is only a fallback for blocking " +
            "when the accessibility service is off. Nothing is stored or sent anywhere.",
    ),
    Overlay(
        "Allow display over other apps",
        "This lets FocusFlow show the block screen reliably and, in a later update of this app, the floating " +
            "study assistant over the apps you are allowed to use.",
    ),
}

private fun launch(context: Context, e: Explain) {
    val intent: Intent = when (e) {
        Explain.Accessibility -> Permissions.accessibilitySettingsIntent()
        Explain.Usage -> Permissions.usageAccessIntent()
        Explain.Overlay -> Permissions.overlayIntent(context)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, why: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (granted) "On. " else "Off. ") + why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) Button(onClick = onFix) { Text("Enable") }
    }
}
