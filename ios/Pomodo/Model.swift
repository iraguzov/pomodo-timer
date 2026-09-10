import SwiftUI

/// Начертание цифр на экране таймера.
enum DigitStyle: String, CaseIterable, Identifiable {
    case minimal, neon, outline, mono, serif, flip, nixie, segment

    var id: String { rawValue }

    var label: String {
        switch self {
        case .minimal: "Минимал"
        case .neon: "Неон"
        case .outline: "Контур"
        case .mono: "Моно"
        case .serif: "Серифы"
        case .flip: "Флип"
        case .nixie: "Лампы"
        case .segment: "Сегменты"
        }
    }
}

/// Как располагать части времени друг относительно друга.
enum DigitLayout: String, CaseIterable, Identifiable {
    case horizontal, vertical

    var id: String { rawValue }

    var label: String {
        switch self {
        case .horizontal: "Горизонтально"
        case .vertical: "Вертикально"
        }
    }
}

enum Mode: String, CaseIterable, Identifiable, Codable {
    case work, rest

    var id: String { rawValue }

    var label: String {
        switch self {
        case .work: "Работа"
        case .rest: "Отдых"
        }
    }
}

enum Palette {
    static let darkBackground: UInt32 = 0x000000
    static let lightBackground: UInt32 = 0xF5F5F5
    static let accent: UInt32 = 0xFF4D5E

    static let presets: [UInt32] = [
        0xFF4D5E, 0xFF7A29, 0xFFC53D, 0x3DDC84, 0x00E5FF, 0x4D7CFE,
        0xA855F7, 0xFF2D95, 0xFFFFFF, 0x8A8A8F, 0x1E1E22, 0x000000,
    ]
}

/// После смены фона цифры могут слиться с ним — тогда возвращаем контрастный цвет,
/// а осознанно выбранный пользователем оставляем как есть.
func readableDigitColor(background: UInt32, digits: UInt32) -> UInt32 {
    let backgroundLuminance = Color(rgb: background).luminance
    let digitsLuminance = Color(rgb: digits).luminance
    guard abs(backgroundLuminance - digitsLuminance) < 0.35 else { return digits }
    return backgroundLuminance < 0.5 ? 0xFFFFFF : 0x101014
}

extension Color {
    init(rgb: UInt32) {
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255,
            opacity: 1
        )
    }

    var rgbValue: UInt32 {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        let clamp: (CGFloat) -> UInt32 = { UInt32((max(0, min(1, $0)) * 255).rounded()) }
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b)
    }

    /// Насколько цвет светлый — по нему выбираем контрастный текст и тему интерфейса.
    var luminance: Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        return 0.2126 * Double(r) + 0.7152 * Double(g) + 0.0722 * Double(b)
    }

    var contrastingContent: Color { luminance > 0.5 ? .black : .white }
}

/// Настройки приложения. Пишутся в UserDefaults сразу при изменении.
@Observable
final class SettingsStore {

    var workSeconds: Int { didSet { store(workSeconds, "workSeconds") } }
    var restSeconds: Int { didSet { store(restSeconds, "restSeconds") } }
    var showDigits: Bool { didSet { store(showDigits, "showDigits") } }
    var showSeconds: Bool { didSet { store(showSeconds, "showSeconds") } }
    var showProgress: Bool { didSet { store(showProgress, "showProgress") } }
    var progressColor: UInt32 { didSet { store(Int(progressColor), "progressColor") } }
    var backgroundColor: UInt32 { didSet { store(Int(backgroundColor), "backgroundColor") } }
    var digitColor: UInt32 { didSet { store(Int(digitColor), "digitColor") } }
    var digitStyle: DigitStyle { didSet { store(digitStyle.rawValue, "digitStyle") } }
    var digitLayout: DigitLayout { didSet { store(digitLayout.rawValue, "digitLayout") } }
    var keepScreenOn: Bool { didSet { store(keepScreenOn, "keepScreenOn") } }
    var hapticOnFinish: Bool { didSet { store(hapticOnFinish, "hapticOnFinish") } }

    private let defaults = UserDefaults.standard

    init() {
        let d = UserDefaults.standard
        workSeconds = d.object(forKey: "workSeconds") as? Int ?? 25 * 60
        restSeconds = d.object(forKey: "restSeconds") as? Int ?? 5 * 60
        showDigits = d.object(forKey: "showDigits") as? Bool ?? true
        showSeconds = d.object(forKey: "showSeconds") as? Bool ?? true
        showProgress = d.object(forKey: "showProgress") as? Bool ?? true
        progressColor = UInt32(d.object(forKey: "progressColor") as? Int ?? Int(Palette.accent))
        backgroundColor = UInt32(d.object(forKey: "backgroundColor") as? Int ?? Int(Palette.darkBackground))
        digitColor = UInt32(d.object(forKey: "digitColor") as? Int ?? 0xFFFFFF)
        digitStyle = DigitStyle(rawValue: d.string(forKey: "digitStyle") ?? "") ?? .minimal
        digitLayout = DigitLayout(rawValue: d.string(forKey: "digitLayout") ?? "") ?? .horizontal
        keepScreenOn = d.object(forKey: "keepScreenOn") as? Bool ?? true
        hapticOnFinish = d.object(forKey: "hapticOnFinish") as? Bool ?? true
    }

    func seconds(for mode: Mode) -> Int {
        mode == .work ? workSeconds : restSeconds
    }

    func setSeconds(_ value: Int, for mode: Mode) {
        let clamped = min(max(value, 60), 24 * 3600)
        if mode == .work { workSeconds = clamped } else { restSeconds = clamped }
    }

    private func store(_ value: Any, _ key: String) {
        defaults.set(value, forKey: key)
    }
}
