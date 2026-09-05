package com.rohit.videoprocessor.domain.model

/**
 * The stages a video passes through, in order. [weight] is that stage's
 * approximate share of total processing time, used by
 * [com.rohit.videoprocessor.domain.pipeline.ProcessingProgressCalculator] to
 * turn (stage, progress-within-stage) into one overall 0..1 value - stages
 * differ hugely in real duration (the frame-by-frame face detection loop
 * dominates; clustering/segmentation are near-instant in-memory operations
 * once detections/embeddings exist), so a naive "stage index / 9" bar would
 * be misleading about how much work is actually left.
 *
 * [ExtractingFrames], [DetectingFaces] and [GeneratingEmbeddings] are listed
 * as three separate stages (matching the assignment's 9-stage spec) even
 * though the pipeline runs them as one interleaved per-frame streaming loop,
 * not three sequential passes - see [com.rohit.videoprocessor.viewmodel.VideoViewModel]'s
 * class doc for why real separate passes were rejected (it would mean
 * re-decoding every frame's bitmap a second time purely for cosmetic
 * progress granularity, a real performance regression for no accuracy
 * benefit). [DetectingFaces] carries almost all of that combined work's
 * weight since frames-processed is the one genuinely live, meaningful
 * progress signal available; [ExtractingFrames] and [GeneratingEmbeddings]
 * are real but near-instantaneous bookend transitions either side of it.
 */
enum class ProcessingStage(val label: String, val weight: Float) {
    LoadingVideo("Loading video", 0.02f),
    ExtractingFrames("Extracting frames", 0.01f),
    DetectingFaces("Detecting faces", 0.76f),
    GeneratingEmbeddings("Generating embeddings", 0.01f),
    BuildingAppearanceSegments("Building appearance segments", 0.03f),
    ClusteringIdentities("Clustering identities", 0.03f),
    SelectingRepresentativeFrames("Selecting representative frames", 0.08f),
    GeneratingCollage("Generating collage", 0.05f),
    Complete("Complete", 0.01f),
    ;

    companion object {
        /** Declaration order, i.e. pipeline order - relied on by [com.rohit.videoprocessor.domain.pipeline.ProcessingProgressCalculator]. */
        val ORDERED: List<ProcessingStage> = entries.toList()
    }
}
