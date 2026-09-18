package com.iris.assistant.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.ui.theme.*

@Composable
fun ActionsScreen() {
    val ctx = LocalContext.current
    val actions = listOf(
        "📞" to "تماس با کسی",
        "💬" to "ارسال پیام",
        "🌤" to "بررسی آب‌وهوا",
        "📷" to "گرفتن عکس",
        "📱" to "باز کردن اپ",
        "⏰" to "تنظیم آلارم"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(20.dp)
    ) {
        Text(
            "اقدامات سریع",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        actions.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEach { (icon, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .clickable {
                                Toast.makeText(ctx, "$label انتخاب شد", Toast.LENGTH_SHORT).show()
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon, fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(label, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
