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

    var days: Int {
        switch self {
        case .day: 1
        case .week: 7
        case .month: 30
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

/// Столбик графика: подпись плюс те же три числа.
struct Bucket: Identifiable {
    let id: Int
    let label: String
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

/// Периоды скользящие: «неделя» — это последние семь дней вместе с сегодняшним,
/// а не календарная неделя, иначе в понедельник смотреть было бы не на что.
func sessionsIn(_ sessions: [Session], period: StatsPeriod) -> [Session] {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    guard let from = calendar.date(byAdding: .day, value: -(period.days - 1), to: today) else {
        return sessions
    }
    return sessions.filter { $0.startedAt >= from }
}

func totalsFor(_ sessions: [Session], period: StatsPeriod) -> Totals {
    totalsOf(sessionsIn(sessions, period: period))
}

/// Прогон попадает в корзину по времени своего начала — сессии короткие,
/// и делить их между часами ради точности не стоит.
func bucketsFor(_ sessions: [Session], period: StatsPeriod) -> [Bucket] {
    let calendar = Calendar.current
    let relevant = sessionsIn(sessions, period: period)

    if period == .day {
        return (0...23).map { hour in
            let inHour = relevant.filter { calendar.component(.hour, from: $0.startedAt) == hour }
            return Bucket(
                id: hour,
                label: String(format: "%02d", hour),
                emphasised: hour % 6 == 0,
                totals: totalsOf(inHour)
            )
        }
    }

    let today = calendar.startOfDay(for: Date())
    return (0..<period.days).reversed().map { back in
        let date = calendar.date(byAdding: .day, value: -back, to: today) ?? today
        let inDay = relevant.filter { calendar.isDate($0.startedAt, inSameDayAs: date) }
        let dayOfMonth = calendar.component(.day, from: date)
        return Bucket(
            id: back,
            label: period == .week ? shortWeekday(date) : "\(dayOfMonth)",
            emphasised: period == .week || dayOfMonth % 5 == 0,
            totals: totalsOf(inDay)
        )
    }
}

/// Интерфейс русский, поэтому и дни недели подписываем по-русски
/// независимо от языка системы.
private func shortWeekday(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ru_RU")
    formatter.dateFormat = "EEEEEE"
    return formatter.string(from: date)
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
