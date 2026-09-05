package com.rohit.videoprocessor.domain.model

/**
 * Tunable knobs for [com.rohit.videoprocessor.domain.pipeline.RepresentativeFrameSelector].
 * Every threshold is a reasoned starting point to validate against real
 * sample videos, not a benchmarked constant - see each property's doc.
 *
 * Every sub-score is 0..1 ("bad" to "good"); [frontalWeight]..[exposureWeight]
 * combine them into a single weighted average before [clippingPenalty] is
 * subtracted. Weights don't need to sum to 1 - they're normalized internally
 * - so raising one relative to the others is enough to shift its influence.
 */
data class FrameQualityConfig(
    // --- Frontalness: combined |yaw|/|pitch|/|roll|, degrees ---
    /** At or above this combined angle, frontalness scores 0. */
    val maxAcceptableAngleDegrees: Float = 40f,

    // --- Sharpness: Laplacian variance (see BitmapFaceImageQualityAnalyzer) ---
    /** At/below this, sharpness scores 0 - "reject blurry frames". */
    val minAcceptableSharpness: Float = 15f,
    /** At/above this, sharpness scores 1 (fully sharp). */
    val goodSharpness: Float = 60f,

    // --- Eyes: min(left, right) eye-open probability ---
    /** At/below this, eyes score 0 (treated as closed). */
    val eyesClosedThreshold: Float = 0.3f,
    /** At/above this, eyes score 1 (treated as open). */
    val eyesOpenThreshold: Float = 0.7f,

    // --- Expression: smilingProbability, preferring a middle "pleasant/neutral" band ---
    /** Below this, expression score ramps down toward 0 (too serious/blink-like). */
    val idealSmilingRangeStart: Float = 0.2f,
    /** Above this, expression score ramps down toward 0 (too exaggerated). */
    val idealSmilingRangeEnd: Float = 0.8f,

    // --- Face size: min(box width, box height) / min(frame width, frame height) ---
    /** Below this, size score ramps down toward 0 - face too small/far away. */
    val minGoodSizeRatio: Float = 0.12f,
    /** Size ratio at which size score reaches 1 - no upper penalty beyond this: a
     *  large/close-up face is never penalized, it's just not *required* either. */
    val idealSizeRatio: Float = 0.22f,

    // --- Exposure: mean grayscale brightness of the face region, 0..255 ---
    val darkCutoff: Float = 20f,
    val minGoodBrightness: Float = 60f,
    val maxGoodBrightness: Float = 200f,
    val brightCutoff: Float = 235f,

    // --- Visibility/clipping: distance from frame edge, relative to the shorter frame side ---
    /** Below this fraction of margin from any edge, visibility starts degrading. */
    val edgeMarginRatio: Float = 0.03f,
    /** Flat penalty subtracted from the final score when the face box actually touches/exceeds an edge. */
    val clippingPenaltyAmount: Float = 0.5f,

    // --- Weights (relative importance in the combined score) ---
    val frontalWeight: Float = 1.2f,
    val sharpnessWeight: Float = 1.5f,
    val eyesWeight: Float = 1.0f,
    val expressionWeight: Float = 0.5f,
    val visibilityWeight: Float = 1.0f,
    val sizeWeight: Float = 0.8f,
    val exposureWeight: Float = 0.8f,
) {
    init {
        require(maxAcceptableAngleDegrees > 0f) { "maxAcceptableAngleDegrees must be positive" }
        require(goodSharpness > minAcceptableSharpness) { "goodSharpness must exceed minAcceptableSharpness" }
        require(eyesOpenThreshold > eyesClosedThreshold) { "eyesOpenThreshold must exceed eyesClosedThreshold" }
        require(idealSmilingRangeEnd > idealSmilingRangeStart) { "idealSmilingRangeEnd must exceed idealSmilingRangeStart" }
        require(idealSizeRatio > minGoodSizeRatio) { "idealSizeRatio must exceed minGoodSizeRatio" }
        require(maxGoodBrightness > minGoodBrightness) { "maxGoodBrightness must exceed minGoodBrightness" }
        require(minGoodBrightness > darkCutoff) { "minGoodBrightness must exceed darkCutoff" }
        require(brightCutoff > maxGoodBrightness) { "brightCutoff must exceed maxGoodBrightness" }
        require(clippingPenaltyAmount >= 0f) { "clippingPenaltyAmount must not be negative" }
        val weights = listOf(frontalWeight, sharpnessWeight, eyesWeight, expressionWeight, visibilityWeight, sizeWeight, exposureWeight)
        require(weights.all { it >= 0f }) { "weights must not be negative" }
        require(weights.any { it > 0f }) { "at least one weight must be positive" }
    }
}
