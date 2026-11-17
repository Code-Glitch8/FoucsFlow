package com.focusflow.app.ai

/** A task proposed from a study description, by the on-device model or by the rule-based fallback. */
data class ExtractedTask(
    val title: String,
    val minutes: Int? = null,
    val subtasks: List<String> = emptyList(),
)

/**
 * Lightweight fallback used when no on-device model is available. It only rearranges the user's
 * own words: it splits a description into separate actions, tidies the wording, and never adds
 * anything the user did not say. Results are labelled "rule-based" in the UI, never as AI.
 */
object RuleBasedTaskExtractor {
    private val verbs = setOf(
        "revise", "revisit", "solve", "review", "read", "practice", "practise", "learn", "watch", "write",
        "complete", "do", "finish", "memorize", "memorise", "attempt", "summarize", "summarise", "prepare",
        "study", "go", "redo", "analyse", "analyze", "make", "take", "test", "check", "correct", "fix",
        "create", "draw", "derive", "prove", "understand", "recap", "rewrite", "highlight", "work", "try",
        "list", "note", "memorise", "finalize", "organise", "organize", "cover", "go over", "look",
    )

    private val leadingFiller = Regex(
        "^(?:(?:and|then|also|first|next|finally|today|tonight|now|so|okay|ok),?\\s+)*" +
            "(?:(?:i\\s+(?:really\\s+)?(?:want|need|plan|wish|hope|would like|have|am going|am planning|will|should|must|'ll)\\s+(?:to\\s+)?)" +
            "|(?:i'?d like to\\s+)|(?:i'?m going to\\s+)|(?:let'?s\\s+)|(?:please\\s+)|(?:to\\s+))?",
        RegexLevel,
    )
    private val sentenceSplit = Regex("(?<=[.!?])\\s+|[;\\n]+")
    private val connector = Regex("\\s*(?:,\\s*(?:and\\s+|then\\s+)?|\\s+and\\s+then\\s+|\\s+and\\s+|\\s+then\\s+|\\s+after that\\s+)", RegexLevel)
    private val bulletPrefix = Regex("^\\s*(?:[-*•]+|\\d+[.)])\\s+")
    private val trailingPunct = Regex("[\\s.!?,;:]+$")
    private val questionCount = Regex("(\\d+)\\s+(?:more\\s+|practice\\s+|extra\\s+)?(?:questions?|problems?|mcqs?|sums?)", RegexLevel)

    fun extract(description: String, limit: Int = 12): List<ExtractedTask> {
        val out = LinkedHashMap<String, ExtractedTask>()
        for (rawSentence in description.split(sentenceSplit)) {
            val sentence = rawSentence.replace(bulletPrefix, "").trim()
            if (sentence.isEmpty()) continue
            for (piece in splitAtActions(stripFiller(sentence))) {
                val title = tidy(piece) ?: continue
                out.putIfAbsent(title.lowercase(), ExtractedTask(title, estimateMinutes(title)))
                if (out.size >= limit) return out.values.toList()
            }
        }
        return out.values.toList()
    }

    private fun stripFiller(s: String): String {
        var cur = s.trim()
        repeat(3) { cur = cur.replaceFirst(leadingFiller, "").trim() }
        return cur
    }

    /** Splits at "and", "then" or commas, but only where the next word starts a new action. */
    private fun splitAtActions(clause: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        for (m in connector.findAll(clause)) {
            if (m.range.first < start) continue
            val rest = clause.substring(m.range.last + 1)
            val next = rest.trimStart().substringBefore(' ').lowercase().trimEnd(',', '.')
            if (next in verbs) {
                pieces += clause.substring(start, m.range.first)
                start = m.range.last + 1
            }
        }
        pieces += clause.substring(start)
        return pieces
    }

    private fun tidy(piece: String): String? {
        var t = stripFiller(piece).replace(trailingPunct, "").trim()
        t = t.replace(Regex("\\s+"), " ")
        if (t.length < 2) return null
        return t.replaceFirstChar { it.uppercase() }.take(120)
    }

    /** A rough "about 3 minutes a question" estimate, only when the user gave a number of questions. */
    private fun estimateMinutes(title: String): Int? {
        val n = questionCount.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val raw = n * 3
        return (((raw + 4) / 5) * 5).coerceIn(5, 240)
    }
}

private val RegexLevel = setOf(RegexOption.IGNORE_CASE)
