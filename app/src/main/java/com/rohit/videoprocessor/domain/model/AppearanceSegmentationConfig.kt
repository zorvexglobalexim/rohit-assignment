package com.rohit.videoprocessor.domain.model

/**
 * Tunable knobs for [com.rohit.videoprocessor.domain.pipeline.AppearanceSegmenter].
 * Every default here is a deliberately reasoned starting point to be
 * validated against the supplied sample videos, not a value with any
 * special significance beyond that reasoning - see each property's doc.
 */
data class AppearanceSegmentationConfig(
    val maxMissingDurationMs: Long = DEFAULT_MAX_MISSING_DURATION_MS,
    val minAppearanceDurationMs: Long = DEFAULT_MIN_APPEARANCE_DURATION_MS,
    val minPresenceFaceSizeRatio: Float = DEFAULT_MIN_PRESENCE_FACE_SIZE_RATIO,
    val candidateMinFaceSizeRatio: Float = DEFAULT_CANDIDATE_MIN_FACE_SIZE_RATIO,
    val candidateMinEyeOpenProbability: Float = DEFAULT_CANDIDATE_MIN_EYE_OPEN_PROBABILITY,
    val candidateMinSharpness: Float = DEFAULT_CANDIDATE_MIN_SHARPNESS,
    val minMatchIou: Float = DEFAULT_MIN_MATCH_IOU,
) {
    init {
        require(maxMissingDurationMs >= 0) { "maxMissingDurationMs must not be negative" }
        require(minAppearanceDurationMs >= 0) { "minAppearanceDurationMs must not be negative" }
        require(minPresenceFaceSizeRatio in 0f..1f) { "minPresenceFaceSizeRatio must be in [0,1]" }
        require(candidateMinFaceSizeRatio in 0f..1f) { "candidateMinFaceSizeRatio must be in [0,1]" }
        require(candidateMinEyeOpenProbability in 0f..1f) { "candidateMinEyeOpenProbability must be in [0,1]" }
        require(candidateMinSharpness >= 0f) { "candidateMinSharpness must not be negative" }
        require(minMatchIou in 0f..1f) { "minMatchIou must be in [0,1]" }
    }

    companion object {
        /**
         * Maximum tolerated missing duration - "maximum tolerated missing duration".
         *
         * Reasoning: the default frame sampling interval upstream
         * ([FrameExtractionConfig.DEFAULT_SAMPLE_INTERVAL_MS]) is 500ms
         * (2 fps). 1500ms is 3x that interval, i.e. up to ~2 consecutive
         * missed/rejected samples are tolerated before a segment closes.
         * That comfortably absorbs a single bad ML Kit miss, a blink, or a
         * momentary motion blur, while staying short enough that a person
         * genuinely leaving frame and walking back in - which the
         * assignment explicitly wants counted as a *new* appearance - would
         * essentially never happen within 1.5s in a casual portrait video.
         * This value should scale with the sampling interval if that
         * changes; it is not an independent constant.
         */
        const val DEFAULT_MAX_MISSING_DURATION_MS = 1_500L

        /**
         * Minimum appearance duration.
         *
         * Reasoning: deliberately set *below* one full sampling interval
         * (500ms default) so a genuine two-sample appearance (duration
         * exactly one interval) always survives, while a single isolated
         * one-frame detection (duration 0ms, e.g. a spurious false-positive
         * or a face glimpsed for a single sample and never again) is
         * dropped. This targets false-positive suppression without
         * discarding brief-but-real appearances.
         */
        const val DEFAULT_MIN_APPEARANCE_DURATION_MS = 300L

        /**
         * Detection confidence requirements, part 1: bare "is this plausibly
         * a real face and not noise" gate used for segment membership
         * itself (start/end/continuity) - deliberately lax. Portrait videos
         * frame faces prominently, so a real subject should essentially
         * never be this small; this exists as a defensive floor against
         * spurious near-zero-size boxes, not as a meaningful filter under
         * normal conditions.
         */
        const val DEFAULT_MIN_PRESENCE_FACE_SIZE_RATIO = 0.05f

        /**
         * Detection confidence requirements, part 2: the stricter bar a
         * detection must additionally clear to be a *candidate* frame for
         * later representative-shot selection - large enough that fine
         * detail (eyes, expression) is actually resolvable at typical
         * conversational filming distance, without being so strict that a
         * normally-framed subject gets excluded.
         */
        const val DEFAULT_CANDIDATE_MIN_FACE_SIZE_RATIO = 0.15f

        /**
         * Detection confidence requirements, part 3: a candidate frame is
         * rejected only if both eyes are *quite likely* closed (below 0.4).
         * Deliberately lenient - ML Kit's eye-open probability can be noisy
         * at lower resolutions/angles, and picking the single *best* candidate
         * by expression/eyes is explicitly a later phase's job. This gate
         * only needs to reject the clearly-bad case (mid-blink, eyes shut),
         * not make the final quality judgment. A null probability (the
         * classifier had no opinion) is treated permissively, not penalized.
         */
        const val DEFAULT_CANDIDATE_MIN_EYE_OPEN_PROBABILITY = 0.4f

        /**
         * Detection confidence requirements, part 4: a candidate frame must also be at least
         * this sharp (Laplacian variance - see `BitmapFaceImageQualityAnalyzer`), matching the
         * exact "reject clearly blurry" bar [com.rohit.videoprocessor.domain.model.FrameQualityConfig.minAcceptableSharpness]
         * already uses for representative-frame scoring, rather than inventing a second,
         * uncoordinated blur threshold. A blurry face crop produces a measurably less reliable
         * embedding - the same reasoning that already excludes undersized and closed-eye
         * frames from the candidate pool, which [com.rohit.videoprocessor.domain.pipeline.IdentityClusterer]
         * prefers for identity-embedding aggregation.
         */
        const val DEFAULT_CANDIDATE_MIN_SHARPNESS = 15f

        /**
         * Minimum IoU for the bounding-box fallback match (used when ML
         * Kit's own trackingId is unavailable or doesn't match an open
         * segment - see [com.rohit.videoprocessor.domain.pipeline.AppearanceSegmenter]).
         * Kept low deliberately: at 2fps sampling a face can move
         * noticeably between samples, so the conventional ~0.5 IoU used in
         * continuous-video tracking benchmarks would be too strict here and
         * would fail to bridge genuine continuity. 0.15 tolerates
         * roughly a face-width of motion between samples while still being
         * non-zero, so obviously-unrelated faces elsewhere in frame don't
         * get merged.
         */
        const val DEFAULT_MIN_MATCH_IOU = 0.15f
    }
}
