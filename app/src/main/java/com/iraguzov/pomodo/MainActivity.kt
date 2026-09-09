package com.iraguzov.pomodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iraguzov.pomodo.ui.HomeScreen
import com.iraguzov.pomodo.ui.SettingsScreen
import com.iraguzov.pomodo.ui.TimerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { PomodoApp() }
    }
}

private enum class Screen { HOME, SETTINGS }

@Composable
private fun PomodoApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val timer = vm.timer
    val view = LocalView.current
    val darkBackground = Color(settings.backgroundColor).luminance() < 0.5f

    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(timer.running, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn && timer.running
    }

    // Полноэкранный режим только на самом таймере.
    LaunchedEffect(timer.active, darkBackground) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller: WindowInsetsControllerCompat = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
        if (timer.active) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val accent = Color(settings.progressColor)
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (darkBackground) {
        darkColorScheme(primary = accent, onPrimary = onAccent, secondary = accent, onSecondary = onAccent)
    } else {
        lightColorScheme(primary = accent, onPrimary = onAccent, secondary = accent, onSecondary = onAccent)
    }

    MaterialTheme(colorScheme = scheme) {
        when {
            timer.active -> TimerScreen(
                state = timer,
                settings = settings,
                onToggle = vm::toggle,
                onRestart = vm::restart,
                onExit = vm::stop,
            )

            screen == Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = { screen = Screen.HOME },
                onSetDuration = vm::setDuration,
                onShowDigits = vm::setShowDigits,
                onShowSeconds = vm::setShowSeconds,
                onShowProgress = vm::setShowProgress,
                onDigitStyle = vm::setDigitStyle,
                onDigitLayout = vm::setDigitLayout,
                onProgressColor = vm::setProgressColor,
                onBackgroundColor = vm::setBackgroundColor,
                onDigitColor = vm::setDigitColor,
                onKeepScreenOn = vm::setKeepScreenOn,
                onVibrate = vm::setVibrate,
            )

            else -> HomeScreen(
                settings = settings,
                onStart = vm::start,
                onSetDuration = vm::setDuration,
                onOpenSettings = { screen = Screen.SETTINGS },
            )
        }
    }
}
