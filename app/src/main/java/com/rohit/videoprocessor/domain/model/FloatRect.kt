package com.rohit.videoprocessor.domain.model

/**
 * A plain, framework-free rectangle in float coordinates - deliberately NOT
 * `android.graphics.RectF` (see [FaceBox]'s doc: Android graphics types are
 * neutered under the plain JVM unit-test stub jar), so collage layout math
 * ([com.rohit.videoprocessor.domain.pipeline.CollageLayoutCalculator]) stays
 * testable without an emulator.
 */
data class FloatRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
