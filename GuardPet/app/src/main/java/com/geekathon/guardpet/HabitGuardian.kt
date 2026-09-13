package com.geekathon.guardpet

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import dev.pranav.reef.util.HabitEval
import dev.pranav.reef.util.ScreenUsageHelper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class AppCategory {
    VIDEO, GAME, SOCIAL, BROWSER, TOOL, SYSTEM, OTHER
}

data class HabitContext(
    val now: LocalTime,
    val isNightWindow: Boolean,
    val isPastBedtime: Boolean,
    val foregroundPackage: String,
    val category: AppCategory,
    val todaySchedules: List<FlashNote>,
    val habitualPackages: Set<String>
)

enum class HabitDecision {
    NONE, BAN_CURRENT, SLEEP_LOCK, SCHEDULE_WHITELIST
}

fun interface HabitJudge {
    fun decide(context: HabitContext): HabitDecision
}

class RuleHabitJudge : HabitJudge {
    override fun decide(context: HabitContext): HabitDecision {
        if (!context.isNightWindow) return HabitDecision.NONE
        val hasSchedule = context.todaySchedules.isNotEmpty()
        val entertainment = context.category == AppCategory.VIDEO ||
            context.category == AppCategory.GAME ||
            context.category == AppCategory.SOCIAL
        return if (hasSchedule) {
            if (entertainment && context.foregroundPackage !in context.habitualPackages) {
                HabitDecision.BAN_CURRENT
            } else {
                HabitDecision.SCHEDULE_WHITELIST
            }
        } else if (context.isPastBedtime && entertainment) {
            HabitDecision.SLEEP_LOCK
        } else if (entertainment) {
            HabitDecision.BAN_CURRENT
        } else {
            HabitDecision.NONE
        }
    }
}

object HabitGuardian {
    private val judge: HabitJudge = RuleHabitJudge()
    private val bannedTonight = mutableSetOf<String>()
    @Volatile var sleepLockActive: Boolean = false
        private set
    private var scheduleAllow: Set<String>? = null
    private var lastResetDay: LocalDate? = null

    fun evaluate(context: Context, packageName: String): HabitEval {
        resetIfNewDay()
        if (isAlwaysAllowed(context, packageName)) return HabitEval.NONE

        if (sleepLockActive && !isAlwaysAllowed(context, packageName)) {
            return HabitEval.SLEEP_LOCK
        }
        if (packageName in bannedTonight) return HabitEval.BLOCK

        val now = LocalTime.now()
        val schedules = runCatching { FlashNoteStore.todaySchedules() }.getOrDefault(emptyList())
        val habitContext = HabitContext(
            now = now,
            isNightWindow = TimeBehaviorConfig.isSleepTime(now),
            isPastBedtime = !now.isBefore(TimeBehaviorConfig.sleepStart),
            foregroundPackage = packageName,
            category = categorize(context, packageName),
            todaySchedules = schedules,
            habitualPackages = habitualPackages(context) + packagesForSchedule(context, schedules)
        )
        return when (judge.decide(habitContext)) {
            HabitDecision.NONE -> {
                scheduleAllow = null
                HabitEval.NONE
            }
            HabitDecision.BAN_CURRENT -> {
                bannedTonight.add(packageName)
                HabitEval.BLOCK
            }
            HabitDecision.SLEEP_LOCK -> {
                sleepLockActive = true
                showSleepLock(context)
                HabitEval.SLEEP_LOCK
            }
            HabitDecision.SCHEDULE_WHITELIST -> {
                val allow = emergencyPackages(context) + habitContext.habitualPackages
                scheduleAllow = allow
                if (packageName in allow) HabitEval.NONE else HabitEval.BLOCK
            }
        }
    }

    fun clearSleepLock() {
        sleepLockActive = false
    }

    private fun resetIfNewDay() {
        val today = LocalDate.now()
        if (lastResetDay == today) return
        lastResetDay = today
        bannedTonight.clear()
        sleepLockActive = false
        scheduleAllow = null
    }

    private fun showSleepLock(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PetService::class.java).setAction(PetService.ACTION_SLEEP_LOCK)
            )
        }
    }

    private fun isAlwaysAllowed(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return packageName in emergencyPackages(context)
    }

    private fun emergencyPackages(context: Context): Set<String> {
        val packages = mutableSetOf(
            context.packageName,
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.android.systemui"
        )
        runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.defaultDialerPackage?.let { packages.add(it) }
        }
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.forEach { packages.add(it.packageName) }
        }
        return packages
    }

    private fun packagesForSchedule(context: Context, schedules: List<FlashNote>): Set<String> {
        val text = schedules.joinToString(" ") { it.text }.lowercase()
        if (text.isBlank()) return emptySet()
        val matched = mutableSetOf<String>()
        if ("微信" in text || "wechat" in text) matched.add("com.tencent.mm")
        if ("钉钉" in text) matched.add("com.alibaba.android.rimet")
        if ("文档" in text || "docs" in text) matched.add("com.google.android.apps.docs")
        if ("邮件" in text || "gmail" in text) matched.add("com.google.android.gm")
        if ("会议" in text || "meet" in text) matched.add("com.google.android.apps.meet")
        runCatching {
            val pm = context.packageManager
            pm.getInstalledApplications(0).forEach { app ->
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.length >= 2 && text.contains(label)) {
                    matched.add(app.packageName)
                }
            }
        }
        return matched
    }

    private fun categorize(context: Context, packageName: String): AppCategory {
        val lowered = packageName.lowercase()
        if (VIDEO_HINTS.any { it in lowered }) return AppCategory.VIDEO
        if (GAME_HINTS.any { it in lowered }) return AppCategory.GAME
        if (SOCIAL_HINTS.any { it in lowered }) return AppCategory.SOCIAL
        if (BROWSER_HINTS.any { it in lowered }) return AppCategory.BROWSER
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            when {
                info.category == ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
                info.category == ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
                info.category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> AppCategory.SYSTEM
                info.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.TOOL
                else -> AppCategory.OTHER
            }
        } catch (_: Exception) {
            AppCategory.OTHER
        }
    }

    private fun habitualPackages(context: Context): Set<String> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySet()
        val zone = ZoneId.systemDefault()
        val end = System.currentTimeMillis()
        val start = LocalDate.now().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val usage = runCatching { ScreenUsageHelper.fetchUsageInMs(usm, start, end) }
            .getOrDefault(emptyMap())
        return usage.entries
            .asSequence()
            .filter { it.value >= 10 * 60_000L }
            .filter { categorize(context, it.key) == AppCategory.TOOL || !isEntertainment(context, it.key) }
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }
            .toSet()
    }

    private fun isEntertainment(context: Context, packageName: String): Boolean {
        val category = categorize(context, packageName)
        return category == AppCategory.VIDEO ||
            category == AppCategory.GAME ||
            category == AppCategory.SOCIAL
    }

    private val VIDEO_HINTS = listOf(
        "tiktok", "douyin", "bilibili", "youtube", "iqiyi", "youku",
        "tencent.qqlive", "kuaishou", "triller", "netflix", "video"
    )
    private val GAME_HINTS = listOf("game", "unity", "mihoyo", "hoyoverse", "pubg", "mlbb")
    private val SOCIAL_HINTS = listOf("weibo", "instagram", "facebook", "twitter", "reddit")
    private val BROWSER_HINTS = listOf("chrome", "browser", "firefox", "edge", "opera", "brave")
}
