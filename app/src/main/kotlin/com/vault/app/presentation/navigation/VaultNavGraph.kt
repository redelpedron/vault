package com.vault.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vault.app.presentation.browser.FileBrowserScreen
import com.vault.app.presentation.folderpicker.FolderPickerScreen
import com.vault.app.presentation.orgauth.OrgAuthScreen
import com.vault.app.presentation.serversetup.ServerSetupScreen
import com.vault.app.presentation.usermanagement.UserManagementScreen
import com.vault.app.presentation.vaultlist.VaultListScreen

@Composable
fun VaultNavGraph(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()

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
            )
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
}
