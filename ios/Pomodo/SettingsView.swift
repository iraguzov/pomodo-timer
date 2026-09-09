import SwiftUI

struct SettingsView: View {

    let settings: SettingsStore

    @Environment(\.dismiss) private var dismiss
    @State private var durationMode: Mode?

    var body: some View {
        NavigationStack {
            Form {
                Section("Длительность") {
                    durationRow(.work)
                    durationRow(.rest)
                }

                Section("Отображение") {
                    Toggle(isOn: bind(\.showDigits)) {
                        row("Показывать цифры", "Полностью убрать время с экрана")
                    }
                    Toggle(isOn: bind(\.showSeconds)) {
                        row("Показывать секунды", "Иначе — только часы и минуты")
                    }
                    .disabled(!settings.showDigits)
                    Toggle(isOn: bind(\.showProgress)) {
                        row("Фоновый прогресс", "Заливка экрана слева направо")
                    }
                }

                Section("Стиль цифр") {
                    styleRow
                }

                Section("Расположение") {
                    Picker("Расположение", selection: bind(\.digitLayout)) {
                        ForEach(DigitLayout.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }

                Section("Цвета") {
                    ColorPicker("Цвет прогресса", selection: colorBinding(\.progressColor), supportsOpacity: false)
                    ColorPicker("Цвет фона", selection: colorBinding(\.backgroundColor), supportsOpacity: false)
                    backgroundPresets
                    ColorPicker("Цвет цифр", selection: colorBinding(\.digitColor), supportsOpacity: false)
                    palette
                }

                Section("Прочее") {
                    Toggle(isOn: bind(\.keepScreenOn)) {
                        row("Не гасить экран", "Пока таймер идёт")
                    }
                    Toggle("Вибрация в конце", isOn: bind(\.hapticOnFinish))
                }
            }
            .navigationTitle("Настройки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .sheet(item: $durationMode) { mode in
                DurationSheet(
                    title: "Таймер · \(mode.label)",
                    seconds: settings.seconds(for: mode)
                ) { settings.setSeconds($0, for: mode) }
            }
        }
    }

    private func durationRow(_ mode: Mode) -> some View {
        Button {
            durationMode = mode
        } label: {
            HStack {
                Text(mode.label).foregroundStyle(.primary)
                Spacer()
                Text(formatDuration(settings.seconds(for: mode)))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func row(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(subtitle).font(.caption).foregroundStyle(.secondary)
        }
    }

    private var styleRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(DigitStyle.allCases) { style in
                    let selected = style == settings.digitStyle
                    Button {
                        settings.digitStyle = style
                    } label: {
                        VStack(spacing: 6) {
                            TimeDisplay(
                                lines: ["12"],
                                style: style,
                                color: Color(rgb: settings.digitColor),
                                background: Color(rgb: settings.backgroundColor)
                            )
                            .padding(10)
                            .frame(width: 92, height: 64)
                            .background(Color(rgb: settings.backgroundColor), in: RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16).stroke(
                                    selected ? Color(rgb: settings.progressColor) : Color.gray.opacity(0.35),
                                    lineWidth: selected ? 2 : 1
                                )
                            )
                            Text(style.label)
                                .font(.caption)
                                .foregroundStyle(selected ? .primary : .secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)
        }
    }

    private var backgroundPresets: some View {
        HStack(spacing: 10) {
            presetButton("Тёмный", value: Palette.darkBackground) {
                settings.backgroundColor = Palette.darkBackground
                if settings.digitColor == 0x000000 { settings.digitColor = 0xFFFFFF }
            }
            presetButton("Светлый", value: Palette.lightBackground) {
                settings.backgroundColor = Palette.lightBackground
                if settings.digitColor == 0xFFFFFF { settings.digitColor = 0x101014 }
            }
        }
    }

    private func presetButton(_ title: String, value: UInt32, action: @escaping () -> Void) -> some View {
        let selected = settings.backgroundColor == value
        return Button(action: action) {
            Text(title)
                .font(.system(size: 14))
                .foregroundStyle(.primary)
                .padding(.horizontal, 18)
                .padding(.vertical, 9)
                .background(
                    selected ? Color(rgb: settings.progressColor).opacity(0.22) : Color.gray.opacity(0.15),
                    in: Capsule()
                )
                .overlay(
                    Capsule().stroke(
                        selected ? Color(rgb: settings.progressColor) : .clear,
                        lineWidth: 1
                    )
                )
        }
        .buttonStyle(.plain)
    }

    private var palette: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Palette.presets, id: \.self) { value in
                    Button {
                        settings.progressColor = value
                    } label: {
                        Circle()
                            .fill(Color(rgb: value))
                            .frame(width: 32, height: 32)
                            .overlay(Circle().stroke(Color.gray.opacity(0.35), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)
        }
    }

    private func bind<Value>(
        _ keyPath: ReferenceWritableKeyPath<SettingsStore, Value>
    ) -> Binding<Value> {
        Binding(
            get: { settings[keyPath: keyPath] },
            set: { settings[keyPath: keyPath] = $0 }
        )
    }

    private func colorBinding(
        _ keyPath: ReferenceWritableKeyPath<SettingsStore, UInt32>
    ) -> Binding<Color> {
        Binding(
            get: { Color(rgb: settings[keyPath: keyPath]) },
            set: { settings[keyPath: keyPath] = $0.rgbValue }
        )
    }
}

func formatDuration(_ seconds: Int) -> String {
    let h = seconds / 3600
    let m = (seconds % 3600) / 60
    return h > 0 ? "\(h) ч \(String(format: "%02d", m)) мин" : "\(m) мин"
}

/// «По классике» — два барабана, часы и минуты.
struct DurationSheet: View {

    let title: String
    let seconds: Int
    let onConfirm: (Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var hours: Int
    @State private var minutes: Int

    init(title: String, seconds: Int, onConfirm: @escaping (Int) -> Void) {
        self.title = title
        self.seconds = seconds
        self.onConfirm = onConfirm
        _hours = State(initialValue: seconds / 3600)
        _minutes = State(initialValue: (seconds % 3600) / 60)
    }

    var body: some View {
        NavigationStack {
            HStack(spacing: 0) {
                wheel(range: 0..<24, selection: $hours, caption: "часы")
                wheel(range: 0..<60, selection: $minutes, caption: "минуты")
            }
            .padding(.horizontal)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") {
                        onConfirm(max(hours * 3600 + minutes * 60, 60))
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.height(320)])
    }

    private func wheel(range: Range<Int>, selection: Binding<Int>, caption: String) -> some View {
        VStack(spacing: 4) {
            Text(caption).font(.caption).foregroundStyle(.secondary)
            Picker(caption, selection: selection) {
                ForEach(range, id: \.self) { Text(String(format: "%02d", $0)).tag($0) }
            }
            .pickerStyle(.wheel)
            .labelsHidden()
        }
    }
}
