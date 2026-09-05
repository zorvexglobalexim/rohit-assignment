package com.rohit.videoprocessor.domain.model

/**
 * Tunable knobs for [com.rohit.videoprocessor.domain.pipeline.IdentityClusterer].
 * Defaults are reasoned starting points to validate against real sample
 * videos, not values with any special significance beyond the reasoning
 * documented on each property.
 */
data class IdentityClusteringConfig(
    val identitySimilarityThreshold: Float = DEFAULT_IDENTITY_SIMILARITY_THRESHOLD,
    val embeddingQualityThreshold: Float = DEFAULT_EMBEDDING_QUALITY_THRESHOLD,
    val minClusterSize: Int = DEFAULT_MIN_CLUSTER_SIZE,
) {
    init {
        require(identitySimilarityThreshold in -1f..1f) { "identitySimilarityThreshold must be a valid cosine similarity" }
        require(embeddingQualityThreshold in -1f..1f) { "embeddingQualityThreshold must be a valid cosine similarity" }
        require(minClusterSize >= 1) { "minClusterSize must be at least 1" }
    }

    companion object {
        /**
         * Cosine similarity two *appearance-level* aggregate embeddings must
         * clear to be merged into the same identity.
         *
         * Reasoning: this embedding model (see README - MobileFaceNet,
         * generous crop, no landmark alignment) is not benchmark-tuned for
         * this exact preprocessing. 0.6 is a middle-of-the-road starting
         * point: genuinely-the-same-person similarities in casual footage
         * with varied pose/lighting/expression commonly land in ~0.5-0.8,
         * while different people commonly land below ~0.4 but can spike
         * higher without landmark-based alignment. This is a reasoned
         * starting point to validate empirically against real sample
         * videos, not a benchmarked constant - see the class doc.
         */
        const val DEFAULT_IDENTITY_SIMILARITY_THRESHOLD = 0.6f

        /**
         * Cosine similarity an individual embedding sample must have to its
         * own appearance segment's preliminary mean to survive into the
         * final robust aggregate (see [com.rohit.videoprocessor.domain.pipeline.SimilarityUtils.robustMeanEmbedding]).
         *
         * Reasoning: within one continuous appearance (a person glancing at
         * frame for a second or two), pose/lighting shouldn't vary enough to
         * drop a genuine same-face similarity below ~0.5. A sample scoring
         * lower than this is more likely a tracking mistake (the wrong face
         * folded in via an imperfect bounding-box match) or severe motion
         * blur than a hard-but-real pose. Deliberately looser than
         * [identitySimilarityThreshold] since same-appearance variation
         * should be much smaller than cross-appearance variation.
         */
        const val DEFAULT_EMBEDDING_QUALITY_THRESHOLD = 0.5f

        /**
         * Minimum appearances an identity cluster must contain once normal
         * clustering finishes; smaller clusters are force-merged into their
         * nearest neighbor regardless of [identitySimilarityThreshold].
         *
         * Default 1 - effectively disabled - because a value above 1 directly
         * trades against "every appearance belongs to exactly one identity":
         * a person who is genuinely only in the video once must still get
         * their own identity, not be forced into someone else's. Exposed for
         * cases where noisy single-appearance identities prove to be a real
         * problem during tuning, not because raising it is expected to be
         * needed for a typical short portrait video.
         */
        const val DEFAULT_MIN_CLUSTER_SIZE = 1
    }
}
