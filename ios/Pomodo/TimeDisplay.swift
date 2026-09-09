import SwiftUI

/// Со секундами: [ЧЧ, ММ, СС] либо [ММ, СС]; без секунд — всегда [ЧЧ, ММ] («по классике»).
func timeParts(remainingMs: Int, showSeconds: Bool) -> [String] {
    let totalSec = Int(ceil(Double(max(remainingMs, 0)) / 1000))
    let h = totalSec / 3600
    let m = (totalSec % 3600) / 60
    let s = totalSec % 60
    if showSeconds {
        return h > 0 ? [pad(h), pad(m), pad(s)] : [pad(m), pad(s)]
    }
    let totalMin = Int(ceil(Double(totalSec) / 60))
    return [pad(totalMin / 60), pad(totalMin % 60)]
}

private func pad(_ value: Int) -> String {
    String(format: "%02d", value)
}

func timeLines(parts: [String], layout: DigitLayout) -> [String] {
    layout == .horizontal ? [parts.joined(separator: ":")] : parts
}

extension DigitStyle {
    var digitWidthFactor: Double {
        switch self {
        case .segment: 0.66
        case .nixie: 0.82
        case .flip: 0.88
        case .mono, .outline: 0.64
        default: 0.58
        }
    }

    var lineHeightFactor: Double {
        switch self {
        case .flip: 1.15
        case .nixie: 1.30
        case .segment: 1.12
        default: 1.06
        }
    }

    func font(size: Double) -> Font {
        switch self {
        case .minimal: .system(size: size, weight: .ultraLight)
        case .neon: .system(size: size, weight: .light)
        case .mono: .system(size: size, weight: .medium, design: .monospaced)
        case .serif: .system(size: size, weight: .light, design: .serif)
        case .flip: .system(size: size, weight: .medium)
        case .nixie: .system(size: size, weight: .light)
        case .segment: .system(size: size)
        case .outline: .system(size: size, weight: .bold)
        }
    }
}

/// Тёплый янтарь газоразрядной лампы.
let nixieAmber = Color(rgb: 0xFF8A2B)

/// Лампа светится только ярким насыщенным цветом. Белый, серый и тёмный цвет цифр
/// свечением не будет, поэтому для них подставляем классический янтарь.
func nixieGlow(_ color: Color) -> Color {
    var hue: CGFloat = 0, saturation: CGFloat = 0, brightness: CGFloat = 0, alpha: CGFloat = 0
    UIColor(color).getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha)
    return (saturation < 0.35 || brightness < 0.5) ? nixieAmber : color
}

private let colonFactor = 0.34
private let gapFactor = 0.14

/// Табло времени, которое само подбирает кегль под доступное место.
/// Каждый символ живёт в ячейке фиксированной ширины, поэтому цифры не «дёргают» вёрстку.
struct TimeDisplay: View {

    let lines: [String]
    let style: DigitStyle
    let color: Color
    let background: Color

    var body: some View {
        GeometryReader { geo in
            let unitsW = lines.map { line in
                line.reduce(0.0) { $0 + ($1 == ":" ? colonFactor : style.digitWidthFactor) }
            }.max() ?? 1
            let unitsH = Double(lines.count) * style.lineHeightFactor
                + Double(lines.count - 1) * gapFactor
            let fontSize = min(geo.size.width * 0.94 / unitsW, geo.size.height * 0.88 / unitsH)

            VStack(spacing: fontSize * gapFactor) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    HStack(spacing: 0) {
                        ForEach(Array(line.enumerated()), id: \.offset) { index, character in
                            cell(character, index: index, fontSize: fontSize)
                        }
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    private var glowColor: Color { style == .nixie ? nixieGlow(color) : color }

    @ViewBuilder
    private func cell(_ character: Character, index: Int, fontSize: Double) -> some View {
        let width = fontSize * (character == ":" ? colonFactor : style.digitWidthFactor)
        let height = fontSize * style.lineHeightFactor

        if character == ":" && style == .segment {
            SegmentColon(width: width, height: height, color: color)
        } else if character == ":" {
            glyph(":", fontSize: fontSize).frame(width: width, height: height)
        } else if style == .segment {
            SegmentCell(character: character, width: width, height: height, color: color)
        } else if style == .nixie {
            NixieCell(
                character: character,
                width: width,
                height: height,
                fontSize: fontSize,
                glow: glowColor,
                background: background
            )
        } else if style == .flip {
            FlipDigit(
                character: character,
                width: width,
                height: height,
                fontSize: fontSize,
                color: color,
                background: background
            )
        } else {
            glyph(String(character), fontSize: fontSize).frame(width: width, height: height)
        }
    }

    @ViewBuilder
    private func glyph(_ text: String, fontSize: Double) -> some View {
        switch style {
        case .neon, .nixie:
            let glow = glowColor
            let core = style == .nixie
                ? Color(UIColor(glow).blend(with: UIColor(Color(rgb: 0xFFE3B8)), fraction: 0.35))
                : Color(UIColor(glow).blend(with: .white, fraction: 0.8))
            let outer = style == .nixie ? 0.34 : 0.26
            ZStack {
                Text(text).font(style.font(size: fontSize)).foregroundStyle(glow.opacity(0.7))
                    .shadow(color: glow, radius: fontSize * outer)
                Text(text).font(style.font(size: fontSize)).foregroundStyle(glow)
                    .shadow(color: glow, radius: fontSize * 0.15)
                Text(text).font(style.font(size: fontSize)).foregroundStyle(core)
                    .shadow(color: glow, radius: fontSize * 0.05)
            }
            .fixedSize()
        case .outline:
            OutlineText(text: text, fontSize: fontSize, color: UIColor(color))
                .fixedSize()
        default:
            Text(text).font(style.font(size: fontSize)).foregroundStyle(color).fixedSize()
        }
    }
}

/// У SwiftUI нет обводки текста, а обводка UIKit ставит острые стыки и даёт шипы
/// на углах цифр. Поэтому собираем контуры глифов сами и обводим их со скруглением.
struct OutlineText: UIViewRepresentable {

    let text: String
    let fontSize: Double
    let color: UIColor

    func makeUIView(context: Context) -> OutlineLabel {
        let view = OutlineLabel()
        view.backgroundColor = .clear
        view.isOpaque = false
        return view
    }

    func updateUIView(_ view: OutlineLabel, context: Context) {
        view.configure(text: text, fontSize: fontSize, color: color)
    }
}

final class OutlineLabel: UIView {

    private var text = ""
    private var fontSize: Double = 40
    private var strokeColor: UIColor = .white

    private var font: UIFont { .systemFont(ofSize: fontSize, weight: .medium) }

    func configure(text: String, fontSize: Double, color: UIColor) {
        guard text != self.text || fontSize != self.fontSize || color != strokeColor else { return }
        self.text = text
        self.fontSize = fontSize
        strokeColor = color
        invalidateIntrinsicContentSize()
        setNeedsDisplay()
    }

    override var intrinsicContentSize: CGSize {
        let size = (text as NSString).size(withAttributes: [.font: font])
        let extra = fontSize * 0.06
        return CGSize(width: ceil(size.width + extra), height: ceil(size.height + extra))
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), !text.isEmpty else { return }

        let line = CTLineCreateWithAttributedString(
            NSAttributedString(string: text, attributes: [.font: font])
        )
        let glyphs = CGMutablePath()
        for run in CTLineGetGlyphRuns(line) as? [CTRun] ?? [] {
            let attributes = CTRunGetAttributes(run) as? [NSAttributedString.Key: Any] ?? [:]
            guard let runFont = attributes[.font] else { continue }
            let ctFont = runFont as! CTFont
            let count = CTRunGetGlyphCount(run)
            var ids = [CGGlyph](repeating: 0, count: count)
            var positions = [CGPoint](repeating: .zero, count: count)
            CTRunGetGlyphs(run, CFRange(location: 0, length: count), &ids)
            CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
            for index in 0..<count {
                guard let glyph = CTFontCreatePathForGlyph(ctFont, ids[index], nil) else { continue }
                let shift = CGAffineTransform(translationX: positions[index].x, y: positions[index].y)
                glyphs.addPath(glyph, transform: shift)
            }
        }

        var ascent: CGFloat = 0, descent: CGFloat = 0, leading: CGFloat = 0
        let width = CTLineGetTypographicBounds(line, &ascent, &descent, &leading)

        context.saveGState()
        // Core Text считает ось Y вверх, UIKit — вниз.
        context.translateBy(x: 0, y: bounds.height)
        context.scaleBy(x: 1, y: -1)
        context.translateBy(
            x: (bounds.width - CGFloat(width)) / 2,
            y: (bounds.height - (ascent + descent)) / 2 + descent
        )
        context.addPath(glyphs)
        context.setLineWidth(fontSize * 0.026)
        context.setLineJoin(.round)
        context.setLineCap(.round)
        context.setStrokeColor(strokeColor.cgColor)
        context.strokePath()
        context.restoreGState()
    }
}

extension UIColor {
    func blend(with other: UIColor, fraction: CGFloat) -> UIColor {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let f = max(0, min(1, fraction))
        return UIColor(
            red: r1 + (r2 - r1) * f,
            green: g1 + (g2 - g1) * f,
            blue: b1 + (b2 - b1) * f,
            alpha: a1 + (a2 - a1) * f
        )
    }
}
