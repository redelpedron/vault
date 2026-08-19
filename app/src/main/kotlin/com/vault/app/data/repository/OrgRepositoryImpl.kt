package com.vault.app.data.repository

import com.vault.app.data.local.SessionManager
import com.vault.app.data.remote.OrgApi
import com.vault.app.data.remote.apiCall
import com.vault.app.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrgRepositoryImpl @Inject constructor(
    private val api: OrgApi,
    private val sessionManager: SessionManager,
) : OrgRepository {

    override suspend fun registerOrg(orgName: String, email: String, password: String) = apiCall {
        val resp = api.registerOrg(RegisterOrgRequest(orgName, email, password))
        // Registering also logs the caller in (see registerOrgResponse.token
        // server-side) — store the session immediately rather than making
        // the caller register(), then login() as two separate round trips.
        sessionManager.storeUserSession(resp.token, resp.orgId, "admin", resp.email)
        resp
    }

    override suspend fun login(email: String, password: String) = apiCall {
        val resp = api.login(LoginRequest(email, password))
        sessionManager.storeUserSession(resp.token, resp.orgId, resp.role, email)
        resp
    }

    // Deliberately does NOT use apiCall: same reasoning as
    // VaultRepositoryImpl.lockVault — a locally-initiated logout must
    // clear the on-device user session even if the network call fails,
    // since that's the guarantee this device can enforce unilaterally.
    override suspend fun logout(): Result<Unit> {
        runCatching { api.logout() }
        sessionManager.clearUserSession()
        return Result.success(Unit)
    }

    override suspend fun listUsers() = apiCall { api.listUsers() }

    override suspend fun inviteUser(email: String, password: String, role: String) = apiCall {
        api.inviteUser(InviteUserRequest(email, password, role))
    }

    override suspend fun removeUser(userId: String) = apiCall { api.removeUser(userId) }

    override suspend fun changeRole(userId: String, role: String) = apiCall {
        api.changeRole(userId, ChangeRoleRequest(role))
    }

    override suspend fun listOrgVaults() = apiCall { api.listOrgVaults() }

    override suspend fun createOrgVault(name: String, password: String) = apiCall {
        api.createOrgVault(VaultCreateRequest(name, password))
    }

    override suspend fun deleteOrgVault(vaultId: String, password: String) = apiCall {
        api.deleteOrgVault(vaultId, OrgVaultDeleteRequest(password))
    }
}
