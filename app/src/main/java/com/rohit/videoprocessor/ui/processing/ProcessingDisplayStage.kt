package com.rohit.videoprocessor.ui.processing

import com.rohit.videoprocessor.domain.model.ProcessingStage
import com.rohit.videoprocessor.ui.components.ProcessingStepState

/**
 * The 6 checklist rows shown on the Processing screen, each an honest grouping of one or more
 * real [ProcessingStage]s - a presentation-only simplification (this file has no CV/business
 * logic of its own), not fake progress: every row only turns "current"/"done" in response to
 * the real stage the [com.rohit.videoprocessor.processing.VideoProcessingPipeline] reports.
 *
 * - [VideoLoaded] <- [ProcessingStage.LoadingVideo]
 * - [FramesAnalyzed] <- [ProcessingStage.ExtractingFrames]
 * - [FindingPeople] <- [ProcessingStage.DetectingFaces], [ProcessingStage.GeneratingEmbeddings]
 *   (detection is "finding people"; embeddings only make sense once faces are found, so they're
 *   folded into the same row rather than getting a 7th row of their own)
 * - [GroupingAppearances] <- [ProcessingStage.BuildingAppearanceSegments], [ProcessingStage.ClusteringIdentities]
 *   ("grouping" spans both: detections into appearances, then appearances into people)
 * - [ChoosingBestMoments] <- [ProcessingStage.SelectingRepresentativeFrames]
 * - [CreatingCollage] <- [ProcessingStage.GeneratingCollage], [ProcessingStage.Complete]
 */
enum class ProcessingDisplayStage(val label: String) {
    VideoLoaded("Video loaded"),
    FramesAnalyzed("Frames analyzed"),
    FindingPeople("Finding people"),
    GroupingAppearances("Grouping appearances"),
    ChoosingBestMoments("Choosing best moments"),
    CreatingCollage("Creating collage"),
}

fun ProcessingStage.toDisplayStage(): ProcessingDisplayStage = when (this) {
    ProcessingStage.LoadingVideo -> ProcessingDisplayStage.VideoLoaded
    ProcessingStage.ExtractingFrames -> ProcessingDisplayStage.FramesAnalyzed
    ProcessingStage.DetectingFaces, ProcessingStage.GeneratingEmbeddings -> ProcessingDisplayStage.FindingPeople
    ProcessingStage.BuildingAppearanceSegments, ProcessingStage.ClusteringIdentities -> ProcessingDisplayStage.GroupingAppearances
    ProcessingStage.SelectingRepresentativeFrames -> ProcessingDisplayStage.ChoosingBestMoments
    ProcessingStage.GeneratingCollage, ProcessingStage.Complete -> ProcessingDisplayStage.CreatingCollage
}

/** Done/Current/Pending for every display row, given the real current [ProcessingStage]. */
fun displayStepStates(currentStage: ProcessingStage): Map<ProcessingDisplayStage, ProcessingStepState> {
    val currentDisplay = currentStage.toDisplayStage()
    val isFullyComplete = currentStage == ProcessingStage.Complete
    return ProcessingDisplayStage.entries.associateWith { stage ->
        when {
            stage.ordinal < currentDisplay.ordinal -> ProcessingStepState.Done
            stage.ordinal == currentDisplay.ordinal -> if (isFullyComplete) ProcessingStepState.Done else ProcessingStepState.Current
            else -> ProcessingStepState.Pending
        }
    }
}
