package com.rohit.videoprocessor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FRAME's type scale - a small, consistent set of named styles (rather than
 * Material's full 15-slot scale) so every screen picks from the same few
 * sizes: [largeTitle] (30-32sp bold) for hero numbers/app name, [screenTitle]
 * (26-28sp semibold) for per-screen headers, [body] (15-17sp) for primary
 * copy, [secondary] (13-14sp) for supporting text, plus [button]/[caption]
 * for the design-system components. Default sans-serif - no custom font
 * asset, keeping this dependency-free.
 */
object FrameTypography {
    private val family = FontFamily.SansSerif

    val largeTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    )
    val screenTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    )
    val body = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )
    val bodyEmphasis = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )
    val secondary = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val button = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp,
    )
    val caption = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    )
}

/** Material3 [Typography] mapped onto [FrameTypography] so default component text (snackbars, etc.) stays visually consistent. */
val FrameMaterialTypography = Typography(
    displayLarge = FrameTypography.largeTitle,
    headlineSmall = FrameTypography.screenTitle,
    titleMedium = FrameTypography.bodyEmphasis,
    bodyLarge = FrameTypography.body,
    bodyMedium = FrameTypography.body,
    bodySmall = FrameTypography.secondary,
    labelLarge = FrameTypography.button,
    labelSmall = FrameTypography.caption,
)
