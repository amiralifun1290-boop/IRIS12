package com.iris.assistant.agent

import android.content.Context
import com.iris.assistant.memory.ShortTermMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * مدل آنلاین رایگان بدون نیاز به API Key.
 *
 * از چند endpoint عمومی/ناشناس OpenAI-compatible استفاده می‌کند.
 * این روش شکننده است و ممکن است endpointها بعد از مدتی قطع شوند،
 * اما برای استفاده شخصی و بدون کلید طراحی شده.
 *
 * اولویت:
 *  1) LLM7.io (anonymous)
 *  2) g4f public mirrors
 *  3) سایر endpointهای رایگان شناخته‌شده
 */
class FreeWebIntentModel(
    private val context: Context
) : IntentModel {

    data class FreeEndpoint(
        val name: String,
        val baseUrl: String,
        val model: String,
        val needsAuthHeader: Boolean = false,
        val extraHeaders: Map<String, String> = emptyMap()
    )

    // لیست endpointهای رایگان بدون کلید (ممکن است تغییر کنند)
    private val endpoints = listOf(
        FreeEndpoint(
            name = "LLM7",
            baseUrl = "https://api.llm7.io/v1",
            model = "gpt-4.1-nano"
        ),
        FreeEndpoint(
            name = "g4f-pollinations",
            baseUrl = "https://g4f.space/api/pollinations",
            model = "openai"
        ),
        FreeEndpoint(
            name = "g4f-groq",
            baseUrl = "https://g4f.space/api/groq",
            model = "llama-3.3-70b-versatile"
        ),
        FreeEndpoint(
            name = "g4f-auto",
            baseUrl = "https://g4f.space/api/auto",
            model = "gpt-4o-mini"
        )
    )

    override suspend fun decide(
        userText: String,
        availableTools: Collection<Tool>,
        memory: ShortTermMemory
    ): Decision = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", buildSystemPrompt(availableTools)))
        for ((role, content) in memory.recent()) {
            val mappedRole = if (role == "user") "user" else "assistant"
            messages.put(JSONObject().put("role", mappedRole).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        var lastError: String? = null
        for (ep in endpoints) {
            try {
                val body = JSONObject()
                    .put("model", ep.model)
                    .put("messages", messages)
                    .put("temperature", 0.4)
                    .put("stream", false)

                val responseJson = postJson(ep, body)
                val content = extractContent(responseJson)
                if (!content.isNullOrBlank()) {
                    return@withContext parseModelReply(content)
                }
            } catch (e: Exception) {
                lastError = "${ep.name}: ${e.message}"
            }
        }

        // هیچ endpointای جواب نداد
        Decision(
            null,
            emptyMap(),
            "⚠️ فعلاً به مدل‌های رایگان آنلاین وصل نشدم (${lastError ?: "خطای ناشناخته"}). " +
                "سوال‌های ساده‌تر رو خودم جواب می‌دم."
        )
    }

    private fun buildSystemPrompt(availableTools: Collection<Tool>): String {
        val toolsList = availableTools.joinToString("\n") { tool ->
            val params = tool.parameters.joinToString(", ") { "${it.name} (${it.description})" }
            "- ${tool.name}: ${tool.description}" + if (params.isNotBlank()) " | پارامترها: $params" else ""
        }
        return """
            تو IRIS هستی، دستیار هوشمند فارسی‌زبان روی گوشی اندروید.
            کوتاه، خودمونی و مفید جواب بده.

            ابزارهای موجود:
            $toolsList

            قانون خروجی — فقط یک JSON خام برگردان، هیچ متن اضافه‌ای ننویس:
            اگر باید ابزار صدا بزنی:
            {"action":"tool","tool":"<اسم ابزار>","params":{...}}
            اگر فقط جواب متنی:
            {"action":"reply","text":"<جواب>"}
        """.trimIndent()
    }

    private fun parseModelReply(rawContent: String): Decision {
        val jsonText = extractJsonObject(rawContent) ?: return Decision(null, emptyMap(), rawContent.ifBlank { "متوجه نشدم." })
        return try {
            val obj = JSONObject(jsonText)
            when (obj.optString("action")) {
                "tool" -> {
                    val toolName = obj.optString("tool").takeIf { it.isNotBlank() }
                    val paramsObj = obj.optJSONObject("params")
                    val params = mutableMapOf<String, String>()
                    if (paramsObj != null) {
                        paramsObj.keys().forEach { key ->
                            params[key] = paramsObj.optString(key, "")
                        }
                    }
                    Decision(toolName, params)
                }
                else -> {
                    val text = obj.optString("text").ifBlank { rawContent }
                    Decision(null, emptyMap(), text)
                }
            }
        } catch (_: Exception) {
            Decision(null, emptyMap(), rawContent)
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun extractContent(responseJson: JSONObject): String? {
        // OpenAI-style
        val choices = responseJson.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val msg = choices.getJSONObject(0).optJSONObject("message")
            if (msg != null) return msg.optString("content", "").trim().ifBlank { null }
            return choices.getJSONObject(0).optString("text", "").trim().ifBlank { null }
        }
        // Some free proxies return plain text or different shapes
        responseJson.optString("response").takeIf { it.isNotBlank() }?.let { return it }
        responseJson.optString("content").takeIf { it.isNotBlank() }?.let { return it }
        responseJson.optString("text").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun postJson(ep: FreeEndpoint, body: JSONObject): JSONObject {
        val url = URL("${ep.baseUrl.trimEnd('/')}/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "IRIS-Android/2.0")
            // بعضی سرویس‌های رایگان session id می‌خوان
            setRequestProperty("x-session-id", UUID.randomUUID().toString())
            ep.extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            if (ep.needsAuthHeader) {
                setRequestProperty("Authorization", "Bearer free")
            }
        }

        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() } ?: ""

        if (code !in 200..299) {
            throw Exception("HTTP $code: ${responseText.take(200)}")
        }
        return JSONObject(responseText)
    }
}
