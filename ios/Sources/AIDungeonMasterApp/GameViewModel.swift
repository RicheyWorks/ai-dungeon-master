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
    @Published public var marketplace: MarketplacePayload?
    @Published public var marketQuery: String = ""
    @Published public var installJob: MarketplaceInstallJob?
    @Published public var unlockSku: String? = nil
    @Published public var unlockHint: String? = nil
    @Published public var entitlements: EntitlementPayload?
    @Published public var readiness: ReadinessResponse?
    @Published public var health: HealthPayload?
    @Published public var healthOk: Bool?
    @Published public var healthError: String?
    @Published public var healthAt: Date?
    @Published public var lastSavePath: String?
    @Published public var busy: Bool = false
    @Published public var error: String?
    @Published public var info: String?

    private let store: SessionStore
    private var stomp: StompClient?
    private var healthTimer: Timer?

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
        startHealthPolling()
    }

    deinit {
        healthTimer?.invalidate()
    }

    public func startHealthPolling() {
        healthTimer?.invalidate()
        pollHealth()
        healthTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollHealth()
            }
        }
    }

    /// Public probes — no session. Soft-handles 503 readiness/health bodies.
    public func pollHealth() {
        let base = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let readyURL = URL(string: base + "/health/ready"),
              let healthURL = URL(string: base + "/v2/health") else {
            healthError = "Invalid base URL"
            healthOk = false
            return
        }
        Task {
            var readyBody: ReadinessResponse?
            var healthBody: HealthPayload?
            var readyOk = false
            var healthHttpOk = false
            var err: String?

            do {
                let (data, resp) = try await URLSession.shared.data(from: readyURL)
                readyOk = (resp as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
                readyBody = try? JSONDecoder().decode(ReadinessResponse.self, from: data)
                if !readyOk && readyBody == nil {
                    err = "readiness HTTP \((resp as? HTTPURLResponse)?.statusCode ?? -1)"
                }
            } catch {
                err = error.localizedDescription
            }

            do {
                let (data, resp) = try await URLSession.shared.data(from: healthURL)
                healthHttpOk = (resp as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
                if let env = try? JSONDecoder().decode(HealthEnvelope.self, from: data) {
                    healthBody = env.payload
                }
                if !healthHttpOk && healthBody == nil && err == nil {
                    err = "health HTTP \((resp as? HTTPURLResponse)?.statusCode ?? -1)"
                }
            } catch {
                if err == nil { err = error.localizedDescription }
            }

            await MainActor.run {
                self.readiness = readyBody
                self.health = healthBody
                self.healthOk = readyOk && healthHttpOk
                self.healthError = err
                self.healthAt = Date()
            }
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
        pollHealth()
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

    /// Explicit logout: server drops identity/packs/engine, then local wipe.
    public func logout() {
        run {
            self.disconnectStomp()
            if let token = self.session?.token, !token.isEmpty {
                self.applyBearer(token)
                try? await V2API.deleteSessionV2()
            }
            self.clearBearer()
            self.store.clearSession()
            self.session = nil
            self.status = nil
            self.catalog = nil
            self.entitlements = nil
            self.stompConnected = false
            self.error = nil
            self.info = "Logged out"
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

    public func loadMarketplace(query: String? = nil) {
        let q = query ?? marketQuery
        Task {
            do {
                let payload = try await Self.fetchMarketplace(baseURL: baseURL, query: q, token: session?.token)
                await MainActor.run {
                    self.marketQuery = q
                    self.marketplace = payload
                    self.info = "Marketplace: \(payload.available ?? 0) available"
                    self.error = nil
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                }
            }
        }
    }

    public func installMarketplacePack(id: String) {
        Task {
            do {
                let started = try await Self.startInstallAsync(baseURL: baseURL, id: id, token: session?.token)
                await MainActor.run {
                    self.installJob = started
                    self.info = "Installing \(id)…"
                    self.error = nil
                }
                let done = try await Self.pollInstall(
                    baseURL: baseURL,
                    jobId: started.jobId,
                    token: session?.token
                ) { job in
                    Task { @MainActor in self.installJob = job }
                }
                await MainActor.run { self.installJob = done }
                if done.phase == "DONE" {
                    let payload = try await Self.fetchMarketplace(baseURL: baseURL, query: marketQuery, token: session?.token)
                    await MainActor.run {
                        self.marketplace = payload
                        self.info = done.message ?? "Installed \(id)"
                        self.error = nil
                    }
                    run {
                        try await self.ensureSession()
                        let envelope = try await V2API.getCatalogV2()
                        self.catalog = envelope.payload
                    }
                } else if done.phase == "CANCELLED" {
                    await MainActor.run { self.info = done.message ?? "Install cancelled" }
                } else {
                    await MainActor.run {
                        self.error = done.error ?? done.message ?? "Install failed"
                    }
                }
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                }
            }
        }
    }

    public func cancelMarketplaceInstall() {
        guard let jobId = installJob?.jobId else { return }
        Task {
            do {
                let j = try await Self.cancelInstall(baseURL: baseURL, jobId: jobId, token: session?.token)
                await MainActor.run {
                    if let j { self.installJob = j }
                    self.info = "Cancel requested"
                }
            } catch {
                await MainActor.run { self.error = error.localizedDescription }
            }
        }
    }

    private static func fetchMarketplace(baseURL: String, query: String, token: String?) async throws -> MarketplacePayload {
        var urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/v2/marketplace"
        if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let enc = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
            urlString += "?q=\(enc)"
        }
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        let env = try JSONDecoder().decode(MarketplaceEnvelope.self, from: data)
        return env.payload ?? MarketplacePayload(root: nil, available: 0, installed: 0, packs: [])
    }

    private static func startInstallAsync(baseURL: String, id: String, token: String?) async throws -> MarketplaceInstallJob {
        let encId = id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id
        let urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            + "/v2/marketplace/\(encId)/install?async=true"
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let http = resp as? HTTPURLResponse
        guard let http, (200..<300).contains(http.statusCode) else {
            if let err = try? JSONDecoder().decode(ErrorPayloadEnvelope.self, from: data),
               let msg = err.payload?.message {
                throw NSError(domain: "marketplace", code: http?.statusCode ?? -1,
                              userInfo: [NSLocalizedDescriptionKey: msg])
            }
            throw URLError(.badServerResponse)
        }
        let env = try JSONDecoder().decode(MarketplaceInstallJobEnvelope.self, from: data)
        guard let job = env.payload else { throw URLError(.cannotParseResponse) }
        return job
    }

    private static func getInstallJob(baseURL: String, jobId: String, token: String?) async throws -> MarketplaceInstallJob {
        let enc = jobId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? jobId
        let urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            + "/v2/marketplace/jobs/\(enc)"
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        let env = try JSONDecoder().decode(MarketplaceInstallJobEnvelope.self, from: data)
        guard let job = env.payload else { throw URLError(.cannotParseResponse) }
        return job
    }

    private static func cancelInstall(baseURL: String, jobId: String, token: String?) async throws -> MarketplaceInstallJob? {
        let enc = jobId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? jobId
        let urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            + "/v2/marketplace/jobs/\(enc)"
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.httpMethod = "DELETE"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try? JSONDecoder().decode(MarketplaceInstallJobEnvelope.self, from: data).payload
    }

    private static func pollInstall(
        baseURL: String,
        jobId: String,
        token: String?,
        onProgress: @escaping (MarketplaceInstallJob) -> Void
    ) async throws -> MarketplaceInstallJob {
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            let job = try await getInstallJob(baseURL: baseURL, jobId: jobId, token: token)
            onProgress(job)
            if let phase = job.phase, ["DONE", "FAILED", "CANCELLED"].contains(phase) {
                return job
            }
            try await Task.sleep(nanoseconds: 400_000_000)
        }
        throw NSError(domain: "marketplace", code: -1,
                      userInfo: [NSLocalizedDescriptionKey: "install timed out"])
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

    public func requestUnlock(sku: String, packLabel: String?) {
        unlockSku = sku
        if let packLabel, !packLabel.isEmpty {
            unlockHint = "Unlock \"\(packLabel)\" with \(sku)"
        } else {
            unlockHint = "Unlock with \(sku)"
        }
        info = "Store ready — buy \(sku) to unlock."
        loadEntitlements()
    }

    public func clearUnlockHint() {
        unlockSku = nil
        unlockHint = nil
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
                storefront: storefront.isEmpty ? DevReceipts.storefrontDev : storefront,
                productId: productId,
                receipt: receipt
            )
            do {
                let envelope = try await V2API.verifyReceiptV2(verifyReceiptRequest: req)
                self.entitlements = envelope.payload
                if envelope.payload.granted == true {
                    let packs = envelope.payload.enabledPacks ?? []
                    if packs.isEmpty {
                        self.info = "Granted \(envelope.payload.productId ?? productId)"
                    } else {
                        self.info = "Granted \(envelope.payload.productId ?? productId); enabled packs: \(packs.joined(separator: ", "))"
                        // Refresh catalog so Mods reflects auto-enabled packs.
                        if let cat = try? await V2API.getCatalogV2() {
                            self.catalog = cat.payload
                        }
                    }
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

    public func sandboxPurchase(productId: String, storefront: String = DevReceipts.storefrontDev) {
        let minted = DevReceipts.mint(storefront: storefront, productId: productId)
        verifyReceipt(productId: minted.productId, receipt: minted.receipt, storefront: minted.storefront)
        clearUnlockHint()
    }

    public func devPurchase(productId: String) {
        sandboxPurchase(productId: productId, storefront: DevReceipts.storefrontDev)
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
