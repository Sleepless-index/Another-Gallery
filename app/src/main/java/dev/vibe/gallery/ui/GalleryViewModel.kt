package dev.vibe.gallery.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibe.gallery.GalleryApp
import dev.vibe.gallery.data.Album
import dev.vibe.gallery.data.Entry
import dev.vibe.gallery.data.FavoriteEntity
import dev.vibe.gallery.data.LayoutMode
import dev.vibe.gallery.data.MediaItem
import dev.vibe.gallery.data.Prefs
import dev.vibe.gallery.data.MediaOps
import dev.vibe.gallery.data.ResumeEntity
import dev.vibe.gallery.data.VaultStore
import dev.vibe.gallery.data.ThemeMode
import dev.vibe.gallery.data.buildAlbums
import dev.vibe.gallery.data.buildEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off requests the Activity has to carry out (they need system dialogs). */
sealed interface UiEvent {
    data class Trash(val uris: List<Uri>) : UiEvent
    data class Restore(val uris: List<Uri>) : UiEvent
    data class DeleteForever(val uris: List<Uri>) : UiEvent
    data class Rename(val uri: Uri, val name: String, val id: Long) : UiEvent
    data class Message(val text: String) : UiEvent
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val g = app as GalleryApp
    private val repo = g.repository
    private val dao = g.db.dao()

    // ---- settings -------------------------------------------------------
    val prefs: StateFlow<Prefs> =
        g.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    fun setLayout(v: LayoutMode) = viewModelScope.launch { g.settings.setLayout(v) }
    fun setColumns(v: Int) = viewModelScope.launch { g.settings.setColumns(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { g.settings.setTheme(v) }
    fun setAccent(v: Int) = viewModelScope.launch { g.settings.setAccent(v) }
    fun setResume(v: Boolean) = viewModelScope.launch { g.settings.setResume(v) }
    fun setAutoplay(v: Boolean) = viewModelScope.launch { g.settings.setAutoplay(v) }
    fun setBlur(v: Boolean) = viewModelScope.launch { g.settings.setBlur(v) }

    // ---- media ----------------------------------------------------------
    val items: StateFlow<List<MediaItem>> = repo.items
    val loaded: StateFlow<Boolean> = repo.loaded

    val photos: StateFlow<List<MediaItem>> = items

    val photoEntries: StateFlow<List<Entry>> = items
        .map { buildEntries(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = items
        .map { buildAlbums(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val videos: StateFlow<List<MediaItem>> = items
        .map { list -> list.filter { it.isVideo } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<Set<Long>> = dao.observeFavoriteIds()
        .map { it.toHashSet() as Set<Long> }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteItems: StateFlow<List<MediaItem>> = combine(items, favorites) { list, favs ->
        list.filter { it.id in favs }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _trashed = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashed: StateFlow<List<MediaItem>> = _trashed

    fun refresh() {
        viewModelScope.launch {
            repo.refresh()
            _trashed.value = repo.queryTrashed()
        }
    }

    fun loadTrash() {
        viewModelScope.launch { _trashed.value = repo.queryTrashed() }
    }

    fun startObserving() = repo.startObserving(viewModelScope)

    // ---- selection ------------------------------------------------------
    val selectMode = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<Long>>(emptySet())

    fun startSelect() {
        selectMode.value = true
    }

    fun toggle(id: Long) {
        val cur = selected.value
        val next = if (id in cur) cur - id else cur + id
        selected.value = next
        selectMode.value = next.isNotEmpty()
    }

    fun setSelected(id: Long, on: Boolean) {
        val cur = selected.value
        val next = if (on) cur + id else cur - id
        selected.value = next
        selectMode.value = next.isNotEmpty()
    }

    fun deselectAll() {
        selected.value = emptySet()
    }

    fun selectAll(ids: Collection<Long>) {
        selected.value = ids.toHashSet()
        selectMode.value = ids.isNotEmpty()
    }

    fun clearSelection() {
        selected.value = emptySet()
        selectMode.value = false
    }

    // ---- viewer ---------------------------------------------------------
    private val _viewerList = MutableStateFlow<List<MediaItem>>(emptyList())
    val viewerList: StateFlow<List<MediaItem>> = _viewerList

    fun setViewerList(list: List<MediaItem>) {
        _viewerList.value = list
    }

    fun findItem(id: Long): MediaItem? =
        _viewerList.value.firstOrNull { it.id == id }
            ?: items.value.firstOrNull { it.id == id }
            ?: _trashed.value.firstOrNull { it.id == id }
            ?: _vault.value.firstOrNull { it.id == id }

    // ---- favorites ------------------------------------------------------
    fun toggleFavorite(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val favs = favorites.value
        val allFav = ids.all { it in favs }
        viewModelScope.launch(Dispatchers.IO) {
            if (allFav) {
                ids.chunked(500).forEach { dao.removeFavorites(it) }
            } else {
                dao.addFavorites(ids.map { FavoriteEntity(it) })
            }
        }
    }

    // ---- delete / trash -------------------------------------------------
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private var pendingIds: List<Long> = emptyList()

    fun requestTrash(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Trash(list.map { it.uri }))
    }

    fun requestRestore(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Restore(list.map { it.uri }))
    }

    fun requestDeleteForever(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.DeleteForever(list.map { it.uri }))
    }

    fun rename(item: MediaItem, newName: String) {
        if (newName == item.name) return
        _events.tryEmit(UiEvent.Rename(item.uri, newName, item.id))
    }

    fun onRenamed(id: Long, newName: String) {
        _viewerList.value = _viewerList.value.map { if (it.id == id) it.copy(name = newName) else it }
        refresh()
    }

    fun onSystemDialogResult(ok: Boolean) {
        if (ok) {
            val gone = pendingIds.toHashSet()
            _viewerList.value = _viewerList.value.filter { it.id !in gone }
            clearSelection()
            refresh()
        }
        pendingIds = emptyList()
    }

    // ---- copy / move to album --------------------------------------------
    fun transferToAlbum(list: List<MediaItem>, album: Album, move: Boolean) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val dir = MediaOps.albumAbsDir(getApplication(), album)
            if (dir == null) message("Couldn't find that album's folder") else doTransfer(list, dir, move)
        }
    }

    fun transferToNew(list: List<MediaItem>, root: String, name: String, move: Boolean) {
        if (list.isEmpty()) return
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New album" }
        viewModelScope.launch { doTransfer(list, "$root/Pictures/$clean", move) }
    }

    private suspend fun doTransfer(list: List<MediaItem>, dir: String, move: Boolean) {
        val app = getApplication<Application>()
        val name = dir.trimEnd('/').substringAfterLast('/')
        val verb = if (move) "Moved" else "Copied"
        if (MediaOps.hasAllFiles()) {
            if (list.size > 2) message(if (move) "Moving…" else "Copying…")
            val n = MediaOps.transfer(app, list, dir, move)
            clearSelection()
            if (move && n > 0) _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            refresh()
            message(if (n > 0) "$verb $n to $name" else "Couldn't ${if (move) "move" else "copy"} those files")
            return
        }
        // Without All files access only copying into internal Pictures/ DCIM is possible.
        if (move) {
            message("Moving needs All files access (Settings > Permissions)")
            return
        }
        val internal = android.os.Environment.getExternalStorageDirectory().absolutePath
        if (!dir.startsWith("$internal/")) {
            message("The SD card needs All files access (Settings > Permissions)")
            return
        }
        val rel = dir.removePrefix("$internal/").trimEnd('/') + "/"
        val done = MediaOps.copyItems(app, list, rel)
        clearSelection()
        message(if (done.isEmpty()) "Couldn't copy those files" else "Copied ${done.size} to $name")
        refresh()
    }

    // ---- vault ----------------------------------------------------------
    private val vaultStore = VaultStore(app)
    private val _vault = MutableStateFlow<List<MediaItem>>(emptyList())
    val vault: StateFlow<List<MediaItem>> = _vault
    val vaultUnlocked = MutableStateFlow(false)

    fun unlockVault() {
        vaultUnlocked.value = true
        loadVault()
    }

    fun lockVault() {
        vaultUnlocked.value = false
    }

    fun loadVault() {
        viewModelScope.launch { _vault.value = vaultStore.list() }
    }

    fun moveToVault(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val stored = vaultStore.add(list)
            clearSelection()
            loadVault()
            if (stored.isEmpty()) {
                message("Couldn't move those files to the Vault")
            } else {
                message("Moved ${stored.size} to the Vault. Confirm to remove the originals.")
                // Permanently remove the originals so they don't linger in the gallery or trash.
                requestDeleteForever(stored)
            }
        }
    }

    fun vaultRestore(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val n = vaultStore.restore(list)
            clearSelection()
            _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            loadVault()
            refresh()
            message(if (n > 0) "Restored $n to your gallery" else "Couldn't restore those files")
        }
    }

    fun vaultDelete(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            vaultStore.delete(list)
            clearSelection()
            _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            loadVault()
        }
    }

    // ---- video resume ---------------------------------------------------
    suspend fun resumePosition(id: Long): Long? = dao.resumePosition(id)

    fun saveResume(id: Long, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val nearEnd = durationMs > 0 && positionMs > durationMs - 4000
            if (positionMs < 3000 || nearEnd) dao.clearResume(id) else dao.saveResume(ResumeEntity(id, positionMs))
        }
    }

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }
}
