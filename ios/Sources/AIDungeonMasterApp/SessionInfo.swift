import Foundation

/// Player identity returned by `POST /v2/session`.
public struct SessionInfo: Equatable, Sendable {
    public let sessionId: String
    public let token: String
    public let displayName: String
    public let expiresAtEpochSeconds: Int64
    public let createdAtEpochSeconds: Int64

    public init(
        sessionId: String,
        token: String,
        displayName: String,
        expiresAtEpochSeconds: Int64 = 0,
        createdAtEpochSeconds: Int64 = 0
    ) {
        self.sessionId = sessionId
        self.token = token
        self.displayName = displayName
        self.expiresAtEpochSeconds = expiresAtEpochSeconds
        self.createdAtEpochSeconds = createdAtEpochSeconds
    }

    /// Short id for chrome (first 8 chars).
    public var shortId: String {
        sessionId.count <= 8 ? sessionId : String(sessionId.prefix(8))
    }
}
