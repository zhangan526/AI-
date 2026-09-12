package com.geekathon.guardpet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AgentReply(val text: String, val action: AgentToolAction? = null)

class DeepSeekAgent(private val context: android.content.Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    private val messages = mutableListOf<JSONObject>()

    init { messages += JSONObject().put("role", "system").put("content", SYSTEM_PROMPT) }

    suspend fun ask(input: String): AgentReply = withContext(Dispatchers.IO) {
        if (input.length > 4000) throw IllegalStateException("请输入不超过 4000 字的消息")
        val key = ApiKeyStore.read(context) ?: throw IllegalStateException("请先在 Agent 页面配置 DeepSeek API Key")
        messages += JSONObject().put("role", "user").put("content", input)
        val body = JSONObject().put("model", "deepseek-flash")
            .put("messages", JSONArray(messages)).put("temperature", 0.7).put("stream", false).toString()
        val request = Request.Builder().url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $key").addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException(errorFor(response.code))
                val json = JSONObject(response.body?.string().orEmpty())
                val content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                messages += JSONObject().put("role", "assistant").put("content", content)
                parse(content)
            }
        } catch (error: IOException) {
            messages.removeLastOrNull()
            throw IllegalStateException("无法连接 DeepSeek，请检查网络")
        } catch (error: org.json.JSONException) {
            messages.removeLastOrNull()
            throw IllegalStateException("DeepSeek 返回格式异常，请稍后再试")
        } catch (error: IllegalStateException) {
            messages.removeLastOrNull()
            throw error
        }
    }

    private fun parse(content: String): AgentReply {
        val jsonText = content.substringAfter("{", "").substringBeforeLast("}", "")
            .let { if (it.isBlank()) "" else "{$it}" }
        val json = runCatching { JSONObject(jsonText) }.getOrNull()
        val text = json?.optString("reply").orEmpty().ifBlank { content }
        val action = if (json == null || json.optString("action").isBlank() || json.optString("action") == "none") {
            null
        } else {
            runCatching { AgentTools.parse(json) }.getOrElse {
                throw IllegalStateException("模型返回的操作格式无效，未执行：${it.message}")
            }
        }
        return AgentReply(text, action)
    }

    companion object {
        private const val SYSTEM_PROMPT = """
你是 Android 桌宠“守伴”的 Agent。用简短、温和的中文回答。
当用户要求你操作桌宠或记录内容时，只输出一个 JSON 对象，格式：
{"reply":"给用户看的话","action":"pet|feed|sleep|flash_note|none","text":"要记录的内容","category":"schedule|idea|diary|todo|other","date":"YYYY-MM-DD"}
不需要操作时 action 必须是 none。不要输出 Markdown，不要输出 JSON 以外的解释。
"""

        private fun errorFor(code: Int) = when (code) {
            401 -> "API Key 无效，请重新保存"
            402 -> "DeepSeek 余额不足，请充值后重试"
            403 -> "当前 API Key 没有访问权限"
            404, 422 -> "模型或请求不可用，请检查网络"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "DeepSeek 暂时繁忙，请稍后再试"
            else -> "DeepSeek 请求失败（$code）"
        }
    }
}
