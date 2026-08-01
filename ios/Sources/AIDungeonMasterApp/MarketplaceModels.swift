import Foundation

struct MarketplaceListing: Codable, Identifiable, Hashable {
    let id: String
    let displayName: String?
    let version: String?
    let minEngineVersion: String?
    let description: String?
    let installed: Bool?
    let enabled: Bool?
    let sourcePath: String?
    /// `local` or `remote`
    let source: String?
    let downloadUrl: String?
    let sha256: String?
}

struct MarketplacePayload: Codable {
    let root: String?
    let remoteIndexUrl: String?
    let remoteOk: Bool?
    let remoteError: String?
    let available: Int?
    let installed: Int?
    let packs: [MarketplaceListing]?
}

struct MarketplaceEnvelope: Codable {
    let type: String?
    let payload: MarketplacePayload?
}

struct MarketplaceInstallPayload: Codable {
    let packId: String?
    let alreadyInstalled: Bool?
    let message: String?
}

struct MarketplaceInstallEnvelope: Codable {
    let type: String?
    let payload: MarketplaceInstallPayload?
}

struct ErrorPayloadEnvelope: Codable {
    struct Payload: Codable { let message: String? }
    let payload: Payload?
}

struct MarketplaceInstallJob: Codable, Hashable {
    let jobId: String
    let packId: String?
    let phase: String?
    let bytesRead: Int64?
    let bytesTotal: Int64?
    let percent: Int?
    let message: String?
    let cancelRequested: Bool?
    let error: String?
}

struct MarketplaceInstallJobEnvelope: Codable {
    let type: String?
    let payload: MarketplaceInstallJob?
}
