package com.vault.app.presentation.vaultlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vault.app.data.remote.dto.VaultDto

private sealed interface DialogMode {
    data object None : DialogMode
    data object Create : DialogMode
    data class Unlock(val vault: VaultDto) : DialogMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onVaultUnlocked: () -> Unit,
    onChangeServer: () -> Unit,
    onSignIn: () -> Unit,
    onOpenUserManagement: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var dialogMode by remember { mutableStateOf<DialogMode>(DialogMode.None) }

    LaunchedEffect(state.unlockedVaultId) {
        if (state.unlockedVaultId != null) onVaultUnlocked()
    }

    // Re-checks the on-device user session every time this screen becomes
    // the current nav destination again (default NavHost only composes the
    // top back-stack entry, so returning here from OrgAuthScreen or
    // UserManagementScreen re-runs this) — see refreshUserSession's doc
    // comment for why this can't just be a Flow.
    LaunchedEffect(Unit) { viewModel.refreshUserSession() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your vaults") },
                actions = {
                    if (state.userLoggedIn) {
                        IconButton(onClick = onOpenUserManagement) {
                            Icon(Icons.Filled.Group, contentDescription = "Manage users")
                        }
                        IconButton(onClick = viewModel::signOut, enabled = !state.signOutBusy) {
                            Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                        }
                    } else {
                        IconButton(onClick = onSignIn) {
                            Icon(Icons.Filled.Login, contentDescription = "Sign in")
                        }
                    }
                    IconButton(onClick = {
                        viewModel.changeServer()
                        onChangeServer()
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Change server")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogMode = DialogMode.Create }) {
                Icon(Icons.Filled.Add, contentDescription = "Create vault")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.userLoggedIn) {
                TabRow(selectedTabIndex = if (state.viewingOrgVaults) 1 else 0) {
                    Tab(
                        selected = !state.viewingOrgVaults,
                        onClick = { viewModel.setVaultSource(false) },
                        text = { Text("My vaults") },
                    )
                    Tab(
                        selected = state.viewingOrgVaults,
                        onClick = { viewModel.setVaultSource(true) },
                        text = { Text("Org vaults") },
                    )
                }
            }

            val listLoading = if (state.viewingOrgVaults) state.orgVaultsLoading else state.loading
            val listError = if (state.viewingOrgVaults) state.orgVaultsError else state.error
            val listVaults = if (state.viewingOrgVaults) state.orgVaults else state.vaults
            val onRetry = if (state.viewingOrgVaults) viewModel::refreshOrgVaults else viewModel::refresh

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    listLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    listError != null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(listError, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                    listVaults.isEmpty() -> Text(
                        if (state.viewingOrgVaults) {
                            "No org vaults yet — tap + to create one."
                        } else {
                            "No vaults yet — tap + to create one."
                        },
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn {
                        items(listVaults, key = { it.id }) { vault ->
                            ListItem(
                                headlineContent = { Text(vault.name) },
                                supportingContent = {
                                    Text("${vault.fileCount} files · ${vault.folderCount} folders")
                                },
                                leadingContent = {
                                    Icon(
                                        if (vault.isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                        contentDescription = null,
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.requestDelete(vault) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${vault.name}")
                                    }
                                },
                                modifier = Modifier.clickableItem { dialogMode = DialogMode.Unlock(vault) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    when (val mode = dialogMode) {
        is DialogMode.Create -> CreateVaultDialog(
            forOrg = state.viewingOrgVaults,
            busy = state.dialogBusy,
            error = state.dialogError,
            onDismiss = { dialogMode = DialogMode.None; viewModel.clearDialogError() },
            onConfirm = { name, password -> viewModel.createVault(name, password) },
        )
        is DialogMode.Unlock -> UnlockVaultDialog(
            vaultName = mode.vault.name,
            busy = state.dialogBusy,
            error = state.dialogError,
            onDismiss = { dialogMode = DialogMode.None; viewModel.clearDialogError() },
            onConfirm = { password -> viewModel.unlockVault(mode.vault.id, password) },
        )
        DialogMode.None -> Unit
    }

    state.pendingDelete?.let { target ->
        DeleteVaultDialog(
            vaultName = target.name,
            busy = state.deleteBusy,
            error = state.deleteError,
            onDismiss = viewModel::cancelDelete,
            onConfirm = { password -> viewModel.confirmDelete(password) },
        )
    }
}

// Small local helper so ListItem rows have a single, obvious click target
// without pulling in a whole custom Row/clickable rebuild of ListItem.
private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun CreateVaultDialog(
    forOrg: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forOrg) "Create org vault" else "Create vault") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") }, singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "There is no password recovery — this key derives the encryption " +
                        "key for everything in the vault.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank() && password.isNotEmpty() && !mismatch &&
                    confirmPassword.isNotEmpty(),
                onClick = { onConfirm(name.trim(), password) },
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UnlockVaultDialog(
    vaultName: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock \"$vaultName\"") },
        text = {
            Column {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
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
                enabled = !busy && password.isNotEmpty(),
                onClick = { onConfirm(password) },
            ) { Text(if (busy) "Unlocking…" else "Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteVaultDialog(
    vaultName: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$vaultName\"?") },
        text = {
            Column {
                Text(
                    "This permanently deletes the vault and everything in it. " +
                        "There is no recovery — enter the vault password to confirm.",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
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
                enabled = !busy && password.isNotEmpty(),
                onClick = { onConfirm(password) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (busy) "Deleting…" else "Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
