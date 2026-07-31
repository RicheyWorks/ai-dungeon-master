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
                    Text("Content packs").font(.headline)
                    Spacer()
                    Button("Reload") { model.loadCatalog() }
                        .disabled(model.busy)
                }

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
                    Text("Tap Reload to fetch the catalog.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
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

    private var uploadCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Upload pack zip").font(.subheadline.bold())
            Text("POST /v2/catalog/packs — same endpoint as the web mod browser.")
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
                    .font(.headline)
                Text("v\(pack.version ?? "?") · \(pack.monsters ?? 0) monsters · \(pack.items ?? 0) items")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Toggle(
                "",
                isOn: Binding(
                    get: { pack.enabled == true },
                    set: { want in
                        if let id = pack.id {
                            model.togglePack(id: id, enable: want)
                        }
                    }
                )
            )
            .labelsHidden()
            .disabled(model.busy || pack.id == nil)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}
