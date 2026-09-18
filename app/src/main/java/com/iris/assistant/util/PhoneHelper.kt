package com.iris.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneHelper {
    fun call(context: Context, name: String) {
        val number = ContactHelper.findPhoneByName(context, name) ?: return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
