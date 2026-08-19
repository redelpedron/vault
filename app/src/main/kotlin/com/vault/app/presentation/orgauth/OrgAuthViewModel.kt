package com.vault.app.presentation.orgauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.repository.OrgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OrgAuthMode { LOGIN, REGISTER }

data class OrgAuthUiState(
    val mode: OrgAuthMode = OrgAuthMode.LOGIN,
    val orgName: String = "", // register only
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false, // set once, triggers navigation
)

/**
 * Combines login and register into one screen (a mode toggle) rather than
 * two separate destinations: the server splits them into two endpoints
 * because they're different use cases (RegisterOrg also creates the org),
 * but from this app's nav-graph perspective they're the same "how do I
 * get a user session" entry point with one extra field — splitting them
 * into two screens/routes would be nav-graph ceremony without a real
 * benefit to the user.
 */
@HiltViewModel
class OrgAuthViewModel @Inject constructor(
    private val repository: OrgRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OrgAuthUiState())
    val state: StateFlow<OrgAuthUiState> = _state.asStateFlow()

    fun onModeChanged(mode: OrgAuthMode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun onOrgNameChanged(value: String) = _state.update { it.copy(orgName = value, error = null) }
    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank() || (s.mode == OrgAuthMode.REGISTER && s.orgName.isBlank())) {
            _state.update { it.copy(error = "Fill in all fields") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (s.mode == OrgAuthMode.REGISTER) {
                repository.registerOrg(s.orgName, s.email, s.password)
            } else {
                repository.login(s.email, s.password)
            }
            result
                .onSuccess { _state.update { it.copy(loading = false, success = true) } }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed") }
                }
        }
    }
}
