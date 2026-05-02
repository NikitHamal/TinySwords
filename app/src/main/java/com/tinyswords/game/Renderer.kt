package com.tinyswords.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GameRenderer(gameState: GameState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw background
        drawRect(color = Color(0xFF143340))

        scale(scale = gameState.cameraZoom, pivot = Offset.Zero) {
            translate(left = -gameState.cameraX.toFloat(), top = -gameState.cameraY.toFloat()) {
                drawWorld(gameState)
            }
        }
    }
}

fun DrawScope.drawWorld(gameState: GameState) {
    // Draw resources
    for (res in gameState.resources) {
        drawCircle(
            color = if (res.type == "gold") Color(0xFFE8CA4D) else Color(0xFF366F3F),
            radius = 16f,
            center = Offset(res.x.toFloat(), res.y.toFloat())
        )
    }

    // Draw buildings
    for (bld in gameState.buildings) {
        val color = gameState.factions.find { it.id == bld.faction }?.color ?: Color.Gray
        drawRect(
            color = color,
            topLeft = Offset((bld.x - bld.w / 2).toFloat(), (bld.y - bld.h / 2).toFloat()),
            size = Size(bld.w.toFloat(), bld.h.toFloat())
        )
        // Selection highlight
        if (gameState.selected.contains(bld)) {
            drawRect(
                color = color,
                topLeft = Offset((bld.x - bld.w / 2).toFloat(), (bld.y - bld.h / 2).toFloat()),
                size = Size(bld.w.toFloat(), bld.h.toFloat()),
                style = Stroke(width = 2f)
            )
        }
    }

    // Draw units
    for (unit in gameState.units) {
        val color = gameState.factions.find { it.id == unit.faction }?.color ?: Color.Gray
        drawCircle(
            color = color,
            radius = unit.r.toFloat(),
            center = Offset(unit.x.toFloat(), unit.y.toFloat())
        )
        // Selection highlight
        if (gameState.selected.contains(unit)) {
            drawCircle(
                color = Color(0xFFF5D37D),
                radius = unit.r.toFloat() + 4f,
                center = Offset(unit.x.toFloat(), unit.y.toFloat()),
                style = Stroke(width = 2f)
            )
        }
    }
}
