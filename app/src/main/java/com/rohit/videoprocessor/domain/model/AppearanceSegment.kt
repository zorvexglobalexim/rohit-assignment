package com.rohit.videoprocessor.domain.model

/**
 * One continuous visible segment of A face - **not** a person identity.
 * "Appearance" and "identity" are different concepts: the same person can
 * and will produce multiple [AppearanceSegment]s across a video (they leave
 * frame and come back later), and recognizing that two segments belong to
 * the same person is identity clustering's job - a later, separate phase
 * that this type has no knowledge of.
 *
 * @property detections Every detection folded into this segment, in
 *   chronological order, after the lax "is a face even plausibly present"
 *   gate ([AppearanceSegmentationConfig.minPresenceFaceSizeRatio]) - this is
 *   what defines the segment's temporal span and survives brief low-quality
 *   blips so they don't spuriously split an appearance.
 * @property candidateFrames The subset of [detections] that additionally
 *   clear the stricter "detection confidence requirements" gate
 *   (size + eyes-open, see [AppearanceSegmentationConfig]) - i.e. frames
 *   actually worth considering when a later phase picks a representative
 *   shot for this segment's person. A blurry or eyes-closed frame can keep
 *   an appearance alive (it's in [detections]) without being a candidate
 *   for the collage (it's excluded from [candidateFrames]).
 */
data class AppearanceSegment(
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val detections: List<TimestampedDetection>,
    val candidateFrames: List<TimestampedDetection>,
) {
    val durationMs: Long get() = endTimestampMs - startTimestampMs
}
