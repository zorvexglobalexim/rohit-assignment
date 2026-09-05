package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.AppearanceSegmentationConfig
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import kotlin.math.max
import kotlin.math.min

/**
 * Groups a video's raw face detections into [AppearanceSegment]s - continuous
 * visible segments of *a face*, not a person identity. Pure, stateless,
 * deterministic logic with zero Android framework or ML dependency - it
 * reads only [TimestampedDetection.box] ([FaceBox], a plain data class) for
 * geometry, deliberately never `TimestampedDetection.face.boundingBox`
 * (`android.graphics.Rect`), whose constructor is neutered under the plain
 * JVM unit-test stub jar (see [FaceBox]'s doc). This is what makes this
 * class runnable under a plain `./gradlew test`, no emulator or Robolectric
 * needed.
 *
 * Algorithm, per video:
 * 1. Drop detections that fail the bare "plausibly a real face" size gate
 *    ([AppearanceSegmentationConfig.minPresenceFaceSizeRatio]).
 * 2. Sort the rest by timestamp, then walk sampled frames in order.
 * 3. For each frame, greedily match its detections against currently open
 *    segments by score: an ML Kit `trackingId` match always outranks a
 *    bounding-box IoU match, which must clear [AppearanceSegmentationConfig.minMatchIou].
 *    This handles multiple simultaneous people without one detection
 *    accidentally extending the wrong person's segment. Note `trackingId`
 *    is used here only as a *short-horizon continuity hint* between
 *    adjacent samples - never as a cross-appearance identity signal, which
 *    remains a separate, later phase.
 * 4. Any open segment not matched within [AppearanceSegmentationConfig.maxMissingDurationMs]
 *    of its last detection is closed - this is what stops a long gap (scene
 *    cut, person actually leaving) from being folded into one appearance,
 *    while a short gap (a blink, one bad detection) is bridged.
 * 5. Unmatched detections start new segments.
 * 6. After the whole video, segments shorter than [AppearanceSegmentationConfig.minAppearanceDurationMs]
 *    are dropped as likely noise.
 *
 * No assumption is made anywhere about how many people or segments a video
 * contains - both fall out purely from the detections actually observed.
 */
class AppearanceSegmenter(private val config: AppearanceSegmentationConfig = AppearanceSegmentationConfig()) {

    fun segment(detections: List<TimestampedDetection>): List<AppearanceSegment> {
        val present = detections
            .filter { isPresent(it) }
            .sortedWith(compareBy({ it.timestampMs }, { it.frameIndex }))

        // LinkedHashMap (groupBy's default) preserves first-seen key order, which is
        // chronological here since `present` was sorted first.
        val byFrame = present.groupBy { it.frameIndex }

        val openSegments = mutableListOf<OpenSegment>()
        val finished = mutableListOf<AppearanceSegment>()

        for (frameDetections in byFrame.values) {
            val frameTimestamp = frameDetections.first().timestampMs

            val openIterator = openSegments.iterator()
            while (openIterator.hasNext()) {
                val seg = openIterator.next()
                if (frameTimestamp - seg.lastTimestampMs > config.maxMissingDurationMs) {
                    finished += seg.finish()
                    openIterator.remove()
                }
            }

            val candidates = buildList {
                for (segIndex in openSegments.indices) {
                    for (detIndex in frameDetections.indices) {
                        val score = matchScore(openSegments[segIndex].last, frameDetections[detIndex])
                        if (score > 0f) add(Candidate(segIndex, detIndex, score))
                    }
                }
            }.sortedByDescending { it.score }

            val matchedSegments = BooleanArray(openSegments.size)
            val matchedDetections = BooleanArray(frameDetections.size)
            for (candidate in candidates) {
                if (matchedSegments[candidate.segmentIndex] || matchedDetections[candidate.detectionIndex]) continue
                val detection = frameDetections[candidate.detectionIndex]
                openSegments[candidate.segmentIndex].extend(detection, isCandidateFrame(detection))
                matchedSegments[candidate.segmentIndex] = true
                matchedDetections[candidate.detectionIndex] = true
            }

            for (detIndex in frameDetections.indices) {
                if (!matchedDetections[detIndex]) {
                    val detection = frameDetections[detIndex]
                    openSegments += OpenSegment.start(detection, isCandidateFrame(detection))
                }
            }
        }

        finished += openSegments.map { it.finish() }

        return finished
            .filter { it.durationMs >= config.minAppearanceDurationMs }
            .sortedBy { it.startTimestampMs }
    }

    private fun isPresent(detection: TimestampedDetection): Boolean =
        faceSizeRatio(detection) >= config.minPresenceFaceSizeRatio

    private fun isCandidateFrame(detection: TimestampedDetection): Boolean {
        if (faceSizeRatio(detection) < config.candidateMinFaceSizeRatio) return false
        // A face box touching/exceeding the frame edge is missing part of its own geometry -
        // unreliable both as a representative photo and (more importantly) as identity-embedding
        // input, regardless of how large or open-eyed the visible portion looks. See
        // DetectionGeometry's doc.
        if (DetectionGeometry.isClipped(detection)) return false
        // A sufficiently blurry crop is also a materially less reliable embedding input, even
        // when it's large, unclipped and open-eyed - see AppearanceSegmentationConfig.candidateMinSharpness's doc.
        if (detection.imageQuality.sharpness < config.candidateMinSharpness) return false
        val leftEye = detection.face.leftEyeOpenProbability ?: 1f
        val rightEye = detection.face.rightEyeOpenProbability ?: 1f
        return min(leftEye, rightEye) >= config.candidateMinEyeOpenProbability
    }

    private fun faceSizeRatio(detection: TimestampedDetection): Float {
        val shorterFrameSide = min(detection.frameWidth, detection.frameHeight)
        if (shorterFrameSide <= 0) return 0f
        val faceSize = min(detection.box.width, detection.box.height)
        return faceSize.toFloat() / shorterFrameSide
    }

    private fun matchScore(previous: TimestampedDetection, next: TimestampedDetection): Float {
        val previousTrackingId = previous.face.trackingId
        if (previousTrackingId != null && previousTrackingId == next.face.trackingId) {
            return TRACKING_ID_MATCH_SCORE
        }
        val overlap = iou(previous.box, next.box)
        return if (overlap >= config.minMatchIou) overlap else 0f
    }

    /** Intersection-over-union between two plain [FaceBox]es. */
    private fun iou(a: FaceBox, b: FaceBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interWidth = (interRight - interLeft).coerceAtLeast(0)
        val interHeight = (interBottom - interTop).coerceAtLeast(0)
        val interArea = interWidth.toLong() * interHeight.toLong()
        if (interArea <= 0L) return 0f

        val areaA = a.width.toLong() * a.height.toLong()
        val areaB = b.width.toLong() * b.height.toLong()
        val union = areaA + areaB - interArea
        return if (union <= 0L) 0f else interArea.toFloat() / union.toFloat()
    }

    private data class Candidate(val segmentIndex: Int, val detectionIndex: Int, val score: Float)

    /** Mutable accumulator for a segment that hasn't been closed yet. */
    private class OpenSegment private constructor(
        private val allDetections: MutableList<TimestampedDetection>,
        private val candidateFrames: MutableList<TimestampedDetection>,
        var last: TimestampedDetection,
    ) {
        val lastTimestampMs: Long get() = last.timestampMs

        fun extend(detection: TimestampedDetection, isCandidate: Boolean) {
            allDetections += detection
            if (isCandidate) candidateFrames += detection
            last = detection
        }

        fun finish(): AppearanceSegment = AppearanceSegment(
            startTimestampMs = allDetections.first().timestampMs,
            endTimestampMs = allDetections.last().timestampMs,
            detections = allDetections.toList(),
            candidateFrames = candidateFrames.toList(),
        )

        companion object {
            fun start(detection: TimestampedDetection, isCandidate: Boolean): OpenSegment {
                val all = mutableListOf(detection)
                val candidates = if (isCandidate) mutableListOf(detection) else mutableListOf()
                return OpenSegment(all, candidates, detection)
            }
        }
    }

    companion object {
        /** Always outranks any IoU-only match, since IoU is bounded to [0, 1]. */
        private const val TRACKING_ID_MATCH_SCORE = 2f
    }
}
