package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.game.WorldSettings
import com.tinyswords.app.ui.components.CommandButton
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

@Composable
fun NewGameScreen(
    onStart: (WorldSettings) -> Unit,
    onBack: () -> Unit
) {
    var worldSize by remember { mutableStateOf("standard") }
    var difficulty by remember { mutableStateOf("normal") }
    var mapStyle by remember { mutableStateOf("crossroads") }
    var density by remember { mutableStateOf("normal") }
    var rivals by remember { mutableStateOf(4) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0a1a2a), Color(0xFF11281f), Color(0xFF1a1a0a))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.82f else 0.66f)
                .widthIn(max = 720.dp)
                .heightIn(max = maxHeight * 0.92f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.97f), RoundedCornerShape(12.dp))
                .border(2.dp, GameColors.PanelBorder, RoundedCornerShape(12.dp))
                .padding(if (compact) 14.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
        ) {
            Text("NEW REALM", style = GameTypography.Title.copy(fontSize = if (compact) 22.sp else 28.sp))

            OptionRow("SIZE") {
                OptionChip("Standard", worldSize == "standard") { worldSize = "standard" }
                OptionChip("Large", worldSize == "large") { worldSize = "large" }
                OptionChip("Massive", worldSize == "massive") { worldSize = "massive" }
            }

            OptionRow("DIFFICULTY") {
                OptionChip("Peaceful", difficulty == "peaceful") { difficulty = "peaceful" }
                OptionChip("Easy", difficulty == "easy") { difficulty = "easy" }
                OptionChip("Normal", difficulty == "normal") { difficulty = "normal" }
                OptionChip("Hard", difficulty == "hard") { difficulty = "hard" }
            }

            OptionRow("MAP") {
                OptionChip("Cross", mapStyle == "crossroads") { mapStyle = "crossroads" }
                OptionChip("Isles", mapStyle == "archipelago") { mapStyle = "archipelago" }
                OptionChip("Rivers", mapStyle == "twinrivers") { mapStyle = "twinrivers" }
                OptionChip("Corners", mapStyle == "fourcorners") { mapStyle = "fourcorners" }
                OptionChip("Spiral", mapStyle == "spiral") { mapStyle = "spiral" }
                OptionChip("Gold", mapStyle == "goldrush") { mapStyle = "goldrush" }
            }

            OptionRow("RESOURCES") {
                OptionChip("Sparse", density == "sparse") { density = "sparse" }
                OptionChip("Normal", density == "normal") { density = "normal" }
                OptionChip("Rich", density == "rich") { density = "rich" }
                OptionChip("Abundant", density == "abundant") { density = "abundant" }
            }

            OptionRow("RIVALS") {
                for (r in 1..4) OptionChip("$r", rivals == r) { rivals = r }
            }

            Text(
                text = "Tap units to select. With units selected, tap ground to move, enemies to attack, and resources to harvest.",
                style = GameTypography.Small.copy(color = GameColors.TextSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CommandButton("BACK", onClick = onBack, modifier = Modifier.weight(1f))
                CommandButton(
                    "START",
                    onClick = {
                        onStart(WorldSettings(
                            size = worldSize,
                            mapStyle = mapStyle,
                            difficulty = difficulty,
                            resourceDensity = density,
                            rivals = rivals,
                            seed = System.currentTimeMillis().toString()
                        ))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(0.92f)) {
        Text(
            text = label,
            style = GameTypography.Small.copy(letterSpacing = 2.sp),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) GameColors.ButtonPressed else GameColors.ButtonNormal
    val border = if (selected) GameColors.TextGold else GameColors.ButtonBorder

    Box(
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 62.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GameTypography.Button.copy(
                fontSize = 10.sp,
                color = if (selected) GameColors.TextGold else GameColors.TextPrimary
            )
        )
    }
}
