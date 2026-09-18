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

class CallTool(private val context: Context) : Tool {
    override val name = "make_call"
    override val description = "Place a phone call to a contact by name"
    override val parameters = listOf(ToolParameter("contact", "Contact name to call"))
    override val isSensitive = true
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val contact = params["contact"] ?: return ToolResult.InvalidInput("contact name missing")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.READ_CONTACTS, "برای پیدا کردن شماره‌ی $contact به دسترسی مخاطبین نیاز دارم.")
        }
        val number = ContactHelper.findPhoneByName(context, contact)
            ?: return ToolResult.Failure("مخاطبی به اسم \"$contact\" پیدا نکردم.")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.NeedsPermission(Manifest.permission.CALL_PHONE, "برای تماس با $contact به دسترسی تماس نیاز دارم.")
        }

        return ToolResult.NeedsConfirmation(
            prompt = "می‌خوای با $contact تماس بگیرم؟",
            onConfirm = {
                try {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ToolResult.Success("در حال تماس با $contact")
                } catch (e: Exception) {
                    ToolResult.Failure("تماس ناموفق بود: ${e.message}")
                }
            }
        )
    }
}
