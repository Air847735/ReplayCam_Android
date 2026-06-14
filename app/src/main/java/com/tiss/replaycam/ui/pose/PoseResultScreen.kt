package com.tiss.replaycam.ui.pose

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tiss.replaycam.pose.*
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PoseResultScreen(
    clipId: String,
    navController: NavController,
    clipStore: ClipStore
) {
    val context = LocalContext.current
    val clips by clipStore.clips.collectAsState()
    val clip = clips.find { it.id == clipId } ?: return

    var isAnalyzing by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    var poseResults by remember { mutableStateOf<List<PoseResult>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedJoint by remember { mutableStateOf<JointAngle?>(null) }

    LaunchedEffect(clip.path) {
        withContext(Dispatchers.IO) {
            val detector = PoseDetector(context)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(clip.path)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameCount = 30
            val results = mutableListOf<PoseResult>()
            val thumbs = mutableListOf<Bitmap>()

            for (i in 0 until frameCount) {
                val timeUs = durationMs * 1000L * i / frameCount
                val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    thumbs.add(bmp)
                    val result = detector.detect(bmp)
                    if (result != null) results.add(result.copy(timestampMs = timeUs / 1000))
                }
                withContext(Dispatchers.Main) { progress = (i + 1f) / frameCount }
            }
            retriever.release()
            detector.close()
            withContext(Dispatchers.Main) {
                poseResults = results
                thumbnails = thumbs
                isAnalyzing = false
            }
        }
    }

    if (isAnalyzing) {
        LoadingScreen(progress = progress, onDismiss = { navController.popBackStack() })
        return
    }

    val currentFrame = (thumbnails.size * 0.5f).toInt().coerceIn(0, thumbnails.lastIndex)
    val currentPose = if (poseResults.isNotEmpty()) poseResults[(poseResults.size * 0.5f).toInt().coerceIn(0, poseResults.lastIndex)] else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
        ) {
            if (thumbnails.isNotEmpty()) {
                AsyncImage(
                    model = thumbnails[currentFrame],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            currentPose?.let { pose ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSkeleton(pose, size.width, size.height)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Text("✕", color = Color.White)
                }
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Text("⬆", color = Color.White)
                }
            }
        }

        if (selectedJoint == null) {
            AngleGrid(
                poseResults = poseResults,
                modifier = Modifier.weight(0.55f),
                onSelectJoint = { selectedJoint = it }
            )
        } else {
            JointDetailPanel(
                joint = selectedJoint!!,
                poseResults = poseResults,
                modifier = Modifier.weight(0.55f),
                onDismiss = { selectedJoint = null }
            )
        }
    }
}

@Composable
private fun LoadingScreen(progress: Float, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 52.dp, start = 16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Text("✕", color = Color.White)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🦴", fontSize = 48.sp)
            Text("分析中...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(240.dp),
                color = PurpleLine,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun AngleGrid(
    poseResults: List<PoseResult>,
    modifier: Modifier = Modifier,
    onSelectJoint: (JointAngle) -> Unit
) {
    val midPose = if (poseResults.isNotEmpty())
        poseResults[poseResults.size / 2] else null

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.background(DarkSurface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(JointAngle.values()) { joint ->
            val angle = midPose?.let {
                val a = it.keypoints.getOrNull(joint.a.idx) ?: return@let null
                val b = it.keypoints.getOrNull(joint.b.idx) ?: return@let null
                val c = it.keypoints.getOrNull(joint.c.idx) ?: return@let null
                if (a.worldX != 0f || b.worldX != 0f || c.worldX != 0f)
                    calcAngle3D(a, b, c) else calcAngle(a, b, c)
            }
            AngleCard(
                label = joint.label,
                angle = angle,
                onClick = { onSelectJoint(joint) }
            )
        }
    }
}

@Composable
private fun AngleCard(label: String, angle: Float?, onClick: () -> Unit) {
    val color = angle?.let {
        when {
            it > 150f -> AngleGreen
            it > 100f -> AngleOrange
            it > 50f -> AngleBlue
            else -> AngleRed
        }
    } ?: Color.White.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            Text(
                text = if (angle != null) "${angle.toInt()}°" else "--",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun JointDetailPanel(
    joint: JointAngle,
    poseResults: List<PoseResult>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val angles = poseResults.map { pose ->
        val a = pose.keypoints.getOrNull(joint.a.idx) ?: return@map 0f
        val b = pose.keypoints.getOrNull(joint.b.idx) ?: return@map 0f
        val c = pose.keypoints.getOrNull(joint.c.idx) ?: return@map 0f
        if (a.worldX != 0f || b.worldX != 0f || c.worldX != 0f)
            calcAngle3D(a, b, c) else calcAngle(a, b, c)
    }.filter { it > 0f }

    val minAngle = angles.minOrNull() ?: 0f
    val maxAngle = angles.maxOrNull() ?: 0f
    val avgAngle = if (angles.isNotEmpty()) angles.average().toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(joint.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            TextButton(onClick = onDismiss) { Text("返回", color = AccentBlue) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("最小", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("${minAngle.toInt()}°", fontSize = 18.sp, color = AngleRed, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("平均", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("${avgAngle.toInt()}°", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("最大", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("${maxAngle.toInt()}°", fontSize = 18.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (angles.isNotEmpty()) {
            AngleLineChart(
                angles = angles,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun AngleLineChart(angles: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val maxVal = (angles.maxOrNull() ?: 180f) * 1.1f
        val minVal = (angles.minOrNull() ?: 0f) * 0.9f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val path = Path()

        angles.forEachIndexed { idx, angle ->
            val x = w * idx / (angles.size - 1).coerceAtLeast(1)
            val y = h - (h * (angle - minVal) / range)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color = Color(0xFFCC44FF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))

        for (i in 0..3) {
            val y = h * i / 3
            drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
        }
    }
}

private fun DrawScope.drawSkeleton(pose: PoseResult, width: Float, height: Float) {
    skeletonEdges.forEach { (from, to) ->
        val kpA = pose.keypoints.getOrNull(from.idx) ?: return@forEach
        val kpB = pose.keypoints.getOrNull(to.idx) ?: return@forEach
        if (kpA.confidence < 0.3f || kpB.confidence < 0.3f) return@forEach
        drawLine(
            color = Color.White,
            start = Offset(kpA.x * width, kpA.y * height),
            end = Offset(kpB.x * width, kpB.y * height),
            strokeWidth = 2.dp.toPx()
        )
    }
    pose.keypoints.forEach { kp ->
        if (kp.confidence < 0.3f) return@forEach
        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(kp.x * width, kp.y * height))
    }
}
