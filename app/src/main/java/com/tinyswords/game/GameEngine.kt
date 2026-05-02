package com.tinyswords.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun GameEngine(
    gameState: GameState,
    simulation: Simulation
) {
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastTimeNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val dt = (frameTimeNanos - lastTimeNanos) / 1_000_000_000.0
                lastTimeNanos = frameTimeNanos

                // Update simulation
                simulation.update(dt)

                // Trigger recomposition
                tick = frameTimeNanos
            }
        }
    }

    // Render
    GameRenderer(gameState)
}
