package com.focusflow.app.ai

import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.settings.AppSettings
import kotlinx.coroutines.flow.first

data class TaskExtraction(
    val tasks: List<ExtractedTask>,
    /** ON_DEVICE_AI only when the local model really produced the tasks; otherwise RULE_BASED. */
    val source: TaskOrigin,
    /** Why the fallback was used, or other things the user should know. */
    val note: String?,
)

data class SummaryResult(val text: String, val source: SummarySource, val note: String?)

/**
 * Chooses between the on-device model and the rule-based fallback, and always says which one
 * produced a result. If anything about the model goes wrong the app keeps working.
 */
class AiService(
    private val models: ModelManager,
    private val llm: LocalLlm,
    private val settings: AppSettings,
) {
    private val extractSystem =
        "You turn a student's study description into a short, ordered checklist of tasks.\n" +
            "Rules:\n" +
            "1. Use only what the student said. Never add topics, chapters or activities they did not mention.\n" +
            "2. Split large activities into smaller concrete tasks only when their words imply it.\n" +
            "3. Each title is a short imperative phrase of at most 12 words.\n" +
            "4. \"minutes\" is an optional rough estimate (integer); leave it out if unsure.\n" +
            "5. \"subtasks\" is optional (at most 4) and only for tasks with clear steps.\n" +
            "6. Never say anything is done.\n" +
            "Reply with ONLY JSON and no other text, in this form:\n" +
            "{\"tasks\":[{\"title\":\"...\",\"minutes\":15,\"subtasks\":[\"...\"]}]}"

    private val summarySystem =
        "You write a brief, friendly study summary of 3 to 5 sentences from the FACTS you are given. " +
            "Use only the facts. Do not add numbers, topics or achievements that are not in the facts. " +
            "Do not say a topic is mastered. End with one suggested next step."

    private suspend fun usableModel(): Pair<java.io.File?, String?> {
        if (!settings.aiEnabled.first()) return null to "Offline AI is turned off in Settings."
        val file = models.activeModelFile()
            ?: return null to "No offline AI model is installed yet (Settings → Offline AI & voice)."
        models.memoryProblem(file)?.let { return null to it }
        return file to null
    }

    suspend fun extractTasks(
        description: String,
        subject: String?,
        chapters: List<String>,
    ): TaskExtraction {
        val fallback = { why: String? ->
            TaskExtraction(RuleBasedTaskExtractor.extract(description), TaskOrigin.RULE_BASED, why)
        }
        val (file, problem) = usableModel()
        if (file == null) return fallback(problem)

        val noThink = ModelCatalog.forFile(file.name)?.noThink == true || file.name.startsWith("Qwen3", true)
        val prompt = buildString {
            if (!subject.isNullOrBlank()) append("Subject: $subject\n")
            if (chapters.isNotEmpty()) append("Chapters: ${chapters.joinToString(", ")}\n")
            append("Student said: \"${description.trim()}\"")
            if (noThink) append(" /no_think")
        }
        val reply = llm.generate(file.absolutePath, extractSystem, prompt)
            .getOrElse { return fallback("The on-device model could not run (${it.message ?: it.javaClass.simpleName}); used simple rules instead.") }

        val parsed = AiTaskParser.parse(reply)
            ?: return fallback("The model's answer could not be understood; used simple rules instead.")
        val (grounded, dropped) = AiTaskParser.keepGrounded(parsed, description, listOfNotNull(subject) + chapters)
        if (grounded.isEmpty()) return fallback("The model returned nothing usable; used simple rules instead.")
        val note = if (dropped > 0) "$dropped suggested task${if (dropped == 1) " was" else "s were"} left out because it didn't match what you wrote." else null
        return TaskExtraction(grounded, TaskOrigin.ON_DEVICE_AI, note)
    }

    suspend fun summarize(facts: SummaryFacts): SummaryResult {
        val template = TemplateSummary.session(facts)
        val (file, problem) = usableModel()
        if (file == null) return SummaryResult(template, SummarySource.TEMPLATE, problem)
        val noThink = file.name.startsWith("Qwen3", true)
        val reply = llm.generate(
            file.absolutePath, summarySystem,
            "FACTS:\n$template" + if (noThink) "\n/no_think" else "",
        ).getOrElse {
            return SummaryResult(template, SummarySource.TEMPLATE, "The on-device model could not run; used a plain summary.")
        }
        val text = reply.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
        return if (isFaithful(text, template)) {
            SummaryResult(text, SummarySource.ON_DEVICE_AI, null)
        } else {
            SummaryResult(template, SummarySource.TEMPLATE, "The model's summary didn't match your session data; used a plain summary.")
        }
    }

    /** Rejects empty/over-long text, and any number that is not in the facts (a cheap check against invented figures). */
    private fun isFaithful(text: String, facts: String): Boolean {
        if (text.length !in 20..1200) return false
        val allowed = Regex("\\d+").findAll(facts).map { it.value }.toSet()
        return Regex("\\d+").findAll(text).all { it.value in allowed }
    }

    /** Short end-to-end check used by the "Test model" button. */
    suspend fun selfTest(): TaskExtraction =
        extractTasks("I want to revise quadratic equations, solve 20 questions and review the ones I got wrong", "Mathematics", emptyList())
}
