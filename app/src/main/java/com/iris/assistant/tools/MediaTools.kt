package com.iris.assistant.tools

import android.content.Context
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.util.MediaControlHelper

class MediaPlayTool(private val context: Context) : Tool {
    override val name = "media_play"
    override val description = "Toggle play/pause on the current media session"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()
    override suspend fun execute(params: Map<String, String>): ToolResult {
        return try {
            MediaControlHelper.playPause(context)
            ToolResult.Success("پخش/توقف موسیقی اجرا شد")
        } catch (e: Exception) { ToolResult.Failure("کنترل موسیقی ناموفق بود: ${e.message}") }
    }
}

class MediaNextTool(private val context: Context) : Tool {
    override val name = "next_track"
    override val description = "Skip to the next track"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()
    override suspend fun execute(params: Map<String, String>): ToolResult {
        return try {
            MediaControlHelper.next(context)
            ToolResult.Success("آهنگ بعدی")
        } catch (e: Exception) { ToolResult.Failure("ناموفق: ${e.message}") }
    }
}

class MediaPreviousTool(private val context: Context) : Tool {
    override val name = "previous_track"
    override val description = "Go back to the previous track"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()
    override suspend fun execute(params: Map<String, String>): ToolResult {
        return try {
            MediaControlHelper.previous(context)
            ToolResult.Success("آهنگ قبلی")
        } catch (e: Exception) { ToolResult.Failure("ناموفق: ${e.message}") }
    }
}
