package com.rohit.videoprocessor.data.collage

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.rohit.videoprocessor.domain.model.CollageConfig
import com.rohit.videoprocessor.domain.model.CollageResult
import com.rohit.videoprocessor.domain.model.FloatRect
import com.rohit.videoprocessor.domain.model.PersonIdentity
import com.rohit.videoprocessor.domain.model.TileLayout
import com.rohit.videoprocessor.domain.pipeline.CollageLayoutCalculator
import kotlin.math.max

/**
 * Renders the final shareable collage - one high-resolution [Bitmap] with
 * exactly one tile per unique person (never one per appearance), an
 * Instagram Story-inspired portrait design: cream subtle-gradient background,
 * rounded warm-cream cards, gold/green accents (matching the app's FRAME
 * theme - see [com.rohit.videoprocessor.ui.theme.FrameColorTokens]), one
 * generously-cropped photo and an appearance count per person, and a
 * summary footer.
 *
 * Pure layout (geometry, ordering, adapting to N people) lives in
 * [CollageLayoutCalculator] and is unit-tested there; this class only turns
 * that geometry plus already-cropped [Bitmap]s (see [RepresentativeImageProvider])
 * into pixels, which requires a real Android Canvas and so is verified via
 * an instrumented/manual check instead of a JVM unit test.
 *
 * [images] ownership transfers to this call: each input bitmap is recycled
 * immediately after being drawn into the output canvas, so the caller never
 * ends up holding both the per-person source photos and the final collage
 * in memory at once.
 */
class CollageGenerator(private val config: CollageConfig = CollageConfig()) {

    fun generate(identities: List<PersonIdentity>, images: Map<Int, Bitmap>): CollageResult {
        val layout = CollageLayoutCalculator.computeLayout(identities, config)
        val output = Bitmap.createBitmap(layout.canvasWidth, layout.canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawBackground(canvas, layout.canvasWidth, layout.canvasHeight)
        drawTitle(canvas, layout.canvasWidth, layout.titleAreaHeight)

        for (tile in layout.tiles) {
            val image = images[tile.personId]
            drawTile(canvas, tile, image)
            image?.recycle()
        }

        drawFooter(canvas, layout.canvasWidth, layout.footerTop, layout.footerHeight, identities)

        return CollageResult(
            bitmap = output,
            totalPeople = identities.size,
            totalAppearances = identities.sumOf { it.appearances.size },
        )
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor(COLOR_BACKGROUND_TOP),
                Color.parseColor(COLOR_BACKGROUND_BOTTOM),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawTitle(canvas: Canvas, canvasWidth: Int, titleAreaHeight: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_TEXT_PRIMARY)
            textSize = 58f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val centerY = titleAreaHeight / 2f
        canvas.drawText(TITLE_TEXT, canvasWidth / 2f, verticalCenterBaseline(paint, centerY), paint)
    }

    private fun drawTile(canvas: Canvas, tile: TileLayout, image: Bitmap?) {
        drawRoundedFill(canvas, tile.cardRect, config.cardCornerRadius, Color.parseColor(COLOR_CARD_FILL))

        if (image != null) {
            drawImageCenterCropped(canvas, image, tile.imageRect, config.imageCornerRadius)
        } else {
            drawPlaceholder(canvas, tile)
        }
        drawPersonBadge(canvas, tile)
        drawAppearanceLabel(canvas, tile)
    }

    private fun drawImageCenterCropped(canvas: Canvas, bitmap: Bitmap, dest: FloatRect, cornerRadius: Float) {
        val destRectF = dest.toRectF()
        val scale = max(destRectF.width() / bitmap.width, destRectF.height() / bitmap.height)
        val dx = destRectF.left + (destRectF.width() - bitmap.width * scale) / 2f
        val dy = destRectF.top + (destRectF.height() - bitmap.height * scale) / 2f

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                },
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRoundRect(destRectF, cornerRadius, cornerRadius, paint)
    }

    private fun drawPlaceholder(canvas: Canvas, tile: TileLayout) {
        drawRoundedFill(canvas, tile.imageRect, config.imageCornerRadius, Color.parseColor(COLOR_PLACEHOLDER))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0x2B, 0x24, 0x18)
            textSize = 72f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerX = (tile.imageRect.left + tile.imageRect.right) / 2f
        val centerY = (tile.imageRect.top + tile.imageRect.bottom) / 2f
        canvas.drawText("#${tile.personId}", centerX, verticalCenterBaseline(paint, centerY), paint)
    }

    private fun drawPersonBadge(canvas: Canvas, tile: TileLayout) {
        val badgeMargin = 20f
        val badgeRadius = 30f
        val centerX = tile.imageRect.left + badgeMargin + badgeRadius
        val centerY = tile.imageRect.top + badgeMargin + badgeRadius

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 0xC1, 0x97, 0x2B) }
        canvas.drawCircle(centerX, centerY, badgeRadius, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_TEXT_PRIMARY)
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val label = tile.personId.toString().padStart(2, '0')
        canvas.drawText(label, centerX, verticalCenterBaseline(textPaint, centerY), textPaint)
    }

    private fun drawAppearanceLabel(canvas: Canvas, tile: TileLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_ACCENT_GREEN)
            textSize = 40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerX = (tile.imageRect.left + tile.imageRect.right) / 2f
        val noun = if (tile.appearanceCount == 1) "appearance" else "appearances"
        canvas.drawText(
            "${tile.appearanceCount} $noun",
            centerX,
            verticalCenterBaseline(paint, tile.labelCenterY),
            paint,
        )
    }

    private fun drawFooter(canvas: Canvas, canvasWidth: Int, footerTop: Float, footerHeight: Float, identities: List<PersonIdentity>) {
        val centerX = canvasWidth / 2f
        val totalPeople = identities.size
        val totalAppearances = identities.sumOf { it.appearances.size }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0x9C, 0x8F, 0x6E)
            strokeWidth = 2f
        }
        canvas.drawLine(config.horizontalMargin, footerTop, canvasWidth - config.horizontalMargin, footerTop, dividerPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_MUTED)
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        val labelY = footerTop + footerHeight * 0.40f
        canvas.drawText("TOTAL", centerX, verticalCenterBaseline(labelPaint, labelY), labelPaint)

        val summaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(COLOR_ACCENT_GOLD)
            textSize = 46f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val peopleNoun = if (totalPeople == 1) "PERSON" else "PEOPLE"
        val appearancesNoun = if (totalAppearances == 1) "APPEARANCE" else "APPEARANCES"
        val summaryY = footerTop + footerHeight * 0.75f
        canvas.drawText(
            "$totalPeople $peopleNoun • $totalAppearances $appearancesNoun",
            centerX,
            verticalCenterBaseline(summaryPaint, summaryY),
            summaryPaint,
        )
    }

    private fun drawRoundedFill(canvas: Canvas, rect: FloatRect, cornerRadius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(rect.toRectF(), cornerRadius, cornerRadius, paint)
    }

    private fun FloatRect.toRectF(): RectF = RectF(left, top, right, bottom)

    /** Baseline y for text vertically centered at [centerY], given [paint]'s font metrics. */
    private fun verticalCenterBaseline(paint: Paint, centerY: Float): Float {
        val metrics = paint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    companion object {
        private const val TITLE_TEXT = "VIDEO MOMENTS"
        // Matches com.rohit.videoprocessor.ui.theme.FrameColorTokens - kept as separate literal
        // constants (not a shared reference) since this class has no Compose dependency and
        // should stay usable from plain Android graphics code alone.
        private const val COLOR_BACKGROUND_TOP = "#FAF3E3"
        private const val COLOR_BACKGROUND_BOTTOM = "#EFE0B8"
        private const val COLOR_CARD_FILL = "#F2E9D3"
        private const val COLOR_TEXT_PRIMARY = "#2B2418"
        private const val COLOR_ACCENT_GOLD = "#C1972B"
        private const val COLOR_ACCENT_GREEN = "#2E6E4E"
        private const val COLOR_PLACEHOLDER = "#E9DCB8"
        private const val COLOR_MUTED = "#9C8F6E"
    }
}
