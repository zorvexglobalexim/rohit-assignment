package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests - no Android runtime needed. */
class ProcessingProgressCalculatorTest {

    @Test
    fun firstStageAtZeroFraction_isZeroOverallProgress() {
        val progress = ProcessingProgressCalculator.overallProgress(ProcessingStage.LoadingVideo, 0f)
        assertEquals(0f, progress, 1e-5f)
    }

    @Test
    fun lastStageAtFullFraction_isFullOverallProgress() {
        val progress = ProcessingProgressCalculator.overallProgress(ProcessingStage.Complete, 1f)
        assertEquals(1f, progress, 1e-4f)
    }

    @Test
    fun stageWeightsSumToOne() {
        val total = ProcessingStage.ORDERED.sumOf { it.weight.toDouble() }.toFloat()
        assertEquals(1f, total, 1e-4f)
    }

    @Test
    fun nullStageFraction_isTreatedAsZero_notFull() {
        val withNull = ProcessingProgressCalculator.overallProgress(ProcessingStage.ClusteringIdentities, null)
        val withZero = ProcessingProgressCalculator.overallProgress(ProcessingStage.ClusteringIdentities, 0f)
        val withFull = ProcessingProgressCalculator.overallProgress(ProcessingStage.ClusteringIdentities, 1f)

        assertEquals(withZero, withNull, 1e-5f)
        assertTrue(withNull < withFull)
    }

    @Test
    fun progressIsContinuousAcrossStageBoundaries() {
        // Finishing one stage (fraction=1) must land exactly where starting the next (fraction=0) does -
        // no jump backward, no gap.
        val stages = ProcessingStage.ORDERED
        for (i in 0 until stages.size - 1) {
            val endOfCurrent = ProcessingProgressCalculator.overallProgress(stages[i], 1f)
            val startOfNext = ProcessingProgressCalculator.overallProgress(stages[i + 1], 0f)
            assertEquals(
                "boundary between ${stages[i]} and ${stages[i + 1]} should be continuous",
                endOfCurrent,
                startOfNext,
                1e-4f,
            )
        }
    }

    @Test
    fun progressIsMonotonicallyNonDecreasingThroughThePipeline() {
        var previous = 0f
        for (stage in ProcessingStage.ORDERED) {
            for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val current = ProcessingProgressCalculator.overallProgress(stage, fraction)
                assertTrue(
                    "progress should never go backward (stage=$stage, fraction=$fraction, " +
                        "current=$current, previous=$previous)",
                    current >= previous - 1e-5f,
                )
                previous = current
            }
        }
    }

    @Test
    fun outOfRangeFraction_isClamped() {
        val negative = ProcessingProgressCalculator.overallProgress(ProcessingStage.DetectingFaces, -5f)
        val zero = ProcessingProgressCalculator.overallProgress(ProcessingStage.DetectingFaces, 0f)
        val tooLarge = ProcessingProgressCalculator.overallProgress(ProcessingStage.DetectingFaces, 5f)
        val one = ProcessingProgressCalculator.overallProgress(ProcessingStage.DetectingFaces, 1f)

        assertEquals(zero, negative, 1e-5f)
        assertEquals(one, tooLarge, 1e-5f)
    }
}
