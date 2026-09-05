package com.rohit.videoprocessor.domain.model

/**
 * [width]/[height] are display-oriented (rotation already applied), matching
 * the orientation of the bitmaps [com.rohit.videoprocessor.domain.pipeline.VideoFrameExtractor]
 * emits - not necessarily the raw encoded dimensions.
 */
data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
)
