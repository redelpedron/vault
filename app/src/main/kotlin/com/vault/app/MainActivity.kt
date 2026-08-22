package com.vault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vault.app.data.local.SessionManager
import com.vault.app.presentation.navigation.VaultNavGraph
import com.vault.app.presentation.security.BiometricGateController
import com.vault.app.presentation.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // FragmentActivity, not ComponentActivity — BiometricPrompt's
    // Activity-hosted constructor requires it specifically (confirmed
    // against AndroidX's own source before making this change, not
    // assumed). Every ComponentActivity API used below (enableEdgeToEdge,
    // setContent) remains available: FragmentActivity extends it.

    @Inject
    lateinit var biometricGateController: BiometricGateController

    @Inject
    lateinit var sessionManager: SessionManager

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Fires when the WHOLE APP returns to foreground — actually
            // leaving to the home screen/another app and back — not on
            // internal Compose navigation between screens, which is
            // exactly the distinction the biometric gate needs. Also
            // fires once on cold launch; harmless, since
            // onAppForegrounded no-ops whenever no vault is unlocked yet,
            // which is always true at cold launch before the user has
            // navigated anywhere.
            biometricGateController.onAppForegrounded(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE, set once at cold start per the persisted Settings
        // toggle (default true — secure by default) rather than
        // unconditionally as before this setting existed. Toggling it
        // live while the app is already running (e.g. from
        // SettingsScreen) is a separate, direct window.setFlags/
        // clearFlags call made from there via LocalContext-as-Activity —
        // not routed back through here, since there's no clean way for a
        // deep Composable to re-invoke onCreate. This call only covers
        // the initial state on launch. See onCreate's original comment
        // history (still applies) for why FLAG_SECURE itself is
        // always-on-or-off rather than toggled per lifecycle event when
        // the setting IS enabled: the OS only needs it active at the
        // moment it captures a snapshot, so a stable on/off state per
        // the user's preference is both simplest and most reliable.
        if (sessionManager.privacyScreenEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)

        enableEdgeToEdge()
        setContent {
            VaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VaultNavGraph()
                }
            }
        }
    }

    override fun onDestroy() {
        // ProcessLifecycleOwner is a process-wide singleton that outlives
        // any single Activity instance — this app has exactly one
        // Activity, but removing the observer here is correct, cheap
        // hygiene against a leaked reference if MainActivity is ever
        // recreated.
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onDestroy()
    }
}
