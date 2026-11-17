package com.focusflow.app.domain

object TaskNormalizer {
    private val whitespace = Regex("\\s+")
    private val trailingPunctuation = Regex("[\\s.!?;:,]+$")

    /** Cleans user-visible text: trims and collapses internal whitespace. */
    fun clean(text: String): String = text.trim().replace(whitespace, " ")

    /**
     * Key used to detect "the same task": case-insensitive, whitespace- and trailing-
     * punctuation-insensitive, scoped to a chapter so the same words in two chapters
     * stay two separate tasks.
     */
    fun dedupeKey(title: String, chapterId: Long?): String {
        val norm = clean(title).lowercase().replace(trailingPunctuation, "")
        return "${chapterId ?: 0}|$norm"
    }
}
