package com.focusflow.app.ui.subjects

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ConfirmDialog
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.TextInputDialog
import com.focusflow.app.ui.common.containerViewModel

private sealed interface Dialog {
    data object AddSubject : Dialog
    data class RenameSubject(val subject: SubjectEntity) : Dialog
    data class DeleteSubject(val subject: SubjectEntity) : Dialog
    data object AddChapter : Dialog
    data class RenameChapter(val chapter: ChapterEntity) : Dialog
    data class DeleteChapter(val chapter: ChapterEntity) : Dialog
}

/** Manage subjects and their reusable chapters. Chapters persist across sessions. */
@Composable
fun SubjectsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { SubjectsViewModel(it.subjects) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                ScreenTitle("Subjects & chapters")
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.subjects, key = { it.id }) { subject ->
                    FilterChip(
                        selected = subject.id == state.selected?.id,
                        onClick = { vm.select(subject) },
                        label = { Text(subject.name) },
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { dialog = Dialog.AddSubject },
                        label = { Text("New subject") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    )
                }
            }

            val selected = state.selected
            if (selected != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(selected.name, style = MaterialTheme.typography.titleLarge)
                    if (!selected.builtIn) {
                        Row {
                            IconButton(onClick = { dialog = Dialog.RenameSubject(selected) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Rename subject")
                            }
                            IconButton(onClick = { dialog = Dialog.DeleteSubject(selected) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete subject")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search chapters") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.chapters.isEmpty()) {
                        item {
                            Text(
                                text = if (state.totalChaptersInSubject == 0) {
                                    "No chapters yet. Add the chapters you study once and reuse them in every session."
                                } else {
                                    "No chapters match your search."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.chapters, key = { it.id }) { chapter ->
                        AppCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    chapter.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { dialog = Dialog.RenameChapter(chapter) }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit chapter ${chapter.name}")
                                }
                                IconButton(onClick = { dialog = Dialog.DeleteChapter(chapter) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove chapter ${chapter.name}")
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { dialog = Dialog.AddChapter },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Add chapter to ${selected.name}")
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.AddSubject -> TextInputDialog(
            title = "New subject", label = "Subject name", confirmLabel = "Add",
            onDismiss = { dialog = null },
        ) { vm.addSubject(it); dialog = null }
        is Dialog.RenameSubject -> TextInputDialog(
            title = "Rename subject", label = "Subject name", initial = d.subject.name,
            onDismiss = { dialog = null },
        ) { vm.renameSubject(d.subject, it); dialog = null }
        is Dialog.DeleteSubject -> ConfirmDialog(
            title = "Delete ${d.subject.name}?",
            message = "Its chapters will be removed too. Past session history keeps its subject name.",
            confirmLabel = "Delete",
            onDismiss = { dialog = null },
        ) { vm.deleteSubject(d.subject); dialog = null }
        Dialog.AddChapter -> TextInputDialog(
            title = "New chapter", label = "Chapter name", confirmLabel = "Add",
            onDismiss = { dialog = null },
        ) { vm.addChapter(it); dialog = null }
        is Dialog.RenameChapter -> TextInputDialog(
            title = "Edit chapter", label = "Chapter name", initial = d.chapter.name,
            onDismiss = { dialog = null },
        ) { vm.renameChapter(d.chapter, it); dialog = null }
        is Dialog.DeleteChapter -> ConfirmDialog(
            title = "Remove ${d.chapter.name}?",
            message = "It will be removed from presets and session links. Tasks keep their text.",
            confirmLabel = "Remove",
            onDismiss = { dialog = null },
        ) { vm.deleteChapter(d.chapter); dialog = null }
    }
}
