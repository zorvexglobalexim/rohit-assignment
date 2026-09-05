package com.rohit.videoprocessor.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.rohit.videoprocessor.domain.model.FrameExtractionConfig
import com.rohit.videoprocessor.domain.model.VideoFrame
import com.rohit.videoprocessor.domain.model.VideoMetadata
import com.rohit.videoprocessor.domain.pipeline.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * [VideoFrameExtractor] backed by [MediaMetadataRetriever]. Frames are pulled
 * one at a time via [MediaMetadataRetriever.getFrameAtTime] - only one
 * decoded [android.graphics.Bitmap] is ever alive at a time, so extracting
 * from a long video does not require buffering it in memory.
 *
 * [MediaMetadataRetriever.OPTION_CLOSEST] is used (rather than the cheaper
 * OPTION_CLOSEST_SYNC) so the returned frame is decoded at the exact
 * requested timestamp rather than snapped to the nearest keyframe - accurate
 * timestamps matter more here than raw extraction speed, since later stages
 * key off of them.
 */
class MediaMetadataRetrieverFrameExtractor(
    private val context: Context,
) : VideoFrameExtractor {

    override suspend fun getMetadata(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        withRetriever(uri) { retriever -> readMetadata(retriever) }
    }

    override fun extractFrames(uri: Uri, config: FrameExtractionConfig): Flow<VideoFrame> = flow {
        withRetriever(uri) { retriever ->
            val metadata = readMetadata(retriever)
            if (metadata.durationMs <= 0L) {
                error("Video has no readable duration.")
            }

            var frameIndex = 0
            var timestampMs = 0L
            while (timestampMs < metadata.durationMs &&
                (config.maxFrames == null || frameIndex < config.maxFrames)
            ) {
                coroutineContext.ensureActive()

                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1_000L, // ms -> us
                    MediaMetadataRetriever.OPTION_CLOSEST,
                )

                if (bitmap != null) {
                    emit(VideoFrame(bitmap = bitmap, timestampMs = timestampMs, frameIndex = frameIndex))
                    frameIndex++
                }
                // A null bitmap means this particular timestamp couldn't be decoded
                // (e.g. right at the tail of the file) - skip it rather than fail
                // the whole extraction.

                timestampMs += config.sampleIntervalMs
            }

            if (frameIndex == 0) {
                error("No frames could be extracted from this video.")
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun extractFrameAt(uri: Uri, timestampMs: Long): VideoFrame? = withContext(Dispatchers.IO) {
        withRetriever(uri) { retriever ->
            val bitmap = retriever.getFrameAtTime(timestampMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return@withRetriever null
            // frameIndex has no meaning for a one-off re-seek outside the original sampled
            // sequence - callers needing to correlate back to a detection already have its
            // real frameIndex/timestamp from that detection itself.
            VideoFrame(bitmap = bitmap, timestampMs = timestampMs, frameIndex = -1)
        }
    }

    private inline fun <T> withRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T): T {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return block(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun readMetadata(retriever: MediaMetadataRetriever): VideoMetadata {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()

        // getFrameAtTime() bitmaps are already rotated to display orientation;
        // swap the encoded width/height so reported metadata matches what callers see.
        val (width, height) = if (rotation == 90 || rotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }

        return VideoMetadata(durationMs = durationMs, width = width, height = height, frameRate = frameRate)
    }
}
