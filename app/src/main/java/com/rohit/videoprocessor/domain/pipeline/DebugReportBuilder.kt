package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.AccuracyClusteringInfo
import com.rohit.videoprocessor.domain.model.AppearanceDebugInfo
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.DebugReport
import com.rohit.videoprocessor.domain.model.DebugSettings
import com.rohit.videoprocessor.domain.model.DetectionDebugInfo
import com.rohit.videoprocessor.domain.model.IdentityClusteringResult
import com.rohit.videoprocessor.domain.model.IdentityDebugInfo
import com.rohit.videoprocessor.domain.model.RepresentativeFrame
import com.rohit.videoprocessor.domain.model.RepresentativeFrameDebugInfo
import com.rohit.videoprocessor.domain.model.TimestampDetectionCount
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import com.rohit.videoprocessor.domain.model.VideoDebugInfo
import com.rohit.videoprocessor.domain.model.VideoMetadata
import java.util.IdentityHashMap

/**
 * Assembles a [DebugReport] purely from data the pipeline already computed
 * for a real run - no re-derivation with video-specific assumptions, no
 * hardcoded per-sample-video values. Pure, stateless, deterministic; no
 * Android dependency, so this is testable under a plain JVM unit test with
 * synthetic input exactly like [AppearanceSegmenter]/[IdentityClusterer].
 *
 * Appearance ids are the position of each [AppearanceSegment] in [appearanceSegments] -
 * the same list, same order, that was passed to [IdentityClusterer.cluster],
 * so they line up with [IdentityClusteringResult.debugInfo]'s similarity
 * matrix and with the existing `seg[i]` convention in [IdentityDebugFormatter].
 * Segments are matched back to their id by *reference* (via [IdentityHashMap]),
 * not [AppearanceSegment.equals], since [com.rohit.videoprocessor.domain.pipeline.IdentityClusterer]
 * hands back the exact same segment instances inside each [com.rohit.videoprocessor.domain.model.PersonIdentity] -
 * equals-based matching could pick the wrong index if two segments ever
 * happened to have identical field values.
 */
object DebugReportBuilder {

    fun build(
        metadata: VideoMetadata,
        sampledFrameCount: Int,
        timestampedDetections: List<TimestampedDetection>,
        appearanceSegments: List<AppearanceSegment>,
        identityClustering: IdentityClusteringResult,
        representativeFrames: List<RepresentativeFrame>,
        settingsUsed: DebugSettings,
    ): DebugReport {
        val appearanceIdByRef = IdentityHashMap<AppearanceSegment, Int>()
        appearanceSegments.forEachIndexed { index, segment -> appearanceIdByRef[segment] = index }

        val representativeTimestampByPersonId = representativeFrames.associate { it.personId to it.detection.timestampMs }

        val clusterAssignment = mutableMapOf<Int, Int>()
        val identities = identityClustering.identities.map { identity ->
            val appearanceIds = identity.appearances.map { segment ->
                val id = appearanceIdByRef.getValue(segment)
                clusterAssignment[id] = identity.id
                id
            }
            IdentityDebugInfo(
                personId = identity.id,
                appearanceIds = appearanceIds,
                appearanceTimestamps = identity.appearances.map { it.startTimestampMs to it.endTimestampMs },
                representativeFrameTimestampMs = representativeTimestampByPersonId[identity.id],
            )
        }

        return DebugReport(
            video = VideoDebugInfo(
                durationMs = metadata.durationMs,
                width = metadata.width,
                height = metadata.height,
                sampledFrameCount = sampledFrameCount,
                samplingIntervalMs = settingsUsed.sampleIntervalMs,
            ),
            detection = DetectionDebugInfo(
                totalFacesDetected = timestampedDetections.size,
                detectionsPerTimestamp = timestampedDetections
                    .groupBy { it.timestampMs }
                    .map { (timestampMs, detections) -> TimestampDetectionCount(timestampMs, detections.size) }
                    .sortedBy { it.timestampMs },
            ),
            appearances = appearanceSegments.mapIndexed { index, segment ->
                AppearanceDebugInfo(
                    appearanceId = index,
                    startTimestampMs = segment.startTimestampMs,
                    endTimestampMs = segment.endTimestampMs,
                    detectionCount = segment.detections.size,
                )
            },
            identities = identities,
            clustering = AccuracyClusteringInfo(
                similarityMatrix = identityClustering.debugInfo.segmentSimilarityMatrix,
                clusterAssignment = clusterAssignment,
                mergeLog = identityClustering.debugInfo.mergeLog,
                similarityThresholdUsed = settingsUsed.identitySimilarityThreshold,
            ),
            representativeFrames = representativeFrames.map { frame ->
                RepresentativeFrameDebugInfo(
                    personId = frame.personId,
                    selectedTimestampMs = frame.detection.timestampMs,
                    score = frame.score,
                )
            },
            settingsUsed = settingsUsed,
        )
    }
}
