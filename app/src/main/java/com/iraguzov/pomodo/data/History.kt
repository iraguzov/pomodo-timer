package com.iraguzov.pomodo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Один прогон таймера: сколько собирались и сколько получилось на самом деле. */
data class Session(
    val mode: Mode,
    val startedAt: Long,
    val plannedMs: Long,
    val elapsedMs: Long,
) {
    val workedMs: Long get() = minOf(elapsedMs, plannedMs)
    val overtimeMs: Long get() = (elapsedMs - plannedMs).coerceAtLeast(0L)
}

/**
 * История лежит в приватном файле приложения — ничего никуда не уходит.
 * Формат простой JSON, чтобы не тянуть базу ради пары полей.
 */
class HistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "history.json")

    suspend fun load(): List<Session> = withContext(Dispatchers.IO) { read() }

    suspend fun append(session: Session): List<Session> = withContext(Dispatchers.IO) {
        appendBlocking(session)
    }

    /** Нужен, когда приложение уже закрывается и корутины запускать поздно. */
    fun appendBlocking(session: Session): List<Session> {
        val sessions = (read() + session).takeLast(MAX_SESSIONS)
        write(sessions)
        return sessions
    }

    suspend fun clear(): List<Session> = withContext(Dispatchers.IO) {
        write(emptyList())
        emptyList()
    }

    private fun read(): List<Session> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val mode = runCatching { Mode.valueOf(item.getString("mode")) }.getOrNull()
                    ?: return@mapNotNull null
                Session(
                    mode = mode,
                    startedAt = item.getLong("startedAt"),
                    plannedMs = item.getLong("plannedMs"),
                    elapsedMs = item.getLong("elapsedMs"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(sessions: List<Session>) {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(
                JSONObject().apply {
                    put("mode", session.mode.name)
                    put("startedAt", session.startedAt)
                    put("plannedMs", session.plannedMs)
                    put("elapsedMs", session.elapsedMs)
                },
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    private companion object {
        const val MAX_SESSIONS = 2000
    }
}

enum class StatsPeriod(val label: String, val days: Int) {
    DAY("День", 1),
    WEEK("Неделя", 7),
    MONTH("Месяц", 30),
}

data class Totals(val workedMs: Long, val overtimeMs: Long, val restMs: Long) {
    val isEmpty: Boolean get() = workedMs == 0L && overtimeMs == 0L && restMs == 0L
    val maxMs: Long get() = maxOf(workedMs + overtimeMs, restMs)
}

/** Столбик графика: подпись плюс те же три числа. */
data class Bucket(val label: String, val emphasised: Boolean, val totals: Totals)

private fun totalsOf(sessions: List<Session>): Totals = Totals(
    workedMs = sessions.filter { it.mode == Mode.WORK }.sumOf { it.workedMs },
    overtimeMs = sessions.filter { it.mode == Mode.WORK }.sumOf { it.overtimeMs },
    restMs = sessions.filter { it.mode == Mode.BREAK }.sumOf { it.elapsedMs },
)

/**
 * Периоды скользящие: «неделя» — это последние семь дней вместе с сегодняшним,
 * а не календарная неделя, иначе в понедельник смотреть было бы не на что.
 */
fun sessionsIn(sessions: List<Session>, period: StatsPeriod, zone: ZoneId = ZoneId.systemDefault()): List<Session> {
    val from = LocalDate.now(zone).minusDays((period.days - 1).toLong())
    return sessions.filter { session ->
        !dateOf(session, zone).isBefore(from)
    }
}

fun totalsFor(sessions: List<Session>, period: StatsPeriod, zone: ZoneId = ZoneId.systemDefault()): Totals =
    totalsOf(sessionsIn(sessions, period, zone))

/**
 * Прогон попадает в корзину по времени своего начала — сессии короткие,
 * и делить их между часами ради точности не стоит.
 */
fun bucketsFor(
    sessions: List<Session>,
    period: StatsPeriod,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Bucket> {
    val relevant = sessionsIn(sessions, period, zone)
    return when (period) {
        StatsPeriod.DAY -> (0..23).map { hour ->
            Bucket(
                label = "%02d".format(hour),
                emphasised = hour % 6 == 0,
                totals = totalsOf(relevant.filter { hourOf(it, zone) == hour }),
            )
        }

        else -> {
            val today = LocalDate.now(zone)
            (period.days - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                val label = if (period == StatsPeriod.WEEK) {
                    // Интерфейс русский, поэтому и дни недели подписываем по-русски
                    // независимо от языка системы.
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, RussianLocale)
                } else {
                    date.dayOfMonth.toString()
                }
                Bucket(
                    label = label,
                    emphasised = period == StatsPeriod.WEEK || date.dayOfMonth % 5 == 0,
                    totals = totalsOf(relevant.filter { dateOf(it, zone) == date }),
                )
            }
        }
    }
}

private val RussianLocale: Locale = Locale.forLanguageTag("ru")

private fun dateOf(session: Session, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()

private fun hourOf(session: Session, zone: ZoneId): Int =
    Instant.ofEpochMilli(session.startedAt).atZone(zone).hour

/** «1 ч 05 мин», «12 мин», «—» для пустого значения. */
fun formatSpan(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours ч ${"%02d".format(minutes)} мин"
        minutes > 0 -> "$minutes мин"
        else -> "меньше минуты"
    }
}
