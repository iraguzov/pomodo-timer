import SwiftUI
import UIKit

struct StatsView: View {

    let history: HistoryStore
    let settings: SettingsStore

    @Environment(\.dismiss) private var dismiss
    @State private var period: StatsPeriod = .week
    @State private var date = Date()
    @State private var selected: Int?
    @State private var selectionAtTouch: Int?
    @State private var touching = false
    @State private var pickingDate = false
    @State private var confirmClear = false

    private var workColor: Color { Color(rgb: settings.progressColor) }
    private var overtimeColor: Color { oppositeOf(workColor) }
    private var restColor: Color { .secondary }

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                let totals = totalsFor(history.sessions, period: period, containing: date)
                let buckets = bucketsFor(history.sessions, period: period, containing: date)

                // В горизонтальном положении сводка уходит влево, а график получает всю высоту экрана.
                if proxy.size.width > proxy.size.height {
                    HStack(alignment: .top, spacing: 0) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 0) {
                                controls
                                totalsBlock(totals, compact: true)
                                footer
                            }
                        }
                        .frame(width: min(max(proxy.size.width * 0.4, 260), 340))

                        chartArea(totals, buckets)
                            .padding(.leading, 8)
                            .padding(.trailing)
                            .padding(.vertical, 12)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 0) {
                        controls
                        totalsBlock(totals, compact: false)
                        chartArea(totals, buckets)
                            .padding(.horizontal)
                            .padding(.vertical, 12)
                        footer
                    }
                }
            }
            .navigationTitle("Статистика")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .alert("Очистить историю?", isPresented: $confirmClear) {
                Button("Очистить", role: .destructive) { history.clear() }
                Button("Отмена", role: .cancel) {}
            } message: {
                Text("Все записи о работе, переработке и отдыхе будут удалены безвозвратно.")
            }
            .sheet(isPresented: $pickingDate) { datePickerSheet }
        }
        .onChange(of: period) { selected = nil }
        .onChange(of: date) { selected = nil }
    }

    private var controls: some View {
        VStack(spacing: 6) {
            Picker("Период", selection: $period) {
                ForEach(StatsPeriod.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)

            HStack(spacing: 0) {
                Button {
                    date = shifted(date, by: -1, period: period)
                } label: {
                    Image(systemName: "chevron.left").frame(width: 44, height: 40)
                }
                .accessibilityLabel("Предыдущий период")

                Button {
                    pickingDate = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar")
                            .font(.system(size: 14))
                            .foregroundStyle(.secondary)
                        Text(periodTitle(period, containing: date))
                            .font(.system(size: 16, weight: .medium))
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .contentShape(Rectangle())
                }
                .foregroundStyle(.primary)

                Button {
                    date = min(shifted(date, by: 1, period: period), Date())
                } label: {
                    Image(systemName: "chevron.right").frame(width: 44, height: 40)
                }
                .disabled(!canShiftForward(date, period: period))
                .accessibilityLabel("Следующий период")
            }
            .font(.system(size: 17, weight: .semibold))
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 8)
    }

    private func totalsBlock(_ totals: Totals, compact: Bool) -> some View {
        VStack(spacing: 0) {
            totalRow("Работа", totals.workedMs, workColor, compact: compact)
            totalRow("Переработка", totals.overtimeMs, overtimeColor, compact: compact)
            totalRow("Отдых", totals.restMs, restColor, compact: compact)
        }
    }

    private func totalRow(_ title: String, _ ms: Int, _ color: Color, compact: Bool) -> some View {
        HStack(spacing: 12) {
            Circle().fill(color).frame(width: 12, height: 12)
            Text(title).font(.system(size: 16))
            Spacer()
            Text(formatSpan(ms)).font(.system(size: 16, weight: .medium))
        }
        .padding(.horizontal)
        .padding(.vertical, compact ? 6 : 10)
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("История хранится только на устройстве и никуда не отправляется.")
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            Button("Очистить историю", role: .destructive) { confirmClear = true }
        }
        .padding(.horizontal)
        .padding(.top, 4)
        .padding(.bottom, 12)
    }

    private var datePickerSheet: some View {
        NavigationStack {
            ScrollView {
                DatePicker("Дата", selection: $date, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .environment(\.locale, Locale(identifier: "ru_RU"))
                    .environment(\.calendar, statsCalendar)
                    .tint(workColor)
                    .padding(.horizontal)
            }
            .navigationTitle("Выбор даты")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Сегодня") {
                        date = Date()
                        pickingDate = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { pickingDate = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func chartArea(_ totals: Totals, _ buckets: [Bucket]) -> some View {
        if totals.isEmpty {
            Text("За этот период записей нет")
                .font(.system(size: 14))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            chart(buckets)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// Столбики: работа и переработка идут одной стопкой, отдых — соседним столбиком.
    /// Слева шкала времени; тап или проведение пальцем по графику выбирает столбик
    /// и показывает подсказку с его значениями.
    private func chart(_ buckets: [Bucket]) -> some View {
        let peak = buckets.map(\.totals.maxMs).max() ?? 0
        let step = axisStepMs(peak)
        let ticks = max((peak + step - 1) / step, 1)
        let top = Double(step * ticks)
        let axisLabels = (0...ticks).map { formatAxis(step * $0) }
        let axisFont = UIFont.systemFont(ofSize: 10)
        let labelGap: CGFloat = 6
        let axisWidth: CGFloat = axisLabels
            .map { ceil(($0 as NSString).size(withAttributes: [.font: axisFont]).width) }
            .max() ?? 0
        let plotLeft: CGFloat = axisWidth + labelGap
        let plotTop: CGFloat = ceil(axisFont.lineHeight / 2)
        let xLabelsHeight: CGFloat = 18

        return GeometryReader { proxy in
            let plot = CGRect(
                x: plotLeft,
                y: plotTop,
                width: max(proxy.size.width - plotLeft, 1),
                height: max(proxy.size.height - plotTop - xLabelsHeight, 1)
            )
            let slot = plot.width / Double(max(buckets.count, 1))

            Canvas { context, _ in
                for tick in 0...ticks {
                    let y = plot.maxY - plot.height * Double(tick) / Double(ticks)
                    var line = Path()
                    line.move(to: CGPoint(x: plot.minX, y: y))
                    line.addLine(to: CGPoint(x: plot.maxX, y: y))
                    context.stroke(line, with: .color(.secondary.opacity(0.3)), lineWidth: 0.5)
                    context.draw(
                        Text(axisLabels[tick]).font(.system(size: 10)).foregroundStyle(Color.secondary),
                        at: CGPoint(x: plot.minX - labelGap, y: y),
                        anchor: .trailing
                    )
                }

                if let selected {
                    context.fill(
                        Path(CGRect(x: plot.minX + slot * Double(selected), y: plot.minY, width: slot, height: plot.height)),
                        with: .color(.primary.opacity(0.06))
                    )
                }

                let barWidth = slot * 0.34
                let gap = slot * 0.08

                for (index, bucket) in buckets.enumerated() {
                    let opacity = selected == nil || selected == index ? 1.0 : 0.4
                    let left = plot.minX + slot * Double(index) + (slot - barWidth * 2 - gap) / 2

                    func bar(_ x: Double, _ valueMs: Int, _ bottom: Double, _ color: Color) -> Double {
                        guard valueMs > 0 else { return bottom }
                        let height = plot.height * (Double(valueMs) / top)
                        context.fill(
                            Path(CGRect(x: x, y: bottom - height, width: barWidth, height: height)),
                            with: .color(color.opacity(opacity))
                        )
                        return bottom - height
                    }

                    let afterWork = bar(left, bucket.totals.workedMs, plot.maxY, workColor)
                    _ = bar(left, bucket.totals.overtimeMs, afterWork, overtimeColor)
                    _ = bar(left + barWidth + gap, bucket.totals.restMs, plot.maxY, restColor)

                    if bucket.emphasised {
                        context.draw(
                            Text(bucket.label).font(.system(size: 9)).foregroundStyle(Color.secondary),
                            at: CGPoint(x: left - (slot - barWidth * 2 - gap) / 2 + slot / 2, y: plot.maxY + 4),
                            anchor: .top
                        )
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let index = bucketIndex(at: value.location.x, plot: plot, slot: slot, count: buckets.count)
                        if !touching {
                            touching = true
                            selectionAtTouch = selected
                        }
                        selected = index
                    }
                    .onEnded { value in
                        let index = bucketIndex(at: value.location.x, plot: plot, slot: slot, count: buckets.count)
                        let isTap = abs(value.translation.width) < 6 && abs(value.translation.height) < 6
                        // Повторный тап по уже выбранному столбику убирает подсказку.
                        if isTap, selectionAtTouch == index { selected = nil }
                        touching = false
                    }
            )
            .overlay {
                if let selected, buckets.indices.contains(selected) {
                    let slotMinX = plot.minX + slot * Double(selected)
                    TooltipPlacement(
                        slotMinX: slotMinX,
                        slotMaxX: slotMinX + slot,
                        top: plot.minY,
                        toRight: slotMinX + slot / 2 < plot.midX
                    ) {
                        tooltip(buckets[selected])
                    }
                    .allowsHitTesting(false)
                }
            }
        }
    }

    private func bucketIndex(at x: Double, plot: CGRect, slot: Double, count: Int) -> Int {
        min(max(Int((x - plot.minX) / slot), 0), count - 1)
    }

    private func tooltip(_ bucket: Bucket) -> some View {
        let rows = [
            ("Работа", bucket.totals.workedMs, workColor),
            ("Переработка", bucket.totals.overtimeMs, overtimeColor),
            ("Отдых", bucket.totals.restMs, restColor),
        ].filter { $0.1 > 0 }

        return VStack(alignment: .leading, spacing: 3) {
            Text(bucket.title).font(.system(size: 12, weight: .semibold))
            if rows.isEmpty {
                Text("Нет записей").foregroundStyle(.secondary)
            }
            ForEach(rows, id: \.0) { name, ms, color in
                HStack(spacing: 6) {
                    Circle().fill(color).frame(width: 8, height: 8)
                    Text(name).foregroundStyle(.secondary)
                    Spacer(minLength: 12)
                    Text(formatSpan(ms)).fontWeight(.medium)
                }
            }
        }
        .font(.system(size: 12))
        .fixedSize(horizontal: true, vertical: true)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(.primary.opacity(0.12), lineWidth: 1))
    }
}

/// Подсказка встаёт сбоку от выбранного столбика, чтобы не закрывать его,
/// и не вылезает за края графика.
private struct TooltipPlacement: Layout {
    let slotMinX: CGFloat
    let slotMaxX: CGFloat
    let top: CGFloat
    let toRight: Bool

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let x = toRight ? slotMaxX + 8 : slotMinX - 8 - size.width
            let clamped = min(max(x, 0), max(bounds.width - size.width, 0))
            subview.place(
                at: CGPoint(x: bounds.minX + clamped, y: bounds.minY + top),
                proposal: ProposedViewSize(size)
            )
        }
    }
}
