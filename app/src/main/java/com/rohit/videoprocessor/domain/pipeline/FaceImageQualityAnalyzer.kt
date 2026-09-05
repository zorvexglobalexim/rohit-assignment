package com.rohit.videoprocessor.domain.pipeline

import android.graphics.Bitmap
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceImageQuality

/**
 * Measures pixel-derived quality ([FaceImageQuality]) for one face region of
 * one frame. Must be called while the frame's bitmap is still available -
 * see [FaceImageQuality]'s doc for why this can't be deferred.
 */
interface FaceImageQualityAnalyzer {
    fun analyze(frameBitmap: Bitmap, box: FaceBox): FaceImageQuality
}
