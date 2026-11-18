package com.focusflow.app

import com.focusflow.app.data.db.BackupFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {
    private fun sqlite(version: Int): File {
        val f = File.createTempFile("backup", ".db")
        val b = ByteArray(4096)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(b)
        b[60] = (version shr 24).toByte(); b[61] = (version shr 16).toByte(); b[62] = (version shr 8).toByte(); b[63] = version.toByte()
        f.writeBytes(b)
        f.deleteOnExit()
        return f
    }

    @Test fun readsSchemaVersion() = assertEquals(3, BackupFile.userVersion(sqlite(3)))

    @Test fun acceptsSameOrOlderVersionOnly() {
        assertTrue(BackupFile.isAcceptable(sqlite(1), 2))
        assertTrue(BackupFile.isAcceptable(sqlite(2), 2))
        assertFalse(BackupFile.isAcceptable(sqlite(3), 2))
        assertFalse(BackupFile.isAcceptable(sqlite(0), 2))
    }

    @Test fun rejectsNonSqliteAndTinyFiles() {
        val junk = File.createTempFile("junk", ".db").apply { writeBytes(ByteArray(500) { 7 }); deleteOnExit() }
        assertNull(BackupFile.userVersion(junk))
        val tiny = File.createTempFile("tiny", ".db").apply { writeBytes(ByteArray(10)); deleteOnExit() }
        assertFalse(BackupFile.isAcceptable(tiny, 1))
    }
}
