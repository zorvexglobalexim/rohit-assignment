package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.FacePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests - no Android/Bitmap dependency. */
class EyeAlignmentTest {

    @Test
    fun levelEyes_needNoRotation() {
        val angle = EyeAlignment.rotationDegrees(FacePoint(100f, 200f), FacePoint(200f, 200f))

        assertEquals(0f, angle!!, 1e-4f)
    }

    @Test
    fun tiltedEyesDownToTheRight_returnPositiveAngle() {
        // rightEye lower than leftEye (larger y, screen coordinates) -> positive atan2 angle.
        val angle = EyeAlignment.rotationDegrees(FacePoint(0f, 0f), FacePoint(1f, 0.5f))

        assertEquals(26.565f, angle!!, 0.01f)
    }

    @Test
    fun tiltedEyesUpToTheRight_returnNegativeAngle() {
        val angle = EyeAlignment.rotationDegrees(FacePoint(0f, 0.5f), FacePoint(1f, 0f))

        assertEquals(-26.565f, angle!!, 0.01f)
    }

    @Test
    fun missingLeftEyeLandmark_returnsNull() {
        assertNull(EyeAlignment.rotationDegrees(null, FacePoint(200f, 200f)))
    }

    @Test
    fun missingRightEyeLandmark_returnsNull() {
        assertNull(EyeAlignment.rotationDegrees(FacePoint(100f, 200f), null))
    }

    @Test
    fun angleExactlyAtPlausibilityLimit_isStillUsed() {
        // atan2(1, 1) = exactly 45 degrees, MAX_PLAUSIBLE_TILT_DEGREES itself - inclusive.
        val angle = EyeAlignment.rotationDegrees(FacePoint(0f, 0f), FacePoint(1f, 1f))

        assertEquals(45f, angle!!, 0.01f)
    }

    @Test
    fun implausiblySteepAngle_isRejectedRatherThanTrusted() {
        // atan2(1.2, 1) ~= 50.2 degrees - beyond a real head tilt this pipeline should trust
        // from a landmark detection; more likely a mis-detection than genuine pose.
        val angle = EyeAlignment.rotationDegrees(FacePoint(0f, 0f), FacePoint(1f, 1.2f))

        assertNull(angle)
    }
}
