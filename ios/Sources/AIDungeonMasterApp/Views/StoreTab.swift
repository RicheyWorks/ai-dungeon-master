import SwiftUI

struct StoreTab: View {
    @ObservedObject var model: GameViewModel
    @State private var productId = "sku_gold"
    @State private var storefront = DevReceipts.storefrontDev
    @State private var receipt = ""

    private let demoSkus = ["sku_gold", "sku_season_pass", "pack_the_hollows"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Entitlements").font(.headline)
                    Spacer()
                    Button("Refresh") { model.loadEntitlements() }
                        .disabled(model.busy)
                }

                ownedCard
                sandboxPurchaseCard
                verifyCard
                demoSection
            }
            .padding()
        }
    }

    private var ownedCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Owned products").font(.subheadline.bold())
            let owned = model.entitlements?.owned ?? []
            if owned.isEmpty {
                Text("None yet — sandbox-buy a SKU or verify a receipt.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(owned, id: \.self) { sku in
                    Text("• \(sku)")
                }
            }
            if let reason = model.entitlements?.reason {
                Text("Last: \(reason)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var sandboxPurchaseCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sandbox purchase").font(.subheadline.bold())
            Text("Mints a storefront-shaped receipt (HMAC sandbox for google_play / app_store JSON envelopes) and posts it to POST /v2/entitlements/verify.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Picker("Storefront", selection: $storefront) {
                ForEach(DevReceipts.knownStorefronts, id: \.self) { id in
                    Text(id).tag(id)
                }
            }
            .pickerStyle(.segmented)
            TextField("Product id", text: $productId)
                .textFieldStyle(.roundedBorder)
            Button("Buy with \(storefront) sandbox receipt") {
                model.sandboxPurchase(
                    productId: productId.trimmingCharacters(in: .whitespacesAndNewlines),
                    storefront: storefront
                )
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.busy || productId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .frame(maxWidth: .infinity)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var verifyCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Verify arbitrary receipt").font(.subheadline.bold())
            TextField("Storefront id", text: $storefront)
                .textFieldStyle(.roundedBorder)
            TextField("Product id", text: $productId)
                .textFieldStyle(.roundedBorder)
            TextField("Receipt", text: $receipt, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(2...4)
            Button("Verify receipt") {
                model.verifyReceipt(
                    productId: productId.trimmingCharacters(in: .whitespacesAndNewlines),
                    receipt: receipt.trimmingCharacters(in: .whitespacesAndNewlines),
                    storefront: storefront.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.busy || productId.isEmpty || receipt.isEmpty)
            .frame(maxWidth: .infinity)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var demoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Demo SKUs").font(.subheadline)
            ForEach(demoSkus, id: \.self) { sku in
                Button("Buy \(sku) (\(storefront))") {
                    productId = sku
                    model.sandboxPurchase(productId: sku, storefront: storefront)
                }
                .buttonStyle(.bordered)
                .disabled(model.busy)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
