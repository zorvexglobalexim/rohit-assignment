package com.rohit.videoprocessor.viewmodel

import com.rohit.videoprocessor.domain.ProcessingResult
import com.rohit.videoprocessor.domain.VideoInfo
import com.rohit.videoprocessor.domain.model.FrameDebugPreview
import com.rohit.videoprocessor.domain.model.ProcessingStage
import com.rohit.videoprocessor.domain.model.ProcessingState

sealed interface ProcessingUiState {
    data object Idle : ProcessingUiState
    data class VideoSelected(val videoInfo: VideoInfo) : ProcessingUiState
    data class Processing(
        val videoInfo: VideoInfo,
        val processingState: ProcessingState,
        val debugPreview: FrameDebugPreview? = null,
    ) : ProcessingUiState
    data class Success(val videoInfo: VideoInfo, val result: ProcessingResult) : ProcessingUiState
    data class Error(
        val message: String,
        val videoInfo: VideoInfo? = null,
        val failedStage: ProcessingStage? = null,
    ) : ProcessingUiState
}
