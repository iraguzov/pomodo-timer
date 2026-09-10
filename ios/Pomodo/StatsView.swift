import SwiftUI

struct StatsView: View {

    let history: HistoryStore
    let settings: SettingsStore

    @Environment(\.dismiss) private var dismiss
    @State private var period: StatsPeriod = .week
    @State private var confirmClear = false

    private var workColor: Color { Color(rgb: settings.progressColor) }
    private var overtimeColor: Color { oppositeOf(workColor) }
    private var restColor: Color { .secondary }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Picker("Период", selection: $period) {
                        ForEach(StatsPeriod.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal)
                    .padding(.bottom, 20)

                    let totals = totalsFor(history.sessions, period: period)

                    totalRow("Работа", totals.workedMs, workColor)
                    totalRow("Переработка", totals.overtimeMs, overtimeColor)
                    totalRow("Отдых", totals.restMs, restColor)

                    if totals.isEmpty {
                        Text("За этот период записей нет")
                            .font(.system(size: 14))
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 48)
                    } else {
                        chart(bucketsFor(history.sessions, period: period))
                            .padding(.top, 24)
                    }

                    Text("История хранится только на устройстве и никуда не отправляется.")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                        .padding(.top, 32)

                    Button("Очистить историю", role: .destructive) { confirmClear = true }
                        .padding(.horizontal)
                        .padding(.top, 8)
                        .padding(.bottom, 24)
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
        }
    }

    private func totalRow(_ title: String, _ ms: Int, _ color: Color) -> some View {
        HStack(spacing: 12) {
            Circle().fill(color).frame(width: 12, height: 12)
            Text(title).font(.system(size: 16))
            Spacer()
            Text(formatSpan(ms)).font(.system(size: 16, weight: .medium))
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
    }

    /// Столбики: работа и переработка идут одной стопкой, отдых — соседним столбиком.
    private func chart(_ buckets: [Bucket]) -> some View {
        let peak = buckets.map(\.totals.maxMs).max() ?? 0

        return VStack(spacing: 4) {
            Canvas { context, size in
                guard peak > 0, !buckets.isEmpty else { return }
                let slot = size.width / Double(buckets.count)
                let barWidth = slot * 0.34
                let gap = slot * 0.08

                for (index, bucket) in buckets.enumerated() {
                    let left = slot * Double(index) + (slot - barWidth * 2 - gap) / 2

                    func bar(_ x: Double, _ valueMs: Int, _ bottom: Double, _ color: Color) -> Double {
                        guard valueMs > 0 else { return bottom }
                        let height = size.height * (Double(valueMs) / Double(peak))
                        context.fill(
                            Path(CGRect(x: x, y: bottom - height, width: barWidth, height: height)),
                            with: .color(color)
                        )
                        return bottom - height
                    }

                    let afterWork = bar(left, bucket.totals.workedMs, size.height, workColor)
                    _ = bar(left, bucket.totals.overtimeMs, afterWork, overtimeColor)
                    _ = bar(left + barWidth + gap, bucket.totals.restMs, size.height, restColor)
                }
            }
            .frame(height: 180)

            HStack(spacing: 0) {
                ForEach(buckets) { bucket in
                    Text(bucket.emphasised ? bucket.label : "")
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal)
    }
}
