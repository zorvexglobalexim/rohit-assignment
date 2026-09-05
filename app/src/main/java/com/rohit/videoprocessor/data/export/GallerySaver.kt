package com.rohit.videoprocessor.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Saves a [Bitmap] into the device's Gallery via [MediaStore] - never direct
 * file-system access to shared storage, so this works correctly under
 * scoped storage (API 29+) with no permission at all, and under legacy
 * storage (API 26-28) with [android.Manifest.permission.WRITE_EXTERNAL_STORAGE]
 * (declared with `maxSdkVersion="28"` in the manifest, so it's absent/inert
 * on newer devices - see the manifest).
 *
 * Callers on API 26-28 are responsible for holding that permission *before*
 * calling [save] (a runtime permission request is a UI-layer concern - see
 * [com.rohit.videoprocessor.ui.result.ResultScreen]); [save] itself doesn't
 * check or request it, it just surfaces the resulting [SecurityException] as
 * a normal [SaveResult.Failure] if it's missing.
 */
class GallerySaver(private val context: Context) {

    sealed interface SaveResult {
        data class Success(val uri: Uri) : SaveResult
        data class Failure(val message: String) : SaveResult
    }

    suspend fun save(bitmap: Bitmap, displayName: String): SaveResult = withContext(Dispatchers.IO) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaScopedStorage(bitmap, displayName)
            } else {
                saveViaLegacyStorage(bitmap, displayName)
            }
            SaveResult.Success(uri)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            SaveResult.Failure(t.message ?: "Failed to save the image to your Gallery.")
        }
    }

    /** API 29+: MediaStore + RELATIVE_PATH, staged via IS_PENDING so partial writes are never visible to other apps. */
    private fun saveViaScopedStorage(bitmap: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not return a Uri for the new image.")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Bitmap compression failed." }
            } ?: error("Could not open an output stream for $uri.")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            // Don't leave a half-written, permanently-pending entry behind.
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    /** API 26-28: no RELATIVE_PATH/IS_PENDING (added in API 29) - write the file directly, then index it via DATA. */
    private fun saveViaLegacyStorage(bitmap: Bitmap, displayName: String): Uri {
        @Suppress("DEPRECATION")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumDir = File(picturesDir, ALBUM_NAME)
        if (!albumDir.exists() && !albumDir.mkdirs()) {
            error("Could not create $albumDir.")
        }

        val file = File(albumDir, displayName)
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Bitmap compression failed." }
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not return a Uri for the new image.")
    }

    companion object {
        private const val MIME_TYPE = "image/png"
        private const val ALBUM_NAME = "VideoProcessor"
    }
}
