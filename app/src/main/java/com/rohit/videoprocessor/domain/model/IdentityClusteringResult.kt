package com.rohit.videoprocessor.domain.model

/**
 * Result of [com.rohit.videoprocessor.domain.pipeline.IdentityClusterer.cluster] -
 * the final [identities] plus [debugInfo] for inspecting *why* clustering
 * produced them.
 */
data class IdentityClusteringResult(
    val identities: List<PersonIdentity>,
    val debugInfo: ClusteringDebugInfo,
)

/**
 * @property segmentSimilarityMatrix `[i][j]` = cosine similarity between the
 *   aggregate embeddings of `segments[i]` and `segments[j]` (the same
 *   `segments` list passed to `cluster()`, same order/indices). `NaN` where
 *   either segment had no usable embedding at all.
 * @property mergeLog One human-readable line per merge the clustering
 *   algorithm actually performed, in order, including the similarity that
 *   triggered it.
 */
data class ClusteringDebugInfo(
    val segmentSimilarityMatrix: List<List<Float>>,
    val mergeLog: List<String>,
)
