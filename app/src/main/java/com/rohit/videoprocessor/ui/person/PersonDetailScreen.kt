package com.rohit.videoprocessor.ui.person

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.ui.components.FrameScreenPadding
import com.rohit.videoprocessor.ui.components.FrameSpacing
import com.rohit.videoprocessor.ui.theme.Frame
import com.rohit.videoprocessor.viewmodel.ProcessingUiState
import com.rohit.videoprocessor.viewmodel.VideoViewModel

/**
 * Optional screen (per the redesign spec) - a person's representative photo, appearance count
 * and per-appearance timeline. Reads only data [com.rohit.videoprocessor.domain.ProcessingResult]
 * already carries (identities, representative frames, the per-person image kept alongside the
 * collage) - no new pipeline work, no CV logic of its own.
 */
@Composable
fun PersonDetailScreen(viewModel: VideoViewModel, personId: Int, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val result = (uiState as? ProcessingUiState.Success)?.result
    val identity = result?.identityClustering?.identities?.firstOrNull { it.id == personId }

    Scaffold(containerColor = Frame.colors.background) { padding ->
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

            if (result == null || identity == null) {
                Text(
                    text = "Person not found.",
                    style = Frame.typography.body,
                    color = Frame.colors.textSecondary,
                    modifier = Modifier.padding(top = 24.dp),
                )
                return@Column
            }

            val image = result.personImages[identity.id]?.asImageBitmap()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .padding(top = FrameSpacing),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
            ) {
                if (image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = image,
                        contentDescription = "Person ${identity.id}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = "Person ${identity.id.toString().padStart(2, '0')}",
                style = Frame.typography.screenTitle,
                color = Frame.colors.textPrimary,
                modifier = Modifier.padding(top = FrameSpacing),
            )
            Text(
                text = "Appeared ${identity.appearances.size} " +
                    if (identity.appearances.size == 1) "time" else "times",
                style = Frame.typography.body,
                color = Frame.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = FrameSpacing),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                identity.appearances.sortedBy { it.startTimestampMs }.forEachIndexed { index, segment ->
                    AppearanceRow(index = index, segment = segment)
                }
            }
        }
    }
}

@Composable
private fun AppearanceRow(index: Int, segment: AppearanceSegment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Appearance ${(index + 1).toString().padStart(2, '0')}",
                style = Frame.typography.body,
                color = Frame.colors.textPrimary,
            )
            Text(
                text = formatTimestamp(segment.startTimestampMs),
                style = Frame.typography.secondary,
                color = Frame.colors.accentPrimary,
            )
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
