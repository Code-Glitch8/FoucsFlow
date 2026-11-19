package com.focusflow.app.data.repo

import com.focusflow.app.ai.external.ProviderConfig
import com.focusflow.app.ai.external.ProviderJson
import org.json.JSONException
import org.json.JSONObject

/**
 * Portable, human-readable export of app settings. It deliberately contains no secrets: API keys are
 * bound to this phone's Android Keystore and are never written to a backup. Provider endpoints and
 * models are included so they only need their key re-entered after a restore.
 */
data class SettingsSnapshot(
    val theme: String? = null,
    val defaultMode: String? = null,
    val defaultDurationMin: Int? = null,
    val shortsMode: String? = null,
    val homeRedirect: Boolean? = null,
    val blockAllExceptAllowed: Boolean? = null,
    val floatingEnabled: Boolean? = null,
    val snapGptEnabled: Boolean? = null,
    val snapGptPackage: String? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProvider: String? = null,
)

sealed interface SettingsParse {
    data class Ok(val snapshot: SettingsSnapshot) : SettingsParse
    data class Rejected(val reason: String) : SettingsParse
}

object SettingsBackup {
    const val FORMAT = "focusflow-settings"
    const val VERSION = 1

    fun toJson(s: SettingsSnapshot): String {
        val o = JSONObject().put("format", FORMAT).put("version", VERSION)
        s.theme?.let { o.put("theme", it) }
        s.defaultMode?.let { o.put("defaultMode", it) }
        s.defaultDurationMin?.let { o.put("defaultDurationMin", it) }
        s.shortsMode?.let { o.put("shortsMode", it) }
        s.homeRedirect?.let { o.put("homeRedirect", it) }
        s.blockAllExceptAllowed?.let { o.put("blockAllExceptAllowed", it) }
        s.floatingEnabled?.let { o.put("floatingEnabled", it) }
        s.snapGptEnabled?.let { o.put("snapGptEnabled", it) }
        s.snapGptPackage?.let { o.put("snapGptPackage", it) }
        o.put("providers", JSONObject(ProviderJson.toJson(s.providers)).getJSONArray("providers"))
        s.selectedProvider?.let { o.put("selectedProvider", it) }
        return o.toString(2)
    }

    fun parse(json: String): SettingsParse {
        val o = try { JSONObject(json) } catch (e: JSONException) { return SettingsParse.Rejected("That is not a FocusFlow settings file.") }
        if (o.optString("format") != FORMAT) return SettingsParse.Rejected("That is not a FocusFlow settings file.")
        val v = o.optInt("version", -1)
        if (v < 1) return SettingsParse.Rejected("Unreadable settings file version.")
        if (v > VERSION) return SettingsParse.Rejected("This settings file comes from a newer version of FocusFlow.")
        fun str(k: String) = if (o.has(k)) o.optString(k) else null
        fun bool(k: String) = if (o.has(k)) o.optBoolean(k) else null
        val providers = ProviderJson.fromJson(JSONObject().put("providers", o.optJSONArray("providers") ?: org.json.JSONArray()).toString())
        return SettingsParse.Ok(
            SettingsSnapshot(
                theme = str("theme"), defaultMode = str("defaultMode"),
                defaultDurationMin = if (o.has("defaultDurationMin")) o.optInt("defaultDurationMin") else null,
                shortsMode = str("shortsMode"), homeRedirect = bool("homeRedirect"), blockAllExceptAllowed = bool("blockAllExceptAllowed"), floatingEnabled = bool("floatingEnabled"),
                snapGptEnabled = bool("snapGptEnabled"), snapGptPackage = str("snapGptPackage"),
                providers = providers, selectedProvider = str("selectedProvider"),
            ),
        )
    }

    /** Merges imported providers into existing ones by id, so importing twice never duplicates. */
    fun mergeProviders(existing: List<ProviderConfig>, imported: List<ProviderConfig>): List<ProviderConfig> {
        val byId = LinkedHashMap<String, ProviderConfig>()
        existing.forEach { byId[it.id] = it }
        imported.forEach { byId[it.id] = it }
        return byId.values.toList()
    }
}
