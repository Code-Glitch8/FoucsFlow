package com.focusflow.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.ai.AiService
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.speech.VoiceAvailability
import com.focusflow.app.speech.VoiceListener
import com.focusflow.app.speech.VoiceRecognizer
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.StudyPresetEntity
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.data.db.TaskEntity
import com.focusflow.app.data.repo.DraftTask
import com.focusflow.app.data.repo.PresetDraft
import com.focusflow.app.data.repo.PresetRepository
import com.focusflow.app.data.repo.SessionDraft
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.StartResult
import com.focusflow.app.data.repo.SubjectRepository
import com.focusflow.app.data.repo.TaskRepository
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.domain.TaskNormalizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the user is editing on the session preparation screen. */
data class PrepForm(
    val subjectId: Long? = null,
    val chapterIds: Set<Long> = emptySet(),
    val chapterQuery: String = "",
    val description: String = "",
    val tasks: List<DraftTask> = emptyList(),
    val mode: SessionMode = SessionMode.TIMED,
    val durationMinutes: Int = 25,
    val strict: Boolean = true,
    val launchSnapGpt: Boolean = false,
    val loadedPresetId: Long? = null,
)

data class PrepUiState(
    val form: PrepForm = PrepForm(),
    val subjects: List<SubjectEntity> = emptyList(),
    val subject: SubjectEntity? = null,
    /** Chapters of the selected subject matching the search text. */
    val chapters: List<ChapterEntity> = emptyList(),
    val chapterCount: Int = 0,
    val presets: List<StudyPresetEntity> = emptyList(),
    /** Open tasks that can be resumed, excluding ones already in the draft. */
    val resumable: List<TaskEntity> = emptyList(),
    val allChapters: List<ChapterEntity> = emptyList(),
    val starting: Boolean = false,
    val ai: AiUiState = AiUiState(),
) {
    /** Proposed tasks must be accepted or discarded first, so nothing unreviewed ends up in a session. */
    val canStart: Boolean get() = subject != null && !starting && form.durationMinutes > 0 && ai.proposals.isEmpty() && !ai.generating
}

data class Proposal(val title: String, val minutes: Int?, val subtasks: List<String>, val selected: Boolean = true)

data class AiUiState(
    val generating: Boolean = false,
    val proposals: List<Proposal> = emptyList(),
    val origin: TaskOrigin = TaskOrigin.RULE_BASED,
    val note: String? = null,
    val listening: Boolean = false,
    val partial: String = "",
    val voice: VoiceAvailability = VoiceAvailability.UNAVAILABLE,
)

class SessionPrepViewModel(
    private val sessions: SessionRepository,
    private val subjectRepo: SubjectRepository,
    private val taskRepo: TaskRepository,
    private val presetRepo: PresetRepository,
    private val settings: AppSettings,
    private val aiService: AiService,
    private val voice: VoiceRecognizer,
) : ViewModel() {
    private val form = MutableStateFlow(PrepForm())
    private val aiState = MutableStateFlow(AiUiState(voice = voice.availability()))
    private val starting = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    private val _started = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val started: SharedFlow<Long> = _started

    init {
        viewModelScope.launch {
            val mode = settings.defaultMode.first()
            val minutes = settings.defaultDurationMin.first()
            form.update { it.copy(mode = mode, durationMinutes = minutes) }
        }
    }

    val state: StateFlow<PrepUiState> = combine(
        form,
        subjectRepo.subjects,
        subjectRepo.allChapters,
        presetRepo.allPresets,
        taskRepo.openTasks,
    ) { f, subjects, allChapters, presets, open ->
        val subject = subjects.firstOrNull { it.id == f.subjectId }
        val inSubject = allChapters.filter { it.subjectId == subject?.id }
        val inDraft = f.tasks.mapNotNull { it.existingTaskId }.toSet()
        PrepUiState(
            form = f,
            subjects = subjects,
            subject = subject,
            chapters = inSubject.filter { it.name.contains(f.chapterQuery.trim(), ignoreCase = true) },
            chapterCount = inSubject.size,
            presets = presets,
            resumable = open.filter { it.id !in inDraft }
                // Tasks from the chosen subject first, so the most relevant ones are on top.
                .sortedByDescending { it.subjectId == subject?.id },
            allChapters = allChapters,
        )
    }.combine(starting) { ui, s -> ui.copy(starting = s) }
        .combine(aiState) { ui, a -> ui.copy(ai = a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrepUiState())

    fun selectSubject(s: SubjectEntity) = form.update {
        if (it.subjectId == s.id) it else it.copy(subjectId = s.id, chapterIds = emptySet(), chapterQuery = "")
    }

    fun toggleChapter(c: ChapterEntity) = form.update {
        it.copy(chapterIds = if (c.id in it.chapterIds) it.chapterIds - c.id else it.chapterIds + c.id)
    }

    fun setChapterQuery(q: String) = form.update { it.copy(chapterQuery = q) }
    fun setDescription(d: String) = form.update { it.copy(description = d) }
    fun setMode(m: SessionMode) = form.update { it.copy(mode = m) }
    fun setDuration(min: Int) = form.update { it.copy(durationMinutes = min.coerceIn(1, 600)) }
    fun setStrict(v: Boolean) = form.update { it.copy(strict = v) }
    fun setLaunchSnapGpt(v: Boolean) = form.update { it.copy(launchSnapGpt = v) }

    fun addChapter(name: String) {
        val subjectId = form.value.subjectId ?: return
        viewModelScope.launch {
            when (subjectRepo.addChapter(subjectId, name)) {
                com.focusflow.app.data.repo.EditResult.DUPLICATE -> _messages.tryEmit("That chapter already exists")
                com.focusflow.app.data.repo.EditResult.BLANK -> _messages.tryEmit("Chapter name can't be empty")
                else -> Unit
            }
        }
    }

    fun addSubject(name: String) {
        viewModelScope.launch {
            when (subjectRepo.addSubject(name)) {
                com.focusflow.app.data.repo.EditResult.DUPLICATE -> _messages.tryEmit("That subject already exists")
                com.focusflow.app.data.repo.EditResult.BLANK -> _messages.tryEmit("Subject name can't be empty")
                else -> Unit
            }
        }
    }

    // ---- task list (editable before the session starts) ----

    /** Adds tasks to the draft, skipping any that duplicate one already there. */
    fun addDraftTasks(new: List<DraftTask>) = form.update { f ->
        val keys = f.tasks.map { TaskNormalizer.dedupeKey(it.title, it.chapterId) }.toMutableSet()
        val additions = new.filter { t ->
            TaskNormalizer.clean(t.title).isNotEmpty() &&
                keys.add(TaskNormalizer.dedupeKey(t.title, t.chapterId))
        }
        f.copy(tasks = f.tasks + additions.map { it.copy(title = TaskNormalizer.clean(it.title)) })
    }

    fun addTask(title: String, minutes: Int?, chapterId: Long?, subtasks: List<String>) =
        addDraftTasks(listOf(DraftTask(title, minutes, chapterId = chapterId, subtasks = subtasks)))

    fun updateTask(index: Int, title: String, minutes: Int?, chapterId: Long?, subtasks: List<String>) =
        form.update { f ->
            if (index !in f.tasks.indices || TaskNormalizer.clean(title).isEmpty()) return@update f
            f.copy(
                tasks = f.tasks.toMutableList().also {
                    it[index] = it[index].copy(
                        title = TaskNormalizer.clean(title),
                        estimatedMinutes = minutes,
                        chapterId = chapterId,
                        subtasks = subtasks,
                    )
                },
            )
        }

    fun removeTask(index: Int) = form.update { f ->
        if (index !in f.tasks.indices) f else f.copy(tasks = f.tasks.filterIndexed { i, _ -> i != index })
    }

    /** Pulls an unfinished task into this session; it keeps its own subject and chapter. */
    fun resume(task: TaskEntity) = form.update { f ->
        if (f.tasks.any { it.existingTaskId == task.id }) f
        else f.copy(
            tasks = f.tasks + DraftTask(
                title = task.title,
                estimatedMinutes = task.estimatedMinutes,
                origin = task.origin,
                chapterId = task.chapterId,
                existingTaskId = task.id,
            ),
        )
    }

    // ---- AI task generation (proposals only; the user approves them) ----

    fun generateTasks() {
        val ui = state.value
        val text = ui.form.description.trim()
        if (text.isEmpty()) { _messages.tryEmit("Describe what you want to study first"); return }
        if (aiState.value.generating) return
        aiState.update { it.copy(generating = true, proposals = emptyList(), note = null) }
        viewModelScope.launch {
            val chapterNames = ui.allChapters.filter { it.id in ui.form.chapterIds }.map { it.name }
            val r = try {
                aiService.extractTasks(text, ui.subject?.name, chapterNames)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            aiState.update {
                if (r == null) it.copy(generating = false, note = "Could not generate tasks. You can add them by hand.")
                else it.copy(
                    generating = false,
                    origin = r.source,
                    note = r.note,
                    proposals = r.tasks.map { t -> Proposal(t.title, t.minutes, t.subtasks) },
                )
            }
            if (r != null && r.tasks.isEmpty()) _messages.tryEmit("No tasks found in that description")
        }
    }

    fun toggleProposal(i: Int) = aiState.update { a ->
        a.copy(proposals = a.proposals.mapIndexed { idx, p -> if (idx == i) p.copy(selected = !p.selected) else p })
    }

    fun acceptProposals() {
        val a = aiState.value
        val chosen = a.proposals.filter { it.selected }
        addDraftTasks(chosen.map { DraftTask(it.title, it.minutes, origin = a.origin, subtasks = it.subtasks) })
        aiState.update { it.copy(proposals = emptyList(), note = null) }
    }

    fun discardProposals() = aiState.update { it.copy(proposals = emptyList(), note = null) }

    // ---- voice ----

    fun startVoice() {
        viewModelScope.launch {
            val offlineOnly = settings.voiceOfflineOnly.first()
            val ok = voice.start(offlineOnly, object : VoiceListener {
                override fun onListening() = aiState.update { it.copy(listening = true, partial = "") }
                override fun onPartial(text: String) = aiState.update { it.copy(partial = text) }
                override fun onFinal(text: String) {
                    aiState.update { it.copy(listening = false, partial = "") }
                    // Appended to the editable box, so the user can correct it before using it.
                    form.update { f -> f.copy(description = (f.description.trim() + " " + text).trim()) }
                }
                override fun onError(message: String) {
                    aiState.update { it.copy(listening = false, partial = "") }
                    _messages.tryEmit(message)
                }
            })
            if (!ok) {
                _messages.tryEmit(
                    if (offlineOnly) "No offline speech recognizer on this phone. Type instead, or allow online voice in Settings."
                    else "Voice input isn't available on this phone. Type instead.",
                )
            }
        }
    }

    fun stopVoice() { voice.finishListening() }

    override fun onCleared() { voice.stop() }

    // ---- presets ----

    fun loadPreset(id: Long) {
        viewModelScope.launch {
            val p = presetRepo.load(id) ?: return@launch
            form.update {
                it.copy(
                    subjectId = p.subjectId ?: it.subjectId,
                    chapterIds = p.chapterIds,
                    mode = p.mode,
                    durationMinutes = p.durationMinutes,
                    strict = p.strict,
                    launchSnapGpt = p.launchSnapGpt,
                    loadedPresetId = p.id,
                )
            }
        }
    }

    fun savePreset(name: String, updateExisting: Boolean) {
        val f = form.value
        viewModelScope.launch {
            val existing = if (updateExisting) f.loadedPresetId?.let { presetRepo.load(it) } else null
            val draft = (existing ?: PresetDraft(name = name)).copy(
                name = if (existing != null) existing.name else name,
                subjectId = f.subjectId,
                chapterIds = f.chapterIds,
                mode = f.mode,
                durationMinutes = f.durationMinutes,
                strict = f.strict,
                launchSnapGpt = f.launchSnapGpt,
            )
            when (val r = presetRepo.save(draft)) {
                is PresetRepository.SaveResult.Saved -> {
                    form.update { it.copy(loadedPresetId = r.id) }
                    _messages.tryEmit("Preset saved")
                }
                PresetRepository.SaveResult.Blank -> _messages.tryEmit("Preset name can't be empty")
                PresetRepository.SaveResult.DuplicateName -> _messages.tryEmit("A preset with that name already exists")
            }
        }
    }

    fun deleteLoadedPreset() {
        val id = form.value.loadedPresetId ?: return
        viewModelScope.launch {
            presetRepo.delete(id)
            form.update { it.copy(loadedPresetId = null) }
            _messages.tryEmit("Preset deleted")
        }
    }

    // ---- start ----

    fun start() {
        val ui = state.value
        val subject = ui.subject ?: run { _messages.tryEmit("Choose a subject first"); return }
        if (starting.value) return
        starting.value = true
        val f = ui.form
        viewModelScope.launch {
            try {
                val result = sessions.start(
                    SessionDraft(
                        subjectId = subject.id,
                        subjectName = subject.name,
                        chapterIds = f.chapterIds,
                        description = f.description,
                        mode = f.mode,
                        plannedMinutes = if (f.mode == SessionMode.TIMED) f.durationMinutes else null,
                        strict = f.strict,
                        presetId = f.loadedPresetId,
                        tasks = f.tasks,
                    ),
                )
                when (result) {
                    is StartResult.Started -> _started.tryEmit(result.sessionId)
                    StartResult.AlreadyActive -> _messages.tryEmit("A session is already running")
                }
            } finally {
                starting.value = false
            }
        }
    }
}
