package com.iris.assistant.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * PLACEHOLDER FIXED: the original "weather" branch in CommandProcessor
 * just printed the device's raw GPS coordinates and never called a
 * weather service at all. This is a real fetch to Open-Meteo (free,
 * no API key, no account) using the device's last known location.
 */
class WeatherTool(private val context: Context) : Tool {
    override val name = "get_weather"
    override val description = "Get the current weather at the device's location"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()
    override val requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION, "برای آب‌وهوا به موقعیت مکانی نیاز دارم.")
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return ToolResult.Failure("موقعیت مکانی در دسترس نیست — GPS رو روشن کن.")

        return try {
            val result = withContext(Dispatchers.IO) { fetchWeather(loc.latitude, loc.longitude) }
            ToolResult.Success(result, mapOf("lat" to loc.latitude, "lon" to loc.longitude))
        } catch (e: Exception) {
            ToolResult.Failure("دریافت آب‌وهوا ناموفق بود: ${e.message}")
        }
    }

    private fun fetchWeather(lat: Double, lon: Double): String {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val current = JSONObject(text).getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val code = current.getInt("weather_code")
        return "$temp درجه، ${describeCode(code)}"
    }

    private fun describeCode(code: Int): String = when (code) {
        0 -> "صاف و آفتابی"
        1, 2, 3 -> "نیمه‌ابری"
        45, 48 -> "مه‌آلود"
        51, 53, 55, 61, 63, 65 -> "بارونی"
        71, 73, 75 -> "برفی"
        95 -> "طوفانی"
        else -> "نامشخص"
    }
}
