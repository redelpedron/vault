package com.vault.app.presentation.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.vault.app.data.remote.dto.FileListItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onNavigateToFolder: (id: String) -> Unit,
    onBreadcrumbClick: (id: String) -> Unit,
    onLockVault: () -> Unit,
    onNavigateToPicker: (operation: String, itemIds: List<String>) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var fabExpanded by remember { mutableStateOf(false) }

    // Re-fetches every time this screen re-enters composition — covers
    // returning from FolderPickerScreen after a successful move/copy,
    // the same mechanism used elsewhere in this app (see
    // VaultListScreen's identical LaunchedEffect(Unit) comment). Costs one
    // harmless redundant fetch on first entry (ViewModel's own init{}
    // already calls refresh() once) — accepted rather than adding a
    // SavedStateHandle nav-result plumbing just to avoid it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::onFilePicked) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.locked) {
        if (state.locked) onLockVault()
    }

    LaunchedEffect(state.downloadedFile) {
        state.downloadedFile?.let { downloaded ->
            val downloader = FileDownloader(context)
            val intent = downloader.viewIntentFor(downloaded.file, downloaded.mimeType)
            runCatching { context.startActivity(intent) }
                .onFailure { snackbarHostState.showSnackbar("Saved, but no app can open this file type") }
            viewModel.consumeDownloadedFile()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                if (state.selectionMode) {
                    TopAppBar(
                        title = { Text("${state.selectedIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { onNavigateToPicker("move", state.selectedIds.toList()) }) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = "Move selected")
                            }
                            IconButton(onClick = { onNavigateToPicker("copy", state.selectedIds.toList()) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selected")
                            }
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                            }
                            IconButton(onClick = viewModel::downloadSelected) {
                                Icon(Icons.Filled.Download, contentDescription = "Download selected")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text("Vault") },
                        actions = {
                            IconButton(onClick = viewModel::toggleViewMode) {
                                // Icon shown is what tapping switches TO,
                                // not the current mode — standard convention
                                // for this kind of toggle.
                                Icon(
                                    if (state.viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                    contentDescription = if (state.viewMode == ViewMode.LIST) "Switch to grid view" else "Switch to list view",
                                )
                            }
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = viewModel::lockVault) {
                                Icon(Icons.Filled.Lock, contentDescription = "Lock vault")
                            }
                        },
                    )
                }
                Breadcrumb(items = state.breadcrumb, onClick = onBreadcrumbClick)
            }
        },
        floatingActionButton = {
            // Hidden during selection: creating a folder or uploading a
            // file mid-multi-select is a confusing combination, and the
            // FAB would visually compete with the selection top bar's
            // own actions.
            if (!state.selectionMode) {
                Column(horizontalAlignment = Alignment.End) {
                    if (fabExpanded) {
                        SmallFabAction(
                            icon = Icons.Filled.CreateNewFolder,
                            label = "New folder",
                            onClick = { fabExpanded = false; viewModel.setCreateFolderDialogVisible(true) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SmallFabAction(
                            icon = Icons.Filled.UploadFile,
                            label = "Upload",
                            onClick = { fabExpanded = false; pickFileLauncher.launch("*/*") },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                        Icon(if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add, contentDescription = "Actions")
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
                state.items.isEmpty() -> Text(
                    "Empty folder — tap + to upload a file or create a folder.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> when (state.viewMode) {
                    ViewMode.LIST -> LazyColumn {
                        items(state.items, key = { it.id }) { item ->
                            FileRow(
                                item = item,
                                imageLoader = viewModel.imageLoader,
                                selectionMode = state.selectionMode,
                                selected = item.id in state.selectedIds,
                                onClick = {
                                    when {
                                        state.selectionMode -> viewModel.toggleSelection(item)
                                        item.isFolder -> onNavigateToFolder(item.id)
                                        else -> viewModel.download(item)
                                    }
                                },
                                onLongClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(item) else viewModel.enterSelectionMode(item)
                                },
                                onDelete = { viewModel.requestDelete(item) },
                                onMove = { onNavigateToPicker("move", listOf(item.id)) },
                                onCopy = { onNavigateToPicker("copy", listOf(item.id)) },
                                onRename = { viewModel.requestRename(item) },
                            )
                            HorizontalDivider()
                        }
                    }
                    ViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            FileGridCell(
                                item = item,
                                imageLoader = viewModel.imageLoader,
                                selectionMode = state.selectionMode,
                                selected = item.id in state.selectedIds,
                                onClick = {
                                    when {
                                        state.selectionMode -> viewModel.toggleSelection(item)
                                        item.isFolder -> onNavigateToFolder(item.id)
                                        else -> viewModel.download(item)
                                    }
                                },
                                onLongClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(item) else viewModel.enterSelectionMode(item)
                                },
                                onDelete = { viewModel.requestDelete(item) },
                            )
                        }
                    }
                }
            }

            if (state.busyMessage != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(state.busyMessage!!)
                    }
                }
            }
        }
    }

    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.setCreateFolderDialogVisible(false) },
            onConfirm = viewModel::createFolder,
        )
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete \"${item.originalName}\"?") },
            text = {
                Text(
                    if (item.isFolder) "This folder must be empty to delete it."
                    else "This can't be undone from this app.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }

    state.pendingRename?.let { item ->
        RenameDialog(
            item = item,
            busy = state.renameBusy,
            error = state.renameError,
            onDismiss = viewModel::cancelRename,
            onConfirm = { newName -> viewModel.confirmRename(newName) },
        )
    }
}

// Not private — reused by FolderPickerScreen for the same breadcrumb UI
// rather than duplicating it.
@Composable
fun Breadcrumb(items: List<com.vault.app.data.remote.dto.BreadcrumbItemDto>, onClick: (String) -> Unit) {
    if (items.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items.forEachIndexed { index, crumb ->
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onClick(crumb.id) },
            )
            if (index != items.lastIndex) Text("  /  ", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileListItemDto,
    imageLoader: ImageLoader,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(item.originalName) },
        supportingContent = {
            if (!item.isFolder) Text(formatSize(item.size))
        },
        leadingContent = {
            when {
                selectionMode -> Checkbox(checked = selected, onCheckedChange = { onClick() })
                // AsyncImage falls back to its error/placeholder state
                // (nothing drawn, since neither is set) on fetch failure —
                // e.g. a network hiccup or an unsupported codec inside an
                // otherwise-video/* file. That's an acceptable silent
                // fallback for a thumbnail specifically: worst case, the
                // row just shows blank where an icon would normally be,
                // never a crash or a stuck spinner.
                item.isFolder -> Icon(Icons.Filled.Folder, contentDescription = null)
                isThumbnailable(item.mimeType) -> AsyncImage(
                    model = ThumbnailRequest(item.id, item.mimeType),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                )
                else -> Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
            }
        },
        trailingContent = {
            // Per-row actions only make sense outside selection mode —
            // once selecting, this menu would be a confusing second way
            // to act on a row right next to its checkbox; use the
            // selection top bar's actions instead.
            if (!selectionMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = { menuExpanded = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/**
 * Grid counterpart to FileRow — same click/long-click/selection semantics,
 * different layout: a big square thumbnail-or-icon with the name below,
 * rather than a horizontal list row. Selection uses a checkbox overlaid
 * on the corner instead of replacing the leading icon (there's no
 * separate "leading slot" in a grid cell the way ListItem provides one).
 * Delete also becomes a small corner overlay for the same reason — kept
 * rather than dropped, so grid mode doesn't regress a capability list
 * mode already has.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCell(
    item: FileListItemDto,
    imageLoader: ImageLoader,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            when {
                item.isFolder -> Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.6f).align(Alignment.Center),
                )
                isThumbnailable(item.mimeType) -> AsyncImage(
                    model = ThumbnailRequest(item.id, item.mimeType),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
                else -> Icon(
                    Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f).align(Alignment.Center),
                )
            }

            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            } else {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Text(
            item.originalName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SmallFabAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.width(8.dp))
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun RenameDialog(
    item: FileListItemDto,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
) {
    val currentName = item.originalName

    // Folders don't have an extension concept to protect. For files, the
    // extension is everything from the LAST '.', with two guards against
    // false positives: a leading dot (dotfiles like ".gitignore" — the
    // dot is part of the name, not a separator, matching how most file
    // managers already treat them) and a trailing dot with nothing after
    // it. Anything else falls back to the whole name being editable,
    // same as before this existed — this only activates for the common,
    // unambiguous case.
    val lastDot = currentName.lastIndexOf('.')
    val hasExtension = !item.isFolder && lastDot > 0 && lastDot < currentName.length - 1
    val extension = if (hasExtension) currentName.substring(lastDot) else ""
    var baseName by remember { mutableStateOf(if (hasExtension) currentName.substring(0, lastDot) else currentName) }

    val fullNewName = baseName.trim() + extension

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("Name") },
                    // Extension shown as a fixed suffix inside the field,
                    // not part of the editable value — this is the whole
                    // point: it can't be accidentally dropped or typo'd
                    // while renaming just the name portion.
                    suffix = if (hasExtension) { { Text(extension) } } else null,
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                // Also disabled when unchanged, not just blank — renaming
                // to the exact same name is a pointless round trip to the
                // server for a result that changes nothing.
                enabled = !busy && baseName.isNotBlank() && fullNewName != currentName,
                onClick = { onConfirm(fullNewName) },
            ) { Text(if (busy) "Renaming…" else "Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
