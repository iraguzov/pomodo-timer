import SwiftUI

/// Какие сегменты горят у каждой цифры.
private let segmentMap: [Character: String] = [
    "0": "abcdef",
    "1": "bc",
    "2": "abged",
    "3": "abgcd",
    "4": "fgbc",
    "5": "afgcd",
    "6": "afgecd",
    "7": "abc",
    "8": "abcdefg",
    "9": "abcdfg",
]

/// Погашенные сегменты на настоящем индикаторе всё равно чуть видны.
private let segmentOffOpacity = 0.09

/// Индикатор слегка наклонён вправо — как почти все настоящие.
private let segmentSlant = 0.09

struct SegmentCell: View {

    let character: Character
    let width: Double
    let height: Double
    let color: Color

    var body: some View {
        Canvas { context, size in
            let lit = segmentMap[character] ?? ""

            let padX = size.width * 0.05
            let padY = size.height * 0.06
            let top = padY
            let bottom = size.height - padY
            let innerHeight = bottom - top
            let left = padX
            // наклон уводит верх вправо, поэтому справа оставляем на него место
            let right = size.width - padX - innerHeight * segmentSlant
            let thickness = innerHeight * 0.115
            let gap = thickness * 0.2
            let middle = (top + bottom) / 2
            let half = thickness / 2

            func skew(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: x + (bottom - y) * segmentSlant, y: y)
            }

            func shape(_ points: [CGPoint]) -> Path {
                var path = Path()
                path.move(to: points[0])
                points.dropFirst().forEach { path.addLine(to: $0) }
                path.closeSubpath()
                return path
            }

            func horizontal(_ centerY: Double) -> Path {
                let xa = left + gap
                let xb = right - gap
                return shape([
                    skew(xa, centerY),
                    skew(xa + half, centerY - half),
                    skew(xb - half, centerY - half),
                    skew(xb, centerY),
                    skew(xb - half, centerY + half),
                    skew(xa + half, centerY + half),
                ])
            }

            func vertical(_ centerX: Double, _ from: Double, _ to: Double) -> Path {
                let ya = from + gap
                let yb = to - gap
                return shape([
                    skew(centerX, ya),
                    skew(centerX + half, ya + half),
                    skew(centerX + half, yb - half),
                    skew(centerX, yb),
                    skew(centerX - half, yb - half),
                    skew(centerX - half, ya + half),
                ])
            }

            let segments: [(Character, Path)] = [
                ("a", horizontal(top + half)),
                ("g", horizontal(middle)),
                ("d", horizontal(bottom - half)),
                ("f", vertical(left + half, top, middle)),
                ("b", vertical(right - half, top, middle)),
                ("e", vertical(left + half, middle, bottom)),
                ("c", vertical(right - half, middle, bottom)),
            ]

            for (id, path) in segments {
                let on = lit.contains(id)
                context.fill(path, with: .color(on ? color : color.opacity(segmentOffOpacity)))
                if on {
                    // лёгкое свечение по краю зажжённого сегмента
                    context.stroke(
                        path,
                        with: .color(color.opacity(0.22)),
                        lineWidth: thickness * 0.5
                    )
                }
            }
        }
        .frame(width: width, height: height)
    }
}

/// Двоеточие индикатора — две квадратные точки, а не глиф шрифта.
struct SegmentColon: View {

    let width: Double
    let height: Double
    let color: Color

    var body: some View {
        Canvas { context, size in
            let side = size.width * 0.52
            let centerX = size.width / 2
            for fraction in [0.36, 0.64] {
                let centerY = size.height * fraction
                let shift = (size.height * (1 - fraction) - size.height / 2) * segmentSlant
                let rect = CGRect(
                    x: centerX - side / 2 + shift,
                    y: centerY - side / 2,
                    width: side,
                    height: side
                )
                context.fill(
                    Path(roundedRect: rect, cornerRadius: side * 0.25),
                    with: .color(color)
                )
            }
        }
        .frame(width: width, height: height)
    }
}
