package com.tiss.replaycam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tiss.replaycam.BuildConfig
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    clipStore: ClipStore,
    defaultDelay: Double,
    onDelayChange: (Double) -> Unit,
    defaultFps: Int,
    onFpsChange: (Int) -> Unit,
    defaultLens: Int,
    onLensChange: (Int) -> Unit
) {
    val clips by clipStore.clips.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val totalBytes = remember(clips) { clipStore.totalSizeBytes() }
    val totalMB = totalBytes / 1024f / 1024f

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除所有片段") },
            text = { Text("確定要刪除所有 ${clips.size} 個片段嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = { clipStore.clearAll(); showClearDialog = false }) {
                    Text("清除", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(NavyBlue, DeepTeal)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("設定", color = Color.White, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Text("‹", fontSize = 24.sp, color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsCard {
                    SettingsSliderRow(
                        label = "預設延遲",
                        value = defaultDelay.toFloat(),
                        valueLabel = "${defaultDelay.toInt()} 秒",
                        range = 1f..30f,
                        hint = "開啟拍攝時預設的延遲秒數",
                        onValueChange = { onDelayChange(it.toDouble()) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSegmentRow(
                        label = "錄影幀率",
                        hint = "標準幀率，最多可儲存 35 秒",
                        options = listOf(30, 60, 120),
                        labels = listOf("30 fps", "60 fps", "120 fps"),
                        selected = defaultFps,
                        onSelect = { onFpsChange(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSegmentRow(
                        label = "預設鏡頭",
                        hint = "開啟拍攝時預設使用的鏡頭",
                        options = listOf(0, 1),
                        labels = listOf("後鏡頭", "前鏡頭"),
                        selected = defaultLens,
                        onSelect = { onLensChange(it) }
                    )
                }

                SettingsCard {
                    SettingsInfoRow(label = "片段數量", value = "${clips.size} 個")
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsInfoRow(label = "佔用空間", value = "%.1f MB".format(totalMB))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🗑", fontSize = 16.sp)
                            TextButton(onClick = { showClearDialog = true }) {
                                Text("清除所有片段", color = DangerRed, fontSize = 15.sp)
                            }
                        }
                    }
                }

                SettingsCard {
                    SettingsInfoRow(label = "版本", value = BuildConfig.VERSION_NAME)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsInfoRow(label = "建置", value = BuildConfig.VERSION_CODE.toString())
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        content = content
    )
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    hint: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⏱", fontSize = 14.sp)
                Text(label, fontSize = 15.sp, color = Color.White)
            }
            Text(valueLabel, fontSize = 14.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Text(hint, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun <T> SettingsSegmentRow(
    label: String,
    hint: String,
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (label.contains("幀率")) "🎬" else "📷", fontSize = 14.sp)
            Text(label, fontSize = 15.sp, color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            options.forEachIndexed { idx, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected == option) Color.White else Color.Transparent)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[idx],
                        fontSize = 13.sp,
                        color = if (selected == option) NavyBlue else Color.White,
                        fontWeight = if (selected == option) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = Color.White)
        Text(value, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
