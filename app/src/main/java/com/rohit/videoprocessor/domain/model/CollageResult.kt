package com.rohit.videoprocessor.domain.model

import android.graphics.Bitmap

/**
 * The final rendered collage - one high-resolution [bitmap] with exactly one
 * tile per unique person, ready to display or share as a normal image. Mirrors
 * [VideoFrame] in pragmatically carrying a raw [Bitmap] on a domain model
 * where a pure representation wouldn't be able to express the actual output.
 */
data class CollageResult(
    val bitmap: Bitmap,
    val totalPeople: Int,
    val totalAppearances: Int,
)
