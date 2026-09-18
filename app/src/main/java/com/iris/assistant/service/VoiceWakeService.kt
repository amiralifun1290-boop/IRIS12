package com.iris.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.iris.assistant.MainActivity
import com.iris.assistant.R
import kotlinx.coroutines.*
import java.util.Locale

class VoiceWakeService : Service() {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private val wakeWords = listOf("iris", "آیریس")

    private val channelId = "iris_wake_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        startWakeWordDetection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "IRIS Voice Wake",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IRIS فعال است")
            .setContentText("در حال گوش دادن...")
            .setSmallIcon(R.drawable.ic_iris)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    /**
     * Real wake-word detection: repeatedly runs Android's SpeechRecognizer
     * and checks the transcript for "IRIS"/"آیریس".
     *
     * HONEST LIMITATION: Android's SpeechRecognizer is built for short
     * request/response sessions, not always-on listening, so this
     * restart-loop approach uses more battery/data than a dedicated
     * offline wake-word engine (e.g. Porcupine) would — but unlike the
     * previous version, it now actually listens for the word "IRIS"
     * instead of triggering on any loud noise.
     */
    private fun startWakeWordDetection() {
        mainScope.launch {
            if (!SpeechRecognizer.isRecognitionAvailable(this@VoiceWakeService)) {
                stopSelf()
                return@launch
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@VoiceWakeService).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { restartListening() }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null && wakeWords.any { w -> matches.any { it.contains(w, ignoreCase = true) } }) {
                            triggerOverlay()
                        }
                        restartListening()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            restartListening()
        }
    }

    private fun restartListening() {
        // A small pause avoids a tight restart loop (rapid-fire
        // startListening calls were a likely source of the recognizer
        // throwing "busy"/client errors back-to-back and burning battery).
        mainScope.launch {
            delay(400)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString().replace('_', '-'))
            }
            try {
                speechRecognizer?.startListening(intent)
            } catch (_: Exception) {
                // recognizer busy this cycle — onError/onResults will trigger the next restart
            }
        }
    }

    private fun triggerOverlay() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            val wakeLock = pm.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_DIM_WAKE_LOCK,
                "iris:wake"
            )
            wakeLock.acquire(3000)
            wakeLock.release()
        }
        FloatingOverlayService.showOverlay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        mainScope.cancel()
        super.onDestroy()
    }
}
