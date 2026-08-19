// Retrofit contract for the org/user-management server routes, transcribed
// directly from internal/adapters/http/org.go's RegisterRoutes — see that
// file for the exact request/response shapes this was copied from. Unlike
// VaultApi, every route here except register/login requires a valid
// X-User-Token (attached by AuthInterceptor, same as X-Vault-Token is for
// VaultApi) — enforced server-side by UserAuth, not by anything in this
// interface.
package com.vault.app.data.remote

import com.vault.app.data.remote.dto.*
import retrofit2.http.*

interface OrgApi {

    // ---- Registration / login / logout ----

    @POST("api/orgs")
    suspend fun registerOrg(@Body body: RegisterOrgRequest): RegisterOrgResponse

    @POST("api/orgs/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/orgs/logout")
    suspend fun logout()

    // ---- User management ----

    @GET("api/orgs/users")
    suspend fun listUsers(): List<UserDto>

    @POST("api/orgs/users")
    suspend fun inviteUser(@Body body: InviteUserRequest): UserDto

    // @DELETE doesn't support @Query-only bodies any differently from GET
    // here — no body on this one, matching handleRemoveUser, which never
    // calls readJSON.
    @DELETE("api/orgs/users/remove")
    suspend fun removeUser(@Query("userId") userId: String)

    @POST("api/orgs/users/role")
    suspend fun changeRole(@Query("userId") userId: String, @Body body: ChangeRoleRequest)

    // ---- Org-scoped vaults ----

    @GET("api/orgs/vaults")
    suspend fun listOrgVaults(): List<VaultDto>

    @POST("api/orgs/vaults")
    suspend fun createOrgVault(@Body body: VaultCreateRequest): VaultUnlockResponse

    // @DELETE doesn't support @Body in Retrofit; @HTTP(hasBody = true) is
    // the documented workaround — matching handleDeleteVault's
    // readJSON(w, r, &req), which reads a body regardless of method (same
    // pattern as VaultApi.deleteVault).
    @HTTP(method = "DELETE", path = "api/orgs/vaults/delete", hasBody = true)
    suspend fun deleteOrgVault(
        @Query("vaultId") vaultId: String,
        @Body body: OrgVaultDeleteRequest,
    )
}
