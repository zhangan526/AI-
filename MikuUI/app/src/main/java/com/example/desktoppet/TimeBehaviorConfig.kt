package com.example.desktoppet

import java.time.LocalTime

/** Local-device time rules. Sleep has the highest priority. */
object TimeBehaviorConfig {
    val sleepStart: LocalTime = LocalTime.of(23, 0)
    val wakeTime: LocalTime = LocalTime.of(7, 0)
    val lunchTime: LocalTime = LocalTime.of(12, 0)
    const val CHECK_INTERVAL_MS = 60_000L

    fun isSleepTime(time: LocalTime): Boolean =
        !time.isBefore(sleepStart) || time.isBefore(wakeTime)
}
