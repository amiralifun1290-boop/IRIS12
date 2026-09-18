package com.iris.assistant.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

/**
 * FIXED: this class was fully implemented but never actually called anywhere
 * except shutdown() — IRIS never spoke a single reply out loud. Now wired
 * into the reply pipeline (see IrisViewModel), with an onDone callback so
 * the UI knows exactly when speech really finishes (for the "keep listening
 * after I answer" conversation flow), instead of guessing with a timer.
 */
class TextToSpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = mutableListOf<Pair<String, (() -> Unit)?>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("fa", "IR")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        doneCallbacks.remove(utteranceId)?.invoke()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        doneCallbacks.remove(utteranceId)?.invoke()
                    }
                })
                isReady = true
                pending.forEach { (text, onDone) -> speakInternal(text, onDone) }
                pending.clear()
            }
        }
    }

    private val doneCallbacks = mutableMapOf<String, () -> Unit>()

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (isReady) speakInternal(text, onDone) else pending.add(text to onDone)
    }

    fun stop() {
        tts?.stop()
    }

    private fun speakInternal(text: String, onDone: (() -> Unit)?) {
        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) doneCallbacks[utteranceId] = onDone
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
