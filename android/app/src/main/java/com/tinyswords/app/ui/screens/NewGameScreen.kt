package com.tinyswords.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyswords.app.game.WorldSettings
import com.tinyswords.app.ui.components.AssetIcon
import com.tinyswords.app.ui.components.CommandButton
import com.tinyswords.app.ui.theme.GameColors
import com.tinyswords.app.ui.theme.GameTypography

@Composable
fun NewGameScreen(
    onStart: (WorldSettings) -> Unit,
    onBack: () -> Unit
) {
    var worldSize by remember { mutableStateOf("large") }
    var difficulty by remember { mutableStateOf("normal") }
    var mapStyle by remember { mutableStateOf("crossroads") }
    var density by remember { mutableStateOf("rich") }
    var rivals by remember { mutableStateOf(4) }
    var autosave by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(RealmMenuBackground()),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 430.dp
        Row(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.94f else 0.86f)
                .widthIn(max = 940.dp)
                .heightIn(max = maxHeight * 0.92f)
                .verticalScroll(rememberScrollState())
                .background(GameColors.Panel.copy(alpha = 0.97f), RoundedCornerShape(14.dp))
                .border(3.dp, GameColors.PanelBorder, RoundedCornerShape(14.dp))
                .padding(if (compact) 12.dp else 18.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)
        ) {
            Column(modifier = Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)) {
                Text("CREATE REALM", style = GameTypography.Title.copy(fontSize = if (compact) 24.sp else 32.sp))
                Text(
                    "Pick the terrain, economy pressure, rival count, and save behavior before generation starts.",
                    style = GameTypography.Small.copy(color = GameColors.TextSecondary),
                )
                PixelOptionGroup("SIZE") {
                    OptionChip("Standard", worldSize == "standard") { worldSize = "standard" }
                    OptionChip("Large", worldSize == "large") { worldSize = "large" }
                    OptionChip("Massive", worldSize == "massive") { worldSize = "massive" }
                }
                PixelOptionGroup("DIFFICULTY") {
                    OptionChip("Peaceful", difficulty == "peaceful") { difficulty = "peaceful" }
                    OptionChip("Easy", difficulty == "easy") { difficulty = "easy" }
                    OptionChip("Normal", difficulty == "normal") { difficulty = "normal" }
                    OptionChip("Hard", difficulty == "hard") { difficulty = "hard" }
                }
                PixelOptionGroup("MAP") {
                    OptionChip("Cross", mapStyle == "crossroads") { mapStyle = "crossroads" }
                    OptionChip("Isles", mapStyle == "archipelago") { mapStyle = "archipelago" }
                    OptionChip("Rivers", mapStyle == "twinrivers") { mapStyle = "twinrivers" }
                    OptionChip("Corners", mapStyle == "fourcorners") { mapStyle = "fourcorners" }
                    OptionChip("Spiral", mapStyle == "spiral") { mapStyle = "spiral" }
                    OptionChip("Gold", mapStyle == "goldrush") { mapStyle = "goldrush" }
                }
                PixelOptionGroup("RESOURCES") {
                    OptionChip("Sparse", density == "sparse") { density = "sparse" }
                    OptionChip("Normal", density == "normal") { density = "normal" }
                    OptionChip("Rich", density == "rich") { density = "rich" }
                    OptionChip("Abundant", density == "abundant") { density = "abundant" }
                }
                PixelOptionGroup("RIVALS") {
                    for (r in 0..4) OptionChip("$r", rivals == r) { rivals = r }
                }
            }

            Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                RealmPreviewCard(worldSize, difficulty, mapStyle, density, rivals, autosave)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ToggleTile("Autosave", if (autosave) "ON" else "OFF", autosave, Modifier.weight(1f)) { autosave = !autosave }
                    ToggleTile("Seed", "Random", true, Modifier.weight(1f)) { }
                }
                Text(
                    text = "Loading now happens on a generation screen, so terrain, navigation and assets are prepared before the battle opens.",
                    style = GameTypography.Small.copy(color = GameColors.TextSecondary),
                    textAlign = TextAlign.Center
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton("BACK", onClick = onBack, modifier = Modifier.weight(1f))
                    CommandButton(
                        "GENERATE",
                        onClick = {
                            onStart(WorldSettings(
                                size = worldSize,
                                mapStyle = mapStyle,
                                difficulty = difficulty,
                                resourceDensity = density,
                                rivals = rivals,
                                autosave = autosave,
                                seed = System.currentTimeMillis().toString()
                            ))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RealmPreviewCard(size: String, difficulty: String, map: String, density: String, rivals: Int, autosave: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x66000000), RoundedCornerShape(9.dp))
            .border(1.5.dp, GameColors.PanelBorder, RoundedCornerShape(9.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AssetIcon("Tiny Swords (Free Pack)/Buildings/Blue Buildings/Castle.png", GameColors.AccentBlue, Modifier.size(70.dp))
        Text("REALM SUMMARY", style = GameTypography.Heading.copy(fontSize = 13.sp, color = GameColors.TextGold))
        PreviewLine("Size", size)
        PreviewLine("Map", map)
        PreviewLine("Difficulty", difficulty)
        PreviewLine("Resources", density)
        PreviewLine("Rivals", rivals.toString())
        PreviewLine("Autosave", if (autosave) "on" else "off")
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label.uppercase(), style = GameTypography.Small.copy(color = GameColors.TextSecondary, fontSize = 8.sp))
        Text(value.replaceFirstChar { it.uppercase() }, style = GameTypography.Small.copy(color = GameColors.TextPrimary, fontSize = 9.sp))
    }
}

@Composable
private fun PixelOptionGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = GameTypography.Small.copy(letterSpacing = 2.sp, color = GameColors.TextGold), modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) GameColors.ButtonPressed else GameColors.ButtonNormal
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(bg, RoundedCornerShape(5.dp))
            .border(1.dp, if (selected) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = GameTypography.Button.copy(fontSize = 10.sp, color = if (selected) GameColors.TextGold else GameColors.TextPrimary))
    }
}

@Composable
private fun ToggleTile(label: String, value: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(58.dp)
            .background(if (active) GameColors.ButtonPressed else GameColors.ButtonNormal, RoundedCornerShape(6.dp))
            .border(1.dp, if (active) GameColors.TextGold else GameColors.ButtonBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = GameTypography.Small.copy(color = GameColors.TextSecondary, fontSize = 8.sp))
        Text(value, style = GameTypography.Button.copy(fontSize = 11.sp))
    }
}
