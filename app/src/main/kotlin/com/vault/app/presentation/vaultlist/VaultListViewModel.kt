package com.vault.app.presentation.vaultlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.local.SessionManager
import com.vault.app.data.remote.dto.VaultDto
import com.vault.app.data.repository.OrgRepository
import com.vault.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultListUiState(
    val loading: Boolean = true,
    val vaults: List<VaultDto> = emptyList(),
    val error: String? = null,
    // Non-null while an unlock/create dialog is showing its own submit error.
    val dialogError: String? = null,
    val dialogBusy: Boolean = false,
    val unlockedVaultId: String? = null, // set once, triggers navigation
    // Org/user login is entirely separate from the vault session above —
    // see SessionManager's comment on why both can be independently
    // present, absent, or mixed.
    val userLoggedIn: Boolean = false,
    val userEmail: String? = null,
    val signOutBusy: Boolean = false,
    // Which list is currently shown. Only meaningful (and only reachable
    // in the UI) when userLoggedIn — a signed-out session has no org
    // context to scope a second list to. Unlock is NOT split by source:
    // handleUnlock (vault.go) never reads OrgID, so a vault found via
    // either list unlocks through the exact same call — only *listing*
    // and *creation* need to know which source they're talking to.
    val viewingOrgVaults: Boolean = false,
    val orgVaults: List<VaultDto> = emptyList(),
    val orgVaultsLoading: Boolean = false,
    val orgVaultsError: String? = null,
    // Delete confirmation dialog state — deliberately separate from
    // dialogBusy/dialogError above (those are create/unlock's), since a
    // delete can be triggered from a row action while a create/unlock
    // dialog result is still in flight from a previous tap.
    val pendingDelete: VaultDto? = null,
    val deleteBusy: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val orgRepository: OrgRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultListUiState())
    val state: StateFlow<VaultListUiState> = _state.asStateFlow()

    init {
        refresh()
        refreshUserSession()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.listVaults()
                .onSuccess { vaults ->
                    _state.update { it.copy(loading = false, vaults = vaults) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to load vaults") }
                }
        }
    }

    // Re-reads the on-device user session. SessionManager is plain
    // EncryptedSharedPreferences, not a Flow, so it doesn't push changes —
    // this needs an explicit call. VaultListScreen calls this from a
    // LaunchedEffect(Unit) every time it re-enters composition, which
    // covers "just signed in on OrgAuthScreen and navigated back" without
    // this ViewModel needing to know anything about that other screen.
    fun refreshUserSession() {
        _state.update {
            it.copy(
                userLoggedIn = sessionManager.userToken != null,
                userEmail = sessionManager.userEmail,
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(signOutBusy = true) }
            orgRepository.logout()
            // Signing out also drops back to "my vaults" — the org tab
            // has nothing to show without a user session, and leaving it
            // selected would strand the UI on an empty, unreachable tab.
            _state.update {
                it.copy(
                    signOutBusy = false,
                    userLoggedIn = false,
                    userEmail = null,
                    viewingOrgVaults = false,
                )
            }
        }
    }

    fun setVaultSource(showOrgVaults: Boolean) {
        _state.update { it.copy(viewingOrgVaults = showOrgVaults) }
        if (showOrgVaults) refreshOrgVaults()
    }

    fun refreshOrgVaults() {
        viewModelScope.launch {
            _state.update { it.copy(orgVaultsLoading = true, orgVaultsError = null) }
            orgRepository.listOrgVaults()
                .onSuccess { vaults ->
                    _state.update { it.copy(orgVaultsLoading = false, orgVaults = vaults) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(orgVaultsLoading = false, orgVaultsError = e.message ?: "Failed to load org vaults")
                    }
                }
        }
    }

    fun createVault(name: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(dialogBusy = true, dialogError = null) }
            val result = if (_state.value.viewingOrgVaults) {
                orgRepository.createOrgVault(name, password)
            } else {
                repository.createVault(name, password)
            }
            result
                .onSuccess { resp ->
                    sessionManager.storeUnlockedSession(resp.vaultId, resp.token, resp.name)
                    _state.update { it.copy(dialogBusy = false, unlockedVaultId = resp.vaultId) }
                }
                .onFailure { e ->
                    _state.update { it.copy(dialogBusy = false, dialogError = e.message ?: "Failed to create vault") }
                }
        }
    }

    fun unlockVault(vaultId: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(dialogBusy = true, dialogError = null) }
            repository.unlockVault(vaultId, password)
                .onSuccess { resp ->
                    sessionManager.storeUnlockedSession(resp.vaultId, resp.token, resp.name)
                    _state.update { it.copy(dialogBusy = false, unlockedVaultId = resp.vaultId) }
                }
                .onFailure { e ->
                    _state.update { it.copy(dialogBusy = false, dialogError = e.message ?: "Wrong password?") }
                }
        }
    }

    fun clearDialogError() {
        _state.update { it.copy(dialogError = null) }
    }

    fun requestDelete(vault: VaultDto) = _state.update { it.copy(pendingDelete = vault, deleteError = null) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null, deleteError = null) }

    fun confirmDelete(password: String) {
        val target = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleteBusy = true, deleteError = null) }
            // Org tab MUST go through orgRepository.deleteOrgVault, not
            // the legacy repository.deleteVault: only the org path
            // enforces PermVaultDelete (org UseCases.DeleteVault) and org
            // membership server-side. The legacy endpoint is
            // password-only, same as list/unlock — using it here would
            // silently let a viewer/auditor-role member (or anyone who
            // knows the vault password) delete an org vault without the
            // role check ever running.
            val result = if (_state.value.viewingOrgVaults) {
                orgRepository.deleteOrgVault(target.id, password)
            } else {
                repository.deleteVault(target.id, password)
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            deleteBusy = false,
                            pendingDelete = null,
                            vaults = it.vaults.filterNot { v -> v.id == target.id },
                            orgVaults = it.orgVaults.filterNot { v -> v.id == target.id },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(deleteBusy = false, deleteError = e.message ?: "Wrong password?") }
                }
        }
    }

    fun changeServer() {
        sessionManager.serverBaseUrl = null
        sessionManager.clearSession()
    }
}
