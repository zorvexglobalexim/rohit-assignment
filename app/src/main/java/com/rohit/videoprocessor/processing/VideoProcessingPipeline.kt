package com.rohit.videoprocessor.processing

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.rohit.videoprocessor.data.collage.CollageGenerator
import com.rohit.videoprocessor.data.collage.RepresentativeImageProvider
import com.rohit.videoprocessor.domain.ProcessingResult
import com.rohit.videoprocessor.domain.model.AppearanceSegmentationConfig
import com.rohit.videoprocessor.domain.model.DebugSettings
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.FrameAnalysis
import com.rohit.videoprocessor.domain.model.FrameDebugPreview
import com.rohit.videoprocessor.domain.model.FrameExtractionConfig
import com.rohit.videoprocessor.domain.model.FrameQualityConfig
import com.rohit.videoprocessor.domain.model.IdentityClusteringConfig
import com.rohit.videoprocessor.domain.model.ProcessingStage
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import com.rohit.videoprocessor.domain.pipeline.AppearanceSegmenter
import com.rohit.videoprocessor.domain.pipeline.DebugReportBuilder
import com.rohit.videoprocessor.domain.pipeline.FaceDetector
import com.rohit.videoprocessor.domain.pipeline.FaceEmbedder
import com.rohit.videoprocessor.domain.pipeline.FaceImageQualityAnalyzer
import com.rohit.videoprocessor.domain.pipeline.IdentityClusterer
import com.rohit.videoprocessor.domain.pipeline.IdentityDebugFormatter
import com.rohit.videoprocessor.domain.pipeline.RepresentativeFrameDebugFormatter
import com.rohit.videoprocessor.domain.pipeline.RepresentativeFrameSelector
import com.rohit.videoprocessor.domain.pipeline.VideoFrameExtractor
import kotlinx.coroutines.CancellationException

/**
 * Sequences the full video -> collage pipeline: frame extraction, face
 * detection, embedding, appearance segmentation, identity clustering,
 * representative-frame selection, collage generation and the debug report -
 * in that order, reporting progress via [onProgress] along the way.
 *
 * This is a straight extraction of what used to live inline inside
 * [com.rohit.videoprocessor.viewmodel.VideoViewModel.startProcessing] - the
 * algorithm, stage order, and every config/threshold computation are
 * byte-for-byte the same, just moved out so the ViewModel is a thin adapter
 * (owns UI state, cancellation, lifecycle) instead of also being the pipeline
 * coordinator. Nothing about face detection, embeddings, segmentation,
 * clustering or representative-frame selection changed in this move - see
 * each of those classes for the actual algorithms, untouched.
 *
 * Lives outside `domain/pipeline` deliberately: unlike the pure algorithm
 * classes there (no Android/Bitmap dependency, plain-JVM-testable), this
 * class wires those interfaces to concrete Android-dependent implementations
 * ([RepresentativeImageProvider], [CollageGenerator]) and sequences real I/O -
 * an orchestration/use-case role sitting above both `domain` and `data`, not
 * a pure algorithm itself.
 */
class VideoProcessingPipeline(
    private val frameExtractor: VideoFrameExtractor,
    private val faceDetector: FaceDetector,
    private val faceEmbedder: FaceEmbedder,
    private val faceImageQualityAnalyzer: FaceImageQualityAnalyzer,
    private val representativeImageProvider: RepresentativeImageProvider,
    private val collageGenerator: CollageGenerator,
) {
    /** One progress snapshot; mirrors the fields [com.rohit.videoprocessor.domain.model.ProcessingState] needs. */
    data class Update(
        val stage: ProcessingStage,
        val stageFraction: Float? = null,
        val framesProcessed: Int = 0,
        val totalFrames: Int? = null,
        val facesDetected: Int = 0,
        val embeddingsComputed: Int = 0,
        val identitiesFound: Int = 0,
        val statusMessage: String = stage.label,
        val debugPreview: FrameDebugPreview? = null,
    )

    /**
     * Thrown instead of a bare [Throwable] so the caller can report exactly which stage failed
     * without its own separate tracking - mirrors what the inline version did with a local
     * `currentStage` variable, just carried on the exception instead.
     */
    class StageFailure(val stage: ProcessingStage, cause: Throwable) : Exception(cause.message, cause)

    suspend fun run(uri: Uri, settings: DebugSettings, onProgress: suspend (Update) -> Unit): ProcessingResult {
        var currentStage = ProcessingStage.LoadingVideo
        try {
            onProgress(Update(stage = ProcessingStage.LoadingVideo))

            val config = FrameExtractionConfig(sampleIntervalMs = settings.sampleIntervalMs)
            val metadata = frameExtractor.getMetadata(uri)
            val totalFrames = config.estimateFrameCount(metadata.durationMs).coerceAtLeast(1)

            currentStage = ProcessingStage.ExtractingFrames
            onProgress(Update(stage = ProcessingStage.ExtractingFrames, stageFraction = 0f, totalFrames = totalFrames))

            var framesProcessed = 0
            var totalDetections = 0
            var framesWithFaces = 0
            var maxFacesInSingleFrame = 0
            var embeddingErrors = 0
            val embeddings = mutableListOf<FaceEmbedding>()
            val timestampedDetections = mutableListOf<TimestampedDetection>()
            // The previously emitted debug thumbnail - recycled right before the next one
            // replaces it in the UI state, so at most one is ever alive waiting for the GC
            // instead of accumulating across the whole run (see FrameDebugPreview's doc).
            var previousDebugPreview: FrameDebugPreview? = null

            frameExtractor.extractFrames(uri, config).collect { frame ->
                // The whole body is wrapped so frame.bitmap is released on *every* exit
                // path - normal completion, a thrown exception, or a mid-frame
                // cancellation - not just the "everything went fine" path. Both
                // faceDetector.analyze() and faceEmbedder.embed() below are real suspension
                // points, so a cancel arriving between them and the old unconditional
                // recycle() call at the end of this lambda could otherwise leak exactly
                // one full-resolution frame bitmap (the one in flight at cancel time).
                try {
                    currentStage = ProcessingStage.DetectingFaces
                    val rawAnalysis = faceDetector.analyze(frame)
                    val frameWidth = frame.bitmap.width
                    val frameHeight = frame.bitmap.height

                    // DebugSettings.minFaceSizeRatio is the closest honest analogue to a
                    // "detection confidence" gate this pipeline has (see that property's doc) -
                    // applied here, once, so every downstream stage (counts, segmentation,
                    // embedding, clustering) sees only the detections that cleared it.
                    val acceptedFaces = rawAnalysis.faces.filter { face ->
                        faceSizeRatio(face.boundingBox, frameWidth, frameHeight) >= settings.minFaceSizeRatio
                    }
                    val analysis = rawAnalysis.copy(faces = acceptedFaces)

                    framesProcessed++
                    totalDetections += analysis.faces.size
                    framesWithFaces += if (analysis.faces.isNotEmpty()) 1 else 0
                    maxFacesInSingleFrame = maxOf(maxFacesInSingleFrame, analysis.faces.size)

                    // One face's embedding failing (e.g. a degenerate crop) shouldn't abort the
                    // whole video - it's skipped and counted, not fatal.
                    for (face in analysis.faces) {
                        val box = FaceBox(
                            left = face.boundingBox.left,
                            top = face.boundingBox.top,
                            right = face.boundingBox.right,
                            bottom = face.boundingBox.bottom,
                        )
                        // Sharpness/exposure must be measured now, while frame.bitmap still
                        // exists - it's recycled below and never re-decoded for this phase.
                        val imageQuality = faceImageQualityAnalyzer.analyze(frame.bitmap, box)
                        timestampedDetections += TimestampedDetection(
                            timestampMs = frame.timestampMs,
                            frameIndex = frame.frameIndex,
                            frameWidth = frameWidth,
                            frameHeight = frameHeight,
                            face = face,
                            box = box,
                            imageQuality = imageQuality,
                        )
                        try {
                            embeddings += faceEmbedder.embed(frame, face)
                        } catch (c: CancellationException) {
                            throw c
                        } catch (t: Throwable) {
                            embeddingErrors++
                        }
                    }

                    val debugPreview = buildDebugPreview(analysis)
                    previousDebugPreview?.thumbnail?.recycle()
                    previousDebugPreview = debugPreview

                    onProgress(
                        Update(
                            stage = ProcessingStage.DetectingFaces,
                            stageFraction = framesProcessed.toFloat() / totalFrames,
                            framesProcessed = framesProcessed,
                            totalFrames = totalFrames,
                            facesDetected = totalDetections,
                            embeddingsComputed = embeddings.size,
                            statusMessage = "Detecting faces",
                            debugPreview = debugPreview,
                        ),
                    )
                } finally {
                    // No further consumer of the full-res bitmap this phase (clustering is
                    // later) - drop it now that detection, embedding and the (much smaller)
                    // debug thumbnail have all been produced from it.
                    frame.bitmap.recycle()
                }
            }

            // All embedding work already happened inline above (see the class doc for
            // why) - this stage exists for reporting completeness, not extra work.
            currentStage = ProcessingStage.GeneratingEmbeddings
            onProgress(
                Update(
                    stage = ProcessingStage.GeneratingEmbeddings,
                    stageFraction = 1f,
                    framesProcessed = framesProcessed,
                    totalFrames = totalFrames,
                    facesDetected = totalDetections,
                    embeddingsComputed = embeddings.size,
                    statusMessage = "Finalizing embeddings",
                ),
            )

            currentStage = ProcessingStage.BuildingAppearanceSegments
            onProgress(
                Update(
                    stage = ProcessingStage.BuildingAppearanceSegments,
                    facesDetected = totalDetections,
                    embeddingsComputed = embeddings.size,
                ),
            )
            val appearanceSegmenter = AppearanceSegmenter(
                AppearanceSegmentationConfig(maxMissingDurationMs = settings.appearanceGapMs),
            )
            val appearanceSegments = appearanceSegmenter.segment(timestampedDetections)

            currentStage = ProcessingStage.ClusteringIdentities
            onProgress(
                Update(
                    stage = ProcessingStage.ClusteringIdentities,
                    facesDetected = totalDetections,
                    embeddingsComputed = embeddings.size,
                ),
            )
            val identityClusterer = IdentityClusterer(
                IdentityClusteringConfig(identitySimilarityThreshold = settings.identitySimilarityThreshold),
            )
            val identityClustering = identityClusterer.cluster(appearanceSegments, embeddings)

            Log.d(TAG, IdentityDebugFormatter.formatIdentities(identityClustering.identities))
            Log.d(TAG, IdentityDebugFormatter.formatDebugInfo(appearanceSegments, identityClustering))

            // Now known for real (clustering just finished) - reported from here on, never
            // estimated earlier (see ProcessingState.identitiesFound's doc).
            val identitiesFound = identityClustering.identities.size

            currentStage = ProcessingStage.SelectingRepresentativeFrames
            onProgress(
                Update(
                    stage = ProcessingStage.SelectingRepresentativeFrames,
                    facesDetected = totalDetections,
                    embeddingsComputed = embeddings.size,
                    identitiesFound = identitiesFound,
                ),
            )
            val representativeFrameSelector = RepresentativeFrameSelector(
                FrameQualityConfig(
                    frontalWeight = settings.frameQualityWeights.frontalWeight,
                    sharpnessWeight = settings.frameQualityWeights.sharpnessWeight,
                    eyesWeight = settings.frameQualityWeights.eyesWeight,
                    expressionWeight = settings.frameQualityWeights.expressionWeight,
                    visibilityWeight = settings.frameQualityWeights.visibilityWeight,
                    sizeWeight = settings.frameQualityWeights.sizeWeight,
                    exposureWeight = settings.frameQualityWeights.exposureWeight,
                ),
            )
            val representativeFrames = representativeFrameSelector.selectAll(identityClustering.identities)
            Log.d(TAG, RepresentativeFrameDebugFormatter.formatAll(representativeFrames))

            val debugReport = DebugReportBuilder.build(
                metadata = metadata,
                sampledFrameCount = framesProcessed,
                timestampedDetections = timestampedDetections,
                appearanceSegments = appearanceSegments,
                identityClustering = identityClustering,
                representativeFrames = representativeFrames,
                settingsUsed = settings,
            )

            var personImagesForDisplay = emptyMap<Int, Bitmap>()
            val collage = if (identityClustering.identities.isNotEmpty()) {
                currentStage = ProcessingStage.GeneratingCollage
                onProgress(
                    Update(
                        stage = ProcessingStage.GeneratingCollage,
                        facesDetected = totalDetections,
                        embeddingsComputed = embeddings.size,
                        identitiesFound = identitiesFound,
                    ),
                )
                val personImages = representativeImageProvider.buildImages(uri, representativeFrames)
                // CollageGenerator takes ownership of `personImages` and recycles every bitmap
                // in it immediately after drawing (see its doc) - a cheap independent copy is
                // kept here instead, purely for the optional Person Detail screen to display
                // later, since none of this pipeline's own logic changes.
                personImagesForDisplay = personImages.mapValues { (_, bitmap) ->
                    bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                }
                collageGenerator.generate(identityClustering.identities, personImages)
            } else {
                null
            }

            currentStage = ProcessingStage.Complete
            onProgress(
                Update(
                    stage = ProcessingStage.Complete,
                    stageFraction = 1f,
                    framesProcessed = framesProcessed,
                    totalFrames = totalFrames,
                    facesDetected = totalDetections,
                    embeddingsComputed = embeddings.size,
                    identitiesFound = identitiesFound,
                ),
            )

            return ProcessingResult(
                frameCount = framesProcessed,
                metadata = metadata,
                totalDetections = totalDetections,
                framesWithFaces = framesWithFaces,
                maxFacesInSingleFrame = maxFacesInSingleFrame,
                embeddings = embeddings,
                embeddingErrors = embeddingErrors,
                appearanceSegments = appearanceSegments,
                identityClustering = identityClustering,
                representativeFrames = representativeFrames,
                collage = collage,
                debugReport = debugReport,
                personImages = personImagesForDisplay,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw StageFailure(currentStage, t)
        }
    }

    /** Face size relative to the frame's shorter side - same metric [AppearanceSegmenter] uses internally, duplicated here (rather than shared) since it operates on `android.graphics.Rect`, not the pure-Kotlin `FaceBox` that keeps that class JVM-unit-testable. */
    private fun faceSizeRatio(box: Rect, frameWidth: Int, frameHeight: Int): Float {
        val shorterFrameSide = minOf(frameWidth, frameHeight)
        if (shorterFrameSide <= 0) return 0f
        val faceSize = minOf(box.width(), box.height())
        return faceSize.toFloat() / shorterFrameSide
    }

    /** Downscaled, independent copy of [analysis]'s frame plus its face boxes scaled to match. */
    private fun buildDebugPreview(analysis: FrameAnalysis, maxDimension: Int = 480): FrameDebugPreview {
        val source = analysis.frame.bitmap
        val scale = (maxDimension.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        // createScaledBitmap returns the *same* bitmap instance (no copy) when the requested
        // size already matches the source - which happens here for any frame whose long edge
        // is already <= maxDimension. The caller recycles `source` (frame.bitmap) right after
        // this returns, so without this guard `thumbnail` could silently become a recycled
        // bitmap the UI then tries to draw. An explicit copy guarantees independence.
        val thumbnail = if (scaled === source) source.copy(source.config ?: Bitmap.Config.ARGB_8888, false) else scaled

        val faceBoxes = analysis.faces.map { face ->
            val box = face.boundingBox
            Rect(
                (box.left * scale).toInt(),
                (box.top * scale).toInt(),
                (box.right * scale).toInt(),
                (box.bottom * scale).toInt(),
            )
        }

        return FrameDebugPreview(
            thumbnail = thumbnail,
            faceBoxes = faceBoxes,
            frameIndex = analysis.frame.frameIndex,
            timestampMs = analysis.frame.timestampMs,
        )
    }

    companion object {
        private const val TAG = "VideoProcessingPipeline"
    }
}
