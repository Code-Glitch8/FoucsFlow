package com.focusflow.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.BreakKind
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ConfirmDialog
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.TextInputDialog
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.formatClock

@Composable
fun FocusScreen(onStartSession: () -> Unit) {
    val vm = containerViewModel { FocusViewModel(it.sessions, it.subjects) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        if (ui.active == null) {
            IdleFocus(onStartSession)
        } else {
            ActiveFocus(ui, vm)
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun IdleFocus(onStartSession: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle("Focus")
        Text(
            "Pick a subject and chapters, say what you want to accomplish, and start a timed or open-ended session.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onStartSession, modifier = Modifier.heightIn(min = 52.dp)) {
            Text("Start studying")
        }
    }
}

@Composable
private fun ActiveFocus(ui: FocusUiState, vm: FocusViewModel) {
    val active = ui.active ?: return
    val s = active.session
    var showEmergency by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(s.subjectName, style = MaterialTheme.typography.titleLarge)
                if (ui.chapters.isNotEmpty()) {
                    Text(
                        ui.chapters.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- timer ----
        item {
            AppCard {
                val label: String
                val big: String
                when {
                    ui.onBreak -> { label = "ON BREAK"; big = formatClock(ui.breakRemainingMs) }
                    s.mode == SessionMode.TIMED -> { label = "REMAINING"; big = formatClock(ui.remainingMs ?: 0L) }
                    else -> { label = "FOCUSED"; big = formatClock(ui.focusElapsedMs) }
                }
                SectionLabel(label)
                Text(
                    big,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (s.mode == SessionMode.TIMED && s.plannedDurationMs != null && s.plannedDurationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (ui.focusElapsedMs.toFloat() / s.plannedDurationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Focused ${formatClock(ui.focusElapsedMs)} of ${formatClock(s.plannedDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (s.mode == SessionMode.OPEN_ENDED && ui.onBreak) {
                    Text(
                        "Focused so far: ${formatClock(ui.focusElapsedMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (s.strict) "Strict session" else "Relaxed session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- breaks ----
        item {
            AppCard {
                SectionLabel("BREAKS")
                val credits = active.credits()
                val cfg = active.breakConfig
                when {
                    !cfg.enabled -> Text(
                        "Breaks are turned off in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ui.onBreak -> {
                        Text("Enjoy your break. Focus resumes automatically when it ends.")
                        OutlinedButton(onClick = vm::endBreakEarly) { Text("End break now") }
                    }
                    !credits.any -> Text(
                        "Complete ${cfg.tasksForShortBreak} task${if (cfg.tasksForShortBreak == 1) "" else "s"} " +
                            "to earn a ${cfg.shortBreakMinutes}-minute break.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (credits.shortAvailable > 0) {
                            Button(onClick = { vm.startBreak(BreakKind.SHORT) }) {
                                Text("${cfg.shortBreakMinutes}-min break (${credits.shortAvailable})")
                            }
                        }
                        if (credits.longAvailable > 0) {
                            Button(onClick = { vm.startBreak(BreakKind.LONG) }) {
                                Text("${cfg.longBreakMinutes}-min break (${credits.longAvailable})")
                            }
                        }
                    }
                }
            }
        }

        // ---- tasks ----
        item { SectionLabel("TASKS · ${active.completedCount}/${active.tasks.size} done") }
        if (active.tasks.isEmpty()) {
            item {
                Text(
                    "No tasks planned. Add one to start earning breaks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(active.tasks, key = { it.link.id }) { row ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = row.link.completed,
                        onCheckedChange = { vm.setTaskCompleted(row.link.id, row.link.taskId, it) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        row.estimatedMinutes?.let {
                            Text(
                                "~$it min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!row.link.completed) {
                        IconButton(onClick = { vm.removeTask(row.link.id) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${row.title} from this session")
                        }
                    }
                }
                active.subtasks.filter { it.taskId == row.link.taskId }.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                        Checkbox(checked = sub.done, onCheckedChange = { vm.setSubtaskDone(sub.id, it) })
                        Text(sub.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showAddTask = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("  Add task")
            }
        }

        // ---- ending the session ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (ui.canFinishNormally) {
                    Button(onClick = { showEnd = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (s.strict) "Finish session" else "End session")
                    }
                } else {
                    Text(
                        if (s.mode == SessionMode.TIMED) {
                            "Strict session: it ends when the timer does."
                        } else {
                            "Strict session: finish your remaining tasks to end it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.strict) {
                    // Deliberately small and separate from normal controls.
                    TextButton(onClick = { showEmergency = true }) { Text("Emergency exit") }
                }
            }
        }
    }

    if (showAddTask) {
        TextInputDialog(
            title = "Add task", label = "What do you want to do?", confirmLabel = "Add",
            onDismiss = { showAddTask = false },
        ) { vm.addTask(it); showAddTask = false }
    }
    if (showEnd) {
        ConfirmDialog(
            title = "End this session?",
            message = "Unfinished tasks stay on your task list so you can pick them up later.",
            confirmLabel = "End session",
            onDismiss = { showEnd = false },
        ) { vm.finish(); showEnd = false }
    }
    if (showEmergency) {
        EmergencyExitDialog(onDismiss = { showEmergency = false }) { vm.emergencyExit(); showEmergency = false }
    }
}

/** The emergency exit needs a typed confirmation so it can't be hit by accident or on impulse. */
@Composable
private fun EmergencyExitDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency exit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This ends your strict session now and is recorded as an emergency exit in your history. " +
                        "Use it only if you really need to. Phone calls and system features are never blocked.",
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Type EXIT to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.trim().equals("EXIT", ignoreCase = true)) {
                Text("End session")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep studying") } },
    )
}
