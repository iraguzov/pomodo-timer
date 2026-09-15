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

            GeometryReader { geo in
                if geo.size.width > geo.size.height {
                    // В горизонтальном положении одна колонка не влезает по высоте: табло уходит влево,
                    // пресеты и старт — вправо, и всё помещается на экран без прокрутки.
                    HStack(spacing: 24) {
                        VStack(spacing: 0) {
                            modePicker
                            Spacer().frame(height: 20)
                            preview(height: min(160, geo.size.height * 0.45))
                            hint
                        }
                        .frame(maxWidth: .infinity)
                        .layoutPriority(1.4)

                        VStack(spacing: 0) {
                            presets(perRow: 3)
                            Spacer().frame(height: 24)
                            startButton
                        }
                        .frame(maxWidth: .infinity)
                        // Сверху справа кнопки статистики и настроек — не заезжаем под них.
                        .padding(.top, 44)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    .frame(width: geo.size.width, height: geo.size.height)
                } else {
                    ScrollView {
                        VStack(spacing: 0) {
                            modePicker
                            Spacer().frame(height: 36)
                            preview(height: min(150, geo.size.height * 0.34))
                            hint
                            Spacer().frame(height: 28)
                            presets(perRow: quickPresets.count)
                            Spacer().frame(height: 48)
                            startButton
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity, minHeight: geo.size.height)
                    }
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

    private var hint: some View {
        Text("нажмите, чтобы задать часы и минуты")
            .font(.system(size: 12))
            .foregroundStyle(foreground.opacity(0.45))
    }

    private func presets(perRow: Int) -> some View {
        let rows = stride(from: 0, to: quickPresets.count, by: perRow).map {
            Array(quickPresets[$0..<min($0 + perRow, quickPresets.count)])
        }
        return VStack(spacing: 8) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 8) {
                    ForEach(row, id: \.self) { minutes in
                        presetChip(minutes)
                    }
                }
            }
        }
    }

    private func presetChip(_ minutes: Int) -> some View {
        let selected = seconds == minutes * 60
        return Button {
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
