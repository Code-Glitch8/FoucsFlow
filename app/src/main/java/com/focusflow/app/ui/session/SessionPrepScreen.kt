package com.focusflow.app.ui.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.DraftTask
import com.focusflow.app.service.FocusSessionService
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.TextInputDialog
import com.focusflow.app.ui.common.containerViewModel

private val DurationChoices = listOf(15, 25, 30, 45, 60, 90)

private sealed interface PrepDialog {
    data object NewSubject : PrepDialog
    data object NewChapter : PrepDialog
    data object NewTask : PrepDialog
    data class EditTask(val index: Int) : PrepDialog
    data object SavePreset : PrepDialog
}

@Composable
fun SessionPrepScreen(onBack: () -> Unit, onManageSubjects: () -> Unit) {
    val vm = containerViewModel {
        SessionPrepViewModel(it.sessions, it.subjects, it.tasks, it.presets, it.settings, it.ai, com.focusflow.app.speech.VoiceRecognizer(it.appContext))
    }
    val ui by vm.state.collectAsStateWithLifecycle()
    val form = ui.form
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<PrepDialog?>(null) }
    var customDuration by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) {
        vm.started.collect {
            FocusSessionService.start(context)
            onBack()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoice()
    }
    fun toggleMic() {
        if (ui.ai.listening) { vm.stopVoice(); return }
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (ok) vm.startVoice() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Notification permission only affects the ongoing notification, never the session itself.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    fun startWithPermission() {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needs) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    ScreenTitle("Start studying")
                }
            }

            // ---- presets ----
            item {
                AppCard {
                    SectionLabel("PRESETS")
                    if (ui.presets.isEmpty()) {
                        Text(
                            "Save your setup below to reuse it in one tap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ui.presets, key = { it.id }) { p ->
                                FilterChip(
                                    selected = form.loadedPresetId == p.id,
                                    onClick = { vm.loadPreset(p.id) },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { dialog = PrepDialog.SavePreset }) { Text("Save as preset") }
                        if (form.loadedPresetId != null) {
                            OutlinedButton(onClick = { vm.savePreset("", updateExisting = true) }) { Text("Update preset") }
                            TextButton(onClick = vm::deleteLoadedPreset) { Text("Delete") }
                        }
                    }
                }
            }

            // ---- subject ----
            item {
                AppCard {
                    SectionLabel("SUBJECT")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.subjects, key = { it.id }) { s ->
                            FilterChip(
                                selected = form.subjectId == s.id,
                                onClick = { vm.selectSubject(s) },
                                label = { Text(s.name) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { dialog = PrepDialog.NewSubject },
                                label = { Text("Custom") },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            )
                        }
                    }
                }
            }

            // ---- chapters ----
            if (ui.subject != null) {
                item {
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SectionLabel("CHAPTERS (${form.chapterIds.size} selected)")
                            TextButton(onClick = onManageSubjects) { Text("Manage") }
                        }
                        if (ui.chapterCount > 0) {
                            OutlinedTextField(
                                value = form.chapterQuery,
                                onValueChange = vm::setChapterQuery,
                                placeholder = { Text("Search chapters") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (ui.chapters.isEmpty()) {
                            Text(
                                if (ui.chapterCount == 0) "No chapters for ${ui.subject!!.name} yet." else "No chapters match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.heightIn(max = 260.dp)) {
                            // Bounded height keeps long chapter lists scrollable without nesting scroll containers.
                            ui.chapters.take(40).forEach { c ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = c.id in form.chapterIds,
                                        onCheckedChange = { vm.toggleChapter(c) },
                                    )
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (ui.chapters.size > 40) {
                                Text(
                                    "Showing 40 of ${ui.chapters.size}. Search to narrow the list.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(onClick = { dialog = PrepDialog.NewChapter }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("  New chapter")
                        }
                    }
                }
            }

            // ---- description ----
            item {
                AppCard {
                    SectionLabel("WHAT DO YOU WANT TO ACCOMPLISH?")
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = vm::setDescription,
                        placeholder = { Text("e.g. Revise quadratic equations, solve 20 questions and review the ones I got wrong") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ui.ai.listening) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "  Listening…" + if (ui.ai.partial.isNotBlank()) " “${ui.ai.partial}”" else "",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { toggleMic() },
                            enabled = ui.ai.voice != com.focusflow.app.speech.VoiceAvailability.UNAVAILABLE,
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                            Text(if (ui.ai.listening) "  Stop" else "  Speak")
                        }
                        Button(
                            onClick = vm::generateTasks,
                            enabled = form.description.isNotBlank() && !ui.ai.generating,
                        ) { Text(if (ui.ai.generating) "Working…" else "Generate tasks") }
                    }
                    if (ui.ai.voice == com.focusflow.app.speech.VoiceAvailability.UNAVAILABLE) {
                        Text(
                            "Voice input isn't available on this phone; type your plan instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (ui.ai.proposals.isNotEmpty() || ui.ai.note != null) {
                item {
                    AppCard {
                        val label = if (ui.ai.origin == com.focusflow.app.data.db.TaskOrigin.ON_DEVICE_AI)
                            "SUGGESTED BY ON-DEVICE AI" else "SUGGESTED BY SIMPLE RULES (NOT AI)"
                        SectionLabel(label)
                        ui.ai.note?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ui.ai.proposals.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = p.selected, onCheckedChange = { vm.toggleProposal(i) })
                                Column(Modifier.weight(1f)) {
                                    Text(p.title)
                                    val meta = listOfNotNull(p.minutes?.let { "~$it min" }, p.subtasks.takeIf { it.isNotEmpty() }?.joinToString(" · "))
                                    if (meta.isNotEmpty()) Text(
                                        meta.joinToString(" — "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (ui.ai.proposals.isNotEmpty()) {
                            Text(
                                "Review these, then add them to your task list. You can edit each one afterwards. Nothing starts until you decide.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = vm::acceptProposals, enabled = ui.ai.proposals.any { it.selected }) { Text("Add selected") }
                                TextButton(onClick = vm::discardProposals) { Text("Discard") }
                            }
                        } else {
                            TextButton(onClick = vm::discardProposals) { Text("Dismiss") }
                        }
                    }
                }
            }

            // ---- tasks ----
            item {
                AppCard {
                    SectionLabel("TASKS (${form.tasks.size})")
                    if (form.tasks.isEmpty()) {
                        Text(
                            "Add the concrete things you will finish. Breaks are earned by completing tasks, " +
                                "and only you can mark a task done.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(form.tasks, key = { i, t -> "${t.existingTaskId ?: -1}-$i-${t.title}" }) { index, task ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            val meta = listOfNotNull(
                                task.estimatedMinutes?.let { "~$it min" },
                                ui.allChapters.firstOrNull { it.id == task.chapterId }?.name,
                                if (task.existingTaskId != null) "unfinished from before" else null,
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            task.subtasks.forEach {
                                Text(
                                    "• $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (task.existingTaskId == null) {
                            IconButton(onClick = { dialog = PrepDialog.EditTask(index) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit task ${task.title}")
                            }
                        }
                        IconButton(onClick = { vm.removeTask(index) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove task ${task.title}")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { dialog = PrepDialog.NewTask }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Add task")
                }
            }

            if (ui.resumable.isNotEmpty()) {
                item {
                    AppCard {
                        SectionLabel("UNFINISHED FROM EARLIER SESSIONS")
                        ui.resumable.take(8).forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.bodyLarge)
                                    val meta = listOfNotNull(
                                        ui.subjects.firstOrNull { it.id == t.subjectId }?.name,
                                        ui.allChapters.firstOrNull { it.id == t.chapterId }?.name,
                                    ).joinToString(" · ")
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(onClick = { vm.resume(t) }) { Text("Add") }
                            }
                        }
                    }
                }
            }

            // ---- mode & duration ----
            item {
                AppCard {
                    SectionLabel("FOCUS MODE")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.mode == SessionMode.TIMED,
                            onClick = { vm.setMode(SessionMode.TIMED) },
                            label = { Text("Timed") },
                        )
                        FilterChip(
                            selected = form.mode == SessionMode.OPEN_ENDED,
                            onClick = { vm.setMode(SessionMode.OPEN_ENDED) },
                            label = { Text("Open-ended") },
                        )
                    }
                    if (form.mode == SessionMode.TIMED) {
                        SectionLabel("DURATION")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DurationChoices) { d ->
                                FilterChip(
                                    selected = !customDuration && form.durationMinutes == d,
                                    onClick = { customDuration = false; vm.setDuration(d) },
                                    label = { Text("$d min") },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = customDuration || form.durationMinutes !in DurationChoices,
                                    onClick = { customDuration = true },
                                    label = { Text("Custom") },
                                )
                            }
                        }
                        if (customDuration || form.durationMinutes !in DurationChoices) {
                            var text by remember { mutableStateOf(form.durationMinutes.toString()) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { v ->
                                    text = v.filter(Char::isDigit).take(3)
                                    text.toIntOrNull()?.takeIf { it > 0 }?.let(vm::setDuration)
                                },
                                label = { Text("Minutes") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Text(
                            "No end time. A stopwatch runs until you finish.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- strict ----
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Strict session", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "No ordinary early exit. Timed sessions end when the timer does; open-ended " +
                                    "ones when your tasks are done. A separate emergency exit always exists.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = form.strict, onCheckedChange = vm::setStrict)
                    }
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        Button(
            onClick = { startWithPermission() },
            enabled = ui.canStart,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 52.dp),
        ) {
            Text(
                when {
                    ui.subject == null -> "Choose a subject to start"
                    ui.ai.proposals.isNotEmpty() -> "Add or discard suggested tasks first"
                    else -> "Start session"
                },
            )
        }
    }

    when (val d = dialog) {
        null -> Unit
        PrepDialog.NewSubject -> TextInputDialog(
            title = "New subject", label = "Subject name", confirmLabel = "Add",
            onDismiss = { dialog = null },
        ) { vm.addSubject(it); dialog = null }
        PrepDialog.NewChapter -> TextInputDialog(
            title = "New chapter", label = "Chapter name", confirmLabel = "Add",
            onDismiss = { dialog = null },
        ) { vm.addChapter(it); dialog = null }
        PrepDialog.SavePreset -> TextInputDialog(
            title = "Save preset", label = "Preset name (e.g. JEE Mathematics)", confirmLabel = "Save",
            onDismiss = { dialog = null },
        ) { vm.savePreset(it, updateExisting = false); dialog = null }
        PrepDialog.NewTask -> TaskDialog(
            title = "Add task",
            initial = null,
            chapterOptions = ui.allChapters.filter { it.id in form.chapterIds },
            onDismiss = { dialog = null },
        ) { t, m, c, s -> vm.addTask(t, m, c, s); dialog = null }
        is PrepDialog.EditTask -> TaskDialog(
            title = "Edit task",
            initial = form.tasks.getOrNull(d.index),
            chapterOptions = ui.allChapters.filter { it.id in form.chapterIds },
            onDismiss = { dialog = null },
        ) { t, m, c, s -> vm.updateTask(d.index, t, m, c, s); dialog = null }
    }
}

@Composable
private fun TaskDialog(
    title: String,
    initial: DraftTask?,
    chapterOptions: List<com.focusflow.app.data.db.ChapterEntity>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, minutes: Int?, chapterId: Long?, subtasks: List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.title ?: "") }
    var minutes by remember { mutableStateOf(initial?.estimatedMinutes?.toString() ?: "") }
    var chapterId by remember { mutableStateOf(initial?.chapterId) }
    var subtasks by remember { mutableStateOf(initial?.subtasks?.joinToString("\n") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Task") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Estimate in minutes (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subtasks, onValueChange = { subtasks = it },
                    label = { Text("Subtasks (optional, one per line)") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chapterOptions.size > 1) {
                    SectionLabel("Chapter (optional)")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chapterOptions, key = { it.id }) { c ->
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
                enabled = text.isNotBlank(),
                onClick = {
                    onConfirm(
                        text,
                        minutes.toIntOrNull()?.takeIf { it > 0 },
                        chapterId,
                        subtasks.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
