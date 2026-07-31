package com.xai.dungeonmaster.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xai.dungeonmaster.client.models.EntitlementPayload

/**
 * Store / entitlements tab: list owned products, paste a receipt to verify,
 * or mint a local **dev** receipt (same HMAC scheme as the server's DevStorefront)
 * for an end-to-end purchase loop without Play Billing.
 */
@Composable
fun EntitlementsScreen(
    entitlements: EntitlementPayload?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onVerify: (productId: String, receipt: String, storefront: String) -> Unit,
    onDevPurchase: (productId: String) -> Unit,
) {
    var productId by remember { mutableStateOf("sku_gold") }
    var storefront by remember { mutableStateOf(DevReceipts.STOREFRONT_ID) }
    var receipt by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Entitlements",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Text("Owned products", style = MaterialTheme.typography.titleSmall)
                    val owned = entitlements?.owned.orEmpty()
                    if (owned.isEmpty()) {
                        Text(
                            "None yet — buy a dev SKU or verify a receipt.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        owned.forEach { sku ->
                            Text("• $sku", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    entitlements?.reason?.let {
                        Text("Last: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Dev purchase", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mints a signed test receipt locally (storefront “dev”) and posts it to " +
                            "POST /v2/entitlements/verify — same loop as a real store, without billing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = { Text("Product id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { if (productId.isNotBlank()) onDevPurchase(productId.trim()) },
                        enabled = !busy && productId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buy with dev receipt") }
                }
            }
        }

        item {
            Text("Verify arbitrary receipt", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = storefront,
                        onValueChange = { storefront = it },
                        label = { Text("Storefront id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = { Text("Product id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = receipt,
                        onValueChange = { receipt = it },
                        label = { Text("Receipt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = {
                            if (productId.isNotBlank() && receipt.isNotBlank()) {
                                onVerify(productId.trim(), receipt.trim(), storefront.trim().ifBlank { "dev" })
                            }
                        },
                        enabled = !busy && productId.isNotBlank() && receipt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Verify receipt") }
                }
            }
        }

        // Quick-pick SKUs for demos
        item { Text("Demo SKUs", style = MaterialTheme.typography.titleSmall) }
        items(listOf("sku_gold", "sku_season_pass", "pack_the_hollows")) { sku ->
            OutlinedButton(
                onClick = {
                    productId = sku
                    onDevPurchase(sku)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buy $sku (dev)") }
        }
    }
}
