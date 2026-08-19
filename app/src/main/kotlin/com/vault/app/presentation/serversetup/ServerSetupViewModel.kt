package com.vault.app.presentation.serversetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

data class ServerSetupUiState(
    val urlInput: String = "",
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ServerSetupUiState(urlInput = sessionManager.serverBaseUrl.orEmpty())
    )
    val state: StateFlow<ServerSetupUiState> = _state.asStateFlow()

    fun onUrlChanged(value: String) {
        _state.update { it.copy(urlInput = value, error = null) }
    }

    fun save() {
        val raw = _state.value.urlInput.trim().removeSuffix("/")
        val parsed = raw.toHttpUrlOrNull()
        if (parsed == null) {
            _state.update {
                it.copy(error = "Enter a full URL, e.g. http://192.168.1.42:8080")
            }
            return
        }
        if (parsed.scheme == "http") {
            // Not an error — this app's whole cleartext posture is a
            // deliberate choice (see network_security_config.xml) — just
            // don't silently pretend it's HTTPS-equivalent.
        }
        viewModelScope.launch {
            sessionManager.serverBaseUrl = raw
            _state.update { it.copy(saved = true) }
        }
    }
}
