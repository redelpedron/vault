package com.vault.app.presentation.settings

import androidx.lifecycle.ViewModel
import com.vault.app.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val privacyScreenEnabled: Boolean = true,
    val biometricGateEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            privacyScreenEnabled = sessionManager.privacyScreenEnabled,
            biometricGateEnabled = sessionManager.biometricGateEnabled,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // Persists here; the live window.setFlags/clearFlags call for
    // privacy-screen changes happens in SettingsScreen itself, not here —
    // that needs an Activity reference this ViewModel has no business
    // holding (ViewModels must not hold Activity/View references, they
    // can outlive them across configuration changes).
    fun setPrivacyScreenEnabled(enabled: Boolean) {
        sessionManager.privacyScreenEnabled = enabled
        _state.update { it.copy(privacyScreenEnabled = enabled) }
    }

    fun setBiometricGateEnabled(enabled: Boolean) {
        sessionManager.biometricGateEnabled = enabled
        _state.update { it.copy(biometricGateEnabled = enabled) }
    }
}
