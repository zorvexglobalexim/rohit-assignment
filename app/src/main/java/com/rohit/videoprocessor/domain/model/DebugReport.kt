package com.rohit.videoprocessor.domain.model

/**
 * Full inspectable snapshot of one processing run, for the accuracy-debugging
 * screens - not the production result UI (see [com.rohit.videoprocessor.domain.ProcessingResult]
 * for that). Every field here is derived straight from data the pipeline
 * already computed for real (see [com.rohit.videoprocessor.domain.pipeline.DebugReportBuilder]) -
 * nothing is re-derived with video-specific assumptions or hardcoded per
 * sample video.
 *
 * [AppearanceDebugInfo.appearanceId] is a stable index into the *original*
 * appearance-segments list produced by segmentation, in the same order used
 * everywhere else in the codebase (see [ClusteringDebugInfo.segmentSimilarityMatrix]'s
 * doc and [com.rohit.videoprocessor.domain.pipeline.IdentityDebugFormatter]'s
 * `seg[i]` convention) - [IdentityDebugInfo.appearanceIds] and
 * [AccuracyClusteringInfo.clusterAssignment] use the same ids, so an
 * appearance can be cross-referenced across every section.
 */
data class DebugReport(
    val video: VideoDebugInfo,
    val detection: DetectionDebugInfo,
    val appearances: List<AppearanceDebugInfo>,
    val identities: List<IdentityDebugInfo>,
    val clustering: AccuracyClusteringInfo,
    val representativeFrames: List<RepresentativeFrameDebugInfo>,
    val settingsUsed: DebugSettings,
)

data class VideoDebugInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sampledFrameCount: Int,
    val samplingIntervalMs: Long,
)

data class DetectionDebugInfo(
    val totalFacesDetected: Int,
    /** One entry per sampled timestamp that had at least one face survive [DebugSettings.minFaceSizeRatio], in chronological order. */
    val detectionsPerTimestamp: List<TimestampDetectionCount>,
)

data class TimestampDetectionCount(val timestampMs: Long, val faceCount: Int)

data class AppearanceDebugInfo(
    val appearanceId: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val detectionCount: Int,
)

data class IdentityDebugInfo(
    val personId: Int,
    val appearanceIds: List<Int>,
    val appearanceTimestamps: List<Pair<Long, Long>>,
    val representativeFrameTimestampMs: Long?,
)

/**
 * @property similarityMatrix Same contents as [ClusteringDebugInfo.segmentSimilarityMatrix],
 *   carried over unchanged - `[i][j]` indexes the same appearance ids used throughout this report.
 * @property clusterAssignment appearanceId -> personId, so "which cluster did appearance N end up
 *   in" never requires manually cross-referencing [IdentityDebugInfo.appearanceIds] lists.
 */
data class AccuracyClusteringInfo(
    val similarityMatrix: List<List<Float>>,
    val clusterAssignment: Map<Int, Int>,
    val mergeLog: List<String>,
    val similarityThresholdUsed: Float,
)

data class RepresentativeFrameDebugInfo(
    val personId: Int,
    val selectedTimestampMs: Long,
    val score: FrameQualityScore,
)
