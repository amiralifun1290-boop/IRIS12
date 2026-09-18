package com.iris.assistant.util

import android.content.Context
import android.telephony.SmsManager

object SmsHelper {
    fun send(context: Context, phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
