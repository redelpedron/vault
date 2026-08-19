package com.vault.app.presentation.browser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** What we need from a SAF-picked content:// Uri to build an upload request. */
data class PickedFile(val name: String, val size: Long, val mimeType: String)

fun resolvePickedFile(context: Context, uri: Uri): PickedFile {
    var name = "upload"
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
    }
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return PickedFile(name, size, mimeType)
}
