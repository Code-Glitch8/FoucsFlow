package com.focusflow.app.ai

/** Parsed task suggested by the configured external AI provider. */
data class ExtractedTask(
    val title: String,
    val minutes: Int? = null,
    val subtasks: List<String> = emptyList(),
)
