package com.tiss.replaycam.ui.library

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tiss.replaycam.store.ClipFolder
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.store.SavedClip
import com.tiss.replaycam.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class LibraryDisplayMode { FOLDER, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    clipStore: ClipStore,
    title: String = "日期記錄",
    onSelectClip: ((SavedClip) -> Unit)? = null,
    filterDateKey: String? = null,
    filterFolderId: String? = null
) {
    var displayMode by remember { mutableStateOf(LibraryDisplayMode.FOLDER) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val allClips by clipStore.clips.collectAsState()
    val clips = remember(allClips, filterDateKey, filterFolderId) {
        when {
            filterDateKey != null -> {
                val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                allClips.filter { fmt.format(java.util.Date(it.dateMillis)) == filterDateKey }
            }
            filterFolderId != null -> allClips.filter { it.folderID == filterFolderId }
            else -> allClips
        }
    }
    val folders by clipStore.folders.collectAsState()

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("新增資料夾") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("資料夾名稱") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        clipStore.createFolder(newFolderName.trim())
                        newFolderName = ""
                    }
                    showCreateFolder = false
                }) { Text("新增") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false }) { Text("取消") }
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
                    title = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Text("‹", fontSize = 24.sp, color = Color.White)
                        }
                    },
                    actions = {
                        if (displayMode == LibraryDisplayMode.FOLDER) {
                            IconButton(onClick = { showCreateFolder = true }) {
                                Icon(Icons.Default.Add, contentDescription = "新增資料夾", tint = Color.White)
                            }
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
            ) {
                SegmentedControl(
                    selected = displayMode,
                    onSelect = { displayMode = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                when (displayMode) {
                    LibraryDisplayMode.FOLDER -> FolderView(
                        clipStore = clipStore,
                        clips = clips,
                        folders = folders,
                        navController = navController,
                        onSelectClip = onSelectClip,
                        onAddFolder = { showCreateFolder = true }
                    )
                    LibraryDisplayMode.DATE -> DateView(
                        clips = clips,
                        clipStore = clipStore,
                        navController = navController,
                        onSelectClip = onSelectClip
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    selected: LibraryDisplayMode,
    onSelect: (LibraryDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        listOf(LibraryDisplayMode.FOLDER to "資料夾", LibraryDisplayMode.DATE to "日期").forEach { (mode, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected == mode) Color.White else Color.Transparent)
                    .combinedClickable(onClick = { onSelect(mode) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected == mode) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == mode) NavyBlue else Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun FolderView(
    clipStore: ClipStore,
    clips: List<SavedClip>,
    folders: List<ClipFolder>,
    navController: NavController,
    onSelectClip: ((SavedClip) -> Unit)?,
    onAddFolder: () -> Unit
) {
    val rootFolders = folders.filter { it.parentID == null }
    val unassigned = clips.filter { it.folderID == null }
    val unassignedGroups = makeDateGroups(unassigned)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("資料夾", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        }
        items(rootFolders, key = { it.id }) { folder ->
            FolderRow(
                folder = folder,
                clipCount = clipStore.clipsIn(folder).size,
                onTap = { navController.navigate("folder/${folder.id}") },
                onDelete = { clipStore.deleteFolder(folder) }
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = GlassWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .combinedClickable(onClick = onAddFolder)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📁", fontSize = 16.sp)
                    Text("新增資料夾", fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
        if (unassignedGroups.isNotEmpty()) {
            item {
                Text("未分類", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassWhite
                ) {
                    Column {
                        unassignedGroups.forEachIndexed { idx, group ->
                            DateGroupRow(
                                group = group,
                                onTap = { navController.navigate("day/${group.dateKey}") }
                            )
                            if (idx < unassignedGroups.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateView(
    clips: List<SavedClip>,
    clipStore: ClipStore,
    navController: NavController,
    onSelectClip: ((SavedClip) -> Unit)?
) {
    val groups = makeDateGroups(clips)
    var columnCount by remember { mutableStateOf(3) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
                columnCount = (columnCount / zoom).roundToInt().coerceIn(2, 5)
            }
        }
    ) {
        groups.forEach { group ->
            stickyHeader {
                Text(
                    group.displayDate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(vertical = 8.dp)
                )
            }
            item {
                ClipGrid(
                    clips = group.clips,
                    columnCount = columnCount,
                    clipStore = clipStore,
                    onTap = { clip ->
                        if (onSelectClip != null) onSelectClip(clip)
                        else navController.navigate("player/${clip.id}")
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipGrid(
    clips: List<SavedClip>,
    columnCount: Int,
    clipStore: ClipStore,
    onTap: (SavedClip) -> Unit
) {
    val favoriteIDs by clipStore.favoriteIDs.collectAsState()
    var selectedIDs by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectMode = selectedIDs.isNotEmpty()

    Column {
        clips.chunked(columnCount).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { clip ->
                    ClipThumbnail(
                        clip = clip,
                        isFavorite = favoriteIDs.contains(clip.id),
                        isSelected = selectedIDs.contains(clip.id),
                        isSelectMode = isSelectMode,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            if (isSelectMode) {
                                selectedIDs = if (selectedIDs.contains(clip.id))
                                    selectedIDs - clip.id else selectedIDs + clip.id
                            } else onTap(clip)
                        },
                        onLongPress = {
                            selectedIDs = selectedIDs + clip.id
                        }
                    )
                }
                repeat(columnCount - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (isSelectMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { selectedIDs = emptySet() }) {
                    Text("取消", color = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        clips.filter { selectedIDs.contains(it.id) }.forEach { clipStore.delete(it) }
                        selectedIDs = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刪除 ${selectedIDs.size} 個")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipThumbnail(
    clip: SavedClip,
    isFavorite: Boolean,
    isSelected: Boolean,
    isSelectMode: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(clip.path))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isFavorite) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF5C518))
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AccentBlue.copy(alpha = 0.4f))
            )
            Text(
                "✓",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: ClipFolder,
    clipCount: Int,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("刪除資料夾") },
            text = { Text("確定要刪除「${folder.name}」嗎？資料夾內的片段將移至未分類。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("刪除", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassWhite,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📁", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("$clipCount 個片段", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = DangerRed)
            }
        }
    }
}

@Composable
private fun DateGroupRow(group: DateGroup, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(group.displayDate, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎬 ${group.clips.size} 個片段", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
        Text("›", fontSize = 18.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

data class DateGroup(
    val dateKey: String,
    val displayDate: String,
    val date: Date,
    val clips: List<SavedClip>
)

fun makeDateGroups(clips: List<SavedClip>): List<DateGroup> {
    val cal = Calendar.getInstance()
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val displayFmt = SimpleDateFormat("M月d日", Locale.getDefault())
    val today = fmt.format(Date())
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = fmt.format(cal.time)

    return clips.groupBy { fmt.format(Date(it.dateMillis)) }
        .map { (key, group) ->
            val date = Date(group.first().dateMillis)
            val display = when (key) {
                today -> "今天"
                yesterday -> "昨天"
                else -> displayFmt.format(date)
            }
            DateGroup(dateKey = key, displayDate = display, date = date, clips = group)
        }
        .sortedByDescending { it.date }
}

