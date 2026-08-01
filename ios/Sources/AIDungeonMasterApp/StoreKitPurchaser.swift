import Foundation
#if canImport(StoreKit)
import StoreKit
#endif

/// StoreKit 2 purchase helper that builds the JSON envelope for server `app_store` verify:
/// `{"receiptData":"<appStoreReceipt base64>","productId":"…"}`.
@MainActor
public final class StoreKitPurchaser: ObservableObject {
    @Published public var lastError: String?

    public init() {}

    public func purchase(productId: String) async -> (productId: String, receiptJson: String)? {
        #if canImport(StoreKit)
        if #available(iOS 15.0, macOS 12.0, *) {
            do {
                let products = try await Product.products(for: [productId])
                guard let product = products.first else {
                    lastError = "StoreKit product not found: \(productId)"
                    return nil
                }
                let result = try await product.purchase()
                switch result {
                case .success(let verification):
                    let transaction = try checkVerified(verification)
                    await transaction.finish()
                    let receiptData = bundleReceiptBase64() ?? ""
                    let json = """
                    {"receiptData":"\(receiptData)","productId":"\(productId)"}
                    """
                    return (productId, json)
                case .userCancelled:
                    lastError = "Purchase cancelled"
                    return nil
                case .pending:
                    lastError = "Purchase pending"
                    return nil
                @unknown default:
                    lastError = "Unknown purchase result"
                    return nil
                }
            } catch {
                lastError = error.localizedDescription
                return nil
            }
        }
        #endif
        lastError = "StoreKit unavailable on this platform"
        return nil
    }

    #if canImport(StoreKit)
    @available(iOS 15.0, macOS 12.0, *)
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let safe):
            return safe
        }
    }
    #endif

    private func bundleReceiptBase64() -> String? {
        guard let url = Bundle.main.appStoreReceiptURL,
              let data = try? Data(contentsOf: url) else {
            return nil
        }
        return data.base64EncodedString()
    }
}
