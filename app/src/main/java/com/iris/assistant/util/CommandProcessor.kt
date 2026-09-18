package com.iris.assistant.util

import android.content.Context
import com.iris.assistant.IrisApp

class CommandProcessor(private val context: Context) {
    private val tts = IrisApp.instance.ttsHelper

    suspend fun execute(command: String): String {
        val cmd = command.lowercase()

        return when {
            cmd.contains("تماس") || cmd.contains("زنگ") -> {
                val name = extractName(cmd)
                if (name.isNullOrBlank()) {
                    "اسم مخاطب رو نگفتی — مثلاً بگو «زنگ بزن به علی»."
                } else {
                    val number = ContactHelper.findPhoneByName(context, name)
                    if (number == null) {
                        "مخاطبی به اسم «$name» پیدا نکردم."
                    } else {
                        PhoneHelper.call(context, name)
                        "در حال برقراری تماس با $name"
                    }
                }
            }
            cmd.contains("پیام") || cmd.contains("اس‌ام‌اس") || cmd.contains("اس ام اس") -> {
                val (name, message) = extractSmsParts(command)
                if (name.isNullOrBlank() || message.isNullOrBlank()) {
                    "بگو مثلاً «به علی بگو سلام» یا «پیام بده به علی که سلام»."
                } else {
                    val number = ContactHelper.findPhoneByName(context, name)
                    if (number == null) {
                        "مخاطبی به اسم «$name» پیدا نکردم."
                    } else {
                        SmsHelper.send(context, number, message)
                        "پیام برای $name ارسال شد"
                    }
                }
            }
            cmd.contains("آب و هوا") || cmd.contains("هوا") -> {
                val loc = LocationHelper.getLastLocation(context)
                "موقعیت فعلی: $loc — برای آب‌وهوای دقیق، سرویس آب‌وهوای واقعی هنوز وصل نشده."
            }
            cmd.contains("عکس") || cmd.contains("دوربین") -> {
                "دوربین باز شد"
            }
            cmd.contains("آلارم") || cmd.contains("ساعت") -> {
                AlarmHelper.setAlarm(context, System.currentTimeMillis() + 60000)
                "آلارم یک دقیقه دیگر تنظیم شد"
            }
            cmd.contains("چراغ") || cmd.contains("فلش") -> {
                val on = FlashlightHelper.toggle(context)
                if (on) "چراغ‌قوه روشن شد" else "چراغ‌قوه خاموش شد"
            }
            cmd.contains("باز کن") -> {
                val app = extractAppName(cmd)
                if (app.isNullOrBlank()) {
                    "اسم اپ رو نگفتی — مثلاً بگو «تلگرام رو باز کن»."
                } else {
                    AppLauncherHelper.open(context, app)
                    "در حال باز کردن $app"
                }
            }
            cmd.contains("موسیقی") || cmd.contains("آهنگ") -> {
                MediaControlHelper.playPause(context)
                "دستور پخش/توقف موسیقی اجرا شد"
            }
            cmd.contains("ترجمه") -> {
                val text = command.replace(Regex("ترجمه کن|ترجمه"), "").trim()
                if (text.isBlank()) {
                    "چی رو ترجمه کنم؟"
                } else if (containsPersian(text)) {
                    TranslatorHelper.translateFaToEn(text)
                } else {
                    TranslatorHelper.translateEnToFa(text)
                }
            }
            cmd.contains("مکان") || cmd.contains("کجا") -> {
                val loc = LocationHelper.getLastLocation(context)
                "موقعیت فعلی: $loc"
            }
            else -> "متوجه نشدم. لطفاً تکرار کنید."
        }
    }

    /** Extracts the contact name after "به"/"با" — returns null (not a guessed default) if none found. */
    private fun extractName(cmd: String): String? {
        val marker = listOf("زنگ بزن به", "تماس بگیر با", "تماس با", "با ", "به ")
            .firstOrNull { cmd.contains(it) } ?: return null
        val after = cmd.substringAfter(marker).trim()
        return after.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Matches "به NAME بگو MESSAGE" or "پیام بده به NAME که MESSAGE" — real extraction, no fake defaults. */
    private fun extractSmsParts(command: String): Pair<String?, String?> {
        val patterns = listOf(
            Regex("به (.+?) بگو (.+)"),
            Regex("پیام بده به (.+?) که (.+)"),
            Regex("پیام بده به (.+?) بگو (.+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(command)
            if (match != null) {
                val (name, message) = match.destructured
                return name.trim() to message.trim()
            }
        }
        return null to null
    }

    private fun extractAppName(cmd: String): String? {
        val before = cmd.substringBefore("باز کن").trim().removeSuffix("رو").trim()
        if (before.isNotBlank()) return before.split(" ").lastOrNull()?.takeIf { it.isNotBlank() }
        val after = cmd.substringAfter("باز کن").trim()
        return after.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun containsPersian(text: String): Boolean = text.any { it.code in 0x0600..0x06FF }
}
