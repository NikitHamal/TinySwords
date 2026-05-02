package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.data.SaveSystem
import com.tinyswords.app.ui.components.AssetIcon
import com.tinyswords.app.ui.components.CommandButton
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LoadGameScreen(
    saveSystem: SaveSystem,
    onLoad: (String) -> Unit,
    onBack: () -> Unit
) {
    var worlds by remember { mutableStateOf(saveSystem.listWorlds()) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(RealmMenuBackground()),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.92f else 0.74f)
                .widthIn(max = 760.dp)
                .heightIn(max = maxHeight * 0.88f)
                .background(GameColors.Panel.copy(alpha = 0.97f), RoundedCornerShape(14.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                .padding(if (compact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssetIcon("Tiny Swords (Free Pack)/Buildings/Blue Buildings/Castle.png", GameColors.AccentBlue, Modifier.size(if (compact) 38.dp else 50.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("LOAD REALM", style = GameTypography.Title.copy(fontSize = if (compact) 22.sp else 30.sp))
                    Text("Saved wars, persistent economy and generated maps.", style = GameTypography.Small.copy(color = GameColors.TextSecondary))
                }
                Text("${worlds.size}", style = GameTypography.Heading.copy(color = GameColors.TextGold, fontSize = 20.sp))
            }

            if (worlds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0x55000000), RoundedCornerShape(9.dp))
                        .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No saved realms yet. Create a realm first.", style = GameTypography.Body.copy(color = GameColors.TextSecondary))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(worlds) { world ->
                        WorldCard(world, onLoad = { onLoad(world.id) }, onDelete = {
                            saveSystem.deleteWorld(world.id)
                            worlds = saveSystem.listWorlds()
                        })
                    }
                }
            }
            CommandButton("BACK", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WorldCard(
    world: SaveSystem.WorldMeta,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GameColors.ButtonNormal.copy(alpha = 0.96f), RoundedCornerShape(7.dp))
            .border(1.5.dp, GameColors.ButtonBorder, RoundedCornerShape(7.dp))
            .clickable(onClick = onLoad)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssetIcon("Tiny Swords (Free Pack)/UI Elements/UI Elements/Icons/Icon_01.png", GameColors.TextGold, Modifier.size(34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(world.name, style = GameTypography.Heading.copy(fontSize = 13.sp, color = GameColors.TextGold))
            Text("${world.settings.difficulty.cap()} · ${world.settings.size.cap()} · ${world.settings.mapStyle.cap()}", style = GameTypography.Small)
            Text("Played ${formatTime(world.playTime)} · ${dateFormat.format(Date(world.updatedAt))}", style = GameTypography.Small.copy(color = GameColors.TextSecondary))
        }
        CommandButton("X", onClick = onDelete)
    }
}

private fun String.cap(): String = replaceFirstChar { it.uppercase() }

private fun formatTime(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
