package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.ClusteringDebugInfo
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.IdentityClusteringConfig
import com.rohit.videoprocessor.domain.model.IdentityClusteringResult
import com.rohit.videoprocessor.domain.model.PersonIdentity
import kotlin.math.max
import kotlin.math.min

/**
 * Groups [AppearanceSegment]s that belong to the same real person into
 * [PersonIdentity]s. Pure, stateless, deterministic - no Android/ML
 * dependency, fully testable under a plain JVM unit test.
 *
 * IMPORTANT: appearance order plays no role in clustering. Two segments are
 * grouped *only* because their embeddings are similar - the 1st and 3rd
 * appearances in a video can be the same person while the 1st and 2nd are
 * different people, and this algorithm has no notion of "next" or "previous"
 * appearance being more likely related. [PersonIdentity.id] is assigned only
 * *after* clustering finishes, purely for stable/human-readable numbering
 * (by earliest appearance), never to decide membership.
 *
 * Algorithm - deliberately simple agglomerative clustering with a threshold
 * cutoff, appropriate for the handful of appearances a short video produces
 * (no need for k-means, DBSCAN, or any heavier ML: the number of people is
 * unknown up front, which rules out k-means directly, and at this scale an
 * O(n^2)-per-step approach is trivially fast and easy to reason about/test):
 *
 * 1. For each segment, compute one aggregate embedding via
 *    [SimilarityUtils.robustMeanEmbedding] over its candidate-quality
 *    detections (falling back to all its detections if none cleared that
 *    bar) - see [aggregateSegmentEmbedding].
 * 2. Repeatedly find the two *clusters* (starting one-per-segment) whose
 *    centroids have the highest cosine similarity; if that similarity clears
 *    [IdentityClusteringConfig.identitySimilarityThreshold], merge them and
 *    recompute the merged centroid as the mean of all its members. Stop when
 *    the best remaining pair no longer clears the threshold.
 * 3. Optionally (see [IdentityClusteringConfig.minClusterSize]) force-merge
 *    any resulting cluster smaller than the configured minimum into its
 *    nearest neighbor, regardless of the threshold.
 * 4. Sort clusters by earliest appearance and assign IDs 1..N.
 *
 * A segment with no usable embedding at all (every one of its faces failed
 * embedding) still becomes its own singleton identity - every appearance
 * must belong to exactly one identity.
 */
class IdentityClusterer(private val config: IdentityClusteringConfig = IdentityClusteringConfig()) {

    fun cluster(segments: List<AppearanceSegment>, embeddings: List<FaceEmbedding>): IdentityClusteringResult {
        if (segments.isEmpty()) {
            return IdentityClusteringResult(emptyList(), ClusteringDebugInfo(emptyList(), emptyList()))
        }

        val embeddingsByFaceId = embeddings.associateBy { it.faceId }
        val aggregates: List<FloatArray?> = segments.map { aggregateSegmentEmbedding(it, embeddingsByFaceId) }
        val similarityMatrix = buildSimilarityMatrix(aggregates)

        // Positions here index into this list, not directly into `segments` -
        // `originalIndex` maps back for every cluster member.
        val clusterableIndices = aggregates.indices.filter { aggregates[it] != null }
        val mergeLog = mutableListOf<String>()

        val clusters = clusterableIndices.indices
            .map { position -> Cluster(mutableListOf(position), aggregates[clusterableIndices[position]]!!.copyOf()) }
            .toMutableList()

        while (clusters.size > 1) {
            var bestI = -1
            var bestJ = -1
            var bestSim = Float.NEGATIVE_INFINITY
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val sim = SimilarityUtils.cosineSimilarity(clusters[i].centroid, clusters[j].centroid)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestI = i
                        bestJ = j
                    }
                }
            }
            if (bestSim < config.identitySimilarityThreshold) break

            val mergedMembers = (clusters[bestI].members + clusters[bestJ].members).toMutableList()
            val mergedCluster = Cluster(mergedMembers, SimilarityUtils.meanEmbedding(mergedMembers.map { aggregates[clusterableIndices[it]]!! }))
            mergeLog += "Merged clusters of size ${clusters[bestI].members.size} and ${clusters[bestJ].members.size} " +
                "at similarity %.3f -> new size %d".format(bestSim, mergedMembers.size)
            clusters[bestI] = mergedCluster
            clusters.removeAt(bestJ)
        }

        if (config.minClusterSize > 1) {
            enforceMinimumClusterSize(clusters, aggregates, clusterableIndices, mergeLog)
        }

        val orderedClusters = clusters.sortedBy { cluster ->
            cluster.members.minOf { segments[clusterableIndices[it]].startTimestampMs }
        }

        val identities = mutableListOf<PersonIdentity>()
        var nextId = 1
        for (cluster in orderedClusters) {
            val appearances = cluster.members
                .map { segments[clusterableIndices[it]] }
                .sortedBy { it.startTimestampMs }
            identities += PersonIdentity(id = nextId++, appearances = appearances)
        }

        val unclusterable = segments.indices
            .filter { aggregates[it] == null }
            .sortedBy { segments[it].startTimestampMs }
        for (index in unclusterable) {
            identities += PersonIdentity(id = nextId++, appearances = listOf(segments[index]))
        }

        return IdentityClusteringResult(
            identities = identities,
            debugInfo = ClusteringDebugInfo(segmentSimilarityMatrix = similarityMatrix, mergeLog = mergeLog),
        )
    }

    /**
     * Force-merges any cluster smaller than [IdentityClusteringConfig.minClusterSize]
     * into its nearest neighbor by centroid similarity, regardless of
     * [IdentityClusteringConfig.identitySimilarityThreshold]. No-op when every
     * cluster already meets the minimum (in particular, always a no-op at the
     * default minClusterSize=1).
     */
    private fun enforceMinimumClusterSize(
        clusters: MutableList<Cluster>,
        aggregates: List<FloatArray?>,
        clusterableIndices: List<Int>,
        mergeLog: MutableList<String>,
    ) {
        while (clusters.size > 1) {
            val tooSmall = clusters.indices.firstOrNull { clusters[it].members.size < config.minClusterSize } ?: return

            var bestOther = -1
            var bestSim = Float.NEGATIVE_INFINITY
            for (k in clusters.indices) {
                if (k == tooSmall) continue
                val sim = SimilarityUtils.cosineSimilarity(clusters[tooSmall].centroid, clusters[k].centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestOther = k
                }
            }
            if (bestOther == -1) return

            val survivor = min(tooSmall, bestOther)
            val removed = max(tooSmall, bestOther)
            val mergedMembers = (clusters[tooSmall].members + clusters[bestOther].members).toMutableList()
            mergeLog += "Force-merged undersized cluster of size ${clusters[tooSmall].members.size} into cluster of " +
                "size ${clusters[bestOther].members.size} at similarity %.3f (minClusterSize=%d)"
                    .format(bestSim, config.minClusterSize)
            clusters[survivor] = Cluster(mergedMembers, SimilarityUtils.meanEmbedding(mergedMembers.map { aggregates[clusterableIndices[it]]!! }))
            clusters.removeAt(removed)
        }
    }

    /**
     * One appearance segment's aggregate embedding: candidate-quality
     * detections preferred (see [AppearanceSegment.candidateFrames]),
     * falling back to the segment's other (non-candidate) detections if none
     * cleared that bar, so every segment with *any* successfully-embedded
     * face still gets an aggregate. Returns null only if not a single face
     * in the whole segment has a usable embedding.
     *
     * The fallback tier excludes clipped detections ([DetectionGeometry.isClipped])
     * even though it otherwise tolerates lower quality (small size, closed eyes)
     * than the candidate bar requires: those are gradations of quality still
     * usable in a pinch, but a clipped face is missing part of its own geometry -
     * structurally unreliable for embedding, never a reasonable fallback. A
     * segment whose detections are *all* clipped therefore has no usable
     * embedding at all and becomes its own singleton identity (see [cluster]'s
     * doc) rather than contaminating clustering with an untrustworthy signal.
     */
    private fun aggregateSegmentEmbedding(
        segment: AppearanceSegment,
        embeddingsByFaceId: Map<String, FaceEmbedding>,
    ): FloatArray? {
        val candidatePool = segment.candidateFrames.mapNotNull { embeddingsByFaceId[it.face.id]?.vector }
        val fallbackPool = segment.detections
            .filterNot { DetectionGeometry.isClipped(it) }
            .mapNotNull { embeddingsByFaceId[it.face.id]?.vector }
        val pool = candidatePool.ifEmpty { fallbackPool }
        if (pool.isEmpty()) return null
        return SimilarityUtils.robustMeanEmbedding(pool, config.embeddingQualityThreshold)
    }

    private fun buildSimilarityMatrix(aggregates: List<FloatArray?>): List<List<Float>> =
        aggregates.indices.map { i ->
            aggregates.indices.map { j ->
                val a = aggregates[i]
                val b = aggregates[j]
                if (a == null || b == null) Float.NaN else SimilarityUtils.cosineSimilarity(a, b)
            }
        }

    /** Mutable accumulator for a cluster being built; `members` are positions into `clusterableIndices`. */
    private class Cluster(val members: MutableList<Int>, val centroid: FloatArray)
}
