package com.rohit.videoprocessor.domain.model

import android.graphics.Rect

/**
 * One face detected in a [VideoFrame]. [boundingBox] is in the coordinate
 * space of that frame's (uncropped, full-resolution) bitmap. [id] is a
 * synthetic, per-run-unique key ("<frameIndex>-<indexInFrame>") used only to
 * correlate this face with derived data (e.g. its [com.rohit.videoprocessor.domain.model.FaceEmbedding])
 * - it carries no identity meaning of its own.
 *
 * [trackingId] is ML Kit's own short-horizon, IOU-based frame-to-frame
 * tracker. It is NOT a person identity - it breaks across occlusion, motion,
 * or re-entry into frame - and must never be used as the appearance/identity
 * solution. It is kept here purely as raw signal a later tracking stage may
 * use as a hint.
 *
 * [leftEyePosition]/[rightEyePosition] are the eye landmarks in the same
 * bitmap coordinate space as [boundingBox] - null if ML Kit couldn't
 * localize them (e.g. a steep profile view). Used by
 * [com.rohit.videoprocessor.data.embedding.FaceEmbeddingEngine] to rotate a
 * face crop level before embedding (see [com.rohit.videoprocessor.domain.pipeline.EyeAlignment]);
 * default null so every existing call site that predates this field keeps
 * compiling unchanged.
 */
data class DetectedFace(
    val id: String,
    val boundingBox: Rect,
    val trackingId: Int?,
    val headEulerAngleX: Float?,
    val headEulerAngleY: Float?,
    val headEulerAngleZ: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val leftEyePosition: FacePoint? = null,
    val rightEyePosition: FacePoint? = null,
)
