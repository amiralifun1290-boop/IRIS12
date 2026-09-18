package com.iris.assistant.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.assistant.agent.AgentState
import com.iris.assistant.ui.theme.*
import com.iris.assistant.ui.viewmodel.IrisViewModel
import com.iris.assistant.util.SingleShotSpeechRecognizer
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: IrisViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    fun beginListening() {
        isListening = true
        com.iris.assistant.agent.AgentStateHolder.setState(com.iris.assistant.agent.AgentState.LISTENING)
        SingleShotSpeechRecognizer.listenOnce(
            ctx,
            onResult = { text ->
                isListening = false
                viewModel.sendMessage(text)
            },
            onError = { message ->
                isListening = false
                com.iris.assistant.agent.AgentStateHolder.setState(com.iris.assistant.agent.AgentState.IDLE)
                viewModel.showSystemMessage("🎙 $message")
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) beginListening() }

    fun onMicTapped() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            beginListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        val stateLabel = when (agentState) {
            AgentState.IDLE -> null
            AgentState.LISTENING -> "🎙 در حال گوش دادن"
            AgentState.THINKING -> "🤔 در حال فکر کردن"
            AgentState.EXECUTING -> "⚙️ در حال اجرا"
            AgentState.SPEAKING -> "🔊 در حال پاسخ"
        }
        stateLabel?.let {
            Text(
                it, color = IrisTeal, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(GlassBg).padding(8.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        text = msg.content,
                        modifier = Modifier
                            .background(
                                if (isUser) Brush.linearGradient(listOf(Color(0x2600D4C8), Color(0x1A008B8B)))
                                else Brush.linearGradient(listOf(GlassBg, GlassBg)),
                                shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                                else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                            )
                            .border(1.dp, if (isUser) Color(0x4000D4C8) else GlassBorder, shape = RoundedCornerShape(20.dp))
                            .padding(14.dp, 12.dp),
                        color = if (isUser) Color(0xFFE0F7F6) else TextPrimary,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(GlassBg, RoundedCornerShape(28.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("پیام بنویسید...", color = TextSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMicTapped() }) {
                Text(if (isListening) "🔴" else "🎙", fontSize = 20.sp)
            }
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input)
                        input = ""
                        scope.launch { listState.animateScrollToItem(messages.size) }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = IrisTeal),
                modifier = Modifier.size(48.dp)
            ) {
                Text("➤", color = Black, fontSize = 18.sp)
            }
        }
    }
}
