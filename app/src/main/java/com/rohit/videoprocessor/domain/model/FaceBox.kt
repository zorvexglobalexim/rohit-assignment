package com.rohit.videoprocessor.domain.model

/**
 * A plain, framework-free bounding box - deliberately NOT `android.graphics.Rect`.
 * `Rect`'s constructor is neutered by Android's plain-JVM unit-test stub jar
 * (it silently leaves fields at 0 instead of throwing), so anything meant to
 * be pure-JVM-testable, like [AppearanceSegmentationConfig]'s consumer
 * [com.rohit.videoprocessor.domain.pipeline.AppearanceSegmenter], must work
 * with a real, un-stubbed type instead.
 */
data class FaceBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
