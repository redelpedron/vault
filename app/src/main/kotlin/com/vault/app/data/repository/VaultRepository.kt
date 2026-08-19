// The port between presentation/domain and the actual network transport
// — ViewModels depend on this interface, never on VaultApi or Retrofit
// directly, so a ViewModel test can supply a fake without touching
// OkHttp. DTOs are used directly as the return/param types rather than a
// separate parallel domain-model layer: this app's whole job is to
// render server state and forward user actions back to it — there is no
// client-side business logic to protect behind an extra mapping layer,
// unlike the Go backend's own domain/application split, which exists
// because *it* owns real invariants (uniqueness, versioning, quota...).
// Introducing one here on principle would be ceremony without benefit;
// see SKILL.md "avoid interface pollution" / "never introduce complexity
// without measurable benefit".
package com.vault.app.data.repository

import com.vault.app.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.InputStream

interface VaultRepository {
    // Vaults
    suspend fun listVaults(): Result<List<VaultDto>>
    suspend fun createVault(name: String, password: String): Result<VaultUnlockResponse>
    suspend fun unlockVault(vaultId: String, password: String): Result<VaultUnlockResponse>
    suspend fun lockVault(): Result<Unit>
    suspend fun deleteVault(vaultId: String, password: String): Result<Unit>

    // Files
    suspend fun listFiles(folderId: String?): Result<List<FileListItemDto>>
    suspend fun upload(
        fileName: String,
        mimeType: String,
        folderId: String?,
        openStream: () -> InputStream,
        contentLength: Long,
    ): Result<UploadResponse>
    suspend fun download(id: String): Result<Response<ResponseBody>>
    suspend fun createFolder(name: String, parentFolderId: String?): Result<FolderResponse>
    suspend fun rename(id: String, newName: String): Result<Unit>
    suspend fun copy(id: String, targetFolderId: String): Result<UploadResponse>
    suspend fun move(id: String, targetFolderId: String): Result<Unit>
    suspend fun deleteFile(id: String): Result<Unit>
    suspend fun breadcrumb(folderId: String?): Result<List<BreadcrumbItemDto>>
    suspend fun listFolders(): Result<List<FolderTreeItemDto>>

    // Versioning
    suspend fun listVersions(id: String): Result<List<VersionListItemDto>>
    suspend fun restoreVersion(id: String): Result<RestoreVersionResponse>
    suspend fun createVersion(
        id: String,
        mimeType: String,
        openStream: () -> InputStream,
        contentLength: Long,
    ): Result<CreateVersionResponse>

    // Bulk
    suspend fun bulkDelete(ids: List<String>): Result<BulkResultDto>
    suspend fun bulkMove(ids: List<String>, targetFolderId: String): Result<BulkResultDto>
    suspend fun bulkCopy(ids: List<String>, targetFolderId: String): Result<BulkResultDto>
    suspend fun bulkDownload(ids: List<String>): Result<Response<ResponseBody>>
}
