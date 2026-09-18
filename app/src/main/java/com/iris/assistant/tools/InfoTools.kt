package com.iris.assistant.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolParameter
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.util.TranslatorHelper

class TranslateTool : Tool {
    override val name = "translate"
    override val description = "Translate text between Persian and English (on-device, offline)"
    override val parameters = listOf(ToolParameter("text", "The text to translate"))

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val text = params["text"]?.trim()
        if (text.isNullOrBlank()) return ToolResult.InvalidInput("text missing")
        val isPersian = text.any { it.code in 0x0600..0x06FF }
        return try {
            val translated = if (isPersian) TranslatorHelper.translateFaToEn(text) else TranslatorHelper.translateEnToFa(text)
            ToolResult.Success(translated)
        } catch (e: Exception) {
            ToolResult.Failure("ترجمه ناموفق بود: ${e.message}")
        }
    }
}

class LocationTool(private val context: Context) : Tool {
    override val name = "get_location"
    override val description = "Get the device's current GPS coordinates"
    override val parameters = emptyList<ToolParameter>()
    override val requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION, "برای موقعیت مکانی به این دسترسی نیاز دارم.")
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return ToolResult.Failure("موقعیت در دسترس نیست.")
        return ToolResult.Success("موقعیت فعلی: ${loc.latitude}, ${loc.longitude}")
    }
}
