package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.VideoFrame
import java.io.Closeable

/**
 * Computes a [FaceEmbedding] for one [DetectedFace] within its source
 * [VideoFrame]. Implementations own a native model instance and must be
 * [close]d once the caller is done with them.
 *
 * This is the seam that lets the embedding model be swapped later (a
 * different .tflite file, a different architecture entirely) without any
 * caller needing to change.
 */
interface FaceEmbedder : Closeable {

    /** @throws FaceEmbeddingException if inference fails for this face. */
    suspend fun embed(frame: VideoFrame, face: DetectedFace): FaceEmbedding
}

/** Thrown for anything that prevents producing a valid embedding - model load, shape validation, or inference failures. */
class FaceEmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)
