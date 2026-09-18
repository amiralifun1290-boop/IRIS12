package com.iris.assistant.util

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object TranslatorHelper {
    suspend fun translateFaToEn(text: String): String {
        return translate(text, TranslateLanguage.PERSIAN, TranslateLanguage.ENGLISH)
    }

    suspend fun translateEnToFa(text: String): String {
        return translate(text, TranslateLanguage.ENGLISH, TranslateLanguage.PERSIAN)
    }

    private suspend fun translate(text: String, from: String, to: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()
        val translator = Translation.getClient(options)
        return try {
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resume("خطا در ترجمه") }
                    }
                    .addOnFailureListener { cont.resume("خطا در دانلود مدل") }
            }
        } finally {
            translator.close()
        }
    }
}
