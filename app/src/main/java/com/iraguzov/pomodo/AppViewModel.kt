package com.iraguzov.pomodo

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iraguzov.pomodo.data.AppSettings
import com.iraguzov.pomodo.data.DigitLayout
import com.iraguzov.pomodo.data.DigitStyle
import com.iraguzov.pomodo.data.Mode
import com.iraguzov.pomodo.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

data class TimerState(
    val mode: Mode = Mode.WORK,
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
    val running: Boolean = false,
    val finished: Boolean = false,
) {
    /** Таймер запущен или стоит на паузе — экран таймера показан. */
    val active: Boolean get() = totalMs > 0L

    val progress: Float
        get() = if (totalMs <= 0L) 0f else ((totalMs - remainingMs).toFloat() / totalMs).coerceIn(0f, 1f)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    // Первое значение читаем синхронно, иначе на холодном старте на кадр мигают цвета по умолчанию.
    private val initialSettings: AppSettings = runBlocking {
        withTimeoutOrNull(1000) { repo.settings.first() } ?: AppSettings()
    }

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)

    var timer by mutableStateOf(TimerState())
        private set

    private var tickJob: Job? = null

    fun start(mode: Mode, totalSeconds: Int) {
        tickJob?.cancel()
        val total = totalSeconds.coerceAtLeast(1) * 1000L
        timer = TimerState(mode = mode, totalMs = total, remainingMs = total, running = true)
        runLoop()
    }

    fun pause() {
        tickJob?.cancel()
        tickJob = null
        timer = timer.copy(running = false)
    }

    fun resume() {
        if (timer.remainingMs <= 0L) return
        timer = timer.copy(running = true, finished = false)
        runLoop()
    }

    fun toggle() { if (timer.running) pause() else resume() }

    fun restart() {
        tickJob?.cancel()
        timer = timer.copy(remainingMs = timer.totalMs, running = true, finished = false)
        runLoop()
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        timer = TimerState()
    }

    private fun runLoop() {
        val deadline = SystemClock.elapsedRealtime() + timer.remainingMs
        tickJob = viewModelScope.launch {
            while (isActive) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    timer = timer.copy(remainingMs = 0L, running = false, finished = true)
                    if (settings.value.vibrateOnFinish) vibrate()
                    break
                }
                timer = timer.copy(remainingMs = left)
                delay(40)
            }
        }
    }

    private fun vibrate() {
        val ctx = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 250, 150, 250, 150, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    // --- проброс настроек ---

    fun setDuration(mode: Mode, seconds: Int) { viewModelScope.launch { repo.setDuration(mode, seconds) } }
    fun setShowDigits(v: Boolean) { viewModelScope.launch { repo.setShowDigits(v) } }
    fun setShowSeconds(v: Boolean) { viewModelScope.launch { repo.setShowSeconds(v) } }
    fun setShowProgress(v: Boolean) { viewModelScope.launch { repo.setShowProgress(v) } }
    fun setProgressColor(v: Long) { viewModelScope.launch { repo.setProgressColor(v) } }
    fun setBackgroundColor(v: Long) { viewModelScope.launch { repo.setBackgroundColor(v) } }
    fun setDigitColor(v: Long) { viewModelScope.launch { repo.setDigitColor(v) } }
    fun setDigitStyle(v: DigitStyle) { viewModelScope.launch { repo.setDigitStyle(v) } }
    fun setDigitLayout(v: DigitLayout) { viewModelScope.launch { repo.setDigitLayout(v) } }
    fun setKeepScreenOn(v: Boolean) { viewModelScope.launch { repo.setKeepScreenOn(v) } }
    fun setVibrate(v: Boolean) { viewModelScope.launch { repo.setVibrate(v) } }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}
