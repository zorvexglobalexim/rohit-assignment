package com.rohit.videoprocessor.data.collage

import android.graphics.Bitmap
import android.net.Uri
import com.rohit.videoprocessor.domain.model.CollageConfig
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.RepresentativeFrame
import com.rohit.videoprocessor.domain.pipeline.VideoFrameExtractor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns each [RepresentativeFrame] (a timestamp + box - Phase 6 deliberately
 * never produced an image) into an actual displayable [Bitmap]: re-seeks the
 * source video at that exact timestamp for a fresh full-quality frame, then
 * takes a generous crop around the face - never tight to [FaceBox], and
 * never padded/distorted - see [buildDisplayCrop].
 */
class RepresentativeImageProvider(private val frameExtractor: VideoFrameExtractor) {

    suspend fun buildImages(
        uri: Uri,
        representativeFrames: List<RepresentativeFrame>,
        config: CollageConfig = CollageConfig(),
    ): Map<Int, Bitmap> {
        val images = mutableMapOf<Int, Bitmap>()
        for (representative in representativeFrames) {
            val frame = frameExtractor.extractFrameAt(uri, representative.detection.timestampMs) ?: continue
            val cropped = buildDisplayCrop(
                source = frame.bitmap,
                box = representative.detection.box,
                aspectRatio = config.imageAspectRatioWidthToHeight,
                marginMultiplier = CROP_MARGIN_MULTIPLIER,
            )
            frame.bitmap.recycle()
            images[representative.personId] = downscaleIfLarger(cropped, MAX_OUTPUT_WIDTH)
        }
        return images
    }

    /**
     * Expands [box] to [marginMultiplier] times its larger side, forces that
     * region to [aspectRatio] (width/height), centers it on the box, then -
     * if it doesn't fit inside [source] - shrinks it proportionally (never
     * distorts) until it does, and finally repositions (never resizes
     * further) to stay in bounds. Never pads: a shareable photo shouldn't
     * have artificial borders, unlike the fixed-size model-input crop used
     * for embeddings.
     */
    private fun buildDisplayCrop(source: Bitmap, box: FaceBox, aspectRatio: Float, marginMultiplier: Float): Bitmap {
        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f
        val boxSize = max(box.width, box.height).toFloat().coerceAtLeast(1f)

        var width = boxSize * marginMultiplier
        var height = width / aspectRatio

        val fitScale = min(1f, min(source.width / width, source.height / height))
        width *= fitScale
        height *= fitScale

        val left = (centerX - width / 2f).coerceIn(0f, (source.width - width).coerceAtLeast(0f))
        val top = (centerY - height / 2f).coerceIn(0f, (source.height - height).coerceAtLeast(0f))

        val cropWidth = width.roundToInt().coerceIn(1, source.width)
        val cropHeight = height.roundToInt().coerceIn(1, source.height)
        val crop = Bitmap.createBitmap(source, left.roundToInt(), top.roundToInt(), cropWidth, cropHeight)
        // Bitmap.createBitmap(source, x, y, w, h) returns `source` itself (no copy) if the
        // requested region is exactly the whole source bitmap - plausible here when a face
        // fills most of the frame and the margin/aspect math ends up wanting the full image.
        // buildImages() recycles `frame.bitmap` (== source) right after this call, so without
        // this guard the "cropped" result handed to the collage could be a recycled bitmap.
        return if (crop === source) crop.copy(crop.config ?: Bitmap.Config.ARGB_8888, false) else crop
    }

    /** Bounds memory regardless of source video resolution - the collage never displays wider than this anyway. */
    private fun downscaleIfLarger(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val scale = maxWidth.toFloat() / bitmap.width
        val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * scale).roundToInt(), true)
        bitmap.recycle()
        return scaled
    }

    companion object {
        private const val CROP_MARGIN_MULTIPLIER = 3.2f
        private const val MAX_OUTPUT_WIDTH = 1000
    }
}
