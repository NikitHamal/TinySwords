package com.tinyswords.realmwar.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.data.WorldStorage
import com.tinyswords.realmwar.ui.components.PixelButton
import com.tinyswords.realmwar.ui.components.PixelPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldListScreen(
    storage: WorldStorage,
    onBack: () -> kotlin.Unit,
    onCreate: () -> kotlin.Unit,
    onPlay: (WorldRecord) -> kotlin.Unit,
    onDelete: (WorldRecord) -> kotlin.Unit
) {
    var records by remember { mutableStateOf(emptyList<WorldRecord>()) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTick) { records = storage.listWorlds() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PixelButton("← Back", onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Single Player",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Manage saved worlds and jump back into a realm.",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                PixelButton("Create New World", primary = true, onClick = onCreate)
            }
            PixelPanel(title = "Your Worlds (${records.size})", modifier = Modifier.fillMaxSize()) {
                if (records.isEmpty()) {
                    Text("No worlds yet. Tap Create New World to start your first realm.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(records, key = { it.id }) { rec ->
                            WorldRow(rec, onPlay = { onPlay(rec) }, onDelete = {
                                onDelete(rec); refreshTick++
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldRow(rec: WorldRecord, onPlay: () -> kotlin.Unit, onDelete: () -> kotlin.Unit) {
    val df = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rec.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    "Seed ${rec.seed.take(12)} • ${rec.settings.size} • ${rec.settings.difficulty} • Last played ${df.format(Date(rec.lastPlayed))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            PixelButton("Play", primary = true, onClick = onPlay)
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            PixelButton("Delete", onClick = onDelete)
        }
    }
}
