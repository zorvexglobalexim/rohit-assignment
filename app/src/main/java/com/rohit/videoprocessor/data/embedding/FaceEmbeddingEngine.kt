package com.rohit.videoprocessor.data.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceEmbedding
import com.rohit.videoprocessor.domain.model.VideoFrame
import com.rohit.videoprocessor.domain.pipeline.EyeAlignment
import com.rohit.videoprocessor.domain.pipeline.FaceEmbedder
import com.rohit.videoprocessor.domain.pipeline.FaceEmbeddingException
import com.rohit.videoprocessor.domain.pipeline.SimilarityUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [FaceEmbedder] backed by a bundled MobileFaceNet TFLite model
 * (`assets/mobilefacenet.tflite`) - fully on-device, no network. See the
 * project README for the model's source, license and exact spec.
 *
 * Preprocessing deliberately does NOT crop tightly to [DetectedFace.boundingBox]:
 * the box is expanded by [CROP_MARGIN_RATIO] and made square before resizing,
 * keeping forehead/chin/ears/background context and tolerating imprecise
 * detector boxes - see [cropFace]. When eye landmarks are available, the crop
 * is also rotated so the eyes are level *before* resizing (see [EyeAlignment]) -
 * standard face-recognition preprocessing this pipeline previously skipped
 * (see the README), and a real source of embedding drift for the same person
 * photographed at different head tilts.
 *
 * Not thread-safe by design: [pixelScratch]/[modelInputScratch]/[outputScratch]
 * below are allocated once and reused across every [embed] call instead of
 * allocating a fresh pixel array, direct [ByteBuffer] and output array per
 * face - this runs once per detected face, potentially dozens of times per
 * video, so avoiding that churn measurably reduces GC pressure. Safe only
 * because [Interpreter] itself isn't thread-safe either, so this pipeline
 * already calls [embed] sequentially, one face at a time, from a single
 * coroutine - never concurrently.
 */
class FaceEmbeddingEngine(context: Context) : FaceEmbedder {

    private val interpreter: Interpreter
    private val inputSize: Int
    private val embeddingDimension: Int

    init {
        interpreter = try {
            Interpreter(loadModelFile(context, MODEL_FILE_NAME))
        } catch (t: Throwable) {
            throw FaceEmbeddingException("Failed to load face embedding model '$MODEL_FILE_NAME' from assets.", t)
        }

        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape() // expected [1, size, size, 3]
        val validInputShape = inputShape.size == 4 &&
            inputShape[0] == 1 &&
            inputShape[1] == inputShape[2] &&
            inputShape[1] > 0 &&
            inputShape[3] == 3
        if (!validInputShape) {
            interpreter.close()
            throw FaceEmbeddingException(
                "Unexpected embedding model input shape: ${inputShape.toList()}; expected [1, size, size, 3].",
            )
        }
        if (inputTensor.dataType() != DataType.FLOAT32) {
            interpreter.close()
            throw FaceEmbeddingException(
                "Unexpected embedding model input type: ${inputTensor.dataType()}; only FLOAT32 input is supported.",
            )
        }
        inputSize = inputShape[1]

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape() // expected [1, embeddingDim]
        val validOutputShape = outputShape.size == 2 && outputShape[0] == 1 && outputShape[1] > 0
        if (!validOutputShape) {
            interpreter.close()
            throw FaceEmbeddingException(
                "Unexpected embedding model output shape: ${outputShape.toList()}; expected [1, embeddingDim].",
            )
        }
        if (outputTensor.dataType() != DataType.FLOAT32) {
            interpreter.close()
            throw FaceEmbeddingException(
                "Unexpected embedding model output type: ${outputTensor.dataType()}; only FLOAT32 output is supported.",
            )
        }
        embeddingDimension = outputShape[1]
    }

    // Sized only once inputSize/embeddingDimension are known above - see the class doc for
    // why reusing these across calls is safe and worthwhile.
    private val pixelScratch = IntArray(inputSize * inputSize)
    private val modelInputScratch: ByteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val outputScratch = Array(1) { FloatArray(embeddingDimension) }

    override suspend fun embed(frame: VideoFrame, face: DetectedFace): FaceEmbedding =
        withContext(Dispatchers.Default) {
            try {
                val cropped = cropFace(frame.bitmap, face, CROP_MARGIN_RATIO, inputSize)
                val inputBuffer = try {
                    bitmapToInputBuffer(cropped)
                } finally {
                    cropped.recycle()
                }

                interpreter.run(inputBuffer, outputScratch)

                // l2Normalize returns a *new* array, so the embedding we hand back never
                // aliases outputScratch - safe for outputScratch to be overwritten by the
                // next embed() call regardless of how long this FaceEmbedding lives after.
                FaceEmbedding(
                    vector = SimilarityUtils.l2Normalize(outputScratch[0]),
                    timestampMs = frame.timestampMs,
                    frameIndex = frame.frameIndex,
                    faceId = face.id,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                throw FaceEmbeddingException(
                    "Failed to compute face embedding for face ${face.id} at frame ${frame.frameIndex}.",
                    t,
                )
            }
        }

    override fun close() {
        interpreter.close()
    }

    /**
     * Expands [face]'s bounding box into a square region [marginRatio] larger
     * on every side, centered on the original box, optionally rotated so the
     * eyes are level (see [EyeAlignment.rotationDegrees] - null when
     * landmarks are missing or implausible, in which case this is exactly
     * the previous unaligned behavior), then draws the corresponding
     * (possibly partially out-of-bounds) area of [source] onto a same-sized
     * canvas - any part that falls outside the source frame, before or after
     * rotation, is filled with neutral gray (0 after normalization) rather
     * than clamped/shrunk, so the face stays centered and the aspect ratio
     * stays square for [targetSize] resize.
     */
    private fun cropFace(source: Bitmap, face: DetectedFace, marginRatio: Float, targetSize: Int): Bitmap {
        val box = face.boundingBox
        val side = (max(box.width(), box.height()) * (1f + 2f * marginRatio)).roundToInt().coerceAtLeast(1)
        val cropLeft = box.centerX() - side / 2
        val cropTop = box.centerY() - side / 2

        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.rgb(128, 128, 128))

        // Rotate around the box's own center (in source coordinates) first, then shift into the
        // square canvas's coordinate space - a single matrix-driven draw handles both the
        // rotated and unrotated (rotationDegrees == null -> pure translation) cases identically,
        // and Canvas.drawBitmap(Bitmap, Matrix, Paint) already clips to the destination and
        // leaves untouched (our gray fill) any canvas pixel the source doesn't project onto, so
        // no manual out-of-bounds intersection math is needed either way.
        val rotationDegrees = EyeAlignment.rotationDegrees(face.leftEyePosition, face.rightEyePosition)
        val matrix = Matrix().apply {
            if (rotationDegrees != null) {
                postRotate(-rotationDegrees, box.centerX().toFloat(), box.centerY().toFloat())
            }
            postTranslate(-cropLeft.toFloat(), -cropTop.toFloat())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        val resized = Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
        if (resized !== square) square.recycle()
        return resized
    }

    /**
     * MobileFaceNet's expected normalization for this bundled model:
     * `(pixel - 128) / 128`, mapping [0,255] to roughly [-1, 1]. Writes into
     * [pixelScratch]/[modelInputScratch] rather than allocating a fresh pixel
     * array and direct buffer per call - [bitmap] is always exactly
     * `inputSize` x `inputSize` (guaranteed by [cropFace]'s `targetSize`
     * param), which is exactly how those scratch buffers are sized.
     */
    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        bitmap.getPixels(pixelScratch, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        modelInputScratch.rewind()
        for (pixel in pixelScratch) {
            modelInputScratch.putFloat((((pixel shr 16) and 0xFF) - 128) / 128f)
            modelInputScratch.putFloat((((pixel shr 8) and 0xFF) - 128) / 128f)
            modelInputScratch.putFloat(((pixel and 0xFF) - 128) / 128f)
        }
        modelInputScratch.rewind()
        return modelInputScratch
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
    }

    companion object {
        private const val MODEL_FILE_NAME = "mobilefacenet.tflite"

        /** Fraction of the face box's larger side added as margin on every edge before squaring. */
        private const val CROP_MARGIN_RATIO = 0.4f
    }
}
