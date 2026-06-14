package com.tiss.replaycam.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clip_store")

@Serializable
data class SavedClip(
    val id: String,
    val path: String,
    val dateMillis: Long,
    val folderID: String? = null
)

@Serializable
data class ClipFolder(
    val id: String,
    val name: String,
    val createdMillis: Long,
    val parentID: String? = null
)

class ClipStore private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val FAVORITES_KEY = stringSetPreferencesKey("favoriteClipIDs")
    private val FOLDERS_KEY = stringPreferencesKey("clipFolders")
    private val FOLDER_MAP_KEY = stringPreferencesKey("clipFolderMap")

    private val _clips = MutableStateFlow<List<SavedClip>>(emptyList())
    val clips: StateFlow<List<SavedClip>> = _clips.asStateFlow()

    private val _folders = MutableStateFlow<List<ClipFolder>>(emptyList())
    val folders: StateFlow<List<ClipFolder>> = _folders.asStateFlow()

    private val _favoriteIDs = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIDs: StateFlow<Set<String>> = _favoriteIDs.asStateFlow()

    private val _folderMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val folderMap: StateFlow<Map<String, String>> = _folderMap.asStateFlow()

    val clipsDirectory: File get() = File(context.filesDir, "ReplayCamClips").also { it.mkdirs() }

    init {
        scope.launch {
            val prefs = context.dataStore.data.first()
            _favoriteIDs.value = prefs[FAVORITES_KEY] ?: emptySet()
            _folders.value = prefs[FOLDERS_KEY]?.let {
                Json.decodeFromString<List<ClipFolder>>(it)
            } ?: emptyList()
            _folderMap.value = prefs[FOLDER_MAP_KEY]?.let {
                Json.decodeFromString<Map<String, String>>(it)
            } ?: emptyMap()
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            val files = clipsDirectory.listFiles()
                ?.filter { it.extension == "mp4" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            val map = _folderMap.value
            _clips.value = files.map { f ->
                SavedClip(
                    id = f.nameWithoutExtension,
                    path = f.absolutePath,
                    dateMillis = f.lastModified(),
                    folderID = map[f.nameWithoutExtension]
                )
            }.sortedWith(compareByDescending<SavedClip> {
                _favoriteIDs.value.contains(it.id)
            }.thenByDescending { it.dateMillis })
        }
    }

    fun addClip(file: File) {
        refresh()
    }

    fun delete(clip: SavedClip) {
        scope.launch {
            File(clip.path).delete()
            val newFavs = _favoriteIDs.value - clip.id
            _favoriteIDs.value = newFavs
            val newMap = _folderMap.value - clip.id
            _folderMap.value = newMap
            context.dataStore.edit { prefs ->
                prefs[FAVORITES_KEY] = newFavs
                prefs[FOLDER_MAP_KEY] = Json.encodeToString(newMap)
            }
            refresh()
        }
    }

    fun toggleFavorite(clip: SavedClip) {
        scope.launch {
            val newFavs = if (_favoriteIDs.value.contains(clip.id))
                _favoriteIDs.value - clip.id
            else
                _favoriteIDs.value + clip.id
            _favoriteIDs.value = newFavs
            context.dataStore.edit { it[FAVORITES_KEY] = newFavs }
            refresh()
        }
    }

    fun isFavorite(clip: SavedClip) = _favoriteIDs.value.contains(clip.id)

    fun createFolder(name: String, parentID: String? = null) {
        scope.launch {
            val folder = ClipFolder(
                id = UUID.randomUUID().toString(),
                name = name,
                createdMillis = System.currentTimeMillis(),
                parentID = parentID
            )
            val newFolders = _folders.value + folder
            _folders.value = newFolders
            context.dataStore.edit { it[FOLDERS_KEY] = Json.encodeToString(newFolders) }
        }
    }

    fun renameFolder(folder: ClipFolder, newName: String) {
        scope.launch {
            val newFolders = _folders.value.map {
                if (it.id == folder.id) it.copy(name = newName) else it
            }
            _folders.value = newFolders
            context.dataStore.edit { it[FOLDERS_KEY] = Json.encodeToString(newFolders) }
        }
    }

    fun deleteFolder(folder: ClipFolder) {
        scope.launch {
            val toDelete = mutableListOf(folder.id)
            var remaining = _folders.value.filter { it.id != folder.id }
            var changed = true
            while (changed) {
                changed = false
                val newRemaining = remaining.filter { f ->
                    if (toDelete.contains(f.parentID)) { toDelete.add(f.id); changed = true; false }
                    else true
                }
                remaining = newRemaining
            }
            _folders.value = remaining
            val newMap = _folderMap.value.filterKeys { !toDelete.contains(it) }
            _folderMap.value = newMap
            context.dataStore.edit {
                it[FOLDERS_KEY] = Json.encodeToString(remaining)
                it[FOLDER_MAP_KEY] = Json.encodeToString(newMap)
            }
            refresh()
        }
    }

    fun assignClips(clipIDs: Set<String>, folderID: String?) {
        scope.launch {
            val newMap = _folderMap.value.toMutableMap()
            clipIDs.forEach { id ->
                if (folderID != null) newMap[id] = folderID
                else newMap.remove(id)
            }
            _folderMap.value = newMap
            context.dataStore.edit { it[FOLDER_MAP_KEY] = Json.encodeToString(newMap) }
            refresh()
        }
    }

    fun clearAll() {
        scope.launch {
            clipsDirectory.listFiles()?.forEach { it.delete() }
            context.dataStore.edit {
                it[FAVORITES_KEY] = emptySet()
                it[FOLDER_MAP_KEY] = Json.encodeToString(emptyMap<String, String>())
            }
            _favoriteIDs.value = emptySet()
            _folderMap.value = emptyMap()
            refresh()
        }
    }

    fun rootFolders() = _folders.value.filter { it.parentID == null }
    fun subfolders(folder: ClipFolder) = _folders.value.filter { it.parentID == folder.id }
    fun clipsIn(folder: ClipFolder) = _clips.value.filter { it.folderID == folder.id }
    fun unassignedClips() = _clips.value.filter { it.folderID == null }

    fun totalSizeBytes(): Long =
        clipsDirectory.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }

    companion object {
        @Volatile private var instance: ClipStore? = null
        fun getInstance(context: Context): ClipStore =
            instance ?: synchronized(this) {
                instance ?: ClipStore(context.applicationContext).also { instance = it }
            }
    }
}
