package com.vault.app.presentation.folderpicker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.remote.dto.BreadcrumbItemDto
import com.vault.app.data.remote.dto.FileListItemDto
import com.vault.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PickerOperation { MOVE, COPY }

data class FolderPickerUiState(
    val loading: Boolean = true,
    val folders: List<FileListItemDto> = emptyList(), // listFiles() filtered to isFolder — see currentFolderId's doc comment
    val breadcrumb: List<BreadcrumbItemDto> = emptyList(),
    val error: String? = null,
    val showCreateFolderDialog: Boolean = false,
    val createFolderBusy: Boolean = false,
    val createFolderError: String? = null,
    val confirmBusy: Boolean = false,
    val confirmError: String? = null,
    val done: Boolean = false, // one-shot: screen pops back once true
)

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val repository: VaultRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val operation: PickerOperation =
        if (savedStateHandle.get<String>("operation") == "copy") PickerOperation.COPY else PickerOperation.MOVE

    private val itemIds: List<String> =
        savedStateHandle.get<String>("itemIds")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    private val _state = MutableStateFlow(FolderPickerUiState())
    val state: StateFlow<FolderPickerUiState> = _state.asStateFlow()

    // Unlike FileBrowserViewModel's `folderId` (a val — each folder is its
    // own nav destination with its own ViewModel instance), this is
    // mutable: the picker navigates through the folder tree entirely
    // within one screen instance, one ViewModel, no new nav routes per
    // folder entered. "" means vault root, same convention as everywhere
    // else in this app.
    private var currentFolderId: String = ""

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val filesResult = repository.listFiles(currentFolderId.ifEmpty { null })
            val crumbResult = repository.breadcrumb(currentFolderId.ifEmpty { null })
            filesResult
                .onSuccess { items ->
                    _state.update {
                        it.copy(
                            loading = false,
                            // Only folders are navigable/selectable destinations
                            // here — there's no dedicated "folders within this
                            // specific parent" endpoint (listFolders() returns
                            // every folder in the vault flat, unusable for
                            // hierarchical navigation), so this reuses the same
                            // listFiles() the main browser uses and filters
                            // client-side.
                            folders = items.filter { f -> f.isFolder },
                            breadcrumb = crumbResult.getOrDefault(it.breadcrumb),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load folder") }
                }
        }
    }

    fun navigateInto(folderId: String) {
        currentFolderId = folderId
        load()
    }

    fun navigateToBreadcrumb(folderId: String) {
        currentFolderId = folderId
        load()
    }

    fun showCreateFolderDialog() = _state.update { it.copy(showCreateFolderDialog = true, createFolderError = null) }
    fun dismissCreateFolderDialog() = _state.update { it.copy(showCreateFolderDialog = false, createFolderError = null) }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _state.update { it.copy(createFolderBusy = true, createFolderError = null) }
            repository.createFolder(name, currentFolderId.ifEmpty { null })
                .onSuccess { resp ->
                    _state.update { it.copy(createFolderBusy = false, showCreateFolderDialog = false) }
                    // Auto-navigates into the folder just created, rather than
                    // leaving the user to find and tap it themselves — this is
                    // specifically what "create folder then directly transfer
                    // the file to the created folder" asked for: one dialog,
                    // then one "confirm" tap, nothing in between.
                    navigateInto(resp.id)
                }
                .onFailure { e ->
                    _state.update { it.copy(createFolderBusy = false, createFolderError = e.message ?: "Couldn't create folder") }
                }
        }
    }

    fun confirm() {
        viewModelScope.launch {
            _state.update { it.copy(confirmBusy = true, confirmError = null) }
            val target = currentFolderId // already "" for root, matches server's optionalID convention
            val result = when {
                operation == PickerOperation.MOVE && itemIds.size == 1 ->
                    repository.move(itemIds.single(), target)
                operation == PickerOperation.COPY && itemIds.size == 1 ->
                    repository.copy(itemIds.single(), target).map { Unit }
                operation == PickerOperation.MOVE ->
                    repository.bulkMove(itemIds, target).map { Unit }
                else ->
                    repository.bulkCopy(itemIds, target).map { Unit }
            }
            result
                .onSuccess { _state.update { it.copy(confirmBusy = false, done = true) } }
                .onFailure { e ->
                    _state.update { it.copy(confirmBusy = false, confirmError = e.message ?: "Failed") }
                }
        }
    }
}
