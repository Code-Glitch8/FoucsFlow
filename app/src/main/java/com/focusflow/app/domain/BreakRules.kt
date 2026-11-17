package com.focusflow.app.domain

/** User-configurable rules for earning breaks by completing tasks. */
data class BreakConfig(
    val enabled: Boolean = true,
    val tasksForShortBreak: Int = 1,
    val shortBreakMinutes: Int = 5,
    val tasksForLongBreak: Int = 3,
    val longBreakMinutes: Int = 15,
)

data class BreakCredits(val shortAvailable: Int, val longAvailable: Int) {
    val any: Boolean get() = shortAvailable > 0 || longAvailable > 0
}

object BreakRules {
    /**
     * Break credits are earned only through completed tasks.
     *
     * Every [BreakConfig.tasksForShortBreak]-th completed task earns a short break, and every
     * [BreakConfig.tasksForLongBreak]-th earns a long break. When one completion qualifies for
     * both, only the long break is granted (it replaces the short one).
     *
     * Returns (shortEarned, longEarned) for the given number of completed tasks.
     */
    fun earned(completedTasks: Int, config: BreakConfig): Pair<Int, Int> {
        if (!config.enabled || completedTasks <= 0) return 0 to 0
        val s = config.tasksForShortBreak.coerceAtLeast(1)
        val l = config.tasksForLongBreak.coerceAtLeast(1)
        var short = 0
        var long = 0
        for (k in 1..completedTasks) {
            when {
                k % l == 0 -> long++
                k % s == 0 -> short++
            }
        }
        return short to long
    }

    fun available(
        completedTasks: Int,
        shortUsed: Int,
        longUsed: Int,
        config: BreakConfig,
    ): BreakCredits {
        val (short, long) = earned(completedTasks, config)
        return BreakCredits(
            shortAvailable = (short - shortUsed).coerceAtLeast(0),
            longAvailable = (long - longUsed).coerceAtLeast(0),
        )
    }
}
