import Foundation
import CommonCrypto

/// Client-side mint of DevStorefront test receipts (same HMAC scheme as the server).
public enum DevReceipts {
    public static let storefrontId = "dev"
    public static let defaultSecret = "dev-storefront-insecure-secret-change-me"

    public static func sign(productId: String, secret: String = defaultSecret) -> String {
        let productData = Data(productId.utf8)
        let body = base64url(productData)
        let sig = base64url(hmacSHA256(key: Data(secret.utf8), data: productData))
        return "\(body).\(sig)"
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
