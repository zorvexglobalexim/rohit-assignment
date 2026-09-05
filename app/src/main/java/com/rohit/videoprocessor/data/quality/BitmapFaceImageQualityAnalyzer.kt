package com.rohit.videoprocessor.data.quality

import android.graphics.Bitmap
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceImageQuality
import com.rohit.videoprocessor.domain.pipeline.FaceImageQualityAnalyzer

/**
 * [FaceImageQualityAnalyzer] using Laplacian-variance blur detection - a
 * standard, cheap sharpness metric (no ML model, no OpenCV dependency): a
 * sharp image has strong local intensity changes (edges), so the variance of
 * a Laplacian (second-derivative) filter's response is high; a blurred image
 * has smoothed-out gradients, so that variance is low.
 *
 * Downscales the face region to a small fixed [ANALYSIS_SIZE] first, so cost
 * is constant regardless of source resolution - appropriate for running once
 * per detected face on-device.
 *
 * Not thread-safe by design: [pixelScratch]/[grayScratch] are reused across
 * calls to avoid allocating a fresh ~16KB int array and float array for
 * every single detected face (this runs once per face, potentially dozens
 * of times per video). Safe only because the pipeline already calls
 * [analyze] sequentially, one face at a time, from a single coroutine - the
 * same assumption [com.rohit.videoprocessor.data.embedding.FaceEmbeddingEngine]
 * makes for its own scratch buffers.
 */
class BitmapFaceImageQualityAnalyzer : FaceImageQualityAnalyzer {

    private val pixelScratch = IntArray(ANALYSIS_SIZE * ANALYSIS_SIZE)
    private val grayScratch = FloatArray(ANALYSIS_SIZE * ANALYSIS_SIZE)

    override fun analyze(frameBitmap: Bitmap, box: FaceBox): FaceImageQuality {
        val left = box.left.coerceIn(0, frameBitmap.width - 1)
        val top = box.top.coerceIn(0, frameBitmap.height - 1)
        val right = box.right.coerceIn(left + 1, frameBitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, frameBitmap.height)

        val patch = Bitmap.createBitmap(frameBitmap, left, top, right - left, bottom - top)
        // Bitmap.createBitmap(source, x, y, w, h) aliases `source` itself (no copy) when the
        // requested region is the whole bitmap - practically impossible for a face box (it
        // would require the detection to span the entire frame), but guarded anyway so this
        // can never recycle the caller's live frameBitmap out from under it.
        val downscaled = Bitmap.createScaledBitmap(patch, ANALYSIS_SIZE, ANALYSIS_SIZE, true)
        if (downscaled !== patch && patch !== frameBitmap) patch.recycle()

        toGrayscale(downscaled)
        downscaled.recycle()

        return FaceImageQuality(
            sharpness = laplacianVariance(),
            meanBrightness = grayScratch.average().toFloat(),
        )
    }

    /** Fills [grayScratch] (always exactly [ANALYSIS_SIZE] x [ANALYSIS_SIZE], guaranteed by [analyze]) from [bitmap]. */
    private fun toGrayscale(bitmap: Bitmap) {
        bitmap.getPixels(pixelScratch, 0, ANALYSIS_SIZE, 0, 0, ANALYSIS_SIZE, ANALYSIS_SIZE)
        for (i in pixelScratch.indices) {
            val pixel = pixelScratch[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            grayScratch[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
    }

    /** Variance of a 4-neighbor discrete Laplacian response over [grayScratch]'s interior pixels. */
    private fun laplacianVariance(): Float {
        val width = ANALYSIS_SIZE
        val height = ANALYSIS_SIZE

        var count = 0
        var sum = 0.0
        var sumSquares = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = grayScratch[y * width + x]
                val up = grayScratch[(y - 1) * width + x]
                val down = grayScratch[(y + 1) * width + x]
                val left = grayScratch[y * width + x - 1]
                val right = grayScratch[y * width + x + 1]
                val response = (up + down + left + right - 4f * center).toDouble()
                sum += response
                sumSquares += response * response
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSquares / count) - (mean * mean)
        return variance.toFloat().coerceAtLeast(0f)
    }

    companion object {
        private const val ANALYSIS_SIZE = 64
    }
}
