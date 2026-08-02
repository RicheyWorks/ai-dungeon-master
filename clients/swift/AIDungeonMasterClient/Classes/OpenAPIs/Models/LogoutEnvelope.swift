//
// LogoutEnvelope.swift
// Hand-maintained for DELETE /v2/session
//

import Foundation
#if canImport(AnyCodable)
import AnyCodable
#endif

public struct LogoutPayload: Codable, JSONEncodable, Hashable {
    public var loggedOut: Bool?
    public var sessionId: String?

    public init(loggedOut: Bool? = nil, sessionId: String? = nil) {
        self.loggedOut = loggedOut
        self.sessionId = sessionId
    }

    public enum CodingKeys: String, CodingKey, CaseIterable {
        case loggedOut
        case sessionId
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(loggedOut, forKey: .loggedOut)
        try container.encodeIfPresent(sessionId, forKey: .sessionId)
    }
}

public struct LogoutEnvelope: Codable, JSONEncodable, Hashable {
    public var type: String?
    public var version: Int?
    public var payload: LogoutPayload?
    public var requestId: String?

    public init(type: String? = nil, version: Int? = nil, payload: LogoutPayload? = nil, requestId: String? = nil) {
        self.type = type
        self.version = version
        self.payload = payload
        self.requestId = requestId
    }

    public enum CodingKeys: String, CodingKey, CaseIterable {
        case type
        case version
        case payload
        case requestId
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(type, forKey: .type)
        try container.encodeIfPresent(version, forKey: .version)
        try container.encodeIfPresent(payload, forKey: .payload)
        try container.encodeIfPresent(requestId, forKey: .requestId)
    }
}
