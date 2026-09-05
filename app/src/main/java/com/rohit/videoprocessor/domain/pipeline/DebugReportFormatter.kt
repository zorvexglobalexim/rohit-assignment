package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.DebugReport

/**
 * Renders each [DebugReport] section as human-readable debug text for the
 * debug screen - pure string formatting over already-computed data, no
 * Android dependency. Deliberately one function per section (rather than one
 * giant block) so the debug screen can lay them out as separate, individually
 * scrollable/collapsible cards.
 */
object DebugReportFormatter {

    fun formatVideo(report: DebugReport): String = with(report.video) {
        "Duration: ${formatSeconds(durationMs)}s\n" +
            "Dimensions: ${width}x$height\n" +
            "Sampled frames: $sampledFrameCount\n" +
            "Sampling interval: ${samplingIntervalMs}ms"
    }

    fun formatDetection(report: DebugReport): String = buildString {
        appendLine("Total faces detected: ${report.detection.totalFacesDetected}")
        appendLine("Detections per timestamp:")
        if (report.detection.detectionsPerTimestamp.isEmpty()) {
            appendLine("  (none)")
        } else {
            report.detection.detectionsPerTimestamp.forEach { entry ->
                appendLine("  ${formatSeconds(entry.timestampMs)}s: ${entry.faceCount} face(s)")
            }
        }
    }.trimEnd()

    fun formatAppearances(report: DebugReport): String {
        if (report.appearances.isEmpty()) return "(no appearance segments)"
        return report.appearances.joinToString(separator = "\n") { appearance ->
            "A${appearance.appearanceId}: ${formatSeconds(appearance.startTimestampMs)}s - " +
                "${formatSeconds(appearance.endTimestampMs)}s (${appearance.detectionCount} detection(s))"
        }
    }

    fun formatIdentities(report: DebugReport): String {
        if (report.identities.isEmpty()) return "(no identities)"
        return report.identities.joinToString(separator = "\n\n") { identity ->
            buildString {
                appendLine("Person ${identity.personId}")
                appendLine("  Appearances: ${identity.appearanceIds.joinToString { "A$it" }}")
                identity.appearanceTimestamps.forEachIndexed { index, (start, end) ->
                    appendLine("    A${identity.appearanceIds[index]}: ${formatSeconds(start)}s - ${formatSeconds(end)}s")
                }
                append(
                    "  Representative frame: " +
                        (identity.representativeFrameTimestampMs?.let { "${formatSeconds(it)}s" } ?: "none"),
                )
            }
        }
    }

    fun formatClustering(report: DebugReport): String = buildString {
        val clustering = report.clustering
        appendLine("Similarity threshold used: ${"%.3f".format(clustering.similarityThresholdUsed)}")
        appendLine()
        appendLine("Pairwise appearance similarity:")
        val ids = report.appearances.map { it.appearanceId }
        if (ids.size < 2) {
            appendLine("  (fewer than 2 appearances - nothing to compare)")
        } else {
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val similarity = clustering.similarityMatrix.getOrNull(ids[i])?.getOrNull(ids[j]) ?: Float.NaN
                    val label = if (similarity.isNaN()) "N/A (no usable embedding)" else "%.3f".format(similarity)
                    appendLine("  A${ids[i]} vs A${ids[j]}: $label")
                }
            }
        }
        appendLine()
        appendLine("Cluster assignment:")
        clustering.clusterAssignment.toSortedMap().forEach { (appearanceId, personId) ->
            appendLine("  A$appearanceId -> Person $personId")
        }
        appendLine()
        appendLine("Merge log:")
        if (clustering.mergeLog.isEmpty()) {
            appendLine("  (no merges - every appearance is its own identity)")
        } else {
            clustering.mergeLog.forEach { appendLine("  $it") }
        }
    }.trimEnd()

    fun formatRepresentativeFrames(report: DebugReport): String {
        if (report.representativeFrames.isEmpty()) return "(no representative frames)"
        return report.representativeFrames.joinToString(separator = "\n\n") { frame ->
            val score = frame.score
            "Person ${frame.personId} - selected ${formatSeconds(frame.selectedTimestampMs)}s\n" +
                "  frontal=${format2(score.frontalScore)} sharpness=${format2(score.sharpnessScore)} " +
                "eyes=${format2(score.eyesScore)} expression=${format2(score.expressionScore)}\n" +
                "  visibility=${format2(score.visibilityScore)} size=${format2(score.sizeScore)} " +
                "exposure=${format2(score.exposureScore)} clipping_penalty=${format2(score.clippingPenalty)}\n" +
                "  final=${format2(score.finalScore)}"
        }
    }

    fun formatSettings(report: DebugReport): String = with(report.settingsUsed) {
        "Sample interval: ${sampleIntervalMs}ms\n" +
            "Min face size ratio: $minFaceSizeRatio\n" +
            "Appearance gap: ${appearanceGapMs}ms\n" +
            "Identity similarity threshold: $identitySimilarityThreshold\n" +
            "Frame quality weights: frontal=${frameQualityWeights.frontalWeight} " +
            "sharpness=${frameQualityWeights.sharpnessWeight} eyes=${frameQualityWeights.eyesWeight} " +
            "expression=${frameQualityWeights.expressionWeight} visibility=${frameQualityWeights.visibilityWeight} " +
            "size=${frameQualityWeights.sizeWeight} exposure=${frameQualityWeights.exposureWeight}"
    }

    private fun formatSeconds(ms: Long): String = "%.1f".format(ms / 1000.0)

    private fun format2(value: Float): String = "%.2f".format(value)
}
