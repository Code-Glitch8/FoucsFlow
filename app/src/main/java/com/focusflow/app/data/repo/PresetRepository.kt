package com.focusflow.app.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.focusflow.app.data.db.AllowedApplicationEntity
import com.focusflow.app.data.db.AppCategory
import com.focusflow.app.data.db.BlockedApplicationEntity
import com.focusflow.app.data.db.BreakConfigurationEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.data.db.PresetChapterEntity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.db.StudyPresetEntity
import com.focusflow.app.domain.BreakConfig
import com.focusflow.app.domain.TaskNormalizer
import kotlinx.coroutines.flow.Flow

data class BlockedAppRule(
    val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.CUSTOM,
    val blockedDuringBreak: Boolean = false,
)

data class AllowedAppRule(val packageName: String, val label: String)

/** Editable form of a preset, independent of how it is split across tables. */
data class PresetDraft(
    val id: Long = 0,
    val name: String,
    val subjectId: Long? = null,
    val chapterIds: Set<Long> = emptySet(),
    val mode: SessionMode = SessionMode.TIMED,
    val durationMinutes: Int = 25,
    val strict: Boolean = true,
    val blockYoutubeShorts: Boolean = true,
    val floatingAssistant: Boolean = true,
    val launchSnapGpt: Boolean = false,
    val blockedApps: List<BlockedAppRule> = emptyList(),
    val allowedApps: List<AllowedAppRule> = emptyList(),
    val breakConfig: BreakConfig = BreakConfig(),
)

class PresetRepository(private val db: FocusFlowDatabase) {
    private val presets = db.presetDao()
    private val rules = db.appRuleDao()

    val allPresets: Flow<List<StudyPresetEntity>> = presets.observeAll()

    suspend fun load(presetId: Long): PresetDraft? {
        val p = presets.getById(presetId) ?: return null
        val bc = rules.getBreakConfigForPreset(presetId)
        return PresetDraft(
            id = p.id,
            name = p.name,
            subjectId = p.subjectId,
            chapterIds = presets.chapterIds(presetId).toSet(),
            mode = p.mode,
            durationMinutes = p.durationMinutes,
            strict = p.strict,
            blockYoutubeShorts = p.blockYoutubeShorts,
            floatingAssistant = p.floatingAssistant,
            launchSnapGpt = p.launchSnapGpt,
            blockedApps = rules.getBlockedForPreset(presetId).map {
                BlockedAppRule(it.packageName, it.label, it.category, it.blockedDuringBreak)
            },
            allowedApps = rules.getAllowedForPreset(presetId).map { AllowedAppRule(it.packageName, it.label) },
            breakConfig = bc?.toConfig() ?: BreakConfig(),
        )
    }

    /** Creates or updates a preset with all of its related rows atomically. */
    suspend fun save(draft: PresetDraft): SaveResult {
        val name = TaskNormalizer.clean(draft.name)
        if (name.isEmpty()) return SaveResult.Blank
        return try {
            val id = db.withTransaction {
                val entity = StudyPresetEntity(
                    id = draft.id,
                    name = name,
                    subjectId = draft.subjectId,
                    mode = draft.mode,
                    durationMinutes = draft.durationMinutes,
                    strict = draft.strict,
                    blockYoutubeShorts = draft.blockYoutubeShorts,
                    floatingAssistant = draft.floatingAssistant,
                    launchSnapGpt = draft.launchSnapGpt,
                )
                val presetId = if (draft.id == 0L) {
                    presets.insert(entity)
                } else {
                    presets.update(entity)
                    draft.id
                }
                presets.clearChapters(presetId)
                presets.insertChapters(draft.chapterIds.map { PresetChapterEntity(presetId, it) })
                rules.clearBlockedForPreset(presetId)
                rules.insertBlocked(
                    draft.blockedApps.map {
                        BlockedApplicationEntity(
                            presetId = presetId,
                            packageName = it.packageName,
                            label = it.label,
                            category = it.category,
                            blockedDuringBreak = it.blockedDuringBreak,
                        )
                    },
                )
                rules.clearAllowedForPreset(presetId)
                rules.insertAllowed(
                    draft.allowedApps.map { AllowedApplicationEntity(presetId = presetId, packageName = it.packageName, label = it.label) },
                )
                rules.clearBreakConfigForPreset(presetId)
                rules.insertBreakConfig(draft.breakConfig.toEntity(presetId))
                presetId
            }
            SaveResult.Saved(id)
        } catch (e: SQLiteConstraintException) {
            SaveResult.DuplicateName
        }
    }

    suspend fun delete(presetId: Long) = presets.delete(presetId)

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object Blank : SaveResult
        data object DuplicateName : SaveResult
    }
}

fun BreakConfigurationEntity.toConfig() = BreakConfig(
    enabled = enabled,
    tasksForShortBreak = tasksForShortBreak,
    shortBreakMinutes = shortBreakMinutes,
    tasksForLongBreak = tasksForLongBreak,
    longBreakMinutes = longBreakMinutes,
)

fun BreakConfig.toEntity(presetId: Long?) = BreakConfigurationEntity(
    presetId = presetId,
    enabled = enabled,
    tasksForShortBreak = tasksForShortBreak,
    shortBreakMinutes = shortBreakMinutes,
    tasksForLongBreak = tasksForLongBreak,
    longBreakMinutes = longBreakMinutes,
)
