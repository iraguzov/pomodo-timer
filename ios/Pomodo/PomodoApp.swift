import SwiftUI

@main
struct PomodoApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {

    @State private var settings = SettingsStore()
    @State private var timer = TimerModel()
    @State private var history = HistoryStore()
    @State private var showSettings = false
    @State private var showStats = false

    private var background: Color { Color(rgb: settings.backgroundColor) }

    var body: some View {
        Group {
            if timer.active {
                TimerView(
                    settings: settings,
                    timer: timer,
                    onOpenSettings: { showSettings = true }
                )
            } else {
                HomeView(
                    settings: settings,
                    timer: timer,
                    onOpenSettings: { showSettings = true },
                    onOpenStats: { showStats = true }
                )
            }
        }
        // Настройки — лист поверх экрана, поэтому их видно и из работающего таймера.
        .sheet(isPresented: $showSettings) {
            SettingsView(settings: settings)
        }
        .sheet(isPresented: $showStats) {
            StatsView(history: history, settings: settings)
        }
        .tint(Color(rgb: settings.progressColor))
        .preferredColorScheme(background.luminance < 0.5 ? .dark : .light)
        .onAppear {
            timer.onSessionEnd = { history.append($0) }
            timer.hapticOnFinish = settings.hapticOnFinish
            updateIdleTimer()
        }
        .onChange(of: timer.running) { _, _ in updateIdleTimer() }
        .onChange(of: settings.keepScreenOn) { _, _ in updateIdleTimer() }
        .onChange(of: settings.hapticOnFinish) { _, new in timer.hapticOnFinish = new }
    }

    private func updateIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = settings.keepScreenOn && timer.running
    }
}
