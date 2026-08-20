package com.vault.app.presentation.folderpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vault.app.presentation.browser.Breadcrumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    val actionLabel = if (viewModel.operation == PickerOperation.MOVE) "Move" else "Copy"

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("$actionLabel here") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::showCreateFolderDialog) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                    },
                )
                Breadcrumb(items = state.breadcrumb, onClick = viewModel::navigateToBreadcrumb)
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.confirmError != null) {
                        Text(
                            state.confirmError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = viewModel::confirm,
                        enabled = !state.confirmBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.confirmBusy) "$actionLabel…" else "$actionLabel here")
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
                }
                state.folders.isEmpty() -> Text(
                    "No subfolders here — tap the folder icon above to create one.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn {
                    items(state.folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.originalName) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.navigateInto(folder.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            busy = state.createFolderBusy,
            error = state.createFolderError,
            onDismiss = viewModel::dismissCreateFolderDialog,
            onConfirm = { name -> viewModel.createFolder(name) },
        )
    }
}

@Composable
private fun CreateFolderDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
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
                enabled = !busy && name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
