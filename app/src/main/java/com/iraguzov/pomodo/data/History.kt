package com.iraguzov.pomodo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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

enum class StatsPeriod(val label: String) {
    DAY("День"),
    WEEK("Неделя"),
    MONTH("Месяц"),
}

data class Totals(val workedMs: Long, val overtimeMs: Long, val restMs: Long) {
    val isEmpty: Boolean get() = workedMs == 0L && overtimeMs == 0L && restMs == 0L
    val maxMs: Long get() = maxOf(workedMs + overtimeMs, restMs)
}

/** Столбик графика: короткая подпись под осью, полная — для подсказки, плюс те же три числа. */
data class Bucket(val label: String, val title: String, val emphasised: Boolean, val totals: Totals)

private fun totalsOf(sessions: List<Session>): Totals = Totals(
    workedMs = sessions.filter { it.mode == Mode.WORK }.sumOf { it.workedMs },
    overtimeMs = sessions.filter { it.mode == Mode.WORK }.sumOf { it.overtimeMs },
    restMs = sessions.filter { it.mode == Mode.BREAK }.sumOf { it.elapsedMs },
)

/** Периоды календарные: день, неделя с понедельника или месяц, в который попадает дата. */
fun startOf(period: StatsPeriod, date: LocalDate): LocalDate = when (period) {
    StatsPeriod.DAY -> date
    StatsPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    StatsPeriod.MONTH -> date.withDayOfMonth(1)
}

/** Первый день следующего периода — граница не включается. */
private fun endOf(period: StatsPeriod, date: LocalDate): LocalDate = shift(period, startOf(period, date), 1)

fun shift(period: StatsPeriod, date: LocalDate, steps: Long): LocalDate = when (period) {
    StatsPeriod.DAY -> date.plusDays(steps)
    StatsPeriod.WEEK -> date.plusWeeks(steps)
    StatsPeriod.MONTH -> date.plusMonths(steps)
}

/** Вперёд листать можно, пока не дошли до периода с сегодняшним днём. */
fun canShiftForward(period: StatsPeriod, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    !endOf(period, date).isAfter(LocalDate.now(zone))

fun sessionsIn(
    sessions: List<Session>,
    period: StatsPeriod,
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Session> {
    val from = startOf(period, date)
    val until = endOf(period, date)
    return sessions.filter { session ->
        val day = dateOf(session, zone)
        !day.isBefore(from) && day.isBefore(until)
    }
}

fun totalsFor(
    sessions: List<Session>,
    period: StatsPeriod,
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Totals = totalsOf(sessionsIn(sessions, period, date, zone))

/**
 * Прогон попадает в корзину по времени своего начала — сессии короткие,
 * и делить их между часами ради точности не стоит.
 */
fun bucketsFor(
    sessions: List<Session>,
    period: StatsPeriod,
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Bucket> {
    val relevant = sessionsIn(sessions, period, date, zone)
    return when (period) {
        StatsPeriod.DAY -> (0..23).map { hour ->
            Bucket(
                label = "%02d".format(hour),
                title = "%02d:00–%02d:00".format(hour, (hour + 1) % 24),
                emphasised = hour % 6 == 0,
                totals = totalsOf(relevant.filter { hourOf(it, zone) == hour }),
            )
        }

        else -> {
            val until = endOf(period, date)
            generateSequence(startOf(period, date)) { it.plusDays(1) }
                .takeWhile { it.isBefore(until) }
                .map { day ->
                    Bucket(
                        label = if (period == StatsPeriod.WEEK) weekdayOf(day) else day.dayOfMonth.toString(),
                        title = dayTitle(day),
                        emphasised = period == StatsPeriod.WEEK || day.dayOfMonth == 1 || day.dayOfMonth % 5 == 0,
                        totals = totalsOf(relevant.filter { dateOf(it, zone) == day }),
                    )
                }
                .toList()
        }
    }
}

/** «Сегодня», «8–14 сентября», «Август»; год добавляется, только если он не текущий. */
fun periodTitle(period: StatsPeriod, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val today = LocalDate.now(zone)
    fun year(day: LocalDate) = if (day.year == today.year) "" else " ${day.year}"
    return when (period) {
        StatsPeriod.DAY -> when (date) {
            today -> "Сегодня"
            today.minusDays(1) -> "Вчера"
            else -> dayTitle(date) + year(date)
        }

        StatsPeriod.WEEK -> {
            val first = startOf(period, date)
            val last = first.plusDays(6)
            if (first.month == last.month) {
                "${first.dayOfMonth}–${last.dayOfMonth} ${monthOf(last)}${year(last)}"
            } else {
                "${first.dayOfMonth} ${monthOf(first)} – ${last.dayOfMonth} ${monthOf(last)}${year(last)}"
            }
        }

        StatsPeriod.MONTH -> MonthsNominative[date.monthValue - 1] + year(date)
    }
}

// Интерфейс русский, поэтому даты подписываем по-русски независимо от языка системы.
private val WeekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val MonthsGenitive = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val MonthsNominative = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

private fun weekdayOf(day: LocalDate): String = WeekdaysShort[day.dayOfWeek.value - 1]

private fun monthOf(day: LocalDate): String = MonthsGenitive[day.monthValue - 1]

/** «Пн, 8 сентября». */
private fun dayTitle(day: LocalDate): String = "${weekdayOf(day)}, ${day.dayOfMonth} ${monthOf(day)}"

private fun dateOf(session: Session, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()

private fun hourOf(session: Session, zone: ZoneId): Int =
    Instant.ofEpochMilli(session.startedAt).atZone(zone).hour

/** Шаг сетки графика: круглое число минут или часов, чтобы делений было не больше четырёх. */
fun axisStepMs(peakMs: Long): Long {
    val stepMinutes = longArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360, 720)
    stepMinutes.forEach { minutes ->
        val step = minutes * 60_000
        if (peakMs <= step * 4) return step
    }
    val hour = 3_600_000L
    return (peakMs + hour * 4 - 1) / (hour * 4) * hour
}

/** Подпись деления оси: «0», «15 мин», «1 ч», «1,5 ч». */
fun formatAxis(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes == 0L -> "0"
        minutes < 60 -> "$minutes мин"
        minutes % 60 == 0L -> "${minutes / 60} ч"
        minutes % 30 == 0L -> "${minutes / 60},5 ч"
        else -> "${minutes / 60} ч ${"%02d".format(minutes % 60)}"
    }
}

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
