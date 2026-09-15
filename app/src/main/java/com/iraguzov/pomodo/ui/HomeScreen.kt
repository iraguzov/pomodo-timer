package com.iraguzov.pomodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.iraguzov.pomodo.data.AppSettings
import com.iraguzov.pomodo.data.DigitLayout
import com.iraguzov.pomodo.data.Mode

private val QUICK_PRESETS = listOf(5, 10, 15, 25, 45, 60)

@Composable
fun HomeScreen(
    settings: AppSettings,
    onStart: (Mode, Int) -> Unit,
    onSetDuration: (Mode, Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val background = Color(settings.backgroundColor)
    val accent = Color(settings.progressColor)
    val onBackground = Color(settings.digitColor)

    var mode by remember { mutableStateOf(Mode.WORK) }
    var editing by remember { mutableStateOf(false) }
    val seconds = settings.secondsFor(mode)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val viewportHeight = maxHeight
        val landscape = maxWidth > maxHeight

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .zIndex(1f),
        ) {
            IconButton(onClick = onOpenStats) {
                Icon(Icons.Rounded.BarChart, "Статистика", tint = onBackground.copy(alpha = 0.7f))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, "Настройки", tint = onBackground.copy(alpha = 0.7f))
            }
        }

        val modePills = @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mode.entries.forEach { m ->
                    ModePill(
                        label = m.label,
                        selected = m == mode,
                        accent = accent,
                        contentColor = onBackground,
                    ) { mode = m }
                }
            }
        }

        val preview = @Composable { height: Dp ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { editing = true },
            ) {
                TimeDisplay(
                    lines = linesFor(
                        timeParts(seconds * 1000L, settings.showSeconds),
                        DigitLayout.HORIZONTAL,
                    ),
                    style = settings.digitStyle,
                    color = onBackground,
                    background = background,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "нажмите, чтобы задать часы и минуты",
                color = onBackground,
                fontSize = 12.sp,
                modifier = Modifier.alpha(0.45f),
            )
        }

        val presets = @Composable { perRow: Int ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_PRESETS.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            PresetChip(
                                text = "$minutes",
                                selected = seconds == minutes * 60,
                                accent = accent,
                                contentColor = onBackground,
                            ) { onSetDuration(mode, minutes * 60) }
                        }
                    }
                }
            }
        }

        val startButton = @Composable {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { onStart(mode, seconds) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Старт",
                    tint = contentColorOn(accent),
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        if (landscape) {
            // В горизонтальном положении одна колонка не влезает по высоте: табло уходит влево,
            // пресеты и старт — вправо, и всё помещается на экран без прокрутки.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1.4f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    modePills()
                    Spacer(Modifier.height(20.dp))
                    preview(minOf(160.dp, viewportHeight * 0.45f))
                }
                Spacer(Modifier.width(24.dp))
                Column(
                    // Сверху справа кнопки статистики и настроек — не заезжаем под них.
                    Modifier
                        .weight(1f)
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    presets(3)
                    Spacer(Modifier.height(24.dp))
                    startButton()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                modePills()
                Spacer(Modifier.height(36.dp))
                preview(minOf(150.dp, viewportHeight * 0.34f))
                Spacer(Modifier.height(28.dp))
                presets(QUICK_PRESETS.size)
                Spacer(Modifier.height(48.dp))
                startButton()
            }
        }
    }

    if (editing) {
        DurationDialog(
            title = "Таймер · ${mode.label}",
            initialSeconds = seconds,
            onDismiss = { editing = false },
            onConfirm = {
                onSetDuration(mode, it)
                editing = false
            },
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    accent: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) accent else contentColor.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) contentColor else contentColor.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PresetChip(
    text: String,
    selected: Boolean,
    accent: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.25f) else contentColor.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor.copy(alpha = if (selected) 1f else 0.65f), fontSize = 14.sp)
    }
}

/** Чёрный или белый — что читается поверх выбранного цвета. */
fun contentColorOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
