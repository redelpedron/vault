package com.vault.app.presentation.settings

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Settings screens are only ever reached from an Activity context in
    // this app (there's exactly one Activity, MainActivity) — cast rather
    // than a full LocalActivity-provider abstraction, since that's the
    // only real case this needs to handle. Null-safe regardless: if this
    // somehow isn't an Activity, the toggle still persists via the
    // ViewModel below, it just won't take live effect until next cold
    // start (MainActivity.onCreate reads the same persisted setting).
    val activity = LocalContext.current as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            ListItem(
                modifier = Modifier.padding(padding),
                headlineContent = { Text("Privacy screen") },
                supportingContent = {
                    Text("Hide vault contents in the Recent Apps switcher and block screenshots")
                },
                trailingContent = {
                    Switch(
                        checked = state.privacyScreenEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setPrivacyScreenEnabled(enabled)
                            // Applied live, not just persisted for next
                            // cold start — window.setFlags/clearFlags can
                            // be called at any point in the Activity's
                            // life, not just onCreate, so there's no
                            // reason to make the user restart the app to
                            // see this take effect.
                            if (enabled) {
                                activity?.window?.setFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                )
                            } else {
                                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Biometric lock") },
                supportingContent = {
                    Text("Require Face/Fingerprint (or device PIN) when returning to an unlocked vault")
                },
                trailingContent = {
                    Switch(
                        checked = state.biometricGateEnabled,
                        onCheckedChange = viewModel::setBiometricGateEnabled,
                    )
                },
            )
            HorizontalDivider()
        }
    }
}
