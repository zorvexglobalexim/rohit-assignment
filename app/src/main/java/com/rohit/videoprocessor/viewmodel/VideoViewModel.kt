package com.rohit.videoprocessor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.videoprocessor.data.VideoInfoResolver
import com.rohit.videoprocessor.data.collage.CollageGenerator
import com.rohit.videoprocessor.data.collage.RepresentativeImageProvider
import com.rohit.videoprocessor.data.embedding.FaceEmbeddingEngine
import com.rohit.videoprocessor.data.export.GallerySaver
import com.rohit.videoprocessor.data.export.ShareImageProvider
import com.rohit.videoprocessor.data.face.MlKitFaceDetector
import com.rohit.videoprocessor.data.quality.BitmapFaceImageQualityAnalyzer
import com.rohit.videoprocessor.data.video.MediaMetadataRetrieverFrameExtractor
import com.rohit.videoprocessor.domain.model.CollageResult
import com.rohit.videoprocessor.domain.model.DebugSettings
import com.rohit.videoprocessor.domain.model.ProcessingState
import com.rohit.videoprocessor.domain.pipeline.FaceDetector
import com.rohit.videoprocessor.domain.pipeline.FaceEmbedder
import com.rohit.videoprocessor.domain.pipeline.FaceImageQualityAnalyzer
import com.rohit.videoprocessor.domain.pipeline.ProcessingProgressCalculator
import com.rohit.videoprocessor.domain.pipeline.VideoFrameExtractor
import com.rohit.videoprocessor.processing.VideoProcessingPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Owns the flow's state across Home -> Processing -> Result. UI reads
 * [uiState] and calls [onVideoPicked] / [startProcessing] / [cancelProcessing]
 * / [reset]. The actual pipeline sequencing (frame extraction through
 * collage generation) lives in [VideoProcessingPipeline] - this class only
 * owns UI state, cancellation and lifecycle around it: it builds the
 * pipeline, launches it, and maps its progress callback / result / failure
 * into [uiState], nothing more.
 */
class VideoViewModel @JvmOverloads constructor(
    application: Application,
    private val videoInfoResolver: VideoInfoResolver = VideoInfoResolver(application),
    private val frameExtractor: VideoFrameExtractor = MediaMetadataRetrieverFrameExtractor(application),
    private val faceDetectorFactory: () -> FaceDetector = { MlKitFaceDetector() },
    private val faceEmbedderFactory: () -> FaceEmbedder = { FaceEmbeddingEngine(application) },
    private val faceImageQualityAnalyzer: FaceImageQualityAnalyzer = BitmapFaceImageQualityAnalyzer(),
    private val representativeImageProvider: RepresentativeImageProvider = RepresentativeImageProvider(frameExtractor),
    private val collageGenerator: CollageGenerator = CollageGenerator(),
    private val gallerySaver: GallerySaver = GallerySaver(application),
    private val shareImageProvider: ShareImageProvider = ShareImageProvider(application),
) : AndroidViewModel(application) {

    // Constructing the real FaceDetector/FaceEmbedder is genuinely heavy (the TFLite
    // Interpreter parses the graph and applies the XNNPACK delegate synchronously) - lazy so
    // that work happens the first time it's actually used, inside the pipeline's
    // Dispatchers.Default coroutine, never eagerly on the Main thread at ViewModel
    // construction time (which is where a plain constructor-default value would run).
    private val faceDetectorLazy: Lazy<FaceDetector> = lazy { faceDetectorFactory() }
    private val faceEmbedderLazy: Lazy<FaceEmbedder> = lazy { faceEmbedderFactory() }
    private val faceDetector: FaceDetector get() = faceDetectorLazy.value
    private val faceEmbedder: FaceEmbedder get() = faceEmbedderLazy.value

    // Built lazily too: resolving faceDetector/faceEmbedder for the first time here is exactly
    // what triggers their lazy construction, deferred until startProcessing() actually runs.
    private val pipeline: Lazy<VideoProcessingPipeline> = lazy {
        VideoProcessingPipeline(
            frameExtractor = frameExtractor,
            faceDetector = faceDetector,
            faceEmbedder = faceEmbedder,
            faceImageQualityAnalyzer = faceImageQualityAnalyzer,
            representativeImageProvider = representativeImageProvider,
            collageGenerator = collageGenerator,
        )
    }

    private val _uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private val _collageActionState = MutableStateFlow<CollageActionState>(CollageActionState.Idle)
    val collageActionState: StateFlow<CollageActionState> = _collageActionState.asStateFlow()

    // Debug-only tuning knobs (see DebugSettings' doc) - read fresh at the start of every
    // startProcessing() run so a change here takes effect on the *next* run without needing
    // to reconstruct this ViewModel. Never written to from production (non-debug-build) UI.
    private val _debugSettings = MutableStateFlow(DebugSettings())
    val debugSettings: StateFlow<DebugSettings> = _debugSettings.asStateFlow()

    fun updateDebugSettings(settings: DebugSettings) {
        _debugSettings.value = settings
    }

    private var processingJob: Job? = null

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = videoInfoResolver.resolve(uri)
                // Best-effort duration for the "Video Selected" screen's stat card - reads the
                // same metadata the real pipeline reads again at LoadingVideo (cheap, and this
                // is purely a display convenience, not a second copy of any pipeline logic).
                // Never fails video selection: a video whose duration can't be read yet still
                // gets picked, and the real pipeline will surface any genuine problem with it
                // when Process is tapped.
                val durationMs = try {
                    frameExtractor.getMetadata(uri).durationMs.takeIf { it > 0L }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    null
                }
                _uiState.value = ProcessingUiState.VideoSelected(info.copy(durationMs = durationMs))
            } catch (t: Throwable) {
                _uiState.value = ProcessingUiState.Error(
                    message = t.message ?: "Failed to read the selected video.",
                )
            }
        }
    }

    fun startProcessing() {
        val current = _uiState.value
        val videoInfo = when (current) {
            is ProcessingUiState.VideoSelected -> current.videoInfo
            is ProcessingUiState.Error -> current.videoInfo ?: return
            else -> return
        }

        // Dispatchers.Default (not the viewModelScope default of Main.immediate) so the whole
        // pipeline runs off the main thread; StateFlow.value is safe to set from a background thread.
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            // Captured once, up front: cancelProcessing() runs on the Main thread and writes
            // _uiState synchronously right after calling Job.cancel() (which takes effect
            // immediately/atomically, visible from any thread). Without this guard, a progress
            // callback already in flight on this background thread at that exact moment could
            // still win the race and overwrite cancelProcessing()'s VideoSelected/Idle write
            // with a stale Processing one - leaving the Processing screen stuck showing frozen
            // progress forever, since nothing else would update _uiState afterward. Checking
            // this specific coroutine's own Job (not the mutable processingJob field, which
            // could in principle point at a different run by the time this reads it) closes
            // that window down to a single atomic read.
            val thisJob = coroutineContext[Job]!!

            fun publish(state: ProcessingUiState) {
                if (thisJob.isActive) _uiState.value = state
            }

            try {
                val settings = _debugSettings.value
                val result = pipeline.value.run(videoInfo.uri, settings) { update ->
                    publish(
                        ProcessingUiState.Processing(
                            videoInfo = videoInfo,
                            processingState = ProcessingState(
                                stage = update.stage,
                                overallProgress = ProcessingProgressCalculator.overallProgress(update.stage, update.stageFraction),
                                stageProgress = update.stageFraction,
                                framesProcessed = update.framesProcessed,
                                totalFrames = update.totalFrames,
                                facesDetected = update.facesDetected,
                                embeddingsComputed = update.embeddingsComputed,
                                identitiesFound = update.identitiesFound,
                                statusMessage = update.statusMessage,
                            ),
                            debugPreview = update.debugPreview,
                        ),
                    )
                }
                publish(ProcessingUiState.Success(videoInfo = videoInfo, result = result))
            } catch (c: CancellationException) {
                throw c
            } catch (t: VideoProcessingPipeline.StageFailure) {
                publish(
                    ProcessingUiState.Error(
                        message = "Failed while ${t.stage.label.replaceFirstChar { it.lowercase() }}: " +
                            (t.cause?.message ?: "an unexpected error occurred."),
                        videoInfo = videoInfo,
                        failedStage = t.stage,
                    ),
                )
            }
        }
    }

    fun cancelProcessing() {
        val videoInfo = (_uiState.value as? ProcessingUiState.Processing)?.videoInfo
        processingJob?.cancel()
        _uiState.value = if (videoInfo != null) {
            ProcessingUiState.VideoSelected(videoInfo)
        } else {
            ProcessingUiState.Idle
        }
    }

    fun reset() {
        processingJob?.cancel()
        _uiState.value = ProcessingUiState.Idle
        _collageActionState.value = CollageActionState.Idle
    }

    /** Saves the current result's collage to the device Gallery via [GallerySaver]. No-op if there is no collage yet. */
    fun saveCollageToGallery() {
        val collage = currentCollage() ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _collageActionState.value = CollageActionState.Working
            _collageActionState.value = when (val result = gallerySaver.save(collage.bitmap, collageFileName())) {
                is GallerySaver.SaveResult.Success -> CollageActionState.SaveSuccess(result.uri)
                is GallerySaver.SaveResult.Failure -> CollageActionState.Error(result.message)
            }
        }
    }

    /**
     * On API 26-28, saving requires WRITE_EXTERNAL_STORAGE - the UI is responsible for
     * requesting it (a runtime-permission dialog is a UI-layer concern) and calling this
     * if the user denies it, so a clear message shows instead of a silent no-op.
     */
    fun onGalleryPermissionDenied() {
        _collageActionState.value = CollageActionState.Error(
            "Saving to Gallery needs storage permission on this Android version.",
        )
    }

    /**
     * Prepares a shareable content Uri for the current collage (see [ShareImageProvider]);
     * the caller (UI) launches the actual share sheet Intent with it. Returns null - and
     * surfaces the failure via [collageActionState] - if there is no collage or writing
     * the temp file fails.
     */
    suspend fun prepareShareUri(): Uri? {
        val collage = currentCollage() ?: return null
        return try {
            shareImageProvider.createShareUri(collage.bitmap, collageFileName())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _collageActionState.value = CollageActionState.Error(t.message ?: "Failed to prepare the image for sharing.")
            null
        }
    }

    fun dismissActionMessage() {
        _collageActionState.value = CollageActionState.Idle
    }

    private fun currentCollage(): CollageResult? = (_uiState.value as? ProcessingUiState.Success)?.result?.collage

    private fun collageFileName(): String = "video_moments_${System.currentTimeMillis()}.png"

    override fun onCleared() {
        super.onCleared()
        // Guarded so a ViewModel that was cleared without ever processing a video (e.g. the
        // user backed out from Home) doesn't force-construct these heavy objects on the Main
        // thread just to immediately close them.
        if (faceDetectorLazy.isInitialized()) faceDetectorLazy.value.close()
        if (faceEmbedderLazy.isInitialized()) faceEmbedderLazy.value.close()
    }
}
