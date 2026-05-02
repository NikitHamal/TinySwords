package com.tinyswords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Title screen + new-game options. Matches the web game's flow: Continue, Single Player, Settings.
 * On phones we keep the layout dense and centered.
 */
@Composable
fun MainMenu(
    hasSave: Boolean,
    onContinue: () -> kotlin.Unit,
    onNewGame: (seed: String) -> kotlin.Unit,
    onAbout: () -> kotlin.Unit
) {
    var showNewGame by remember { mutableStateOf(false) }
    var seed by remember { mutableStateOf("crossroads-${(1000..9999).random()}") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.PanelDeep, Palette.Panel))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TINY SWORDS", style = Type.Display.copy(fontSize = 40.sp))
            Text("Pixel-Art Real-Time Strategy", style = Type.Small)
            Spacer(Modifier.height(24.dp))

            if (!showNewGame) {
                if (hasSave) MenuButton("Continue", onContinue)
                MenuButton("Single Player") { showNewGame = true }
                MenuButton("About") { onAbout() }
            } else {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.Panel)
                        .border(2.dp, Palette.LineHot, RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("New Realm", style = Type.Heading)
                    Spacer(Modifier.height(12.dp))
                    Text("Seed: $seed", style = Type.Body)
                    Spacer(Modifier.height(8.dp))
                    MenuButton("Reroll Seed") { seed = "crossroads-${(1000..9999).random()}" }
                    MenuButton("Begin Battle") { onNewGame(seed) }
                    MenuButton("Back") { showNewGame = false }
                }
            }
        }
    }
}

@Composable
fun MenuButton(label: String, onClick: () -> kotlin.Unit) {
    Box(
        Modifier
            .widthIn(min = 220.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.PanelStrong)
            .border(2.dp, Palette.LineHot, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Type.Heading.copy(color = Palette.Gold))
    }
}
