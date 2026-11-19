package com.focusflow.app.data.sync

import android.content.Context
import com.focusflow.app.data.security.SecureTextStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SyncedTask(
    val title: String,
    val completed: Boolean,
    val estimatedMinutes: Int? = null,
    val completedAt: Long? = null,
)

data class SyncedSession(
    /** Origin-device/session key, retained when another phone relays this record. */
    val globalId: String,
    val subjectName: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long,
    val status: String,
    val plannedDurationMs: Long? = null,
    val strict: Boolean = true,
    val description: String = "",
    val breakMsCompleted: Long = 0,
    val breakStartedAt: Long? = null,
    val breakEndsAt: Long? = null,
    val shortBreaksUsed: Int = 0,
    val longBreaksUsed: Int = 0,
    val chapterNames: List<String> = emptyList(),
    val tasks: List<SyncedTask> = emptyList(),
)

class LocalSyncRepository(context: Context) {
    private val secure = SecureTextStore(
        context.applicationContext,
        "focusflow_local_sync_secure",
        "focusflow_local_sync_key",
    )

    val deviceId: String = secure.get("device_id") ?: UUID.randomUUID().toString().also {
        secure.put("device_id", it)
    }

    private val _pairingCode = MutableStateFlow(secure.get("pairing_code"))
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()
    private val _records = MutableStateFlow(readRecords())
    val records: StateFlow<List<SyncedSession>> = _records.asStateFlow()

    fun savePairingCode(value: String): Boolean {
        val clean = value.trim()
        if (clean.length !in 10..128) return false
        secure.put("pairing_code", clean)
        _pairingCode.value = clean
        return true
    }

    fun clearPairingCode() {
        secure.put("pairing_code", null)
        _pairingCode.value = null
    }

    @Synchronized
    fun merge(incoming: List<SyncedSession>): Int {
        if (incoming.isEmpty()) return 0
        val before = _records.value.associateBy { it.globalId }
        val merged = LinkedHashMap(before)
        incoming.take(MAX_RECORDS).forEach { record ->
            if (record.globalId.isNotBlank() && record.status != "ACTIVE") {
                merged[record.globalId] = record
            }
        }
        val result = merged.values.sortedByDescending { it.startedAt }.take(MAX_RECORDS)
        val changed = result.count { before[it.globalId] != it }
        _records.value = result
        secure.put(RECORDS_KEY, encodeRecords(result))
        return changed
    }

    /** The phone's own DB values are placed last so they win over stale relayed copies. */
    fun buildPayload(localRecords: List<SyncedSession>): String {
        val combined = (_records.value + localRecords)
            .filter { it.globalId.isNotBlank() && it.status != "ACTIVE" }
            .associateBy { it.globalId }
            .values
            .sortedByDescending { it.startedAt }
            .take(MAX_RECORDS)
        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("deviceId", deviceId)
            .put("records", JSONArray().apply { combined.forEach { put(it.toJson()) } })
            .toString()
    }

    fun mergePayload(payload: String): Int {
        val root = JSONObject(payload)
        require(root.optInt("protocol") == PROTOCOL) { "Unsupported sync protocol" }
        val array = root.optJSONArray("records") ?: JSONArray()
        val incoming = buildList {
            for (i in 0 until minOf(array.length(), MAX_RECORDS)) {
                val item = array.optJSONObject(i) ?: continue
                runCatching { item.toRecord() }.getOrNull()?.let(::add)
            }
        }
        return merge(incoming)
    }

    private fun readRecords(): List<SyncedSession> = runCatching {
        val raw = secure.get(RECORDS_KEY) ?: return emptyList()
        decodeRecords(raw)
    }.getOrDefault(emptyList())

    private fun encodeRecords(records: List<SyncedSession>): String =
        JSONArray().apply { records.forEach { put(it.toJson()) } }.toString()

    private fun decodeRecords(raw: String): List<SyncedSession> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until minOf(array.length(), MAX_RECORDS)) {
                val item = array.optJSONObject(i) ?: continue
                runCatching { item.toRecord() }.getOrNull()?.let(::add)
            }
        }
    }

    private fun SyncedSession.toJson() = JSONObject()
        .put("globalId", globalId)
        .put("subjectName", subjectName)
        .put("mode", mode)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt)
        .put("status", status)
        .put("plannedDurationMs", plannedDurationMs ?: JSONObject.NULL)
        .put("strict", strict)
        .put("description", description)
        .put("breakMsCompleted", breakMsCompleted)
        .put("breakStartedAt", breakStartedAt ?: JSONObject.NULL)
        .put("breakEndsAt", breakEndsAt ?: JSONObject.NULL)
        .put("shortBreaksUsed", shortBreaksUsed)
        .put("longBreaksUsed", longBreaksUsed)
        .put("chapterNames", JSONArray(chapterNames.take(100)))
        .put("tasks", JSONArray().apply {
            tasks.take(200).forEach { task ->
                put(JSONObject()
                    .put("title", task.title.take(160))
                    .put("completed", task.completed)
                    .put("estimatedMinutes", task.estimatedMinutes ?: JSONObject.NULL)
                    .put("completedAt", task.completedAt ?: JSONObject.NULL))
            }
        })

    private fun JSONObject.toRecord(): SyncedSession {
        val chapterArray = optJSONArray("chapterNames") ?: JSONArray()
        val taskArray = optJSONArray("tasks") ?: JSONArray()
        val chapterList = buildList {
            for (i in 0 until minOf(chapterArray.length(), 100)) {
                chapterArray.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
        val taskList = buildList {
            for (i in 0 until minOf(taskArray.length(), 200)) {
                val item = taskArray.optJSONObject(i) ?: continue
                val title = item.optString("title").trim().take(160)
                if (title.isNotEmpty()) add(
                    SyncedTask(
                        title = title,
                        completed = item.optBoolean("completed"),
                        estimatedMinutes = item.optInt("estimatedMinutes").takeIf { item.has("estimatedMinutes") && !item.isNull("estimatedMinutes") },
                        completedAt = item.optLong("completedAt").takeIf { item.has("completedAt") && !item.isNull("completedAt") },
                    ),
                )
            }
        }
        return SyncedSession(
            globalId = optString("globalId").take(180),
            subjectName = optString("subjectName").take(120),
            mode = optString("mode", "OPEN_ENDED"),
            startedAt = optLong("startedAt"),
            endedAt = optLong("endedAt"),
            status = optString("status", "COMPLETED"),
            plannedDurationMs = optLong("plannedDurationMs").takeIf { has("plannedDurationMs") && !isNull("plannedDurationMs") },
            strict = optBoolean("strict", true),
            description = optString("description").take(2000),
            breakMsCompleted = optLong("breakMsCompleted"),
            breakStartedAt = optLong("breakStartedAt").takeIf { has("breakStartedAt") && !isNull("breakStartedAt") },
            breakEndsAt = optLong("breakEndsAt").takeIf { has("breakEndsAt") && !isNull("breakEndsAt") },
            shortBreaksUsed = optInt("shortBreaksUsed"),
            longBreaksUsed = optInt("longBreaksUsed"),
            chapterNames = chapterList,
            tasks = taskList,
        )
    }

    companion object {
        private const val RECORDS_KEY = "synced_sessions"
        private const val PROTOCOL = 1
        private const val MAX_RECORDS = 5000
    }
}
