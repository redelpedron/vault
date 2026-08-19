package com.vault.app.presentation.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

/**
 * Streams a download response's body to this app's own external-files
 * "downloads" subfolder (must match res/xml/file_paths.xml's
 * <external-files-path name="downloads" path="downloads/">) and builds a
 * viewable/shareable FileProvider Uri for it — never a raw file:// Uri,
 * which triggers a FileUriExposedException on API 24+ if handed to
 * another app.
 */
class FileDownloader(private val context: Context) {

    fun saveToDownloads(body: ResponseBody, fileName: String): File {
        val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val target = File(dir, sanitize(fileName))
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        }
        return target
    }

    fun viewIntentFor(file: File, mimeType: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // Defense-in-depth mirror of the server's own sanitizeZipEntryName
    // (internal/adapters/http/file.go) — a name is already validated at
    // upload time server-side, but nothing stops a stored legacy/pre-
    // validation record from containing a path separator.
    private fun sanitize(name: String): String {
        val cleaned = name.replace("/", "_").replace("\\", "_")
        return cleaned.ifBlank { "file" }
    }
}
