package com.iraguzov.pomodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iraguzov.pomodo.TimerState
import com.iraguzov.pomodo.data.AppSettings
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(
    state: TimerState,
    settings: AppSettings,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val background = Color(settings.backgroundColor)
    val progressColor = Color(settings.progressColor)
    val digitColor = Color(settings.digitColor)

    var controlsVisible by remember { mutableStateOf(false) }

    // Панель управления сама прячется, чтобы экран оставался чистым.
    LaunchedEffect(controlsVisible, state.running) {
        if (controlsVisible && state.running) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) controlsVisible = true
    }

    BackHandler { onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        if (settings.showProgress) {
            Canvas(Modifier.fillMaxSize()) {
                val width = size.width * state.progress
                if (width > 0f) {
                    drawRect(color = progressColor, size = Size(width, size.height))
                }
            }
        }

        if (settings.showDigits) {
            val parts = timeParts(state.remainingMs, settings.showSeconds)
            TimeDisplay(
                lines = linesFor(parts, settings.digitLayout),
                style = settings.digitStyle,
                color = digitColor,
                background = background,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !state.running,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                text = if (state.finished) "Готово" else state.mode.label,
                color = digitColor.copy(alpha = 0.75f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 20.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !state.running,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(Icons.Rounded.Close, "Выйти", digitColor, onExit)
                ControlButton(
                    if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.running) "Пауза" else "Продолжить",
                    digitColor,
                ) { if (state.finished) onRestart() else onToggle() }
                ControlButton(Icons.Rounded.Refresh, "Заново", digitColor, onRestart)
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 14.dp)) {
        Icon(icon, contentDescription = description, tint = tint.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
    }
}
