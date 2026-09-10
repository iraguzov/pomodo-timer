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
import com.iraguzov.pomodo.data.HistoryStore
import com.iraguzov.pomodo.data.Session
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
    val elapsedMs: Long = 0L,
    val running: Boolean = false,
) {
    /** Таймер запущен или стоит на паузе — экран таймера показан. */
    val active: Boolean get() = totalMs > 0L

    /** Сколько осталось до нуля. */
    val remainingMs: Long get() = (totalMs - elapsedMs).coerceAtLeast(0L)

    /** Сколько натикало сверх заданного времени. */
    val overtimeMs: Long get() = (elapsedMs - totalMs).coerceAtLeast(0L)

    val finished: Boolean get() = active && elapsedMs >= totalMs

    /** Что на табло: обратный отсчёт, а после нуля — время переработки. */
    val displayMs: Long get() = if (finished) overtimeMs else remainingMs

    /**
     * Весь экран — это всё время с момента старта. Пока идёт отсчёт, это заданное время;
     * дальше масштаб растёт вместе с переработкой.
     */
    private val spanMs: Long get() = maxOf(totalMs, elapsedMs)

    /**
     * Доля экрана под заданным временем: до нуля заполняется, после — сжимается
     * пропорционально. Полностью переработка её никогда не вытеснит.
     */
    val progress: Float
        get() = if (spanMs <= 0L) 0f else (minOf(elapsedMs, totalMs).toFloat() / spanMs).coerceIn(0f, 1f)

    /** Доля экрана под переработкой — остаток справа от заданного времени. */
    val overtimeProgress: Float
        get() = if (spanMs <= 0L) 0f else (overtimeMs.toFloat() / spanMs).coerceIn(0f, 1f)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val historyStore = HistoryStore(app)

    /** История прогонов, целиком локальная. */
    var history by mutableStateOf<List<Session>>(emptyList())
        private set

    // Первое значение читаем синхронно, иначе на холодном старте на кадр мигают цвета по умолчанию.
    private val initialSettings: AppSettings = runBlocking {
        withTimeoutOrNull(1000) { repo.settings.first() } ?: AppSettings()
    }

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)

    var timer by mutableStateOf(TimerState())
        private set

    private var tickJob: Job? = null

    /** Отметка, чтобы провибрировать на нуле ровно один раз за прогон. */
    private var finishNotified = false

    /** Когда начался текущий прогон — по календарным часам, для истории. */
    private var runStartedAt = 0L

    init {
        viewModelScope.launch { history = historyStore.load() }
    }

    fun start(mode: Mode, totalSeconds: Int) {
        tickJob?.cancel()
        commitRun()
        val total = totalSeconds.coerceAtLeast(1) * 1000L
        finishNotified = false
        runStartedAt = System.currentTimeMillis()
        timer = TimerState(mode = mode, totalMs = total, elapsedMs = 0L, running = true)
        runLoop()
    }

    fun pause() {
        tickJob?.cancel()
        tickJob = null
        timer = timer.copy(running = false)
    }

    fun resume() {
        if (!timer.active) return
        timer = timer.copy(running = true)
        runLoop()
    }

    fun toggle() { if (timer.running) pause() else resume() }

    fun restart() {
        tickJob?.cancel()
        commitRun()
        finishNotified = false
        runStartedAt = System.currentTimeMillis()
        timer = timer.copy(elapsedMs = 0L, running = true)
        runLoop()
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        commitRun()
        timer = TimerState()
    }

    /**
     * Записывает завершённый прогон в историю. Совсем короткие пропускаем —
     * это случайные нажатия, а не работа.
     */
    private fun commitRun(blocking: Boolean = false) {
        val run = timer
        if (!run.active || run.elapsedMs < MIN_RECORDED_MS) return
        val session = Session(
            mode = run.mode,
            startedAt = runStartedAt,
            plannedMs = run.totalMs,
            elapsedMs = run.elapsedMs,
        )
        if (blocking) {
            history = historyStore.appendBlocking(session)
        } else {
            viewModelScope.launch { history = historyStore.append(session) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { history = historyStore.clear() }
    }

    /** После нуля отсчёт не останавливается, а продолжает копить переработку. */
    private fun runLoop() {
        val startedAt = SystemClock.elapsedRealtime() - timer.elapsedMs
        tickJob = viewModelScope.launch {
            while (isActive) {
                timer = timer.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                if (timer.finished && !finishNotified) {
                    finishNotified = true
                    if (settings.value.vibrateOnFinish) vibrate()
                }
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
        // Корутины здесь запускать поздно, поэтому дописываем историю синхронно.
        commitRun(blocking = true)
        super.onCleared()
    }

    private companion object {
        const val MIN_RECORDED_MS = 5_000L
    }
}
