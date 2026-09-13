package com.example.desktoppet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 通义千问 / 万相 文生图与图生图（DashScope multimodal-generation）。
 * fidelity=true 时关闭随意扩写，强化提示词贴合。
 */
class QwenImageClient(private val apiKey: String) {

    fun textToImage(
        prompt: String,
        negativePrompt: String = DEFAULT_NEGATIVE,
        size: String = DEFAULT_SIZE,
        seed: Int? = null,
        promptExtend: Boolean = false,
        fidelity: Boolean = true
    ): ByteArray {
        require(apiKey.isNotBlank()) { "千问 API Key 未配置" }
        val cleaned = prompt.trim().take(2200)
        require(cleaned.isNotEmpty()) { "提示词为空" }
        val content = JSONArray().put(JSONObject().put("text", cleaned))
        return generateWithFallback(
            content = content,
            negativePrompt = negativePrompt,
            size = size,
            forEdit = false,
            seed = seed,
            promptExtend = promptExtend,
            fidelity = fidelity
        )
    }

    fun imageToImage(
        prompt: String,
        referenceJpegOrPng: ByteArray,
        mime: String = "image/jpeg",
        negativePrompt: String = DEFAULT_NEGATIVE,
        size: String = DEFAULT_SIZE,
        seed: Int? = null,
        promptExtend: Boolean = false,
        fidelity: Boolean = true
    ): ByteArray {
        require(apiKey.isNotBlank()) { "千问 API Key 未配置" }
        val cleaned = prompt.trim().take(2200)
        require(cleaned.isNotEmpty()) { "提示词为空" }
        require(referenceJpegOrPng.isNotEmpty()) { "参考图为空" }
        val prepared = compressForApi(referenceJpegOrPng)
        val dataUrl = "data:${prepared.second};base64," +
            Base64.encodeToString(prepared.first, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("image", dataUrl))
            .put(JSONObject().put("text", cleaned))
        return generateWithFallback(
            content = content,
            negativePrompt = negativePrompt,
            size = size,
            forEdit = true,
            seed = seed,
            promptExtend = promptExtend,
            fidelity = fidelity
        )
    }

    private fun generateWithFallback(
        content: JSONArray,
        negativePrompt: String,
        size: String,
        forEdit: Boolean,
        seed: Int?,
        promptExtend: Boolean,
        fidelity: Boolean
    ): ByteArray {
        val models = if (forEdit) {
            listOf("qwen-image-3.0", "qwen-image-plus", "qwen-image", "wan2.6-image")
        } else {
            // Prefer instruction-following image models first
            listOf("qwen-image-3.0", "qwen-image-plus", "qwen-image", "wan2.6-t2i", "wanx2.1-t2i-turbo")
        }
        var lastError: Exception? = null
        for (model in models) {
            try {
                return generateOnce(
                    model = model,
                    content = content,
                    negativePrompt = negativePrompt,
                    size = size,
                    seed = seed,
                    promptExtend = promptExtend,
                    fidelity = fidelity
                )
            } catch (e: Exception) {
                Log.w(TAG, "image model $model failed: ${e.message}")
                lastError = e
            }
        }
        throw friendlyError(lastError)
    }

    private fun generateOnce(
        model: String,
        content: JSONArray,
        negativePrompt: String,
        size: String,
        seed: Int?,
        promptExtend: Boolean,
        fidelity: Boolean
    ): ByteArray {
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", content)
        )
        val parameters = JSONObject()
            .put("size", size)
            .put("n", 1)
            // 已手写锁定提示时关闭扩写，避免模型自由发挥偏离用户描述
            .put("prompt_extend", if (fidelity) false else promptExtend)
            .put("watermark", false)
            .put("negative_prompt", negativePrompt)
        if (seed != null) {
            parameters.put("seed", seed.coerceIn(0, Int.MAX_VALUE))
        }
        val body = JSONObject()
            .put("model", model)
            .put("input", JSONObject().put("messages", messages))
            .put("parameters", parameters)
            .toString()

        val connection = (URL(GENERATION_ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                error("HTTP $code: ${extractError(text)}")
            }
            val imageUrl = extractImageUrl(text) ?: error("千问未返回图片 URL: ${text.take(200)}")
            return downloadImage(imageUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractImageUrl(raw: String): String? {
        val root = JSONObject(raw)
        if (root.has("code") && root.optString("code").isNotBlank() &&
            root.optString("code") != "Success"
        ) {
            error(root.optString("message").ifBlank { raw.take(200) })
        }
        root.optJSONObject("output")?.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optJSONArray("content")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val img = item.optString("image").ifBlank { item.optString("image_url") }
                    if (img.isNotBlank()) return img
                }
            }
        root.optJSONObject("output")?.optJSONArray("results")?.optJSONObject(0)?.let {
            val u = it.optString("url")
            if (u.isNotBlank()) return u
        }
        root.optJSONArray("data")?.optJSONObject(0)?.let {
            val u = it.optString("url")
            if (u.isNotBlank()) return u
        }
        return null
    }

    private fun downloadImage(urlSpec: String): ByteArray {
        val connection = (URL(urlSpec).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "image/*,*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("下载图片 HTTP $code")
            val bytes = connection.inputStream.use { it.readBytes() }
            require(bytes.size > 2_000) { "图片过小" }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun compressForApi(bytes: ByteArray): Pair<ByteArray, String> {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes to "image/jpeg"
        val maxSide = 1280
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == decoded.width && h == decoded.height) decoded
        else Bitmap.createScaledBitmap(decoded, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled !== decoded && !scaled.isRecycled) scaled.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        return out.toByteArray() to "image/jpeg"
    }

    private fun extractError(raw: String): String = runCatching {
        val obj = JSONObject(raw)
        obj.optString("message")
            .ifBlank { obj.optJSONObject("error")?.optString("message").orEmpty() }
            .ifBlank { raw.take(240) }
    }.getOrDefault(raw.take(240))

    private fun friendlyError(cause: Exception?): IllegalStateException {
        val msg = cause?.message.orEmpty()
        return when {
            msg.contains("InvalidApiKey", true) || msg.contains("401") ->
                IllegalStateException("千问 API Key 无效，请在「陪伴偏好」中填写阿里云百炼 Key")
            msg.contains("Arrearage", true) || msg.contains("AccessDenied", true) ->
                IllegalStateException("千问账户余额不足或无模型权限，请到阿里云百炼开通图像模型")
            msg.contains("Unable to resolve host", true) || msg.contains("Failed to connect", true) ->
                IllegalStateException("无法连接千问服务，请检查网络后重试")
            else -> IllegalStateException(
                "千问出图失败：${msg.ifBlank { cause?.javaClass?.simpleName ?: "unknown" }}"
            )
        }
    }

    companion object {
        private const val TAG = "QwenImageClient"
        private const val GENERATION_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
        private const val DEFAULT_SIZE = "1024*1024"
        private const val DEFAULT_NEGATIVE =
            "低分辨率，模糊，畸形肢体，多余手指，文字水印，多只角色，真实照片背景杂乱，血腥恐怖，换物种，错误颜色"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 180_000
    }
}
