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

        fun build(context: Context): FocusFlowDatabase =
            Room.databaseBuilder(context.applicationContext, FocusFlowDatabase::class.java, "focusflow.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
