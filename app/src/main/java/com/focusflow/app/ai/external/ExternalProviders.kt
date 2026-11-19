package com.focusflow.app.ai.external

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class ProviderType(val label: String, val defaultEndpoint: String, val defaultModel: String) {
    OPENAI_COMPATIBLE("OpenAI-compatible", "https://api.openai.com/v1", "gpt-4o-mini"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
}

/** Non-secret provider settings. The API key lives in [com.focusflow.app.data.security.SecretStore], never here. */
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: ProviderType,
    val name: String,
    val endpoint: String,
    val model: String,
    val enabled: Boolean = true,
) {
    companion object {
        fun default(type: ProviderType) = ProviderConfig(type = type, name = type.label, endpoint = type.defaultEndpoint, model = type.defaultModel)
    }
}

object ProviderJson {
    const val VERSION = 1

    fun toJson(list: List<ProviderConfig>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type.name).put("name", it.name)
                .put("endpoint", it.endpoint).put("model", it.model).put("enabled", it.enabled))
        }
        return JSONObject().put("version", VERSION).put("providers", arr).toString()
    }

    /** Tolerant: unknown types and malformed rows are skipped, never thrown. */
    fun fromJson(json: String?): List<ProviderConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONObject(json).optJSONArray("providers") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = ProviderType.entries.firstOrNull { it.name == o.optString("type") } ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                ProviderConfig(id, type, o.optString("name", type.label), o.optString("endpoint", type.defaultEndpoint),
                    o.optString("model", type.defaultModel), o.optBoolean("enabled", true))
            }.distinctBy { it.id }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }
}

sealed interface ExternalError {
    val message: String
    data class InvalidKey(override val message: String = "The provider rejected the API key.") : ExternalError
    data class Network(override val message: String) : ExternalError
    data class Server(val code: Int, override val message: String) : ExternalError
    data class BadResponse(override val message: String = "The provider's answer could not be understood.") : ExternalError
}

class ExternalAiException(val error: ExternalError) : Exception(error.message)

data class HttpReply(val code: Int, val body: String)

fun interface HttpTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpReply
}

/** Default transport. Never logs URLs with secrets, headers or bodies. */
object UrlConnectionTransport : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpReply {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpReply(code, text.take(200_000))
        } finally {
            conn.disconnect()
        }
    }
}

/** Builds requests and parses replies for each provider family. Pure apart from the injected transport. */
class ExternalAiClient(private val transport: HttpTransport = UrlConnectionTransport) {

    /** Sends one system+user prompt. Never throws; failures come back as [ExternalError]. */
    fun complete(config: ProviderConfig, apiKey: String, system: String, user: String, timeoutMs: Int = 30_000): Result<String> {
        if (apiKey.isBlank()) return Result.failure(ExternalAiException(ExternalError.InvalidKey("No API key is saved for this provider.")))
        val (url, headers, body) = buildRequest(config, apiKey, system, user)
        val reply = try {
            transport.post(url, headers, body, timeoutMs)
        } catch (e: IOException) {
            return Result.failure(ExternalAiException(ExternalError.Network("Could not reach the provider (${e.javaClass.simpleName}).")))
        } catch (e: RuntimeException) {
            return Result.failure(ExternalAiException(ExternalError.Network("The request could not be sent.")))
        }
        return when (reply.code) {
            in 200..299 -> parseReply(config.type, reply.body)?.let { Result.success(it) }
                ?: Result.failure(ExternalAiException(ExternalError.BadResponse()))
            401, 403 -> Result.failure(ExternalAiException(ExternalError.InvalidKey()))
            else -> Result.failure(ExternalAiException(ExternalError.Server(reply.code, "The provider returned HTTP ${reply.code}.")))
        }
    }

    /** Cheap end-to-end check used by "Test connection". */
    fun test(config: ProviderConfig, apiKey: String): Result<String> =
        complete(config, apiKey, "Reply with the single word OK.", "ping", timeoutMs = 15_000)

    data class Request(val url: String, val headers: Map<String, String>, val body: String)

    fun buildRequest(config: ProviderConfig, apiKey: String, system: String, user: String): Request {
        val base = config.endpoint.trim().trimEnd('/')
        return when (config.type) {
            ProviderType.GEMINI -> Request(
                "$base/models/${config.model.trim()}:generateContent",
                // The key goes in a header, not the URL, so it cannot leak through URL logging.
                mapOf("x-goog-api-key" to apiKey),
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                    .put("generationConfig", JSONObject().put("temperature", 0.2))
                    .toString(),
            )
            ProviderType.OPENAI_COMPATIBLE, ProviderType.OPENROUTER -> Request(
                "$base/chat/completions",
                mapOf("Authorization" to "Bearer $apiKey"),
                JSONObject()
                    .put("model", config.model.trim())
                    .put("temperature", 0.2)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)))
                    .toString(),
            )
        }
    }

    fun parseReply(type: ProviderType, body: String): String? = try {
        val root = JSONObject(body)
        val text = when (type) {
            ProviderType.GEMINI -> root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            else -> root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        }
        text?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: org.json.JSONException) {
        null
    }
}
