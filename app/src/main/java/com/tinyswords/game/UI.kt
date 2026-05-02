package com.tinyswords.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

@Composable
fun MainMenu(onStartGame: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF143340)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TINY SWORDS", color = Color.White, fontSize = 48.sp, modifier = Modifier.padding(bottom = 32.dp))
            Button(onClick = onStartGame) {
                Text("Single Player", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun HUD(gameState: GameState) {
    val playerFaction = gameState.factions.find { it.id == 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        // Resources UI
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color(0x88000000))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            playerFaction?.res?.forEach { (res, amt) ->
                Text(
                    text = "${res.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}: ${floor(amt).toInt()}",
                    color = Color.White
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { /* Handle action */ }) {
                Text("Build")
            }
            Button(onClick = { /* Handle action */ }) {
                Text("Attack")
            }
        }
    }
}
