package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.RepresentativeFrame

/**
 * Renders a [RepresentativeFrame] as human-readable debug text - pure string
 * formatting over already-computed data, no Android dependency.
 */
object RepresentativeFrameDebugFormatter {

    /**
     * ```
     * Representative frame:
     * timestamp = 12.4s
     *
     * Score:
     * frontal = 0.85
     * sharpness = 0.72
     * eyes = 1.00
     * expression = 0.60
     * visibility = 1.00
     * size = 0.90
     * exposure = 1.00
     * clipping penalty = 0.00
     * final = 0.84
     * ```
     */
    fun format(frame: RepresentativeFrame): String = buildString {
        val score = frame.score
        appendLine("Representative frame:")
        appendLine("timestamp = ${formatSeconds(frame.detection.timestampMs)}s")
        appendLine()
        appendLine("Score:")
        appendLine("frontal = ${format2(score.frontalScore)}")
        appendLine("sharpness = ${format2(score.sharpnessScore)}")
        appendLine("eyes = ${format2(score.eyesScore)}")
        appendLine("expression = ${format2(score.expressionScore)}")
        appendLine("visibility = ${format2(score.visibilityScore)}")
        appendLine("size = ${format2(score.sizeScore)}")
        appendLine("exposure = ${format2(score.exposureScore)}")
        appendLine("clipping penalty = ${format2(score.clippingPenalty)}")
        appendLine("final = ${format2(score.finalScore)}")
    }.trimEnd()

    fun formatAll(frames: List<RepresentativeFrame>): String =
        frames.joinToString(separator = "\n\n") { frame ->
            "Person ${frame.personId}\n${format(frame)}"
        }

    private fun formatSeconds(ms: Long): String = "%.1f".format(ms / 1000.0)

    private fun format2(value: Float): String = "%.2f".format(value)
}
