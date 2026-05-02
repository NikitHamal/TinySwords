package com.tinyswords.realmwar.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyswords.realmwar.ui.components.PixelButton
import com.tinyswords.realmwar.ui.components.PixelPanel

@Composable
fun SettingsScreen(onBack: () -> kotlin.Unit) {
    var volume by remember { mutableStateOf(0.8f) }
    var autosave by remember { mutableStateOf(true) }
    var edgePan by remember { mutableStateOf(true) }
    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PixelButton("← Back", onClick = onBack)
                Text("Settings", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
            PixelPanel(title = "Defaults", modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Master Volume", color = MaterialTheme.colorScheme.onSurface)
                Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Autosave by default", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = autosave, onCheckedChange = { autosave = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Edge-pan camera", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = edgePan, onCheckedChange = { edgePan = it })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "These defaults apply to menus and new world creation. Existing worlds keep their own settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
