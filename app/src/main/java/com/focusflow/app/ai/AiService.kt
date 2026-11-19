package com.focusflow.app.ai

import com.focusflow.app.ai.external.ExternalAiClient
import com.focusflow.app.ai.external.ProviderRepository
import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TaskExtraction(
    val tasks: List<ExtractedTask>,
    val source: TaskOrigin,
    val note: String?,
    val provider: String? = null,
)

data class SummaryResult(
    val text: String,
    val source: SummarySource,
    val note: String?,
    val provider: String? = null,
)

/** API-only AI. It never falls back to a downloaded model, local rules, or a template summary. */
class AiService(
    private val providers: ProviderRepository,
    private val client: ExternalAiClient = ExternalAiClient(),
) {
    private val extractSystem =
        "Turn a student's study description into a short ordered checklist. Use only the topics and work " +
            "the student mentioned. Never invent chapters, activities or achievements. Split larger activities " +
            "into smaller concrete tasks only when implied. Each title must be an imperative phrase of at most " +
            "12 words. minutes is an optional integer estimate; subtasks are optional. Never mark anything done. " +
            "Reply only with JSON: {\\"tasks\\":[{\\"title\\":\\"...\\",\\"minutes\\":15,\\"subtasks\\":[\\"...\\"]}]}"

    private val summarySystem =
        "Write a brief, friendly study-session summary of 3 to 5 sentences using only the supplied facts. " +
            "Do not invent numbers, topics or achievements, and do not claim that time spent means mastery. " +
            "End with one practical next step."

    suspend fun extractTasks(
        description: String,
        subject: String?,
        chapters: List<String>,
        allowExternal: Boolean = true,
    ): TaskExtraction {
        if (!allowExternal) {
            return TaskExtraction(emptyList(), TaskOrigin.MANUAL, "API task generation is disabled for this request.")
        }
        val provider = providers.ready()
            ?: return TaskExtraction(
                emptyList(),
                TaskOrigin.MANUAL,
                "Add and select an API provider in Settings → AI providers. You can still add tasks manually.",
            )
        val prompt = buildString {
            if (!subject.isNullOrBlank()) append("Subject: $subject\\n")
            if (chapters.isNotEmpty()) append("Chapters: ${chapters.joinToString(", ")}\\n")
            append("Student's plan:\\n${description.trim()}")
        }
        val result = withContext(Dispatchers.IO) {
            client.complete(provider.config, provider.apiKey, extractSystem, prompt)
        }
        return result.fold(
            onSuccess = { reply ->
                val parsed = AiTaskParser.parse(reply)
                    ?: return@fold TaskExtraction(emptyList(), TaskOrigin.MANUAL, "${provider.config.name} returned an unreadable response. Try again or add tasks manually.", provider.config.name)
                val (grounded, dropped) = AiTaskParser.keepGrounded(parsed, description, listOfNotNull(subject) + chapters)
                if (grounded.isEmpty()) {
                    TaskExtraction(emptyList(), TaskOrigin.MANUAL, "The API returned no tasks that matched your plan. Add tasks manually or try rephrasing it.", provider.config.name)
                } else {
                    val note = if (dropped > 0) "$dropped suggestion(s) were removed because they did not match your plan." else null
                    TaskExtraction(grounded, TaskOrigin.EXTERNAL_AI, note, provider.config.name)
                }
            },
            onFailure = {
                TaskExtraction(emptyList(), TaskOrigin.MANUAL, "API request failed: ${it.message ?: "unknown error"}. Check your connection and API settings.", provider.config.name)
            },
        )
    }

    suspend fun summarize(facts: SummaryFacts): SummaryResult {
        val provider = providers.ready()
            ?: return SummaryResult("", SummarySource.EXTERNAL_AI, "Add and select an API provider to generate a session summary.")
        val factText = buildString {
            append("Subject: ${facts.subject}\\n")
            append("Chapters: ${facts.chapters.joinToString(", ").ifBlank { "None selected" }}\\n")
            append("Plan: ${facts.planned.ifBlank { "No plan text supplied" }}\\n")
            append("Session status: ${facts.status}\\n")
            append("Focused minutes: ${facts.focusMinutes}\\n")
            append("Break minutes: ${facts.breakMinutes}\\n")
            append("Completed tasks: ${facts.completedTasks.joinToString("; ").ifBlank { "None" }}\\n")
            append("Unfinished tasks: ${facts.unfinishedTasks.joinToString("; ").ifBlank { "None" }}")
        }
        val result = withContext(Dispatchers.IO) {
            client.complete(provider.config, provider.apiKey, summarySystem, factText)
        }
        return result.fold(
            onSuccess = { raw ->
                val clean = raw.replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "").trim()
                if (isFaithful(clean, factText)) {
                    SummaryResult(clean, SummarySource.EXTERNAL_AI, null, provider.config.name)
                } else {
                    SummaryResult("", SummarySource.EXTERNAL_AI, "The API response could not be matched to the session facts. Regenerate it or check another provider.", provider.config.name)
                }
            },
            onFailure = {
                SummaryResult("", SummarySource.EXTERNAL_AI, "API summary request failed: ${it.message ?: "unknown error"}. Check your connection and API settings.", provider.config.name)
            },
        )
    }

    private fun isFaithful(text: String, facts: String): Boolean {
        if (text.length !in 20..1800) return false
        val allowedNumbers = Regex("\\\\d+").findAll(facts).map { it.value }.toSet()
        return Regex("\\\\d+").findAll(text).all { it.value in allowedNumbers }
    }

    suspend fun selfTest(): TaskExtraction = extractTasks(
        "Revise quadratic equations, solve 20 questions and review the ones I got wrong",
        "Mathematics",
        emptyList(),
        allowExternal = true,
    )
}
