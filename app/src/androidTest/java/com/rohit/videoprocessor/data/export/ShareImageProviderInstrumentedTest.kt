package com.rohit.videoprocessor.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `FileProvider`/content resolution aren't usable under the plain JVM
 * unit-test stub jar, so this runs on a real device/emulator instead.
 */
@RunWith(AndroidJUnit4::class)
class ShareImageProviderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val shareImageProvider = ShareImageProvider(context)

    @Test
    fun createShareUri_producesAContentUriReadableViaFileProvider() = runBlocking {
        val bitmap = solidColorBitmap(48, 48, Color.MAGENTA)

        val uri = shareImageProvider.createShareUri(bitmap, "share_test_${System.currentTimeMillis()}.png")

        assertEquals("content", uri.scheme)
        assertEquals("com.rohit.videoprocessor.fileprovider", uri.authority)

        // Round-trip proof: what FileProvider hands back is actually readable and matches what was written.
        context.contentResolver.openInputStream(uri)?.use { input ->
            val decoded = BitmapFactory.decodeStream(input)
            assertNotNull("shared image should be decodable", decoded)
            assertEquals(48, decoded!!.width)
            assertEquals(48, decoded.height)
        } ?: error("could not open an input stream for $uri")
    }

    @Test
    fun createShareUri_eachCallProducesAFreshUsableFile() = runBlocking {
        val bitmapA = solidColorBitmap(20, 20, Color.CYAN)
        val bitmapB = solidColorBitmap(30, 30, Color.YELLOW)

        val uriA = shareImageProvider.createShareUri(bitmapA, "share_test_a_${System.currentTimeMillis()}.png")
        val uriB = shareImageProvider.createShareUri(bitmapB, "share_test_b_${System.currentTimeMillis()}.png")

        val sizeA = context.contentResolver.openInputStream(uriA)?.use { BitmapFactory.decodeStream(it) }
        val sizeB = context.contentResolver.openInputStream(uriB)?.use { BitmapFactory.decodeStream(it) }

        assertEquals(20, sizeA?.width)
        assertEquals(30, sizeB?.width)
    }

    private fun solidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return bitmap
    }
}
