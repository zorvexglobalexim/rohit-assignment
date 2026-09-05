package com.rohit.videoprocessor.domain.model

/**
 * A plain, framework-free 2D point - deliberately NOT `android.graphics.PointF`, for the same
 * reason [FaceBox] exists instead of `android.graphics.Rect` (see that type's doc): anything
 * meant to be pure-JVM-testable must never touch a real Android framework class, whose
 * constructor is neutered under the plain unit-test stub jar.
 */
data class FacePoint(val x: Float, val y: Float)
