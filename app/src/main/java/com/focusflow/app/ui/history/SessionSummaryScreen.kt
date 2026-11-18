package com.focusflow.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.ai.AiService
import com.focusflow.app.ai.SummaryFacts
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.StatTile
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.formatMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionSummaryViewModel(
    private val sessionId: Long,
    private val history: HistoryRepository,
    private val ai: AiService,
    private val chapterNames: suspend (Set<Long>) -> List<String>,
) : ViewModel() {
    val session = history.observeSession(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tasks = history.observeSessionTasks(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val summary = history.observeSummary(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chapters = history.observeChapterIds(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val working = MutableStateFlow(false)
    val note = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            // Written once when a session is first viewed after finishing; kept so it never changes behind your back.
            val s = history.observeSession(sessionId).first()
            if (s != null && s.status != SessionStatus.ACTIVE && history.observeSummary(sessionId).first() == null) generate()
        }
    }

    fun generate() {
        if (working.value) return
        working.value = true
        note.value = null
        viewModelScope.launch {
            try {
                val s = history.observeSession(sessionId).first() ?: return@launch
                val rows = history.observeSessionTasks(sessionId).first()
                val names = chapterNames(history.observeChapterIds(sessionId).first().toSet())
                val end = s.endedAt ?: System.currentTimeMillis()
                val facts = SummaryFacts(
                    subject = s.subjectName,
                    chapters = names,
                    planned = s.description,
                    status = s.status,
                    focusMinutes = SessionClock.focusElapsedMs(s.clock(), end) / 60_000,
                    breakMinutes = SessionClock.breakElapsedMs(s.clock(), end) / 60_000,
                    completedTasks = rows.filter { it.link.completed }.map { it.title },
                    unfinishedTasks = rows.filter { !it.link.completed }.map { it.title },
                )
                val r = ai.summarize(facts)
                if (r.text.isNotBlank()) history.saveSummary(sessionId, r.text, r.source)
                note.value = r.note
            } finally {
                working.value = false
            }
        }
    }
}

@Composable
fun SessionSummaryScreen(sessionId: Long, onBack: () -> Unit, onStartNext: () -> Unit) {
    val vm = containerViewModel { c ->
        SessionSummaryViewModel(sessionId, c.history, c.ai) { ids ->
            c.subjects.allChapters.first().filter { it.id in ids }.map { it.name }
        }
    }
    val session by vm.session.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val s = session

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Session summary")
            }
        }
        if (s == null) {
            item { Text("This session could not be found.") }
            return@LazyColumn
        }
        val end = s.endedAt ?: System.currentTimeMillis()
        item {
            AppCard {
                Text(s.subjectName, style = MaterialTheme.typography.titleLarge)
                Text(formatWhen(s.startedAt) + " · " + statusLabel(s.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatTile("focus", formatMinutes(SessionClock.focusElapsedMs(s.clock(), end)))
                    StatTile("breaks", formatMinutes(SessionClock.breakElapsedMs(s.clock(), end)))
                    StatTile("tasks done", "${tasks.count { it.link.completed }}/${tasks.size}")
                }
                if (s.mode == SessionMode.TIMED && s.plannedDurationMs != null) {
                    Text("Planned ${formatMinutes(s.plannedDurationMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AppCard {
                val label = when (summary?.source) {
                    SummarySource.API_AI -> "WRITTEN BY API AI"
                    SummarySource.ON_DEVICE_AI -> "LEGACY ON-DEVICE AI SUMMARY"
                    SummarySource.TEMPLATE -> "LEGACY TEMPLATE SUMMARY"
                    null -> "API AI SUMMARY"
                }
                SectionLabel(label)
                Text(
                    summary?.text ?: if (working) "Writing summary…" else "No summary yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (s.status != SessionStatus.ACTIVE) {
                    OutlinedButton(onClick = vm::generate, enabled = !working) { Text(if (summary == null) "Generate" else "Regenerate") }
                }
            }
        }
        if (tasks.isNotEmpty()) {
            item {
                AppCard {
                    SectionLabel("TASKS")
                    tasks.forEach { t ->
                        Text((if (t.link.completed) "✓  " else "○  ") + t.title)
                    }
                }
            }
        }
        if (tasks.any { !it.link.completed }) {
            item {
                AppCard {
                    SectionLabel("UNFINISHED")
                    Text(
                        "${tasks.count { !it.link.completed }} task(s) are still open on your Tasks list, with their subject and chapter. " +
                            "Add them to a new session to continue; they won't be duplicated.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onStartNext) { Text("Plan next session") }
                }
            }
        }
    }
}
