package com.vault.app.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vault.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BiometricGateController is a plain Hilt singleton, not a ViewModel, so
 * it can't be obtained via hiltViewModel() directly from a Composable —
 * this thin wrapper exists to bridge that, matching every other screen's
 * hiltViewModel() access pattern rather than introducing a Hilt
 * EntryPoint (a pattern nothing else in this app uses).
 *
 * Also owns the actual repository.lockVault() call on gate failure —
 * BiometricGateController deliberately holds no VaultRepository/suspend
 * dependency, so that call happens here instead, where viewModelScope
 * already exists.
 */
@HiltViewModel
class BiometricGateViewModel @Inject constructor(
    val controller: BiometricGateController,
    private val repository: VaultRepository,
) : ViewModel() {
    fun lockAndConsume() {
        viewModelScope.launch {
            repository.lockVault()
            controller.consumeLockSignal()
        }
    }
}
