package com.iris.assistant.util

import android.content.Context
import android.content.Intent

/**
 * BUG FIXED FROM THE ORIGINAL: it called `packageManager.getInstalledApplications()`,
 * which on Android 11+ (API 30+) returns almost nothing due to package-visibility
 * restrictions unless the app declares <queries> in its manifest — so on any modern
 * phone this basically never found a real app. It also never checked whether a match
 * was actually found before reporting success back to the user.
 *
 * Fixed by:
 *  - querying only launcher-visible activities (which the manifest's <queries> block
 *    for ACTION_MAIN/CATEGORY_LAUNCHER makes visible), the same way the home screen
 *    itself finds apps
 *  - mapping common Persian app names ("تقویم", "واتساپ"...) to the English words
 *    that actually appear in most app labels/package names
 *  - returning a real Boolean so the caller can report an honest failure instead of
 *    a fake "opening..." message when nothing was actually launched
 */
object AppLauncherHelper {

    private val aliases = mapOf(
        "تقویم" to "calendar",
        "واتساپ" to "whatsapp",
        "وا تس اپ" to "whatsapp",
        "اینستاگرام" to "instagram",
        "اینستا" to "instagram",
        "تلگرام" to "telegram",
        "یوتیوب" to "youtube",
        "گوگل مپ" to "maps",
        "نقشه" to "maps",
        "دوربین" to "camera",
        "گالری" to "gallery",
        "عکس" to "gallery",
        "مخاطبین" to "contacts",
        "مرورگر" to "chrome",
        "کروم" to "chrome",
        "تنظیمات" to "settings",
        "ساعت" to "clock",
        "ماشین حساب" to "calculator",
        "پیامک" to "messag"
    )

    /** Returns true if an app was actually found and launched. */
    fun open(context: Context, appNameRaw: String): Boolean {
        val pm = context.packageManager
        val appName = appNameRaw.trim()
        if (appName.isEmpty()) return false

        val alias = aliases.entries.firstOrNull { appName.contains(it.key, ignoreCase = true) }?.value

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = pm.queryIntentActivities(launcherIntent, 0)

        val match = candidates.firstOrNull { info ->
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            label.contains(appName, ignoreCase = true) ||
                (alias != null && (label.contains(alias, ignoreCase = true) || pkg.contains(alias, ignoreCase = true)))
        } ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: return false

        context.startActivity(launchIntent)
        return true
    }
}
