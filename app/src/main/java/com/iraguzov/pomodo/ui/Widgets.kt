package com.iraguzov.pomodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Колесо-барабан со снапом по элементам. */
@Composable
fun WheelPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 54.dp,
    visibleCount: Int = 3,
) {
    val count = range.last - range.first + 1
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, count - 1),
    )
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val flingBehavior = rememberSnapFlingBehavior(listState)

    val centerIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            listState.firstVisibleItemIndex + if (offset > itemPx / 2f) 1 else 0
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val target = centerIndex.coerceIn(0, count - 1)
                if (listState.firstVisibleItemScrollOffset != 0 || listState.firstVisibleItemIndex != target) {
                    listState.animateScrollToItem(target)
                }
                val newValue = range.first + target
                if (newValue != value) onValueChange(newValue)
            }
        }
    }

    Box(modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { index ->
                val distance = abs(index - centerIndex)
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (range.first + index).toString().padStart(2, '0'),
                        fontSize = if (distance == 0) 30.sp else 24.sp,
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.alpha(if (distance == 0) 1f else 0.35f),
                    )
                }
            }
        }
    }
}

/** Диалог «часы : минуты». */
@Composable
fun DurationDialog(
    title: String,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hours by remember { mutableIntStateOf(initialSeconds / 3600) }
    var minutes by remember { mutableIntStateOf((initialSeconds % 3600) / 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("часы", fontSize = 12.sp, modifier = Modifier.alpha(0.6f))
                    WheelPicker(0..23, hours, { hours = it }, Modifier.width(96.dp))
                }
                Text(":", fontSize = 28.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("минуты", fontSize = 12.sp, modifier = Modifier.alpha(0.6f))
                    WheelPicker(0..59, minutes, { minutes = it }, Modifier.width(96.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm((hours * 3600 + minutes * 60).coerceAtLeast(60)) }) {
                Text("Готово")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

val PRESET_COLORS = listOf(
    0xFFFF4D5EL, 0xFFFF7A29L, 0xFFFFC53DL, 0xFF3DDC84L, 0xFF00E5FFL,
    0xFF4D7CFEL, 0xFFA855F7L, 0xFFFF2D95L, 0xFFFFFFFFL, 0xFF8A8A8FL,
    0xFF1E1E22L, 0xFF000000L,
)

/** Палитра + HSV-регуляторы. */
@Composable
fun ColorDialog(
    title: String,
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val startHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember { mutableFloatStateOf(startHsv[1]) }
    var brightness by remember { mutableFloatStateOf(startHsv[2]) }

    val current = Color(
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PRESET_COLORS.take(6).forEach { preset -> Swatch(preset) { setHsv(it, { hue = it }, { saturation = it }, { brightness = it }) } }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PRESET_COLORS.drop(6).forEach { preset -> Swatch(preset) { setHsv(it, { hue = it }, { saturation = it }, { brightness = it }) } }
                }

                LabeledSlider("Оттенок", hue, 0f..360f) { hue = it }
                LabeledSlider("Насыщенность", saturation, 0f..1f) { saturation = it }
                LabeledSlider("Яркость", brightness, 0f..1f) { brightness = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toArgb().toLong() and 0xFFFFFFFFL) }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private inline fun setHsv(
    argb: Long,
    setHue: (Float) -> Unit,
    setSat: (Float) -> Unit,
    setVal: (Float) -> Unit,
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    setHue(hsv[0]); setSat(hsv[1]); setVal(hsv[2])
}

@Composable
private fun Swatch(argb: Long, onClick: (Long) -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick(argb) },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.alpha(0.7f))
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Небольшая круглая точка-индикатор выбранного цвета. */
@Composable
fun ColorDot(argb: Long, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape),
    )
}

/** Горизонтальная линейка-градиент, чтобы шкала оттенка читалась. */
@Composable
fun HueBar(modifier: Modifier = Modifier) {
    val colors = (0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(colors)),
    )
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h ч ${m.toString().padStart(2, '0')} мин" else "$m мин"
}

fun Float.roundToPercent(): Int = (this * 100).roundToInt()

@Composable
fun CenteredCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 13.sp)
}
