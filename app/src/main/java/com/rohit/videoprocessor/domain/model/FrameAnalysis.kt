package com.rohit.videoprocessor.domain.model

/**
 * Result of running face detection on one sampled [frame]. [frame] retains
 * the full, uncropped bitmap and its timestamp/frame index - detection never
 * crops to a face's bounding box, so later stages (representative frame
 * selection, collage) can take a generous crop from the original image
 * rather than a tight face-only region.
 */
data class FrameAnalysis(
    val frame: VideoFrame,
    val faces: List<DetectedFace>,
)
