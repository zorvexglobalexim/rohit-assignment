package com.rohit.videoprocessor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rohit.videoprocessor.ui.theme.Frame
import kotlin.math.roundToInt

/** Standard corner radius for large cards throughout FRAME. */
val FrameCardRadius = 24.dp

/** Standard horizontal screen padding. */
val FrameScreenPadding = 24.dp

/** Standard spacing between stacked components on a screen. */
val FrameSpacing = 16.dp

/** Height every primary/secondary button uses. */
val FrameButtonHeight = 56.dp

/**
 * FRAME's filled call-to-action button - solid [Frame.colors.accentPrimary], full width,
 * [FrameButtonHeight] tall, subtle press-scale feedback. Used for the one primary action per
 * screen ("Choose a video", "Create collage", "Save").
 */
@Composable
fun FramePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, tween(120), label = "primaryButtonScale")

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(FrameButtonHeight)
            .scale(scale),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Frame.colors.accentPrimary,
            contentColor = Frame.colors.onAccentPrimary,
            disabledContainerColor = Frame.colors.accentPrimary.copy(alpha = 0.35f),
            disabledContentColor = Frame.colors.onAccentPrimary.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        Text(text = text, style = Frame.typography.button)
    }
}

/**
 * FRAME's lower-emphasis button - transparent fill, thin accent-tinted border. Used for
 * secondary actions ("Choose another", "Share", "Process another video").
 */
@Composable
fun FrameSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, tween(120), label = "secondaryButtonScale")

    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(FrameButtonHeight)
            .scale(scale),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Frame.colors.textSecondary.copy(alpha = 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Frame.colors.textPrimary,
            disabledContentColor = Frame.colors.muted,
        ),
    ) {
        Text(text = text, style = Frame.typography.button)
    }
}

/**
 * The large rounded video placeholder/preview used on Home (empty treatment, no [fileName])
 * and the Video Selected screen ([fileName] shown as a caption chip). No real video frame is
 * decoded here (that would mean invoking the frame extractor purely for a cosmetic thumbnail,
 * outside the actual processing pipeline) - a tasteful gradient card with a play glyph stands
 * in for the preview, consistent for both states.
 */
@Composable
fun VideoPreviewCard(
    modifier: Modifier = Modifier,
    fileName: String? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.78f),
        shape = RoundedCornerShape(FrameCardRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Frame.colors.surfaceSecondary, Frame.colors.surface),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Frame.colors.accentPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayGlyph(color = Frame.colors.accentPrimary, size = 28.dp)
            }

            if (fileName != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = fileName,
                        style = Frame.typography.secondary,
                        color = Frame.colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A small triangular "play" glyph drawn with Canvas - avoids pulling in an icon library for one shape. */
@Composable
fun PlayGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, h * 0.16f)
            lineTo(w * 0.28f, h * 0.84f)
            lineTo(w * 0.86f, h * 0.5f)
            close()
        }
        drawPath(path, color = color)
    }
}

/** A small padlock glyph (body + shackle) for the "processed on-device" privacy indicator - avoids an icon library dependency for one shape. */
@Composable
fun LockGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.11f

        // Shackle: the top arc of the lock.
        val shackleRect = androidx.compose.ui.geometry.Rect(
            left = w * 0.26f,
            top = 0f,
            right = w * 0.74f,
            bottom = h * 0.62f,
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = shackleRect.topLeft,
            size = shackleRect.size,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )

        // Body: the rounded rectangle base.
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.42f),
            size = Size(w, h * 0.58f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f, w * 0.18f),
        )
    }
}

/** A compact stat tile - label above value - used for "Duration"/"Video type" on Video Selected. */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = Frame.typography.caption, color = Frame.colors.muted)
            Text(
                text = value,
                style = Frame.typography.bodyEmphasis,
                color = Frame.colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * One row in the processing checklist. [state] drives both the leading marker glyph and the
 * text color/weight - [ProcessingStepState.Current] pulses subtly via [pulseAlpha] so the
 * active stage is unmistakable without full-blown animation.
 */
enum class ProcessingStepState { Done, Current, Pending }

@Composable
fun ProcessingStep(label: String, state: ProcessingStepState, modifier: Modifier = Modifier) {
    val pulse = rememberInfinitePulse(enabled = state == ProcessingStepState.Current)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        ProcessingStepState.Done -> Frame.colors.success.copy(alpha = 0.18f)
                        ProcessingStepState.Current -> Frame.colors.accentPrimary.copy(alpha = 0.18f * pulse + 0.1f)
                        ProcessingStepState.Pending -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ProcessingStepState.Done -> Text("✓", color = Frame.colors.success, style = Frame.typography.caption)
                ProcessingStepState.Current -> Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Frame.colors.accentPrimary),
                )
                ProcessingStepState.Pending -> Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Frame.colors.muted.copy(alpha = 0.35f)),
                )
            }
        }
        Text(
            text = label,
            style = if (state == ProcessingStepState.Current) Frame.typography.bodyEmphasis else Frame.typography.body,
            color = when (state) {
                ProcessingStepState.Done -> Frame.colors.textPrimary
                ProcessingStepState.Current -> Frame.colors.textPrimary
                ProcessingStepState.Pending -> Frame.colors.muted
            },
        )
    }
}

/**
 * The large circular progress ring with a percentage readout in the center. [progress] (0..1)
 * is always the real value from [com.rohit.videoprocessor.domain.model.ProcessingState.overallProgress] -
 * animated for smoothness, never a fabricated/independent value.
 */
@Composable
fun ProcessingProgress(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "processingProgress",
    )
    // Colors must be read here, in composable scope, not inside Canvas's DrawScope lambda
    // below (that block runs during drawing, not composition, so @Composable reads like
    // Frame.colors are not allowed there).
    val trackColor = Frame.colors.surfaceSecondary
    val gradientColors = listOf(Frame.colors.accentSecondary, Frame.colors.accentPrimary)

    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(gradientColors),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Text(
            text = "${(animatedProgress * 100).roundToInt()}%",
            style = Frame.typography.largeTitle,
            color = Frame.colors.textPrimary,
        )
    }
}

/** Rounded, elevated presentation of the generated collage - the hero visual on the Result screen. */
@Composable
fun CollagePreview(bitmap: ImageBitmap, contentDescription: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FrameCardRadius),
        colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Small pill showing an appearance count, e.g. "3 appearances". */
@Composable
fun AppearanceBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Frame.colors.accentPrimary.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (count == 1) "1 appearance" else "$count appearances",
            style = Frame.typography.caption,
            color = Frame.colors.accentPrimary,
        )
    }
}

/**
 * One person's row/card - thumbnail (or an initial-style placeholder when [image] is null),
 * name, and an [AppearanceBadge]. Used in the Result screen's people list and as the entry
 * point into the optional Person Detail screen.
 */
@Composable
fun PersonCard(
    personId: Int,
    appearanceCount: Int,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableCard(onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Frame.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Frame.colors.surfaceSecondary),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = image,
                        contentDescription = "Person $personId",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = personId.toString().padStart(2, '0'),
                        style = Frame.typography.bodyEmphasis,
                        color = Frame.colors.textSecondary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Person ${personId.toString().padStart(2, '0')}",
                    style = Frame.typography.bodyEmphasis,
                    color = Frame.colors.textPrimary,
                )
                AppearanceBadge(count = appearanceCount, modifier = Modifier.padding(top = 6.dp))
            }

            if (onClick != null) {
                Text(text = "›", style = Frame.typography.largeTitle, color = Frame.colors.muted)
            }
        }
    }
}

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** Simple 0..1 pulse (via an infinite transition) used to make the current [ProcessingStep] feel alive. */
@Composable
private fun rememberInfinitePulse(enabled: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseValue",
    )
    return if (enabled) value else 0f
}
