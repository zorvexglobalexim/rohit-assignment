package com.rohit.videoprocessor.domain.model

/**
 * User-adjustable knobs for accuracy debugging/tuning, applied to the *next*
 * run. Not exposed in the production (release) UI - see the `BuildConfig.DEBUG`-gated
 * debug screens that read/write this - since the app is otherwise fully
 * self-configuring with reasoned defaults (see each pipeline config's own
 * doc). Every default here mirrors the corresponding pipeline config's
 * existing default, so leaving these settings untouched reproduces exactly
 * the same behavior as not having this screen at all.
 */
data class DebugSettings(
    /** Forwarded to [FrameExtractionConfig.sampleIntervalMs]. */
    val sampleIntervalMs: Long = FrameExtractionConfig.DEFAULT_SAMPLE_INTERVAL_MS,
    /** See [DEFAULT_MIN_FACE_SIZE_RATIO]. */
    val minFaceSizeRatio: Float = DEFAULT_MIN_FACE_SIZE_RATIO,
    /** Forwarded to [AppearanceSegmentationConfig.maxMissingDurationMs]. */
    val appearanceGapMs: Long = AppearanceSegmentationConfig.DEFAULT_MAX_MISSING_DURATION_MS,
    /** Forwarded to [IdentityClusteringConfig.identitySimilarityThreshold]. */
    val identitySimilarityThreshold: Float = IdentityClusteringConfig.DEFAULT_IDENTITY_SIMILARITY_THRESHOLD,
    /** Forwarded to the matching weights on [FrameQualityConfig]. */
    val frameQualityWeights: FrameQualityWeights = FrameQualityWeights(),
) {
    init {
        require(sampleIntervalMs > 0) { "sampleIntervalMs must be positive" }
        require(minFaceSizeRatio in 0f..1f) { "minFaceSizeRatio must be in [0,1]" }
        require(appearanceGapMs >= 0) { "appearanceGapMs must not be negative" }
        require(identitySimilarityThreshold in -1f..1f) { "identitySimilarityThreshold must be a valid cosine similarity" }
    }

    companion object {
        /**
         * Below this face-size-to-frame-shorter-side ratio, a detection is discarded entirely
         * before it reaches segmentation/clustering/embedding - the closest honest analogue to
         * a "detection confidence" threshold this pipeline has, since ML Kit's face detector
         * reports no numeric confidence score (unlike e.g. object detection). Default 0f -
         * effectively disabled, since a real detection returned by ML Kit is trusted as-is -
         * so leaving this untouched changes nothing versus not having the setting at all.
         */
        const val DEFAULT_MIN_FACE_SIZE_RATIO = 0f
    }
}

/**
 * Mirrors [FrameQualityConfig]'s seven relative-importance weights - the only
 * [FrameQualityConfig] properties exposed for debug tuning. The many
 * threshold/range properties (e.g. [FrameQualityConfig.goodSharpness]) are
 * left at their reasoned defaults: re-deriving *how* each sub-score is
 * computed is a much bigger surface than adjusting *how much* each
 * already-computed sub-score counts toward the final choice.
 */
data class FrameQualityWeights(
    val frontalWeight: Float = FrameQualityConfig().frontalWeight,
    val sharpnessWeight: Float = FrameQualityConfig().sharpnessWeight,
    val eyesWeight: Float = FrameQualityConfig().eyesWeight,
    val expressionWeight: Float = FrameQualityConfig().expressionWeight,
    val visibilityWeight: Float = FrameQualityConfig().visibilityWeight,
    val sizeWeight: Float = FrameQualityConfig().sizeWeight,
    val exposureWeight: Float = FrameQualityConfig().exposureWeight,
)
