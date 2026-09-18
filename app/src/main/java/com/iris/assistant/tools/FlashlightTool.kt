package com.iris.assistant.tools

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.util.FlashlightHelper

class FlashlightTool(private val context: Context) : Tool {
    override val name = "control_flashlight"
    override val description = "Toggle the device flashlight on or off"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()
    override val requiredPermissions = listOf(Manifest.permission.CAMERA)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.CAMERA, "برای کنترل چراغ‌قوه به دسترسی دوربین نیاز دارم.")
        }
        return try {
            val on = FlashlightHelper.toggle(context)
            ToolResult.Success(if (on) "چراغ‌قوه روشن شد" else "چراغ‌قوه خاموش شد")
        } catch (e: Exception) {
            ToolResult.Failure("کنترل چراغ‌قوه ناموفق بود: ${e.message}")
        }
    }
}
