package com.focusflow.app.ai

import com.focusflow.app.data.db.SessionStatus
import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.settings.ApiConfigStore

data class ExtractedTask(
    val title: String,
    val minutes: Int? = null,
    val subtasks: List<String> = emptyList(),
)

data class TaskExtraction(
    val tasks: List<ExtractedTask>,
    val source: TaskOrigin,
    val note: String?,
)

data class SummaryFacts(
    val subject: String,
    val chapters: List<String>,
    val planned: String,
    val status: SessionStatus,
    val focusMinutes: Long,
    val breakMinutes: Long,
    val completedTasks: List<String>,
    val unfinishedTasks: List<String>,
)

data class SummaryResult(val text: String, val source: SummarySource, val note: String?)

class AiService(
    private val configs: ApiConfigStore,
    private val client: ApiAiClient,
) {
    private val extractSystem =
        "Turn a student's study description into a short ordered checklist. Use only the topics and work the student mentioned. " +
            "Never invent chapters, activities or achievements. Split larger activities into smaller concrete tasks only when implied. " +
            "Each title must be a concise imperative phrase of at most 12 words. minutes is an optional integer estimate; subtasks are optional. " +
            "Never mark anything done. Reply only with JSON: {\"tasks\":[{\"title\":\"...\",\"minutes\":15,\"subtasks\":[\"...\"]}]}"

    private val summarySystem =
        "Write a brief, friendly study-session summary of 3 to 5 sentences using only the supplied facts. " +
            "Do not invent numbers, topics or achievements, and do not claim that time spent means mastery. End with one practical next step."

    suspend fun extractTasks(description: String, subject: String?, chapters: List<String>): TaskExtraction {
        val profile = configs.activeProfile()
            ?: return TaskExtraction(emptyList(), TaskOrigin.API_AI, "Add an API profile in Settings → AI API & voice to generate tasks.")
        val prompt = buildString {
            if (!subject.isNullOrBlank()) append("Subject: $subject\n")
            if (chapters.isNotEmpty()) append("Chapters: ${chapters.joinToString(", ")}\n")
            append("Student's plan:\n${description.trim()}")
        }
        val reply = client.generate(profile, extractSystem, prompt).getOrElse {
            return TaskExtraction(emptyList(), TaskOrigin.API_AI, "AI API request failed: ${it.message ?: "unknown error"}")
        }
        val parsed = AiTaskParser.parse(reply)
            ?: return TaskExtraction(emptyList(), TaskOrigin.API_AI, "The API response was not valid task JSON. Try again or choose another model.")
        val (grounded, dropped) = AiTaskParser.keepGrounded(parsed, description, listOfNotNull(subject) + chapters)
        if (grounded.isEmpty()) {
            return TaskExtraction(emptyList(), TaskOrigin.API_AI, "The AI returned no usable tasks matching your description. You can add tasks manually.")
        }
        val note = if (dropped > 0) "$dropped suggestion(s) were removed because they did not match your description." else null
        return TaskExtraction(grounded, TaskOrigin.API_AI, note)
    }

    suspend fun summarize(facts: SummaryFacts): SummaryResult {
        val profile = configs.activeProfile()
            ?: return SummaryResult("", SummarySource.API_AI, "Add an API profile in Settings → AI API & voice to generate a summary.")
        val factText = buildString {
            append("Subject: ${facts.subject}\n")
            append("Chapters: ${facts.chapters.joinToString(", ").ifBlank { "None selected" }}\n")
            append("Plan: ${facts.planned.ifBlank { "No plan text supplied" }}\n")
            append("Session status: ${facts.status}\n")
            append("Focused minutes: ${facts.focusMinutes}\n")
            append("Break minutes: ${facts.breakMinutes}\n")
            append("Completed tasks: ${facts.completedTasks.joinToString("; ").ifBlank { "None" }}\n")
            append("Unfinished tasks: ${facts.unfinishedTasks.joinToString("; ").ifBlank { "None" }}")
        }
        val reply = client.generate(profile, summarySystem, factText).getOrElse {
            return SummaryResult("", SummarySource.API_AI, "AI summary request failed: ${it.message ?: "unknown error"}")
        }.trim()
        return if (isFaithful(reply, factText)) {
            SummaryResult(reply, SummarySource.API_AI, null)
        } else {
            SummaryResult("", SummarySource.API_AI, "The AI response could not be safely matched to the session facts. Try again.")
        }
    }

    private fun isFaithful(text: String, facts: String): Boolean {
        if (text.length !in 20..1800) return false
        val allowedNumbers = Regex("\\d+").findAll(facts).map { it.value }.toSet()
        return Regex("\\d+").findAll(text).all { it.value in allowedNumbers }
    }

    suspend fun selfTest(): TaskExtraction = extractTasks(
        "Revise quadratic equations, solve 20 questions and review the ones I got wrong",
        "Mathematics",
        emptyList(),
    )
}
