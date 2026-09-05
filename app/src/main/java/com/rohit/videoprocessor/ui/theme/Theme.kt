package com.rohit.videoprocessor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic color slots for FRAME - see [FrameColorTokens] for the raw values. */
data class FrameColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val onAccentPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val muted: Color,
    val success: Color,
    val error: Color,
)

private val FrameColors = FrameColorScheme(
    background = FrameColorTokens.Background,
    surface = FrameColorTokens.Surface,
    surfaceSecondary = FrameColorTokens.SurfaceSecondary,
    accentPrimary = FrameColorTokens.AccentPrimary,
    accentSecondary = FrameColorTokens.AccentSecondary,
    onAccentPrimary = FrameColorTokens.OnAccentPrimary,
    textPrimary = FrameColorTokens.TextPrimary,
    textSecondary = FrameColorTokens.TextSecondary,
    muted = FrameColorTokens.Muted,
    success = FrameColorTokens.Success,
    error = FrameColorTokens.Error,
)

private val LocalFrameColors = staticCompositionLocalOf { FrameColors }

/**
 * Access point for FRAME's design tokens from any composable - `Frame.colors.*` /
 * `Frame.typography.*` - so nothing in the UI layer ever hardcodes a color or text style.
 * FRAME ships a single theme (cream/green/gold) rather than separate light/dark variants.
 */
object Frame {
    val colors: FrameColorScheme
        @Composable get() = LocalFrameColors.current
    val typography get() = FrameTypography
}

@Composable
fun FrameTheme(content: @Composable () -> Unit) {
    val materialColorScheme = lightColorScheme(
        primary = FrameColorTokens.AccentPrimary,
        onPrimary = FrameColorTokens.OnAccentPrimary,
        secondary = FrameColorTokens.AccentSecondary,
        onSecondary = Color.White,
        background = FrameColorTokens.Background,
        onBackground = FrameColorTokens.TextPrimary,
        surface = FrameColorTokens.Surface,
        onSurface = FrameColorTokens.TextPrimary,
        surfaceVariant = FrameColorTokens.SurfaceSecondary,
        onSurfaceVariant = FrameColorTokens.TextSecondary,
        error = FrameColorTokens.Error,
        onError = Color.White,
        outline = FrameColorTokens.Muted,
    )

    CompositionLocalProvider(LocalFrameColors provides FrameColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = FrameMaterialTypography,
            content = content,
        )
    }
}
