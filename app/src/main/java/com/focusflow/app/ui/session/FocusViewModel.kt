package com.focusflow.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.repo.ActiveSession
import com.focusflow.app.data.repo.BreakKind
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.ClockState
import com.focusflow.app.domain.SessionClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val active: ActiveSession? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val now: Long = System.currentTimeMillis(),
) {
    val clock: ClockState? get() = active?.session?.clock()
    val onBreak: Boolean get() = clock?.let { SessionClock.isOnBreak(it, now) } == true
    val focusElapsedMs: Long get() = clock?.let { SessionClock.focusElapsedMs(it, now) } ?: 0L
    val remainingMs: Long? get() = clock?.let { SessionClock.remainingMs(it, now) }
    val breakRemainingMs: Long get() = clock?.let { SessionClock.breakRemainingMs(it, now) } ?: 0L

    /**
     * Whether an ordinary "finish" action is offered. Strict sessions have no early exit:
     * timed ones end with the timer; open-ended ones can be finished once every planned task is
     * done (or none were planned). Non-strict sessions can always be ended.
     */
    val canFinishNormally: Boolean
        get() {
            val a = active ?: return false
            return when {
                !a.session.strict -> true
                a.session.mode == SessionMode.TIMED -> false
                else -> a.allTasksDone
            }
        }
}

class FocusViewModel(
    private val sessions: SessionRepository,
    subjects: SubjectRepository,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // Only ticks while the screen is visible (stateIn WhileSubscribed + lifecycle-aware collection).
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<FocusUiState> = combine(
        sessions.observeActive(),
        subjects.allChapters,
        ticker,
    ) { active, chapters, now ->
        FocusUiState(
            active = active,
            chapters = chapters.filter { it.id in (active?.chapterIds ?: emptyList()) },
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun setTaskCompleted(sessionTaskId: Long, taskId: Long, completed: Boolean) {
        viewModelScope.launch { sessions.setTaskCompleted(sessionTaskId, taskId, completed) }
    }

    fun setSubtaskDone(subtaskId: Long, done: Boolean) {
        viewModelScope.launch { sessions.setSubtaskDone(subtaskId, done) }
    }

    fun addTask(title: String) {
        val id = state.value.active?.session?.id ?: return
        viewModelScope.launch {
            if (!sessions.addTask(id, title)) _messages.tryEmit("That task is already in this session")
        }
    }

    fun removeTask(sessionTaskId: Long) {
        viewModelScope.launch { sessions.removeTask(sessionTaskId) }
    }

    fun startBreak(kind: BreakKind) {
        val id = state.value.active?.session?.id ?: return
        viewModelScope.launch {
            if (!sessions.startBreak(id, kind)) _messages.tryEmit("No break available right now")
        }
    }

    fun endBreakEarly() {
        val id = state.value.active?.session?.id ?: return
        viewModelScope.launch { sessions.endBreakEarly(id) }
    }

    /** Ordinary finish; only offered when [FocusUiState.canFinishNormally]. */
    fun finish() {
        val ui = state.value
        val a = ui.active ?: return
        if (!ui.canFinishNormally) return
        val status = if (a.session.mode == SessionMode.TIMED && (ui.remainingMs ?: 0L) > 0L) {
            SessionStatus.ENDED_EARLY
        } else {
            SessionStatus.COMPLETED
        }
        viewModelScope.launch { sessions.endSession(a.session.id, status) }
    }

    /** The deliberate escape hatch for strict sessions. Recorded as such in history. */
    fun emergencyExit() {
        val id = state.value.active?.session?.id ?: return
        viewModelScope.launch { sessions.endSession(id, SessionStatus.EMERGENCY_EXIT) }
    }
}
