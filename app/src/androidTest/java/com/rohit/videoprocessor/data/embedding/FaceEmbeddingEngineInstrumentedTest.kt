package com.rohit.videoprocessor.data.embedding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.VideoFrame
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * Runs the real bundled `mobilefacenet.tflite` model on-device via
 * [FaceEmbeddingEngine] - this is what actually proves the model loads,
 * the input/output shapes are what the code assumes, and inference runs
 * without crashing on a real device, none of which a JVM unit test or a
 * Gradle build alone can verify.
 */
@RunWith(AndroidJUnit4::class)
class FaceEmbeddingEngineInstrumentedTest {

    private lateinit var engine: FaceEmbeddingEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Constructing this alone exercises model loading + input/output shape validation.
        engine = FaceEmbeddingEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun embed_returnsUnitLengthVectorOfConsistentSize() = runBlocking {
        val frame = fakeFrame(width = 640, height = 480)
        val face = fakeFace(id = "0-0", box = Rect(200, 120, 400, 360))

        val embedding = engine.embed(frame, face)

        assertEquals(0L, embedding.timestampMs)
        assertEquals(0, embedding.frameIndex)
        assertEquals("0-0", embedding.faceId)
        assertTrue("embedding vector should be non-empty", embedding.vector.isNotEmpty())

        val norm = sqrt(embedding.vector.fold(0f) { acc, v -> acc + v * v })
        assertTrue("expected L2-normalized vector (norm ~1.0), was $norm", norm in 0.99f..1.01f)
    }

    @Test
    fun embed_toleratesFaceBoxNearFrameEdge() = runBlocking {
        // A generous crop around a box touching the top-left corner will extend
        // outside the frame on two sides - this must be padded, not crash.
        val frame = fakeFrame(width = 320, height = 320)
        val face = fakeFace(id = "1-0", box = Rect(0, 0, 60, 60))

        val embedding = engine.embed(frame, face)

        val norm = sqrt(embedding.vector.fold(0f) { acc, v -> acc + v * v })
        assertTrue("expected L2-normalized vector (norm ~1.0), was $norm", norm in 0.99f..1.01f)
    }

    @Test
    fun embed_differentCropsProduceDifferentEmbeddings() = runBlocking {
        val frame = fakeFrame(width = 640, height = 480)
        val faceA = fakeFace(id = "0-0", box = Rect(0, 0, 200, 200))
        val faceB = fakeFace(id = "0-1", box = Rect(400, 280, 600, 480))

        val embeddingA = engine.embed(frame, faceA)
        val embeddingB = engine.embed(frame, faceB)

        assertEquals(embeddingA.vector.size, embeddingB.vector.size)
        assertNotEquals(embeddingA.vector.toList(), embeddingB.vector.toList())
    }

    private fun fakeFrame(width: Int, height: Int): VideoFrame {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // A gradient-ish pattern so different crop regions are not pixel-identical.
        canvas.drawColor(Color.rgb(60, 90, 120))
        canvas.drawRect(Rect(0, 0, width / 2, height / 2), android.graphics.Paint().apply { color = Color.rgb(200, 150, 100) })
        canvas.drawRect(Rect(width / 2, height / 2, width, height), android.graphics.Paint().apply { color = Color.rgb(30, 200, 30) })
        return VideoFrame(bitmap = bitmap, timestampMs = 0L, frameIndex = 0)
    }

    private fun fakeFace(id: String, box: Rect): DetectedFace = DetectedFace(
        id = id,
        boundingBox = box,
        trackingId = null,
        headEulerAngleX = null,
        headEulerAngleY = null,
        headEulerAngleZ = null,
        leftEyeOpenProbability = null,
        rightEyeOpenProbability = null,
        smilingProbability = null,
    )
}
