package com.vault.app.presentation.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.vault.app.data.remote.dto.BreadcrumbItemDto
import com.vault.app.data.remote.dto.FileListItemDto
import com.vault.app.data.repository.VaultRepository
import com.vault.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FileBrowserUiState(
    val loading: Boolean = true,
    val items: List<FileListItemDto> = emptyList(),
    val breadcrumb: List<BreadcrumbItemDto> = emptyList(),
    val error: String? = null,
    val busyMessage: String? = null, // "Uploading…" / "Downloading…" — shown as a blocking-ish snackbar
    val toast: String? = null,
    val downloadedFile: DownloadedFile? = null, // one-shot: screen launches a view intent then clears it
    val showCreateFolderDialog: Boolean = false,
    val pendingDelete: FileListItemDto? = null,
    val locked: Boolean = false,
    // Selection is scoped to this screen/folder only — each folder is its
    // own nav-graph destination with its own FileBrowserViewModel
    // instance, so there's no cross-folder multi-select today. Tapping a
    // folder row while selectionMode is true toggles its selection rather
    // than navigating into it, for exactly that reason: navigating away
    // would abandon this ViewModel (and its selection) entirely.
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
)

enum class ViewMode { LIST, GRID }

data class DownloadedFile(val file: File, val mimeType: String)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repository: VaultRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    // Exposed as a plain public val, not wrapped — the Screen reads this
    // once and passes it straight to every AsyncImage's imageLoader
    // param. See di/ImageModule.kt for why this is provided through Hilt
    // rather than Coil's global-singleton setter.
    val imageLoader: ImageLoader,
) : ViewModel() {

    /** "" means the vault's root folder — see Destinations for the nav-arg sentinel translation. */
    val folderId: String =
        savedStateHandle.get<String>("folderId")
            ?.takeIf { it != Destinations.BROWSER_ROOT_SENTINEL }
            ?: ""

    private val downloader = FileDownloader(appContext)

    private val _state = MutableStateFlow(FileBrowserUiState())
    val state: StateFlow<FileBrowserUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val filesResult = repository.listFiles(folderId.ifEmpty { null })
            val crumbResult = repository.breadcrumb(folderId.ifEmpty { null })
            filesResult
                .onSuccess { items ->
                    _state.update {
                        // Reconciles selectedIds against the fresh list —
                        // matters once bulk actions (copy/move/delete) can
                        // trigger a refresh() while items are selected;
                        // without this, selectedIds could reference items
                        // that no longer exist. Drops out of selection mode
                        // entirely if nothing selected survives.
                        val stillSelected = it.selectedIds.intersect(items.map { i -> i.id }.toSet())
                        it.copy(
                            loading = false,
                            items = items,
                            breadcrumb = crumbResult.getOrDefault(it.breadcrumb),
                            selectedIds = stillSelected,
                            selectionMode = it.selectionMode && stillSelected.isNotEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load folder") }
                }
        }
    }

    fun onFilePicked(uri: Uri) {
        val picked = resolvePickedFile(appContext, uri)
        if (picked.size < 0) {
            _state.update { it.copy(toast = "Couldn't read that file") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Uploading ${picked.name}…") }
            repository.upload(
                fileName = picked.name,
                mimeType = picked.mimeType,
                folderId = folderId.ifEmpty { null },
                openStream = {
                    appContext.contentResolver.openInputStream(uri)
                        ?: error("Could not reopen picked file")
                },
                contentLength = picked.size,
            ).onSuccess {
                _state.update { it.copy(busyMessage = null, toast = "Uploaded ${picked.name}") }
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(busyMessage = null, toast = e.message ?: "Upload failed") }
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name, folderId.ifEmpty { null })
                .onSuccess {
                    _state.update { it.copy(showCreateFolderDialog = false) }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(toast = e.message ?: "Couldn't create folder") }
                }
        }
    }

    fun setCreateFolderDialogVisible(visible: Boolean) {
        _state.update { it.copy(showCreateFolderDialog = visible) }
    }

    fun requestDelete(item: FileListItemDto) {
        _state.update { it.copy(pendingDelete = item) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val item = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDelete = null) }
            repository.deleteFile(item.id)
                .onSuccess {
                    _state.update { it.copy(toast = "Deleted ${item.originalName}") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(toast = e.message ?: "Couldn't delete ${item.originalName}") }
                }
        }
    }

    fun download(item: FileListItemDto) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Downloading ${item.originalName}…") }
            repository.download(item.id)
                .onSuccess { response ->
                    val body = response.body()
                    if (!response.isSuccessful || body == null) {
                        _state.update { it.copy(busyMessage = null, toast = "Download failed") }
                        return@onSuccess
                    }
                    val file = downloader.saveToDownloads(body, item.originalName)
                    _state.update {
                        it.copy(
                            busyMessage = null,
                            downloadedFile = DownloadedFile(file, item.mimeType),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(busyMessage = null, toast = e.message ?: "Download failed") }
                }
        }
    }

    fun consumeDownloadedFile() {
        _state.update { it.copy(downloadedFile = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun enterSelectionMode(item: FileListItemDto) {
        _state.update { it.copy(selectionMode = true, selectedIds = setOf(item.id)) }
    }

    fun toggleSelection(item: FileListItemDto) {
        _state.update { current ->
            val newSelection = if (item.id in current.selectedIds) {
                current.selectedIds - item.id
            } else {
                current.selectedIds + item.id
            }
            // Dropping out of selection mode when the last item is
            // deselected matches Photos/Gmail-style multi-select — an
            // empty selection toolbar has nothing useful to do, so this
            // returns to normal browsing instead of leaving it showing.
            current.copy(selectionMode = newSelection.isNotEmpty(), selectedIds = newSelection)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selectedIds = it.items.map { item -> item.id }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleViewMode() {
        _state.update { it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) }
    }

    /**
     * Calls POST /api/vaults/lock (invalidating the session server-side)
     * and clears the locally stored token either way — even if the
     * network call fails (server unreachable, already expired), there is
     * no good reason to keep holding a token in EncryptedSharedPreferences
     * that this screen is about to navigate away from using.
     */
    fun lockVault() {
        viewModelScope.launch {
            repository.lockVault()
            _state.update { it.copy(locked = true) }
        }
    }
}
