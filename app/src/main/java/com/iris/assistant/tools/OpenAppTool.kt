package com.iris.assistant.tools

import android.content.Context
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolParameter
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.util.AppLauncherHelper

class OpenAppTool(private val context: Context) : Tool {
    override val name = "open_app"
    override val description = "Open an installed app by name"
    override val parameters = listOf(ToolParameter("app", "App name (partial match against installed apps)"))

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val app = params["app"]?.trim()
        if (app.isNullOrBlank()) return ToolResult.InvalidInput("app name missing")
        return try {
            val opened = AppLauncherHelper.open(context, app)
            if (opened) {
                ToolResult.Success("در حال باز کردن $app")
            } else {
                ToolResult.Failure("اپ «$app» رو پیدا نکردم. مطمئنی نصبه و اسمش همینه؟")
            }
        } catch (e: Exception) {
            ToolResult.Failure("باز کردن $app ناموفق بود: ${e.message}")
        }
    }
}
