package com.rohit.videoprocessor.domain.model

/**
 * An L2-normalized face embedding vector - since `||vector|| == 1`, cosine
 * similarity between two embeddings reduces to a plain dot product.
 * [faceId] ties this back to the [DetectedFace] (`DetectedFace.id`) it was
 * computed from; [timestampMs]/[frameIndex] mirror that face's source frame
 * for convenience.
 */
data class FaceEmbedding(
    val vector: FloatArray,
    val timestampMs: Long,
    val frameIndex: Int,
    val faceId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceEmbedding) return false
        return vector.contentEquals(other.vector) &&
            timestampMs == other.timestampMs &&
            frameIndex == other.frameIndex &&
            faceId == other.faceId
    }

    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + frameIndex.hashCode()
        result = 31 * result + faceId.hashCode()
        return result
    }
}
