package com.iraguzov.pomodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iraguzov.pomodo.data.AppSettings
import com.iraguzov.pomodo.data.DARK_BACKGROUND
import com.iraguzov.pomodo.data.DigitLayout
import com.iraguzov.pomodo.data.DigitStyle
import com.iraguzov.pomodo.data.LIGHT_BACKGROUND
import com.iraguzov.pomodo.data.Mode
import kotlin.math.abs

private enum class ColorTarget(val title: String) {
    PROGRESS("Цвет прогресса"),
    BACKGROUND("Цвет фона"),
    DIGITS("Цвет цифр"),
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetDuration: (Mode, Int) -> Unit,
    onShowDigits: (Boolean) -> Unit,
    onShowSeconds: (Boolean) -> Unit,
    onShowProgress: (Boolean) -> Unit,
    onDigitStyle: (DigitStyle) -> Unit,
    onDigitLayout: (DigitLayout) -> Unit,
    onProgressColor: (Long) -> Unit,
    onBackgroundColor: (Long) -> Unit,
    onDigitColor: (Long) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
) {
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var durationTarget by remember { mutableStateOf<Mode?>(null) }

    BackHandler { onBack() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { SectionTitle("Длительность") }
                item {
                    RowItem("Работа", formatDuration(settings.workSeconds)) { durationTarget = Mode.WORK }
                }
                item {
                    RowItem("Отдых", formatDuration(settings.breakSeconds)) { durationTarget = Mode.BREAK }
                }

                item { SectionTitle("Отображение") }
                item {
                    SwitchItem("Показывать цифры", "Полностью убрать время с экрана", settings.showDigits, onShowDigits)
                }
                item {
                    SwitchItem("Показывать секунды", "Иначе — только часы и минуты", settings.showSeconds, onShowSeconds, enabled = settings.showDigits)
                }
                item {
                    SwitchItem("Фоновый прогресс", "Заливка экрана слева направо", settings.showProgress, onShowProgress)
                }

                item { SectionTitle("Стиль цифр") }
                item {
                    val styles = DigitStyle.entries
                    // Лента открывается на текущем стиле, а не с начала.
                    val rowState = rememberLazyListState(
                        initialFirstVisibleItemIndex = styles.indexOf(settings.digitStyle).coerceAtLeast(0),
                    )
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(styles.size) { index ->
                            val style = styles[index]
                            StylePreviewChip(
                                style = style,
                                selected = style == settings.digitStyle,
                                digitColor = Color(settings.digitColor),
                                background = Color(settings.backgroundColor),
                                accent = Color(settings.progressColor),
                            ) { onDigitStyle(style) }
                        }
                    }
                }

                item { SectionTitle("Расположение") }
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DigitLayout.entries.forEach { layout ->
                            SelectableChip(layout.label, layout == settings.digitLayout, Color(settings.progressColor)) {
                                onDigitLayout(layout)
                            }
                        }
                    }
                }

                item { SectionTitle("Цвета") }
                item {
                    ColorItem("Цвет прогресса", settings.progressColor) { colorTarget = ColorTarget.PROGRESS }
                }
                item {
                    ColorItem("Цвет фона", settings.backgroundColor) { colorTarget = ColorTarget.BACKGROUND }
                }
                item {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SelectableChip("Тёмный", settings.backgroundColor == DARK_BACKGROUND, Color(settings.progressColor)) {
                            onBackgroundColor(DARK_BACKGROUND)
                            onDigitColor(readableDigitColor(DARK_BACKGROUND, settings.digitColor))
                        }
                        SelectableChip("Светлый", settings.backgroundColor == LIGHT_BACKGROUND, Color(settings.progressColor)) {
                            onBackgroundColor(LIGHT_BACKGROUND)
                            onDigitColor(readableDigitColor(LIGHT_BACKGROUND, settings.digitColor))
                        }
                    }
                }
                item {
                    ColorItem("Цвет цифр", settings.digitColor) { colorTarget = ColorTarget.DIGITS }
                }

                item { SectionTitle("Прочее") }
                item {
                    SwitchItem("Не гасить экран", "Пока таймер идёт", settings.keepScreenOn, onKeepScreenOn)
                }
                item {
                    SwitchItem("Вибрация в конце", null, settings.vibrateOnFinish, onVibrate)
                }
            }
        }
    }

    colorTarget?.let { target ->
        val initial = when (target) {
            ColorTarget.PROGRESS -> settings.progressColor
            ColorTarget.BACKGROUND -> settings.backgroundColor
            ColorTarget.DIGITS -> settings.digitColor
        }
        ColorDialog(
            title = target.title,
            initial = initial,
            onDismiss = { colorTarget = null },
            onConfirm = { value ->
                when (target) {
                    ColorTarget.PROGRESS -> onProgressColor(value)
                    ColorTarget.BACKGROUND -> onBackgroundColor(value)
                    ColorTarget.DIGITS -> onDigitColor(value)
                }
                colorTarget = null
            },
        )
    }

    durationTarget?.let { mode ->
        DurationDialog(
            title = "Таймер · ${mode.label}",
            initialSeconds = settings.secondsFor(mode),
            onDismiss = { durationTarget = null },
            onConfirm = {
                onSetDuration(mode, it)
                durationTarget = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            .alpha(0.5f),
    )
}

@Composable
private fun RowItem(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, modifier = Modifier.alpha(0.65f))
    }
}

@Composable
private fun ColorItem(title: String, argb: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        ColorDot(argb)
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, modifier = Modifier.alpha(0.55f))
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .border(1.dp, if (selected) accent else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun StylePreviewChip(
    style: DigitStyle,
    selected: Boolean,
    digitColor: Color,
    background: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 92.dp, height = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            TimeDisplay(
                lines = listOf("12"),
                style = style,
                color = digitColor,
                background = background,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            style.label,
            fontSize = 12.sp,
            modifier = Modifier
                .width(92.dp)
                .alpha(if (selected) 1f else 0.6f),
        )
    }
}

/**
 * После смены фона цифры могут слиться с ним — тогда возвращаем контрастный цвет,
 * а осознанно выбранный пользователем оставляем как есть.
 */
private fun readableDigitColor(background: Long, digits: Long): Long {
    val backgroundLuminance = Color(background.toInt()).luminance()
    val digitsLuminance = Color(digits.toInt()).luminance()
    if (abs(backgroundLuminance - digitsLuminance) >= 0.35f) return digits
    return if (backgroundLuminance < 0.5f) 0xFFFFFFFFL else 0xFF101014L
}
