package com.tiss.replaycam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.ui.theme.*

@Composable
fun HomeScreen(
    navController: NavController,
    clipStore: ClipStore,
    onOpenCamera: () -> Unit
) {
    val clips by clipStore.clips.collectAsState()
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(NavyBlue, DeepTeal)))
    ) {
        if (isLandscape) {
            LandscapeLayout(
                clipCount = clips.size,
                onOpenCamera = onOpenCamera,
                onOpenLibrary = { navController.navigate("library") },
                onOpenPose = { navController.navigate("pose") },
                onOpenSettings = { navController.navigate("settings") }
            )
        } else {
            PortraitLayout(
                clipCount = clips.size,
                onOpenCamera = onOpenCamera,
                onOpenLibrary = { navController.navigate("library") },
                onOpenPose = { navController.navigate("pose") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    clipCount: Int,
    onOpenCamera: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ReplayCam",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        MainCard(
            icon = "📷",
            title = "延遲錄影",
            subtitle = "設定延遲秒數，即時觀看動作回放",
            gradient = Brush.linearGradient(listOf(CardBlueStart, CardBlueEnd)),
            onClick = onOpenCamera
        )
        MainCard(
            icon = "🏃",
            title = "骨架分析",
            subtitle = "選取影片，分析關節角度與動作數據",
            gradient = Brush.linearGradient(listOf(CardPurpleStart, CardPurpleEnd)),
            onClick = onOpenPose
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SmallCard(
                icon = "📅",
                title = "日期記錄",
                subtitle = if (clipCount == 0) "尚無片段" else "$clipCount 個片段",
                modifier = Modifier.weight(1f),
                onClick = onOpenLibrary
            )
            SmallCard(
                icon = "⚙️",
                title = "設定",
                subtitle = "延遲與偏好",
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TissLogoBar()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun LandscapeLayout(
    clipCount: Int,
    onOpenCamera: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ReplayCam", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TissLogoBar(compact = true)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MainCard(
                icon = "📷",
                title = "延遲錄影",
                subtitle = "設定延遲秒數，即時觀看動作回放",
                gradient = Brush.linearGradient(listOf(CardBlueStart, CardBlueEnd)),
                onClick = onOpenCamera,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainCard(
                    icon = "🏃",
                    title = "骨架分析",
                    subtitle = "分析關節角度與動作數據",
                    gradient = Brush.linearGradient(listOf(CardPurpleStart, CardPurpleEnd)),
                    onClick = onOpenPose,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallCard(
                        icon = "📅",
                        title = "日期記錄",
                        subtitle = if (clipCount == 0) "尚無片段" else "$clipCount 個片段",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onOpenLibrary
                    )
                    SmallCard(
                        icon = "⚙️",
                        title = "設定",
                        subtitle = "延遲與偏好",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun MainCard(
    icon: String,
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 28.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun SmallCard(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(GlassWhite)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 20.sp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun TissLogoBar(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .let { if (compact) it else it.fillMaxWidth() }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(horizontal = 20.dp, vertical = if (compact) 6.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TISS 國家運動科學中心",
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = NavyBlue
        )
    }
}
