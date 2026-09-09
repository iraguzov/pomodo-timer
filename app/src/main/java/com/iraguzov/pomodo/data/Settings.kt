package com.iraguzov.pomodo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Начертание цифр на экране таймера. */
enum class DigitStyle(val label: String) {
    MINIMAL("Минимал"),
    NEON("Неон"),
    OUTLINE("Контур"),
    MONO("Моно"),
    SERIF("Серифы"),
    FLIP("Флип"),
    NIXIE("Лампы"),
    SEGMENT("Сегменты"),
}

/** Как располагать части времени друг относительно друга. */
enum class DigitLayout(val label: String) {
    HORIZONTAL("Горизонтально"),
    VERTICAL("Вертикально"),
}

enum class Mode(val label: String) {
    WORK("Работа"),
    BREAK("Отдых"),
}

const val DARK_BACKGROUND = 0xFF000000L
const val LIGHT_BACKGROUND = 0xFFF5F5F5L

data class AppSettings(
    val workSeconds: Int = 25 * 60,
    val breakSeconds: Int = 5 * 60,
    val showDigits: Boolean = true,
    val showSeconds: Boolean = true,
    val showProgress: Boolean = true,
    val progressColor: Long = 0xFFFF4D5EL,
    val backgroundColor: Long = DARK_BACKGROUND,
    val digitColor: Long = 0xFFFFFFFFL,
    val digitStyle: DigitStyle = DigitStyle.MINIMAL,
    val digitLayout: DigitLayout = DigitLayout.HORIZONTAL,
    val keepScreenOn: Boolean = true,
    val vibrateOnFinish: Boolean = true,
) {
    fun secondsFor(mode: Mode) = if (mode == Mode.WORK) workSeconds else breakSeconds
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pomodo_settings")

private object Keys {
    val WORK = intPreferencesKey("work_seconds")
    val BREAK = intPreferencesKey("break_seconds")
    val SHOW_DIGITS = booleanPreferencesKey("show_digits")
    val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
    val SHOW_PROGRESS = booleanPreferencesKey("show_progress")
    val PROGRESS_COLOR = longPreferencesKey("progress_color")
    val BACKGROUND_COLOR = longPreferencesKey("background_color")
    val DIGIT_COLOR = longPreferencesKey("digit_color")
    val DIGIT_STYLE = stringPreferencesKey("digit_style")
    val DIGIT_LAYOUT = stringPreferencesKey("digit_layout")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val VIBRATE = booleanPreferencesKey("vibrate_on_finish")
}

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            workSeconds = p[Keys.WORK] ?: defaults.workSeconds,
            breakSeconds = p[Keys.BREAK] ?: defaults.breakSeconds,
            showDigits = p[Keys.SHOW_DIGITS] ?: defaults.showDigits,
            showSeconds = p[Keys.SHOW_SECONDS] ?: defaults.showSeconds,
            showProgress = p[Keys.SHOW_PROGRESS] ?: defaults.showProgress,
            progressColor = p[Keys.PROGRESS_COLOR] ?: defaults.progressColor,
            backgroundColor = p[Keys.BACKGROUND_COLOR] ?: defaults.backgroundColor,
            digitColor = p[Keys.DIGIT_COLOR] ?: defaults.digitColor,
            digitStyle = p[Keys.DIGIT_STYLE]?.let { runCatching { DigitStyle.valueOf(it) }.getOrNull() }
                ?: defaults.digitStyle,
            digitLayout = p[Keys.DIGIT_LAYOUT]?.let { runCatching { DigitLayout.valueOf(it) }.getOrNull() }
                ?: defaults.digitLayout,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            vibrateOnFinish = p[Keys.VIBRATE] ?: defaults.vibrateOnFinish,
        )
    }

    suspend fun setDuration(mode: Mode, seconds: Int) = store.edit {
        it[if (mode == Mode.WORK) Keys.WORK else Keys.BREAK] = seconds.coerceIn(1, 24 * 3600)
    }

    suspend fun setShowDigits(v: Boolean) = store.edit { it[Keys.SHOW_DIGITS] = v }
    suspend fun setShowSeconds(v: Boolean) = store.edit { it[Keys.SHOW_SECONDS] = v }
    suspend fun setShowProgress(v: Boolean) = store.edit { it[Keys.SHOW_PROGRESS] = v }
    suspend fun setProgressColor(v: Long) = store.edit { it[Keys.PROGRESS_COLOR] = v }
    suspend fun setBackgroundColor(v: Long) = store.edit { it[Keys.BACKGROUND_COLOR] = v }
    suspend fun setDigitColor(v: Long) = store.edit { it[Keys.DIGIT_COLOR] = v }
    suspend fun setDigitStyle(v: DigitStyle) = store.edit { it[Keys.DIGIT_STYLE] = v.name }
    suspend fun setDigitLayout(v: DigitLayout) = store.edit { it[Keys.DIGIT_LAYOUT] = v.name }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setVibrate(v: Boolean) = store.edit { it[Keys.VIBRATE] = v }
}
