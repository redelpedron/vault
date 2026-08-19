package com.vault.app.data.remote

import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a single suspending Retrofit call and folds every failure mode
 * this app needs to distinguish in the UI into one [Result]:
 *   - HttpException (4xx/5xx): message becomes the server's plain-text
 *     error body — see mapError/displayMessage in internal/adapters/
 *     http/handlers.go, which is written specifically to be short and
 *     safe to show a user (it never leaks unreviewed internals).
 *   - IOException (no connection, wrong IP, DynamicBaseUrlInterceptor's
 *     "no server configured" case, TLS failure, timeout): message is
 *     already human-readable.
 *   - Anything else: rethrown. A bug in this app's own request-building
 *     code should crash loudly in a debug build, not be silently folded
 *     into "something went wrong".
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val serverMessage = e.response()?.errorBody()?.string()?.trim()
    Result.failure(ApiException(serverMessage?.takeIf { it.isNotEmpty() } ?: e.message(), e))
} catch (e: IOException) {
    Result.failure(ApiException(e.message ?: "Network error", e))
}

class ApiException(message: String?, cause: Throwable) : Exception(message, cause)
