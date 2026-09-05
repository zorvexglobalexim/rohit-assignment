package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.TimestampedDetection

/**
 * Shared pure geometry checks over a [TimestampedDetection]'s box - used by anything that
 * decides whether a detection is trustworthy enough to represent a face (candidate-frame
 * selection, identity-embedding aggregation, representative-frame scoring), not specific to
 * any one of them.
 */
object DetectionGeometry {

    /**
     * True when the face box touches or exceeds any frame edge - part of the face is
     * structurally missing from the image, which makes it unreliable for face embedding
     * *regardless* of embedding model quality (no amount of generous-crop padding recovers
     * geometry that was never captured). This is a hard, general disqualifier - not a
     * graded/tunable score like the other quality signals - so it has no threshold to configure.
     */
    fun isClipped(detection: TimestampedDetection): Boolean {
        val box = detection.box
        return box.left <= 0 || box.top <= 0 || box.right >= detection.frameWidth || box.bottom >= detection.frameHeight
    }
}
