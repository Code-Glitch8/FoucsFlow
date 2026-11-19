package com.focusflow.app.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.StudyPresetEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.data.db.WidgetSize
import com.focusflow.app.data.db.WidgetType
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.repo.WidgetRepository
import com.focusflow.app.data.repo.WidgetSaveResult
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ConfirmDialog
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WidgetsUi(
    val widgets: List<DashboardWidgetEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val openTasks: List<TaskEntity> = emptyList(),
    val presets: List<StudyPresetEntity> = emptyList(),
)

class WidgetsViewModel(
    private val repo: WidgetRepository,
    subjects: SubjectRepository,
    tasks: TaskRepository,
    presets: PresetRepository,
) : ViewModel() {
    val ui = combine(repo.widgets, subjects.subjects, subjects.allChapters, tasks.openTasks, presets.allPresets) { w, s, c, t, p ->
        WidgetsUi(w, s, c, t, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetsUi())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun save(w: DashboardWidgetEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            when (repo.save(w)) {
                is WidgetSaveResult.Saved -> onSaved()
                WidgetSaveResult.BlankTitle -> _messages.tryEmit("Give the widget a title")
            }
        }
    }

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
    fun move(id: String, delta: Int) { viewModelScope.launch { repo.move(id, delta) } }
    fun setVisible(id: String, v: Boolean) { viewModelScope.launch { repo.setVisible(id, v) } }
}

private val DurationChoices = listOf(15, 25, 30, 45, 60, 90)

/** "Edit dashboard": add, edit, delete, reorder and hide/show widgets. Everything persists in Room. */
@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { WidgetsViewModel(it.widgets, it.subjects, it.tasks, it.presets) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DashboardWidgetEntity?>(null) }
    var deleting by remember { mutableStateOf<DashboardWidgetEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(vm) { vm.messages.collect { message = it } }

    editing?.let { w ->
        WidgetEditor(
            initial = w,
            ui = ui,
            error = message,
            onSave = { vm.save(it) { editing = null; message = null } },
            onDismiss = { editing = null; message = null },
        )
    }
    deleting?.let { w ->
        ConfirmDialog("Delete \"${w.title}\"?", "The widget is removed from your dashboard. Your sessions and tasks are not affected.", "Delete",
            onDismiss = { deleting = null }) { vm.delete(w.id); deleting = null }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    ScreenTitle("Edit dashboard")
                }
            }
            item {
                Text(
                    "Widgets start a session the moment you tap them on the dashboard. Reorder with the arrows; hidden widgets stay saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.widgets.isEmpty()) {
                item { Text("No widgets yet. Add one below.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(ui.widgets, key = { it.id }) { w ->
                val idx = ui.widgets.indexOfFirst { it.id == w.id }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(WidgetStyle.icon(w.icon), contentDescription = null, tint = WidgetStyle.color(w.colorKey))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                WidgetStyle.typeLabel(w.type) + " · " + WidgetStyle.subtitle(
                                    w,
                                    ui.subjects.firstOrNull { it.id == w.subjectId }?.name,
                                    ui.presets.firstOrNull { it.id == w.presetId }?.name,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = w.visible, onCheckedChange = { vm.setVisible(w.id, it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.move(w.id, -1) }, enabled = idx > 0) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(onClick = { vm.move(w.id, 1) }, enabled = idx < ui.widgets.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { editing = w }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { deleting = w }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = DashboardWidgetEntity(type = WidgetType.TIMER, title = "") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add widget") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun WidgetEditor(
    initial: DashboardWidgetEntity,
    ui: WidgetsUi,
    error: String?,
    onSave: (DashboardWidgetEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var w by remember { mutableStateOf(initial) }
    var chapterQuery by remember { mutableStateOf("") }
    val chapterSel = w.chapterIdSet()
    val taskSel = w.taskIdList().toSet()
    val startsSession = w.type != WidgetType.CUSTOM || w.action == WidgetAction.START_SESSION
    val showSubject = w.type == WidgetType.SUBJECT || w.type == WidgetType.HOMEWORK || (w.type == WidgetType.CUSTOM && startsSession)
    val showModeChoice = w.type == WidgetType.SUBJECT || w.type == WidgetType.HOMEWORK || (w.type == WidgetType.CUSTOM && startsSession)
    val showDuration = (w.type == WidgetType.TIMER || (showModeChoice && w.mode == SessionMode.TIMED))
    val showStrict = w.type != WidgetType.PRESET && startsSession
    val valid = w.title.isNotBlank() &&
        (w.type != WidgetType.SUBJECT || w.subjectId != null) &&
        (w.type != WidgetType.PRESET || w.presetId != null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") }
                    Text(if (initial.title.isEmpty()) "New widget" else "Edit widget", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { onSave(w) }, enabled = valid) { Text("Save") }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    item {
                        AppCard {
                            SectionLabel("TYPE")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(WidgetType.entries) { t ->
                                    FilterChip(
                                        selected = w.type == t,
                                        onClick = {
                                            w = w.copy(
                                                type = t,
                                                mode = when (t) {
                                                    WidgetType.OPEN_ENDED -> SessionMode.OPEN_ENDED
                                                    WidgetType.TIMER -> SessionMode.TIMED
                                                    else -> w.mode
                                                },
                                                icon = if (w.icon == initial.icon && initial.title.isEmpty()) defaultIcon(t) else w.icon,
                                            )
                                        },
                                        label = { Text(WidgetStyle.typeLabel(t)) },
                                    )
                                }
                            }
                            OutlinedTextField(w.title, { w = w.copy(title = it) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (w.type == WidgetType.CUSTOM) {
                        item {
                            AppCard {
                                SectionLabel("WHEN TAPPED")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(WidgetAction.entries) { a ->
                                        FilterChip(selected = w.action == a, onClick = { w = w.copy(action = a) }, label = { Text(WidgetStyle.actionLabel(a)) })
                                    }
                                }
                            }
                        }
                    }
                    if (showModeChoice) {
                        item {
                            AppCard {
                                SectionLabel("MODE")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = w.mode == SessionMode.TIMED, onClick = { w = w.copy(mode = SessionMode.TIMED) }, label = { Text("Timed") })
                                    FilterChip(selected = w.mode == SessionMode.OPEN_ENDED, onClick = { w = w.copy(mode = SessionMode.OPEN_ENDED) }, label = { Text("Open-ended") })
                                }
                            }
                        }
                    }
                    if (showDuration) {
                        item {
                            AppCard {
                                SectionLabel("DURATION")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(DurationChoices) { d ->
                                        FilterChip(selected = w.durationMinutes == d, onClick = { w = w.copy(durationMinutes = d) }, label = { Text("$d min") })
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { w = w.copy(durationMinutes = (w.durationMinutes - 1).coerceAtLeast(1)) }) { Icon(Icons.Filled.Remove, contentDescription = "Less") }
                                    OutlinedTextField(
                                        value = w.durationMinutes.toString(),
                                        onValueChange = { t -> t.filter(Char::isDigit).take(3).toIntOrNull()?.let { w = w.copy(durationMinutes = it.coerceIn(1, 600)) } },
                                        label = { Text("Minutes") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { w = w.copy(durationMinutes = (w.durationMinutes + 1).coerceAtMost(600)) }) { Icon(Icons.Filled.Add, contentDescription = "More") }
                                }
                            }
                        }
                    }
                    if (showStrict) {
                        item {
                            AppCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Strict session", style = MaterialTheme.typography.titleMedium)
                                        Text("Makes leaving the session harder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = w.strict, onCheckedChange = { w = w.copy(strict = it) })
                                }
                            }
                        }
                    }
                    if (showSubject) {
                        item {
                            AppCard {
                                SectionLabel(if (w.type == WidgetType.SUBJECT) "SUBJECT (REQUIRED)" else "SUBJECT")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (w.type != WidgetType.SUBJECT) {
                                        item { FilterChip(selected = w.subjectId == null, onClick = { w = w.copy(subjectId = null, chapterIds = "") }, label = { Text("None") }) }
                                    }
                                    items(ui.subjects, key = { it.id }) { s ->
                                        FilterChip(
                                            selected = w.subjectId == s.id,
                                            onClick = { if (w.subjectId != s.id) w = w.copy(subjectId = s.id, chapterIds = "") },
                                            label = { Text(s.name) },
                                        )
                                    }
                                }
                            }
                        }
                        val subjectId = w.subjectId
                        if (subjectId != null) {
                            val chapters = ui.chapters.filter { it.subjectId == subjectId }
                            item {
                                AppCard {
                                    SectionLabel("CHAPTERS (${chapterSel.size} selected)")
                                    OutlinedTextField(chapterQuery, { chapterQuery = it }, label = { Text("Search chapters") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    if (chapters.isEmpty()) Text("This subject has no chapters yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(chapters.filter { it.name.contains(chapterQuery.trim(), ignoreCase = true) }, key = { "c${it.id}" }) { c ->
                                Row(
                                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                                        val next = if (c.id in chapterSel) chapterSel - c.id else chapterSel + c.id
                                        w = w.copy(chapterIds = DashboardWidgetEntity.joinIds(next))
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = c.id in chapterSel, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                                    Text(c.name, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                        val taskPool = ui.openTasks.filter { w.subjectId == null || it.subjectId == w.subjectId || it.subjectId == null }
                        item {
                            AppCard {
                                SectionLabel("SAVED TASKS (${taskSel.size} selected)")
                                if (taskPool.isEmpty()) Text("No unfinished tasks. Add tasks in the Tasks tab, then pick them here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(taskPool, key = { "t${it.id}" }) { t ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                                    val next = if (t.id in taskSel) taskSel - t.id else taskSel + t.id
                                    w = w.copy(taskIds = DashboardWidgetEntity.joinIds(next))
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = t.id in taskSel, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                                Text(t.title, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    if (w.type == WidgetType.PRESET) {
                        item {
                            AppCard {
                                SectionLabel("SAVED SETUP (REQUIRED)")
                                if (ui.presets.isEmpty()) {
                                    Text("No saved setups yet. On the session setup screen, choose \"Save as preset\" first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ui.presets.forEach { p ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { w = w.copy(presetId = p.id) }) {
                                        RadioButton(selected = w.presetId == p.id, onClick = { w = w.copy(presetId = p.id) })
                                        Text(p.name)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        AppCard {
                            SectionLabel("APPEARANCE")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(WidgetStyle.icons.keys.toList()) { key ->
                                    val sel = w.icon == key
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(if (sel) WidgetStyle.color(w.colorKey).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { w = w.copy(icon = key) },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(WidgetStyle.icon(key), contentDescription = key) }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(WidgetStyle.colors.keys.toList()) { key ->
                                    val sel = w.colorKey == key
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(WidgetStyle.color(key))
                                            .clickable { w = w.copy(colorKey = key) },
                                        contentAlignment = Alignment.Center,
                                    ) { if (sel) Text("✓", color = androidx.compose.ui.graphics.Color.White) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = w.size == WidgetSize.SMALL, onClick = { w = w.copy(size = WidgetSize.SMALL) }, label = { Text("Small (half width)") })
                                FilterChip(selected = w.size == WidgetSize.LARGE, onClick = { w = w.copy(size = WidgetSize.LARGE) }, label = { Text("Large (full width)") })
                            }
                            Spacer(Modifier.size(4.dp))
                            WidgetTile(
                                w.copy(title = w.title.ifBlank { "Preview" }),
                                WidgetStyle.subtitle(w, ui.subjects.firstOrNull { it.id == w.subjectId }?.name, ui.presets.firstOrNull { it.id == w.presetId }?.name),
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultIcon(t: WidgetType) = when (t) {
    WidgetType.TIMER -> "timer"
    WidgetType.OPEN_ENDED -> "play"
    WidgetType.HOMEWORK -> "book"
    WidgetType.SUBJECT -> "school"
    WidgetType.PRESET -> "bookmark"
    WidgetType.CUSTOM -> "star"
}
