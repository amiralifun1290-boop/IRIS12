package com.iris.assistant.config

import android.content.Context

/**
 * Simple on-device profile so IRIS knows who it's talking to and how they
 * want to be addressed/answered. Injected into the system prompt on every
 * request (see OpenAiIntentModel) so the personalization actually affects
 * behavior, not just a settings screen nobody reads.
 */
object UserProfileStore {
    private const val PREFS_NAME = "iris_profile_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_NICKNAME = "user_nickname"
    private const val KEY_STYLE = "response_style"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getName(context: Context): String = prefs(context).getString(KEY_NAME, "") ?: ""
    fun setName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_NAME, value.trim()).apply()
    }

    /** What IRIS should call the person — may differ from their full name. */
    fun getNickname(context: Context): String = prefs(context).getString(KEY_NICKNAME, "") ?: ""
    fun setNickname(context: Context, value: String) {
        prefs(context).edit().putString(KEY_NICKNAME, value.trim()).apply()
    }

    /** Free-text instructions like "خیلی خودمونی و کوتاه جواب بده" or "رسمی صحبت کن". */
    fun getResponseStyle(context: Context): String = prefs(context).getString(KEY_STYLE, "") ?: ""
    fun setResponseStyle(context: Context, value: String) {
        prefs(context).edit().putString(KEY_STYLE, value.trim()).apply()
    }

    fun hasProfile(context: Context): Boolean =
        getName(context).isNotBlank() || getNickname(context).isNotBlank()

    /** A short block to prepend to the AI system prompt — empty if nothing is set. */
    fun buildProfileBlock(context: Context): String {
        val name = getName(context)
        val nickname = getNickname(context)
        val style = getResponseStyle(context)
        if (name.isBlank() && nickname.isBlank() && style.isBlank()) return ""

        val lines = mutableListOf<String>()
        if (name.isNotBlank()) lines += "اسم کاربر: $name"
        if (nickname.isNotBlank()) lines += "کاربر رو با این اسم صدا بزن: $nickname"
        if (style.isNotBlank()) lines += "سبک جواب دادن که کاربر خواسته: $style"
        return "اطلاعات کاربر:\n" + lines.joinToString("\n")
    }
}
