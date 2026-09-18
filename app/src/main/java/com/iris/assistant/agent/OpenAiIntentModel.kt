package com.iris.assistant.agent

import android.content.Context
import com.iris.assistant.config.ApiKeyStore
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

class ApiException(message: String, val httpCode: Int = -1) : Exception(message)

/**
 * Sends the user's message to an OpenAI-compatible chat-completions endpoint
 * and decides what IRIS should do.
 *
 * Deliberately does NOT rely on the provider's native "tools"/function-calling
 * parameter — support for that varies a lot between providers and even
 * between models on the same provider (e.g. small open models on Cloudflare
 * Workers AI may not support it reliably at all). Instead, every available
 * Tool is described in plain text in the system prompt, and the model is
 * instructed to answer with a small JSON object saying either "call this
 * tool with these params" or "just reply with this text". This is more
 * verbose per-request but works the same way regardless of which provider
 * or model is configured — which matters here since this project has
 * already had to switch providers once.
 */
class OpenAiIntentModel(private val context: Context) : IntentModel {

    override suspend fun decide(
        userText: String,
        availableTools: Collection<Tool>,
        memory: ShortTermMemory
    ): Decision = withContext(Dispatchers.IO) {
        val providers = ApiKeyStore.getAllUsableProviders(context)
        if (providers.isEmpty()) {
            throw ApiException("هیچ کلید API تنظیم نشده. از تنظیمات یک سرویس رایگان اضافه کن.")
        }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", buildSystemPrompt(availableTools)))
        for ((role, content) in memory.recent()) {
            val mappedRole = if (role == "user") "user" else "assistant"
            messages.put(JSONObject().put("role", mappedRole).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        // Try providers in order until one succeeds (multi-model free fallback)
        var lastError: Exception? = null
        for (provider in providers) {
            try {
                val body = JSONObject()
                    .put("model", provider.model)
                    .put("messages", messages)
                    .put("temperature", 0.3)

                val responseJson = postJson(
                    "${provider.baseUrl.trimEnd('/')}/chat/completions",
                    provider.apiKey,
                    body
                )
                val choices = responseJson.optJSONArray("choices")
                    ?: throw ApiException("پاسخ نامعتبر از ${provider.name}")
                val content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim()

                return@withContext parseModelReply(content)
            } catch (e: Exception) {
                lastError = e
                // try next provider
            }
        }

        throw ApiException(
            lastError?.message ?: "همه سرویس‌های آنلاین در دسترس نبودند."
        )
    }

    /** Sends a minimal request just to confirm the API key/URL/model actually work. */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = ApiKeyStore.getApiKey(context)
                ?: return@withContext Result.failure(ApiException("ابتدا یک کلید API وارد کن."))
            val model = ApiKeyStore.getModel(context)
            val baseUrl = ApiKeyStore.getBaseUrl(context)
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
                .put("max_tokens", 5)
            postJson("$baseUrl/chat/completions", apiKey, body)
            Result.success("اتصال موفق بود ✅ کلید و آدرس درست کار می‌کنن.")
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ApiException(e.message ?: "خطای نامشخص در اتصال."))
        }
    }

    private fun buildSystemPrompt(availableTools: Collection<Tool>): String {
        val toolsList = availableTools.joinToString("\n") { tool ->
            val params = tool.parameters.joinToString(", ") { "${it.name} (${it.description})" }
            "- ${tool.name}: ${tool.description}" + if (params.isNotBlank()) " | پارامترها: $params" else " | بدون پارامتر"
        }
        val profileBlock = com.iris.assistant.config.UserProfileStore.buildProfileBlock(context)
        return """
            تو IRIS هستی، یک دستیار هوشمند فارسی‌زبان روی گوشی اندروید. خیلی خودمونی، کوتاه و دوستانه جواب بده، مگه اینکه کاربر سبک دیگه‌ای خواسته باشه.
            ${if (profileBlock.isNotBlank()) "\n$profileBlock\n" else ""}
            این ابزارها رو در اختیار داری:
            $toolsList

            قانون خروجی — این قانون رو هیچ‌وقت نشکن: پاسخ تو باید همیشه دقیقاً و فقط یک شیء JSON خام باشه.
            هیچ متن دیگه‌ای، هیچ توضیحی، هیچ علامت ```، و هیچ چیز قبل یا بعد از JSON ننویس.
            فقط خود JSON، از { شروع بشه و با } تموم بشه.

            اگر باید یکی از ابزارهای بالا رو صدا بزنی، دقیقاً همین شکل:
            {"action":"tool","tool":"<اسم دقیق ابزار>","params":{"<پارامتر>":"<مقدار>"}}

            اگر چیزی لازم برای اجرای ابزار کمه (مثلاً اسم مخاطب یا متن پیام)، به‌جای صدا زدن ابزار، دقیقاً همین شکل بپرس:
            {"action":"reply","text":"<سوالت اینجا>"}

            اگر کاربر فقط می‌خواد صحبت کنه یا سوال عمومی داره، باز هم دقیقاً همین شکل جواب بده:
            {"action":"reply","text":"<جواب اینجا>"}

            مثال غلط (هرگز این‌طور جواب نده): سلام! چطور می‌تونم کمکت کنم؟
            مثال درست: {"action":"reply","text":"سلام! چطور می‌تونم کمکت کنم؟"}

            یادت باشه: خروجی تو همیشه با { شروع می‌شه، هیچ‌وقت با کلمه یا متن عادی شروع نمی‌شه.
        """.trimIndent()
    }

    private fun parseModelReply(rawContent: String): Decision {
        val jsonText = extractJsonObject(rawContent) ?: return Decision(null, emptyMap(), rawContent.ifBlank { "متوجه نشدم." })
        return try {
            val json = JSONObject(jsonText)
            when (json.optString("action")) {
                "tool" -> {
                    val toolName = json.optString("tool").ifBlank { return Decision(null, emptyMap(), rawContent) }
                    val params = mutableMapOf<String, String>()
                    json.optJSONObject("params")?.let { paramsJson ->
                        val keys = paramsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            params[key] = paramsJson.optString(key)
                        }
                    }
                    Decision(toolName, params)
                }
                else -> Decision(null, emptyMap(), json.optString("text", rawContent))
            }
        } catch (e: Exception) {
            // Model didn't follow the JSON format this time — just show whatever it said.
            Decision(null, emptyMap(), rawContent)
        }
    }

    /** Finds the first {...} block in the text, in case the model wrapped it in extra words or ``` fences. */
    private fun extractJsonObject(text: String): String? {
        if (text.isBlank()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    private fun postJson(endpoint: String, apiKey: String, body: JSONObject): JSONObject {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 20000
            readTimeout = 30000
        }
        try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                val errorMessage = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                } ?: text
                val friendly = when (code) {
                    401, 403 -> "کلید API نامعتبره یا اجازه‌ی دسترسی نداره."
                    429 -> "محدودیت استفاده یا اعتبار حساب تمام شده."
                    else -> errorMessage.ifBlank { "خطای سرور (کد $code)" }
                }
                throw ApiException(friendly, code)
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
