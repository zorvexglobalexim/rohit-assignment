package com.rohit.videoprocessor.data.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a [Bitmap] to the app's cache dir and returns a `content://` [Uri]
 * for it via [FileProvider] - sharing must never hand another app a raw
 * `file://` path (blocked by `FileUriExposedException` on modern Android),
 * and doesn't require the image to already be saved to the Gallery first.
 * The directory here (`cacheDir/shared_images/`) must match the
 * `<cache-path>` declared in `res/xml/file_paths.xml`.
 */
class ShareImageProvider(private val context: Context) {

    suspend fun createShareUri(bitmap: Bitmap, fileName: String): Uri = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_SUBDIRECTORY).apply { mkdirs() }
        val file = File(shareDir, fileName)
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Bitmap compression failed." }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    companion object {
        private const val SHARE_SUBDIRECTORY = "shared_images"
    }
}
