import SwiftUI

/// Root shell: Game / Mods / Store / System tabs, matching the Android client.
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
                        model.loadMarketplace()
                        if model.catalog == nil { model.loadCatalog() }
                    }
                StoreTab(model: model)
                    .tabItem { Label("Store", systemImage: "cart") }
                    .tag(2)
                    .onAppear {
                        if model.entitlements == nil { model.loadEntitlements() }
                    }
                SystemTab(model: model)
                    .tabItem { Label("System", systemImage: "heart.text.square") }
                    .tag(3)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            model.refresh()
            model.startHealthPolling()
        }
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
                    .foregroundStyle(sessionLineColor)
            } else if let ok = model.healthOk {
                Text(ok ? "Engine READY" : "Engine NOT READY")
                    .font(.caption)
                    .foregroundStyle(ok ? .mint : .red)
            }
        }
        .padding()
    }

    private func sessionLine(_ session: SessionInfo) -> String {
        var s = "Playing as \(session.displayName) · \(session.shortId)"
        if model.stompConnected { s += " · LIVE" }
        if let ok = model.healthOk {
            s += ok ? " · READY" : " · NOT READY"
        }
        return s
    }

    private var sessionLineColor: Color {
        if model.healthOk == false { return .red }
        if model.stompConnected { return .mint }
        return .accentColor
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
