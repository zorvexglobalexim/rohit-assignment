package com.rohit.videoprocessor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.domain.model.DebugSettings
import com.rohit.videoprocessor.domain.model.FrameQualityWeights
import com.rohit.videoprocessor.viewmodel.VideoViewModel

/**
 * Debug-build-only settings screen for the accuracy-tuning knobs in
 * [DebugSettings] - see that type's doc for why each exists and what it
 * forwards to. Changes apply to the *next* run (the pipeline reads
 * [VideoViewModel.debugSettings] fresh at the start of every
 * [VideoViewModel.startProcessing] call), not the one already in progress.
 * Never reachable from a release build - see the `BuildConfig.DEBUG` gate
 * where this is launched from in [com.rohit.videoprocessor.ui.home.HomeScreen].
 */
@Composable
fun DebugSettingsScreen(viewModel: VideoViewModel, onBack: () -> Unit) {
    val settings by viewModel.debugSettings.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Debug Settings", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Applies to the next run. Not available in release builds.",
                style = MaterialTheme.typography.bodySmall,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledSlider(
                        label = "Frame sampling interval",
                        valueLabel = "${settings.sampleIntervalMs}ms",
                        value = settings.sampleIntervalMs.toFloat(),
                        range = 100f..2000f,
                        onValueChange = { viewModel.updateDebugSettings(settings.copy(sampleIntervalMs = it.toLong())) },
                    )
                    LabeledSlider(
                        label = "Min face size ratio (detection confidence)",
                        valueLabel = "%.2f".format(settings.minFaceSizeRatio),
                        value = settings.minFaceSizeRatio,
                        range = 0f..0.5f,
                        onValueChange = { viewModel.updateDebugSettings(settings.copy(minFaceSizeRatio = it)) },
                    )
                    LabeledSlider(
                        label = "Appearance gap tolerance",
                        valueLabel = "${settings.appearanceGapMs}ms",
                        value = settings.appearanceGapMs.toFloat(),
                        range = 0f..5000f,
                        onValueChange = { viewModel.updateDebugSettings(settings.copy(appearanceGapMs = it.toLong())) },
                    )
                    LabeledSlider(
                        label = "Identity similarity threshold",
                        valueLabel = "%.2f".format(settings.identitySimilarityThreshold),
                        value = settings.identitySimilarityThreshold,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateDebugSettings(settings.copy(identitySimilarityThreshold = it)) },
                    )
                }
            }

            Text(text = "Representative-frame score weights", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val weights = settings.frameQualityWeights
                    fun updateWeights(newWeights: FrameQualityWeights) {
                        viewModel.updateDebugSettings(settings.copy(frameQualityWeights = newWeights))
                    }
                    WeightSlider("Frontalness", weights.frontalWeight) { updateWeights(weights.copy(frontalWeight = it)) }
                    WeightSlider("Sharpness", weights.sharpnessWeight) { updateWeights(weights.copy(sharpnessWeight = it)) }
                    WeightSlider("Eyes open", weights.eyesWeight) { updateWeights(weights.copy(eyesWeight = it)) }
                    WeightSlider("Expression", weights.expressionWeight) { updateWeights(weights.copy(expressionWeight = it)) }
                    WeightSlider("Visibility", weights.visibilityWeight) { updateWeights(weights.copy(visibilityWeight = it)) }
                    WeightSlider("Face size", weights.sizeWeight) { updateWeights(weights.copy(sizeWeight = it)) }
                    WeightSlider("Exposure", weights.exposureWeight) { updateWeights(weights.copy(exposureWeight = it)) }
                }
            }

            Button(
                onClick = { viewModel.updateDebugSettings(DebugSettings()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to defaults")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun WeightSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    LabeledSlider(
        label = label,
        valueLabel = "%.2f".format(value),
        value = value,
        range = 0f..3f,
        onValueChange = onValueChange,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(text = "$label: $valueLabel", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
