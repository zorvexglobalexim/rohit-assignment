package com.rohit.videoprocessor.domain.pipeline

import android.graphics.Rect
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.DebugSettings
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.FrameQualityScore
import com.rohit.videoprocessor.domain.model.IdentityClusteringConfig
import com.rohit.videoprocessor.domain.model.RepresentativeFrame
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import com.rohit.videoprocessor.domain.model.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests, no video-specific hardcoding: every assertion is derived
 * from the *shape* of synthetic input built here (segment count, timestamps,
 * cluster membership), not a fixed expected answer tied to any real sample
 * video - mirroring [IdentityClustererTest]'s approach, which this reuses.
 */
class DebugReportBuilderTest {

    private val personA = SimilarityUtils.l2Normalize(floatArrayOf(1f, 0f, 0f, 0f))
    private val personB = SimilarityUtils.l2Normalize(floatArrayOf(0f, 1f, 0f, 0f))
    private val clusterer = IdentityClusterer(IdentityClusteringConfig())
    private val metadata = VideoMetadata(durationMs = 10_000L, width = 1080, height = 1920, frameRate = 30f)

    @Test
    fun appearanceIds_matchPositionInTheOriginalSegmentList() {
        val segA1 = buildSegment("a1-0" to personA, startMs = 0L, endMs = 1000L)
        val segB1 = buildSegment("b1-0" to personB, startMs = 2000L, endMs = 3000L)
        val segA2 = buildSegment("a2-0" to personA, startMs = 5000L, endMs = 6000L)
        val segments = listOf(segA1.segment, segB1.segment, segA2.segment)
        val embeddings = segA1.embeddings + segB1.embeddings + segA2.embeddings
        val clustering = clusterer.cluster(segments, embeddings)

        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 20,
            timestampedDetections = segments.flatMap { it.detections },
            appearanceSegments = segments,
            identityClustering = clustering,
            representativeFrames = emptyList(),
            settingsUsed = DebugSettings(),
        )

        assertEquals(listOf(0, 1, 2), report.appearances.map { it.appearanceId })
        assertEquals(0L, report.appearances[0].startTimestampMs)
        assertEquals(2000L, report.appearances[1].startTimestampMs)
        assertEquals(5000L, report.appearances[2].startTimestampMs)
    }

    @Test
    fun clusterAssignment_mapsEveryAppearanceIdToItsActualIdentity_notAppearanceOrder() {
        // A, B, A chronologically - the 1st and 3rd (ids 0 and 2) are the same person despite
        // B (id 1) appearing in between, so a naive "consecutive ids = same person" reading of
        // clusterAssignment would get this wrong if the builder didn't use real cluster membership.
        val segA1 = buildSegment("a1-0" to personA, startMs = 0L, endMs = 1000L)
        val segB1 = buildSegment("b1-0" to personB, startMs = 2000L, endMs = 3000L)
        val segA2 = buildSegment("a2-0" to personA, startMs = 5000L, endMs = 6000L)
        val segments = listOf(segA1.segment, segB1.segment, segA2.segment)
        val embeddings = segA1.embeddings + segB1.embeddings + segA2.embeddings
        val clustering = clusterer.cluster(segments, embeddings)

        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 20,
            timestampedDetections = segments.flatMap { it.detections },
            appearanceSegments = segments,
            identityClustering = clustering,
            representativeFrames = emptyList(),
            settingsUsed = DebugSettings(),
        )

        assertEquals(2, report.identities.size)
        val personAId = report.clustering.clusterAssignment.getValue(0)
        val personBId = report.clustering.clusterAssignment.getValue(1)
        assertEquals(personAId, report.clustering.clusterAssignment.getValue(2))
        assertTrue(personAId != personBId)

        val personAEntry = report.identities.first { it.personId == personAId }
        assertEquals(listOf(0, 2), personAEntry.appearanceIds.sorted())
        assertEquals(listOf(0L to 1000L, 5000L to 6000L), personAEntry.appearanceTimestamps.sortedBy { it.first })
    }

    @Test
    fun representativeFrameTimestamp_isNullForAnIdentityWithNoRepresentativeFrame() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val segments = listOf(seg1.segment)
        val clustering = clusterer.cluster(segments, seg1.embeddings)

        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 5,
            timestampedDetections = seg1.segment.detections,
            appearanceSegments = segments,
            identityClustering = clustering,
            representativeFrames = emptyList(),
            settingsUsed = DebugSettings(),
        )

        assertEquals(1, report.identities.size)
        assertNull(report.identities.single().representativeFrameTimestampMs)
    }

    @Test
    fun representativeFrameTimestamp_isPopulatedWhenProvided() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val segments = listOf(seg1.segment)
        val clustering = clusterer.cluster(segments, seg1.embeddings)
        val personId = clustering.identities.single().id
        val representative = RepresentativeFrame(
            personId = personId,
            detection = seg1.segment.detections.single().copy(timestampMs = 750L),
            score = FrameQualityScore(1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f),
        )

        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 5,
            timestampedDetections = seg1.segment.detections,
            appearanceSegments = segments,
            identityClustering = clustering,
            representativeFrames = listOf(representative),
            settingsUsed = DebugSettings(),
        )

        assertEquals(750L, report.identities.single().representativeFrameTimestampMs)
        assertEquals(1, report.representativeFrames.size)
        assertEquals(750L, report.representativeFrames.single().selectedTimestampMs)
    }

    @Test
    fun detectionsPerTimestamp_aggregatesCountsAcrossDifferentTimestamps() {
        val detectionsAt0 = listOf(detectionAt(0L, "f0-0"), detectionAt(0L, "f0-1"))
        val detectionsAt500 = listOf(detectionAt(500L, "f1-0"))
        val allDetections = detectionsAt500 + detectionsAt0 // deliberately out of chronological order

        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 2,
            timestampedDetections = allDetections,
            appearanceSegments = emptyList(),
            identityClustering = clusterer.cluster(emptyList(), emptyList()),
            representativeFrames = emptyList(),
            settingsUsed = DebugSettings(),
        )

        assertEquals(3, report.detection.totalFacesDetected)
        assertEquals(
            listOf(0L to 2, 500L to 1),
            report.detection.detectionsPerTimestamp.map { it.timestampMs to it.faceCount },
        )
    }

    @Test
    fun settingsUsed_andClusteringThreshold_arePassedThroughUnchanged() {
        val customSettings = DebugSettings(identitySimilarityThreshold = 0.42f)
        val report = DebugReportBuilder.build(
            metadata = metadata,
            sampledFrameCount = 0,
            timestampedDetections = emptyList(),
            appearanceSegments = emptyList(),
            identityClustering = clusterer.cluster(emptyList(), emptyList()),
            representativeFrames = emptyList(),
            settingsUsed = customSettings,
        )

        assertEquals(customSettings, report.settingsUsed)
        assertEquals(0.42f, report.clustering.similarityThresholdUsed, 1e-6f)
    }

    // --- helpers (mirrors IdentityClustererTest's) ---

    private class Built(val segment: AppearanceSegment, val embeddings: List<FaceEmbedding>)

    private fun detectionAt(timestampMs: Long, faceId: String): TimestampedDetection = TimestampedDetection(
        timestampMs = timestampMs,
        frameIndex = 0,
        frameWidth = 1000,
        frameHeight = 1000,
        face = DetectedFace(
            id = faceId,
            boundingBox = Rect(),
            trackingId = null,
            headEulerAngleX = null,
            headEulerAngleY = null,
            headEulerAngleZ = null,
            leftEyeOpenProbability = null,
            rightEyeOpenProbability = null,
            smilingProbability = null,
        ),
        box = FaceBox(300, 300, 500, 500),
    )

    private fun buildSegment(face: Pair<String, FloatArray>, startMs: Long, endMs: Long): Built {
        val (faceId, vector) = face
        val detection = detectionAt(startMs, faceId)
        val segment = AppearanceSegment(
            startTimestampMs = startMs,
            endTimestampMs = endMs,
            detections = listOf(detection),
            candidateFrames = listOf(detection),
        )
        val embedding = FaceEmbedding(vector = vector, timestampMs = startMs, frameIndex = 0, faceId = faceId)
        return Built(segment, listOf(embedding))
    }
}
