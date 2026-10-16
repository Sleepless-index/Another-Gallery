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
import dev.vibe.gallery.data.DupGroup
import dev.vibe.gallery.data.Cleanup
import dev.vibe.gallery.data.MediaOps
import dev.vibe.gallery.data.ResumeEntity
import dev.vibe.gallery.data.SortMode
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
    fun setSort(v: SortMode) = viewModelScope.launch { g.settings.setSort(v) }

    // ---- media ----------------------------------------------------------
    val items: StateFlow<List<MediaItem>> = repo.items
    val loaded: StateFlow<Boolean> = repo.loaded

    /** The Photos tab list, ordered by the chosen sort. */
    val photos: StateFlow<List<MediaItem>> = combine(items, prefs.map { it.sort }.distinctUntilChanged()) { list, sort ->
        when (sort) {
            SortMode.DATE_DESC -> list
            SortMode.DATE_ASC -> list.sortedBy { it.dateMs }
            SortMode.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Date headers only make sense when sorting by date. */
    val photoEntries: StateFlow<List<Entry>> = combine(photos, prefs.map { it.sort }.distinctUntilChanged()) { list, sort ->
        if (sort == SortMode.DATE_DESC || sort == SortMode.DATE_ASC) buildEntries(list) else list.map { Entry.Media(it) }
    }
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

    fun onSystemDialogResult(ok: Boolean) {
        if (ok) {
            _dups.value = DupState.Idle
            val gone = pendingIds.toHashSet()
            _viewerList.value = _viewerList.value.filter { it.id !in gone }
            clearSelection()
            refresh()
        }
        pendingIds = emptyList()
    }

    // ---- copy / move to album --------------------------------------------
    fun copyToDir(list: List<MediaItem>, relativeDir: String, move: Boolean) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val done = MediaOps.copyItems(getApplication(), list, relativeDir)
            clearSelection()
            if (done.isEmpty()) {
                message("Couldn't copy those files")
                return@launch
            }
            message(if (move) "Moved ${done.size} to ${relativeDir.trimEnd('/').substringAfterLast('/')}"
            else "Copied ${done.size} to ${relativeDir.trimEnd('/').substringAfterLast('/')}")
            refresh()
            // "Move" = copy first, then send the originals to the trash (the system asks to confirm).
            if (move) requestTrash(done)
        }
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

    // ---- duplicates -----------------------------------------------------
    sealed interface DupState {
        data object Idle : DupState
        data class Scanning(val done: Int, val total: Int) : DupState
        data class Done(val groups: List<DupGroup>) : DupState
    }

    private val _dups = MutableStateFlow<DupState>(DupState.Idle)
    val dups: StateFlow<DupState> = _dups
    private var dupJob: kotlinx.coroutines.Job? = null

    fun scanDuplicates() {
        if (dupJob?.isActive == true) return
        dupJob = viewModelScope.launch {
            _dups.value = DupState.Scanning(0, 0)
            val groups = Cleanup.findDuplicates(getApplication<Application>().contentResolver, items.value) { d, t ->
                _dups.value = DupState.Scanning(d, t)
            }
            _dups.value = DupState.Done(groups)
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
