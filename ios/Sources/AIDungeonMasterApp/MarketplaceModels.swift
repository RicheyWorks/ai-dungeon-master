import Foundation
import AIDungeonMasterClient

struct MarketplaceListing: Codable, Identifiable, Hashable {
    let id: String
    let displayName: String?
    let version: String?
    let minEngineVersion: String?
    let description: String?
    let installed: Bool?
    let enabled: Bool?
    let requiredProductIds: [String]?
    let locked: Bool?
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

// MARK: - Map generated SDK models → app UI models

extension AIDungeonMasterClient.MarketplacePayload {
    func toApp() -> MarketplacePayload {
        MarketplacePayload(
            root: root,
            remoteIndexUrl: remoteIndexUrl,
            remoteOk: remoteOk,
            remoteError: remoteError,
            available: available,
            installed: installed,
            packs: packs?.map { $0.toApp() }
        )
    }
}

extension AIDungeonMasterClient.MarketplaceListing {
    func toApp() -> MarketplaceListing {
        MarketplaceListing(
            id: id,
            displayName: displayName,
            version: version,
            minEngineVersion: minEngineVersion,
            description: description,
            installed: installed,
            enabled: enabled,
            requiredProductIds: nil,
            locked: nil,
            sourcePath: sourcePath,
            source: source.rawValue,
            downloadUrl: downloadUrl,
            sha256: sha256
        )
    }
}

extension AIDungeonMasterClient.MarketplaceInstallJob {
    func toApp() -> MarketplaceInstallJob {
        MarketplaceInstallJob(
            jobId: jobId,
            packId: packId,
            phase: phase.rawValue,
            bytesRead: bytesRead,
            bytesTotal: bytesTotal,
            percent: percent,
            message: message,
            cancelRequested: cancelRequested,
            error: error
        )
    }
}
