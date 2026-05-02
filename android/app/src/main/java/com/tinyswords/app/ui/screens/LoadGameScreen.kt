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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.data.SaveSystem
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
    val worlds = remember { saveSystem.listWorlds() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0a1a2a), Color(0xFF1a2a1a))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .fillMaxHeight(0.85f)
                .background(GameColors.Panel, RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SAVED REALMS", style = GameTypography.Title.copy(fontSize = 24.sp))
            Spacer(modifier = Modifier.height(12.dp))

            if (worlds.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved worlds yet.\nStart a new game!",
                        style = GameTypography.Body.copy(color = GameColors.TextSecondary),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(worlds) { world ->
                        WorldCard(world, onLoad = { onLoad(world.id) }, onDelete = {
                            saveSystem.deleteWorld(world.id)
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
            .background(GameColors.ButtonNormal, RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.PanelBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onLoad)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = world.name,
                style = GameTypography.Heading.copy(fontSize = 13.sp)
            )
            Text(
                text = "${world.settings.difficulty.replaceFirstChar { it.uppercase() }} | ${world.settings.size.replaceFirstChar { it.uppercase() }}",
                style = GameTypography.Small
            )
            Text(
                text = "Played: ${formatTime(world.playTime)} | ${dateFormat.format(Date(world.updatedAt))}",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary)
            )
        }

        CommandButton("X", onClick = onDelete)
    }
}

private fun formatTime(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
