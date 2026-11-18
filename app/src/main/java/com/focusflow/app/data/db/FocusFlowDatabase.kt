package com.focusflow.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        SubjectEntity::class,
        ChapterEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        StudySessionEntity::class,
        SessionChapterEntity::class,
        SessionTaskEntity::class,
        StudyPresetEntity::class,
        PresetChapterEntity::class,
        BlockedApplicationEntity::class,
        AllowedApplicationEntity::class,
        BreakConfigurationEntity::class,
        SessionSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FocusFlowDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun chapterDao(): ChapterDao
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun presetDao(): PresetDao
    abstract fun appRuleDao(): AppRuleDao

    companion object {
        /**
         * Every schema change must add a [Migration] here. Destructive fallback is deliberately
         * NOT enabled: losing a user's study history on an app update is worse than a crash
         * that a developer will notice.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        private const val DB_NAME = "focusflow.db"
        const val PENDING_IMPORT = "pending_import.db"

        /**
         * An imported backup is only swapped in here, before Room opens the file, so the live
         * database is never replaced underneath running code. Anything that is not a SQLite file
         * from this app (same or older schema) is ignored and deleted.
         */
        private fun applyPendingImport(context: Context) {
            val pending = context.getDatabasePath(PENDING_IMPORT)
            if (!pending.exists()) return
            try {
                if (BackupFile.isAcceptable(pending, CURRENT_VERSION)) {
                    val target = context.getDatabasePath(DB_NAME)
                    target.parentFile?.mkdirs()
                    listOf("", "-wal", "-shm", "-journal").forEach { java.io.File(target.path + it).delete() }
                    pending.copyTo(target, overwrite = true)
                }
            } finally {
                pending.delete()
            }
        }

        const val CURRENT_VERSION = 1

        fun build(context: Context): FocusFlowDatabase {
            applyPendingImport(context)
            return buildInternal(context)
        }

        private fun buildInternal(context: Context): FocusFlowDatabase =
            Room.databaseBuilder(context.applicationContext, FocusFlowDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
