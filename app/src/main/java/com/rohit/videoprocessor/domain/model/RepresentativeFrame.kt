package com.rohit.videoprocessor.domain.model

/**
 * The single chosen representative frame for one [PersonIdentity]. Carries
 * the winning [detection] (timestamp, frame index, box, frame dimensions -
 * everything needed to later re-extract and generously crop the actual
 * image, never tightly to [TimestampedDetection.box]) plus the [score] that
 * won it, for debug/explanation purposes.
 */
data class RepresentativeFrame(
    val personId: Int,
    val detection: TimestampedDetection,
    val score: FrameQualityScore,
)
