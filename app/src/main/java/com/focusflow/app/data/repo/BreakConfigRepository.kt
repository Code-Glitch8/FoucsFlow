package com.focusflow.app.data.repo

import androidx.room.withTransaction
import com.focusflow.app.data.db.AppRuleDao
import com.focusflow.app.data.db.FocusFlowDatabase
import com.focusflow.app.domain.BreakConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The global (non-preset) break rules, editable from Settings. */
class BreakConfigRepository(
    private val db: FocusFlowDatabase,
    private val dao: AppRuleDao = db.appRuleDao(),
) {
    val global: Flow<BreakConfig> = dao.observeGlobalBreakConfig().map { it?.toConfig() ?: BreakConfig() }

    suspend fun save(config: BreakConfig) {
        val sane = config.copy(
            tasksForShortBreak = config.tasksForShortBreak.coerceIn(1, 20),
            shortBreakMinutes = config.shortBreakMinutes.coerceIn(1, 60),
            tasksForLongBreak = config.tasksForLongBreak.coerceIn(1, 40),
            longBreakMinutes = config.longBreakMinutes.coerceIn(1, 120),
        )
        db.withTransaction {
            dao.clearGlobalBreakConfig()
            dao.insertBreakConfig(sane.toEntity(presetId = null))
        }
    }
}
