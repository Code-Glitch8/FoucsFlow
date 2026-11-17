package com.focusflow.app.ai

import org.json.JSONArray
import org.json.JSONObject

/** Turns the model's reply into tasks, and rejects replies or tasks it cannot trust. */
object AiTaskParser {
    private val thinkBlock = Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val wordRegex = Regex("[a-z0-9]+")

    /** Words that make sense in a task even if the student did not literally say them. */
    private val genericStudyWords = setOf(
        "answers", "answer", "questions", "question", "theory", "practice", "notes", "note", "mistakes", "mistake",
        "errors", "error", "incorrect", "wrong", "problems", "problem", "exercises", "exercise", "summary",
        "revision", "formulas", "formula", "concepts", "concept", "test", "quiz", "flashcards", "mock", "paper",
        "papers", "homework", "chapter", "topic", "topics", "examples", "example", "solutions", "solution",
        "weak", "areas", "key", "points", "definitions", "past", "previous", "year", "review", "revise",
    )
    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "your", "about", "all", "any", "some",
        "my", "our", "its", "them", "then", "than", "are", "was", "were", "got", "get", "have", "has", "had",
        "you", "his", "her", "their", "out", "off", "over", "one", "two",
    )
    private val actionVerbs = setOf(
        "revise", "solve", "review", "read", "practice", "practise", "learn", "watch", "write", "complete", "do",
        "finish", "memorize", "memorise", "attempt", "summarize", "summarise", "prepare", "study", "redo",
        "analyse", "analyze", "make", "take", "test", "check", "correct", "fix", "create", "draw", "derive",
        "prove", "understand", "recap", "rewrite", "highlight", "work", "try", "list", "note", "go", "cover",
    )

    /** Returns null if the reply is not usable JSON; an empty list if it parsed but held no tasks. */
    fun parse(raw: String, maxTasks: Int = 12): List<ExtractedTask>? {
        val text = raw.replace(thinkBlock, "").trim()
        val array: JSONArray = try {
            val objStart = text.indexOf('{')
            val arrStart = text.indexOf('[')
            if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
                JSONObject(text.substring(objStart, text.lastIndexOf('}') + 1)).optJSONArray("tasks")
            } else if (arrStart >= 0) {
                JSONArray(text.substring(arrStart, text.lastIndexOf(']') + 1))
            } else null
        } catch (e: Exception) {
            null
        } ?: return null

        val out = LinkedHashMap<String, ExtractedTask>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            val task = when (item) {
                is String -> clean(item, null, emptyList())
                is JSONObject -> clean(
                    item.optString("title", ""),
                    item.opt("minutes")?.let { (it as? Number)?.toInt() ?: it.toString().toIntOrNull() },
                    item.optJSONArray("subtasks")?.let { a -> (0 until a.length()).map { a.optString(it, "") } } ?: emptyList(),
                )
                else -> null
            } ?: continue
            out.putIfAbsent(task.title.lowercase(), task)
            if (out.size >= maxTasks) break
        }
        return out.values.toList()
    }

    private fun clean(title: String, minutes: Int?, subtasks: List<String>): ExtractedTask? {
        val t = title.trim().replace(Regex("\\s+"), " ").trimEnd('.', ',', ';').take(120)
        if (t.length < 2) return null
        return ExtractedTask(
            title = t.replaceFirstChar { it.uppercase() },
            minutes = minutes?.takeIf { it in 1..240 },
            subtasks = subtasks.map { it.trim().take(100) }.filter { it.length >= 2 }.take(6),
        )
    }

    /**
     * Guards against invented work: a task is kept only if its subject words appear in what the
     * student said (or their chosen subject/chapters), or are generic study words.
     * Returns the kept tasks and how many were dropped.
     */
    fun keepGrounded(
        tasks: List<ExtractedTask>,
        description: String,
        contextWords: Collection<String>,
    ): Pair<List<ExtractedTask>, Int> {
        val source = tokens(description + " " + contextWords.joinToString(" ")).toSet() + genericStudyWords.map(::stem)
        val kept = tasks.filter { task ->
            val titleTokens = tokens(task.title)
            val content = titleTokens.filterNot { it in actionVerbs.map(::stem) }
            val toCheck = if (content.isNotEmpty()) content else titleTokens
            toCheck.isEmpty() || toCheck.any { it in source }
        }
        return kept to (tasks.size - kept.size)
    }

    internal fun tokens(text: String): List<String> =
        wordRegex.findAll(text.lowercase()).map { it.value }
            .filter { it.length >= 3 && it !in stopWords }
            .map(::stem).toList()

    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }
}
