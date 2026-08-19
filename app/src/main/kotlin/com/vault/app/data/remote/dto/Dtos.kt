// Wire DTOs for the vault HTTP API. Field names and nullability are
// copied field-for-field from internal/domain/models/models.go on the
// server — that file, not this one, is the source of truth. If a field
// is renamed or added there, this file must be updated to match; there
// is no schema validation between the two beyond what CI catches at
// runtime.
package com.vault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncSettingsDto(
    val localDir: String,
    val direction: String,
    val intervalSeconds: Int,
    val updatedAt: String,
)

@Serializable
data class VaultDto(
    val id: String,
    val name: String,
    val salt: String,
    val keyHash: String,
    val keyScheme: String,
    val kdfAlgo: String,
    val kdfMemory: Long? = null,
    val kdfIterations: Long? = null,
    val kdfParallel: Int? = null,
    val createdAt: String,
    val folderCount: Int,
    val fileCount: Int,
    val totalSize: Long,
    val isUnlocked: Boolean,
    val sync: SyncSettingsDto? = null,
)

@Serializable
data class VaultCreateRequest(val name: String, val password: String)

@Serializable
data class VaultUnlockRequest(val password: String)

@Serializable
data class VaultDeleteRequest(val password: String)

@Serializable
data class VaultUnlockResponse(
    val vaultId: String,
    val token: String,
    val name: String,
)

@Serializable
data class FileListItemDto(
    val id: String,
    val originalName: String,
    val size: Long,
    val mimeType: String,
    val uploadedAt: String,
    val isFolder: Boolean,
)

@Serializable
data class UploadResponse(val id: String, val originalName: String)

@Serializable
data class VersionListItemDto(
    val id: String,
    val versionNum: Int,
    val size: Long,
    val mimeType: String,
    val uploadedAt: String,
    val isCurrent: Boolean,
)

@Serializable
data class CreateVersionResponse(val documentId: String, val versionNum: Int)

@Serializable
data class RestoreVersionResponse(val documentId: String, val versionNum: Int)

@Serializable
data class FolderRequest(val name: String)

@Serializable
data class FolderResponse(val id: String, val name: String)

@Serializable
data class RenameRequest(val name: String)

@Serializable
data class MoveRequest(@SerialName("folderId") val folderId: String)

@Serializable
data class BulkIdsRequest(val ids: List<String>)

@Serializable
data class BulkMoveRequest(val ids: List<String>, val folderId: String)

@Serializable
data class BulkFailureDto(val id: String, val reason: String)

@Serializable
data class BulkResultDto(
    val deleted: Int = 0,
    val moved: Int = 0,
    val copied: Int = 0,
    val failed: List<BulkFailureDto> = emptyList(),
)

@Serializable
data class FolderTreeItemDto(val id: String, val name: String)

@Serializable
data class BreadcrumbItemDto(val id: String, val name: String)

// ---- Org / user management ----
// Field-for-field from internal/adapters/http/org.go's request/response
// structs and internal/domain/org.User — see that file for the four
// Role values (admin/contributor/viewer/auditor), defined in
// internal/domain/auth/roles.go. Role is transcribed as plain String
// here rather than a Kotlin enum: the server is free to add a role
// value in the future, and a strict enum would make kotlinx.serialization
// throw on deserializing it instead of just rendering unrecognized text —
// same forward-compatibility reasoning as Json.ignoreUnknownKeys in
// NetworkModule.

@Serializable
data class RegisterOrgRequest(val orgName: String, val email: String, val password: String)

@Serializable
data class RegisterOrgResponse(val orgId: String, val userId: String, val email: String, val token: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val userId: String, val orgId: String, val role: String)

@Serializable
data class UserDto(
    val id: String,
    val orgId: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: String,
)

@Serializable
data class InviteUserRequest(val email: String, val password: String, val role: String)

@Serializable
data class ChangeRoleRequest(val role: String)

@Serializable
data class OrgVaultDeleteRequest(val password: String)
