package com.rohit.videoprocessor.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rohit.videoprocessor.domain.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves display metadata for a picked video Uri via ContentResolver.
 * Runs off the main thread since resolver queries touch disk/IPC.
 */
class VideoInfoResolver(private val context: Context) {

    suspend fun resolve(uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment ?: "Unknown"
        var size = -1L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        VideoInfo(
            uri = uri,
            displayName = displayName,
            sizeBytes = size,
            mimeType = context.contentResolver.getType(uri),
        )
    }
}
