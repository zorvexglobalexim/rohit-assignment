package com.rohit.videoprocessor.domain.model

/**
 * [sampleIntervalMs] of 500ms (2 frames/sec) is a practical default for a
 * ~30s portrait clip: ~60 sampled frames, dense enough to catch brief
 * appearances without extracting every decoded frame. Callers may override
 * it (e.g. sample more sparsely for very long videos).
 */
data class FrameExtractionConfig(
    val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS,
    val maxFrames: Int? = null,
) {
    init {
        require(sampleIntervalMs > 0) { "sampleIntervalMs must be positive" }
        require(maxFrames == null || maxFrames > 0) { "maxFrames must be positive if set" }
    }

    /** Number of samples [VideoFrameExtractor] is expected to emit for a video of [durationMs]. */
    fun estimateFrameCount(durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        val bySampling = (durationMs / sampleIntervalMs).toInt() + 1
        return maxFrames?.let { minOf(it, bySampling) } ?: bySampling
    }

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS = 500L
    }
}
