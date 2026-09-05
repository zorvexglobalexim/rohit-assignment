package com.rohit.videoprocessor.domain.pipeline

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohit.videoprocessor.data.collage.CollageGenerator
import com.rohit.videoprocessor.data.collage.RepresentativeImageProvider
import com.rohit.videoprocessor.data.embedding.FaceEmbeddingEngine
import com.rohit.videoprocessor.data.face.MlKitFaceDetector
import com.rohit.videoprocessor.data.quality.BitmapFaceImageQualityAnalyzer
import com.rohit.videoprocessor.data.video.MediaMetadataRetrieverFrameExtractor
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.FrameExtractionConfig
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Runs frame extraction + real ML Kit detection + face embedding + pixel
 * quality analysis + [AppearanceSegmenter] + [IdentityClusterer] +
 * [RepresentativeFrameSelector] + [RepresentativeImageProvider] +
 * [CollageGenerator] end to end against an actual sample video pushed onto
 * the device, to catch integration bugs synthetic unit tests can't (real ML
 * Kit trackingId behavior, real bounding boxes, real embeddings, real pixel
 * sharpness/brightness, real re-seeked crops, real timestamps). Not a
 * correctness assertion on exact numbers (this app must not hardcode
 * expected counts for any specific video) - it proves the whole chain runs
 * without crashing on real footage, prints what it found for manual
 * inspection, and saves the rendered collage PNG for visual review.
 *
 * Skips itself (via [assumeTrue]) if no sample video is found at any
 * candidate path.
 *
 * To run (app-private internal storage avoids all scoped-storage
 * permission/visibility issues - even `adb shell` can't list another app's
 * external-data directory on modern Android, so pushing there directly does
 * not work):
 * ```
 * adb push "<video>.mp4" /data/local/tmp/sample.mp4
 * adb shell run-as com.rohit.videoprocessor cp /data/local/tmp/sample.mp4 files/sample.mp4
 * ./gradlew connectedDebugAndroidTest
 * adb shell run-as com.rohit.videoprocessor cat files/collage_output.png > collage_output.png
 * ```
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSegmentationRealVideoTest {

    @Test
    fun realSampleVideo_segmentsClustersSelectsAndBuildsCollageWithoutCrashing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val candidatePaths = listOf(
            File(context.filesDir, "sample.mp4"),
            File(context.getExternalFilesDir(null), "sample.mp4"),
            File("/sdcard/Download/sample.mp4"),
        )
        val foundFile = candidatePaths.firstOrNull { it.exists() }
        assumeTrue(
            "No sample video pushed to any of ${candidatePaths.map { it.path }} - skipping",
            foundFile != null,
        )
        val sampleFile = foundFile!!

        val uri = Uri.fromFile(sampleFile)
        val extractor = MediaMetadataRetrieverFrameExtractor(context)
        val detector = MlKitFaceDetector()
        val embedder = FaceEmbeddingEngine(context)
        val qualityAnalyzer = BitmapFaceImageQualityAnalyzer()

        try {
            val metadata = extractor.getMetadata(uri)
            println("[RealVideoTest] duration=${metadata.durationMs}ms size=${metadata.width}x${metadata.height}")

            val detections = mutableListOf<TimestampedDetection>()
            val embeddings = mutableListOf<FaceEmbedding>()
            var frameCount = 0
            var totalFaces = 0
            var embeddingErrors = 0

            extractor.extractFrames(uri, FrameExtractionConfig()).collect { frame ->
                val analysis = detector.analyze(frame)
                frameCount++
                totalFaces += analysis.faces.size
                for (face in analysis.faces) {
                    val box = FaceBox(
                        left = face.boundingBox.left,
                        top = face.boundingBox.top,
                        right = face.boundingBox.right,
                        bottom = face.boundingBox.bottom,
                    )
                    val imageQuality = qualityAnalyzer.analyze(frame.bitmap, box)
                    detections += TimestampedDetection(
                        timestampMs = frame.timestampMs,
                        frameIndex = frame.frameIndex,
                        frameWidth = frame.bitmap.width,
                        frameHeight = frame.bitmap.height,
                        face = face,
                        box = box,
                        imageQuality = imageQuality,
                    )
                    try {
                        embeddings += embedder.embed(frame, face)
                    } catch (t: Throwable) {
                        embeddingErrors++
                    }
                }
                frame.bitmap.recycle()
            }

            val segments = AppearanceSegmenter().segment(detections)
            println(
                "[RealVideoTest] frames=$frameCount totalFaceDetections=$totalFaces " +
                    "embeddings=${embeddings.size} (errors=$embeddingErrors) appearanceSegments=${segments.size}",
            )

            val clusteringResult = IdentityClusterer().cluster(segments, embeddings)

            println("[RealVideoTest] === Identities ===")
            IdentityDebugFormatter.formatIdentities(clusteringResult.identities).lines().forEach {
                println("[RealVideoTest] $it")
            }

            val representatives = RepresentativeFrameSelector().selectAll(clusteringResult.identities)
            println("[RealVideoTest] === Representative frames ===")
            representatives.forEach { representative ->
                println("[RealVideoTest] Person ${representative.personId}")
                RepresentativeFrameDebugFormatter.format(representative).lines().forEach {
                    println("[RealVideoTest] $it")
                }
            }

            val personImages = RepresentativeImageProvider(extractor).buildImages(uri, representatives)
            println("[RealVideoTest] built ${personImages.size} representative image(s) for the collage")

            val collageResult = CollageGenerator().generate(clusteringResult.identities, personImages)
            println(
                "[RealVideoTest] collage size=${collageResult.bitmap.width}x${collageResult.bitmap.height} " +
                    "totalPeople=${collageResult.totalPeople} totalAppearances=${collageResult.totalAppearances}",
            )

            val outputFile = File(context.filesDir, "collage_output.png")
            FileOutputStream(outputFile).use { out ->
                collageResult.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            println("[RealVideoTest] saved collage to ${outputFile.absolutePath}")
        } finally {
            detector.close()
            embedder.close()
        }
    }
}
