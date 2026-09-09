package com.iraguzov.pomodo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.iraguzov.pomodo.data.DigitLayout
import com.iraguzov.pomodo.data.DigitStyle
import kotlin.math.ceil

/**
 * Разбивает остаток времени на части.
 * Со секундами: [ЧЧ, ММ, СС] либо [ММ, СС]; без секунд — всегда [ЧЧ, ММ] («по классике»).
 */
fun timeParts(remainingMs: Long, showSeconds: Boolean): List<String> {
    val totalSec = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (showSeconds) {
        if (h > 0) listOf(pad(h), pad(m), pad(s)) else listOf(pad(m), pad(s))
    } else {
        val totalMin = ceil(totalSec / 60.0).toInt()
        listOf(pad(totalMin / 60), pad(totalMin % 60))
    }
}

private fun pad(v: Int) = v.toString().padStart(2, '0')

fun linesFor(parts: List<String>, layout: DigitLayout): List<String> =
    if (layout == DigitLayout.HORIZONTAL) listOf(parts.joinToString(":")) else parts

private fun digitWidthFactor(style: DigitStyle) = when (style) {
    DigitStyle.FLIP -> 0.88f
    DigitStyle.MONO -> 0.64f
    DigitStyle.OUTLINE -> 0.64f
    else -> 0.58f
}

private fun lineHeightFactor(style: DigitStyle) = if (style == DigitStyle.FLIP) 1.15f else 1.06f

private const val COLON_FACTOR = 0.34f

@Composable
private fun baseStyle(style: DigitStyle, fontSize: TextUnit, color: Color): TextStyle {
    val strokeWidth = with(LocalDensity.current) { fontSize.toPx() * 0.028f }
    val common = TextStyle(fontSize = fontSize, color = color, textAlign = TextAlign.Center)
    return when (style) {
        DigitStyle.MINIMAL -> common.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin)
        DigitStyle.NEON -> common.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
        DigitStyle.MONO -> common.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        DigitStyle.SERIF -> common.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light)
        DigitStyle.FLIP -> common.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
        DigitStyle.OUTLINE -> common.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/**
 * Табло времени, которое само подбирает кегль под доступное место.
 * Каждый символ живёт в ячейке фиксированной ширины, поэтому цифры не «дёргают» вёрстку.
 */
@Composable
fun TimeDisplay(
    lines: List<String>,
    style: DigitStyle,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val digitFactor = digitWidthFactor(style)
        val unitsW = lines.maxOf { line ->
            line.sumOf { c -> (if (c == ':') COLON_FACTOR else digitFactor).toDouble() }
        }.toFloat()
        val lineFactor = lineHeightFactor(style)
        val gapFactor = 0.14f
        val unitsH = lines.size * lineFactor + (lines.size - 1) * gapFactor

        val byWidth = maxWidth * 0.94f / unitsW
        val byHeight = maxHeight * 0.88f / unitsH
        val fontDp: Dp = minOf(byWidth, byHeight)
        val fontSize = with(LocalDensity.current) { fontDp.toSp() }

        val textStyle = baseStyle(style, fontSize, color)
        val cellHeight = fontDp * lineFactor

        Column(
            verticalArrangement = Arrangement.spacedBy(fontDp * gapFactor),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    line.forEach { c ->
                        val cellWidth = fontDp * (if (c == ':') COLON_FACTOR else digitFactor)
                        when {
                            c == ':' -> Cell(cellWidth, cellHeight) {
                                PlainGlyph(":", style, textStyle, color)
                            }

                            style == DigitStyle.FLIP -> FlipCell(
                                char = c,
                                width = cellWidth,
                                height = cellHeight,
                                textStyle = textStyle,
                                background = background,
                                color = color,
                            )

                            else -> Cell(cellWidth, cellHeight) {
                                PlainGlyph(c.toString(), style, textStyle, color)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(width: Dp, height: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PlainGlyph(text: String, style: DigitStyle, textStyle: TextStyle, color: Color) {
    val unbounded = Modifier.wrapContentSize(Alignment.Center, unbounded = true)
    if (style == DigitStyle.NEON) {
        val blur = with(LocalDensity.current) { textStyle.fontSize.toPx() * 0.16f }
        val core = lerp(color, Color.White, 0.8f)
        Text(text, modifier = unbounded, maxLines = 1, softWrap = false,
            style = textStyle.copy(color = color.copy(alpha = 0.55f), shadow = Shadow(color, Offset.Zero, blur * 2.4f)))
        Text(text, modifier = unbounded, maxLines = 1, softWrap = false,
            style = textStyle.copy(color = color.copy(alpha = 0.9f), shadow = Shadow(color, Offset.Zero, blur * 1.2f)))
        Text(text, modifier = unbounded, maxLines = 1, softWrap = false,
            style = textStyle.copy(color = core, shadow = Shadow(color, Offset.Zero, blur * 0.5f)))
    } else {
        Text(text, modifier = unbounded, maxLines = 1, softWrap = false, style = textStyle)
    }
}

/** Одна перекидная карточка «флип-часов». */
@Composable
private fun FlipCell(
    char: Char,
    width: Dp,
    height: Dp,
    textStyle: TextStyle,
    background: Color,
    color: Color,
) {
    val cardWidth = width * 0.9f
    val radius = cardWidth * 0.12f
    val cardColor = lerp(background, color, 0.13f)

    var shown by remember { mutableStateOf(char) }
    var previous by remember { mutableStateOf(char) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(char) {
        if (char != shown) {
            previous = shown
            shown = char
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 420, easing = LinearEasing))
        }
    }

    val p = progress.value

    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
        Box(Modifier.size(cardWidth, height)) {
            HalfCard(shown, true, cardWidth, height, textStyle, cardColor, radius, Modifier.align(Alignment.TopCenter))
            HalfCard(previous, false, cardWidth, height, textStyle, cardColor, radius, Modifier.align(Alignment.BottomCenter))

            if (p < 1f) {
                if (p < 0.5f) {
                    HalfCard(
                        previous, true, cardWidth, height, textStyle, cardColor, radius,
                        Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                rotationX = -180f * p
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                cameraDistance = 16f * density
                            },
                    )
                } else {
                    HalfCard(
                        shown, false, cardWidth, height, textStyle, cardColor, radius,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                rotationX = 180f * (1f - p)
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                cameraDistance = 16f * density
                            },
                    )
                }
            }

            // Линия разлома посередине карточки.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(cardWidth * 0.025f)
                    .background(background),
            )
        }
    }
}

@Composable
private fun HalfCard(
    char: Char,
    top: Boolean,
    width: Dp,
    height: Dp,
    textStyle: TextStyle,
    cardColor: Color,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = if (top) {
        RoundedCornerShape(topStart = radius, topEnd = radius)
    } else {
        RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    }
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .size(width, height / 2)
            .clip(shape)
            .background(cardColor),
    ) {
        val layout = measurer.measure(char.toString(), style = textStyle)
        // Цифра рисуется так, будто карточка целая: половина её и попадает в кадр.
        val fullHeight = size.height * 2f
        val capHeight = textStyle.fontSize.toPx() * 0.72f
        val glyphCenter = layout.firstBaseline - capHeight / 2f
        val x = (size.width - layout.size.width) / 2f
        val y = fullHeight / 2f - glyphCenter - if (top) 0f else size.height
        drawText(layout, topLeft = Offset(x, y))
    }
}
