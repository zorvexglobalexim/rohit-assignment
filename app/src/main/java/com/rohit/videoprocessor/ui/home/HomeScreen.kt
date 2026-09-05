package com.rohit.videoprocessor.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.BuildConfig
import com.rohit.videoprocessor.domain.VideoInfo
import com.rohit.videoprocessor.ui.components.FramePrimaryButton
import com.rohit.videoprocessor.ui.components.FrameScreenPadding
import com.rohit.videoprocessor.ui.components.FrameSecondaryButton
import com.rohit.videoprocessor.ui.components.FrameSpacing
import com.rohit.videoprocessor.ui.components.LockGlyph
import com.rohit.videoprocessor.ui.components.StatCard
import com.rohit.videoprocessor.ui.components.VideoPreviewCard
import com.rohit.videoprocessor.ui.theme.Frame
import com.rohit.videoprocessor.viewmodel.ProcessingUiState
import com.rohit.videoprocessor.viewmodel.VideoViewModel

private sealed interface HomeDisplay {
    data class Empty(val errorMessage: String? = null) : HomeDisplay
    data class Selected(val videoInfo: VideoInfo, val errorMessage: String? = null) : HomeDisplay
}

private fun ProcessingUiState.toHomeDisplay(): HomeDisplay = when (this) {
    is ProcessingUiState.VideoSelected -> HomeDisplay.Selected(videoInfo)
    is ProcessingUiState.Error -> videoInfo?.let { HomeDisplay.Selected(it, message) } ?: HomeDisplay.Empty(message)
    // Processing/Success shouldn't normally be visible on Home (navigation moves away as soon
    // as either is reached) - falling back to the empty screen is the safe, non-stale default.
    else -> HomeDisplay.Empty()
}

@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    onProcessRequested: () -> Unit,
    onDebugSettingsRequested: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val display = uiState.toHomeDisplay()

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::onVideoPicked)
    }

    Scaffold(containerColor = Frame.colors.background) { padding ->
        AnimatedContent(
            targetState = display is HomeDisplay.Selected,
            transitionSpec = {
                (fadeIn(tween(260)) togetherWith fadeOut(tween(160)))
            },
            label = "homeContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { showSelected ->
            if (showSelected && display is HomeDisplay.Selected) {
                VideoSelectedContent(
                    videoInfo = display.videoInfo,
                    errorMessage = display.errorMessage,
                    onBack = viewModel::reset,
                    onChangeVideo = { pickVideoLauncher.launch("video/*") },
                    onCreateCollage = onProcessRequested,
                )
            } else {
                val emptyDisplay = display as? HomeDisplay.Empty
                HomeEmptyContent(
                    errorMessage = emptyDisplay?.errorMessage,
                    onChooseVideo = { pickVideoLauncher.launch("video/*") },
                    onDebugSettingsRequested = onDebugSettingsRequested,
                )
            }
        }
    }
}

/** Screen 1 - the premium empty-state home. */
@Composable
private fun HomeEmptyContent(
    errorMessage: String?,
    onChooseVideo: () -> Unit,
    onDebugSettingsRequested: () -> Unit,
) {
    // Scrollable rather than centered-via-weight: on a short device (small screen, or a large
    // system font size), weight-based centering would silently push the button - the one thing
    // on this screen the user must be able to reach - off-screen with no way to scroll to it.
    // A scrollable column costs a little of the "perfectly centered" look on tall screens but
    // guarantees every control stays reachable on every screen size (see the redesign's own
    // responsiveness requirement).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FrameScreenPadding, vertical = 28.dp),
    ) {
        Text(text = "FRAME", style = Frame.typography.largeTitle, color = Frame.colors.textPrimary)
        Text(
            text = "Turn videos into memories.",
            style = Frame.typography.body,
            color = Frame.colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        VideoPreviewCard(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))

        Text(
            text = "Create a people collage from your video.",
            style = Frame.typography.body,
            color = Frame.colors.textSecondary,
            modifier = Modifier.padding(top = 28.dp, bottom = 20.dp),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = Frame.typography.secondary,
                color = Frame.colors.error,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        FramePrimaryButton(
            text = "+  Choose a video",
            onClick = onChooseVideo,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockGlyph(color = Frame.colors.muted)
            Text(
                text = "Processed completely on-device",
                style = Frame.typography.caption,
                color = Frame.colors.muted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = onDebugSettingsRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Debug Settings", style = Frame.typography.secondary, color = Frame.colors.muted)
            }
        }
    }
}

/** Screen 2 - the video-selected confirmation before processing starts. */
@Composable
private fun VideoSelectedContent(
    videoInfo: VideoInfo,
    errorMessage: String?,
    onBack: () -> Unit,
    onChangeVideo: () -> Unit,
    onCreateCollage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FrameScreenPadding, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ Back", style = Frame.typography.body, color = Frame.colors.textSecondary)
            }
        }

        Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
            Text(text = "Your video", style = Frame.typography.screenTitle, color = Frame.colors.textPrimary)
            Text(
                text = "Ready to analyze",
                style = Frame.typography.body,
                color = Frame.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        VideoPreviewCard(
            fileName = videoInfo.displayName,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FrameSpacing),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = "Duration",
                value = videoInfo.durationMs?.let { formatDuration(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Video type",
                value = videoInfo.mimeType?.substringAfter('/')?.uppercase() ?: "Video",
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "We'll find the people, group their appearances and create your collage.",
            style = Frame.typography.secondary,
            color = Frame.colors.textSecondary,
            modifier = Modifier.padding(top = FrameSpacing, bottom = 4.dp),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = Frame.typography.secondary,
                color = Frame.colors.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        FramePrimaryButton(
            text = "Process Video",
            onClick = onCreateCollage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        )
        FrameSecondaryButton(
            text = "Choose another",
            onClick = onChangeVideo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
