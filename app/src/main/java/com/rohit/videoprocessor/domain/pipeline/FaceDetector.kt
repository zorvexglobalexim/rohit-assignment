package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.FrameAnalysis
import com.rohit.videoprocessor.domain.model.VideoFrame
import java.io.Closeable

/**
 * Runs face detection on a single [VideoFrame]. Implementations own a native
 * detector instance and must be [close]d once the caller is done with them.
 */
interface FaceDetector : Closeable {
    suspend fun analyze(frame: VideoFrame): FrameAnalysis
}
