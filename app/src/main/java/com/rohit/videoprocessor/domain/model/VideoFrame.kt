package com.rohit.videoprocessor.domain.model

import android.graphics.Bitmap

/**
 * One frame sampled from a video. [bitmap] is at the video's original,
 * display-oriented dimensions - callers own its lifecycle and are
 * responsible for recycling it once done.
 */
data class VideoFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val frameIndex: Int,
)
