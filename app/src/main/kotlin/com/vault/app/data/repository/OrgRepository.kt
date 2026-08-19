// The port between presentation and the org/user-management transport —
// see VaultRepository.kt for why this app uses DTOs directly rather than
// a separate domain-model layer; the same reasoning applies here.
package com.vault.app.data.repository

import com.vault.app.data.remote.dto.*

interface OrgRepository {
    // Registration / login / logout
    suspend fun registerOrg(orgName: String, email: String, password: String): Result<RegisterOrgResponse>
    suspend fun login(email: String, password: String): Result<LoginResponse>
    suspend fun logout(): Result<Unit>

    // User management
    suspend fun listUsers(): Result<List<UserDto>>
    suspend fun inviteUser(email: String, password: String, role: String): Result<UserDto>
    suspend fun removeUser(userId: String): Result<Unit>
    suspend fun changeRole(userId: String, role: String): Result<Unit>

    // Org-scoped vaults
    suspend fun listOrgVaults(): Result<List<VaultDto>>
    suspend fun createOrgVault(name: String, password: String): Result<VaultUnlockResponse>
    suspend fun deleteOrgVault(vaultId: String, password: String): Result<Unit>
}
