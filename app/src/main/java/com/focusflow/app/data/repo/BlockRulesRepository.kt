package com.focusflow.app.data.repo

import androidx.room.withTransaction
import com.focusflow.app.data.db.AllowedApplicationEntity
import com.focusflow.app.data.db.AppCategory
import com.focusflow.app.data.db.BlockedApplicationEntity
import com.focusflow.app.data.db.FocusFlowDatabase
import kotlinx.coroutines.flow.Flow

/** The global blocked and allowed app lists (presetId = null rows). */
class BlockRulesRepository(
    private val db: FocusFlowDatabase,
    private val dao: com.focusflow.app.data.db.AppRuleDao = db.appRuleDao(),
) {
    val blocked: Flow<List<BlockedApplicationEntity>> = dao.observeGlobalBlocked()
    val allowed: Flow<List<AllowedApplicationEntity>> = dao.observeGlobalAllowed()

    /** Blocking an app removes it from the allowed list (an app is in at most one list). */
    suspend fun block(packageName: String, label: String, category: AppCategory, blockedDuringBreak: Boolean = false) {
        db.withTransaction {
            dao.deleteGlobalAllowed(packageName)
            dao.deleteGlobalBlocked(packageName)
            dao.insertBlocked(
                listOf(
                    BlockedApplicationEntity(
                        presetId = null,
                        packageName = packageName,
                        label = label,
                        category = category,
                        blockedDuringBreak = blockedDuringBreak,
                    ),
                ),
            )
        }
    }

    suspend fun blockMany(apps: List<Triple<String, String, AppCategory>>) {
        db.withTransaction {
            val existing = dao.getGlobalBlocked().associateBy { it.packageName }
            apps.forEach { (pkg, label, cat) ->
                if (pkg !in existing) {
                    dao.deleteGlobalAllowed(pkg)
                    dao.insertBlocked(listOf(BlockedApplicationEntity(presetId = null, packageName = pkg, label = label, category = cat)))
                }
            }
        }
    }

    suspend fun allow(packageName: String, label: String) {
        db.withTransaction {
            dao.deleteGlobalBlocked(packageName)
            dao.deleteGlobalAllowed(packageName)
            dao.insertAllowed(listOf(AllowedApplicationEntity(presetId = null, packageName = packageName, label = label)))
        }
    }

    suspend fun clear(packageName: String) {
        db.withTransaction {
            dao.deleteGlobalBlocked(packageName)
            dao.deleteGlobalAllowed(packageName)
        }
    }

    suspend fun setBlockedDuringBreak(rule: BlockedApplicationEntity, value: Boolean) {
        db.withTransaction {
            dao.deleteGlobalBlocked(rule.packageName)
            dao.insertBlocked(listOf(rule.copy(blockedDuringBreak = value)))
        }
    }
}
