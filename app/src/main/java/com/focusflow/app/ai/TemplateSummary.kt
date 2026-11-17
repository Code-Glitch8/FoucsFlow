package com.focusflow.app.ai

import com.focusflow.app.data.db.SessionStatus

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

/**
 * Plain, deterministic session summary. Used when no on-device model is available, and as the
 * factual basis the AI narrative is checked against. It reports time and task counts only; it
 * never claims that time spent means a chapter is mastered.
 */
object TemplateSummary {
    fun session(f: SummaryFacts): String = buildString {
        val where = if (f.chapters.isEmpty()) f.subject else "${f.subject} (${f.chapters.joinToString(", ")})"
        append(
            when (f.status) {
                SessionStatus.COMPLETED -> "You finished a session on $where"
                SessionStatus.ENDED_EARLY -> "You ended a session on $where early"
                SessionStatus.EMERGENCY_EXIT -> "You left a strict session on $where with the emergency exit"
                SessionStatus.ACTIVE -> "Session in progress on $where"
            },
        )
        append(" with ${f.focusMinutes} min of focused time")
        if (f.breakMinutes > 0) append(" and ${f.breakMinutes} min of breaks")
        append(".\n")

        if (f.planned.isNotBlank()) append("You planned: \"${f.planned.trim()}\"\n")

        val total = f.completedTasks.size + f.unfinishedTasks.size
        if (total == 0) {
            append("No tasks were planned, so there is nothing to compare against.\n")
        } else {
            append("Completed ${f.completedTasks.size} of $total tasks.\n")
            if (f.completedTasks.isNotEmpty()) {
                append("Done:\n"); f.completedTasks.forEach { append("• $it\n") }
            }
            if (f.unfinishedTasks.isNotEmpty()) {
                append("Still to do:\n"); f.unfinishedTasks.forEach { append("• $it\n") }
            }
        }
        append("Next: ")
        append(
            when {
                f.unfinishedTasks.isNotEmpty() ->
                    "pick up \"${f.unfinishedTasks.first()}\" first. Unfinished tasks are saved on your Tasks list and can be added to your next session."
                total == 0 -> "list the tasks you want to finish next time so you can see what you accomplish, not just how long you studied."
                else -> "everything planned is done. Choose the next chapter you want to cover."
            },
        )
    }

    fun unfinished(tasks: List<String>): String = when {
        tasks.isEmpty() -> "You have no unfinished tasks."
        else -> "You have ${tasks.size} unfinished task${if (tasks.size == 1) "" else "s"}:\n" +
            tasks.take(10).joinToString("\n") { "• $it" } +
            if (tasks.size > 10) "\n…and ${tasks.size - 10} more." else ""
    }
}
