package com.iris.assistant.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.iris.assistant.agent.AgentState
import com.iris.assistant.ui.theme.*
import com.iris.assistant.ui.viewmodel.IrisViewModel
import com.iris.assistant.util.SingleShotSpeechRecognizer

/**
 * The primary, voice-first experience: one big responsive orb instead of a
 * chat log. Tap once to talk; after IRIS answers, if conversation mode is
 * on, it automatically starts listening for your next turn — a real
 * back-and-forth instead of tap-talk-tap-talk-tap.
 */
@Composable
fun VoiceScreen(navController: NavHostController? = null, viewModel: IrisViewModel = viewModel()) {
    val agentState by viewModel.agentState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val ctx = LocalContext.current

    var conversationMode by remember { mutableStateOf(true) }
    var lastHeard by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf<String?>(null) }

    fun beginListening() {
        lastError = null
        com.iris.assistant.agent.AgentStateHolder.setState(AgentState.LISTENING)
        SingleShotSpeechRecognizer.listenOnce(
            ctx,
            onResult = { text ->
                lastHeard = text
                viewModel.sendMessage(text)
            },
            onError = { message ->
                lastError = message
                // Routed through showSystemMessage so the error is also
                // spoken aloud and the conversation loop below only listens
                // again once that finishes — not before, which would talk
                // over it.
                viewModel.showSystemMessage(message)
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) beginListening() }

    fun onOrbTapped() {
        if (agentState == AgentState.LISTENING) return // already listening, ignore double-tap
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            beginListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // The natural "conversation" loop: once IRIS finishes speaking (state
    // returns to IDLE after having answered), automatically listen again —
    // unless the person hasn't actually started talking yet at all.
    var hasStartedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(agentState) {
        if (agentState == AgentState.LISTENING) hasStartedOnce = true
        if (agentState == AgentState.IDLE && hasStartedOnce && conversationMode) {
            onOrbTapped()
        }
    }

    val lastAssistantReply = messages.lastOrNull { it.role == "assistant" }?.content

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onOrbTapped() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Orb(state = agentState)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when (agentState) {
                    AgentState.IDLE -> "برای صحبت لمس کن"
                    AgentState.LISTENING -> "در حال گوش دادن..."
                    AgentState.THINKING -> "در حال فکر کردن..."
                    AgentState.EXECUTING -> "در حال انجام کار..."
                    AgentState.SPEAKING -> "..."
                },
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = agentState == AgentState.LISTENING && lastHeard.isNotBlank()) {
                Text(lastHeard, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
            AnimatedVisibility(visible = (agentState == AgentState.SPEAKING) && lastAssistantReply != null) {
                Text(
                    lastAssistantReply ?: "",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            lastError?.let {
                Text("🎙 $it", color = Color(0xFFFF5050), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, horizontal = 24.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مکالمه‌ی پیوسته", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = conversationMode, onCheckedChange = { conversationMode = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (navController != null) {
                TextButton(onClick = { navController.navigate("chat") }) {
                    Text("حالت متنی / تاریخچه", color = IrisTeal, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Orb(state: AgentState) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (state == AgentState.LISTENING || state == AgentState.SPEAKING) 1.15f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == AgentState.LISTENING) 700 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing)),
        label = "rotation"
    )

    val colors = when (state) {
        AgentState.IDLE -> listOf(Color(0xFF00D4C8), Color(0xFF008B8B))
        AgentState.LISTENING -> listOf(Color(0xFF00F0E0), Color(0xFF00D4C8))
        AgentState.THINKING -> listOf(Color(0xFF7C4DFF), Color(0xFF00D4C8))
        AgentState.EXECUTING -> listOf(Color(0xFFFFC107), Color(0xFF00D4C8))
        AgentState.SPEAKING -> listOf(Color(0xFF00D4C8), Color(0xFF00F0E0))
    }

    Box(
        modifier = Modifier
            .size(180.dp)
            .scale(pulse)
            .let { if (state == AgentState.THINKING) it.rotate(rotation) else it }
            .background(brush = Brush.sweepGradient(colors), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(Black, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(brush = Brush.radialGradient(colors.map { it.copy(alpha = 0.35f) }), shape = CircleShape)
        )
    }
}
