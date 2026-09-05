package com.rohit.videoprocessor.domain.model

/**
 * Layout constants for [com.rohit.videoprocessor.domain.pipeline.CollageLayoutCalculator]
 * and [com.rohit.videoprocessor.data.collage.CollageGenerator]. A single
 * portrait column of cards (Instagram Story-inspired) whose height adapts to
 * however many people were found - not a fixed grid, so 1 and 6+ people both
 * render cleanly with the same math.
 */
data class CollageConfig(
    /** Fixed output width - high enough for sharing without being wasteful. */
    val canvasWidth: Int = 1080,
    val horizontalMargin: Float = 56f,
    val titleAreaHeight: Float = 190f,
    val cardSpacing: Float = 40f,
    val cardInternalPadding: Float = 28f,
    /** Width-to-height ratio of each person's photo - 0.8 = 4:5 portrait. */
    val imageAspectRatioWidthToHeight: Float = 0.8f,
    val labelAreaHeight: Float = 110f,
    val footerAreaHeight: Float = 170f,
    val bottomMargin: Float = 72f,
    val cardCornerRadius: Float = 32f,
    val imageCornerRadius: Float = 24f,
) {
    init {
        require(canvasWidth > 0) { "canvasWidth must be positive" }
        require(imageAspectRatioWidthToHeight > 0f) { "imageAspectRatioWidthToHeight must be positive" }
        require(horizontalMargin >= 0f && horizontalMargin * 2 < canvasWidth) { "horizontalMargin too large for canvasWidth" }
    }
}
