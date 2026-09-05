package com.rohit.videoprocessor.ui.result

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rohit.videoprocessor.BuildConfig
import com.rohit.videoprocessor.domain.ProcessingResult
import com.rohit.videoprocessor.ui.components.CollagePreview
import com.rohit.videoprocessor.ui.components.FramePrimaryButton
import com.rohit.videoprocessor.ui.components.FrameScreenPadding
import com.rohit.videoprocessor.ui.components.FrameSecondaryButton
import com.rohit.videoprocessor.ui.components.FrameSpacing
import com.rohit.videoprocessor.ui.components.PersonCard
import com.rohit.videoprocessor.ui.theme.Frame
import com.rohit.videoprocessor.viewmodel.CollageActionState
import com.rohit.videoprocessor.viewmodel.ProcessingUiState
import com.rohit.videoprocessor.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

/**
 * The hero screen. All save/share logic below (permission handling, the share intent, the
 * snackbar reacting to [VideoViewModel.collageActionState]) is unchanged from before this
 * redesign - only the visual tree around it changed.
 */
@Composable
fun ResultScreen(
    viewModel: VideoViewModel,
    onReset: () -> Unit,
    onBack: () -> Unit = onReset,
    onDebugInfoRequested: () -> Unit = {},
    onPersonSelected: (Int) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionState by viewModel.collageActionState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.saveCollageToGallery() else viewModel.onGalleryPermissionDenied()
    }

    fun onSaveClick() {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission && !alreadyGranted) {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveCollageToGallery()
        }
    }

    fun onShareClick() {
        coroutineScope.launch {
            val uri = viewModel.prepareShareUri() ?: return@launch
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share your collage"))
        }
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is CollageActionState.SaveSuccess -> {
                val result = snackbarHostState.showSnackbar(
                    message = "Saved to Gallery.",
                    actionLabel = "View",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(state.uri, "image/png")
                    }
                    context.startActivity(viewIntent)
                }
                viewModel.dismissActionMessage()
            }
            is CollageActionState.Error -> {
                snackbarHostState.showSnackbar(message = state.message)
                viewModel.dismissActionMessage()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = Frame.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = FrameScreenPadding, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Back", style = Frame.typography.body, color = Frame.colors.textSecondary)
            }

            when (val state = uiState) {
                is ProcessingUiState.Success -> ResultContent(
                    result = state.result,
                    isWorking = actionState is CollageActionState.Working,
                    onSaveClick = ::onSaveClick,
                    onShareClick = ::onShareClick,
                    onReset = onReset,
                    onDebugInfoRequested = onDebugInfoRequested,
                    onPersonSelected = onPersonSelected,
                )

                is ProcessingUiState.Error -> {
                    Text(
                        text = "Processing failed",
                        style = Frame.typography.screenTitle,
                        color = Frame.colors.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    state.failedStage?.let { stage ->
                        Text(
                            text = "Failed at stage: ${stage.label}",
                            style = Frame.typography.secondary,
                            color = Frame.colors.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        text = state.message,
                        style = Frame.typography.body,
                        color = Frame.colors.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                    )
                    FrameSecondaryButton(text = "Process another video", onClick = onReset, modifier = Modifier.fillMaxWidth())
                }

                else -> {
                    Text(
                        text = "No result yet.",
                        style = Frame.typography.body,
                        color = Frame.colors.textSecondary,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    result: ProcessingResult,
    isWorking: Boolean,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onReset: () -> Unit,
    onDebugInfoRequested: () -> Unit,
    onPersonSelected: (Int) -> Unit,
) {
    val totalPeople = result.identityClustering.identities.size
    val totalAppearances = result.appearanceSegments.size

    Text(
        text = "Your collage",
        style = Frame.typography.screenTitle,
        color = Frame.colors.textPrimary,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = "$totalPeople ${if (totalPeople == 1) "person" else "people"} · " +
            "$totalAppearances ${if (totalAppearances == 1) "appearance" else "appearances"}",
        style = Frame.typography.secondary,
        color = Frame.colors.textSecondary,
        modifier = Modifier.padding(top = 4.dp, bottom = FrameSpacing),
    )

    if (result.collage != null) {
        CollagePreview(
            bitmap = result.collage.bitmap.asImageBitmap(),
            contentDescription = "Collage of ${result.collage.totalPeople} people found in the video",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FrameSpacing),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FramePrimaryButton(
                text = "↓  Save",
                onClick = onSaveClick,
                enabled = !isWorking,
                modifier = Modifier.weight(1f),
            )
            FrameSecondaryButton(
                text = "↗  Share",
                onClick = onShareClick,
                enabled = !isWorking,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Text(
            text = "No people were found in this video, so no collage was generated.",
            style = Frame.typography.body,
            color = Frame.colors.textSecondary,
        )
    }

    TextButton(
        onClick = onReset,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FrameSpacing),
    ) {
        Text("Process another video", style = Frame.typography.body, color = Frame.colors.accentSecondary)
    }

    if (totalPeople > 0) {
        Text(
            text = "People",
            style = Frame.typography.bodyEmphasis,
            color = Frame.colors.textPrimary,
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            result.identityClustering.identities.forEach { identity ->
                PersonCard(
                    personId = identity.id,
                    appearanceCount = identity.appearances.size,
                    image = result.personImages[identity.id]?.asImageBitmap(),
                    onClick = { onPersonSelected(identity.id) },
                )
            }
        }
    }

    if (BuildConfig.DEBUG) {
        TextButton(
            onClick = onDebugInfoRequested,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FrameSpacing),
        ) {
            Text("Debug Info", style = Frame.typography.secondary, color = Frame.colors.muted)
        }
    }
}
