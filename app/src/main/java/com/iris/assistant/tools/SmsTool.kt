package com.iris.assistant.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolParameter
import com.iris.assistant.agent.ToolResult
import com.iris.assistant.util.ContactHelper

/**
 * BUG FIXED FROM THE ORIGINAL SmsHelper: it called SmsManager directly,
 * which MIUI (and increasingly stock Android) hard-blocks as a
 * "restricted permission" for any app not from the Play Store — no
 * permission toggle can override it. This tool instead opens the
 * phone's own messaging app pre-filled; the user taps Send. That needs
 * no special permission and actually works.
 */
class SmsTool(private val context: Context) : Tool {
    override val name = "send_sms"
    override val description = "Send an SMS message to a contact by name"
    override val parameters = listOf(
        ToolParameter("contact", "Contact name to look up"),
        ToolParameter("message", "The text message body")
    )
    override val isSensitive = true
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val contact = params["contact"] ?: return ToolResult.InvalidInput("contact name missing")
        val message = params["message"] ?: return ToolResult.InvalidInput("message text missing")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.READ_CONTACTS, "برای پیدا کردن شماره‌ی $contact به دسترسی مخاطبین نیاز دارم.")
        }

        val number = ContactHelper.findPhoneByName(context, contact)
            ?: return ToolResult.Failure("مخاطبی به اسم \"$contact\" پیدا نکردم.")

        return ToolResult.NeedsConfirmation(
            prompt = "می‌خوای این پیام برای $contact ارسال بشه؟\n\"$message\"",
            onConfirm = {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                        putExtra("sms_body", message)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ToolResult.Success("اپ پیام‌رسان با متن آماده برای $contact باز شد — فقط دکمه‌ی ارسال رو بزن.")
                } catch (e: Exception) {
                    ToolResult.Failure("خطا در باز کردن اپ پیام‌رسان: ${e.message}")
                }
            }
        )
    }
}
