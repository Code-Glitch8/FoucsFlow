package com.focusflow.app.ai.external

import com.focusflow.app.data.security.SecretStore
import com.focusflow.app.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A provider that can actually be used right now. */
data class ReadyProvider(val config: ProviderConfig, val apiKey: String)

class ProviderRepository(private val settings: AppSettings, private val secrets: SecretStore) {
    val providers: Flow<List<ProviderConfig>> = settings.aiProvidersJson.map { ProviderJson.fromJson(it) }
    val selectedId: Flow<String?> = settings.aiSelectedProvider

    private fun keyName(id: String) = "provider_key_$id"

    fun hasKey(id: String): Boolean = !secrets.get(keyName(id)).isNullOrBlank()

    /** Saves the config; the key is saved only when [apiKey] is non-null (null keeps the stored key). Returns false if the key could not be stored securely. */
    suspend fun save(config: ProviderConfig, apiKey: String?): Boolean {
        if (!apiKey.isNullOrBlank() && !secrets.put(keyName(config.id), apiKey.trim())) return false
        val list = providers.first().filter { it.id != config.id } + config.copy(
            endpoint = config.endpoint.trim(), model = config.model.trim(), name = config.name.trim().ifEmpty { config.type.label },
        )
        settings.setAiProvidersJson(ProviderJson.toJson(list))
        return true
    }

    suspend fun delete(id: String) {
        secrets.remove(keyName(id))
        settings.setAiProvidersJson(ProviderJson.toJson(providers.first().filter { it.id != id }))
        if (selectedId.first() == id) settings.setAiSelectedProvider(null)
    }

    suspend fun select(id: String?) = settings.setAiSelectedProvider(id)

    fun apiKey(id: String): String? = secrets.get(keyName(id))

    /** The user-selected provider, only if it is enabled, has an endpoint/model and a stored key. */
    suspend fun ready(): ReadyProvider? {
        val id = selectedId.first() ?: return null
        val cfg = providers.first().firstOrNull { it.id == id } ?: return null
        if (!cfg.enabled || cfg.endpoint.isBlank() || cfg.model.isBlank()) return null
        val key = secrets.get(keyName(id))?.takeIf { it.isNotBlank() } ?: return null
        return ReadyProvider(cfg, key)
    }
}
