package com.focusflow.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.TaskStatus
import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.domain.StudyStats
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.Bar
import com.focusflow.app.ui.common.BarChart
import com.focusflow.app.ui.common.ProgressBar
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.StatTile
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.formatMinutes
import com.focusflow.app.ui.history.formatWhen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class StatisticsViewModel(history: HistoryRepository, private val subjects: SubjectRepository) : ViewModel() {
    val data = history.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setConfidence(c: ChapterEntity, value: Int?) { viewModelScope.launch { subjects.setChapterConfidence(c, value) } }
}

@Composable
fun StatisticsScreen(onOpenHistory: () -> Unit) {
    val vm = containerViewModel { StatisticsViewModel(it.history, it.subjects) }
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Statistics") }
        if (d == null) { item { Text("Loading…") }; return@LazyColumn }
        if (d.sessions.isEmpty()) {
            item {
                Text(
                    "Nothing to show yet. Finish a session and your time, tasks and chapters will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val facts = d.facts()
        val week = StudyStats.dailyFocus(facts, today, 7, zone)
        val weekTotal = week.sumOf { it.focusMs }
        val total = facts.sumOf { it.focusMs }

        item {
            AppCard {
                SectionLabel("OVERVIEW")
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatTile("all-time focus", formatMinutes(total))
                    StatTile("sessions", "${d.sessions.size}")
                    StatTile("streak (days)", "${StudyStats.streak(facts, today, zone)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatTile("last 7 days", formatMinutes(weekTotal))
                    StatTile("tasks done", "${d.tasks.count { it.status == TaskStatus.DONE }}")
                    StatTile("tasks open", "${d.tasks.count { it.status == TaskStatus.OPEN }}")
                }
            }
        }
        item {
            AppCard {
                SectionLabel("DAILY FOCUS, LAST 7 DAYS")
                BarChart(week.map { Bar(it.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), it.focusMs, if (it.focusMs > 0) "${it.focusMs / 60_000}m" else "") })
            }
        }
        item {
            val weeks = (3 downTo 0).map { w ->
                val end = today.minusDays(w * 7L)
                val days = StudyStats.dailyFocus(facts, end, 7, zone)
                Bar(if (w == 0) "This wk" else "${w}w ago", days.sumOf { it.focusMs }, "${days.sumOf { it.focusMs } / 60_000}m")
            }
            AppCard {
                SectionLabel("WEEKLY FOCUS")
                BarChart(weeks)
            }
        }
        item {
            val bySubject = facts.groupBy { it.subjectId }.map { (id, list) ->
                (d.subjects.firstOrNull { it.id == id }?.name ?: d.sessions.first { s -> s.subjectId == id }.subjectName) to list.sumOf { it.focusMs }
            }.sortedByDescending { it.second }
            AppCard {
                SectionLabel("TIME PER SUBJECT")
                val max = bySubject.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                bySubject.forEach { (name, ms) ->
                    Text("$name — ${formatMinutes(ms)}")
                    ProgressBar(ms.toFloat() / max)
                }
            }
        }
        val completed = d.tasks.filter { it.status == TaskStatus.DONE && it.chapterId != null }.groupingBy { it.chapterId!! }.eachCount()
        val open = d.tasks.filter { it.status == TaskStatus.OPEN && it.chapterId != null }.groupingBy { it.chapterId!! }.eachCount()
        val stats = StudyStats.chapterStats(d.chapters.map { it.id }, facts, completed, open)
            .filter { it.sessions > 0 || it.unfinishedTasks > 0 || it.completedTasks > 0 }
            .sortedByDescending { it.focusMs }
        item {
            AppCard {
                SectionLabel("CHAPTERS")
                Text(
                    "Time shows how long you spent, not how well you know a chapter. Rate your own confidence separately below each one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stats.isEmpty()) {
            item { Text("No chapter activity yet. Pick chapters when you start a session.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val maxMs = stats.maxOfOrNull { it.focusMs }?.coerceAtLeast(1) ?: 1
        items(stats, key = { it.chapterId }) { st ->
            val ch = d.chapters.first { it.id == st.chapterId }
            val sub = d.subjects.firstOrNull { it.id == ch.subjectId }?.name.orEmpty()
            AppCard {
                Text(ch.name, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProgressBar(st.focusMs.toFloat() / maxMs)
                Text("${formatMinutes(st.focusMs)} · ${st.sessions} session${if (st.sessions == 1) "" else "s"}")
                Text("${st.completedTasks} done · ${st.unfinishedTasks} unfinished")
                Text(
                    "Last studied: " + (st.lastStudiedAt?.let { formatWhen(it) } ?: "never"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel("YOUR CONFIDENCE (OPTIONAL)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { v ->
                        FilterChip(
                            selected = ch.selfConfidence == v,
                            onClick = { vm.setConfidence(ch, if (ch.selfConfidence == v) null else v) },
                            label = { Text("$v") },
                        )
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onOpenHistory) { Text("Open session history") } }
    }
}
