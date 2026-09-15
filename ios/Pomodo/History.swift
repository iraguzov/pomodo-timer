import Foundation

/// Один прогон таймера: сколько собирались и сколько получилось на самом деле.
struct Session: Codable, Identifiable {
    let mode: Mode
    let startedAt: Date
    let plannedMs: Int
    let elapsedMs: Int

    var id: Date { startedAt }
    var workedMs: Int { min(elapsedMs, plannedMs) }
    var overtimeMs: Int { max(elapsedMs - plannedMs, 0) }
}

/// История лежит в приватном файле приложения — ничего никуда не уходит.
@Observable
final class HistoryStore {

    private(set) var sessions: [Session] = []

    @ObservationIgnored private let url: URL
    @ObservationIgnored private let maxSessions = 2000

    init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        url = directory.appendingPathComponent("history.json")
        load()
    }

    func append(_ session: Session) {
        sessions = Array((sessions + [session]).suffix(maxSessions))
        save()
    }

    func clear() {
        sessions = []
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: url) else { return }
        sessions = (try? JSONDecoder().decode([Session].self, from: data)) ?? []
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

enum StatsPeriod: String, CaseIterable, Identifiable {
    case day, week, month

    var id: String { rawValue }

    var label: String {
        switch self {
        case .day: "День"
        case .week: "Неделя"
        case .month: "Месяц"
        }
    }

    fileprivate var component: Calendar.Component {
        switch self {
        case .day: .day
        case .week: .weekOfYear
        case .month: .month
        }
    }
}

struct Totals {
    let workedMs: Int
    let overtimeMs: Int
    let restMs: Int

    var isEmpty: Bool { workedMs == 0 && overtimeMs == 0 && restMs == 0 }
    var maxMs: Int { max(workedMs + overtimeMs, restMs) }
}

/// Столбик графика: короткая подпись под осью, полная — для подсказки, плюс те же три числа.
struct Bucket: Identifiable {
    let id: Int
    let label: String
    let title: String
    let emphasised: Bool
    let totals: Totals
}

private func totalsOf(_ sessions: [Session]) -> Totals {
    Totals(
        workedMs: sessions.filter { $0.mode == .work }.reduce(0) { $0 + $1.workedMs },
        overtimeMs: sessions.filter { $0.mode == .work }.reduce(0) { $0 + $1.overtimeMs },
        restMs: sessions.filter { $0.mode == .rest }.reduce(0) { $0 + $1.elapsedMs }
    )
}

/// Интерфейс русский, поэтому неделя начинается с понедельника независимо от настроек системы.
var statsCalendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = Locale(identifier: "ru_RU")
    calendar.firstWeekday = 2
    return calendar
}

/// Периоды календарные: день, неделя с понедельника или месяц, в который попадает дата.
func range(of period: StatsPeriod, containing date: Date) -> DateInterval {
    statsCalendar.dateInterval(of: period.component, for: date) ?? DateInterval(start: date, duration: 0)
}

func shifted(_ date: Date, by steps: Int, period: StatsPeriod) -> Date {
    statsCalendar.date(byAdding: period.component, value: steps, to: date) ?? date
}

/// Вперёд листать можно, пока не дошли до периода с сегодняшним днём.
func canShiftForward(_ date: Date, period: StatsPeriod) -> Bool {
    range(of: period, containing: date).end <= Date()
}

func sessionsIn(_ sessions: [Session], period: StatsPeriod, containing date: Date) -> [Session] {
    let interval = range(of: period, containing: date)
    return sessions.filter { $0.startedAt >= interval.start && $0.startedAt < interval.end }
}

func totalsFor(_ sessions: [Session], period: StatsPeriod, containing date: Date) -> Totals {
    totalsOf(sessionsIn(sessions, period: period, containing: date))
}

/// Прогон попадает в корзину по времени своего начала — сессии короткие,
/// и делить их между часами ради точности не стоит.
func bucketsFor(_ sessions: [Session], period: StatsPeriod, containing date: Date) -> [Bucket] {
    let calendar = statsCalendar
    let relevant = sessionsIn(sessions, period: period, containing: date)

    if period == .day {
        return (0...23).map { hour in
            let inHour = relevant.filter { calendar.component(.hour, from: $0.startedAt) == hour }
            return Bucket(
                id: hour,
                label: String(format: "%02d", hour),
                title: String(format: "%02d:00–%02d:00", hour, (hour + 1) % 24),
                emphasised: hour % 6 == 0,
                totals: totalsOf(inHour)
            )
        }
    }

    let interval = range(of: period, containing: date)
    var days: [Date] = []
    var day = interval.start
    while day < interval.end {
        days.append(day)
        day = calendar.date(byAdding: .day, value: 1, to: day) ?? interval.end
    }
    return days.enumerated().map { index, day in
        let inDay = relevant.filter { calendar.isDate($0.startedAt, inSameDayAs: day) }
        let dayOfMonth = calendar.component(.day, from: day)
        return Bucket(
            id: index,
            label: period == .week ? weekdayOf(day) : "\(dayOfMonth)",
            title: dayTitle(day),
            emphasised: period == .week || dayOfMonth == 1 || dayOfMonth % 5 == 0,
            totals: totalsOf(inDay)
        )
    }
}

/// «Сегодня», «8–14 сентября», «Август»; год добавляется, только если он не текущий.
func periodTitle(_ period: StatsPeriod, containing date: Date) -> String {
    let calendar = statsCalendar
    let currentYear = calendar.component(.year, from: Date())
    func year(_ day: Date) -> String {
        let value = calendar.component(.year, from: day)
        return value == currentYear ? "" : " \(value)"
    }

    switch period {
    case .day:
        if calendar.isDateInToday(date) { return "Сегодня" }
        if calendar.isDateInYesterday(date) { return "Вчера" }
        return dayTitle(date) + year(date)
    case .week:
        let interval = range(of: .week, containing: date)
        let first = interval.start
        let last = calendar.date(byAdding: .day, value: -1, to: interval.end) ?? first
        let firstDay = calendar.component(.day, from: first)
        let lastDay = calendar.component(.day, from: last)
        if calendar.isDate(first, equalTo: last, toGranularity: .month) {
            return "\(firstDay)–\(lastDay) \(monthOf(last))" + year(last)
        }
        return "\(firstDay) \(monthOf(first)) – \(lastDay) \(monthOf(last))" + year(last)
    case .month:
        return monthsNominative[calendar.component(.month, from: date) - 1] + year(date)
    }
}

// Интерфейс русский, поэтому даты подписываем по-русски независимо от языка системы.
private let weekdaysShort = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]
private let monthsGenitive = [
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
]
private let monthsNominative = [
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
]

private func weekdayOf(_ day: Date) -> String {
    // В Calendar воскресенье — 1, понедельник — 2.
    weekdaysShort[(statsCalendar.component(.weekday, from: day) + 5) % 7]
}

private func monthOf(_ day: Date) -> String {
    monthsGenitive[statsCalendar.component(.month, from: day) - 1]
}

/// «Пн, 8 сентября».
private func dayTitle(_ day: Date) -> String {
    "\(weekdayOf(day)), \(statsCalendar.component(.day, from: day)) \(monthOf(day))"
}

/// Шаг сетки графика: круглое число минут или часов, чтобы делений было не больше четырёх.
func axisStepMs(_ peakMs: Int) -> Int {
    for minutes in [1, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360, 720] {
        let step = minutes * 60_000
        if peakMs <= step * 4 { return step }
    }
    let hour = 3_600_000
    return (peakMs + hour * 4 - 1) / (hour * 4) * hour
}

/// Подпись деления оси: «0», «15 мин», «1 ч», «1,5 ч».
func formatAxis(_ ms: Int) -> String {
    let minutes = ms / 60_000
    if minutes == 0 { return "0" }
    if minutes < 60 { return "\(minutes) мин" }
    if minutes % 60 == 0 { return "\(minutes / 60) ч" }
    if minutes % 30 == 0 { return "\(minutes / 60),5 ч" }
    return "\(minutes / 60) ч \(String(format: "%02d", minutes % 60))"
}

/// «1 ч 05 мин», «12 мин», «—» для пустого значения.
func formatSpan(_ ms: Int) -> String {
    guard ms > 0 else { return "—" }
    let totalMinutes = ms / 60_000
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    if hours > 0 { return "\(hours) ч \(String(format: "%02d", minutes)) мин" }
    if minutes > 0 { return "\(minutes) мин" }
    return "меньше минуты"
}
