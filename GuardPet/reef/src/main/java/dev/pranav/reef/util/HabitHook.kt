package dev.pranav.reef.util

import android.content.Context

data class HabitEval(
    val shouldBlock: Boolean,
    val sleepLock: Boolean
) {
    companion object {
        val NONE = HabitEval(shouldBlock = false, sleepLock = false)
        val BLOCK = HabitEval(shouldBlock = true, sleepLock = false)
        val SLEEP_LOCK = HabitEval(shouldBlock = true, sleepLock = true)
    }
}

/**
 * App module registers [evaluator] so Reef's blocker can apply night-habit rules
 * without depending on GuardPet classes.
 */
object HabitHook {
    @Volatile
    var evaluator: ((Context, String) -> HabitEval)? = null

    fun evaluate(context: Context, packageName: String): HabitEval {
        return evaluator?.invoke(context, packageName) ?: HabitEval.NONE
    }
}
