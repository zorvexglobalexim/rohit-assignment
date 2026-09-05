package com.rohit.videoprocessor.domain.pipeline

import android.graphics.Rect
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceImageQuality
import com.rohit.videoprocessor.domain.model.FrameQualityConfig
import com.rohit.videoprocessor.domain.model.PersonIdentity
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests. `DetectedFace.boundingBox` below is an unused placeholder
 * (see [FaceBox]'s doc for why `android.graphics.Rect` can't be relied on
 * under the plain unit-test stub jar) - [RepresentativeFrameSelector] reads
 * geometry from [TimestampedDetection.box] only, never `face.boundingBox`.
 */
class RepresentativeFrameSelectorTest {

    private val selector = RepresentativeFrameSelector(FrameQualityConfig())

    @Test
    fun frontalFace_scoresHigherThanSideProfile() {
        val frontal = detection(yaw = 0f, pitch = 0f, roll = 0f)
        val profile = detection(yaw = 60f, pitch = 0f, roll = 0f)

        assertTrue(selector.score(frontal).finalScore > selector.score(profile).finalScore)
    }

    @Test
    fun sharperFrame_scoresHigherThanBlurryFrame() {
        val sharp = detection(sharpness = 100f)
        val blurry = detection(sharpness = 2f)

        assertTrue(selector.score(sharp).finalScore > selector.score(blurry).finalScore)
    }

    @Test
    fun openEyes_scoreHigherThanClosedEyes() {
        val open = detection(leftEye = 0.95f, rightEye = 0.95f)
        val closed = detection(leftEye = 0.05f, rightEye = 0.05f)

        assertTrue(selector.score(open).finalScore > selector.score(closed).finalScore)
    }

    @Test
    fun neutralExpression_scoresHigherThanExtremeOrMissing() {
        val neutral = detection(smiling = 0.5f)
        val extreme = detection(smiling = 1.0f)
        val missing = detection(smiling = null)

        val neutralScore = selector.score(neutral)
        val extremeScore = selector.score(extreme)
        val missingScore = selector.score(missing)

        assertTrue(neutralScore.expressionScore > extremeScore.expressionScore)
        assertEquals(1f, neutralScore.expressionScore, 1e-5f)
        assertEquals(0.5f, missingScore.expressionScore, 1e-5f) // unknown treated as neutral, not penalized
    }

    @Test
    fun clippedFace_isPenalized() {
        val comfortable = detection(box = FaceBox(300, 300, 500, 500), frameWidth = 1000, frameHeight = 1000)
        val clipped = detection(box = FaceBox(0, 300, 200, 500), frameWidth = 1000, frameHeight = 1000)

        val clippedScore = selector.score(clipped)
        assertTrue(clippedScore.clippingPenalty > 0f)
        assertTrue(selector.score(comfortable).finalScore > clippedScore.finalScore)
    }

    @Test
    fun tinyFace_scoresLowerThanNormallySizedFace() {
        val normal = detection(box = FaceBox(300, 300, 500, 500), frameWidth = 1000, frameHeight = 1000) // ratio 0.2
        val tiny = detection(box = FaceBox(500, 500, 520, 520), frameWidth = 1000, frameHeight = 1000) // ratio 0.02

        assertTrue(selector.score(normal).finalScore > selector.score(tiny).finalScore)
    }

    @Test
    fun normalExposure_scoresHigherThanDarkOrOverexposed() {
        val normal = detection(brightness = 128f)
        val dark = detection(brightness = 5f)
        val overexposed = detection(brightness = 250f)

        val normalScore = selector.score(normal)
        assertTrue(normalScore.finalScore > selector.score(dark).finalScore)
        assertTrue(normalScore.finalScore > selector.score(overexposed).finalScore)
    }

    @Test
    fun doesNotSimplyPickTheLargestFace() {
        // A big face that's blurry, off-angle and eyes-closed vs. a smaller face that's
        // otherwise excellent - the smaller-but-better one must win.
        val bigButBad = detection(
            box = FaceBox(50, 50, 900, 900),
            frameWidth = 1000,
            frameHeight = 1000,
            yaw = 55f,
            sharpness = 3f,
            leftEye = 0.05f,
            rightEye = 0.05f,
        )
        val smallButGood = detection(
            box = FaceBox(400, 400, 550, 550),
            frameWidth = 1000,
            frameHeight = 1000,
            yaw = 0f,
            sharpness = 100f,
            leftEye = 0.95f,
            rightEye = 0.95f,
        )

        val identity = personWith(bigButBad, smallButGood)
        val representative = selector.select(identity)

        assertNotNull(representative)
        assertEquals(smallButGood, representative!!.detection)
    }

    @Test
    fun doesNotSimplyPickFirstMiddleOrLastFrame() {
        // 5 candidates in temporal order; the best one is 4th of 5 - neither first, middle, nor last.
        val candidates = listOf(
            detection(sharpness = 20f, timestampMs = 0L),
            detection(sharpness = 25f, timestampMs = 1000L),
            detection(sharpness = 22f, timestampMs = 2000L), // middle
            detection(sharpness = 90f, timestampMs = 3000L), // best
            detection(sharpness = 18f, timestampMs = 4000L), // last
        )

        val identity = personWith(*candidates.toTypedArray())
        val representative = selector.select(identity)

        assertNotNull(representative)
        assertEquals(3000L, representative!!.detection.timestampMs)
    }

    @Test
    fun everyIdentityGetsExactlyOneRepresentative_evenWhenAllCandidatesArePoor() {
        val onlyBadOption = detection(yaw = 70f, sharpness = 1f, leftEye = 0.02f, rightEye = 0.02f, brightness = 2f)
        val identity = personWith(onlyBadOption)

        val representative = selector.select(identity)

        assertNotNull(representative)
        assertEquals(onlyBadOption, representative!!.detection)
    }

    @Test
    fun selectAll_returnsExactlyOnePerIdentity() {
        val identities = listOf(
            personWithId(1, detection(sharpness = 50f)),
            personWithId(2, detection(sharpness = 70f)),
            personWithId(3, detection(sharpness = 30f)),
        )

        val representatives = selector.selectAll(identities)

        assertEquals(3, representatives.size)
        assertEquals(setOf(1, 2, 3), representatives.map { it.personId }.toSet())
    }

    // --- helpers ---

    private fun personWith(vararg detections: TimestampedDetection): PersonIdentity = personWithId(1, *detections)

    private fun personWithId(id: Int, vararg detections: TimestampedDetection): PersonIdentity {
        val list = detections.toList()
        val segment = AppearanceSegment(
            startTimestampMs = list.minOf { it.timestampMs },
            endTimestampMs = list.maxOf { it.timestampMs },
            detections = list,
            candidateFrames = list,
        )
        return PersonIdentity(id = id, appearances = listOf(segment))
    }

    private fun detection(
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f,
        sharpness: Float = 100f,
        brightness: Float = 128f,
        leftEye: Float? = 0.9f,
        rightEye: Float? = 0.9f,
        smiling: Float? = 0.5f,
        box: FaceBox = FaceBox(300, 300, 500, 500),
        frameWidth: Int = 1000,
        frameHeight: Int = 1000,
        timestampMs: Long = 0L,
    ): TimestampedDetection = TimestampedDetection(
        timestampMs = timestampMs,
        frameIndex = (timestampMs / 100L).toInt(),
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        face = DetectedFace(
            id = "$timestampMs-0",
            boundingBox = Rect(),
            trackingId = null,
            headEulerAngleX = pitch,
            headEulerAngleY = yaw,
            headEulerAngleZ = roll,
            leftEyeOpenProbability = leftEye,
            rightEyeOpenProbability = rightEye,
            smilingProbability = smiling,
        ),
        box = box,
        imageQuality = FaceImageQuality(sharpness = sharpness, meanBrightness = brightness),
    )
}
