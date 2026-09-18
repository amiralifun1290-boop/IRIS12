package com.iris.assistant.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolParameter
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.service.IrisAccessibilityService

class AccessibilityClickTool(private val context: Context) : Tool {
    override val name = "accessibility_click"
    override val description = "Find on-screen text in the current app and tap it"
    override val parameters = listOf(ToolParameter("text", "The visible text to find and tap"))
    override val isSensitive = true

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val text = params["text"] ?: return ToolResult.InvalidInput("text missing")
        val service = IrisAccessibilityService.instance
            ?: return ToolResult.Failure("سرویس Accessibility فعال نیست. اول از تنظیمات فعالش کن.")

        return ToolResult.NeedsConfirmation(
            prompt = "می‌خوام روی «$text» کلیک کنم — تاییدش می‌کنی؟",
            onConfirm = {
                if (service.findAndClick(text)) ToolResult.Success("روی «$text» کلیک شد")
                else ToolResult.Failure("چیزی به اسم «$text» روی صفحه پیدا نکردم")
            }
        )
    }
}

class AccessibilityScrollTool : Tool {
    override val name = "accessibility_scroll"
    override val description = "Scroll down in the current app"
    override val parameters = emptyList<ToolParameter>()
    override val isSensitive = false // scrolling is low-risk, doesn't need confirmation

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val service = IrisAccessibilityService.instance
            ?: return ToolResult.Failure("سرویس Accessibility فعال نیست. اول از تنظیمات فعالش کن.")
        return if (service.scrollDown()) ToolResult.Success("صفحه اسکرول شد")
        else ToolResult.Failure("چیز قابل‌اسکرولی پیدا نشد")
    }
}

/** Not a Tool itself — a helper the UI calls to send the user to the Accessibility settings screen. */
object AccessibilitySettingsLauncher {
    fun open(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
