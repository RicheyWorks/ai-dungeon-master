import Foundation
import CommonCrypto

/// Client-side mint of sandbox receipts for server storefront plugins.
public enum DevReceipts {
    public static let storefrontDev = "dev"
    public static let storefrontGooglePlay = "google_play"
    public static let storefrontAppStore = "app_store"
    public static let storefrontSteam = "steam"

    /// Backward-compatible alias.
    public static let storefrontId = storefrontDev

    public static let secretDev = "dev-storefront-insecure-secret-change-me"
    public static let secretGoogle = "google-play-sandbox-insecure-secret"
    public static let secretApple = "app-store-sandbox-insecure-secret"
    public static let secretSteam = "steam-sandbox-insecure-secret"
    public static let defaultPackageName = "com.xai.dungeonmaster"

    public static let knownStorefronts = [
        storefrontDev, storefrontGooglePlay, storefrontAppStore, storefrontSteam,
    ]

    public static func secret(for storefront: String) -> String {
        switch storefront.lowercased() {
        case storefrontGooglePlay: return secretGoogle
        case storefrontAppStore: return secretApple
        case storefrontSteam: return secretSteam
        default: return secretDev
        }
    }


    public static func sign(productId: String, secret: String = secretDev) -> String {
        let productData = Data(productId.utf8)
        let body = base64url(productData)
        let sig = base64url(hmacSHA256(key: Data(secret.utf8), data: productData))
        return "\(body).\(sig)"
    }

    public struct Minted: Sendable {
        public let storefront: String
        public let productId: String
        public let receipt: String
    }

    public static func mint(
        storefront: String,
        productId: String,
        packageName: String = defaultPackageName
    ) -> Minted {
        let id = storefront.isEmpty ? storefrontDev : storefront.lowercased()
        let hmac = sign(productId: productId, secret: secret(for: id))
        let receipt: String
        switch id {
        case storefrontGooglePlay:
            receipt = #"{"packageName":\#(json(packageName)),"productId":\#(json(productId)),"purchaseToken":\#(json(hmac))}"#
        case storefrontAppStore:
            receipt = #"{"receiptData":\#(json(hmac)),"productId":\#(json(productId))}"#
        case storefrontSteam:
            receipt = #"{"orderId":\#(json(hmac)),"steamId":"76561198000000000","productId":\#(json(productId))}"#
        default:
            receipt = hmac
        }

        return Minted(storefront: id, productId: productId, receipt: receipt)
    }

    private static func json(_ s: String) -> String {
        let escaped = s
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }

    private static func base64url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func hmacSHA256(key: Data, data: Data) -> Data {
        var result = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        key.withUnsafeBytes { keyPtr in
            data.withUnsafeBytes { dataPtr in
                CCHmac(
                    CCHmacAlgorithm(kCCHmacAlgSHA256),
                    keyPtr.baseAddress, key.count,
                    dataPtr.baseAddress, data.count,
                    &result
                )
            }
        }
        return Data(result)
    }
}
