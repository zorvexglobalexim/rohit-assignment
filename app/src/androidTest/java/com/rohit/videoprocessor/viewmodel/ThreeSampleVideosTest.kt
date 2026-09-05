package com.rohit.videoprocessor.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * One-off verification run across all three grading sample videos (pushed to
 * `/sdcard/Download/sample{1,2,3}.mp4` before running this) - drives the real
 * [VideoViewModel] end to end for each, asserts it reaches [ProcessingUiState.Success],
 * and saves the generated collage bitmap to this app's external files dir for manual
 * visual inspection (of the cream/green/gold recolor in particular). Not a permanent
 * regression test - [VideoViewModelStateMachineTest] already covers the state-machine
 * behavior itself; this is purely a multi-sample smoke run.
 */
@RunWith(AndroidJUnit4::class)
class ThreeSampleVideosTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    @Test
    fun processAllThreeSamples() = runBlocking {
        val summaries = mutableListOf<String>()

        for (name in listOf("sample1.mp4", "sample2.mp4", "sample3.mp4")) {
            // App-private filesDir, not /sdcard/Download - a raw file:// Uri into shared
            // storage fails under scoped storage even when File.exists() reports true; filesDir
            // is always directly accessible to the app that owns it, no permission needed.
            val file = File(context.filesDir, name)
            assumeTrue("$name not found at ${file.absolutePath} - push it first", file.exists())

            val viewModel = VideoViewModel(application)
            val observedStates = mutableListOf<ProcessingUiState>()
            val collector = launch(Dispatchers.Default) {
                viewModel.uiState.collect { observedStates.add(it) }
            }

            try {
                viewModel.onVideoPicked(Uri.fromFile(file))
                waitUntil {
                    viewModel.uiState.value is ProcessingUiState.VideoSelected ||
                        viewModel.uiState.value is ProcessingUiState.Error
                }
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

            val state = viewModel.uiState.value
            if (state is ProcessingUiState.Error) {
                summaries += "$name -> FAILED at ${state.failedStage?.label}: ${state.message}"
                Log.e("ThreeSampleTest", summaries.last())
                continue
            }
            assertTrue("$name: expected Success, got $state", state is ProcessingUiState.Success)
            state as ProcessingUiState.Success
            val result = state.result

            val summary = "$name -> people=${result.identityClustering.identities.size}, " +
                "appearanceSegments=${result.appearanceSegments.size}, " +
                "totalDetections=${result.totalDetections}, " +
                "frames=${result.frameCount}, " +
                "collageGenerated=${result.collage != null}, " +
                "appearancesPerPerson=${result.identityClustering.identities.map { it.appearances.size }}"
            Log.d("ThreeSampleTest", summary)
            summaries += summary

            result.collage?.let { collage ->
                val outFile = File(context.getExternalFilesDir(null), "collage_${name.removeSuffix(".mp4")}.png")
                FileOutputStream(outFile).use { out -> collage.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Log.d("ThreeSampleTest", "Saved collage for $name to ${outFile.absolutePath}")
            }
        }

        Log.d("ThreeSampleTest", "===== SUMMARY =====\n" + summaries.joinToString("\n"))
        Unit
    }

    private suspend fun waitUntil(pollIntervalMs: Long = 50L, condition: () -> Boolean) {
        while (!condition()) {
            delay(pollIntervalMs)
        }
    }
}
