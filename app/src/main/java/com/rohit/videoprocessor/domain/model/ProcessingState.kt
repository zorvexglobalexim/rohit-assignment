package com.rohit.videoprocessor.domain.model

/**
 * A snapshot of pipeline progress for the UI. See
 * [com.rohit.videoprocessor.domain.pipeline.ProcessingProgressCalculator]
 * for how [overallProgress] is derived from [stage] and [stageProgress].
 *
 * @property overallProgress 0..1 across the *whole* pipeline (all 9 stages).
 * @property stageProgress 0..1 progress *within* [stage] only, or null when
 *   that stage has no meaningful sub-progress to show (e.g. clustering has
 *   no natural "percent done" signal - it's a single fast in-memory call
 *   that's either not started or finished).
 * @property identitiesFound Distinct people found so far - 0 until [ProcessingStage.ClusteringIdentities]
 *   actually completes (clustering is what turns appearances into a people count); real and
 *   never estimated/faked in the meantime.
 * @property statusMessage Human-readable current status, e.g. "Detecting faces".
 */
data class ProcessingState(
    val stage: ProcessingStage,
    val overallProgress: Float,
    val stageProgress: Float? = null,
    val framesProcessed: Int = 0,
    val totalFrames: Int? = null,
    val facesDetected: Int = 0,
    val embeddingsComputed: Int = 0,
    val identitiesFound: Int = 0,
    val statusMessage: String = stage.label,
)
