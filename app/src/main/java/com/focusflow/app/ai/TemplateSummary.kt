package com.focusflow.app.ai

import com.focusflow.app.data.db.SessionStatus

/** Facts supplied to the configured API provider when it writes a session summary. */
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
