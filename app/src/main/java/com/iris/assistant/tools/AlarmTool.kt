package com.iris.assistant.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolParameter
import com.iris.assistant.agent.ToolResult

/**
 * BUG FIXED FROM THE ORIGINAL AlarmHelper: it derived hour/minute from
 * an absolute epoch-millis value using UTC-based modulo arithmetic
 * (`(timeMillis / 3_600_000) % 24`) — this reads the UTC hour-of-day
 * component of an epoch timestamp, not "hours from local midnight", so
 * on a UTC+3:30 device (Iran) every alarm landed roughly 3.5 hours off.
 * This tool takes an explicit "HH:mm" string and uses it directly — no
 * timezone arithmetic to get wrong.
 */
class AlarmTool(private val context: Context) : Tool {
    override val name = "set_alarm"
    override val description = "Set a system alarm for a specific time of day"
    override val parameters = listOf(ToolParameter("time", "Time in HH:mm 24-hour format, e.g. 6:30"))

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val time = params["time"] ?: return ToolResult.InvalidInput("time missing")
        val match = Regex("(\\d{1,2}):(\\d{2})").find(time)
            ?: return ToolResult.InvalidInput("could not parse time \"$time\", expected HH:mm")
        val (hourStr, minuteStr) = match.destructured
        val hour = hourStr.toIntOrNull() ?: return ToolResult.InvalidInput("invalid hour in \"$time\"")
        val minute = minuteStr.toIntOrNull() ?: return ToolResult.InvalidInput("invalid minute in \"$time\"")

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "IRIS Alarm")
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return ToolResult.Failure("هیچ اپ ساعتی برای تنظیم آلارم پیدا نشد.")
            }
            context.startActivity(intent)
            ToolResult.Success("آلارم برای ساعت %02d:%02d تنظیم شد".format(hour, minute))
        } catch (e: Exception) {
            ToolResult.Failure("تنظیم آلارم ناموفق بود: ${e.message}")
        }
    }
}
