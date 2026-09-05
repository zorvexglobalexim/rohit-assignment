package com.rohit.videoprocessor.domain.model

/**
 * Pixel-derived quality signals for one detected face, measured against its
 * source frame's bitmap *at capture time* (see [TimestampedDetection.imageQuality]) -
 * by the time identity clustering/representative selection run, that bitmap
 * has long been recycled (the pipeline never holds more than one frame in
 * memory at once), so anything pixel-dependent must be computed and stored
 * here while the frame is still available, not recomputed later.
 *
 * @property sharpness Laplacian-variance blur metric - higher means sharper/
 *   more in-focus, lower means blurrier. Not bounded to a fixed range; see
 *   [com.rohit.videoprocessor.domain.model.FrameQualityConfig] for the
 *   thresholds that turn it into a 0..1 score.
 * @property meanBrightness Mean grayscale luminance of the face region, 0..255.
 */
data class FaceImageQuality(
    val sharpness: Float,
    val meanBrightness: Float,
)
