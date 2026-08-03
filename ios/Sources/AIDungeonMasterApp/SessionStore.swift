import Foundation

/// Persists guest session + preferred server URL across launches (UserDefaults).
public final class SessionStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let prefix: String

    public init(defaults: UserDefaults = .standard, prefix: String = "dm.") {
        self.defaults = defaults
        self.prefix = prefix
    }

    public func loadBaseURL(default defaultURL: String) -> String {
        let value = defaults.string(forKey: key("base_url"))
        if let value, !value.isEmpty { return value }
        return defaultURL
    }

    public func saveBaseURL(_ url: String) {
        defaults.set(url, forKey: key("base_url"))
    }

    public func loadSession() -> SessionInfo? {
        guard let id = defaults.string(forKey: key("session_id")),
              let token = defaults.string(forKey: key("token")),
              !id.isEmpty, !token.isEmpty else {
            return nil
        }
        return SessionInfo(
            sessionId: id,
            token: token,
            displayName: defaults.string(forKey: key("display_name")) ?? "Guest",
            expiresAtEpochSeconds: Int64(defaults.integer(forKey: key("expires_at"))),
            createdAtEpochSeconds: Int64(defaults.integer(forKey: key("created_at")))
        )
    }

    public func saveSession(_ info: SessionInfo) {
        defaults.set(info.sessionId, forKey: key("session_id"))
        defaults.set(info.token, forKey: key("token"))
        defaults.set(info.displayName, forKey: key("display_name"))
        defaults.set(Int(info.expiresAtEpochSeconds), forKey: key("expires_at"))
        defaults.set(Int(info.createdAtEpochSeconds), forKey: key("created_at"))
    }

    public func clearSession() {
        defaults.removeObject(forKey: key("session_id"))
        defaults.removeObject(forKey: key("token"))
        defaults.removeObject(forKey: key("display_name"))
        defaults.removeObject(forKey: key("expires_at"))
        defaults.removeObject(forKey: key("created_at"))
    }

    private func key(_ name: String) -> String { prefix + name }
}

extension SessionInfo {
    public func isExpired(nowEpochSeconds: Int64 = Int64(Date().timeIntervalSince1970)) -> Bool {
        if expiresAtEpochSeconds <= 0 { return false }
        return nowEpochSeconds >= (expiresAtEpochSeconds - 30)
    }

    /// Seconds until JWT expiry (0 if missing/expired).
    public func secondsUntilExpiry(nowEpochSeconds: Int64 = Int64(Date().timeIntervalSince1970)) -> Int64 {
        if expiresAtEpochSeconds <= 0 { return 0 }
        return max(0, expiresAtEpochSeconds - nowEpochSeconds)
    }
}
