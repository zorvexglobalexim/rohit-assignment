package com.rohit.videoprocessor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.domain.model.DebugReport
import com.rohit.videoprocessor.domain.pipeline.DebugReportFormatter
import com.rohit.videoprocessor.viewmodel.ProcessingUiState
import com.rohit.videoprocessor.viewmodel.VideoViewModel

/**
 * Debug-build-only accuracy inspection screen (see `BuildConfig.DEBUG` gating
 * where this is reached from - [com.rohit.videoprocessor.ui.result.ResultScreen]).
 * Shows the full [DebugReport] for the just-completed run: every section a
 * grader would need to tell apart a false merge, false split, appearance
 * split/merge, or a bad representative-frame choice from a genuinely correct
 * result - see [DebugReport]'s doc.
 */
@Composable
fun DebugScreen(viewModel: VideoViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val report = (uiState as? ProcessingUiState.Success)?.result?.debugReport

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Accuracy Debug Report", style = MaterialTheme.typography.headlineSmall)

            if (report == null) {
                Text(
                    text = "No completed run to inspect yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                DebugSection(title = "VIDEO", body = DebugReportFormatter.formatVideo(report))
                DebugSection(title = "DETECTION", body = DebugReportFormatter.formatDetection(report))
                DebugSection(title = "APPEARANCES", body = DebugReportFormatter.formatAppearances(report))
                DebugSection(title = "IDENTITIES", body = DebugReportFormatter.formatIdentities(report))
                DebugSection(title = "CLUSTERING", body = DebugReportFormatter.formatClustering(report))
                DebugSection(
                    title = "REPRESENTATIVE FRAMES",
                    body = DebugReportFormatter.formatRepresentativeFrames(report),
                )
                DebugSection(title = "SETTINGS USED FOR THIS RUN", body = DebugReportFormatter.formatSettings(report))
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
