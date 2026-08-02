package com.xai.dungeonmaster.android

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xai.dungeonmaster.client.models.EntitlementPayload

/**
 * Store / entitlements: sandbox HMAC, live Play Billing, or paste-a-receipt.
 */
@Composable
fun EntitlementsScreen(
    entitlements: EntitlementPayload?,
    busy: Boolean,
    initialProductId: String? = null,
    unlockHint: String? = null,
    onClearUnlockHint: () -> Unit = {},
    onRefresh: () -> Unit,
    onVerify: (productId: String, receipt: String, storefront: String) -> Unit,
    onSandboxPurchase: (productId: String, storefront: String) -> Unit,
) {
    var productId by remember(initialProductId) {
        mutableStateOf(initialProductId?.takeIf { it.isNotBlank() } ?: "sku_gold")
    }
    var storefront by remember { mutableStateOf(DevReceipts.STOREFRONT_DEV) }
    var receipt by remember { mutableStateOf("") }
    var billingNote by remember { mutableStateOf<String?>(null) }

    val activity = LocalContext.current as? Activity
    val billingHolder = remember { arrayOfNulls<PlayBillingPurchaser>(1) }
    val billing = remember(activity) {
        activity?.let { act ->
            PlayBillingPurchaser(
                activity = act,
                onReceipt = { sku, json, token ->
                    onVerify(sku, json, DevReceipts.STOREFRONT_GOOGLE_PLAY)
                    act.window.decorView.post {
                        billingHolder[0]?.settle(token)
                    }
                },
                onError = { billingNote = it },
            ).also { billingHolder[0] = it }
        }
    }
    DisposableEffect(billing) {
        billing?.start()
        onDispose { billing?.end() }
    }

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
                            "None yet — buy via Play Billing, sandbox, or verify a receipt.",
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

        if (unlockHint != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                        Text("Unlock pack", style = MaterialTheme.typography.titleSmall)
                        Text(unlockHint, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                if (productId.isNotBlank()) {
                                    onSandboxPurchase(productId.trim(), DevReceipts.STOREFRONT_DEV)
                                }
                            },
                            enabled = !busy && productId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Buy $productId (sandbox) now") }
                        OutlinedButton(onClick = onClearUnlockHint, enabled = !busy) { Text("Dismiss") }
                    }
                }
            }
        }

        item {
            Text("Google Play Billing (live)", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Launches Play Billing for the product id, then posts " +
                            "{\"packageName\",\"productId\",\"purchaseToken\"} to " +
                            "POST /v2/entitlements/verify (storefront google_play).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = { Text("Play product id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            billingNote = null
                            if (billing == null) {
                                billingNote = "Activity unavailable for Billing"
                            } else {
                                billing.purchase(productId.trim())
                            }
                        },
                        enabled = !busy && productId.isNotBlank() && billing != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buy with Play Billing") }
                    billingNote?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Sandbox purchase", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mints a signed sandbox receipt for the selected storefront and posts it to " +
                            "POST /v2/entitlements/verify.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DevReceipts.knownStorefronts.forEach { id ->
                            FilterChip(
                                selected = storefront == id,
                                onClick = { storefront = id },
                                label = { Text(id) },
                                enabled = !busy,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = productId,
                        onValueChange = { productId = it },
                        label = { Text("Product id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (productId.isNotBlank()) {
                                onSandboxPurchase(productId.trim(), storefront)
                            }
                        },
                        enabled = !busy && productId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buy with $storefront sandbox receipt") }
                }
            }
        }

        item {
            Text("Verify arbitrary receipt", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(modifier.fillMaxWidth()) {
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
                                onVerify(
                                    productId.trim(),
                                    receipt.trim(),
                                    storefront.trim().ifBlank { DevReceipts.STOREFRONT_DEV },
                                )
                            }
                        },
                        enabled = !busy && productId.isNotBlank() && receipt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Verify receipt") }
                }
            }
        }

        item { Text("Demo SKUs", style = MaterialTheme.typography.titleSmall) }
        items(listOf("sku_gold", "sku_season_pass", "pack_the_hollows")) { sku ->
            OutlinedButton(
                onClick = {
                    productId = sku
                    onSandboxPurchase(sku, storefront)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buy $sku ($storefront)") }
        }
    }
}
