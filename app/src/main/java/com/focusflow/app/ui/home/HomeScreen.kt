package com.focusflow.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.repo.ActiveSession
import com.focusflow.app.data.repo.HistoryRepository
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.domain.StudyStats
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ProgressBar
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel
import com.focusflow.app.ui.common.StatTile
import com.focusflow.app.ui.common.containerViewModel
import com.focusflow.app.ui.formatMinutes
import com.focusflow.app.ui.history.SessionRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(history: HistoryRepository, sessions: SessionRepository, presets: PresetRepository) : ViewModel() {
    val data = history.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val active = sessions.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val presets = presets.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onStartPreset: (Long) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it.history, it.sessions, it.presets) }
    val data by vm.data.collectAsStateWithLifecycle()
    val active: ActiveSession? by vm.active.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Home") }

        active?.let { a ->
            item {
                AppCard(Modifier.clickable(onClick = onOpenFocus)) {
                    SectionLabel("SESSION IN PROGRESS")
                    Text(a.session.subjectName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${formatMinutes(SessionClock.focusElapsedMs(a.session.clock(), now))} focused · ${a.completedCount}/${a.tasks.size} tasks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenFocus) { Text("Open session") }
                }
            }
        }

        val d = data
        if (d != null) {
            val facts = d.facts()
            val activeFocus = active?.let { SessionClock.focusElapsedMs(it.session.clock(), now) } ?: 0L
            val todayFocus = StudyStats.focusOnDay(facts, today, zone) + activeFocus
            val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val doneToday = d.sessionTasks.filter { it.completed && (it.completedAt ?: 0L) >= startOfDay }.map { it.taskId }.toSet().size
            item {
                AppCard {
                    SectionLabel("TODAY")
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatTile("focused", formatMinutes(todayFocus))
                        StatTile("tasks done", "$doneToday")
                        StatTile("day streak", "${StudyStats.streak(facts, today, zone)}")
                    }
                }
            }
        }

        item {
            AppCard {
                SectionLabel("QUICK START")
                Button(onClick = onStartSession, enabled = active == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (active == null) "Start a session" else "A session is already running")
                }
                if (presets.isNotEmpty() && active == null) {
                    Text("Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets, key = { it.id }) { p -> AssistChip(onClick = { onStartPreset(p.id) }, label = { Text(p.name) }) }
                    }
                }
            }
        }

        if (d != null) {
            val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
            val recentFacts = d.facts().filter { it.startedAt >= weekStart }
            val completed = d.tasks.filter { it.status == com.focusflow.app.data.db.TaskStatus.DONE && it.chapterId != null }.groupingBy { it.chapterId!! }.eachCount()
            val open = d.tasks.filter { it.status == com.focusflow.app.data.db.TaskStatus.OPEN && it.chapterId != null }.groupingBy { it.chapterId!! }.eachCount()
            val chapterStats = StudyStats.chapterStats(d.chapters.map { it.id }, recentFacts, completed, open)
                .filter { it.focusMs > 0 }.sortedByDescending { it.focusMs }.take(4)
            if (chapterStats.isNotEmpty()) {
                item {
                    AppCard {
                        SectionLabel("CHAPTERS THIS WEEK")
                        val max = chapterStats.first().focusMs.coerceAtLeast(1)
                        chapterStats.forEach { st ->
                            val name = d.chapters.first { it.id == st.chapterId }.name
                            Text("$name — ${formatMinutes(st.focusMs)} · ${st.completedTasks} done, ${st.unfinishedTasks} open")
                            ProgressBar(st.focusMs.toFloat() / max)
                        }
                    }
                }
            }
            item { SectionLabel("RECENT SESSIONS") }
            if (d.sessions.isEmpty()) {
                item { Text("No sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(d.sessions.take(3), key = { it.id }) { s -> SessionRow(s, d) { onOpenSession(s.id) } }
                item { OutlinedButton(onClick = onOpenHistory) { Text("See all history") } }
            }
        }
    }
}
