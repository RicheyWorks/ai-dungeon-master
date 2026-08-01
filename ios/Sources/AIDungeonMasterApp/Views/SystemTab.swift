import SwiftUI
import AIDungeonMasterClient

/// System health tab — public readiness / metrics probes (no session).
struct SystemTab: View {
    @ObservedObject var model: GameViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("System health").font(.headline)
                    Spacer()
                    Button("Refresh") { model.pollHealth() }
                }

                Text("Public probes via /health/ready and /v2/health (no session). Auto-refreshes every 15s.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                statusCard
                metricsCard
                depsCard
            }
            .padding()
        }
        .onAppear { model.pollHealth() }
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Status").font(.subheadline.bold())
                Text(statusLabel)
                    .font(.subheadline.bold())
                    .foregroundStyle(statusColor)
            }
            if let err = model.healthError {
                Text(err).font(.caption).foregroundStyle(.red)
            }
            Text("Base: \(model.baseURL)")
                .font(.caption)
                .foregroundStyle(.secondary)
            if let at = model.healthAt {
                Text("Checked \(at.formatted(date: .omitted, time: .standard))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var metricsCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Metrics").font(.subheadline.bold())
            Text("Sessions: \(model.health?.sessions.map(String.init) ?? model.readiness?.sessions.map(String.init) ?? "—")")
            Text("Engines: \(model.health?.engines.map(String.init) ?? model.readiness?.engines.map(String.init) ?? "—")")
            Text("Uptime: \(formatUptime(model.health?.uptimeSeconds))")
            if let mem = model.health?.memory {
                Text("Heap free \(fmtBytes(mem.freeBytes)) / total \(fmtBytes(mem.totalBytes)) (max \(fmtBytes(mem.maxBytes)))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var depsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Dependencies").font(.subheadline.bold())
            let deps = model.readiness?.dependencies ?? model.health?.dependencies ?? [:]
            if deps.isEmpty {
                Text("No dependency data yet — hit Refresh.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(deps.keys.sorted(), id: \.self) { name in
                    let check = deps[name]
                    HStack {
                        Text(name)
                        Spacer()
                        Text(depLabel(check))
                            .foregroundStyle(depColor(check))
                    }
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var statusLabel: String {
        switch model.healthOk {
        case true: return "UP"
        case false: return "DOWN"
        case nil: return "…"
        }
    }

    private var statusColor: Color {
        switch model.healthOk {
        case true: return .mint
        case false: return .red
        case nil: return .secondary
        }
    }

    private func depLabel(_ check: DependencyCheck?) -> String {
        guard let check else { return "?" }
        var s = check.status.rawValue
        if let detail = check.detail, !detail.isEmpty {
            s += " · \(detail)"
        }
        return s
    }

    private func depColor(_ check: DependencyCheck?) -> Color {
        switch check?.status {
        case .up: return .mint
        case .down: return .red
        default: return .secondary
        }
    }

    private func formatUptime(_ seconds: Int64?) -> String {
        guard let seconds else { return "—" }
        let s = max(0, Int(seconds))
        let h = s / 3600
        let m = (s % 3600) / 60
        let r = s % 60
        if h > 0 { return "\(h)h \(m)m" }
        if m > 0 { return "\(m)m \(r)s" }
        return "\(r)s"
    }

    private func fmtBytes(_ n: Int64?) -> String {
        guard let n else { return "—" }
        let v = Double(n)
        if v < 1024 { return "\(n) B" }
        if v < 1024 * 1024 { return "\(Int(v / 1024)) KB" }
        if v < 1024 * 1024 * 1024 { return String(format: "%.1f MB", v / (1024 * 1024)) }
        return String(format: "%.2f GB", v / (1024 * 1024 * 1024))
    }
}
