package com.iris.assistant.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.iris.assistant.IrisApp
import com.iris.assistant.data.db.PreferenceEntity
import com.iris.assistant.service.VoiceWakeService
import com.iris.assistant.ui.theme.*
import kotlinx.coroutines.launch

data class PermissionItem(val icon: String, val title: String, val desc: String, var enabled: Boolean)

/**
 * The switches below were previously pure local UI state — flipping
 * them did nothing at all. The Wake Word switch is now wired to a real
 * stored preference and actually starts/stops VoiceWakeService; the
 * others remain informational until each is connected the same way.
 */
@Composable
fun PermissionsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = IrisApp.instance.database

    var wakeWordEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val pref = db.preferenceDao().get("wake_word_enabled")
        wakeWordEnabled = pref != null && pref.value == "true"
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wakeWordEnabled = true
            scope.launch { db.preferenceDao().set(PreferenceEntity("wake_word_enabled", "true")) }
            ctx.startForegroundService(Intent(ctx, VoiceWakeService::class.java))
        }
    }

    val permissions = remember {
        mutableStateListOf(
            PermissionItem("🔦", "دوربین", "لازم برای کنترل چراغ‌قوه", true),
            PermissionItem("👥", "مخاطبین", "جستجوی مخاطب و برقراری تماس", true),
            PermissionItem("💬", "پیامک", "باز کردن اپ پیام‌رسان با متن آماده", true),
            PermissionItem("📞", "تماس تلفنی", "برقراری تماس", true),
            PermissionItem("📍", "موقعیت مکانی", "آب‌وهوا و موقعیت", false),
            PermissionItem("🖐", "دسترسی‌پذیری", "کلیک/اسکرول در سایر اپ‌ها", true)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("مجوزها و قابلیت‌ها", color = Color.White, fontSize = 20.sp)
        Text(
            "این‌ها به‌صورت Just-in-Time درخواست می‌شوند — یعنی فقط وقتی یک دستور واقعاً بهشان نیاز دارد",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)
        )

        // Real, working toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1A00D4C8)),
                contentAlignment = Alignment.Center
            ) { Text("🎙", fontSize = 20.sp) }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("بیدارشدن با گفتن IRIS", color = TextPrimary, fontSize = 15.sp)
                Text("سرویس گوش‌دادن پس‌زمینه — واقعاً روشن/خاموش می‌کند", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            wakeWordEnabled = true
                            scope.launch { db.preferenceDao().set(PreferenceEntity("wake_word_enabled", "true")) }
                            ctx.startForegroundService(Intent(ctx, VoiceWakeService::class.java))
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        wakeWordEnabled = false
                        scope.launch { db.preferenceDao().set(PreferenceEntity("wake_word_enabled", "false")) }
                        ctx.stopService(Intent(ctx, VoiceWakeService::class.java))
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        permissions.forEach { perm ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1A00D4C8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(perm.icon, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(perm.title, color = TextPrimary, fontSize = 15.sp)
                    Text(perm.desc, color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = perm.enabled,
                    onCheckedChange = { perm.enabled = it }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
