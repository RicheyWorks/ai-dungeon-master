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
        }
        return s
    }

    private func marketCard(_ pack: MarketplaceListing) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(pack.displayName ?? pack.id).font(.subheadline.bold())
                    Text(
                        "v\(pack.version ?? "?") · min \(pack.minEngineVersion ?? "?")"
                            + (pack.installed == true ? " · installed" : "")
                            + (pack.enabled == true ? " · enabled" : "")
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
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
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(pack.displayName ?? pack.id ?? "?")
                    .font(.subheadline.bold())
                Text("v\(pack.version ?? "?") · \(pack.monsters ?? 0) monsters · \(pack.items ?? 0) items")
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
            .disabled(model.busy || pack.id == nil)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}
