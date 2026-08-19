package com.vault.app.data.remote

import com.vault.app.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches X-Vault-Token and X-User-Token to every outgoing request when
 * the corresponding session exists — matching static/app.js's `api()`
 * helper, which does the same unconditionally rather than only on routes
 * that need it. That's safe for both headers, independently: VaultAuth
 * (middleware.go) only ever reads X-Vault-Token and UserAuth
 * (user_middleware.go) only ever reads X-User-Token — each ignores the
 * other's header entirely, so sending both on every request is inert
 * wherever one of them isn't needed, not a privilege-escalation risk.
 * This is also why no per-path routing logic lives here: there's nothing
 * to route, since an extra header a handler never reads has no effect.
 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        sessionManager.vaultToken?.let { builder.header("X-Vault-Token", it) }
        sessionManager.userToken?.let { builder.header("X-User-Token", it) }
        return chain.proceed(builder.build())
    }
}
