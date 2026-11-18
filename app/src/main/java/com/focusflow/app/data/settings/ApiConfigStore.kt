package com.focusflow.app.data.settings

import android.content.Context
import com.focusflow.app.data.security.SecureTextStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ApiProvider { OPENAI_COMPATIBLE, GEMINI }

data class ApiProfile(
    val id: String,
    val name: String,
    val provider: ApiProvider,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

class ApiConfigStore(context: Context) {
    private val secure = SecureTextStore(
        context.applicationContext,
        "focusflow_api_secure",
        "focusflow_api_secure_key",
    )
    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<List<ApiProfile>> = _profiles.asStateFlow()
    private val _activeId = MutableStateFlow(secure.get("active_profile_id"))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun activeProfile(): ApiProfile? = _profiles.value.firstOrNull { it.id == _activeId.value }

    @Synchronized
    fun save(profile: ApiProfile, activate: Boolean = true) {
        val updated = (_profiles.value.filterNot { it.id == profile.id } + profile).takeLast(MAX_PROFILES)
        secure.put(PROFILES_KEY, encode(updated))
        _profiles.value = updated
        if (activate || _activeId.value == null) setActive(profile.id)
    }

    @Synchronized
    fun setActive(id: String) {
        if (_profiles.value.none { it.id == id }) return
        secure.put("active_profile_id", id)
        _activeId.value = id
    }

    @Synchronized
    fun delete(id: String) {
        val updated = _profiles.value.filterNot { it.id == id }
        secure.put(PROFILES_KEY, encode(updated))
        _profiles.value = updated
        if (_activeId.value == id) {
            val next = updated.firstOrNull()?.id
            secure.put("active_profile_id", next)
            _activeId.value = next
        }
    }

    private fun readProfiles(): List<ApiProfile> = runCatching {
        val raw = secure.get(PROFILES_KEY) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until minOf(array.length(), MAX_PROFILES)) {
                val item = array.optJSONObject(i) ?: continue
                val provider = runCatching { ApiProvider.valueOf(item.optString("provider")) }.getOrNull() ?: continue
                val key = item.optString("apiKey").take(4096)
                val name = item.optString("name").trim().take(80)
                val model = item.optString("model").trim().take(120)
                if (key.isNotBlank() && name.isNotBlank() && model.isNotBlank()) add(
                    ApiProfile(
                        id = item.optString("id").take(80),
                        name = name,
                        provider = provider,
                        baseUrl = item.optString("baseUrl").take(500),
                        model = model,
                        apiKey = key,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(profiles: List<ApiProfile>) = JSONArray().apply {
        profiles.forEach { profile ->
            put(JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("provider", profile.provider.name)
                .put("baseUrl", profile.baseUrl)
                .put("model", profile.model)
                .put("apiKey", profile.apiKey))
        }
    }.toString()

    companion object {
        private const val PROFILES_KEY = "api_profiles"
        private const val MAX_PROFILES = 20
        fun newId(): String = UUID.randomUUID().toString()
    }
}
