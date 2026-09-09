import SwiftUI

/// Перекидная карточка «флип-часов».
///
/// Переворот разбит на две половины: сначала падает верхняя створка с прошлой цифрой,
/// затем поднимается нижняя с новой. Смена цифр и фазы происходит одним изменением
/// состояния — иначе на кадр видно верх новой цифры вместе с низом старой.
struct FlipDigit: View {

    let character: Character
    let width: Double
    let height: Double
    let fontSize: Double
    let color: Color
    let background: Color

    private enum Phase { case idle, fallingTop, risingBottom }

    @State private var current: Character
    @State private var previous: Character
    @State private var phase: Phase = .idle
    @State private var topAngle: Double = 0
    @State private var bottomAngle: Double = 90
    @State private var generation = 0

    init(
        character: Character,
        width: Double,
        height: Double,
        fontSize: Double,
        color: Color,
        background: Color
    ) {
        self.character = character
        self.width = width
        self.height = height
        self.fontSize = fontSize
        self.color = color
        self.background = background
        _current = State(initialValue: character)
        _previous = State(initialValue: character)
    }

    private var cardWidth: Double { width * 0.9 }
    private var radius: Double { cardWidth * 0.12 }
    private var cardColor: Color {
        Color(UIColor(background).blend(with: UIColor(color), fraction: 0.13))
    }

    /// Пока створка падает, снизу видна прошлая цифра; как только она легла — текущая.
    private var bottomCharacter: Character { phase == .idle ? current : previous }

    var body: some View {
        ZStack {
            half(current, top: true)
                .frame(maxHeight: .infinity, alignment: .top)

            half(bottomCharacter, top: false)
                .frame(maxHeight: .infinity, alignment: .bottom)

            if phase == .fallingTop {
                half(previous, top: true)
                    .rotation3DEffect(
                        .degrees(topAngle),
                        axis: (x: 1, y: 0, z: 0),
                        anchor: .bottom,
                        perspective: 0.55
                    )
                    .frame(maxHeight: .infinity, alignment: .top)
            }

            if phase == .risingBottom {
                half(current, top: false)
                    .rotation3DEffect(
                        .degrees(bottomAngle),
                        axis: (x: 1, y: 0, z: 0),
                        anchor: .top,
                        perspective: 0.55
                    )
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }

            // Линия разлома посередине карточки.
            Rectangle()
                .fill(background)
                .frame(width: cardWidth, height: max(cardWidth * 0.025, 1))
        }
        .frame(width: width, height: height)
        .onChange(of: character) { _, newValue in
            flip(to: newValue)
        }
    }

    private func half(_ value: Character, top: Bool) -> some View {
        Text(String(value))
            .font(.system(size: fontSize, weight: .medium))
            .foregroundStyle(color)
            .fixedSize()
            .frame(width: cardWidth, height: height)
            .frame(height: height / 2, alignment: top ? .top : .bottom)
            .background(cardColor)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: top ? radius : 0,
                    bottomLeadingRadius: top ? 0 : radius,
                    bottomTrailingRadius: top ? 0 : radius,
                    topTrailingRadius: top ? radius : 0
                )
            )
    }

    private func flip(to newValue: Character) {
        guard newValue != current else { return }
        generation += 1
        let token = generation

        previous = current
        current = newValue
        topAngle = 0
        phase = .fallingTop

        withAnimation(.linear(duration: 0.21)) {
            topAngle = -90
        } completion: {
            guard token == generation else { return }
            bottomAngle = 90
            phase = .risingBottom
            withAnimation(.linear(duration: 0.21)) {
                bottomAngle = 0
            } completion: {
                guard token == generation else { return }
                phase = .idle
            }
        }
    }
}
