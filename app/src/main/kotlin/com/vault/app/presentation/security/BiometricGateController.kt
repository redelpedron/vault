package com.vault.app.presentation.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vault.app.data.local.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the "re-authenticate with biometrics after returning from
 * background" gate — the second half of this app's screenshot/app-
 * switcher protection alongside MainActivity's FLAG_SECURE. A Hilt
 * singleton rather than tied to any one screen/ViewModel: the gate
 * applies globally regardless of which screen happens to be showing when
 * the app backgrounds and foregrounds again (see VaultNavGraph, which
 * wraps its whole NavHost in this gate rather than any single screen
 * owning it).
 *
 * Deliberately holds no VaultRepository/network dependency — the actual
 * suspend `lockVault()` call on failure lives in BiometricGateViewModel
 * instead, where a proper coroutine scope already exists. This class is
 * pure state coordination plus the BiometricPrompt call itself, which is
 * not a suspend API.
 */
@Singleton
class BiometricGateController @Inject constructor(
    private val sessionManager: SessionManager,
) {
    private val _isGateActive = MutableStateFlow(false)
    val isGateActive: StateFlow<Boolean> = _isGateActive.asStateFlow()

    // Set right before launching a trusted external Activity this app
    // itself started on purpose — the file picker (upload) and the
    // "view downloaded file" intent are the two current cases (see
    // FileBrowserScreen). Launching either causes THIS app's process to
    // go through onStop/onStart, which ProcessLifecycleOwner can't tell
    // apart from the user actually leaving to another app — without this
    // flag, picking a file to upload or viewing a downloaded file would
    // both spuriously re-trigger the biometric prompt the instant control
    // returns. Plain var, not AtomicBoolean: every access happens on the
    // main thread (Activity lifecycle callbacks and Compose UI callbacks
    // both are), so there's no real concurrency to guard against.
    private var suppressNextForeground = false

    fun notifyLaunchingTrustedActivity() {
        suppressNextForeground = true
    }

    // One-shot signal: true when auth fails or is cancelled, telling
    // VaultNavGraph to lock the vault and return to the vault list — the
    // same fail-securely default the app's existing explicit Lock button
    // already uses, not a softer "just stay blocked" behavior.
    private val _shouldLockAndReturnToList = MutableStateFlow(false)
    val shouldLockAndReturnToList: StateFlow<Boolean> = _shouldLockAndReturnToList.asStateFlow()

    fun consumeLockSignal() {
        _shouldLockAndReturnToList.value = false
    }

    /**
     * Called from MainActivity's ProcessLifecycleOwner observer on every
     * ON_START (app returned to foreground, including the initial cold
     * launch — harmless there too, since the early return below covers
     * it the same way). No-ops if no vault is currently unlocked:
     * nothing sensitive to protect yet, so no reason to interrupt the
     * server-setup or vault-list screens with a prompt.
     */
    fun onAppForegrounded(activity: FragmentActivity) {
        if (suppressNextForeground) {
            // Consumed unconditionally, regardless of whether a vault is
            // even unlocked below — a stale flag must never leak into a
            // later, unrelated background/foreground cycle. Known,
            // accepted limitation: this is a one-shot suppression, not a
            // matched request/response pair — if a picker Activity itself
            // triggers more than one onStop/onStart cycle on this app
            // before returning a result (uncommon, but not impossible on
            // every device/picker combination), only the first of those
            // extra cycles is suppressed. Not worth a more complex
            // request-matching mechanism for an edge case this narrow.
            suppressNextForeground = false
            return
        }
        if (sessionManager.vaultToken == null) return
        if (!sessionManager.biometricGateEnabled) return

        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No enrolled biometric AND no device PIN/pattern/password
            // set up at all — can't force a security measure the device
            // itself doesn't support. Falls back to FLAG_SECURE alone
            // rather than locking the user out with no way back in.
            return
        }

        _isGateActive.value = true

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _isGateActive.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Covers explicit cancel (back button, tapping
                    // outside the system dialog) and hard errors (too
                    // many failed attempts, hardware unavailable, etc.)
                    // the same way — both mean "did not prove identity,"
                    // so both lock. Deliberately does NOT lock on a
                    // single wrong fingerprint (onAuthenticationFailed,
                    // not overridden here) — the system prompt already
                    // lets the user retry a few times before it gives up
                    // and calls this instead.
                    _isGateActive.value = false
                    _shouldLockAndReturnToList.value = true
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Verify it's you to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            // No setNegativeButtonText: the platform requires omitting it
            // when DEVICE_CREDENTIAL is an allowed authenticator — the
            // system supplies the "use PIN instead"/cancel affordance
            // itself in that combination, and setting both throws.
            .build()

        prompt.authenticate(promptInfo)
    }
}
