package com.tinyswords.realmwar.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyswords.realmwar.data.WorldSettingsData
import com.tinyswords.realmwar.game.GameConfig
import com.tinyswords.realmwar.ui.components.PixelButton
import com.tinyswords.realmwar.ui.components.PixelPanel

@Composable
fun CreateWorldScreen(
    onBack: () -> kotlin.Unit,
    onCreate: (String, WorldSettingsData) -> kotlin.Unit
) {
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(GameConfig.WorldSize.LARGE) }
    var mapStyle by remember { mutableStateOf(GameConfig.MapStyle.CROSSROADS) }
    var difficulty by remember { mutableStateOf(GameConfig.Difficulty.NORMAL) }
    var density by remember { mutableStateOf(GameConfig.ResourceDensity.RICH) }
    var graphics by remember { mutableStateOf(GameConfig.GraphicsQuality.BALANCED) }
    var rivals by remember { mutableStateOf(4) }
    var autosave by remember { mutableStateOf(true) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PixelButton("← Back", onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text("Create New World", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Configure your realm, then generate it with a persistent save slot.",
                        color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                PixelPanel(title = "World Settings", modifier = Modifier.weight(1.4f)) {
                    LabeledField("World Name") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Leave empty for an unnamed world") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LabeledField("Seed") {
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it },
                            placeholder = { Text("Leave empty for random seed") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                    EnumChips("World Size", GameConfig.WorldSize.values(), size) { size = it }
                    EnumChips("Map", GameConfig.MapStyle.values(), mapStyle) { mapStyle = it }
                    EnumChips("Difficulty", GameConfig.Difficulty.values(), difficulty) { difficulty = it }
                    EnumChips("Resource Density", GameConfig.ResourceDensity.values(), density) { density = it }
                    EnumChips("Graphics", GameConfig.GraphicsQuality.values(), graphics) { graphics = it }
                    LabeledField("Rival Realms ($rivals)") {
                        Slider(
                            value = rivals.toFloat(),
                            onValueChange = { rivals = it.toInt() },
                            valueRange = 0f..4f,
                            steps = 3
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Autosave", color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Switch(checked = autosave, onCheckedChange = { autosave = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Use Unnamed World", onClick = { name = "" })
                        PixelButton("Generate Random Seed", onClick = { seed = (1..7).joinToString("") { (('a'..'z') + ('0'..'9')).random().toString() } })
                    }
                    PixelButton(
                        "Create World",
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                        subtitle = "Generate terrain and enter the realm",
                        onClick = {
                            val settings = WorldSettingsData(
                                seed = seed,
                                size = size.name,
                                mapStyle = mapStyle.name,
                                difficulty = difficulty.name,
                                density = density.name,
                                graphics = graphics.name,
                                rivals = rivals,
                                autosave = autosave
                            )
                            onCreate(name, settings)
                        }
                    )
                }
                PixelPanel(title = "Generation Preview", modifier = Modifier.weight(1f)) {
                    Text("World: ${size.label}", color = MaterialTheme.colorScheme.onSurface)
                    Text("Map: ${mapStyle.label}", color = MaterialTheme.colorScheme.onSurface)
                    Text(mapStyle.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text("Difficulty: ${difficulty.label}", color = MaterialTheme.colorScheme.onSurface)
                    Text("Resource density: ${density.label}", color = MaterialTheme.colorScheme.onSurface)
                    Text("Graphics: ${graphics.label}", color = MaterialTheme.colorScheme.onSurface)
                    Text("Rivals: $rivals (the rest stay neutral)", color = MaterialTheme.colorScheme.onSurface)
                    Text("Autosave: ${if (autosave) "every 45 seconds" else "off"}", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> kotlin.Unit) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> EnumChips(label: String, values: Array<T>, selected: T, onChange: (T) -> kotlin.Unit) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (v in values) {
                val pretty = v.toString().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                val isSelected = v == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onChange(v) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        pretty,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
