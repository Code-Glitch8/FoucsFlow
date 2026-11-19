package com.focusflow.app.ai

import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin

/** Result of trying one AI step. [tasks]/[text] is null when the step did not produce anything usable. */
data class StepResult<T>(val value: T?, val note: String? = null)

/**
 * The fallback order, kept free of Android types so it can be unit tested:
 *   1. the user's selected external provider (only if one is configured and ready),
 *   2. the downloaded on-device model,
 *   3. the built-in rules / plain template.
 * The returned source always names the step that really produced the output, so rule-based output
 * is never labelled as AI.
 */
object AiChain {
    suspend fun extract(
        external: (suspend () -> StepResult<List<ExtractedTask>>)?,
        providerName: String?,
        onDevice: suspend () -> StepResult<List<ExtractedTask>>,
        rules: () -> List<ExtractedTask>,
    ): TaskExtraction {
        val notes = mutableListOf<String>()
        if (external != null) {
            val e = external()
            if (e.value != null) return TaskExtraction(e.value, TaskOrigin.EXTERNAL_AI, e.note, providerName)
            e.note?.let { notes += it }
        }
        val d = onDevice()
        if (d.value != null) {
            return TaskExtraction(d.value, TaskOrigin.ON_DEVICE_AI, (notes + listOfNotNull(d.note)).joinToString(" ").ifBlank { null })
        }
        d.note?.let { notes += it }
        return TaskExtraction(rules(), TaskOrigin.RULE_BASED, notes.joinToString(" ").ifBlank { null })
    }

    suspend fun summarize(
        template: String,
        external: (suspend () -> StepResult<String>)?,
        providerName: String?,
        onDevice: suspend () -> StepResult<String>,
    ): SummaryResult {
        val notes = mutableListOf<String>()
        if (external != null) {
            val e = external()
            if (e.value != null) return SummaryResult(e.value, SummarySource.EXTERNAL_AI, e.note, providerName)
            e.note?.let { notes += it }
        }
        val d = onDevice()
        if (d.value != null) {
            return SummaryResult(d.value, SummarySource.ON_DEVICE_AI, (notes + listOfNotNull(d.note)).joinToString(" ").ifBlank { null })
        }
        d.note?.let { notes += it }
        return SummaryResult(template, SummarySource.TEMPLATE, notes.joinToString(" ").ifBlank { null })
    }
}
