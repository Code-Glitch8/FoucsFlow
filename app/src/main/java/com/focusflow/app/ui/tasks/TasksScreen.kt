package com.focusflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel

@Composable
fun TasksScreen(onManageSubjects: () -> Unit) {
    val vm = containerViewModel { TasksViewModel(it.tasks, it.subjects) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenTitle("Tasks") }
            item {
                Text(
                    "Open tasks, including anything left unfinished from earlier sessions. " +
                        "Tasks are only ever completed by you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAdd = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("  Add task")
                    }
                    OutlinedButton(onClick = onManageSubjects) { Text("Subjects & chapters") }
                }
            }

            item { SectionLabel("OPEN (${state.open.size})") }
            if (state.open.isEmpty()) {
                item {
                    Text(
                        "Nothing open. Add a task, or generate tasks when you start a study session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.open, key = { "o${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    context = contextLine(state, task),
                    primaryIcon = { Icon(Icons.Outlined.Check, contentDescription = "Mark ${task.title} done") },
                    onPrimary = { vm.markDone(task) },
                    secondaryIcon = { Icon(Icons.Outlined.Close, contentDescription = "Dismiss ${task.title}") },
                    onSecondary = { vm.dismiss(task) },
                )
            }

            if (state.done.isNotEmpty()) {
                item { SectionLabel("RECENTLY COMPLETED") }
                items(state.done, key = { "d${it.id}" }) { task ->
                    TaskCard(
                        task = task,
                        context = contextLine(state, task),
                        primaryIcon = { Icon(Icons.Outlined.Undo, contentDescription = "Reopen ${task.title}") },
                        onPrimary = { vm.reopen(task) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAdd) {
        AddTaskDialog(
            state = state,
            onDismiss = { showAdd = false },
            onAdd = { title, subjectId, chapterId, minutes ->
                vm.add(title, subjectId, chapterId, minutes)
                showAdd = false
            },
        )
    }
}

private fun contextLine(state: TasksUiState, task: TaskEntity): String {
    val parts = listOfNotNull(
        state.subjectName(task.subjectId),
        state.chapterName(task.chapterId),
        task.estimatedMinutes?.let { "~$it min" },
        when (task.origin) {
            TaskOrigin.API_AI -> "API AI"
            TaskOrigin.ON_DEVICE_AI -> "on-device AI"
            TaskOrigin.RULE_BASED -> "rule-based"
            TaskOrigin.MANUAL -> null
        },
    )
    return parts.joinToString(" · ")
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    context: String,
    primaryIcon: @Composable () -> Unit,
    onPrimary: () -> Unit,
    secondaryIcon: (@Composable () -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                if (context.isNotEmpty()) {
                    Text(
                        context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onPrimary) { primaryIcon() }
            if (secondaryIcon != null && onSecondary != null) {
                IconButton(onClick = onSecondary) { secondaryIcon() }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    state: TasksUiState,
    onDismiss: () -> Unit,
    onAdd: (title: String, subjectId: Long?, chapterId: Long?, minutes: Int?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var subjectId by remember { mutableStateOf<Long?>(null) }
    var chapterId by remember { mutableStateOf<Long?>(null) }
    val chaptersForSubject = state.chapters.filter { it.subjectId == subjectId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What do you want to do?") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Estimate in minutes (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Subject (optional)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.subjects, key = { it.id }) { s ->
                        FilterChip(
                            selected = subjectId == s.id,
                            onClick = {
                                subjectId = if (subjectId == s.id) null else s.id
                                chapterId = null
                            },
                            label = { Text(s.name) },
                        )
                    }
                }
                if (chaptersForSubject.isNotEmpty()) {
                    SectionLabel("Chapter (optional)")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chaptersForSubject, key = { it.id }) { c ->
                            FilterChip(
                                selected = chapterId == c.id,
                                onClick = { chapterId = if (chapterId == c.id) null else c.id },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, subjectId, chapterId, minutes.toIntOrNull()) },
                enabled = title.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
