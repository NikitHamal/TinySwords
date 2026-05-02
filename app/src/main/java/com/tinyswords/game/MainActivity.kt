package com.tinyswords.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gameState = GameState()

        // Add some dummy initial data for demo
        gameState.units.add(Unit(1, "worker", 0, 200.0, 200.0, hp = 100.0, maxHp = 100.0))
        gameState.units.add(Unit(2, "worker", 0, 250.0, 220.0, hp = 100.0, maxHp = 100.0))
        gameState.buildings.add(Building(1, "castle", 0, 400.0, 300.0, 64.0, 64.0, 1000.0, 1000.0))
        gameState.resources.add(Resource(1, "tree", 600.0, 400.0, 500.0, 500.0))
        gameState.resources.add(Resource(2, "gold", 650.0, 450.0, 500.0, 500.0))

        val simulation = Simulation(gameState)

        setContent {
            var isPlaying by remember { mutableStateOf(false) }

            if (!isPlaying) {
                MainMenu(onStartGame = { isPlaying = true })
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    GameEngine(gameState, simulation)
                    HUD(gameState)
                }
            }
        }
    }
}
