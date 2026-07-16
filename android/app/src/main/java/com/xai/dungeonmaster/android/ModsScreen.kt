package com.xai.dungeonmaster.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xai.dungeonmaster.client.models.CatalogPayload
import com.xai.dungeonmaster.client.models.PackInfo

/**
 * Mod browser (Android counterpart of /mod-browser.html): installed packs
 * with runtime enable/disable toggles, plugin counts, and the active
 * narration provider. Pack upload stays on the web UI for now.
 */
@Composable
fun ModsScreen(
    catalog: CatalogPayload?,
    busy: Boolean,
    onLoad: () -> Unit,
    onToggle: (id: String, enable: Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Content packs",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onLoad, enabled = !busy) { Text("Reload") }
            }
        }

        if (catalog == null) {
            item { Text("Tap Reload to fetch the catalog.", style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }

        items(catalog.contentPacks.orEmpty()) { pack ->
            PackCard(pack, busy, onToggle)
        }

        catalog.narration?.let { narration ->
            item { Text("Narration", style = MaterialTheme.typography.titleMedium) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${narration.active ?: "?"} (${narration.health ?: "UNKNOWN"})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Available: ${narration.available.orEmpty().joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        catalog.plugins?.let { plugins ->
            item { Text("Plugins", style = MaterialTheme.typography.titleMedium) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                        PluginLine("Quest scripts", plugins.questScripts)
                        PluginLine("Spell effects", plugins.spellEffects)
                        PluginLine("Item effects", plugins.itemEffects)
                        PluginLine("Encounter biomes", plugins.encounterBiomes)
                        PluginLine("Loot biomes", plugins.lootBiomes)
                        PluginLine("Storefronts", plugins.storefronts)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCard(pack: PackInfo, busy: Boolean, onToggle: (String, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                Text(
                    pack.displayName ?: pack.id ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "v${pack.version ?: "?"} · ${pack.monsters ?: 0} monsters · ${pack.items ?: 0} items",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val id = pack.id
            Switch(
                checked = pack.enabled == true,
                enabled = !busy && id != null,
                onCheckedChange = { wantEnabled -> id?.let { onToggle(it, wantEnabled) } },
            )
        }
    }
}

@Composable
private fun PluginLine(label: String, ids: List<String>?) {
    Text("$label: ${ids.orEmpty().joinToString().ifEmpty { "—" }}",
        style = MaterialTheme.typography.bodySmall)
}
