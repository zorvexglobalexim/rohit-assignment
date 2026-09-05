package com.rohit.videoprocessor.domain.pipeline

import android.graphics.Rect
import com.rohit.videoprocessor.domain.model.AppearanceSegment
import com.rohit.videoprocessor.domain.model.CollageConfig
import com.rohit.videoprocessor.domain.model.DetectedFace
import com.rohit.videoprocessor.domain.model.FaceBox
import com.rohit.videoprocessor.domain.model.PersonIdentity
import com.rohit.videoprocessor.domain.model.TimestampedDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests - no Android runtime/emulator needed. */
class CollageLayoutCalculatorTest {

    @Test
    fun zeroPeople_producesValidMinimalCanvasWithNoTiles() {
        val layout = CollageLayoutCalculator.computeLayout(emptyList())

        assertTrue(layout.tiles.isEmpty())
        assertTrue(layout.canvasHeight > 0)
        assertTrue(layout.canvasWidth > 0)
    }

    @Test
    fun onePerson_producesExactlyOneTile() {
        val layout = CollageLayoutCalculator.computeLayout(listOf(person(1, appearances = 3)))

        assertEquals(1, layout.tiles.size)
        assertEquals(1, layout.tiles.single().personId)
        assertEquals(3, layout.tiles.single().appearanceCount)
    }

    @Test
    fun worksForOneThroughSixPlusPeople() {
        for (count in 1..8) {
            val identities = (1..count).map { person(it, appearances = it) }
            val layout = CollageLayoutCalculator.computeLayout(identities)

            assertEquals("failed for count=$count", count, layout.tiles.size)
            assertEquals(identities.map { it.id }, layout.tiles.map { it.personId })
        }
    }

    @Test
    fun canvasHeightGrowsMonotonicallyWithMorePeople() {
        var previousHeight = 0
        for (count in 1..6) {
            val layout = CollageLayoutCalculator.computeLayout((1..count).map { person(it, 1) })
            assertTrue(
                "canvas height should grow as people are added (count=$count)",
                layout.canvasHeight > previousHeight,
            )
            previousHeight = layout.canvasHeight
        }
    }

    @Test
    fun tilesNeverOverlapVertically() {
        val identities = (1..6).map { person(it, appearances = it) }
        val layout = CollageLayoutCalculator.computeLayout(identities)

        for (i in 0 until layout.tiles.size - 1) {
            val current = layout.tiles[i]
            val next = layout.tiles[i + 1]
            assertTrue(
                "tile $i (bottom=${current.cardRect.bottom}) overlaps tile ${i + 1} (top=${next.cardRect.top})",
                current.cardRect.bottom <= next.cardRect.top,
            )
        }
        // Footer must not overlap the last card either.
        assertTrue(layout.tiles.last().cardRect.bottom <= layout.footerTop)
    }

    @Test
    fun allTilesShareTheSameWidthAndImageAspectRatio() {
        val config = CollageConfig()
        val identities = (1..5).map { person(it, 1) }
        val layout = CollageLayoutCalculator.computeLayout(identities, config)

        val widths = layout.tiles.map { it.cardRect.width }.distinct()
        assertEquals("all cards should share one width", 1, widths.size)

        for (tile in layout.tiles) {
            val ratio = tile.imageRect.width / tile.imageRect.height
            assertEquals(config.imageAspectRatioWidthToHeight, ratio, 1e-3f)
        }
    }

    @Test
    fun tileOrderMatchesInputOrder_notAppearanceCount() {
        // Deliberately out of appearance-count order - layout must not resort by count.
        val identities = listOf(person(1, appearances = 1), person(2, appearances = 9), person(3, appearances = 2))
        val layout = CollageLayoutCalculator.computeLayout(identities)

        assertEquals(listOf(1, 2, 3), layout.tiles.map { it.personId })
    }

    private fun person(id: Int, appearances: Int): PersonIdentity {
        val segments = (1..appearances).map { i ->
            val detection = TimestampedDetection(
                timestampMs = i * 1000L,
                frameIndex = i,
                frameWidth = 1000,
                frameHeight = 1000,
                face = DetectedFace(
                    id = "$id-$i",
                    boundingBox = Rect(),
                    trackingId = null,
                    headEulerAngleX = null,
                    headEulerAngleY = null,
                    headEulerAngleZ = null,
                    leftEyeOpenProbability = null,
                    rightEyeOpenProbability = null,
                    smilingProbability = null,
                ),
                box = FaceBox(300, 300, 500, 500),
            )
            AppearanceSegment(
                startTimestampMs = detection.timestampMs,
                endTimestampMs = detection.timestampMs + 500L,
                detections = listOf(detection),
                candidateFrames = listOf(detection),
            )
        }
        return PersonIdentity(id = id, appearances = segments)
    }
}
