import SwiftUI
import AIDungeonMasterClient
import UniformTypeIdentifiers

struct ModsTab: View {
    @ObservedObject var model: GameViewModel
    @State private var replace = false
    @State private var showImporter = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Marketplace").font(.headline)
                    Spacer()
                    Button("Reload") {
                        model.loadMarketplace()
                        model.loadCatalog()
                    }
                    .disabled(model.busy)
                }

                Text(marketSummary)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let job = model.installJob {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Install \(job.packId ?? "…") · \(job.phase ?? "…")")
                                .font(.subheadline.bold())
                            Spacer()
                            if job.phase != "DONE" && job.phase != "FAILED" && job.phase != "CANCELLED" {
                                Button("Cancel") { model.cancelMarketplaceInstall() }
                            }
                        }
                        ProgressView(value: Double(job.percent ?? 0), total: 100)
                        Text(
                            "\(job.percent ?? 0)%"
                            + ((job.bytesTotal ?? 0) > 0
                               ? " · \(job.bytesRead ?? 0) / \(job.bytesTotal ?? 0) bytes"
                               : "")
                            + (job.message.map { " · \($0)" } ?? "")
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                }

                HStack {
                    TextField("Search packs…", text: $model.marketQuery)
                        .textFieldStyle(.roundedBorder)
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        #endif
                    Button("Search") { model.loadMarketplace() }
                        .disabled(model.busy)
                }

                let packs = model.marketplace?.packs ?? []
                if model.marketplace != nil && packs.isEmpty {
                    Text("No marketplace packs match.")
                        .foregroundStyle(.secondary)
                }
                ForEach(packs) { pack in
                    marketCard(pack)
                }

                Text("Live catalog").font(.headline)
                uploadCard

                if let catalog = model.catalog {
                    ForEach(Array((catalog.contentPacks ?? []).enumerated()), id: \.offset) { _, pack in
                        packRow(pack)
                    }
                    if let narration = catalog.narration {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Narration").font(.headline)
                            Text("\(narration.active ?? "?") (\(narration.health ?? "UNKNOWN"))")
                            Text("Available: \((narration.available ?? []).joined(separator: ", "))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    }
                } else {
                    Text("Install a pack or start a session to load the live catalog.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
        }
        .onAppear {
            model.loadMarketplace()
            if model.catalog == nil { model.loadCatalog() }
        }
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.zip, .data],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                model.uploadPack(fileURL: url, replace: replace)
            case .failure(let error):
                model.error = error.localizedDescription
            }
        }
    }

    private var marketSummary: String {
        var s = "GET /v2/marketplace"
        if let root = model.marketplace?.root { s += " · \(root)" }
        if let m = model.marketplace {
            s += " · \(m.available ?? 0) available · \(m.installed ?? 0) installed"
            if let remote = m.remoteIndexUrl, !remote.isEmpty {
                s += " · remote \(m.remoteOk == true ? "OK" : "ERR")"
                if let err = m.remoteError, !err.isEmpty { s += " (\(err))" }
            }
        }
        return s
    }

    private func marketCard(_ pack: MarketplaceListing) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(pack.displayName ?? pack.id).font(.subheadline.bold())
                        Text((pack.source ?? "local").uppercased())
                            .font(.caption2.bold())
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                (pack.source == "remote" ? Color.orange.opacity(0.25) : Color.mint.opacity(0.25)),
                                in: Capsule()
                            )
                    }
                    Text(
                        "v\(pack.version ?? "?") · min \(pack.minEngineVersion ?? "?")"
                            + (pack.installed == true ? " · installed" : "")
                            + (pack.enabled == true ? " · enabled" : "")
                            + (pack.locked == true ? " · LOCKED" : "")
                            + ((pack.requiredProductIds?.isEmpty == false)
                                ? " · requires \((pack.requiredProductIds ?? []).joined(separator: " | "))"
                                : "")
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    if pack.source == "remote", let url = pack.downloadUrl, !url.isEmpty {
                        Text(url)
                            .font(.caption2)
                            .foregroundStyle(.tint)
                            .lineLimit(2)
                    }
                    if let sha = pack.sha256, !sha.isEmpty {
                        Text("sha256 \(Self.shortSha(sha))"
                             + (pack.source == "remote" ? " · verified on install" : ""))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                if pack.installed == true {
                    Text("Installed")
                        .font(.caption.bold())
                        .foregroundStyle(.mint)
                } else {
                    Button("Install") { model.installMarketplacePack(id: pack.id) }
                        .buttonStyle(.borderedProminent)
                        .disabled(model.busy)
                }
            }
            if let description = pack.description, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private static func shortSha(_ sha: String) -> String {
        let s = sha.trimmingCharacters(in: .whitespacesAndNewlines)
        guard s.count > 24 else { return s }
        return String(s.prefix(12)) + "…" + String(s.suffix(8))
    }

    private var uploadCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Upload pack zip").font(.subheadline.bold())
            Text("POST /v2/catalog/packs")
                .font(.caption)
                .foregroundStyle(.secondary)
            Toggle("Replace if exists", isOn: $replace)
                .disabled(model.busy)
            Button("Choose zip…") { showImporter = true }
                .buttonStyle(.borderedProminent)
                .disabled(model.busy)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func packRow(_ pack: PackInfo) -> some View {
        let locked = pack.locked == true
        let required = pack.requiredProductIds ?? []
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(pack.displayName ?? pack.id ?? "?")
                        .font(.subheadline.bold())
                    if locked {
                        Text("LOCKED")
                            .font(.caption2.bold())
                            .foregroundStyle(.red)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.red.opacity(0.12), in: Capsule())
                    }
                }
                Text(
                    buildPackMeta(pack, required: required)
                )
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Toggle(
                "",
                isOn: Binding(
                    get: { pack.enabled ?? false },
                    set: { enabled in
                        if let id = pack.id {
                            model.togglePack(id: id, enable: enabled)
                        }
                    }
                )
            )
            .labelsHidden()
            .disabled(model.busy || pack.id == nil || (locked && !(pack.enabled ?? false)))
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func buildPackMeta(_ pack: PackInfo, required: [String]) -> String {
        var s = "v\(pack.version ?? "?") · \(pack.monsters ?? 0) monsters · \(pack.items ?? 0) items"
        if !required.isEmpty {
            s += " · requires \(required.joined(separator: " | "))"
        }
        return s
    }
}
