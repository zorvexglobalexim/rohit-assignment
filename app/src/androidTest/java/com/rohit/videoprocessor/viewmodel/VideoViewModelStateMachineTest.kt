package com.rohit.videoprocessor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohit.videoprocessor.domain.model.ProcessingStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real [VideoViewModel] (not its individual pipeline stages,
 * already covered elsewhere) against an actual sample video, to verify the
 * *state machine* wiring specifically: stages are visited in order, overall
 * progress never regresses, processing reaches [ProcessingUiState.Success],
 * and cancellation returns cleanly to [ProcessingUiState.VideoSelected].
 *
 * Skips itself if no sample video is found - see [com.rohit.videoprocessor.domain.pipeline.AppearanceSegmentationRealVideoTest]'s
 * doc for how to push one (same candidate paths).
 */
@RunWith(AndroidJUnit4::class)
class VideoViewModelStateMachineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    @Test
    fun processing_visitsStagesInOrder_withMonotonicProgress_andReachesSuccess() = runBlocking {
        val sampleFile = findSampleVideo()
        val viewModel = VideoViewModel(application)

        val observedStates = mutableListOf<ProcessingUiState>()
        val collector = launch(Dispatchers.Default) {
            viewModel.uiState.collect { observedStates.add(it) }
        }

        try {
            viewModel.onVideoPicked(Uri.fromFile(sampleFile))
            waitUntil { viewModel.uiState.value is ProcessingUiState.VideoSelected }

            viewModel.startProcessing()
            withTimeout(180_000) {
                waitUntil {
                    viewModel.uiState.value is ProcessingUiState.Success ||
                        viewModel.uiState.value is ProcessingUiState.Error
                }
            }
        } finally {
            collector.cancelAndJoin()
        }

        val finalState = viewModel.uiState.value
        assertTrue("expected Success, got $finalState", finalState is ProcessingUiState.Success)

        val processingStates = observedStates
            .filterIsInstance<ProcessingUiState.Processing>()
            .map { it.processingState }
        assertTrue("expected at least one Processing state to have been observed", processingStates.isNotEmpty())

        var previousStageOrdinal = -1
        var previousOverallProgress = -0.0001f
        for (state in processingStates) {
            assertTrue(
                "stage moved backward: was ordinal $previousStageOrdinal, now ${state.stage} (${state.stage.ordinal})",
                state.stage.ordinal >= previousStageOrdinal,
            )
            assertTrue(
                "overall progress regressed: was $previousOverallProgress, now ${state.overallProgress} (stage=${state.stage})",
                state.overallProgress >= previousOverallProgress,
            )
            previousStageOrdinal = state.stage.ordinal
            previousOverallProgress = state.overallProgress
        }

        val distinctStagesVisited = processingStates.map { it.stage }.distinct()
        assertTrue(
            "expected most pipeline stages to be visited, only saw $distinctStagesVisited",
            distinctStagesVisited.size >= 5,
        )
        // The dominant, real per-frame work must show live, non-trivial progress.
        val detectingFacesStates = processingStates.filter { it.stage == ProcessingStage.DetectingFaces }
        assertTrue(detectingFacesStates.isNotEmpty())
        assertTrue(detectingFacesStates.any { it.facesDetected > 0 || it.framesProcessed > 0 })
    }

    @Test
    fun cancelProcessing_stopsAndReturnsToVideoSelected() = runBlocking {
        val sampleFile = findSampleVideo()
        val viewModel = VideoViewModel(application)

        viewModel.onVideoPicked(Uri.fromFile(sampleFile))
        waitUntil { viewModel.uiState.value is ProcessingUiState.VideoSelected }

        viewModel.startProcessing()
        // Let it get meaningfully into the per-frame loop before cancelling.
        withTimeout(30_000) {
            waitUntil {
                val state = viewModel.uiState.value
                state is ProcessingUiState.Processing && state.processingState.stage == ProcessingStage.DetectingFaces
            }
        }

        viewModel.cancelProcessing()

        withTimeout(10_000) { waitUntil { viewModel.uiState.value is ProcessingUiState.VideoSelected } }
        assertEquals(ProcessingUiState.VideoSelected::class, viewModel.uiState.value::class)
    }

    private fun findSampleVideo(): File {
        val candidatePaths = listOf(
            File(context.filesDir, "sample.mp4"),
            File(context.getExternalFilesDir(null), "sample.mp4"),
            File("/sdcard/Download/sample.mp4"),
        )
        val found = candidatePaths.firstOrNull { it.exists() }
        assumeTrue("No sample video pushed to any of ${candidatePaths.map { it.path }} - skipping", found != null)
        return found!!
    }

    private suspend fun waitUntil(pollIntervalMs: Long = 50L, condition: () -> Boolean) {
        while (!condition()) {
            delay(pollIntervalMs)
        }
    }
}
