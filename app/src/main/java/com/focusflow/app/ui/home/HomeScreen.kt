package com.focusflow.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
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
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.WidgetRepository
import com.focusflow.app.data.db.DashboardWidgetEntity
import com.focusflow.app.data.db.WidgetAction
import com.focusflow.app.launch.LaunchOutcome
import com.focusflow.app.launch.Requirement
import com.focusflow.app.launch.SessionLauncher
import com.focusflow.app.service.Permissions
import com.focusflow.app.ui.widgets.WidgetStyle
import com.focusflow.app.ui.widgets.WidgetTile
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

sealed interface HomeEvent {
    data class Message(val text: String, val offerOpenSession: Boolean = false) : HomeEvent
    data class Navigate(val action: WidgetAction) : HomeEvent
    data object OpenFocus : HomeEvent
    /** The widget wants the bubble but the overlay permission is missing; let the user decide. */
    data class NeedOverlay(val widgetId: String, val title: String, val notificationsToo: Boolean) : HomeEvent
    /** Android 13+: ask for the (optional) notification permission, then call [HomeViewModel.run]. */
    data class AskNotifications(val widgetId: String, val floatingOverride: Boolean?) : HomeEvent
}

class HomeViewModel(
    history: HistoryRepository,
    sessions: SessionRepository,
    presets: PresetRepository,
    widgetRepo: WidgetRepository,
    subjects: SubjectRepository,
    private val launcher: SessionLauncher,
) : ViewModel() {
    val data = history.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val active = sessions.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val presets = presets.allPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val widgets = widgetRepo.widgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = subjects.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: kotlinx.coroutines.flow.SharedFlow<HomeEvent> = _events
    private var busy = false

    /** Entry point for a widget tap: guards against a second session, then checks permissions. */
    fun onWidgetTap(w: DashboardWidgetEntity) {
        if (busy) return
        viewModelScope.launch {
            if (launcher.hasActive()) {
                _events.tryEmit(HomeEvent.Message("A session is already running.", offerOpenSession = true))
                return@launch
            }
            val reqs = launcher.requirements(w.id)
            when {
                Requirement.OVERLAY in reqs ->
                    _events.tryEmit(HomeEvent.NeedOverlay(w.id, w.title, Requirement.NOTIFICATIONS in reqs))
                Requirement.NOTIFICATIONS in reqs -> _events.tryEmit(HomeEvent.AskNotifications(w.id, null))
                else -> run(w.id, null)
            }
        }
    }

    /** Called after the user returned from the overlay settings screen. */
    fun resumeAfterOverlaySettings(widgetId: String) {
        viewModelScope.launch {
            val reqs = launcher.requirements(widgetId)
            if (Requirement.OVERLAY in reqs) return@launch // still not granted: the user can tap again
            if (Requirement.NOTIFICATIONS in reqs) _events.tryEmit(HomeEvent.AskNotifications(widgetId, null)) else run(widgetId, null)
        }
    }

    /** "Start without the floating bubble" from the permission dialog. */
    fun startWithoutBubble(widgetId: String, notificationsToo: Boolean) {
        if (notificationsToo) _events.tryEmit(HomeEvent.AskNotifications(widgetId, false)) else run(widgetId, false)
    }

    fun run(widgetId: String, floatingOverride: Boolean?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                when (val o = launcher.runWidget(widgetId, floatingOverride)) {
                    is LaunchOutcome.Started -> {
                        o.notes.firstOrNull()?.let { _events.tryEmit(HomeEvent.Message(it)) }
                        _events.tryEmit(HomeEvent.OpenFocus)
                    }
                    is LaunchOutcome.AlreadyActive -> _events.tryEmit(HomeEvent.Message("A session is already running.", offerOpenSession = true))
                    is LaunchOutcome.Invalid -> _events.tryEmit(HomeEvent.Message(o.reason))
                    is LaunchOutcome.Navigate -> _events.tryEmit(HomeEvent.Navigate(o.action))
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onStartPreset: (Long) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onEditWidgets: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenStatistics: () -> Unit = {},
) {
    val vm = containerViewModel { HomeViewModel(it.history, it.sessions, it.presets, it.widgets, it.subjects, it.launcher) }
    val widgets by vm.widgets.collectAsStateWithLifecycle()
    val subjectList by vm.subjects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var overlayDialog by remember { mutableStateOf<HomeEvent.NeedOverlay?>(null) }
    var waitingForOverlay by remember { mutableStateOf<String?>(null) }
    var notifRequest by remember { mutableStateOf<HomeEvent.AskNotifications?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The notification only decorates the session; start whether or not it was granted.
        notifRequest?.let { vm.run(it.widgetId, it.floatingOverride) }
        notifRequest = null
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is HomeEvent.Message -> {
                    val r = snackbar.showSnackbar(e.text, actionLabel = if (e.offerOpenSession) "Open session" else null)
                    if (r == SnackbarResult.ActionPerformed) onOpenFocus()
                }
                is HomeEvent.Navigate -> when (e.action) {
                    WidgetAction.OPEN_PREPARE -> onStartSession()
                    WidgetAction.OPEN_TASKS -> onOpenTasks()
                    WidgetAction.OPEN_HISTORY -> onOpenHistory()
                    WidgetAction.OPEN_STATISTICS -> onOpenStatistics()
                    WidgetAction.START_SESSION -> Unit
                }
                HomeEvent.OpenFocus -> onOpenFocus()
                is HomeEvent.NeedOverlay -> overlayDialog = e
                is HomeEvent.AskNotifications -> {
                    notifRequest = e
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Returning from the overlay settings screen: carry on with the widget the user tapped.
    LifecycleResumeEffect(waitingForOverlay) {
        waitingForOverlay?.let { id -> waitingForOverlay = null; vm.resumeAfterOverlaySettings(id) }
        onPauseOrDispose { }
    }

    overlayDialog?.let { d ->
        AlertDialog(
            onDismissRequest = { overlayDialog = null },
            title = { Text("Allow the floating assistant?") },
            text = {
                Text(
                    "\"${d.title}\" shows the floating FocusFlow bubble during the session, which needs the \"Display over other apps\" " +
                        "permission. Enable it, or start this session without the bubble. The timer and blocking work either way.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    waitingForOverlay = d.widgetId
                    overlayDialog = null
                    context.startActivity(Permissions.overlayIntent(context))
                }) { Text("Enable permission") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { overlayDialog = null; vm.startWithoutBubble(d.widgetId, d.notificationsToo) }) { Text("Start without bubble") }
                    TextButton(onClick = { overlayDialog = null }) { Text("Cancel") }
                }
            },
        )
    }
    val data by vm.data.collectAsStateWithLifecycle()
    val active: ActiveSession? by vm.active.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Home", Modifier.weight(1f))
                TextButton(onClick = onEditWidgets) { Text("Edit dashboard") }
            }
        }

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

        item { SectionLabel("WIDGETS") }
        val visibleWidgets = widgets.filter { it.visible }
        if (visibleWidgets.isEmpty()) {
            item {
                AppCard {
                    Text("Your dashboard has no widgets.", style = MaterialTheme.typography.titleMedium)
                    Text("Add timer, subject or saved-setup widgets that start a session in one tap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onEditWidgets) { Text("Add widgets") }
                }
            }
        }
        items(WidgetStyle.rows(visibleWidgets), key = { row -> row.joinToString { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { w ->
                    WidgetTile(
                        widget = w,
                        subtitle = WidgetStyle.subtitle(
                            w,
                            subjectList.firstOrNull { it.id == w.subjectId }?.name,
                            presets.firstOrNull { it.id == w.presetId }?.name,
                        ),
                        onClick = { vm.onWidgetTap(w) },
                        modifier = Modifier.weight(1f),
                        dimmed = active != null,
                    )
                }
                if (row.size == 1 && row.first().size == com.focusflow.app.data.db.WidgetSize.SMALL) Spacer(Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(onClick = onStartSession, enabled = active == null, modifier = Modifier.fillMaxWidth()) {
                Text(if (active == null) "Custom session…" else "A session is already running")
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
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
