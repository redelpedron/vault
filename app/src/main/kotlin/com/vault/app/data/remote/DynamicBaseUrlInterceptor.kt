package com.vault.app.data.remote

import com.vault.app.data.local.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Retrofit is built once, at app start, against a placeholder base URL
 * (see NetworkModule) — but the *real* server address isn't known until
 * the user types it into Settings, and can change later without a
 * reinstall (DHCP reassigning the vault host's LAN IP, moving to a
 * different network, etc). Rebuilding the whole Retrofit/OkHttp stack
 * every time that changes is unnecessary churn; instead this interceptor
 * rewrites just the scheme/host/port of each outgoing request to
 * whatever SessionManager currently holds, leaving the path and query
 * string Retrofit already built untouched.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val configured = sessionManager.serverBaseUrl
            ?: throw IOException("No vault server configured yet — set one in Settings.")

        val configuredUrl = configured.toHttpUrlOrNull()
            ?: throw IOException("Configured server URL is invalid: $configured")

        val original = chain.request()
        val rewritten = original.url.newBuilder()
            .scheme(configuredUrl.scheme)
            .host(configuredUrl.host)
            .port(configuredUrl.port)
            .build()

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
