import Foundation
import AIDungeonMasterClient

/// Observable app state: session, game status, catalog, entitlements, STOMP stream.
@MainActor
public final class GameViewModel: ObservableObject {
    public static let defaultBaseURL = "http://127.0.0.1:8080"

    @Published public var baseURL: String
    @Published public var session: SessionInfo?
    @Published public var status: GameStatusV2?
    @Published public var narration: String?
    @Published public var streamBuffer: String = ""
    @Published public var stompConnected: Bool = false
    @Published public var catalog: CatalogPayload?
    @Published public var entitlements: EntitlementPayload?
    @Published public var lastSavePath: String?
    @Published public var busy: Bool = false
    @Published public var error: String?
    @Published public var info: String?

    private let store: SessionStore
    private var stomp: StompClient?

    public init(store: SessionStore = SessionStore()) {
        self.store = store
        let url = store.loadBaseURL(default: GameViewModel.defaultBaseURL)
        self.baseURL = url
        if let saved = store.loadSession(), !saved.isExpired() {
            self.session = saved
            AIDungeonMasterClientAPI.basePath = url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            AIDungeonMasterClientAPI.customHeaders["Authorization"] = "Bearer \(saved.token)"
            self.info = "Restored session \(saved.shortId) · \(saved.displayName)"
        } else {
            if store.loadSession() != nil {
                store.clearSession()
            }
            self.session = nil
        }
    }

    public func setBaseURL(_ url: String) {
        store.saveBaseURL(url)
        let trimmed = url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard trimmed != baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) else {
            baseURL = url
            return
        }
        disconnectStomp()
        clearBearer()
        store.clearSession()
        baseURL = url
        session = nil
        status = nil
        stompConnected = false
        info = "Server changed — new session on next sync"
    }

    public func refresh() {
        run {
            try await self.ensureSession()
            self.connectStomp()
            let envelope = try await V2API.getStatusV2()
            self.status = envelope.payload
        }
    }

    public func startSession(displayName: String? = nil) {
        run {
            self.disconnectStomp()
            self.store.clearSession()
            let info = try await self.mintSession(displayName: displayName)
            self.session = info
            self.applyBearer(info.token)
            self.store.saveSession(info)
            self.connectStomp()
            self.info = "Session \(info.shortId) · \(info.displayName)"
        }
    }

    public func act(choiceLabel: String) {
        run {
            try await self.ensureSession()
            self.connectStomp()
            if let stomp = self.stomp, stomp.isConnected {
                let body = #"{"choiceLabel":\#(Self.jsonString(choiceLabel))}"#
                stomp.send(destination: "/app/action", body: body)
                let envelope = try await V2API.getStatusV2()
                self.status = envelope.payload
                self.info = "Action sent via WS"
            } else {
                let req = ActionRequest(choiceLabel: choiceLabel)
                let envelope = try await V2API.submitActionV2(actionRequest: req)
                self.status = envelope.payload
            }
        }
    }

    public func narrate(prompt: String) {
        run {
            try await self.ensureSession()
            self.connectStomp()
            if let stomp = self.stomp, stomp.isConnected {
                self.streamBuffer = ""
                self.narration = nil
                let body = #"{"prompt":\#(Self.jsonString(prompt))}"#
                stomp.send(destination: "/app/narrate", body: body)
                self.info = "Streaming narration…"
            } else {
                let req = NarrateRequest(prompt: prompt)
                let envelope = try await V2API.narrateV2(narrateRequest: req)
                self.narration = envelope.payload.text
                self.info = "REST narrate"
            }
        }
    }

    public func loadCatalog() {
        run {
            try await self.ensureSession()
            let envelope = try await V2API.getCatalogV2()
            self.catalog = envelope.payload
        }
    }

    public func togglePack(id: String, enable: Bool) {
        run {
            try await self.ensureSession()
            let envelope = enable
                ? try await V2API.enablePackV2(id: id)
                : try await V2API.disablePackV2(id: id)
            self.catalog = envelope.payload
        }
    }

    public func uploadPack(fileURL: URL, replace: Bool) {
        run {
            try await self.ensureSession()
            let data = try Data(contentsOf: fileURL)
            let temp = FileManager.default.temporaryDirectory
                .appendingPathComponent("upload-\(UUID().uuidString).zip")
            try data.write(to: temp)
            defer { try? FileManager.default.removeItem(at: temp) }
            let envelope = try await V2API.uploadPackV2(file: temp, replace: replace)
            self.catalog = envelope.payload
            self.info = replace ? "Pack replaced" : "Pack uploaded"
        }
    }

    public func loadEntitlements() {
        run {
            try await self.ensureSession()
            let envelope = try await V2API.listEntitlementsV2()
            self.entitlements = envelope.payload
        }
    }

    public func verifyReceipt(productId: String, receipt: String, storefront: String) {
        run {
            try await self.ensureSession()
            let req = VerifyReceiptRequest(
                storefront: storefront.isEmpty ? DevReceipts.storefrontId : storefront,
                productId: productId,
                receipt: receipt
            )
            do {
                let envelope = try await V2API.verifyReceiptV2(verifyReceiptRequest: req)
                self.entitlements = envelope.payload
                if envelope.payload.granted == true {
                    self.info = "Granted \(envelope.payload.productId ?? productId)"
                } else {
                    self.info = "Not granted: \(envelope.payload.reason ?? "")"
                }
            } catch {
                self.error = error.localizedDescription
                if let listed = try? await V2API.listEntitlementsV2() {
                    self.entitlements = listed.payload
                }
            }
        }
    }

    public func devPurchase(productId: String) {
        let receipt = DevReceipts.sign(productId: productId)
        verifyReceipt(productId: productId, receipt: receipt, storefront: DevReceipts.storefrontId)
    }

    public func saveGame() {
        run {
            try await self.ensureSession()
            let envelope = try await V2API.saveGameV2()
            let p = envelope.payload
            self.lastSavePath = p.path
            if p.saved == true {
                self.info = p.sessionScoped == true ? "Saved (session)" : "Saved"
            } else {
                self.info = "Save failed"
            }
        }
    }

    public func loadGame() {
        run {
            try await self.ensureSession()
            let envelope = try await V2API.loadGameV2()
            self.status = envelope.payload
            self.info = "Loaded save"
        }
    }

    public func resetGame() {
        run {
            try await self.ensureSession()
            let envelope = try await V2API.resetGameV2()
            self.status = envelope.payload
            self.info = "New adventure started"
        }
    }

    // MARK: - Session / auth

    private func mintSession(displayName: String?) async throws -> SessionInfo {
        applyBasePath()
        clearBearer()
        let req = displayName.flatMap { $0.isEmpty ? nil : SessionRequest(displayName: $0) }
        let envelope = try await V2API.createSessionV2(sessionRequest: req)
        let p = envelope.payload
        guard let token = p.token, !token.isEmpty else {
            throw NSError(domain: "AIDungeonMaster", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Session token missing from createSessionV2",
            ])
        }
        return SessionInfo(
            sessionId: p.sessionId,
            token: token,
            displayName: p.displayName,
            expiresAtEpochSeconds: p.expiresAtEpochSeconds ?? 0,
            createdAtEpochSeconds: p.createdAtEpochSeconds ?? 0
        )
    }

    private func ensureSession() async throws {
        applyBasePath()
        var candidate = session
        if candidate == nil || AIDungeonMasterClientAPI.customHeaders["Authorization"] == nil {
            if let fromDisk = store.loadSession(), !fromDisk.isExpired() {
                candidate = fromDisk
                applyBearer(fromDisk.token)
            }
        }

        if let candidate, !candidate.isExpired(),
           AIDungeonMasterClientAPI.customHeaders["Authorization"] != nil {
            do {
                _ = try await V2API.getSessionMeV2()
                session = candidate
                store.saveSession(candidate)
                return
            } catch {
                // Stale JWT — mint below.
            }
        }

        store.clearSession()
        let info = try await mintSession(displayName: candidate?.displayName)
        session = info
        applyBearer(info.token)
        store.saveSession(info)
        self.info = "New session \(info.shortId)"
    }

    private func applyBasePath() {
        let trimmed = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        AIDungeonMasterClientAPI.basePath = trimmed
    }

    private func applyBearer(_ token: String) {
        AIDungeonMasterClientAPI.customHeaders["Authorization"] = "Bearer \(token)"
    }

    private func clearBearer() {
        AIDungeonMasterClientAPI.customHeaders.removeValue(forKey: "Authorization")
    }

    // MARK: - STOMP

    private func connectStomp() {
        if let stomp, stomp.isConnected { return }
        guard let session else { return }
        disconnectStomp()
        guard let url = StompClient.stompURL(httpBase: baseURL) else {
            error = "Invalid STOMP URL"
            return
        }
        let client = StompClient(wsURL: url, token: session.token, listener: self)
        stomp = client
        client.connect()
    }

    private func disconnectStomp() {
        stomp?.disconnect()
        stomp = nil
        stompConnected = false
    }

    private func handleStompBody(_ body: String) {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        if trimmed.hasPrefix("{"),
           let data = trimmed.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let type = obj["type"] as? String {
            let payload = obj["payload"] as? [String: Any]
            switch type {
            case "narrative_chunk":
                let chunk = (payload?["chunk"] as? String) ?? (payload?["text"] as? String) ?? ""
                streamBuffer += chunk
                info = nil
                return
            case "narrative_update":
                let text = (payload?["text"] as? String)
                    ?? (streamBuffer.isEmpty ? nil : streamBuffer)
                if let text {
                    narration = text
                    streamBuffer = ""
                    info = "Narration complete"
                }
                return
            default:
                break
            }
        }

        if trimmed.hasPrefix("[WS]") {
            info = trimmed
        } else {
            narration = [narration, trimmed].compactMap { $0 }.joined(separator: "\n")
        }
    }

    // MARK: - helpers

    private func run(_ work: @escaping () async throws -> Void) {
        busy = true
        error = nil
        Task {
            do {
                try await work()
            } catch {
                self.error = error.localizedDescription
            }
            self.busy = false
        }
    }

    private static func jsonString(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }
}

extension GameViewModel: StompClientListener {
    public func stompDidConnect(_ client: StompClient) {
        client.subscribe(destination: "/topic/narrative")
        if let session {
            client.subscribe(destination: "/topic/narrative/\(session.sessionId)")
        }
        stompConnected = true
        info = "Live stream connected"
    }

    public func stomp(_ client: StompClient, didReceive destination: String, body: String) {
        handleStompBody(body)
    }

    public func stomp(_ client: StompClient, didFail message: String) {
        stompConnected = false
        error = "WS: \(message)"
    }

    public func stompDidClose(_ client: StompClient) {
        stompConnected = false
    }
}
