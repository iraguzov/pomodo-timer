package com.iraguzov.pomodo.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iraguzov.pomodo.data.AppSettings
import com.iraguzov.pomodo.data.Bucket
import com.iraguzov.pomodo.data.Session
import com.iraguzov.pomodo.data.StatsPeriod
import com.iraguzov.pomodo.data.Totals
import com.iraguzov.pomodo.data.axisStepMs
import com.iraguzov.pomodo.data.bucketsFor
import com.iraguzov.pomodo.data.canShiftForward
import com.iraguzov.pomodo.data.formatAxis
import com.iraguzov.pomodo.data.formatSpan
import com.iraguzov.pomodo.data.periodTitle
import com.iraguzov.pomodo.data.shift
import com.iraguzov.pomodo.data.totalsFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

private class ChartColors(val work: Color, val overtime: Color, val rest: Color)

@Composable
fun StatsScreen(
    sessions: List<Session>,
    settings: AppSettings,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember(period, date) { mutableStateOf<Int?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val workColor = Color(settings.progressColor)
    val colors = ChartColors(
        work = workColor,
        overtime = remember(settings.progressColor) { oppositeOf(workColor) },
        rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
    )

    val totals = remember(sessions, period, date) { totalsFor(sessions, period, date) }
    val buckets = remember(sessions, period, date) { bucketsFor(sessions, period, date) }

    BackHandler { onBack() }

    val controls = @Composable {
        PeriodTabs(period, workColor) { period = it }
        PeriodNavigator(
            title = periodTitle(period, date),
            canGoForward = canShiftForward(period, date),
            onPrevious = { date = shift(period, date, -1) },
            onNext = { date = minOf(shift(period, date, 1), LocalDate.now()) },
            onPickDate = { pickingDate = true },
        )
    }
    val chart = @Composable { modifier: Modifier ->
        if (totals.isEmpty) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("За этот период записей нет", fontSize = 14.sp, modifier = Modifier.alpha(0.5f))
            }
        } else {
            BucketChart(buckets, colors, selected, { selected = it }, modifier)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            // В горизонтальном положении сводка уходит влево, а график получает всю высоту экрана.
            val landscape = maxWidth > maxHeight
            val header = @Composable { Header(onBack, compact = landscape) }
            if (landscape) {
                val paneWidth = (maxWidth * 0.4f).coerceIn(260.dp, 340.dp)
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(paneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        header()
                        controls()
                        TotalsBlock(totals, colors, compact = true)
                        Footer { confirmClear = true }
                    }
                    chart(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    header()
                    controls()
                    TotalsBlock(totals, colors, compact = false)
                    chart(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Footer { confirmClear = true }
                }
            }

            if (pickingDate) {
                PeriodDatePicker(
                    initial = date,
                    inputMode = landscape,
                    onPick = {
                        date = it
                        pickingDate = false
                    },
                    onDismiss = { pickingDate = false },
                )
            }
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все записи о работе, переработке и отдыхе будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }
        Text("Статистика", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PeriodTabs(period: StatsPeriod, accent: Color, onSelect: (StatsPeriod) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(3.dp),
    ) {
        StatsPeriod.entries.forEach { item ->
            val isSelected = item == period
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .border(1.dp, if (isSelected) accent else Color.Transparent, shape)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    title: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Предыдущий период")
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickDate)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(0.6f),
            )
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Следующий период")
        }
    }
}

@Composable
private fun TotalsBlock(totals: Totals, colors: ChartColors, compact: Boolean) {
    val vertical = if (compact) 6.dp else 10.dp
    TotalRow("Работа", totals.workedMs, colors.work, vertical)
    TotalRow("Переработка", totals.overtimeMs, colors.overtime, vertical)
    TotalRow("Отдых", totals.restMs, colors.rest, vertical)
}

@Composable
private fun TotalRow(title: String, ms: Long, color: Color, vertical: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(12.dp))
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(formatSpan(ms), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Footer(onClear: () -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            "История хранится только на устройстве и никуда не отправляется.",
            fontSize = 12.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .alpha(0.5f),
        )
        TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Очистить историю")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDatePicker(
    initial: LocalDate,
    inputMode: Boolean,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Календарь в DatePicker работает в UTC-полночах, поэтому переводим даты без часового пояса.
    fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = (today.year - 10)..today.year,
        // На низком горизонтальном экране сетка календаря не помещается — там ввод с клавиатуры.
        initialDisplayMode = if (inputMode) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toUtcMillis()
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("Выбрать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    ) {
        // Месяцы и дни недели в календаре — по-русски, как и весь интерфейс, независимо от языка системы.
        val configuration = LocalConfiguration.current
        val russian = remember(configuration) {
            Configuration(configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        }
        CompositionLocalProvider(LocalConfiguration provides russian) {
            DatePicker(
                state,
                title = {
                    Text("Выберите дату", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
                },
            )
        }
    }
}

/**
 * Столбики: работа и переработка идут одной стопкой, отдых — соседним столбиком.
 * Слева шкала времени; тап или проведение пальцем по графику выбирает столбик
 * и показывает подсказку с его значениями.
 */
@Composable
private fun BucketChart(
    buckets: List<Bucket>,
    colors: ChartColors,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier,
) {
    val peak = buckets.maxOfOrNull { it.totals.maxMs } ?: 0L
    if (peak <= 0L) return

    val step = axisStepMs(peak)
    val ticks = ((peak + step - 1) / step).toInt().coerceAtLeast(1)
    val top = step * ticks

    val ink = MaterialTheme.colorScheme.onSurface
    val labelColor = ink.copy(alpha = 0.55f)
    val gridColor = ink.copy(alpha = 0.1f)
    val highlightColor = ink.copy(alpha = 0.06f)

    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp)
    val axisLabels = remember(step, ticks, measurer) {
        (0..ticks).map { measurer.measure(formatAxis(step * it), axisStyle) }
    }
    val xLabels = remember(buckets, measurer) {
        buckets.map { if (it.emphasised) measurer.measure(it.label, TextStyle(fontSize = 9.sp)) else null }
    }

    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val labelGap = with(density) { 6.dp.toPx() }
        val width = constraints.maxWidth.toFloat()
        val plotLeft = axisLabels.maxOf { it.size.width } + labelGap
        val plotTop = axisLabels.first().size.height / 2f
        val plotBottom = constraints.maxHeight - with(density) { 18.dp.toPx() }
        val plotHeight = plotBottom - plotTop
        val slot = (width - plotLeft) / buckets.size

        fun indexAt(x: Float): Int = ((x - plotLeft) / slot).toInt().coerceIn(0, buckets.lastIndex)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(plotLeft, slot, buckets.size) {
                    detectTapGestures { offset ->
                        val index = indexAt(offset.x)
                        currentOnSelect(if (index == currentSelected) null else index)
                    }
                }
                .pointerInput(plotLeft, slot, buckets.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { currentOnSelect(indexAt(it.x)) },
                    ) { change, _ ->
                        change.consume()
                        currentOnSelect(indexAt(change.position.x))
                    }
                },
        ) {
            val hairline = 1.dp.toPx()
            axisLabels.forEachIndexed { tick, label ->
                val y = plotBottom - plotHeight * tick / ticks
                drawLine(gridColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = hairline)
                drawText(
                    label,
                    color = labelColor,
                    topLeft = Offset(plotLeft - labelGap - label.size.width, y - label.size.height / 2f),
                )
            }

            selected?.let { index ->
                drawRect(highlightColor, Offset(plotLeft + slot * index, plotTop), Size(slot, plotHeight))
            }

            val barWidth = slot * 0.34f
            val gap = slot * 0.08f
            buckets.forEachIndexed { index, bucket ->
                val alpha = if (selected == null || selected == index) 1f else 0.4f
                val left = plotLeft + slot * index + (slot - barWidth * 2 - gap) / 2f
                fun bar(x: Float, valueMs: Long, bottom: Float, color: Color): Float {
                    if (valueMs <= 0L) return bottom
                    val h = plotHeight * (valueMs.toFloat() / top)
                    drawRect(color, Offset(x, bottom - h), Size(barWidth, h), alpha = alpha)
                    return bottom - h
                }

                val afterWork = bar(left, bucket.totals.workedMs, plotBottom, colors.work)
                bar(left, bucket.totals.overtimeMs, afterWork, colors.overtime)
                bar(left + barWidth + gap, bucket.totals.restMs, plotBottom, colors.rest)

                xLabels[index]?.let { label ->
                    drawText(
                        label,
                        color = labelColor,
                        topLeft = Offset(
                            plotLeft + slot * index + (slot - label.size.width) / 2f,
                            plotBottom + 4.dp.toPx(),
                        ),
                    )
                }
            }
        }

        val index = selected
        if (index != null && index in buckets.indices) {
            val slotLeft = plotLeft + slot * index
            // Подсказка встаёт сбоку от выбранного столбика, чтобы не закрывать его.
            val toRight = slotLeft + slot / 2f < (plotLeft + width) / 2f
            val offset = with(density) { 8.dp.toPx() }
            ChartTooltip(
                bucket = buckets[index],
                colors = colors,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        val x = if (toRight) slotLeft + slot + offset else slotLeft - offset - placeable.width
                        val maxX = (width - placeable.width).coerceAtLeast(0f)
                        placeable.place(x.coerceIn(0f, maxX).roundToInt(), plotTop.roundToInt())
                    }
                },
            )
        }
    }
}

@Composable
private fun ChartTooltip(bucket: Bucket, colors: ChartColors, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val rows = listOf(
        Triple("Работа", bucket.totals.workedMs, colors.work),
        Triple("Переработка", bucket.totals.overtimeMs, colors.overtime),
        Triple("Отдых", bucket.totals.restMs, colors.rest),
    ).filter { it.second > 0L }

    Column(
        modifier
            .width(IntrinsicSize.Max)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(bucket.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) {
            Text("Нет записей", fontSize = 12.sp, modifier = Modifier.alpha(0.6f))
        }
        rows.forEach { (name, ms, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(name, fontSize = 12.sp, modifier = Modifier.weight(1f).alpha(0.7f))
                Spacer(Modifier.width(12.dp))
                Text(formatSpan(ms), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
            }
        }
    }
}
