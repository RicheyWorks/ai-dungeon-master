import SwiftUI

/// Root shell: Game / Mods / Store tabs, matching the Android v1 client.
public struct ContentView: View {
    @StateObject private var model = GameViewModel()
    @State private var tab = 0

    public init() {}

    public var body: some View {
        VStack(spacing: 0) {
            serverBar
            statusBanner
            TabView(selection: $tab) {
                GameTab(model: model)
                    .tabItem { Label("Game", systemImage: "shield.lefthalf.filled") }
                    .tag(0)
                ModsTab(model: model)
                    .tabItem { Label("Mods", systemImage: "shippingbox") }
                    .tag(1)
                    .onAppear {
                        if model.catalog == nil { model.loadCatalog() }
                    }
                StoreTab(model: model)
                    .tabItem { Label("Store", systemImage: "cart") }
                    .tag(2)
                    .onAppear {
                        if model.entitlements == nil { model.loadEntitlements() }
                    }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { model.refresh() }
    }

    private var serverBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("Server", text: Binding(
                    get: { model.baseURL },
                    set: { model.setBaseURL($0) }
                ))
                .textFieldStyle(.roundedBorder)
                #if os(iOS)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                #endif
                if model.busy {
                    ProgressView()
                } else {
                    Button("Sync") { model.refresh() }
                        .buttonStyle(.bordered)
                }
            }
            HStack {
                Button(model.session == nil ? "Start session" : "New session") {
                    model.startSession()
                }
                .buttonStyle(.bordered)
                .disabled(model.busy)
            }
            if let session = model.session {
                Text(sessionLine(session))
                    .font(.caption)
                    .foregroundStyle(model.stompConnected ? .mint : .accentColor)
            }
        }
        .padding()
    }

    private func sessionLine(_ session: SessionInfo) -> String {
        var s = "Playing as \(session.displayName) · \(session.shortId)"
        if model.stompConnected { s += " · LIVE" }
        return s
    }

    private var statusBanner: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let info = model.info {
                Text(info)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
            }
            if let error = model.error {
                Text("Error: \(error)")
                    .font(.caption)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
            }
        }
    }
}
