package com.focusflow.app.ui.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.repo.EditResult
import com.focusflow.app.data.repo.SubjectRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubjectsUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val selected: SubjectEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val query: String = "",
    val totalChaptersInSubject: Int = 0,
)

class SubjectsViewModel(private val repo: SubjectRepository) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val state: StateFlow<SubjectsUiState> = combine(
        repo.subjects,
        repo.allChapters,
        selectedId,
        query,
    ) { subjects, chapters, sel, q ->
        val selected = subjects.firstOrNull { it.id == sel } ?: subjects.firstOrNull()
        val inSubject = chapters.filter { it.subjectId == selected?.id }
        SubjectsUiState(
            subjects = subjects,
            selected = selected,
            chapters = inSubject.filter { it.name.contains(q.trim(), ignoreCase = true) },
            query = q,
            totalChaptersInSubject = inSubject.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectsUiState())

    fun select(subject: SubjectEntity) { selectedId.value = subject.id }
    fun setQuery(q: String) { query.value = q }

    fun addSubject(name: String) = launchReport("Subject") { repo.addSubject(name) }
    fun renameSubject(s: SubjectEntity, name: String) = launchReport("Subject") { repo.renameSubject(s, name) }
    fun deleteSubject(s: SubjectEntity) = launchReport("Subject") { repo.deleteSubject(s) }

    fun addChapter(name: String) {
        val subject = state.value.selected ?: return
        launchReport("Chapter") { repo.addChapter(subject.id, name) }
    }

    fun renameChapter(c: ChapterEntity, name: String) = launchReport("Chapter") { repo.renameChapter(c, name) }
    fun deleteChapter(c: ChapterEntity) = launchReport("Chapter") { repo.deleteChapter(c); EditResult.OK }

    private fun launchReport(what: String, block: suspend () -> EditResult) {
        viewModelScope.launch {
            when (block()) {
                EditResult.OK -> Unit
                EditResult.BLANK -> _messages.tryEmit("$what name can't be empty")
                EditResult.DUPLICATE -> _messages.tryEmit("A ${what.lowercase()} with that name already exists")
                EditResult.NOT_ALLOWED -> _messages.tryEmit("Built-in subjects can't be changed")
            }
        }
    }
}
