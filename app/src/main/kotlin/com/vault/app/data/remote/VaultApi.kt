// Retrofit contract for the vault server. Every route here is transcribed
// directly from internal/adapters/http/{vault,file}.go and cross-checked
// against static/app.js's existing, working fetch() calls — not guessed
// from REST convention. Where the two disagreed on HTTP method (they
// never did), app.js would win: it's the client already proven to work
// against this exact server.
//
// Paths are relative (no leading '/') because the base URL is resolved
// per-request by DynamicBaseUrlInterceptor against whatever the user has
// configured in Settings, not by Retrofit's own fixed baseUrl() — see
// that class for why.
package com.vault.app.data.remote

import com.vault.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VaultApi {

    // ---- Vault management (no X-Vault-Token required — see middleware.go:
    // VaultAuth only wraps the file/sync routers, not VaultHandler) ----

    @GET("api/vaults")
    suspend fun listVaults(): List<VaultDto>

    @POST("api/vaults")
    suspend fun createVault(@Body body: VaultCreateRequest): VaultUnlockResponse

    @POST("api/vaults/unlock")
    suspend fun unlockVault(
        @Query("vaultId") vaultId: String,
        @Body body: VaultUnlockRequest,
    ): VaultUnlockResponse

    @POST("api/vaults/lock")
    suspend fun lockVault()

    // @DELETE doesn't support @Body in Retrofit; @HTTP(hasBody = true) is
    // the documented way to send a body on a verb that isn't POST/PUT/PATCH
    // — matching handleDelete's readJSON(w, r, &req) on the server, which
    // reads a body regardless of method.
    @HTTP(method = "DELETE", path = "api/vaults/delete", hasBody = true)
    suspend fun deleteVault(
        @Query("vaultId") vaultId: String,
        @Body body: VaultDeleteRequest,
    )

    // ---- Files (all require X-Vault-Token; attached by AuthInterceptor) ----

    @GET("api/files")
    suspend fun listFiles(@Query("folderId") folderId: String? = null): List<FileListItemDto>

    @Multipart
    @POST("api/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Query("folderId") folderId: String? = null,
    ): UploadResponse

    @Streaming
    @GET("api/download")
    suspend fun download(@Query("id") id: String): Response<ResponseBody>

    @Multipart
    @POST("api/version")
    suspend fun createVersion(
        @Query("id") id: String,
        @Part file: MultipartBody.Part,
    ): CreateVersionResponse

    @GET("api/versions")
    suspend fun listVersions(@Query("id") id: String): List<VersionListItemDto>

    @POST("api/version/restore")
    suspend fun restoreVersion(@Query("id") id: String): RestoreVersionResponse

    @POST("api/folder")
    suspend fun createFolder(
        @Query("folderId") parentFolderId: String? = null,
        @Body body: FolderRequest,
    ): FolderResponse

    @POST("api/rename")
    suspend fun rename(@Query("id") id: String, @Body body: RenameRequest)

    @POST("api/copy")
    suspend fun copy(@Query("id") id: String, @Body body: MoveRequest): UploadResponse

    @POST("api/move")
    suspend fun move(@Query("id") id: String, @Body body: MoveRequest)

    @HTTP(method = "DELETE", path = "api/delete", hasBody = false)
    suspend fun deleteFile(@Query("id") id: String)

    @POST("api/bulk-delete")
    suspend fun bulkDelete(@Body body: BulkIdsRequest): BulkResultDto

    @POST("api/bulk-move")
    suspend fun bulkMove(@Body body: BulkMoveRequest): BulkResultDto

    @POST("api/bulk-copy")
    suspend fun bulkCopy(@Body body: BulkMoveRequest): BulkResultDto

    @Streaming
    @POST("api/bulk-download")
    suspend fun bulkDownload(@Body body: BulkIdsRequest): Response<ResponseBody>

    @GET("api/breadcrumb")
    suspend fun breadcrumb(@Query("folderId") folderId: String? = null): List<BreadcrumbItemDto>

    @GET("api/folders")
    suspend fun listFolders(): List<FolderTreeItemDto>
}
