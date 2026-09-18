package com.iris.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.iris.assistant.agent.ConfirmationBridge
import com.iris.assistant.agent.PermissionBridge
import com.iris.assistant.service.VoiceWakeService
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.ui.screen.MainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Permissions are now requested JUST-IN-TIME by whichever Tool
    // actually needs one (via PermissionBridge), not all at once on
    // first launch — this activity only relays the system dialog when a
    // Tool asks for something.
    private val requestSinglePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> PermissionBridge.resolve(granted) }

    // BUG FIXED: RECORD_AUDIO was declared in the manifest but NEVER actually
    // requested at runtime anywhere in the app, so on any real device it stayed
    // permanently denied and the wake-word / voice features could never hear
    // anything. This launcher requests it specifically before the wake service
    // is started.
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startWakeService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        setContent {
            IrisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen()
                        PermissionRelay()
                        ConfirmationDialogHost()
                    }
                }
            }
        }

        // Wake-word is real now (see VoiceWakeService) — but repeatedly
        // restarting speech recognition in the background is what was
        // keeping the mic indicator on constantly and draining battery, so
        // this now defaults to OFF and only runs if the user explicitly
        // turns it on from the Permissions screen. Voice input is otherwise
        // available via the tap-to-talk mic button in the chat screen.
        lifecycleScope.launch {
            val pref = IrisApp.instance.database.preferenceDao().get("wake_word_enabled")
            if (pref != null && pref.value == "true") {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startWakeService()
                } else {
                    requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    /** Watches for a Tool asking for a permission and forwards it to the real system dialog. */
    @androidx.compose.runtime.Composable
    private fun PermissionRelay() {
        val pending by PermissionBridge.permission.collectAsState()
        LaunchedEffect(pending) {
            pending?.let { requestSinglePermission.launch(it) }
        }
    }

    /** Renders the sensitive-action confirmation dialog whenever AgentCore asks for one. */
    @androidx.compose.runtime.Composable
    private fun ConfirmationDialogHost() {
        val prompt by ConfirmationBridge.prompt.collectAsState()
        prompt?.let { text ->
            AlertDialog(
                onDismissRequest = { ConfirmationBridge.resolve(false) },
                title = { Text("تایید IRIS") },
                text = { Text(text) },
                confirmButton = {
                    TextButton(onClick = { ConfirmationBridge.resolve(true) }) { Text("تایید") }
                },
                dismissButton = {
                    TextButton(onClick = { ConfirmationBridge.resolve(false) }) { Text("لغو") }
                }
            )
        }
    }

    fun startWakeService() {
        val intent = Intent(this, VoiceWakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IrisApp.instance.ttsHelper.shutdown()
    }
}
