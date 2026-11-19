package com.focusflow.app.ai

import com.focusflow.app.data.db.SummarySource
import com.focusflow.app.data.db.TaskOrigin
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.ai.external.ExternalAiClient
import com.focusflow.app.ai.external.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class TaskExtraction(
    val tasks: List<ExtractedTask>,
    /** EXTERNAL_AI / ON_DEVICE_AI only when that step really produced the tasks; otherwise RULE_BASED. */
    val source: TaskOrigin,
    /** Why a fallback was used, or other things the user should know. */
    val note: String?,
    /** Display name of the external provider when [source] is EXTERNAL_AI. */
    val provider: String? = null,
)

data class SummaryResult(val text: String, val source: SummarySource, val note: String?, val provider: String? = null)

/**
 * Chooses between the on-device model and the rule-based fallback, and always says which one
 * produced a result. If anything about the model goes wrong the app keeps working.
 */
class AiService(
    private val models: ModelManager,
    private val llm: LocalLlm,
    private val settings: AppSettings,
    private val providers: ProviderRepository,
    private val client: ExternalAiClient = ExternalAiClient(),
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

    private fun extractPrompt(description: String, subject: String?, chapters: List<String>, suffix: String = ""): String =
        buildString {
            if (!subject.isNullOrBlank()) append("Subject: $subject\n")
            if (chapters.isNotEmpty()) append("Chapters: ${chapters.joinToString(", ")}\n")
            append("Student said: \"${description.trim()}\"")
            append(suffix)
        }

    private fun groundedTasks(reply: String, description: String, subject: String?, chapters: List<String>): StepResult<List<ExtractedTask>> {
        val parsed = AiTaskParser.parse(reply) ?: return StepResult(null, "The AI's answer could not be understood.")
        val (grounded, dropped) = AiTaskParser.keepGrounded(parsed, description, listOfNotNull(subject) + chapters)
        if (grounded.isEmpty()) return StepResult(null, "The AI returned nothing usable.")
        val note = if (dropped > 0) "$dropped suggested task${if (dropped == 1) " was" else "s were"} left out because it didn't match what you wrote." else null
        return StepResult(grounded, note)
    }

    suspend fun extractTasks(
        description: String,
        subject: String?,
        chapters: List<String>,
        allowExternal: Boolean = true,
    ): TaskExtraction {
        val provider = if (allowExternal) providers.ready() else null
        val external: (suspend () -> StepResult<List<ExtractedTask>>)? = provider?.let { p ->
            suspend {
                val r = withContext(Dispatchers.IO) {
                    client.complete(p.config, p.apiKey, extractSystem, extractPrompt(description, subject, chapters))
                }
                r.fold(
                    onSuccess = { reply -> groundedTasks(reply, description, subject, chapters).let { if (it.value == null) StepResult(null, "${p.config.name}: ${it.note} Used the next option instead.") else it } },
                    onFailure = { StepResult(null, "${p.config.name} could not be used (${it.message}). Used the next option instead.") },
                )
            }
        }
        val onDevice: suspend () -> StepResult<List<ExtractedTask>> = onDevice@{
            val (file, problem) = usableModel()
            if (file == null) {
                StepResult(null, problem)
            } else {
                val noThink = ModelCatalog.forFile(file.name)?.noThink == true || file.name.startsWith("Qwen3", true)
                val reply = llm.generate(file.absolutePath, extractSystem, extractPrompt(description, subject, chapters, if (noThink) " /no_think" else ""))
                    .getOrElse { return@onDevice StepResult(null, "The on-device model could not run (${it.message ?: it.javaClass.simpleName}); used simple rules instead.") }
                groundedTasks(reply, description, subject, chapters).let { if (it.value == null) StepResult(null, it.note + " Used simple rules instead.") else it }
            }
        }
        return AiChain.extract(external, provider?.config?.name, onDevice) { RuleBasedTaskExtractor.extract(description) }
    }

    suspend fun summarize(facts: SummaryFacts): SummaryResult {
        val template = TemplateSummary.session(facts)
        val provider = providers.ready()
        val external: (suspend () -> StepResult<String>)? = provider?.let { p ->
            suspend {
                val r = withContext(Dispatchers.IO) { client.complete(p.config, p.apiKey, summarySystem, "FACTS:\n$template") }
                r.fold(
                    onSuccess = { text ->
                        val clean = text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
                        if (isFaithful(clean, template)) StepResult(clean) else StepResult(null, "${p.config.name}'s summary didn't match your session data.")
                    },
                    onFailure = { StepResult(null, "${p.config.name} could not be used (${it.message}).") },
                )
            }
        }
        val onDevice: suspend () -> StepResult<String> = onDevice@{
            val (file, problem) = usableModel()
            if (file == null) {
                StepResult(null, problem)
            } else {
                val noThink = file.name.startsWith("Qwen3", true)
                val reply = llm.generate(file.absolutePath, summarySystem, "FACTS:\n$template" + if (noThink) "\n/no_think" else "")
                    .getOrElse { return@onDevice StepResult(null, "The on-device model could not run; used a plain summary.") }
                val text = reply.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
                if (isFaithful(text, template)) StepResult(text) else StepResult(null, "The model's summary didn't match your session data; used a plain summary.")
            }
        }
        return AiChain.summarize(template, external, provider?.config?.name, onDevice)
    }

    /** Rejects empty/over-long text, and any number that is not in the facts (a cheap check against invented figures). */
    private fun isFaithful(text: String, facts: String): Boolean {
        if (text.length !in 20..1200) return false
        val allowed = Regex("\\d+").findAll(facts).map { it.value }.toSet()
        return Regex("\\d+").findAll(text).all { it.value in allowed }
    }

    /** Short end-to-end check used by the "Test model" button. */
    suspend fun selfTest(): TaskExtraction =
        extractTasks("I want to revise quadratic equations, solve 20 questions and review the ones I got wrong", "Mathematics", emptyList(), allowExternal = false)
}
