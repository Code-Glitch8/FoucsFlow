package com.focusflow.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 1 -> 2. Purely additive, so no existing row is touched:
 *  - study_sessions gets per-session floating-assistant / Shorts switches (defaults match old behaviour),
 *  - preset_tasks stores task templates inside presets,
 *  - dashboard_widgets stores the customisable dashboard widgets.
 */
val MIGRATION_1_2_SQL: List<String> = listOf(
    "ALTER TABLE `study_sessions` ADD COLUMN `floatingAssistant` INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE `study_sessions` ADD COLUMN `blockShorts` INTEGER NOT NULL DEFAULT 1",
    "CREATE TABLE IF NOT EXISTS `preset_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`presetId` INTEGER NOT NULL, `title` TEXT NOT NULL, `estimatedMinutes` INTEGER, `chapterId` INTEGER, " +
        "`subtasks` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "FOREIGN KEY(`presetId`) REFERENCES `study_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_preset_tasks_presetId` ON `preset_tasks` (`presetId`)",
    "CREATE TABLE IF NOT EXISTS `dashboard_widgets` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`icon` TEXT NOT NULL, `colorKey` TEXT NOT NULL, `size` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "`visible` INTEGER NOT NULL, `mode` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `strict` INTEGER NOT NULL, " +
        "`subjectId` INTEGER, `chapterIds` TEXT NOT NULL, `taskIds` TEXT NOT NULL, `presetId` INTEGER, " +
        "`action` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_dashboard_widgets_position` ON `dashboard_widgets` (`position`)",
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
    }
}
