package com.rohit.videoprocessor.ui.processing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.domain.model.FrameDebugPreview
import com.rohit.videoprocessor.ui.components.FrameCardRadius
import com.rohit.videoprocessor.ui.components.FrameScreenPadding
import com.rohit.videoprocessor.ui.components.FrameSecondaryButton
import com.rohit.videoprocessor.ui.components.LockGlyph
import com.rohit.videoprocessor.ui.components.ProcessingProgress
import com.rohit.videoprocessor.ui.components.ProcessingStep
import com.rohit.videoprocessor.ui.theme.Frame
import com.rohit.videoprocessor.viewmodel.ProcessingUiState
import com.rohit.videoprocessor.viewmodel.VideoViewModel

/** Vivid, theme-independent green for detection boxes - needs to stand out against an arbitrary photo, not blend with the palette. */
private val DetectionBoxColor = Color(0xFF00E676)

/**
 * The processing/analysis experience. Every number and checklist state here comes straight
 * from [VideoViewModel.uiState] - [com.rohit.videoprocessor.domain.model.ProcessingState],
 * itself fed by the real [com.rohit.videoprocessor.processing.VideoProcessingPipeline] - never
 * a fabricated animation. See [displayStepStates] for how the pipeline's 9 real stages map onto
 * the 6 rows shown here. The live face-detection preview (current frame + green boxes) reflects
 * [ProcessingUiState.Processing.debugPreview], updated once per analyzed frame.
 */
@Composable
fun ProcessingScreen(
    viewModel: VideoViewModel,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // A single effect, not two independent ones: starting processing and reacting to its
    // outcome must see a consistent view of "have we actually entered Processing yet".
    // startProcessing() is fire-and-forget (it dispatches onto Dispatchers.Default and
    // returns immediately) so uiState is still VideoSelected for at least one recomposition
    // after calling it. A separate LaunchedEffect(uiState) reacting to that same initial
    // VideoSelected snapshot would misread "not started yet" as "the user cancelled" and
    // navigate straight back to Home - silently leaving the real pipeline running in the
    // background with no visible UI. sawProcessing makes "cancelled" mean "we were actually
    // Processing and are no longer", not "state happens to be VideoSelected right now".
    LaunchedEffect(Unit) {
        if (viewModel.uiState.value is ProcessingUiState.VideoSelected) {
            viewModel.startProcessing()
        }
        var sawProcessing = false
        viewModel.uiState.collect { state ->
            when (state) {
                is ProcessingUiState.Processing -> sawProcessing = true
                is ProcessingUiState.Success, is ProcessingUiState.Error -> onFinished()
                is ProcessingUiState.VideoSelected -> if (sawProcessing) onCancelled()
                else -> Unit
            }
        }
    }

    Scaffold(containerColor = Frame.colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = FrameScreenPadding, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = uiState) {
                is ProcessingUiState.Processing -> ProcessingContent(state, onCancel = viewModel::cancelProcessing)
                else -> {
                    // Momentary - shown only for the single frame before startProcessing()'s
                    // first real progress update lands.
                    Text(text = "Preparing…", style = Frame.typography.screenTitle, color = Frame.colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun ProcessingContent(state: ProcessingUiState.Processing, onCancel: () -> Unit) {
    val ps = state.processingState
    val steps = displayStepStates(ps.stage)

    Text(text = "Analyzing", style = Frame.typography.largeTitle, color = Frame.colors.textPrimary)
    Text(
        text = "Finding your people",
        style = Frame.typography.body,
        color = Frame.colors.textSecondary,
        modifier = Modifier.padding(top = 4.dp, bottom = 36.dp),
    )

    ProcessingProgress(progress = ps.overallProgress)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ProcessingDisplayStage.entries.forEach { stage ->
            ProcessingStep(label = stage.label, state = steps.getValue(stage))
        }
    }

    // Real, already-known numbers only - "people found" only ever appears once clustering has
    // actually finished (identitiesFound stays 0 until then, see ProcessingState's doc), never
    // estimated ahead of time.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = ps.facesDetected > 0) {
            Text(
                text = "${ps.facesDetected} faces detected",
                style = Frame.typography.secondary,
                color = Frame.colors.textSecondary,
            )
        }
        AnimatedVisibility(visible = ps.identitiesFound > 0) {
            Text(
                text = "${ps.identitiesFound} ${if (ps.identitiesFound == 1) "person" else "people"} found",
                style = Frame.typography.secondary,
                color = Frame.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    state.debugPreview?.let { preview ->
        LiveDetectionPreview(preview, modifier = Modifier.padding(top = 28.dp))
    }

    Row(
        modifier = Modifier.padding(top = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LockGlyph(color = Frame.colors.muted)
        Text(
            text = "Everything is processed on-device",
            style = Frame.typography.caption,
            color = Frame.colors.muted,
        )
    }

    FrameSecondaryButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
    )
}

/**
 * The live "what the detector is seeing" preview - the most recently analyzed frame with a
 * green box drawn over every face [com.rohit.videoprocessor.data.face.MlKitFaceDetector] found
 * in it, refreshed roughly once per sampled frame. Purely a visualization of already-computed
 * data ([FrameDebugPreview]) - no detection logic lives here.
 */
@Composable
private fun LiveDetectionPreview(preview: FrameDebugPreview, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FrameCardRadius),
        colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Frame #${preview.frameIndex} · ${preview.faceBoxes.size} " +
                    if (preview.faceBoxes.size == 1) "face detected" else "faces detected",
                style = Frame.typography.secondary,
                color = Frame.colors.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            val aspectRatio = preview.thumbnail.width.toFloat() / preview.thumbnail.height.toFloat()
            val imageBitmap = preview.thumbnail.asImageBitmap()
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            ) {
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )

                val scaleX = size.width / preview.thumbnail.width.toFloat()
                val scaleY = size.height / preview.thumbnail.height.toFloat()

                preview.faceBoxes.forEach { box ->
                    drawRect(
                        color = DetectionBoxColor,
                        topLeft = Offset(box.left * scaleX, box.top * scaleY),
                        size = Size((box.right - box.left) * scaleX, (box.bottom - box.top) * scaleY),
                        style = Stroke(width = 5f),
                    )
                }
            }
        }
    }
}
