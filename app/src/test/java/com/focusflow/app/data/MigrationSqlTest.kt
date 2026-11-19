package com.focusflow.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.focusflow.app.data.db.MIGRATION_1_2_SQL
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room validates the schema when it opens a migrated database and crashes on any mismatch. This
 * test applies the hand-written 1 -> 2 SQL to stand-in v1 tables and compares the result with what
 * Room itself generates for the v2 entities, so a mismatch is caught here instead of on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationSqlTest : RoomTestBase() {

    private fun scratch(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE `study_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    db.execSQL("CREATE TABLE `study_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    db.execSQL("INSERT INTO study_sessions DEFAULT VALUES")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        return helper.writableDatabase
    }

    private fun columns(db: SupportSQLiteDatabase, table: String, only: Set<String>? = null): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                if (only == null || name in only) {
                    out += "$name|${c.getString(c.getColumnIndexOrThrow("type"))}|${c.getInt(c.getColumnIndexOrThrow("notnull"))}|${c.getInt(c.getColumnIndexOrThrow("pk"))}"
                }
            }
            out.sorted()
        }

    private fun indexes(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) {
                val n = c.getString(c.getColumnIndexOrThrow("name"))
                if (!n.startsWith("sqlite_")) out += n
            }
            out.sorted()
        }

    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) {
                out += listOf("table", "from", "to", "on_delete").joinToString("|") { c.getString(c.getColumnIndexOrThrow(it)) }
            }
            out.sorted()
        }

    @Test fun `migration sql produces the same schema Room generates`() {
        val room = db.openHelper.writableDatabase
        val old = scratch()
        MIGRATION_1_2_SQL.forEach { old.execSQL(it) }

        for (t in listOf("dashboard_widgets", "preset_tasks")) {
            assertEquals("columns of $t", columns(room, t), columns(old, t))
            assertEquals("indexes of $t", indexes(room, t), indexes(old, t))
            assertEquals("foreign keys of $t", foreignKeys(room, t), foreignKeys(old, t))
        }
        val added = setOf("floatingAssistant", "blockShorts")
        assertEquals(columns(room, "study_sessions", added), columns(old, "study_sessions", added))
    }

    @Test fun `existing sessions keep working after migration with old behaviour defaults`() {
        val old = scratch()
        MIGRATION_1_2_SQL.forEach { old.execSQL(it) }
        old.query("SELECT floatingAssistant, blockShorts FROM study_sessions").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
            assertEquals(1, c.getInt(1))
        }
    }
}
