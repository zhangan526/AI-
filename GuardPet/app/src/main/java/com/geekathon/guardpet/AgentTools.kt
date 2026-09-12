package com.geekathon.guardpet

import org.json.JSONArray
import org.json.JSONObject

sealed class AgentToolAction {
    data object Pet : AgentToolAction()
    data object Feed : AgentToolAction()
    data object Sleep : AgentToolAction()
    data class FlashNote(val text: String, val category: FlashNoteCategory, val date: String?) : AgentToolAction()
}

/** Validates the model's structured action before the app executes it. */
object AgentTools {
    fun parse(json: JSONObject): AgentToolAction {
        val action = json.optString("action")
        require(action in setOf("pet", "feed", "sleep", "flash_note", "none")) { "守伴不支持此操作" }
        return when (action) {
            "pet" -> AgentToolAction.Pet
            "feed" -> AgentToolAction.Feed
            "sleep" -> AgentToolAction.Sleep
            "flash_note" -> {
                val text = json.optString("text").trim()
                require(text.isNotEmpty() && text.length <= 4000) { "闪记内容无效" }
                val category = FlashNoteCategory.fromKey(json.optString("category"))
                val date = json.optString("date").takeIf { it.isNotBlank() }
                if (category == FlashNoteCategory.SCHEDULE) {
                    require(date != null && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "日程必须有 YYYY-MM-DD 日期" }
                    runCatching { java.time.LocalDate.parse(date) }.getOrElse { throw IllegalArgumentException("日程日期无效") }
                } else require(date == null) { "只有日程可以带日期" }
                AgentToolAction.FlashNote(text, category, date)
            }
            else -> throw IllegalArgumentException("没有可执行操作")
        }
    }
}
