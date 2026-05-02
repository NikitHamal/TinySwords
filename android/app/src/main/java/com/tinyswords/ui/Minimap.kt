package com.tinyswords.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tinyswords.game.C
import com.tinyswords.game.Game

/**
 * Minimap: terrain colored by biome, dots for entities, a viewport rectangle showing the
 * camera, and tap-to-jump. Sized to fit comfortably in the bottom-right on phones.
 */
@Composable
fun Minimap(game: Game) {
    val mmW = 168.dp; val mmH = 112.dp
    Canvas(
        modifier = Modifier
            .size(mmW, mmH)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F6773))
            .border(2.dp, Palette.LineHot, RoundedCornerShape(6.dp))
            .pointerInput(game.revision) {
                detectTapGestures { off ->
                    val sx = (off.x / size.width) * C.WORLD_W
                    val sy = (off.y / size.height) * C.WORLD_H
                    game.camera.x = sx - game.camera.viewW / game.camera.zoom * 0.5f
                    game.camera.y = sy - game.camera.viewH / game.camera.zoom * 0.5f
                    game.camera.clamp()
                    game.bumpRevision()
                }
            }
    ) {
        val sx = size.width / C.WORLD_W
        val sy = size.height / C.WORLD_H
        val w = game.world
        // Draw a chunky biome map (every 4th tile is enough at this resolution).
        val step = 4
        for (ty in 0 until w.rows step step) {
            for (tx in 0 until w.cols step step) {
                if (!w.isLandTile(tx, ty)) continue
                val tint = when (w.biomeAt(tx, ty)) {
                    0 -> Color(0xFF7CAE5E); 1 -> Color(0xFFB6A65C)
                    2 -> Color(0xFF9CB54B); 3 -> Color(0xFF638A55)
                    else -> Color(0xFF82BB6A)
                }
                drawRect(
                    tint,
                    Offset(tx * C.TILE * sx, ty * C.TILE * sy),
                    Size(C.TILE * sx * step, C.TILE * sy * step)
                )
            }
        }
        // Buildings + units as dots.
        for (b in game.buildings) {
            val color = if (b.faction == 0) Color(0xFF4f8ad6) else Color(0xFFc8504a)
            drawRect(color, Offset(b.x * sx - 3f, b.y * sy - 3f), Size(6f, 6f))
        }
        for (u in game.units) {
            val color = if (u.faction == 0) Color(0xFF8ec6ff) else Color(0xFFff8478)
            drawRect(color, Offset(u.x * sx - 1.5f, u.y * sy - 1.5f), Size(3f, 3f))
        }
        // Camera viewport rect.
        val cam = game.camera
        drawRect(
            Color.White,
            Offset(cam.x * sx, cam.y * sy),
            Size(cam.viewW / cam.zoom * sx, cam.viewH / cam.zoom * sy),
            style = Stroke(width = 1.5f)
        )
    }
}
