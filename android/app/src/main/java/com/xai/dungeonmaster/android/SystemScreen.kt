package com.xai.dungeonmaster.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xai.dungeonmaster.client.models.DependencyCheck
import com.xai.dungeonmaster.client.models.HealthPayload
import com.xai.dungeonmaster.client.models.ReadinessResponse
import java.text.DateFormat
import java.util.Date

/**
 * System health tab — public readiness / metrics probes (no session required).
 */
@Composable
fun SystemScreen(
    readiness: ReadinessResponse?,
    health: HealthPayload?,
    healthOk: Boolean?,
    healthError: String?,
    healthAtEpochMs: Long?,
    baseUrl: String,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }

    val deps: Map<String, DependencyCheck> =
        readiness?.dependencies ?: health?.dependencies ?: emptyMap()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "System health",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        item {
            Text(
                "Public probes via /health/ready and /v2/health (no session).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (healthOk) {
                                true -> "UP"
                                false -> "DOWN"
                                null -> "…"
                            },
                            color = when (healthOk) {
                                true -> MaterialTheme.colorScheme.tertiary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    healthError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Base: $baseUrl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    healthAtEpochMs?.let {
                        Text(
                            "Checked ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Text("Metrics", style = MaterialTheme.typography.titleSmall)
                    Text("Sessions: ${health?.sessions ?: readiness?.sessions ?: "—"}")
                    Text("Engines: ${health?.engines ?: readiness?.engines ?: "—"}")
                    Text("Uptime: ${formatUptime(health?.uptimeSeconds)}")
                    health?.memory?.let { mem ->
                        Text(
                            "Heap free ${fmtBytes(mem.freeBytes)} / total ${fmtBytes(mem.totalBytes)} (max ${fmtBytes(mem.maxBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Card(modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Text("Dependencies", style = MaterialTheme.typography.titleSmall)
                    if (deps.isEmpty()) {
                        Text(
                            "No dependency data yet — hit Refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        deps.forEach { (name, check) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(name)
                                Text(
                                    buildString {
                                        append(check.status.value)
                                        check.detail?.let { append(" · $it") }
                                    },
                                    color = when (check.status) {
                                        DependencyCheck.Status.UP -> MaterialTheme.colorScheme.tertiary
                                        DependencyCheck.Status.DOWN -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatUptime(seconds: Long?): String {
    if (seconds == null) return "—"
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${r}s"
        else -> "${r}s"
    }
}

private fun fmtBytes(n: Long?): String {
    if (n == null) return "—"
    val v = n.toDouble()
    return when {
        v < 1024 -> "$n B"
        v < 1024 * 1024 -> "${(v / 1024).toInt()} KB"
        v < 1024 * 1024 * 1024 -> String.format("%.1f MB", v / (1024 * 1024))
        else -> String.format("%.2f GB", v / (1024 * 1024 * 1024))
    }
}
