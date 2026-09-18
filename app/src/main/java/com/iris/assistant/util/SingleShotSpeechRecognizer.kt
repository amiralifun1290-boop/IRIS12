package com.iris.assistant.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Runs SpeechRecognizer for exactly one request/response cycle — the mic
 * indicator turns on only while actively listening for this one utterance,
 * then off. This is the reliable way to do voice input; the always-on
 * background wake-word service is a separate, optional feature.
 *
 * FIXED: errors used to just silently reset a flag with no explanation.
 * Many phones without Google Play Services (common on some carriers/ROMs)
 * have no speech recognition service at all, so onError now always reports
 * a real Persian reason instead of just going quiet.
 */
object SingleShotSpeechRecognizer {
    fun listenOnce(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("تشخیص گفتار روی این گوشی در دسترس نیست (نیاز به سرویس‌های گوگل داره). فعلاً از تایپ استفاده کن.")
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                recognizer.destroy()
                onError(describeError(error))
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                recognizer.destroy()
                if (!text.isNullOrBlank()) onResult(text) else onError("چیزی نشنیدم، دوباره امتحان کن.")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString().replace('_', '-'))
        }
        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            recognizer.destroy()
            onError("تشخیص گفتار در دسترس نیست: ${e.message}")
        }
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "چیزی نشنیدم، دوباره امتحان کن."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "تشخیص گفتار نیاز به اینترنت داره و الان وصل نیست."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "مجوز میکروفون داده نشده."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "تشخیص گفتار الان مشغوله، یه لحظه صبر کن و دوباره امتحان کن."
        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_AUDIO ->
            "تشخیص گفتار روی این گوشی در دسترس نیست. فعلاً از تایپ استفاده کن."
        else ->
            "تشخیص گفتار جواب نداد. فعلاً از تایپ استفاده کن."
    }
}

