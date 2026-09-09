import SwiftUI

struct TimerView: View {

    let settings: SettingsStore
    let timer: TimerModel
    let onOpenSettings: () -> Void

    @State private var controlsVisible = false
    @State private var hideTask: Task<Void, Never>?

    private var background: Color { Color(rgb: settings.backgroundColor) }
    private var progressColor: Color { Color(rgb: settings.progressColor) }
    private var digitColor: Color { Color(rgb: settings.digitColor) }
    private var controlsShown: Bool { controlsVisible || !timer.running }

    var body: some View {
        ZStack {
            background.ignoresSafeArea()

            if settings.showProgress {
                GeometryReader { geo in
                    progressColor.frame(width: geo.size.width * timer.progress)
                }
                .ignoresSafeArea()
            }

            if settings.showDigits {
                TimeDisplay(
                    lines: timeLines(
                        parts: timeParts(
                            remainingMs: timer.remainingMs,
                            showSeconds: settings.showSeconds
                        ),
                        layout: settings.digitLayout
                    ),
                    style: settings.digitStyle,
                    color: digitColor,
                    background: background
                )
                .padding(.horizontal, 24)
                .padding(.vertical, 32)
            }

            VStack {
                Text(timer.finished ? "Готово" : timer.mode.label)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(digitColor.opacity(0.75))
                    .padding(.top, 20)
                    .opacity(controlsShown ? 1 : 0)

                Spacer()

                controls
                    .padding(.bottom, 28)
                    .opacity(controlsShown ? 1 : 0)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: controlsShown)
        .contentShape(Rectangle())
        .onTapGesture { showControls(!controlsVisible) }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onChange(of: timer.finished) { _, finished in
            if finished { showControls(true) }
        }
        .onDisappear { hideTask?.cancel() }
    }

    private var controls: some View {
        HStack(spacing: 28) {
            control("xmark", "Выйти") { timer.stop() }
            control(timer.running ? "pause.fill" : "play.fill", "Пуск") {
                timer.finished ? timer.restart() : timer.toggle()
            }
            control("arrow.clockwise", "Заново") { timer.restart() }
            control("gearshape.fill", "Настройки", action: onOpenSettings)
        }
    }

    private func control(
        _ symbol: String,
        _ hint: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 24))
                .foregroundStyle(digitColor.opacity(0.85))
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(hint)
    }

    /// Панель сама прячется, чтобы экран оставался чистым.
    private func showControls(_ visible: Bool) {
        controlsVisible = visible
        hideTask?.cancel()
        guard visible else { return }
        hideTask = Task {
            try? await Task.sleep(for: .milliseconds(3500))
            guard !Task.isCancelled, timer.running else { return }
            controlsVisible = false
        }
    }
}
