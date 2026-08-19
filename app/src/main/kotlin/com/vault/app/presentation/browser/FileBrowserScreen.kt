package com.vault.app.presentation.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vault.app.data.remote.dto.FileListItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onNavigateToFolder: (id: String) -> Unit,
    onBreadcrumbClick: (id: String) -> Unit,
    onLockVault: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var fabExpanded by remember { mutableStateOf(false) }

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
                TopAppBar(
                    title = { Text("Vault") },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = viewModel::lockVault) {
                            Icon(Icons.Filled.Lock, contentDescription = "Lock vault")
                        }
                    },
                )
                Breadcrumb(items = state.breadcrumb, onClick = onBreadcrumbClick)
            }
        },
        floatingActionButton = {
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
                else -> LazyColumn {
                    items(state.items, key = { it.id }) { item ->
                        FileRow(
                            item = item,
                            onClick = {
                                if (item.isFolder) onNavigateToFolder(item.id) else viewModel.download(item)
                            },
                            onDelete = { viewModel.requestDelete(item) },
                        )
                        HorizontalDivider()
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
}

@Composable
private fun Breadcrumb(items: List<com.vault.app.data.remote.dto.BreadcrumbItemDto>, onClick: (String) -> Unit) {
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

@Composable
private fun FileRow(item: FileListItemDto, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.originalName) },
        supportingContent = {
            if (!item.isFolder) Text(formatSize(item.size))
        },
        leadingContent = {
            Icon(
                if (item.isFolder) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
