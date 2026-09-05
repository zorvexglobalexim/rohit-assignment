package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.ProcessingStage

/**
 * Pure math turning (current [ProcessingStage], progress within that stage)
 * into one overall 0..1 progress value, weighted by each stage's
 * [ProcessingStage.weight]. No Android dependency - fully testable under a
 * plain JVM unit test.
 */
object ProcessingProgressCalculator {

    /**
     * @param stageFraction 0..1 progress within [stage]; null (unknown/indeterminate)
     *   is treated as 0 - i.e. "at least this stage has started" - so the
     *   overall value never claims progress that isn't confirmed yet, and
     *   never regresses once the next stage actually begins.
     */
    fun overallProgress(stage: ProcessingStage, stageFraction: Float?): Float {
        val startOffset = ProcessingStage.ORDERED
            .takeWhile { it != stage }
            .sumOf { it.weight.toDouble() }
            .toFloat()
        val clampedFraction = (stageFraction ?: 0f).coerceIn(0f, 1f)
        return (startOffset + stage.weight * clampedFraction).coerceIn(0f, 1f)
    }
}
