package com.vault.app.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source

/**
 * Streams multipart file content straight from a content:// Uri's
 * InputStream to the socket, 8 KB at a time — mirroring the Go server's
 * own "never materialises in RAM" design for file content (see the
 * comment on Copy in internal/application/file/move.go). The naive
 * alternative — reading the whole file into a ByteArray and wrapping it
 * in RequestBody.create — works fine for a few-MB test file and then
 * OOMs the first time someone uploads a video from their camera roll.
 *
 * [openStream] is a factory, not a live InputStream, so OkHttp can call
 * writeTo() more than once if it needs to retry the request (e.g. after
 * an auth challenge) without the second attempt reading from an already
 * exhausted stream.
 */
class StreamingRequestBody(
    private val mediaType: MediaType?,
    private val length: Long,
    private val openStream: () -> java.io.InputStream,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        openStream().use { input ->
            input.source().buffer().use { source ->
                sink.writeAll(source)
            }
        }
    }
}
