package com.vault.app.presentation.navigation

object Destinations {
    const val SERVER_SETUP = "server_setup"
    const val VAULT_LIST = "vault_list"
    const val ORG_AUTH = "org_auth"
    const val USER_MANAGEMENT = "user_management"

    // folderId is nav-arg-encoded as the literal string "root" for the
    // vault's top level, since Compose Navigation's default string arg
    // handling treats an empty segment as "argument missing" rather than
    // "empty argument" — this is translated back to "" (what the API
    // expects for the root folder) at the one call site that reads it,
    // FileBrowserViewModel.
    const val BROWSER_ROOT_SENTINEL = "root"
    const val BROWSER_PATTERN = "browser/{folderId}"
    fun browserRoute(folderId: String) = "browser/${folderId.ifEmpty { BROWSER_ROOT_SENTINEL }}"

    // itemIds is comma-joined — fine given these are server-generated IDs
    // (UUID-style), never user-supplied text that could itself contain a
    // comma. operation is the literal string "move" or "copy".
    const val FOLDER_PICKER_PATTERN = "folder_picker/{operation}/{itemIds}"
    fun folderPickerRoute(operation: String, itemIds: List<String>) =
        "folder_picker/$operation/${itemIds.joinToString(",")}"
}
