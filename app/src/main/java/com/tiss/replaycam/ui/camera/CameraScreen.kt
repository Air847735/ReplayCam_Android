package com.tiss.replaycam.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tiss.replaycam.camera.CameraManager
import com.tiss.replaycam.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    cameraManager: CameraManager,
    defaultDelay: Double,
    defaultFps: Int,
    defaultLens: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedDelay by remember { mutableStateOf(defaultDelay.toFloat()) }
    var saveDuration by remember { mutableStateOf(10f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var pipOffsetX by remember { mutableStateOf(0f) }
    var pipOffsetY by remember { mutableStateOf(0f) }
    var pipScale by remember { mutableStateOf(1f) }

    val realtimeBitmap by cameraManager.realtimeBitmap.collectAsState()
    val delayedBitmap by cameraManager.delayedBitmap.collectAsState()
    val bufferDuration by cameraManager.bufferDuration.collectAsState()
    val isSaving by cameraManager.isSaving.collectAsState()
    val showSuccess by cameraManager.showSuccess.collectAsState()
    val supportedFps by cameraManager.supportedFps.collectAsState()
    var selectedFps by remember { mutableStateOf(defaultFps) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        cameraManager.delaySeconds = defaultDelay
        cameraManager.targetFps = defaultFps
        cameraManager.lensFacing = defaultLens
    }

    LaunchedEffect(selectedDelay) {
        cameraManager.delaySeconds = selectedDelay.toDouble()
    }

    val saveRange = when (selectedFps) {
        120 -> 3f..20f
        60 -> 3f..30f
        else -> 3f..35f
    }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraManager.startCamera(lifecycleOwner, previewView)
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { cameraManager.dismissSuccess() },
            title = { Text("儲存成功") },
            text = { Text("影片已儲存到片段庫") },
            confirmButton = {
                TextButton(onClick = { cameraManager.dismissSuccess() }) { Text("確定") }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("儲存片段") },
            text = {
                Column {
                    Text("儲存最近 ${saveDuration.roundToInt()} 秒")
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = saveDuration,
                        onValueChange = { saveDuration = it },
                        valueRange = saveRange,
                        steps = (saveRange.endInclusive - saveRange.start).toInt() - 1
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    cameraManager.saveRecentFrames(saveDuration.toDouble()) {}
                }) { Text("儲存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // PreviewView 縮成 0dp 隱藏，CameraX 仍需要它來綁定 Preview use case
        AndroidView(
            factory = { previewView },
            modifier = Modifier.size(0.dp)
        )

        // 延遲畫面：主畫面（N秒前的畫面）
        delayedBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (cameraManager.isMirrored) scaleX = -1f }
                    .clickable { controlsVisible = !controlsVisible },
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { controlsVisible = !controlsVisible },
            contentAlignment = Alignment.Center
        ) {
            val remaining = (selectedDelay - bufferDuration).coerceAtLeast(0.0)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${remaining.toInt() + 1}",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "準備延遲畫面中...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                LinearProgressIndicator(
                    progress = { (bufferDuration / selectedDelay).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.width(160.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 60.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Text("‹", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(
            visible = !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "⏱ ${selectedDelay.roundToInt()}s",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        realtimeBitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .align(Alignment.TopEnd)
                    .padding(top = 70.dp, end = 12.dp)
                    .size(width = (80 * pipScale).dp, height = (120 * pipScale).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            pipOffsetX += pan.x
                            pipOffsetY += pan.y
                            pipScale = (pipScale * zoom).coerceIn(0.8f, 3f)
                        }
                    }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "即時畫面",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { if (cameraManager.isMirrored) scaleX = -1f },
                    contentScale = ContentScale.Crop
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlPanel(
                selectedDelay = selectedDelay,
                onDelayChange = { selectedDelay = it },
                saveDuration = saveDuration,
                onSaveDurationChange = { saveDuration = it },
                saveRange = saveRange,
                selectedFps = selectedFps,
                supportedFps = supportedFps,
                isMirrored = cameraManager.isMirrored,
                isSaving = isSaving,
                onFpsChange = { fps ->
                    selectedFps = fps
                    cameraManager.applyFps(fps, lifecycleOwner, previewView)
                },
                onMirrorToggle = { cameraManager.isMirrored = !cameraManager.isMirrored },
                onSwitchCamera = { cameraManager.switchCamera(lifecycleOwner, previewView) },
                onSave = { showSaveDialog = true }
            )
        }
    }
}

@Composable
private fun ControlPanel(
    selectedDelay: Float,
    onDelayChange: (Float) -> Unit,
    saveDuration: Float,
    onSaveDurationChange: (Float) -> Unit,
    saveRange: ClosedFloatingPointRange<Float>,
    selectedFps: Int,
    supportedFps: List<Int>,
    isMirrored: Boolean,
    isSaving: Boolean,
    onFpsChange: (Int) -> Unit,
    onMirrorToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = Color(0xD90A0F1A)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("延遲", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Slider(
                    value = selectedDelay,
                    onValueChange = onDelayChange,
                    valueRange = 1f..30f,
                    modifier = Modifier.weight(1f)
                )
                Text("${selectedDelay.roundToInt()}s", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("儲存", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Slider(
                    value = saveDuration,
                    onValueChange = onSaveDurationChange,
                    valueRange = saveRange,
                    modifier = Modifier.weight(1f)
                )
                Text("${saveDuration.roundToInt()}s", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(30, 60, 120).forEach { fps ->
                    FilterChip(
                        selected = selectedFps == fps,
                        onClick = { onFpsChange(fps) },
                        label = { Text("${fps}fps", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Text("🔄", fontSize = 16.sp)
                }
                IconButton(
                    onClick = onMirrorToggle,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isMirrored) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Text("⇄", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("儲存中...", color = Color.White)
                    } else {
                        Text("⬇ 儲存", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
