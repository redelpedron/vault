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

        // FLAG_SECURE, set once for the Activity's whole lifetime rather
        // than toggled per-screen: the OS only needs it active AT THE
        // MOMENT it captures a screenshot or an app-switcher thumbnail,
        // so "always on" is both the simplest implementation and the
        // only reliable one — a toggle tied to onPause/onResume risks a
        // race where the snapshot is taken before the flag lands. This
        // single flag does three things simultaneously: blanks this
        // app's thumbnail in the Recent Apps switcher (a plain gray
        // rectangle instead of vault contents), makes screenshot/screen-
        // recording attempts of this app fail, and blocks the window
        // from being mirrored to a non-secure external display. This is
        // the "don't show a snapshot" half of the protection; the
        // biometric gate wired below is the separate "re-authenticate
        // before showing the live screen again" half.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

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
