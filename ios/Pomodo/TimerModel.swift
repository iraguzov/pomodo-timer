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

    var finished: Bool { active && elapsedMs >= totalMs }

    var progress: Double {
        totalMs <= 0 ? 0 : min(max(Double(elapsedMs) / Double(totalMs), 0), 1)
    }

    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var startedAt: TimeInterval = 0
    @ObservationIgnored private var finishNotified = false
    @ObservationIgnored var hapticOnFinish = true

    func start(mode: Mode, seconds: Int) {
        stopTicker()
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
        guard active, !finished else { return }
        running = true
        runLoop()
    }

    func toggle() {
        running ? pause() : resume()
    }

    func restart() {
        stopTicker()
        elapsedMs = 0
        finishNotified = false
        running = true
        runLoop()
    }

    func stop() {
        stopTicker()
        mode = .work
        totalMs = 0
        elapsedMs = 0
        running = false
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
    private func tick() {
        let elapsed = Int((ProcessInfo.processInfo.systemUptime - startedAt) * 1000)
        if elapsed >= totalMs {
            elapsedMs = totalMs
            running = false
            stopTicker()
            if !finishNotified {
                finishNotified = true
                if hapticOnFinish {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
            }
            return
        }
        elapsedMs = elapsed
    }

    private func stopTicker() {
        ticker?.invalidate()
        ticker = nil
    }
}
