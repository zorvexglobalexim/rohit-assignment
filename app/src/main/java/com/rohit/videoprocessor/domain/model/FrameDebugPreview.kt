package com.rohit.videoprocessor.domain.model

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * A small, independent snapshot for the live "what is the detector seeing"
 * debug view on the Processing screen - [thumbnail] is a downscaled copy
 * (not the original frame bitmap). The pipeline recycles the *previous*
 * preview's [thumbnail] right before producing a new one (see
 * [com.rohit.videoprocessor.processing.VideoProcessingPipeline]) rather than
 * leaving it for the GC - at up to ~2 of these per second for a whole run,
 * explicit recycling keeps peak native bitmap memory bounded to one preview
 * at a time instead of however many the GC hasn't gotten to yet.
 */
data class FrameDebugPreview(
    val thumbnail: Bitmap,
    val faceBoxes: List<Rect>,
    val frameIndex: Int,
    val timestampMs: Long,
)
