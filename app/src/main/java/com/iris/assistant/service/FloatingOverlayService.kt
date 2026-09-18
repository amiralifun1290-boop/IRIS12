package com.iris.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.iris.assistant.IrisApp
import com.iris.assistant.MainActivity
import com.iris.assistant.R
import com.iris.assistant.agent.AgentBootstrap
import com.iris.assistant.agent.AgentState
import com.iris.assistant.agent.AgentStateHolder
import com.iris.assistant.agent.ConfirmationBridge
import com.iris.assistant.data.db.ActivityLogEntity
import com.iris.assistant.ui.theme.IrisTheme
import kotlinx.coroutines.*

class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val agentCore by lazy {
        AgentBootstrap.buildAgentCore(this) { logLine ->
            serviceScope.launch {
                IrisApp.instance.database.activityLogDao().insert(ActivityLogEntity(action = logLine, isSuccess = true))
            }
        }
    }

    companion object {
        fun showOverlay(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = "SHOW_OVERLAY"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, buildNotification())
        if (intent?.action == "SHOW_OVERLAY") {
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "iris_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "IRIS Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IRIS")
            .setContentText("دستیار فعال")
            .setSmallIcon(R.drawable.ic_iris)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                IrisTheme {
                    OverlayContent(
                        onDismiss = { hideOverlay() },
                        onCommand = { cmd -> processCommand(cmd) }
                    )
                }
            }
        }

        windowManager?.addView(overlayView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { restartListening() }
                override fun onResults(results: android.os.Bundle?) {
                    val cmd = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    cmd?.let { processCommand(it) }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    partial?.let { /* update live text */ }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
        restartListening()
    }

    private fun restartListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(command: String) {
        serviceScope.launch {
            val result = agentCore.handleRequest(command)
            IrisApp.instance.ttsHelper.speak(result)
            delay(3500)
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        overlayView?.let {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            windowManager?.removeView(it)
            overlayView = null
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayContent(onDismiss: () -> Unit, onCommand: (String) -> Unit) {
    val agentState by AgentStateHolder.state.collectAsState()
    val lastAction by AgentStateHolder.lastAction.collectAsState()
    val confirmPrompt by ConfirmationBridge.prompt.collectAsState()

    val statusText = when (agentState) {
        AgentState.IDLE -> "IRIS آماده است"
        AgentState.LISTENING -> "در حال گوش دادن..."
        AgentState.THINKING -> "در حال فکر کردن..."
        AgentState.EXECUTING -> "در حال اجرا: $lastAction"
        AgentState.SPEAKING -> "در حال پاسخ..."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6050505)),
        contentAlignment = Alignment.Center
    ) {
        if (confirmPrompt != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(24.dp)
            ) {
                Text("تایید IRIS", color = Color(0xFF00D4C8), fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(confirmPrompt ?: "", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row {
                    Button(onClick = { ConfirmationBridge.resolve(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF))) {
                        Text("لغو", color = Color(0xFF888888))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { ConfirmationBridge.resolve(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4C8))) {
                        Text("تایید", color = Color.Black)
                    }
                }
            }
            return@Box
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                repeat(3) { i ->
                    val size = 200 - i * 40
                    Box(
                        modifier = Modifier
                            .size(size.dp)
                            .clip(CircleShape)
                            .background(Color(0x2600D4C8))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00D4C8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎙", fontSize = 32.sp)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(statusText, color = Color(0xFF00D4C8), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x14FFFFFF))
            ) {
                Text("لغو", color = Color(0xFF888888))
            }
        }
    }
}
