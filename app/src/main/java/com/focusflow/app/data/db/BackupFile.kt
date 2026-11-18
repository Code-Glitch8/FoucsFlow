package com.focusflow.app.data.db

import java.io.File

/** Checks a file before it is allowed to replace the database. Pure file logic, unit tested. */
object BackupFile {
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** SQLite's `user_version` is a big-endian int at byte offset 60; Room stores the schema version there. */
    fun userVersion(file: File): Int? {
        if (!file.isFile || file.length() < 100) return null
        val head = ByteArray(100)
        file.inputStream().use { if (it.read(head) < 100) return null }
        if (!head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        return ((head[60].toInt() and 0xFF) shl 24) or ((head[61].toInt() and 0xFF) shl 16) or
            ((head[62].toInt() and 0xFF) shl 8) or (head[63].toInt() and 0xFF)
    }

    /** Accepts SQLite files whose schema version is at most [currentVersion] (older ones are migrated on open). */
    fun isAcceptable(file: File, currentVersion: Int): Boolean {
        val v = userVersion(file) ?: return false
        return v in 1..currentVersion
    }
}
