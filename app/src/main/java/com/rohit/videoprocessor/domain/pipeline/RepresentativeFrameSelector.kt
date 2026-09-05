package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.FrameQualityConfig
import com.rohit.videoprocessor.domain.model.FrameQualityScore
import com.rohit.videoprocessor.domain.model.PersonIdentity
import com.rohit.videoprocessor.domain.model.RepresentativeFrame
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Picks exactly one representative [TimestampedDetection] per [PersonIdentity]
 * by a multi-factor quality score - pure, stateless, deterministic, no
 * Android/ML dependency beyond the plain data already computed earlier in
 * the pipeline ([TimestampedDetection.imageQuality] in particular - see that
 * type's doc for why pixel-derived signals had to be captured earlier rather
 * than read here).
 *
 * Deliberately never picks by a single dominant signal (largest face,
 * first/middle frame, or "detection confidence" - which ML Kit doesn't even
 * expose): every candidate gets a composite score across frontalness,
 * sharpness, eyes, expression, visibility/clipping, size and exposure (see
 * [FrameQualityConfig]), and the highest composite wins. This is what makes
 * "don't just pick the largest face" etc. structurally true rather than a
 * rule to remember - no single factor can dominate unless its weight is
 * configured to do so.
 *
 * Only *selects* a frame - it never crops or renders one. [RepresentativeFrame.detection]
 * carries everything (timestamp, box, frame dimensions) needed for a later
 * stage to re-extract the actual image and apply a generous crop (or use
 * the frame as-is); nothing here ever tightly crops to the face box, because
 * nothing here produces an image at all.
 */
class RepresentativeFrameSelector(private val config: FrameQualityConfig = FrameQualityConfig()) {

    /**
     * The candidate pool per identity is every appearance's `candidateFrames`
     * (Phase 5A's quality-filtered subset), falling back to `detections` if
     * that's empty - the same "prefer quality-filtered, never end up with
     * nothing" pattern used by [IdentityClusterer]'s aggregation. Returns
     * null only if the identity has no detections at all, which shouldn't
     * happen for a real [PersonIdentity] (every one is built from at least
     * one appearance with at least one detection).
     */
    fun select(identity: PersonIdentity): RepresentativeFrame? {
        val candidatePool = identity.appearances.flatMap { it.candidateFrames }
        val pool = candidatePool.ifEmpty { identity.appearances.flatMap { it.detections } }
        if (pool.isEmpty()) return null

        val (bestDetection, bestScore) = pool
            .map { it to score(it) }
            .maxByOrNull { it.second.finalScore }
            ?: return null

        return RepresentativeFrame(personId = identity.id, detection = bestDetection, score = bestScore)
    }

    fun selectAll(identities: List<PersonIdentity>): List<RepresentativeFrame> =
        identities.mapNotNull { select(it) }

    fun score(detection: TimestampedDetection): FrameQualityScore {
        val frontal = frontalScore(detection)
        val sharpness = linearScore(detection.imageQuality.sharpness, config.minAcceptableSharpness, config.goodSharpness)
        val eyes = eyesScore(detection)
        val expression = expressionScore(detection)
        val visibility = visibilityScore(detection)
        val size = sizeScore(detection)
        val exposure = exposureScore(detection)
        val clippingPenalty = if (DetectionGeometry.isClipped(detection)) config.clippingPenaltyAmount else 0f

        val weights = listOf(
            frontal to config.frontalWeight,
            sharpness to config.sharpnessWeight,
            eyes to config.eyesWeight,
            expression to config.expressionWeight,
            visibility to config.visibilityWeight,
            size to config.sizeWeight,
            exposure to config.exposureWeight,
        )
        val totalWeight = weights.sumOf { it.second.toDouble() }.toFloat()
        val weightedAverage = if (totalWeight > 0f) {
            weights.sumOf { (score, weight) -> (score * weight).toDouble() }.toFloat() / totalWeight
        } else {
            0f
        }
        val final = (weightedAverage - clippingPenalty).coerceIn(0f, 1f)

        return FrameQualityScore(
            frontalScore = frontal,
            sharpnessScore = sharpness,
            eyesScore = eyes,
            expressionScore = expression,
            visibilityScore = visibility,
            sizeScore = size,
            exposureScore = exposure,
            clippingPenalty = clippingPenalty,
            finalScore = final,
        )
    }

    private fun frontalScore(detection: TimestampedDetection): Float {
        val yaw = detection.face.headEulerAngleY ?: 0f
        val pitch = detection.face.headEulerAngleX ?: 0f
        val roll = detection.face.headEulerAngleZ ?: 0f
        val offAngle = sqrt(yaw * yaw + pitch * pitch + roll * roll)
        // Decreasing ramp: 0 degrees off -> score 1, maxAcceptableAngleDegrees -> score 0.
        return linearScore(offAngle, config.maxAcceptableAngleDegrees, 0f)
    }

    private fun eyesScore(detection: TimestampedDetection): Float {
        val left = detection.face.leftEyeOpenProbability ?: 1f
        val right = detection.face.rightEyeOpenProbability ?: 1f
        return linearScore(min(left, right), config.eyesClosedThreshold, config.eyesOpenThreshold)
    }

    private fun expressionScore(detection: TimestampedDetection): Float {
        val smiling = detection.face.smilingProbability ?: return NEUTRAL_SCORE_WHEN_UNKNOWN
        return when {
            smiling < config.idealSmilingRangeStart -> linearScore(smiling, 0f, config.idealSmilingRangeStart)
            smiling > config.idealSmilingRangeEnd -> linearScore(smiling, 1f, config.idealSmilingRangeEnd)
            else -> 1f
        }
    }

    /** Graded proximity-to-edge quality - degrades near an edge even before actually touching it. */
    private fun visibilityScore(detection: TimestampedDetection): Float {
        val shorterSide = min(detection.frameWidth, detection.frameHeight)
        if (shorterSide <= 0) return 0f
        val margin = config.edgeMarginRatio * shorterSide

        val box = detection.box
        val distances = listOf(
            box.left.toFloat(),
            box.top.toFloat(),
            (detection.frameWidth - box.right).toFloat(),
            (detection.frameHeight - box.bottom).toFloat(),
        )
        val minDistance = distances.min()
        return linearScore(minDistance, 0f, margin)
    }

    private fun sizeScore(detection: TimestampedDetection): Float {
        val shorterFrameSide = min(detection.frameWidth, detection.frameHeight)
        if (shorterFrameSide <= 0) return 0f
        val faceSize = min(detection.box.width, detection.box.height)
        val ratio = faceSize.toFloat() / shorterFrameSide
        // No upper penalty: a large/close-up face is never penalized, just not required
        // beyond idealSizeRatio - see FrameQualityConfig's doc.
        return linearScore(ratio, config.minGoodSizeRatio, config.idealSizeRatio)
    }

    private fun exposureScore(detection: TimestampedDetection): Float {
        val brightness = detection.imageQuality.meanBrightness
        return when {
            brightness < config.minGoodBrightness -> linearScore(brightness, config.darkCutoff, config.minGoodBrightness)
            brightness > config.maxGoodBrightness -> linearScore(brightness, config.brightCutoff, config.maxGoodBrightness)
            else -> 1f
        }
    }

    /** Linear interpolation from 0 at [badAt] to 1 at [goodAt] (works for both increasing and decreasing ramps), clamped. */
    private fun linearScore(value: Float, badAt: Float, goodAt: Float): Float {
        if (badAt == goodAt) return if (value >= goodAt) 1f else 0f
        val t = (value - badAt) / (goodAt - badAt)
        return t.coerceIn(0f, 1f)
    }

    companion object {
        private const val NEUTRAL_SCORE_WHEN_UNKNOWN = 0.5f
    }
}
