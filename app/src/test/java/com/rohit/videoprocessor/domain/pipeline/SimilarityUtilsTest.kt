package com.rohit.videoprocessor.domain.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityUtilsTest {

    @Test
    fun cosineSimilarity_ofIdenticalVectors_isOne() {
        val v = vec(0.6f, 0.8f, 0f)
        assertEquals(1f, SimilarityUtils.cosineSimilarity(v, v), 1e-5f)
    }

    @Test
    fun cosineSimilarity_ofOrthogonalVectors_isZero() {
        val a = vec(1f, 0f, 0f)
        val b = vec(0f, 1f, 0f)
        assertEquals(0f, SimilarityUtils.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun l2Normalize_producesUnitLength() {
        val normalized = SimilarityUtils.l2Normalize(floatArrayOf(3f, 4f, 0f))
        val norm = kotlin.math.sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 1e-5f)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f, 0f), normalized, 1e-5f)
    }

    @Test
    fun meanEmbedding_ofIdenticalVectors_equalsThatVector() {
        val v = vec(1f, 0f, 0f)
        val mean = SimilarityUtils.meanEmbedding(listOf(v, v, v))
        assertArrayEquals(v, mean, 1e-5f)
    }

    @Test
    fun robustMeanEmbedding_excludesOutlierUnlikeNaiveMean() {
        val a1 = vec(1f, 0.05f, 0f, 0f)
        val a2 = vec(1f, -0.05f, 0f, 0f)
        val outlier = vec(0f, 1f, 0f, 0f) // near-orthogonal to the a1/a2 cluster
        val samples = listOf(a1, a2, outlier)
        val pureDirection = vec(1f, 0f, 0f, 0f)

        val naiveMean = SimilarityUtils.meanEmbedding(samples)
        val robustMean = SimilarityUtils.robustMeanEmbedding(samples, qualityThreshold = 0.5f)

        val naiveSimilarity = SimilarityUtils.cosineSimilarity(naiveMean, pureDirection)
        val robustSimilarity = SimilarityUtils.cosineSimilarity(robustMean, pureDirection)

        assertTrue(
            "expected rejecting the outlier to pull the aggregate closer to the majority " +
                "direction (robust=$robustSimilarity should exceed naive=$naiveSimilarity)",
            robustSimilarity > naiveSimilarity,
        )
        assertTrue(robustSimilarity > 0.95f)
    }

    @Test
    fun robustMeanEmbedding_neverRejectsEverything_fallsBackToPreliminaryMean() {
        val v1 = vec(1f, 0f, 0f, 0f)
        val v2 = vec(0.3f, 0.954f, 0f, 0f)
        val samples = listOf(v1, v2)

        // A threshold so strict neither sample can survive comparison to their own mean.
        val result = SimilarityUtils.robustMeanEmbedding(samples, qualityThreshold = 0.99f)
        val expectedFallback = SimilarityUtils.meanEmbedding(samples)

        assertArrayEquals(expectedFallback, result, 1e-5f)
    }

    @Test
    fun robustMeanEmbedding_ofSingleSample_isJustThatSampleNormalized() {
        val result = SimilarityUtils.robustMeanEmbedding(listOf(floatArrayOf(3f, 4f, 0f)), qualityThreshold = 0.9f)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f, 0f), result, 1e-5f)
    }

    private fun vec(vararg values: Float): FloatArray = SimilarityUtils.l2Normalize(values)
}
