package com.rohit.videoprocessor.domain.pipeline

import android.net.Uri
import com.rohit.videoprocessor.domain.model.FrameExtractionConfig
import com.rohit.videoprocessor.domain.model.VideoFrame
import com.rohit.videoprocessor.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Reads a video's metadata and samples frames from it without decoding the
 * whole file into memory at once. [extractFrames] is a cold [Flow]: nothing
 * is read from disk until it is collected, and collection can be cancelled
 * to stop extraction early.
 */
interface VideoFrameExtractor {

    suspend fun getMetadata(uri: Uri): VideoMetadata

    fun extractFrames(uri: Uri, config: FrameExtractionConfig = FrameExtractionConfig()): Flow<VideoFrame>

    /**
     * One-shot re-seek to a specific timestamp - used to fetch a fresh,
     * full-quality frame for a single already-chosen moment (e.g. a
     * representative frame) without re-streaming the whole video. Returns
     * null if that timestamp couldn't be decoded.
     */
    suspend fun extractFrameAt(uri: Uri, timestampMs: Long): VideoFrame?
}
