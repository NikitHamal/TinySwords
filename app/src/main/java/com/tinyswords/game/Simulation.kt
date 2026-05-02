package com.tinyswords.game

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class Simulation(val gameState: GameState) {

    fun update(dt: Double) {
        gameState.time += dt

        // Simple unit movement toward target
        for (unit in gameState.units) {
            if (unit.dead || unit.garrisoned) continue

            unit.anim += dt * 6.0 // update animation frame

            if (unit.order == "move" && unit.target is Pair<*, *>) {
                val t = unit.target as Pair<Double, Double>
                val dx = t.first - unit.x
                val dy = t.second - unit.y
                val dist = hypot(dx, dy)

                if (dist < 5.0) {
                    unit.order = "idle"
                    unit.target = null
                } else {
                    val speed = 80.0 * dt // pixels per second
                    val moveDist = min(speed, dist)
                    unit.x += (dx / dist) * moveDist
                    unit.y += (dy / dist) * moveDist
                    unit.face = if (dx < 0) -1 else 1
                }
            }
        }

        // Basic cleanup
        gameState.units.removeAll { it.dead }
        gameState.buildings.removeAll { it.dead }
        gameState.resources.removeAll { it.dead }
    }
}
