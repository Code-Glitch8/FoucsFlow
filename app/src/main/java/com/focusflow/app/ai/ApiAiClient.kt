package com.focusflow.app.ai

import android.net.Uri
import com.focusflow.app.data.settings.ApiProfile
import com.focusflow.app.data.settings.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTP client for Gemini and OpenAI-compatible chat-completion endpoints. */
class ApiAiClient {
    suspend fun generate(profile: ApiProfile, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint: String
                val body: JSONObject
                val connection: HttpURLConnection = when (profile.provider) {
                    ApiProvider.OPENAI_COMPATIBLE -> {
                        val base = profile.baseUrl.trim().trimEnd('/')
                        require(base.startsWith("https://")) { "Use an HTTPS API endpoint." }
                        require(base.isNotBlank()) { "Enter the API base URL." }
                        endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                        body = JSONObject()
                            .put("model", profile.model)
                            .put("temperature", 0.2)
                            .put("messages", JSONArray()
                                .put(JSONObject().put("role", "system").put("content", system))
                                .put(JSONObject().put("role", "user").put("content", user)))
                        URL(endpoint).openConnection() as HttpURLConnection
                    }
                    ApiProvider.GEMINI -> {
                        val base = profile.baseUrl.trim().trimEnd('/').ifBlank {
                            "https://generativelanguage.googleapis.com/v1beta/models"
                        }
                        require(base.startsWith("https://")) { "Use an HTTPS API endpoint." }
                        endpoint = "$base/${Uri.encode(profile.model)}:generateContent?key=${Uri.encode(profile.apiKey)}"
                        body = JSONObject()
                            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                            .put("generationConfig", JSONObject().put("temperature", 0.2))
                        URL(endpoint).openConnection() as HttpURLConnection
                    }
                }
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (profile.provider == ApiProvider.OPENAI_COMPATIBLE) {
                        setRequestProperty("Authorization", "Bearer ${profile.apiKey}")
                    }
                }
                try {
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val detail = runCatching {
                            val error = JSONObject(response).opt("error")
                            when (error) {
                                is JSONObject -> error.optString("message").take(220)
                                else -> error?.toString()?.take(220).orEmpty()
                            }
                        }.getOrDefault("")
                        throw IllegalStateException("API returned HTTP $code${if (detail.isNotBlank()) ": $detail" else ""}")
                    }
                    extractText(profile.provider, JSONObject(response))
                        .takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("The API response did not contain any text.")
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun extractText(provider: ApiProvider, root: JSONObject): String = when (provider) {
        ApiProvider.OPENAI_COMPATIBLE -> {
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            when (val content = message?.opt("content")) {
                is String -> content
                is JSONArray -> buildString {
                    for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
                }
                else -> ""
            }
        }
        ApiProvider.GEMINI -> {
            val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            buildString {
                if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
    }
}
