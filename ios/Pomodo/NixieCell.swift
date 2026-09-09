import SwiftUI

/// Цифра в газоразрядной лампе: горящий катод впереди, тусклые силуэты соседних цифр
/// позади, сетка анода поверх и тёплый ореол внутри колбы.
struct NixieCell: View {

    let character: Character
    let width: Double
    let height: Double
    let fontSize: Double
    let glow: Color
    let background: Color

    private var tubeWidth: Double { width * 0.94 }

    private var glass: Color {
        Color(UIColor(background).blend(with: UIColor(glow), fraction: 0.10))
    }

    private var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: tubeWidth * 0.46,
            bottomLeadingRadius: tubeWidth * 0.22,
            bottomTrailingRadius: tubeWidth * 0.22,
            topTrailingRadius: tubeWidth * 0.46
        )
    }

    /// Соседние катоды, которые в лампе стоят за горящим.
    private var unlit: [Int] {
        guard let digit = character.wholeNumberValue, (0...9).contains(digit) else { return [] }
        return [(digit + 3) % 10, (digit + 7) % 10]
    }

    private var core: Color {
        Color(UIColor(glow).blend(with: UIColor(Color(rgb: 0xFFE3B8)), fraction: 0.35))
    }

    var body: some View {
        ZStack {
            glass

            RadialGradient(
                colors: [glow.opacity(0.26), .clear],
                center: .center,
                startRadius: 0,
                endRadius: tubeWidth * 0.85
            )

            ForEach(Array(unlit.enumerated()), id: \.offset) { index, digit in
                Text("\(digit)")
                    .font(.system(size: fontSize, weight: .light))
                    .foregroundStyle(glow)
                    .fixedSize()
                    .scaleEffect(0.94)
                    .opacity(0.16)
                    .offset(y: height * (index == 0 ? -0.035 : 0.035))
            }

            anodeGrid

            ZStack {
                Text(String(character)).font(.system(size: fontSize, weight: .light))
                    .foregroundStyle(glow.opacity(0.7))
                    .shadow(color: glow, radius: fontSize * 0.34)
                Text(String(character)).font(.system(size: fontSize, weight: .light))
                    .foregroundStyle(glow)
                    .shadow(color: glow, radius: fontSize * 0.15)
                Text(String(character)).font(.system(size: fontSize, weight: .light))
                    .foregroundStyle(core)
                    .shadow(color: glow, radius: fontSize * 0.05)
            }
            .fixedSize()
        }
        .frame(width: tubeWidth, height: height)
        .clipShape(shape)
        .overlay(shape.stroke(glow.opacity(0.3), lineWidth: tubeWidth * 0.02))
        .frame(width: width, height: height)
    }

    /// Тонкая сетка анода перед цифрой.
    private var anodeGrid: some View {
        Canvas { context, size in
            let step = size.width / 8
            for index in 1...7 {
                let x = step * Double(index)
                var path = Path()
                path.move(to: CGPoint(x: x, y: size.height * 0.14))
                path.addLine(to: CGPoint(x: x, y: size.height * 0.86))
                context.stroke(
                    path,
                    with: .color(glow.opacity(0.16)),
                    lineWidth: size.width * 0.012
                )
            }
        }
    }
}
