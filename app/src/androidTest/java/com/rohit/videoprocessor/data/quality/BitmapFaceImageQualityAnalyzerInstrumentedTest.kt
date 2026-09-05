package com.rohit.videoprocessor.data.quality

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rohit.videoprocessor.domain.model.FaceBox
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * `Bitmap` pixel operations aren't usable under the plain JVM unit-test stub
 * jar, so this runs on a real device/emulator instead.
 */
@RunWith(AndroidJUnit4::class)
class BitmapFaceImageQualityAnalyzerInstrumentedTest {

    private val analyzer = BitmapFaceImageQualityAnalyzer()

    @Test
    fun sharpCheckerboard_scoresHigherSharpnessThanUniformFlatColor() {
        val sharpBitmap = checkerboardBitmap(200, 200, squareSize = 4)
        val flatBitmap = solidColorBitmap(200, 200, Color.rgb(128, 128, 128))
        val box = FaceBox(0, 0, 200, 200)

        val sharpResult = analyzer.analyze(sharpBitmap, box)
        val flatResult = analyzer.analyze(flatBitmap, box)

        assertTrue(
            "expected checkerboard sharpness (${sharpResult.sharpness}) to exceed flat-color " +
                "sharpness (${flatResult.sharpness})",
            sharpResult.sharpness > flatResult.sharpness,
        )
    }

    @Test
    fun blurrySyntheticImage_scoresLowerSharpnessThanNoisyImage() {
        // A heavily blurred (large-block, low-frequency) checkerboard approximates a
        // motion-blurred face; a high-frequency noisy image approximates a sharp one.
        val blurryBitmap = checkerboardBitmap(200, 200, squareSize = 100)
        val noisyBitmap = randomNoiseBitmap(200, 200, seed = 42)
        val box = FaceBox(0, 0, 200, 200)

        val blurryResult = analyzer.analyze(blurryBitmap, box)
        val noisyResult = analyzer.analyze(noisyBitmap, box)

        assertTrue(blurryResult.sharpness < noisyResult.sharpness)
    }

    @Test
    fun darkImage_hasLowerMeanBrightnessThanBrightImage() {
        val darkBitmap = solidColorBitmap(100, 100, Color.rgb(10, 10, 10))
        val brightBitmap = solidColorBitmap(100, 100, Color.rgb(240, 240, 240))
        val box = FaceBox(0, 0, 100, 100)

        val darkResult = analyzer.analyze(darkBitmap, box)
        val brightResult = analyzer.analyze(brightBitmap, box)

        assertTrue(darkResult.meanBrightness < 30f)
        assertTrue(brightResult.meanBrightness > 220f)
    }

    @Test
    fun analyze_toleratesBoxExtendingPastFrameBounds() {
        // The face box can legitimately sit at/near the frame edge - analyze() must
        // clamp rather than crash.
        val bitmap = solidColorBitmap(100, 100, Color.GRAY)
        val edgeBox = FaceBox(80, 80, 150, 150) // right/bottom exceed the 100x100 bitmap

        val result = analyzer.analyze(bitmap, edgeBox)

        assertTrue(result.meanBrightness in 0f..255f)
    }

    private fun solidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return bitmap
    }

    private fun checkerboardBitmap(width: Int, height: Int, squareSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        var y = 0
        var row = 0
        while (y < height) {
            var x = if (row % 2 == 0) 0 else squareSize
            while (x < width) {
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + squareSize).toFloat(), (y + squareSize).toFloat(), paint)
                x += squareSize * 2
            }
            y += squareSize
            row++
        }
        return bitmap
    }

    private fun randomNoiseBitmap(width: Int, height: Int, seed: Long): Bitmap {
        val random = Random(seed)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) {
            val v = random.nextInt(256)
            Color.rgb(v, v, v)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
