package com.vault.app.presentation.navigation

import androidx.lifecycle.ViewModel
import com.vault.app.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    /**
     * Decided once, at process start. A session going stale *during* use
     * (server-side timeout, token revoked) is handled separately by each
     * screen's ViewModel reacting to a 401 from apiCall — this is only
     * the initial "where should the app open" decision.
     */
    val startDestination: String = when {
        sessionManager.serverBaseUrl.isNullOrBlank() -> Destinations.SERVER_SETUP
        sessionManager.vaultToken.isNullOrBlank() -> Destinations.VAULT_LIST
        else -> Destinations.browserRoute("")
    }
}
