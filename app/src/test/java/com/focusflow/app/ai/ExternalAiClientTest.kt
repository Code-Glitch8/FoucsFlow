package com.focusflow.app.ai

import com.focusflow.app.ai.external.ExternalAiClient
import com.focusflow.app.ai.external.ExternalAiException
import com.focusflow.app.ai.external.ExternalError
import com.focusflow.app.ai.external.HttpReply
import com.focusflow.app.ai.external.HttpTransport
import com.focusflow.app.ai.external.ProviderConfig
import com.focusflow.app.ai.external.ProviderJson
import com.focusflow.app.ai.external.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ExternalAiClientTest {
    private val openai = ProviderConfig(id = "1", type = ProviderType.OPENAI_COMPATIBLE, name = "OA", endpoint = "https://api.example.com/v1/", model = "m")
    private val gemini = ProviderConfig(id = "2", type = ProviderType.GEMINI, name = "G", endpoint = "https://g.example.com/v1beta", model = "gem")

    private fun client(reply: () -> HttpReply) = ExternalAiClient(HttpTransport { _, _, _, _ -> reply() })
    private fun error(r: Result<String>) = (r.exceptionOrNull() as ExternalAiException).error

    @Test fun `openai compatible success parses the first choice`() {
        val body = """{"choices":[{"message":{"content":"  hello "}}]}"""
        assertEquals("hello", client { HttpReply(200, body) }.complete(openai, "k", "s", "u").getOrThrow())
    }

    @Test fun `gemini success parses candidates`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}"""
        assertEquals("hi", client { HttpReply(200, body) }.complete(gemini, "k", "s", "u").getOrThrow())
    }

    @Test fun `invalid key is reported for 401 and 403`() {
        assertTrue(error(client { HttpReply(401, "{}") }.complete(openai, "bad", "s", "u")) is ExternalError.InvalidKey)
        assertTrue(error(client { HttpReply(403, "{}") }.complete(gemini, "bad", "s", "u")) is ExternalError.InvalidKey)
    }

    @Test fun `blank key fails without any network call`() {
        var called = false
        val c = ExternalAiClient(HttpTransport { _, _, _, _ -> called = true; HttpReply(200, "") })
        assertTrue(error(c.complete(openai, "  ", "s", "u")) is ExternalError.InvalidKey)
        assertFalse(called)
    }

    @Test fun `network failure becomes a Network error and never throws`() {
        val c = ExternalAiClient(HttpTransport { _, _, _, _ -> throw IOException("boom") })
        assertTrue(error(c.complete(openai, "k", "s", "u")) is ExternalError.Network)
    }

    @Test fun `server errors and garbage bodies are distinguished`() {
        val e = error(client { HttpReply(500, "oops") }.complete(openai, "k", "s", "u"))
        assertEquals(500, (e as ExternalError.Server).code)
        assertTrue(error(client { HttpReply(200, "not json") }.complete(openai, "k", "s", "u")) is ExternalError.BadResponse)
        assertTrue(error(client { HttpReply(200, """{"choices":[]}""") }.complete(openai, "k", "s", "u")) is ExternalError.BadResponse)
    }

    @Test fun `api key goes in a header and never in the url or body`() {
        val c = ExternalAiClient()
        val g = c.buildRequest(gemini, "SECRET", "s", "u")
        assertFalse(g.url.contains("SECRET"))
        assertFalse(g.body.contains("SECRET"))
        assertEquals("SECRET", g.headers["x-goog-api-key"])
        val o = c.buildRequest(openai, "SECRET", "s", "u")
        assertEquals("https://api.example.com/v1/chat/completions".replace("v1/chat", "v1/chat"), o.url)
        assertEquals("Bearer SECRET", o.headers["Authorization"])
        assertFalse(o.body.contains("SECRET"))
    }

    @Test fun `provider json round trips and tolerates garbage`() {
        val list = listOf(openai, gemini.copy(enabled = false))
        assertEquals(list, ProviderJson.fromJson(ProviderJson.toJson(list)))
        assertTrue(ProviderJson.fromJson("{not json").isEmpty())
        assertTrue(ProviderJson.fromJson(null).isEmpty())
        assertFalse(ProviderJson.toJson(list).contains("apiKey"))
    }
}
