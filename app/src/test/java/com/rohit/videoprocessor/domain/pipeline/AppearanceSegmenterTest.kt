package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.AppearanceSegmentationConfig
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceImageQuality
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests - no Android runtime/emulator needed. [FaceBox] (not
 * `android.graphics.Rect`) carries all geometry read by [AppearanceSegmenter];
 * `DetectedFace.boundingBox` below is an unused placeholder value only,
 * since `Rect`'s constructor is neutered under the plain unit-test stub jar
 * (see [FaceBox]'s doc) - constructing one is harmless as long as nothing
 * under test ever reads it, which [AppearanceSegmenter] doesn't.
 *
 * Most tests use [lenientSegmenter] (minAppearanceDurationMs = 0) so they
 * isolate one behavior at a time - the minimum-duration filter itself is
 * covered separately and deliberately with the real default config.
 */
class AppearanceSegmenterTest {

    private val defaultSegmenter = AppearanceSegmenter(AppearanceSegmentationConfig())
    private val lenientSegmenter = AppearanceSegmenter(AppearanceSegmentationConfig(minAppearanceDurationMs = 0))

    @Test
    fun continuousDetections_mergeIntoOneSegment() {
        val box = FaceBox(300, 300, 500, 500) // 200/1000 = 0.2 size ratio: clears both gates
        val detections = listOf(0L, 200L, 400L, 600L, 800L).mapIndexed { i, t ->
            detection(frameIndex = i, timestampMs = t, box = box, trackingId = 1)
        }

        val segments = defaultSegmenter.segment(detections)

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(0L, segment.startTimestampMs)
        assertEquals(800L, segment.endTimestampMs)
        assertEquals(5, segment.detections.size)
        assertEquals(5, segment.candidateFrames.size)
    }

    @Test
    fun shortGapWithinTolerance_bridgesIntoOneSegment() {
        val box = FaceBox(300, 300, 500, 500)
        // gap between 200 and 1600 is 1400ms, under the 1500ms default tolerance.
        val detections = listOf(0L, 200L, 1600L).mapIndexed { i, t ->
            detection(frameIndex = i, timestampMs = t, box = box, trackingId = 7)
        }

        val segments = defaultSegmenter.segment(detections)

        assertEquals(1, segments.size)
        assertEquals(3, segments.single().detections.size)
    }

    @Test
    fun longGapExceedsTolerance_splitsIntoTwoSegments() {
        val box = FaceBox(300, 300, 500, 500)
        // gap between 200 and 2200 is 2000ms, over the 1500ms default tolerance -
        // same trackingId even, but the gap alone must still force a split.
        val detections = listOf(0L, 200L, 2200L).mapIndexed { i, t ->
            detection(frameIndex = i, timestampMs = t, box = box, trackingId = 7)
        }

        val segments = lenientSegmenter.segment(detections)

        assertEquals(2, segments.size)
        assertEquals(2, segments[0].detections.size)
        assertEquals(1, segments[1].detections.size)
    }

    @Test
    fun multiplePeopleInSameFrame_areKeptSeparate() {
        val boxA = FaceBox(50, 50, 150, 150) // ratio 0.1
        val boxB = FaceBox(700, 700, 900, 900) // ratio 0.2
        val timestamps = listOf(0L, 200L, 400L)

        val detections = timestamps.flatMapIndexed { i, t ->
            listOf(
                detection(frameIndex = i, timestampMs = t, box = boxA, trackingId = 1, faceIdSuffix = "a"),
                detection(frameIndex = i, timestampMs = t, box = boxB, trackingId = 2, faceIdSuffix = "b"),
            )
        }

        val segments = defaultSegmenter.segment(detections)

        assertEquals(2, segments.size)
        assertTrue(segments.all { it.detections.size == 3 })
        // Each segment's detections must all belong to the same spatial region (never mixed).
        for (segment in segments) {
            val allLeftEdgesLow = segment.detections.all { it.box.left < 500 }
            val allLeftEdgesHigh = segment.detections.all { it.box.left >= 500 }
            assertTrue("segment mixed detections from both people", allLeftEdgesLow || allLeftEdgesHigh)
        }
    }

    @Test
    fun doesNotAssumeFixedNumberOfPeople() {
        val peopleCount = 4
        val timestamps = listOf(0L, 200L, 400L)
        val detections = timestamps.flatMapIndexed { i, t ->
            (0 until peopleCount).map { p ->
                val offset = p * 220
                detection(
                    frameIndex = i,
                    timestampMs = t,
                    box = FaceBox(offset, offset, offset + 150, offset + 150),
                    trackingId = p,
                    faceIdSuffix = "p$p",
                )
            }
        }

        val segments = defaultSegmenter.segment(detections)

        assertEquals(peopleCount, segments.size)
    }

    @Test
    fun spuriousTinyDetection_isFilteredOutEntirely() {
        // size ratio 10/1000 = 0.01, under the 0.05 presence floor.
        val tiny = detection(frameIndex = 0, timestampMs = 0L, box = FaceBox(0, 0, 10, 10), trackingId = 1)

        val segments = lenientSegmenter.segment(listOf(tiny))

        assertTrue(segments.isEmpty())
    }

    @Test
    fun isolatedSingleFrameDetection_isDroppedByMinDuration() {
        val box = FaceBox(300, 300, 500, 500) // passes the presence gate on its own
        val single = detection(frameIndex = 0, timestampMs = 0L, box = box, trackingId = 1)

        val segments = defaultSegmenter.segment(listOf(single))

        assertTrue(segments.isEmpty())
    }

    @Test
    fun shortButRealAppearance_survivesMinDurationFilter() {
        // Exactly one full default sampling interval (500ms) apart - should NOT be
        // treated as a spurious blip even under the real default config.
        val box = FaceBox(300, 300, 500, 500)
        val detections = listOf(0L, 500L).map { t ->
            detection(frameIndex = (t / 500L).toInt(), timestampMs = t, box = box, trackingId = 1)
        }

        val segments = defaultSegmenter.segment(detections)

        assertEquals(1, segments.size)
    }

    @Test
    fun lowQualityFrame_staysInDetectionsButNotCandidateFrames() {
        val box = FaceBox(300, 300, 500, 500)
        val openEyes = detection(frameIndex = 0, timestampMs = 0L, box = box, trackingId = 1, leftEye = 0.9f, rightEye = 0.9f)
        val closedEyes = detection(frameIndex = 1, timestampMs = 400L, box = box, trackingId = 1, leftEye = 0.1f, rightEye = 0.1f)

        val segments = defaultSegmenter.segment(listOf(openEyes, closedEyes))

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(2, segment.detections.size)
        assertEquals(1, segment.candidateFrames.size)
    }

    @Test
    fun clippedDetection_isNeverACandidateFrame_evenIfOtherwiseHighQuality() {
        // Large (ratio 0.2, clears candidateMinFaceSizeRatio) and both eyes wide open - the
        // *only* thing wrong with it is that the box touches the frame's left edge (left = 0).
        // A clipped face is missing part of its own geometry, so it must never be a candidate
        // regardless of how good everything else about it looks (see DetectionGeometry's doc).
        val clippedBox = FaceBox(0, 300, 200, 500)
        val clipped = detection(frameIndex = 0, timestampMs = 0L, box = clippedBox, trackingId = 1, leftEye = 1f, rightEye = 1f)

        val segments = lenientSegmenter.segment(listOf(clipped))

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(1, segment.detections.size)
        assertTrue(segment.candidateFrames.isEmpty())
    }

    @Test
    fun blurryDetection_isNeverACandidateFrame_evenIfOtherwiseHighQuality() {
        // Large, unclipped, both eyes open - the *only* thing wrong with it is sharpness below
        // candidateMinSharpness (default 15) - see that property's doc. A blurry crop produces
        // a measurably less reliable embedding regardless of everything else about it looking fine.
        val box = FaceBox(300, 300, 500, 500)
        val blurry = detection(frameIndex = 0, timestampMs = 0L, box = box, trackingId = 1, sharpness = 5f)

        val segments = lenientSegmenter.segment(listOf(blurry))

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(1, segment.detections.size)
        assertTrue(segment.candidateFrames.isEmpty())
    }

    @Test
    fun outOfOrderInput_isSortedByTimestampBeforeSegmenting() {
        val box = FaceBox(300, 300, 500, 500)
        val later = detection(frameIndex = 1, timestampMs = 400L, box = box, trackingId = 5)
        val earlier = detection(frameIndex = 0, timestampMs = 0L, box = box, trackingId = 5)

        // Deliberately passed out of chronological order.
        val segments = defaultSegmenter.segment(listOf(later, earlier))

        assertEquals(1, segments.size)
        assertEquals(0L, segments.single().startTimestampMs)
        assertEquals(400L, segments.single().endTimestampMs)
    }

    @Test
    fun overlappingDetectionsWithoutTrackingId_matchViaIou() {
        val boxA = FaceBox(100, 100, 300, 300)
        val boxB = FaceBox(120, 120, 320, 320) // shifted slightly, still heavily overlapping

        val detections = listOf(
            detection(frameIndex = 0, timestampMs = 0L, box = boxA, trackingId = null),
            detection(frameIndex = 1, timestampMs = 400L, box = boxB, trackingId = null),
        )

        val segments = defaultSegmenter.segment(detections)

        assertEquals(1, segments.size)
    }

    @Test
    fun nonOverlappingDetectionsWithoutTrackingId_areTreatedAsSeparateSegments() {
        val boxA = FaceBox(0, 0, 200, 200)
        val boxB = FaceBox(700, 700, 900, 900) // far away, no overlap, no tracking hint

        val detections = listOf(
            detection(frameIndex = 0, timestampMs = 0L, box = boxA, trackingId = null),
            detection(frameIndex = 1, timestampMs = 200L, box = boxB, trackingId = null),
        )

        val segments = lenientSegmenter.segment(detections)

        assertEquals(2, segments.size)
    }

    private fun detection(
        frameIndex: Int,
        timestampMs: Long,
        box: FaceBox,
        trackingId: Int?,
        leftEye: Float? = 1f,
        rightEye: Float? = 1f,
        frameWidth: Int = 1000,
        frameHeight: Int = 1000,
        faceIdSuffix: String = "0",
        sharpness: Float = 100f,
    ): TimestampedDetection = TimestampedDetection(
        timestampMs = timestampMs,
        frameIndex = frameIndex,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        box = box,
        imageQuality = FaceImageQuality(sharpness = sharpness, meanBrightness = 128f),
        face = DetectedFace(
            id = "$frameIndex-$faceIdSuffix",
            boundingBox = UNUSED_RECT_PLACEHOLDER,
            trackingId = trackingId,
            headEulerAngleX = null,
            headEulerAngleY = null,
            headEulerAngleZ = null,
            leftEyeOpenProbability = leftEye,
            rightEyeOpenProbability = rightEye,
            smilingProbability = null,
        ),
    )

    companion object {
        // AppearanceSegmenter never reads DetectedFace.boundingBox (it reads
        // TimestampedDetection.box instead - see FaceBox's doc for why), so this
        // value is never inspected; it only exists because DetectedFace requires one.
        private val UNUSED_RECT_PLACEHOLDER = android.graphics.Rect()
    }
}
