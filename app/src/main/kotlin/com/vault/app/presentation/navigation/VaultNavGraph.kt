package com.vault.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vault.app.presentation.browser.FileBrowserScreen
import com.vault.app.presentation.folderpicker.FolderPickerScreen
import com.vault.app.presentation.orgauth.OrgAuthScreen
import com.vault.app.presentation.security.BiometricGateViewModel
import com.vault.app.presentation.serversetup.ServerSetupScreen
import com.vault.app.presentation.settings.SettingsScreen
import com.vault.app.presentation.usermanagement.UserManagementScreen
import com.vault.app.presentation.vaultlist.VaultListScreen

@Composable
fun VaultNavGraph(
    rootViewModel: RootViewModel = hiltViewModel(),
    gateViewModel: BiometricGateViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val gateActive by gateViewModel.controller.isGateActive.collectAsState()
    val shouldLock by gateViewModel.controller.shouldLockAndReturnToList.collectAsState()

    // Failed/cancelled biometric auth locks the vault and returns to the
    // vault list — same navigation pattern onLockVault already uses
    // below, triggered here instead of from any one screen, since the
    // gate itself isn't tied to any single screen.
    LaunchedEffect(shouldLock) {
        if (shouldLock) {
            gateViewModel.lockAndConsume()
            navController.navigate(Destinations.VAULT_LIST) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = rootViewModel.startDestination) {

            composable(Destinations.SERVER_SETUP) {
                ServerSetupScreen(
                    onSaved = {
                        navController.navigate(Destinations.VAULT_LIST) {
                            popUpTo(Destinations.SERVER_SETUP) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.VAULT_LIST) {
                VaultListScreen(
                    onVaultUnlocked = {
                        navController.navigate(Destinations.browserRoute("")) {
                            popUpTo(Destinations.VAULT_LIST) { inclusive = true }
                        }
                    },
                    onChangeServer = {
                        navController.navigate(Destinations.SERVER_SETUP) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSignIn = { navController.navigate(Destinations.ORG_AUTH) },
                    onOpenUserManagement = { navController.navigate(Destinations.USER_MANAGEMENT) },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                )
            }

            composable(Destinations.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.ORG_AUTH) {
                OrgAuthScreen(
                    onAuthenticated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Destinations.USER_MANAGEMENT) {
                UserManagementScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Destinations.BROWSER_PATTERN,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) {
                FileBrowserScreen(
                    onNavigateToFolder = { id -> navController.navigate(Destinations.browserRoute(id)) },
                    onBreadcrumbClick = { id ->
                        val target = Destinations.browserRoute(id)
                        // Prefer popping back to an existing instance of that folder on
                        // the stack (the common case: user drilled straight down and is
                        // now backing straight up) over pushing a fresh copy, which
                        // would otherwise let the back stack grow unbounded if someone
                        // bounces between breadcrumb entries repeatedly. Falls back to
                        // a normal push if that folder was reached some other way (e.g.
                        // "root" when the user unlocked straight into a deep link) —
                        // see FileBrowserScreen's README note on this as a known,
                        // acceptable milestone-1 limitation rather than a real nav bug.
                        val poppedToExisting = navController.popBackStack(target, inclusive = false)
                        if (!poppedToExisting) navController.navigate(target)
                    },
                    onLockVault = {
                        navController.navigate(Destinations.VAULT_LIST) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToPicker = { operation, itemIds ->
                        navController.navigate(Destinations.folderPickerRoute(operation, itemIds))
                    },
                )
            }

            composable(
                route = Destinations.FOLDER_PICKER_PATTERN,
                arguments = listOf(
                    navArgument("operation") { type = NavType.StringType },
                    navArgument("itemIds") { type = NavType.StringType },
                ),
            ) {
                FolderPickerScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        if (gateActive) {
            // Covers whatever screen is currently the active nav
            // destination, regardless of which one — the whole point of
            // wrapping the NavHost rather than any single screen owning
            // this. Appears the instant ON_START fires (see
            // BiometricGateController), before the system's biometric
            // dialog itself even finishes appearing, so there's no frame
            // where returning from background shows real content before
            // the prompt does.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Vault locked, verify identity to continue",
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                    )
                }
            }
        }
    }
}
