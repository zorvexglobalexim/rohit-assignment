package com.rohit.videoprocessor.domain.pipeline

import com.rohit.videoprocessor.domain.model.CollageConfig
import com.rohit.videoprocessor.domain.model.CollageLayout
import com.rohit.videoprocessor.domain.model.FloatRect
import com.rohit.videoprocessor.domain.model.PersonIdentity
import com.rohit.videoprocessor.domain.model.TileLayout

/**
 * Computes where every person's card goes in the final collage - pure
 * geometry, no Android/Bitmap dependency, fully testable under a plain JVM
 * unit test (see [FloatRect]'s doc for why plain floats are used instead of
 * `android.graphics.RectF`).
 *
 * A single portrait column, one card per person, in the same order as
 * [identities] (already ordered by earliest appearance - see
 * [IdentityClusterer]) - never reordered by appearance count or anything
 * else. The canvas height adapts to the number of people: this is what
 * makes 1 and 6+ people both render with the exact same math, no special
 *-casing per count.
 */
object CollageLayoutCalculator {

    fun computeLayout(identities: List<PersonIdentity>, config: CollageConfig = CollageConfig()): CollageLayout {
        val contentWidth = config.canvasWidth - 2 * config.horizontalMargin
        val imageWidth = contentWidth - 2 * config.cardInternalPadding
        val imageHeight = imageWidth / config.imageAspectRatioWidthToHeight
        val cardHeight = config.cardInternalPadding * 2 + imageHeight + config.labelAreaHeight

        val tiles = identities.mapIndexed { index, identity ->
            val cardTop = config.titleAreaHeight + index * (cardHeight + config.cardSpacing)
            val cardBottom = cardTop + cardHeight
            val cardLeft = config.horizontalMargin
            val cardRight = cardLeft + contentWidth

            val imageLeft = cardLeft + config.cardInternalPadding
            val imageTop = cardTop + config.cardInternalPadding
            val imageRect = FloatRect(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight)

            TileLayout(
                personId = identity.id,
                appearanceCount = identity.appearances.size,
                cardRect = FloatRect(cardLeft, cardTop, cardRight, cardBottom),
                imageRect = imageRect,
                labelCenterY = imageRect.bottom + config.labelAreaHeight / 2f,
            )
        }

        val lastCardBottom = tiles.lastOrNull()?.cardRect?.bottom ?: config.titleAreaHeight
        val footerTop = lastCardBottom + config.cardSpacing
        val canvasHeight = footerTop + config.footerAreaHeight + config.bottomMargin

        return CollageLayout(
            canvasWidth = config.canvasWidth,
            canvasHeight = canvasHeight.toInt(),
            titleAreaHeight = config.titleAreaHeight,
            tiles = tiles,
            footerTop = footerTop,
            footerHeight = config.footerAreaHeight,
        )
    }
}
