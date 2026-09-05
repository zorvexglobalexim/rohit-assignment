package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.IdentityClusteringResult
import com.rohit.videoprocessor.domain.model.PersonIdentity

/**
 * Renders [IdentityClusteringResult] as human-readable debug text - pure
 * string formatting over already-computed data, no Android dependency.
 */
object IdentityDebugFormatter {

    /**
     * ```
     * Person 1
     *   Appearance 1: 0.0s - 4.2s
     *   Appearance 2: 12.4s - 17.1s
     *
     * Person 2
     *   Appearance 1: 3.1s - 8.4s
     * ```
     */
    fun formatIdentities(identities: List<PersonIdentity>): String = buildString {
        identities.forEachIndexed { index, identity ->
            if (index > 0) appendLine()
            appendLine("Person ${identity.id}")
            identity.appearances.forEachIndexed { appearanceIndex, segment ->
                appendLine(
                    "  Appearance ${appearanceIndex + 1}: " +
                        "${formatSeconds(segment.startTimestampMs)}s - ${formatSeconds(segment.endTimestampMs)}s",
                )
            }
        }
    }.trimEnd()

    /** Pairwise similarity for every segment pair that had a usable embedding, plus the merge log. */
    fun formatDebugInfo(segments: List<AppearanceSegment>, result: IdentityClusteringResult): String = buildString {
        appendLine("Pairwise appearance-segment similarity:")
        val matrix = result.debugInfo.segmentSimilarityMatrix
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                val similarity = matrix.getOrNull(i)?.getOrNull(j) ?: Float.NaN
                val label = if (similarity.isNaN()) "N/A (no usable embedding)" else "%.3f".format(similarity)
                appendLine(
                    "  seg[$i] (${formatSeconds(segments[i].startTimestampMs)}s) vs " +
                        "seg[$j] (${formatSeconds(segments[j].startTimestampMs)}s): $label",
                )
            }
        }
        appendLine()
        appendLine("Merge log:")
        if (result.debugInfo.mergeLog.isEmpty()) {
            appendLine("  (no merges - every appearance is its own identity)")
        } else {
            result.debugInfo.mergeLog.forEach { appendLine("  $it") }
        }
    }.trimEnd()

    private fun formatSeconds(ms: Long): String = "%.1f".format(ms / 1000.0)
}
