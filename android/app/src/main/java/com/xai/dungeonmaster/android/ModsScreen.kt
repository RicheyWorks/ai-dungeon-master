package com.xai.dungeonmaster.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xai.dungeonmaster.client.models.CatalogPayload
import com.xai.dungeonmaster.client.models.PackInfo
import java.io.File

/**
 * Marketplace browse/install + live catalog toggles / zip upload.
 */
@Composable
fun ModsScreen(
    catalog: CatalogPayload?,
    marketplace: MarketplacePayload?,
    marketQuery: String,
    busy: Boolean,
    onLoad: () -> Unit,
    onMarketQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onInstall: (String) -> Unit,
    onToggle: (id: String, enable: Boolean) -> Unit,
    onUpload: (file: File, replace: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var replace by remember { mutableStateOf(false) }
    var lastPickedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onLoad() }

    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "pack.zip"
        lastPickedName = name
        val dest = File(context.cacheDir, "upload-${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@rememberLauncherForActivityResult
            onUpload(dest, replace)
        } catch (e: Exception) {
            lastPickedName = "Failed to read: ${e.message}"
        }
    }

    val marketPacks = marketplace?.packs.orEmpty()
    val livePacks = catalog?.contentPacks.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Marketplace",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onLoad, enabled = !busy) { Text("Reload") }
            }
        }
        item {
            Text(
                buildString {
                    append("GET /v2/marketplace")
                    marketplace?.root?.let { append(" · $it") }
                    marketplace?.let {
                        append(" · ${it.available ?: 0} available · ${it.installed ?: 0} installed")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = marketQuery,
                    onValueChange = onMarketQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search packs") },
                    enabled = !busy,
                )
                OutlinedButton(onClick = onSearch, enabled = !busy) { Text("Search") }
            }
        }
        if (marketplace != null && marketPacks.isEmpty()) {
            item {
                Text(
                    "No marketplace packs match.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(marketPacks, key = { it.id }) { pack ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.displayName ?: pack.id, style = MaterialTheme.typography.titleSmall)
                            Text(
                                buildString {
                                    append("v${pack.version ?: "?"} · min ${pack.minEngineVersion ?: "?"}")
                                    if (pack.installed == true) append(" · installed")
                                    if (pack.enabled == true) append(" · enabled")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (pack.installed != true) {
                            Button(
                                onClick = { onInstall(pack.id) },
                                enabled = !busy,
                            ) { Text("Install") }
                        } else {
                            Text(
                                "Installed",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    pack.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Text("Live catalog", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Text("Upload pack zip", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "POST /v2/catalog/packs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Replace if exists", modifier = Modifier.weight(1f))
                        Switch(checked = replace, onCheckedChange = { replace = it }, enabled = !busy)
                    }
                    Button(
                        onClick = { pickZip.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !busy,
                    ) { Text("Choose zip…") }
                    lastPickedName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (livePacks.isEmpty()) {
            item {
                Text(
                    "No live packs yet — install from marketplace or upload a zip.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(livePacks, key = { it.id ?: it.displayName ?: it.hashCode().toString() }) { pack ->
            PackRow(pack = pack, busy = busy, onToggle = onToggle)
        }
        catalog?.narration?.let { narration ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                        Text("Narration", style = MaterialTheme.typography.titleSmall)
                        Text("${narration.active ?: "?"} (${narration.health ?: "UNKNOWN"})")
                        Text(
                            "Available: ${(narration.available ?: emptyList()).joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: PackInfo,
    busy: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pack.displayName ?: pack.id ?: "?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "v${pack.version ?: "?"} · ${pack.monsters ?: 0} monsters · ${pack.items ?: 0} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = pack.enabled == true,
                onCheckedChange = { enable ->
                    pack.id?.let { onToggle(it, enable) }
                },
                enabled = !busy && pack.id != null,
            )
        }
    }
}
