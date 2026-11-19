package com.focusflow.app.ai

import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChainTest {
    private fun task(t: String) = ExtractedTask(t, null, emptyList())
    private val rules = { listOf(task("rule task")) }

    @Test fun `external provider wins when it works`() = runBlocking {
        var onDeviceCalled = false
        val r = AiChain.extract(
            external = { StepResult(listOf(task("ext"))) }, providerName = "OpenRouter",
            onDevice = { onDeviceCalled = true; StepResult(listOf(task("dev"))) }, rules = rules,
        )
        assertEquals(TaskOrigin.EXTERNAL_AI, r.source)
        assertEquals("OpenRouter", r.provider)
        assertEquals("ext", r.tasks.single().title)
        assertEquals(false, onDeviceCalled)
    }

    @Test fun `failed external falls back to on-device and keeps the reason`() = runBlocking {
        val r = AiChain.extract(
            external = { StepResult(null, "Provider rejected the key.") }, providerName = "X",
            onDevice = { StepResult(listOf(task("dev"))) }, rules = rules,
        )
        assertEquals(TaskOrigin.ON_DEVICE_AI, r.source)
        assertTrue(r.note!!.contains("rejected"))
    }

    @Test fun `no provider configured goes straight to on-device`() = runBlocking {
        val r = AiChain.extract(null, null, { StepResult(listOf(task("dev"))) }, rules)
        assertEquals(TaskOrigin.ON_DEVICE_AI, r.source)
        assertNull(r.provider)
    }

    @Test fun `when nothing else works rule output is labelled rule based never AI`() = runBlocking {
        val r = AiChain.extract(
            external = { StepResult(null, "Network down.") }, providerName = "X",
            onDevice = { StepResult(null, "No model installed.") }, rules = rules,
        )
        assertEquals(TaskOrigin.RULE_BASED, r.source)
        assertEquals("rule task", r.tasks.single().title)
        assertTrue(r.note!!.contains("Network down") && r.note!!.contains("No model"))
    }

    @Test fun `summary follows the same order and falls back to the plain template`() = runBlocking {
        val ext = AiChain.summarize("TEMPLATE", { StepResult("from provider") }, "P", { StepResult("local") })
        assertEquals(SummarySource.EXTERNAL_AI, ext.source)
        val dev = AiChain.summarize("TEMPLATE", { StepResult(null, "bad") }, "P", { StepResult("local") })
        assertEquals(SummarySource.ON_DEVICE_AI, dev.source)
        val plain = AiChain.summarize("TEMPLATE", null, null, { StepResult(null, "no model") })
        assertEquals(SummarySource.TEMPLATE, plain.source)
        assertEquals("TEMPLATE", plain.text)
    }
}
