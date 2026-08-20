package com.vault.app.presentation.browser

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.vault.app.data.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.FileSystem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * The "model" a FileListItemDto maps to for AsyncImage — see FileRow's
 * usage. Only constructed by the screen when item.mimeType is one it
 * actually wants a thumbnail for; ThumbnailFetcher itself doesn't filter
 * by type; it just fetches whatever request it's given.
 */
data class ThumbnailRequest(val fileId: String, val mimeType: String)

/** True for anything this app currently knows how to thumbnail. Broader
 * than the original ask's "jpg, png, or video" — any raster image type
 * BitmapFactory already decodes natively (webp, gif, ...) comes along for
 * free through the same code path, at no extra cost or complexity. */
fun isThumbnailable(mimeType: String): Boolean =
    mimeType.startsWith("image/") || mimeType.startsWith("video/")

/**
 * Cache key for ThumbnailRequest — required alongside the Fetcher for any
 * custom data type to be memory-cacheable (Coil's own rule, not specific
 * to this app). Keyed by file ID alone: the underlying file's bytes don't
 * change without a new file ID (versioning creates version records, not
 * in-place mutation of the current file ID) so there's no need to fold
 * mimeType or anything else into the key.
 */
class ThumbnailKeyer : Keyer<ThumbnailRequest> {
    override fun key(data: ThumbnailRequest, options: Options): String = "thumb:${data.fileId}"
}

/**
 * Fetches a thumbnail through this app's own authenticated VaultRepository
 * — not a plain URL — since every file lives behind AuthInterceptor's
 * X-Vault-Token and is decrypted server-side per request. This is why a
 * custom Fetcher exists at all rather than just pointing AsyncImage at a
 * URL string: Coil's built-in network fetchers have no way to know about
 * this app's session/auth model.
 *
 * Images: the server already returns fully decrypted, original bytes —
 * handed straight to Coil's own decoder, which downsamples to the
 * request's target size during decode (BitmapFactory.Options.inSampleSize
 * under the hood). No pre-resizing needed here.
 *
 * Video: MediaMetadataRetriever needs a file path, not a live stream, so
 * this downloads to a scratch temp file, extracts one frame, and deletes
 * the temp file immediately — the extracted frame is what Coil's own
 * disk/memory cache actually holds onto going forward, not the source
 * video.
 *
 * Known cost, stated plainly: video thumbnails download the ENTIRE video
 * to grab one frame. The backend has no byte-range/partial-download
 * support (confirmed: /api/download streams the full body, no Range
 * header handling), so there's no cheaper way to get "just the first few
 * seconds" from this client alone. Bounded by LazyColumn only fetching
 * for on-screen rows, not the whole list — but a real fix would be
 * backend work (Range support, or server-side thumbnail pre-generation),
 * not something to solve here.
 */
class ThumbnailFetcher(
    private val request: ThumbnailRequest,
    private val repository: VaultRepository,
    private val context: Context,
) : Fetcher {

    class Factory @Inject constructor(
        private val repository: VaultRepository,
        @ApplicationContext private val context: Context,
    ) : Fetcher.Factory<ThumbnailRequest> {
        override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, repository, context)
    }

    override suspend fun fetch(): FetchResult? {
        val response = repository.download(request.fileId).getOrNull() ?: return null
        val body = response.body() ?: return null
        return if (request.mimeType.startsWith("video/")) {
            fetchVideoFrame(body)
        } else {
            SourceFetchResult(
                source = ImageSource(source = body.source(), fileSystem = FileSystem.SYSTEM),
                mimeType = request.mimeType,
                dataSource = DataSource.NETWORK,
            )
        }
    }

    private suspend fun fetchVideoFrame(body: ResponseBody): FetchResult? = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("vault_thumb_", ".tmp", context.cacheDir)
        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(tempFile.absolutePath)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
            frame ?: return@withContext null
            val bytes = ByteArrayOutputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            SourceFetchResult(
                source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.NETWORK,
            )
        } finally {
            tempFile.delete()
        }
    }
}
