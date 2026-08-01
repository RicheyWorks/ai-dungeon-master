package com.xai.dungeonmaster.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xai.dungeonmaster.client.models.GameStatusV2
import com.xai.dungeonmaster.client.models.MemberState

/**
 * v1 client shell (roadmap Phase 3): Game / Mods / Store / System tabs over the
 * generated Kotlin SDK, with guest session identity + Bearer auth. Session
 * identity is restored from disk across process restarts.
 */
@Composable
fun GameApp() {
    val context = LocalContext.current
    val viewModel: GameViewModel = viewModel(
        factory = GameViewModel.Factory(SessionStore(context)),
    )
    val ui by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.pollHealth()
    }
    LaunchedEffect(ui.baseUrl) {
        // re-poll when server changes
        viewModel.pollHealth()
        while (true) {
            kotlinx.coroutines.delay(15_000)
            viewModel.pollHealth()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerBar(
            baseUrl = ui.baseUrl,
            session = ui.session,
            busy = ui.busy,
            onBaseUrlChange = viewModel::setBaseUrl,
            onRefresh = viewModel::refresh,
            onNewSession = { viewModel.startSession() },
        )

        ui.session?.let { session ->
            Text(
                buildString {
                    append("Playing as ${session.displayName} · ${session.shortId()}")
                    if (ui.stompConnected) append(" · LIVE")
                    when (ui.healthOk) {
                        true -> append(" · READY")
                        false -> append(" · NOT READY")
                        null -> Unit
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    ui.healthOk == false -> MaterialTheme.colorScheme.error
                    ui.stompConnected -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        if (ui.session == null && ui.healthOk != null) {
            Text(
                if (ui.healthOk == true) "Engine READY" else "Engine NOT READY",
                style = MaterialTheme.typography.labelMedium,
                color = if (ui.healthOk == true) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        ui.info?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }

        ui.error?.let { message ->
            Text(
                "Error: $message",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Game") })
            Tab(
                selected = tab == 1,
                onClick = {
                    tab = 1
                    viewModel.loadMarketplace()
                    if (ui.catalog == null) viewModel.loadCatalog()
                },
                text = { Text("Mods") },
            )
            Tab(
                selected = tab == 2,
                onClick = {
                    tab = 2
                    if (ui.entitlements == null) viewModel.loadEntitlements()
                },
                text = { Text("Store") },
            )
            Tab(
                selected = tab == 3,
                onClick = {
                    tab = 3
                    viewModel.pollHealth()
                },
                text = { Text("System") },
            )
        }

        when (tab) {
            0 -> GameScreen(ui, viewModel)
            1 -> ModsScreen(
                catalog = ui.catalog,
                marketplace = ui.marketplace,
                marketQuery = ui.marketQuery,
                installJob = ui.installJob,
                busy = ui.busy,
                onLoad = {
                    viewModel.loadMarketplace()
                    viewModel.loadCatalog()
                },
                onMarketQueryChange = viewModel::setMarketQuery,
                onSearch = { viewModel.loadMarketplace() },
                onInstall = viewModel::installMarketplacePack,
                onCancelInstall = viewModel::cancelMarketplaceInstall,
                onToggle = viewModel::togglePack,
                onUpload = viewModel::uploadPack,
            )
            2 -> EntitlementsScreen(
                entitlements = ui.entitlements,
                busy = ui.busy,
                onRefresh = viewModel::loadEntitlements,
                onVerify = viewModel::verifyReceipt,
                onSandboxPurchase = viewModel::sandboxPurchase,
            )
            else -> SystemScreen(
                readiness = ui.readiness,
                health = ui.health,
                healthOk = ui.healthOk,
                healthError = ui.healthError,
                healthAtEpochMs = ui.healthAtEpochMs,
                baseUrl = ui.baseUrl,
                onRefresh = viewModel::pollHealth,
            )
        }
    }
}

@Composable
private fun GameScreen(ui: GameViewModel.UiState, viewModel: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SessionActions(ui.busy, viewModel) }

        ui.status?.let { status ->
            item { QuestCard(status) }
            item { Text("Party", style = MaterialTheme.typography.titleMedium) }
            items(status.party.orEmpty()) { member -> MemberCard(member) }

            val events = status.recentEvents.orEmpty()
            if (events.isNotEmpty()) {
                item { Text("The story so far", style = MaterialTheme.typography.titleMedium) }
                item {
                    Card {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                            events.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item { Text("Choices", style = MaterialTheme.typography.titleMedium) }
            item { ChoiceButtons(status, ui.busy, viewModel::act) }
        }

        item { HorizontalDivider() }
        item {
            NarrationPanel(
                narration = ui.narration,
                streamBuffer = ui.streamBuffer,
                stompConnected = ui.stompConnected,
                busy = ui.busy,
                onNarrate = viewModel::narrate,
            )
        }
    }
}

@Composable
private fun SessionActions(busy: Boolean, viewModel: GameViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = viewModel::saveGame,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) { Text("Save") }
        OutlinedButton(
            onClick = viewModel::loadGame,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) { Text("Load") }
        OutlinedButton(
            onClick = viewModel::resetGame,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) { Text("Reset") }
    }
}

@Composable
private fun ServerBar(
    baseUrl: String,
    session: SessionInfo?,
    busy: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Server") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
            } else {
                OutlinedButton(onClick = onRefresh) { Text("Sync") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNewSession, enabled = !busy) {
                Text(if (session == null) "Start session" else "New session")
            }
        }
    }
}

@Composable
private fun QuestCard(status: GameStatusV2) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            val quest = status.quest
            Text(
                quest?.title ?: "No active quest",
                style = MaterialTheme.typography.titleLarge,
            )
            val outcome = when {
                quest?.completed == true -> "Completed"
                quest?.failed == true -> "Failed"
                status.combatActive == true -> "In combat!"
                else -> "In progress"
            }
            Text(
                "$outcome · Chaos ${status.chaosLevel ?: "?"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { (quest?.progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MemberCard(member: MemberState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    member.name ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${member.role ?: ""} L${member.level ?: 1}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val hp = (member.hp ?: 0).coerceAtLeast(0)
            val maxHp = (member.maxHp ?: 1).coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { hp.toFloat() / maxHp.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildString {
                    append("HP $hp/$maxHp")
                    member.mana?.let { append(" · MP $it/${member.maxMana ?: it}") }
                    if (member.alive == false) append(" · FALLEN")
                    val statuses = member.statuses.orEmpty()
                    if (statuses.isNotEmpty()) append(" · ${statuses.joinToString()}")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChoiceButtons(status: GameStatusV2, busy: Boolean, onAct: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val choices = status.availableChoices.orEmpty()
        if (choices.isEmpty()) {
            Text("No choices available.", style = MaterialTheme.typography.bodyMedium)
        }
        choices.forEach { label ->
            Button(
                onClick = { onAct(label) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(label) }
        }
    }
}

@Composable
private fun NarrationPanel(
    narration: String?,
    streamBuffer: String,
    stompConnected: Boolean,
    busy: Boolean,
    onNarrate: (String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (stompConnected) "Ask the Dungeon Master (live stream)" else "Ask the Dungeon Master",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("What do you do?") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { if (prompt.isNotBlank()) onNarrate(prompt) },
            enabled = !busy && prompt.isNotBlank(),
        ) { Text(if (stompConnected) "Stream narrate" else "Narrate") }
        if (streamBuffer.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    streamBuffer,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        narration?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
