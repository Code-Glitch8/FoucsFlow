package com.focusflow.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.repo.AddTaskResult
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val open: List<TaskEntity> = emptyList(),
    val done: List<TaskEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
) {
    fun subjectName(id: Long?): String? = subjects.firstOrNull { it.id == id }?.name
    fun chapterName(id: Long?): String? = chapters.firstOrNull { it.id == id }?.name
}

class TasksViewModel(
    private val tasks: TaskRepository,
    subjects: SubjectRepository,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val state: StateFlow<TasksUiState> = combine(
        tasks.openTasks,
        tasks.recentlyDone(),
        subjects.subjects,
        subjects.allChapters,
    ) { open, done, subs, chaps -> TasksUiState(open, done, subs, chaps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun add(title: String, subjectId: Long?, chapterId: Long?, estimatedMinutes: Int?) {
        viewModelScope.launch {
            when (tasks.add(title, subjectId, chapterId, estimatedMinutes)) {
                is AddTaskResult.Added -> Unit
                is AddTaskResult.AlreadyOpen -> _messages.tryEmit("That task is already on your list")
                AddTaskResult.Blank -> _messages.tryEmit("Task can't be empty")
            }
        }
    }

    fun markDone(task: TaskEntity) { viewModelScope.launch { tasks.markDone(task.id) } }
    fun reopen(task: TaskEntity) { viewModelScope.launch { tasks.reopen(task.id) } }
    fun dismiss(task: TaskEntity) { viewModelScope.launch { tasks.dismiss(task.id) } }
}
