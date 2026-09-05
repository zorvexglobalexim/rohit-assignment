package com.rohit.videoprocessor.data.export

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MediaStore`/`ContentResolver` aren't usable under the plain JVM unit-test
 * stub jar, so this runs on a real device/emulator instead. Each test cleans
 * up the entry it creates so repeated runs don't pollute the device Gallery.
 */
@RunWith(AndroidJUnit4::class)
class GallerySaverInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val gallerySaver = GallerySaver(context)

    @Test
    fun save_createsARealReadableMediaStoreEntry() = runBlocking {
        val bitmap = solidColorBitmap(64, 64, Color.RED)
        val displayName = "gallery_saver_test_${System.currentTimeMillis()}.png"

        val result = gallerySaver.save(bitmap, displayName)

        check(result is GallerySaver.SaveResult.Success) { "expected Success, got $result" }
        try {
            val uri = result.uri
            assertNotNull(uri)

            // Prove it's real: another component (or us, standing in for one) can open and decode it.
            context.contentResolver.openInputStream(uri)?.use { input ->
                val decoded = android.graphics.BitmapFactory.decodeStream(input)
                assertNotNull("saved image should be decodable", decoded)
                assertEquals(64, decoded!!.width)
                assertEquals(64, decoded.height)
            } ?: error("could not open an input stream for $uri")
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }

    @Test
    fun save_setsCorrectMimeTypeAndDisplayName() = runBlocking {
        val bitmap = solidColorBitmap(32, 32, Color.BLUE)
        val displayName = "gallery_saver_mime_test_${System.currentTimeMillis()}.png"

        val result = gallerySaver.save(bitmap, displayName)
        check(result is GallerySaver.SaveResult.Success) { "expected Success, got $result" }

        try {
            assertEquals("image/png", context.contentResolver.getType(result.uri))
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }

    @Test
    fun save_returnedUriIsAContentUriWithAValidRowId() = runBlocking {
        val bitmap = solidColorBitmap(16, 16, Color.GREEN)
        val result = gallerySaver.save(bitmap, "gallery_saver_uri_test_${System.currentTimeMillis()}.png")
        check(result is GallerySaver.SaveResult.Success) { "expected Success, got $result" }

        try {
            assertEquals("content", result.uri.scheme)
            assertTrue(ContentUris.parseId(result.uri) > 0)
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }

    private fun solidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawColor(color)
        return bitmap
    }
}
