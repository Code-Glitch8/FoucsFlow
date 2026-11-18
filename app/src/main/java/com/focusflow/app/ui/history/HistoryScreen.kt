package com.focusflow.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.db.StudySessionEntity
import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.repo.StudyData
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.formatMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class DateRange(val label: String, val days: Int?) { ALL("All time", null), WEEK("Last 7 days", 7), MONTH("Last 30 days", 30) }

data class HistoryFilter(
    val range: DateRange = DateRange.ALL,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val mode: SessionMode? = null,
)

data class HistoryUi(val data: StudyData? = null, val filter: HistoryFilter = HistoryFilter()) {
    val visible: List<StudySessionEntity>
        get() {
            val d = data ?: return emptyList()
            val zone = ZoneId.systemDefault()
            val cutoff = filter.range.days?.let {
                java.time.LocalDate.now(zone).minusDays((it - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            return d.sessions.filter { s ->
                (cutoff == null || s.startedAt >= cutoff) &&
                    (filter.subjectId == null || s.subjectId == filter.subjectId) &&
                    (filter.chapterId == null || filter.chapterId in d.chapterIdsOf(s.id)) &&
                    (filter.mode == null || s.mode == filter.mode)
            }
        }
}

class HistoryViewModel(history: HistoryRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter())
    val ui = combine(history.observe(), filter) { d, f -> HistoryUi(d, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setRange(r: DateRange) = filter.update { it.copy(range = r) }
    fun setSubject(id: Long?) = filter.update { it.copy(subjectId = id, chapterId = null) }
    fun setChapter(id: Long?) = filter.update { it.copy(chapterId = id) }
    fun setMode(m: SessionMode?) = filter.update { it.copy(mode = m) }
}

fun statusLabel(s: SessionStatus) = when (s) {
    SessionStatus.COMPLETED -> "Completed"
    SessionStatus.ENDED_EARLY -> "Ended early"
    SessionStatus.EMERGENCY_EXIT -> "Emergency exit"
    SessionStatus.ACTIVE -> "Running"
}

fun formatWhen(ms: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val vm = containerViewModel { HistoryViewModel(it.history) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data = ui.data
    val f = ui.filter
    val visible = ui.visible

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Session history")
            }
        }
        item {
            AppCard {
                SectionLabel("DATE")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DateRange.entries) { r -> FilterChip(selected = f.range == r, onClick = { vm.setRange(r) }, label = { Text(r.label) }) }
                }
                SectionLabel("TYPE")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = f.mode == null, onClick = { vm.setMode(null) }, label = { Text("Any") }) }
                    item { FilterChip(selected = f.mode == SessionMode.TIMED, onClick = { vm.setMode(SessionMode.TIMED) }, label = { Text("Timed") }) }
                    item { FilterChip(selected = f.mode == SessionMode.OPEN_ENDED, onClick = { vm.setMode(SessionMode.OPEN_ENDED) }, label = { Text("Open-ended") }) }
                }
                if (data != null && data.subjects.isNotEmpty()) {
                    SectionLabel("SUBJECT")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = f.subjectId == null, onClick = { vm.setSubject(null) }, label = { Text("All") }) }
                        items(data.subjects, key = { it.id }) { s ->
                            FilterChip(selected = f.subjectId == s.id, onClick = { vm.setSubject(s.id) }, label = { Text(s.name) })
                        }
                    }
                    val chapters = data.chapters.filter { f.subjectId != null && it.subjectId == f.subjectId }
                    if (chapters.isNotEmpty()) {
                        SectionLabel("CHAPTER")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = f.chapterId == null, onClick = { vm.setChapter(null) }, label = { Text("All") }) }
                            items(chapters, key = { it.id }) { c ->
                                FilterChip(selected = f.chapterId == c.id, onClick = { vm.setChapter(c.id) }, label = { Text(c.name) })
                            }
                        }
                    }
                }
            }
        }
        if (data == null) {
            item { Text("Loading…") }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    if (data.sessions.isEmpty()) "No finished sessions yet. Start one from the Focus tab." else "No sessions match these filters.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { Text("${visible.size} session${if (visible.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(visible, key = { it.id }) { s ->
                val synced = data.isSyncedSession(s.id)
                SessionRow(
                    s, data,
                    onClick = { if (!synced) onOpenSession(s.id) },
                    syncedFromOtherPhone = synced,
                )
            }
        }
    }
}

@Composable
fun SessionRow(
    s: StudySessionEntity,
    data: StudyData,
    onClick: () -> Unit,
    syncedFromOtherPhone: Boolean = false,
) {
    val links = data.sessionTasks.filter { it.sessionId == s.id }
    val chapterNames = data.chapters.filter { it.id in data.chapterIdsOf(s.id) }.joinToString(", ") { it.name }
    AppCard(Modifier.then(if (syncedFromOtherPhone) Modifier else Modifier.clickable(onClick = onClick))) {
        Text(s.subjectName + if (chapterNames.isNotEmpty()) " · $chapterNames" else "", style = MaterialTheme.typography.titleMedium)
        Text(formatWhen(s.startedAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${formatMinutes(data.focusMs(s))} focus")
            Text(if (s.mode == SessionMode.TIMED) "Timed" else "Open-ended")
            if (links.isNotEmpty()) Text("${links.count { it.completed }}/${links.size} tasks")
        }
        Text(
            if (syncedFromOtherPhone) "Synced from other phone · ${statusLabel(s.status)}" else statusLabel(s.status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
