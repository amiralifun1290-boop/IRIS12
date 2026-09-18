package com.iris.assistant.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.config.UserProfileStore
import com.iris.assistant.ui.theme.*

/**
 * This is what actually makes IRIS feel personal rather than generic — the
 * name/nickname/style entered here is injected straight into the AI's
 * system prompt on every request (see OpenAiIntentModel.buildSystemPrompt),
 * so it's not just a settings screen nobody reads; it actively changes how
 * IRIS talks to you.
 */
@Composable
fun ProfileScreen() {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(UserProfileStore.getName(ctx)) }
    var nickname by remember { mutableStateOf(UserProfileStore.getNickname(ctx)) }
    var style by remember { mutableStateOf(UserProfileStore.getResponseStyle(ctx)) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("درباره‌ی من", color = Color.White, fontSize = 20.sp)
        Text(
            "این اطلاعات مستقیم به هوش مصنوعی داده می‌شه تا بشناستت و همون‌طوری که می‌خوای باهات صحبت کنه.",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)
        )

        Text("اسمت", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saved = false },
            placeholder = { Text("مثلاً علی رضایی", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))
        Text("چی صدات بزنه", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it; saved = false },
            placeholder = { Text("مثلاً علی، یا رفیق، یا هر چی دوست داری", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))
        Text("چطور جوابت بده", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = style,
            onValueChange = { style = it; saved = false },
            placeholder = { Text("مثلاً: خیلی خودمونی و کوتاه، یا: رسمی و دقیق", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                UserProfileStore.setName(ctx, name)
                UserProfileStore.setNickname(ctx, nickname)
                UserProfileStore.setResponseStyle(ctx, style)
                saved = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = IrisTeal)
        ) { Text("ذخیره", color = Color.Black) }

        if (saved) {
            Spacer(Modifier.height(12.dp))
            Text("ذخیره شد ✅", color = IrisTeal, fontSize = 13.sp)
        }
    }
}
