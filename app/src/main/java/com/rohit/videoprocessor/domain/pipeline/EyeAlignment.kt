package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.FacePoint
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Pure angle math for eye-based face alignment - no Android/Bitmap dependency, fully testable
 * under a plain JVM unit test. [com.rohit.videoprocessor.data.embedding.FaceEmbeddingEngine]
 * uses [rotationDegrees] to rotate a face crop so the eyes are level *before* resizing and
 * feeding it to the embedding model - standard face-recognition preprocessing that this
 * pipeline previously skipped (see the README's face-embedding section), and a real source of
 * embedding inconsistency across head-tilt variation of the same person.
 */
object EyeAlignment {

    /** Beyond this, a real head tilt is implausible for a face MobileFaceNet could usefully embed anyway - more likely a landmark mis-detection than genuine pose, so alignment is skipped rather than risking a wrong rotation. */
    const val MAX_PLAUSIBLE_TILT_DEGREES = 45f

    /**
     * Degrees to rotate the source image by so a line through [leftEye] and [rightEye] becomes
     * horizontal, or null if either landmark is missing or the implied tilt exceeds
     * [MAX_PLAUSIBLE_TILT_DEGREES] (see that constant's doc) - callers must fall back to the
     * unaligned crop in either case, never fail or guess.
     */
    fun rotationDegrees(leftEye: FacePoint?, rightEye: FacePoint?): Float? {
        if (leftEye == null || rightEye == null) return null
        val angle = Math.toDegrees(
            atan2((rightEye.y - leftEye.y).toDouble(), (rightEye.x - leftEye.x).toDouble()),
        ).toFloat()
        return if (abs(angle) <= MAX_PLAUSIBLE_TILT_DEGREES) angle else null
    }
}
