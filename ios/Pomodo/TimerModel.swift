import SwiftUI
import UIKit

@Observable
final class TimerModel {

    private(set) var mode: Mode = .work
    private(set) var totalMs: Int = 0
    private(set) var elapsedMs: Int = 0
    private(set) var running = false

    /// Таймер запущен или стоит на паузе — экран таймера показан.
    var active: Bool { totalMs > 0 }

    var remainingMs: Int { max(totalMs - elapsedMs, 0) }

    /// Сколько натикало сверх заданного времени.
    var overtimeMs: Int { max(elapsedMs - totalMs, 0) }

    var finished: Bool { active && elapsedMs >= totalMs }

    /// Что на табло: обратный отсчёт, а после нуля — время переработки.
    var displayMs: Int { finished ? overtimeMs : remainingMs }

    /// Весь экран — это всё время с момента старта. Пока идёт отсчёт, это заданное время;
    /// дальше масштаб растёт вместе с переработкой.
    private var spanMs: Int { max(totalMs, elapsedMs) }

    /// Доля экрана под заданным временем: до нуля заполняется, после — сжимается
    /// пропорционально. Полностью переработка её никогда не вытеснит.
    var progress: Double {
        spanMs <= 0 ? 0 : min(max(Double(min(elapsedMs, totalMs)) / Double(spanMs), 0), 1)
    }

    /// Доля экрана под переработкой — остаток справа от заданного времени.
    var overtimeProgress: Double {
        spanMs <= 0 ? 0 : min(max(Double(overtimeMs) / Double(spanMs), 0), 1)
    }

    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var startedAt: TimeInterval = 0
    @ObservationIgnored private var finishNotified = false
    @ObservationIgnored var hapticOnFinish = true

    /// Куда отдавать завершённый прогон. Совсем короткие пропускаем —
    /// это случайные нажатия, а не работа.
    @ObservationIgnored var onSessionEnd: ((Session) -> Void)?
    @ObservationIgnored private var runStartedAt = Date()
    @ObservationIgnored private let minRecordedMs = 5_000

    func start(mode: Mode, seconds: Int) {
        stopTicker()
        commitRun()
        runStartedAt = Date()
        self.mode = mode
        totalMs = max(seconds, 1) * 1000
        elapsedMs = 0
        finishNotified = false
        running = true
        runLoop()
    }

    func pause() {
        stopTicker()
        running = false
    }

    func resume() {
        guard active else { return }
        running = true
        runLoop()
    }

    func toggle() {
        running ? pause() : resume()
    }

    func restart() {
        stopTicker()
        commitRun()
        runStartedAt = Date()
        elapsedMs = 0
        finishNotified = false
        running = true
        runLoop()
    }

    func stop() {
        stopTicker()
        commitRun()
        mode = .work
        totalMs = 0
        elapsedMs = 0
        running = false
    }

    private func commitRun() {
        guard active, elapsedMs >= minRecordedMs else { return }
        onSessionEnd?(
            Session(
                mode: mode,
                startedAt: runStartedAt,
                plannedMs: totalMs,
                elapsedMs: elapsedMs
            )
        )
    }

    private func runLoop() {
        startedAt = ProcessInfo.processInfo.systemUptime - Double(elapsedMs) / 1000
        let ticker = Timer(timeInterval: 1.0 / 30, repeats: true) { [weak self] _ in
            self?.tick()
        }
        // .common — иначе отсчёт замирает, пока пользователь скроллит.
        RunLoop.main.add(ticker, forMode: .common)
        self.ticker = ticker
    }

    /// Считаем от монотонных часов, а не накоплением шагов: так возврат из фона не смазывает отсчёт.
    /// После нуля отсчёт не останавливается, а продолжает копить переработку.
    private func tick() {
        elapsedMs = Int((ProcessInfo.processInfo.systemUptime - startedAt) * 1000)
        if finished, !finishNotified {
            finishNotified = true
            if hapticOnFinish {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }

    private func stopTicker() {
        ticker?.invalidate()
        ticker = nil
    }
}
