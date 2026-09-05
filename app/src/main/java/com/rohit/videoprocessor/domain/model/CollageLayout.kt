package com.rohit.videoprocessor.domain.model

/**
 * One person's card placement within the collage, computed by
 * [com.rohit.videoprocessor.domain.pipeline.CollageLayoutCalculator].
 */
data class TileLayout(
    val personId: Int,
    val appearanceCount: Int,
    val cardRect: FloatRect,
    val imageRect: FloatRect,
    val labelCenterY: Float,
)

/**
 * Full collage geometry for one video - a single portrait column of
 * [tiles], sized to fit however many people were actually found.
 */
data class CollageLayout(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val titleAreaHeight: Float,
    val tiles: List<TileLayout>,
    val footerTop: Float,
    val footerHeight: Float,
)
