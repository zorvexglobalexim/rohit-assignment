package com.rohit.videoprocessor.domain.model

/**
 * A real person, identified by grouping one or more [AppearanceSegment]s
 * whose aggregate embeddings are similar enough to be the same face. [id]
 * is assigned only *after* clustering completes (stable, human-readable
 * numbering by earliest appearance) - it plays no role in deciding which
 * segments belong together, which is purely embedding similarity.
 */
data class PersonIdentity(
    val id: Int,
    val appearances: List<AppearanceSegment>,
)
