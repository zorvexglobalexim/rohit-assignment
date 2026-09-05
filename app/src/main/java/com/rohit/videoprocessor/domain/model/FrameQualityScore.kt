package com.rohit.videoprocessor.domain.model

/**
 * Full breakdown of one candidate frame's representative-shot quality, as
 * produced by [com.rohit.videoprocessor.domain.pipeline.RepresentativeFrameSelector].
 * Every `*Score` is 0..1 ("bad" to "good"); [clippingPenalty] is a flat
 * amount subtracted (not a 0..1 score) - see [FrameQualityConfig]. Kept as a
 * full breakdown (rather than just [finalScore]) specifically so a debug
 * explanation can show *why* a frame won or lost.
 */
data class FrameQualityScore(
    val frontalScore: Float,
    val sharpnessScore: Float,
    val eyesScore: Float,
    val expressionScore: Float,
    val visibilityScore: Float,
    val sizeScore: Float,
    val exposureScore: Float,
    val clippingPenalty: Float,
    val finalScore: Float,
)
