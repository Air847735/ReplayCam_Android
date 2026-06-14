package com.tiss.replaycam.ui.library

import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.store.SavedClip
import com.tiss.replaycam.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    clipId: String,
    navController: NavController,
    clipStore: ClipStore
) {
    val clips by clipStore.clips.collectAsState()
    val clip = clips.find { it.id == clipId } ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favoriteIDs by clipStore.favoriteIDs.collectAsState()
    val isFavorite = favoriteIDs.contains(clip.id)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(clip.path))))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var speed by remember { mutableStateOf(1f) }
    var thumbnails by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }

    LaunchedEffect(clip.path) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(clip.path)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val thumbs = mutableListOf<android.graphics.Bitmap>()
            val count = 30
            for (i in 0 until count) {
                val timeUs = (durationMs * 1000L * i / count)
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { thumbs.add(it) }
            }
            retriever.release()
            thumbnails = thumbs
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
                IconButton(
                    onClick = {
                        val file = File(clip.path)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "儲存影片"))
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Text("⬆", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        Surface(color = DarkSurface) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (thumbnails.isNotEmpty()) {
                    ThumbnailScrubber(
                        thumbnails = thumbnails,
                        onSeek = { fraction ->
                            val duration = exoPlayer.duration
                            if (duration > 0) exoPlayer.seekTo((duration * fraction).toLong())
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.25f to "¼×", 0.5f to "½×", 1f to "1×").forEach { (s, label) ->
                        FilterChip(
                            selected = speed == s,
                            onClick = { speed = s; exoPlayer.setPlaybackSpeed(s) },
                            label = { Text(label, fontSize = 13.sp) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { clipStore.toggleFavorite(clip) }) {
                        Text(if (isFavorite) "⭐" else "☆", fontSize = 24.sp)
                    }
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Text(if (exoPlayer.isPlaying) "⏸" else "▶", fontSize = 20.sp, color = DarkSurface)
                    }
                    IconButton(onClick = {
                        val file = File(clip.path)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "儲存影片"))
                    }) {
                        Text("📤", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailScrubber(
    thumbnails: List<android.graphics.Bitmap>,
    onSeek: (Float) -> Unit
) {
    var dragX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    dragX = change.position.x
                    val fraction = (dragX / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            thumbnails.forEach { thumb ->
                androidx.compose.foundation.Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}
