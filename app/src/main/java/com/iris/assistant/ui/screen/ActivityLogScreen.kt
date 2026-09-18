package com.iris.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.assistant.ui.theme.*
import com.iris.assistant.ui.viewmodel.IrisViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityLogScreen(viewModel: IrisViewModel = viewModel()) {
    val logs by viewModel.activityLogs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(20.dp)
    ) {
        Text("گزارش فعالیت", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(logs) { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (log.isSuccess) Color(0x1A00D4C8) else Color(0x1AFF5050)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                log.action.contains("تماس") -> "📞"
                                log.action.contains("پیام") -> "💬"
                                log.action.contains("آب") -> "🌤"
                                log.action.contains("عکس") -> "📷"
                                log.action.contains("آلارم") -> "⏰"
                                else -> "📱"
                            },
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(log.action, color = TextPrimary, fontSize = 15.sp)
                        Text(
                            SimpleDateFormat("HH:mm", Locale("fa")).format(Date(log.timestamp)),
                            color = TextSecondary, fontSize = 12.sp
                        )
                    }
                    Text(
                        if (log.isSuccess) "✓" else "✕",
                        color = if (log.isSuccess) Success else Error,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
