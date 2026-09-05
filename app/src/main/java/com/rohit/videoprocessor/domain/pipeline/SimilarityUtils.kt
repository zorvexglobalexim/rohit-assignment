package com.rohit.videoprocessor.domain.pipeline

import kotlin.math.sqrt

/**
 * Pure vector math for comparing face embeddings - no Android/ML dependency,
 * fully testable under a plain JVM unit test.
 */
object SimilarityUtils {

    /**
     * Cosine similarity. Assumes both inputs are already L2-normalized (true
     * for every [com.rohit.videoprocessor.domain.model.FaceEmbedding] this
     * app produces - see the embedding model README section), in which case
     * cosine similarity reduces to a plain dot product.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimension mismatch: ${a.size} vs ${b.size}" }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares).coerceAtLeast(MIN_NORM)
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    /** Elementwise mean of [vectors], re-normalized to unit length. */
    fun meanEmbedding(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Cannot average zero vectors" }
        val dim = vectors.first().size
        val sums = FloatArray(dim)
        for (vector in vectors) {
            require(vector.size == dim) { "Inconsistent embedding dimension: ${vector.size} vs $dim" }
            for (i in 0 until dim) sums[i] += vector[i]
        }
        for (i in 0 until dim) sums[i] /= vectors.size
        return l2Normalize(sums)
    }

    /**
     * Robust aggregate for a set of embeddings that are all supposed to be
     * the same face (e.g. one appearance segment's samples): computes a
     * preliminary mean, discards any sample whose similarity to it falls
     * below [qualityThreshold] (a likely misdetection/tracking mistake or
     * severe blur), then returns the mean of the survivors. Never discards
     * everything - falls back to the preliminary mean if every sample is
     * rejected (better than failing outright; see [IdentityClusterer]).
     */
    fun robustMeanEmbedding(vectors: List<FloatArray>, qualityThreshold: Float): FloatArray {
        require(vectors.isNotEmpty()) { "Cannot aggregate zero vectors" }
        if (vectors.size == 1) return l2Normalize(vectors.first())

        val preliminaryMean = meanEmbedding(vectors)
        val survivors = vectors.filter { cosineSimilarity(it, preliminaryMean) >= qualityThreshold }
        return if (survivors.isNotEmpty()) meanEmbedding(survivors) else preliminaryMean
    }

    private const val MIN_NORM = 1e-6f
}
