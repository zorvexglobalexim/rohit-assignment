package com.rohit.videoprocessor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FRAME's color tokens - the single source of truth for every color in the
 * app. Referenced only from [FrameTheme]/[Frame]; screens and components
 * read colors via `Frame.colors.*`, never these constants directly, so
 * there is exactly one place to retune the palette.
 *
 * Palette: cream/green/gold - a warm, light background with deep green and
 * gold as the two accents (gold for primary actions/progress, green for
 * success and as the second stop in gradients).
 */
object FrameColorTokens {
    val Background = Color(0xFFFAF3E3)
    val Surface = Color(0xFFF2E9D3)
    val SurfaceSecondary = Color(0xFFE9DCB8)
    val AccentPrimary = Color(0xFFC1972B)
    val AccentSecondary = Color(0xFF2E6E4E)
    val TextPrimary = Color(0xFF2B2418)
    val TextSecondary = Color(0xFF6B5F45)
    val Muted = Color(0xFF9C8F6E)
    val Success = Color(0xFF2E6E4E)
    val Error = Color(0xFFB3261E)
    /** Text/icon color placed on top of a solid [AccentPrimary] (gold) fill, e.g. FramePrimaryButton - dark reads better on gold than white does. */
    val OnAccentPrimary = Color(0xFF2B2418)
}
