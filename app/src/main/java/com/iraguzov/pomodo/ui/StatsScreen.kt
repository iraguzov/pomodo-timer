package com.iraguzov.pomodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iraguzov.pomodo.data.AppSettings
import com.iraguzov.pomodo.data.Bucket
import com.iraguzov.pomodo.data.Session
import com.iraguzov.pomodo.data.StatsPeriod
import com.iraguzov.pomodo.data.Totals
import com.iraguzov.pomodo.data.bucketsFor
import com.iraguzov.pomodo.data.formatSpan
import com.iraguzov.pomodo.data.totalsFor

@Composable
fun StatsScreen(
    sessions: List<Session>,
    settings: AppSettings,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var confirmClear by remember { mutableStateOf(false) }

    val workColor = Color(settings.progressColor)
    val overtimeColor = remember(settings.progressColor) { oppositeOf(workColor) }
    val restColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    val totals = remember(sessions, period) { totalsFor(sessions, period) }
    val buckets = remember(sessions, period) { bucketsFor(sessions, period) }

    BackHandler { onBack() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Text("Статистика", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatsPeriod.entries.forEach { item ->
                    PeriodChip(item.label, item == period, workColor) { period = item }
                }
            }

            Spacer(Modifier.height(24.dp))

            TotalRow("Работа", totals.workedMs, workColor)
            TotalRow("Переработка", totals.overtimeMs, overtimeColor)
            TotalRow("Отдых", totals.restMs, restColor)

            Spacer(Modifier.height(24.dp))

            if (totals.isEmpty) {
                Text(
                    "За этот период записей нет",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                        .alpha(0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                BucketChart(buckets, workColor, overtimeColor, restColor)
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "История хранится только на устройстве и никуда не отправляется.",
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(0.5f),
            )

            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Очистить историю")
            }

            Spacer(Modifier.height(24.dp))
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
private fun PeriodChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
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
private fun TotalRow(title: String, ms: Long, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(12.dp))
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(formatSpan(ms), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Столбики: работа и переработка идут одной стопкой, отдых — соседним столбиком. */
@Composable
private fun BucketChart(
    buckets: List<Bucket>,
    workColor: Color,
    overtimeColor: Color,
    restColor: Color,
) {
    val peak = buckets.maxOfOrNull { it.totals.maxMs } ?: 0L
    if (peak <= 0L) return

    Column(Modifier.padding(horizontal = 16.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val slot = size.width / buckets.size
            val barWidth = slot * 0.34f
            val gap = slot * 0.08f
            buckets.forEachIndexed { index, bucket ->
                val left = slot * index + (slot - barWidth * 2 - gap) / 2f
                fun bar(x: Float, heightMs: Long, bottom: Float, color: Color): Float {
                    if (heightMs <= 0L) return bottom
                    val h = size.height * (heightMs.toFloat() / peak)
                    drawRect(
                        color = color,
                        topLeft = Offset(x, bottom - h),
                        size = Size(barWidth, h),
                    )
                    return bottom - h
                }

                val afterWork = bar(left, bucket.totals.workedMs, size.height, workColor)
                bar(left, bucket.totals.overtimeMs, afterWork, overtimeColor)
                bar(left + barWidth + gap, bucket.totals.restMs, size.height, restColor)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { bucket ->
                Text(
                    text = if (bucket.emphasised) bucket.label else "",
                    fontSize = 9.sp,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(0.55f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
