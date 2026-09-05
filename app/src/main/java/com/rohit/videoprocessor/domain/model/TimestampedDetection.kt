package com.rohit.videoprocessor.domain.model

/**
 * A single face detection anchored to its position in time and its source
 * frame's dimensions - the atomic input to temporal appearance segmentation.
 *
 * Deliberately carries no [FaceEmbedding]: segmentation is a spatial/temporal
 * problem (is this plausibly the same face as one frame ago?), independent
 * of identity (is this the same *person* as an appearance five minutes ago?).
 * [frameWidth]/[frameHeight] are the source frame's dimensions, needed to
 * judge a face's size *relative to the frame* rather than in raw pixels,
 * which would be meaningless across differently-sized source videos.
 *
 * [box] duplicates [DetectedFace.boundingBox] as a framework-free [FaceBox]
 * - see that type's doc for why: pure-logic consumers of this type (like
 * [com.rohit.videoprocessor.domain.pipeline.AppearanceSegmenter]) read [box],
 * never `face.boundingBox`, so they stay testable under a plain JVM unit test.
 *
 * [imageQuality] defaults to neutral/unpenalized values so existing code
 * (and tests from before this field existed) that doesn't care about pixel
 * quality keeps compiling unchanged; real callers building this from an
 * actual frame should always supply a real measurement.
 */
data class TimestampedDetection(
    val timestampMs: Long,
    val frameIndex: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val face: DetectedFace,
    val box: FaceBox,
    val imageQuality: FaceImageQuality = FaceImageQuality(sharpness = 100f, meanBrightness = 128f),
)
