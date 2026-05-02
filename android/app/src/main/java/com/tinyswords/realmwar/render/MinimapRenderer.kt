package com.tinyswords.realmwar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import com.tinyswords.realmwar.game.BUILDING_DEFS
import com.tinyswords.realmwar.game.BuildingType
import com.tinyswords.realmwar.game.FACTIONS
import com.tinyswords.realmwar.game.GameState
import com.tinyswords.realmwar.game.ResourceType

/**
 * Static terrain bitmap + dynamic dot overlay. Re-render the static part
 * once per world generation; tick the dot pass every frame.
 */
class MinimapRenderer {
    private var terrainBitmap: Bitmap? = null
    private var lastWorldId: Int = -1

    /** Lazily build the static terrain bitmap. Cheap enough to redo on settings change. */
    fun ensureTerrain(state: GameState, w: Int, h: Int): Bitmap {
        val current = terrainBitmap
        if (current != null && current.width == w && current.height == h && lastWorldId == state.hashCode()) {
            return current
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cellW = w.toFloat() / state.landCols
        val cellH = h.toFloat() / state.landRows
        val grass = Paint().apply { color = AColor.rgb(58, 161, 90) }
        val water = Paint().apply { color = AColor.rgb(44, 106, 142) }
        for (ty in 0 until state.landRows) for (tx in 0 until state.landCols) {
            val land = state.landMap[ty * state.landCols + tx].toInt() == 1
            val px = tx * cellW; val py = ty * cellH
            canvas.drawRect(px, py, px + cellW + 1, py + cellH + 1, if (land) grass else water)
        }
        lastWorldId = state.hashCode()
        terrainBitmap = bmp
        return bmp
    }

    fun draw(canvas: Canvas, state: GameState, camera: WorldRenderer.Camera, w: Int, h: Int,
             viewW: Int, viewH: Int) {
        val terrain = ensureTerrain(state, w, h)
        canvas.drawBitmap(terrain, 0f, 0f, null)
        val sx = w.toFloat() / state.worldW
        val sy = h.toFloat() / state.worldH
        val small = Paint().apply { isAntiAlias = false }

        // Resources as faint dots (don't clutter)
        for (r in state.resources) {
            if (r.dead) continue
            val dotColor = when (r.resType) {
                ResourceType.WOOD -> AColor.rgb(140, 200, 80)
                ResourceType.GOLD -> AColor.rgb(245, 220, 90)
                ResourceType.FOOD -> AColor.rgb(240, 165, 110)
            }
            small.color = dotColor
            canvas.drawRect(r.x * sx - 1, r.y * sy - 1, r.x * sx + 1, r.y * sy + 1, small)
        }
        // Buildings
        for (b in state.buildings) {
            if (b.dead) continue
            val faction = FACTIONS.firstOrNull { it.id == b.faction } ?: continue
            small.color = androidColorOf(faction.color)
            val s = if (b.type == BuildingType.CASTLE) 4f else 2.5f
            canvas.drawRect(b.x * sx - s, b.y * sy - s, b.x * sx + s, b.y * sy + s, small)
        }
        // Units
        for (u in state.units) {
            if (u.dead) continue
            val faction = FACTIONS.firstOrNull { it.id == u.faction } ?: continue
            small.color = androidColorOf(faction.color)
            canvas.drawCircle(u.x * sx, u.y * sy, 1.6f, small)
        }
        // Camera frame
        val frame = Paint().apply {
            color = AColor.argb(220, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        val left = camera.x * sx
        val top = camera.y * sy
        val right = (camera.x + viewW / camera.zoom) * sx
        val bottom = (camera.y + viewH / camera.zoom) * sy
        canvas.drawRect(left, top, right, bottom, frame)
    }

    private fun androidColorOf(c: androidx.compose.ui.graphics.Color): Int {
        val a = (c.alpha * 255 + 0.5f).toInt()
        val r = (c.red * 255 + 0.5f).toInt()
        val g = (c.green * 255 + 0.5f).toInt()
        val b = (c.blue * 255 + 0.5f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
