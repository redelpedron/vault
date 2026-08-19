package com.vault.app.presentation.usermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.remote.dto.UserDto
import com.vault.app.data.repository.OrgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Transcribed from internal/domain/auth/roles.go — the four roles the
// server recognizes. Kept as a plain list here (not the DTO's role
// String, not a shared enum) since this is specifically "the roles this
// screen offers in the invite/change dialogs", a UI concern, not a wire
// concern — see Dtos.kt's comment on why UserDto.role stays a String.
val ASSIGNABLE_ROLES = listOf("admin", "contributor", "viewer", "auditor")

data class UserManagementUiState(
    val loading: Boolean = true,
    val users: List<UserDto> = emptyList(),
    val error: String? = null,

    val showInviteDialog: Boolean = false,
    val inviteBusy: Boolean = false,
    val inviteError: String? = null,

    // Non-null while a remove confirmation dialog is showing.
    val pendingRemove: UserDto? = null,
    val removeBusy: Boolean = false,
    val removeError: String? = null,

    // userId currently mid role-change API call, so that row's dropdown
    // can show a spinner and reject further taps until it resolves.
    val roleChangeBusyUserId: String? = null,
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val repository: OrgRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserManagementUiState())
    val state: StateFlow<UserManagementUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.listUsers()
                .onSuccess { users -> _state.update { it.copy(loading = false, users = users) } }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load users") }
                }
        }
    }

    fun showInviteDialog() = _state.update { it.copy(showInviteDialog = true, inviteError = null) }
    fun dismissInviteDialog() = _state.update { it.copy(showInviteDialog = false, inviteError = null) }

    fun invite(email: String, password: String, role: String) {
        viewModelScope.launch {
            _state.update { it.copy(inviteBusy = true, inviteError = null) }
            repository.inviteUser(email, password, role)
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            inviteBusy = false,
                            showInviteDialog = false,
                            users = it.users + user,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(inviteBusy = false, inviteError = e.message ?: "Failed to invite user") }
                }
        }
    }

    fun requestRemove(user: UserDto) = _state.update { it.copy(pendingRemove = user, removeError = null) }
    fun cancelRemove() = _state.update { it.copy(pendingRemove = null, removeError = null) }

    fun confirmRemove() {
        val target = _state.value.pendingRemove ?: return
        viewModelScope.launch {
            _state.update { it.copy(removeBusy = true, removeError = null) }
            repository.removeUser(target.id)
                .onSuccess {
                    _state.update {
                        it.copy(
                            removeBusy = false,
                            pendingRemove = null,
                            users = it.users.filterNot { u -> u.id == target.id },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(removeBusy = false, removeError = e.message ?: "Failed to remove user") }
                }
        }
    }

    fun changeRole(user: UserDto, newRole: String) {
        if (newRole == user.role) return
        viewModelScope.launch {
            _state.update { it.copy(roleChangeBusyUserId = user.id) }
            repository.changeRole(user.id, newRole)
                .onSuccess {
                    _state.update {
                        it.copy(
                            roleChangeBusyUserId = null,
                            users = it.users.map { u -> if (u.id == user.id) u.copy(role = newRole) else u },
                        )
                    }
                }
                .onFailure { e ->
                    // Role change failed (e.g. "can't demote the last admin",
                    // server-side per org UseCases.ChangeUserRole) — surface
                    // it via the same list-level error slot rather than a
                    // per-row dialog; the row itself just reverts to its
                    // last-known role since the local state was never
                    // optimistically updated above.
                    _state.update { it.copy(roleChangeBusyUserId = null, error = e.message ?: "Failed to change role") }
                }
        }
    }
}
