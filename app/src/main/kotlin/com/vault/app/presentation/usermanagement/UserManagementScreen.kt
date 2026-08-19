package com.vault.app.presentation.usermanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vault.app.data.remote.dto.UserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit,
    viewModel: UserManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organization users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showInviteDialog) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Invite user")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.users.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
                state.users.isEmpty() -> Text(
                    "No other users yet — tap + to invite one.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column {
                    if (state.error != null) {
                        // Row-action error (e.g. role change rejected) shown
                        // above the list rather than replacing it, since the
                        // list itself is still valid and usable.
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn {
                        items(state.users, key = { it.id }) { user ->
                            UserRow(
                                user = user,
                                roleChangeBusy = state.roleChangeBusyUserId == user.id,
                                onRoleChange = { newRole -> viewModel.changeRole(user, newRole) },
                                onRemove = { viewModel.requestRemove(user) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (state.showInviteDialog) {
        InviteUserDialog(
            busy = state.inviteBusy,
            error = state.inviteError,
            onDismiss = viewModel::dismissInviteDialog,
            onConfirm = { email, password, role -> viewModel.invite(email, password, role) },
        )
    }

    state.pendingRemove?.let { target ->
        RemoveUserDialog(
            email = target.email,
            busy = state.removeBusy,
            error = state.removeError,
            onDismiss = viewModel::cancelRemove,
            onConfirm = viewModel::confirmRemove,
        )
    }
}

@Composable
private fun UserRow(
    user: UserDto,
    roleChangeBusy: Boolean,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(user.email) },
        supportingContent = {
            if (!user.isActive) Text("Inactive", color = MaterialTheme.colorScheme.error)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(
                        onClick = { if (!roleChangeBusy) roleMenuExpanded = true },
                        label = { Text(if (roleChangeBusy) "…" else user.role) },
                    )
                    DropdownMenu(
                        expanded = roleMenuExpanded,
                        onDismissRequest = { roleMenuExpanded = false },
                    ) {
                        ASSIGNABLE_ROLES.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    roleMenuExpanded = false
                                    onRoleChange(role)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${user.email}")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class) // ExposedDropdownMenuBox
@Composable
private fun InviteUserDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (email: String, password: String, role: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ASSIGNABLE_ROLES.first { it != "admin" }) } // contributor by default
    var roleMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite user") },
        text = {
            Column {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Temporary password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // ExposedDropdownMenuBox, not a plain Box + clickable
                // OutlinedTextField: a readOnly text field still consumes
                // touch for its own focus/cursor handling, so a raw
                // clickable modifier layered on top is not reliably hit —
                // this is the officially supported M3 component for
                // exactly this "read-only field opens a menu" case.
                ExposedDropdownMenuBox(
                    expanded = roleMenuExpanded,
                    onExpandedChange = { roleMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = roleMenuExpanded,
                        onDismissRequest = { roleMenuExpanded = false },
                    ) {
                        ASSIGNABLE_ROLES.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = { role = r; roleMenuExpanded = false },
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                onClick = { onConfirm(email.trim(), password, role) },
            ) { Text(if (busy) "Inviting…" else "Invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RemoveUserDialog(
    email: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $email?") },
        text = {
            Column {
                Text("They will immediately lose access to this organization and its vaults.")
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (busy) "Removing…" else "Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
