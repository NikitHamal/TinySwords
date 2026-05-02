package com.tinyswords.realmwar.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.data.WorldStorage
import com.tinyswords.realmwar.ui.components.PixelButton
import com.tinyswords.realmwar.ui.components.PixelPanel

@Composable
fun TitleScreen(
    storage: WorldStorage,
    onContinue: (WorldRecord) -> kotlin.Unit,
    onSinglePlayer: () -> kotlin.Unit,
    onSettings: () -> kotlin.Unit
) {
    var latest: WorldRecord? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) { latest = storage.mostRecent() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0a1429), Color(0xFF1d2c4a), Color(0xFF142840))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            PixelPanel(title = "Tiny Swords") {
                Text(
                    "REALM WAR",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Build, hunt, defend, expand, save your world,\n" +
                        "and wage realm-scale RTS wars across persistent worlds.",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                PixelButton(
                    label = if (latest == null) "No saves yet" else "Continue \"${latest!!.name}\"",
                    primary = false,
                    enabled = latest != null,
                    onClick = { latest?.let { onContinue(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                PixelButton(
                    label = "Single Player",
                    primary = true,
                    onClick = onSinglePlayer,
                    modifier = Modifier.fillMaxWidth(),
                    subtitle = "Manage saves and create new realms"
                )
                PixelButton(
                    label = "Settings",
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
