package com.iris.assistant.util

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

object AlarmHelper {
    fun setAlarm(context: Context, timeMillis: Long) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "IRIS Alarm")
            putExtra(AlarmClock.EXTRA_HOUR, ((timeMillis / (1000 * 60 * 60)) % 24).toInt())
            putExtra(AlarmClock.EXTRA_MINUTES, ((timeMillis / (1000 * 60)) % 60).toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
