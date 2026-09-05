package com.rohit.videoprocessor.domain.pipeline

import android.graphics.Rect
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.IdentityClusteringConfig
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests. Aggregate-embedding robustness (outlier rejection) is
 * covered separately in [SimilarityUtilsTest]; these tests focus purely on
 * the clustering decision given known embedding directions - each synthetic
 * "person" here is one exact direction (similarity 1.0 within itself, 0.0 to
 * an orthogonal "person"), so the threshold behavior under test is
 * unambiguous.
 */
class IdentityClustererTest {

    private val personA = vec(1f, 0f, 0f, 0f)
    private val personB = vec(0f, 1f, 0f, 0f)
    private val personC = vec(0f, 0f, 1f, 0f)

    private val clusterer = IdentityClusterer(IdentityClusteringConfig())

    @Test
    fun twoAppearancesOfTheSamePerson_clusterIntoOneIdentity() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val seg2 = buildSegment("s2-0" to personA, startMs = 5000L, endMs = 6000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings + seg2.embeddings)

        assertEquals(1, result.identities.size)
        assertEquals(2, result.identities.single().appearances.size)
    }

    @Test
    fun twoAppearancesOfDifferentPeople_remainSeparateIdentities() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val seg2 = buildSegment("s2-0" to personB, startMs = 2000L, endMs = 3000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings + seg2.embeddings)

        assertEquals(2, result.identities.size)
        assertTrue(result.identities.all { it.appearances.size == 1 })
    }

    @Test
    fun doesNotAssumeAppearanceOrderEqualsIdentity() {
        // Chronological order is A, B, A - the 1st and 3rd appearances are the same
        // person despite B appearing in between; a naive "consecutive = same person"
        // or "order = identity" approach would get this wrong.
        val segA1 = buildSegment("a1-0" to personA, startMs = 0L, endMs = 1000L)
        val segB1 = buildSegment("b1-0" to personB, startMs = 2000L, endMs = 3000L)
        val segA2 = buildSegment("a2-0" to personA, startMs = 5000L, endMs = 6000L)
        val embeddings = segA1.embeddings + segB1.embeddings + segA2.embeddings

        val result = clusterer.cluster(listOf(segA1.segment, segB1.segment, segA2.segment), embeddings)

        assertEquals(2, result.identities.size)
        val personAIdentity = result.identities.first { it.appearances.size == 2 }
        assertEquals(listOf(segA1.segment, segA2.segment), personAIdentity.appearances)
        val personBIdentity = result.identities.first { it.appearances.size == 1 }
        assertEquals(listOf(segB1.segment), personBIdentity.appearances)
    }

    @Test
    fun identityIdsAreAssignedByEarliestAppearance_notMergeOrder() {
        val segA1 = buildSegment("a1-0" to personA, startMs = 0L, endMs = 1000L)
        val segB1 = buildSegment("b1-0" to personB, startMs = 1500L, endMs = 2500L)
        val segA2 = buildSegment("a2-0" to personA, startMs = 5000L, endMs = 6000L)
        val embeddings = segA1.embeddings + segB1.embeddings + segA2.embeddings

        val result = clusterer.cluster(listOf(segA1.segment, segB1.segment, segA2.segment), embeddings)

        val personAIdentity = result.identities.first { it.appearances.contains(segA1.segment) }
        val personBIdentity = result.identities.first { it.appearances.contains(segB1.segment) }
        assertEquals(1, personAIdentity.id) // starts at 0ms, earliest overall
        assertEquals(2, personBIdentity.id) // starts at 1500ms
    }

    @Test
    fun everyAppearanceBelongsToExactlyOneIdentity() {
        val segA1 = buildSegment("a1-0" to personA, startMs = 0L, endMs = 1000L)
        val segB1 = buildSegment("b1-0" to personB, startMs = 1500L, endMs = 2500L)
        val segC1 = buildSegment("c1-0" to personC, startMs = 3000L, endMs = 4000L)
        val segA2 = buildSegment("a2-0" to personA, startMs = 5000L, endMs = 6000L)
        val all = listOf(segA1, segB1, segC1, segA2)
        val allSegments = all.map { it.segment }
        val embeddings = all.flatMap { it.embeddings }

        val result = clusterer.cluster(allSegments, embeddings)

        // Compared by reference (===), not equals()/hashCode(): AppearanceSegment nests
        // android.graphics.Rect, whose hashCode() throws under the plain unit-test stub
        // jar - equals() has a same-reference fast path so it's safe, but Set/hashCode
        // is not, so membership is checked by identity instead of via a Set.
        val allAssignedAppearances = result.identities.flatMap { it.appearances }
        assertEquals(allSegments.size, allAssignedAppearances.size)
        assertTrue(allSegments.all { segment -> allAssignedAppearances.any { it === segment } })
        assertTrue(allAssignedAppearances.all { assigned -> allSegments.any { it === assigned } })
    }

    @Test
    fun segmentWithNoUsableEmbedding_stillGetsItsOwnIdentity() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        // seg2's face was detected but its embedding failed - deliberately not included below.
        val seg2 = buildSegment("s2-0" to personA, startMs = 2000L, endMs = 3000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings)

        assertEquals(2, result.identities.size)
        assertTrue(result.identities.any { it.appearances == listOf(seg2.segment) })
    }

    @Test
    fun doesNotAssumeFixedNumberOfPeople() {
        val builds = listOf(personA, personB, personC).mapIndexed { i, direction ->
            buildSegment("s$i-0" to direction, startMs = i * 2000L, endMs = i * 2000L + 1000L)
        }

        val result = clusterer.cluster(builds.map { it.segment }, builds.flatMap { it.embeddings })

        assertEquals(3, result.identities.size)
    }

    @Test
    fun borderlineSimilarity_justAboveThreshold_merges() {
        // Constructed so cosine similarity to personA lands just above the default 0.6
        // identitySimilarityThreshold - verified below from the actual computed matrix
        // (not just hand-derived), so this stays correct even if float rounding shifts
        // the value slightly. Two appearances this close must merge.
        val nearPersonA = vec(0.65f, 0.76f, 0f, 0f)
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val seg2 = buildSegment("s2-0" to nearPersonA, startMs = 2000L, endMs = 3000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings + seg2.embeddings)

        val similarity = result.debugInfo.segmentSimilarityMatrix[0][1]
        assertTrue("test setup should place similarity just above threshold, was $similarity", similarity in 0.6f..0.7f)
        assertEquals(1, result.identities.size)
    }

    @Test
    fun borderlineSimilarity_justBelowThreshold_remainsSeparate() {
        // Mirror of the "just above" case, on the other side of the same 0.6 threshold -
        // two appearances this far apart must NOT merge.
        val nearPersonA = vec(0.55f, 0.835f, 0f, 0f)
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val seg2 = buildSegment("s2-0" to nearPersonA, startMs = 2000L, endMs = 3000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings + seg2.embeddings)

        val similarity = result.debugInfo.segmentSimilarityMatrix[0][1]
        assertTrue("test setup should place similarity just below threshold, was $similarity", similarity in 0.5f..0.6f)
        assertEquals(2, result.identities.size)
    }

    @Test
    fun segmentWithOnlyClippedDetections_hasNoUsableEmbedding_becomesSingletonIdentity() {
        // Mirrors a real run where a spurious, low-quality appearance (face box touching
        // the frame edge) never produces any candidate frame - see AppearanceSegmenter's
        // and IdentityClusterer.aggregateSegmentEmbedding's docs. Even though this
        // segment's only embedding sample is numerically identical to personA here, it
        // must never be fed into clustering at all: a clipped-only segment always ends up
        // as its own identity, never silently merged (which could contaminate a real
        // person's cluster with an unreliable embedding) and never dropped.
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val clippedSeg = buildClippedOnlySegment("clipped-0" to personA, startMs = 2000L, endMs = 2500L)

        val result = clusterer.cluster(
            listOf(seg1.segment, clippedSeg.segment),
            seg1.embeddings + clippedSeg.embeddings,
        )

        assertEquals(2, result.identities.size)
        assertTrue(result.identities.any { it.appearances == listOf(clippedSeg.segment) })
        val matrix = result.debugInfo.segmentSimilarityMatrix
        assertTrue("clipped-only segment must have no usable aggregate embedding", matrix[1][0].isNaN())
    }

    @Test
    fun debugInfo_reportsPairwiseSimilarityForEverySegmentPair() {
        val seg1 = buildSegment("s1-0" to personA, startMs = 0L, endMs = 1000L)
        val seg2 = buildSegment("s2-0" to personB, startMs = 2000L, endMs = 3000L)

        val result = clusterer.cluster(listOf(seg1.segment, seg2.segment), seg1.embeddings + seg2.embeddings)

        val matrix = result.debugInfo.segmentSimilarityMatrix
        assertEquals(2, matrix.size)
        assertEquals(0f, matrix[0][1], 1e-5f) // orthogonal directions
        assertEquals(1f, matrix[0][0], 1e-5f) // a vector is identical to itself
    }

    // --- helpers ---

    private class Built(val segment: AppearanceSegment, val embeddings: List<FaceEmbedding>)

    private fun vec(vararg values: Float): FloatArray = SimilarityUtils.l2Normalize(values)

    private fun buildSegment(face: Pair<String, FloatArray>, startMs: Long, endMs: Long): Built {
        val (faceId, vector) = face
        val detection = TimestampedDetection(
            timestampMs = startMs,
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
        val segment = AppearanceSegment(
            startTimestampMs = startMs,
            endTimestampMs = endMs,
            detections = listOf(detection),
            candidateFrames = listOf(detection),
        )
        val embedding = FaceEmbedding(vector = vector, timestampMs = startMs, frameIndex = 0, faceId = faceId)
        return Built(segment, listOf(embedding))
    }

    /** Like [buildSegment], but the one detection is clipped (box touches the frame's left edge) and is never a candidate frame - matching what a real [AppearanceSegmenter] run would produce for a spurious, edge-of-frame detection. */
    private fun buildClippedOnlySegment(face: Pair<String, FloatArray>, startMs: Long, endMs: Long): Built {
        val (faceId, vector) = face
        val detection = TimestampedDetection(
            timestampMs = startMs,
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
            box = FaceBox(0, 300, 200, 500), // left = 0: touches the frame edge, i.e. clipped
        )
        val segment = AppearanceSegment(
            startTimestampMs = startMs,
            endTimestampMs = endMs,
            detections = listOf(detection),
            candidateFrames = emptyList(),
        )
        val embedding = FaceEmbedding(vector = vector, timestampMs = startMs, frameIndex = 0, faceId = faceId)
        return Built(segment, listOf(embedding))
    }
}
