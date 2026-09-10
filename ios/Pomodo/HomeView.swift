import SwiftUI

private let quickPresets = [5, 10, 15, 25, 45, 60]

struct HomeView: View {

    let settings: SettingsStore
    let timer: TimerModel
    let onOpenSettings: () -> Void
    let onOpenStats: () -> Void

    @State private var mode: Mode = .work
    @State private var editingDuration = false

    private var background: Color { Color(rgb: settings.backgroundColor) }
    private var accent: Color { Color(rgb: settings.progressColor) }
    private var foreground: Color { Color(rgb: settings.digitColor) }
    private var seconds: Int { settings.seconds(for: mode) }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            background.ignoresSafeArea()

            // В альбомной ориентации всё не помещается, поэтому содержимое прокручивается,
            // а табло ужимается под высоту экрана.
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 0) {
                        modePicker
                        Spacer().frame(height: 36)
                        preview(height: min(150, geo.size.height * 0.34))
                        Text("нажмите, чтобы задать часы и минуты")
                            .font(.system(size: 12))
                            .foregroundStyle(foreground.opacity(0.45))
                        Spacer().frame(height: 28)
                        presets
                        Spacer().frame(height: 48)
                        startButton
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    .frame(maxWidth: .infinity, minHeight: geo.size.height)
                }
            }

            HStack(spacing: 18) {
                Button(action: onOpenStats) {
                    Image(systemName: "chart.bar.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(foreground.opacity(0.7))
                }
                .accessibilityLabel("Статистика")
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(foreground.opacity(0.7))
                }
                .accessibilityLabel("Настройки")
            }
            .padding(20)
        }
        .sheet(isPresented: $editingDuration) {
            DurationSheet(
                title: "Таймер · \(mode.label)",
                seconds: seconds
            ) { settings.setSeconds($0, for: mode) }
        }
    }

    private var modePicker: some View {
        HStack(spacing: 10) {
            ForEach(Mode.allCases) { item in
                let selected = item == mode
                Button { mode = item } label: {
                    Text(item.label)
                        .font(.system(size: 14, weight: selected ? .semibold : .regular))
                        .foregroundStyle(foreground.opacity(selected ? 1 : 0.6))
                        .padding(.horizontal, 22)
                        .padding(.vertical, 10)
                        .background(selected ? accent.opacity(0.18) : .clear, in: Capsule())
                        .overlay(
                            Capsule().stroke(
                                selected ? accent : foreground.opacity(0.2),
                                lineWidth: 1
                            )
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func preview(height: Double) -> some View {
        Button {
            editingDuration = true
        } label: {
            TimeDisplay(
                lines: timeLines(
                    parts: timeParts(remainingMs: seconds * 1000, showSeconds: settings.showSeconds),
                    layout: .horizontal
                ),
                style: settings.digitStyle,
                color: foreground,
                background: background
            )
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var presets: some View {
        HStack(spacing: 8) {
            ForEach(quickPresets, id: \.self) { minutes in
                let selected = seconds == minutes * 60
                Button {
                    settings.setSeconds(minutes * 60, for: mode)
                } label: {
                    Text("\(minutes)")
                        .font(.system(size: 14))
                        .foregroundStyle(foreground.opacity(selected ? 1 : 0.65))
                        .frame(width: 44, height: 44)
                        .background(
                            selected ? accent.opacity(0.25) : foreground.opacity(0.07),
                            in: Circle()
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var startButton: some View {
        Button {
            timer.start(mode: mode, seconds: seconds)
        } label: {
            Image(systemName: "play.fill")
                .font(.system(size: 40))
                .foregroundStyle(accent.contrastingContent)
                .frame(width: 96, height: 96)
                .background(accent, in: Circle())
        }
        .buttonStyle(.plain)
    }
}
