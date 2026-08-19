package com.vault.app.data.repository

import com.vault.app.data.local.SessionManager
import com.vault.app.data.remote.StreamingRequestBody
import com.vault.app.data.remote.VaultApi
import com.vault.app.data.remote.apiCall
import com.vault.app.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val api: VaultApi,
    private val sessionManager: SessionManager,
) : VaultRepository {

    override suspend fun listVaults() = apiCall { api.listVaults() }

    override suspend fun createVault(name: String, password: String) = apiCall {
        api.createVault(VaultCreateRequest(name, password))
    }

    override suspend fun unlockVault(vaultId: String, password: String) = apiCall {
        api.unlockVault(vaultId, VaultUnlockRequest(password))
    }

    // Deliberately does NOT use apiCall here: a locally-initiated "lock"
    // must clear the on-device token even if the network call fails
    // (server unreachable, already-expired token) — the whole point of
    // locking is that this device stops being able to act as the vault,
    // which is a local guarantee this app can enforce unilaterally. The
    // server-side invalidation is best-effort on top of that, not a
    // precondition for it. Any network failure is swallowed rather than
    // surfaced: from the user's perspective "lock" still succeeded.
    override suspend fun lockVault(): Result<Unit> {
        runCatching { api.lockVault() }
        sessionManager.clearSession()
        return Result.success(Unit)
    }

    override suspend fun deleteVault(vaultId: String, password: String) = apiCall {
        api.deleteVault(vaultId, VaultDeleteRequest(password))
    }

    override suspend fun listFiles(folderId: String?) = apiCall { api.listFiles(folderId) }

    override suspend fun upload(
        fileName: String,
        mimeType: String,
        folderId: String?,
        openStream: () -> InputStream,
        contentLength: Long,
    ) = apiCall {
        val body = StreamingRequestBody(mimeType.toMediaTypeOrNull(), contentLength, openStream)
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        api.upload(part, folderId)
    }

    override suspend fun download(id: String): Result<Response<ResponseBody>> = apiCall { api.download(id) }

    override suspend fun createFolder(name: String, parentFolderId: String?) = apiCall {
        api.createFolder(parentFolderId, FolderRequest(name))
    }

    override suspend fun rename(id: String, newName: String) = apiCall {
        api.rename(id, RenameRequest(newName))
    }

    override suspend fun copy(id: String, targetFolderId: String) = apiCall {
        api.copy(id, MoveRequest(targetFolderId))
    }

    override suspend fun move(id: String, targetFolderId: String) = apiCall {
        api.move(id, MoveRequest(targetFolderId))
    }

    override suspend fun deleteFile(id: String) = apiCall { api.deleteFile(id) }

    override suspend fun breadcrumb(folderId: String?) = apiCall { api.breadcrumb(folderId) }

    override suspend fun listFolders() = apiCall { api.listFolders() }

    override suspend fun listVersions(id: String) = apiCall { api.listVersions(id) }

    override suspend fun restoreVersion(id: String) = apiCall { api.restoreVersion(id) }

    override suspend fun createVersion(
        id: String,
        mimeType: String,
        openStream: () -> InputStream,
        contentLength: Long,
    ) = apiCall {
        val body = StreamingRequestBody(mimeType.toMediaTypeOrNull(), contentLength, openStream)
        val part = MultipartBody.Part.createFormData("file", "version", body)
        api.createVersion(id, part)
    }

    override suspend fun bulkDelete(ids: List<String>) = apiCall { api.bulkDelete(BulkIdsRequest(ids)) }

    override suspend fun bulkMove(ids: List<String>, targetFolderId: String) = apiCall {
        api.bulkMove(BulkMoveRequest(ids, targetFolderId))
    }

    override suspend fun bulkCopy(ids: List<String>, targetFolderId: String) = apiCall {
        api.bulkCopy(BulkMoveRequest(ids, targetFolderId))
    }

    override suspend fun bulkDownload(ids: List<String>): Result<Response<ResponseBody>> = apiCall {
        api.bulkDownload(BulkIdsRequest(ids))
    }
}
