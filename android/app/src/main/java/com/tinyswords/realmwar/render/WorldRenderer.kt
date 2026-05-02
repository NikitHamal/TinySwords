package com.tinyswords.realmwar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import com.tinyswords.realmwar.assets.AssetPaths
import com.tinyswords.realmwar.assets.SpriteCache
import com.tinyswords.realmwar.game.BUILDING_DEFS
import com.tinyswords.realmwar.game.Building
import com.tinyswords.realmwar.game.BuildingPhase
import com.tinyswords.realmwar.game.Decor
import com.tinyswords.realmwar.game.Effect
import com.tinyswords.realmwar.game.EffectKind
import com.tinyswords.realmwar.game.FACTIONS
import com.tinyswords.realmwar.game.FactionKey
import com.tinyswords.realmwar.game.GameState
import com.tinyswords.realmwar.game.Projectile
import com.tinyswords.realmwar.game.ResourceNode
import com.tinyswords.realmwar.game.ResourceType
import com.tinyswords.realmwar.game.TILE_PX
import com.tinyswords.realmwar.game.Unit
import com.tinyswords.realmwar.game.UnitState
import com.tinyswords.realmwar.game.UnitType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the world onto a raster [Canvas] each frame. Pixel art is drawn
 * with `Paint.FILTER_BITMAP_FLAG = false` so the Tiny Swords art keeps its
 * crisp edges at any zoom level.
 *
 * The renderer is intentionally backend-agnostic: a `RenderContext` carries
 * the camera, view dimensions, and current zoom, so the same code paths can
 * draw the in-game world and the minimap thumbnail.
 */
class WorldRenderer(private val cache: SpriteCache) {

    data class Camera(var x: Float, var y: Float, var zoom: Float)

    private val pixelPaint = Paint(0).apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val groundPaint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val waterPaint = Paint().apply { color = AColor.rgb(44, 106, 142) }
    private val grassPaint = Paint().apply { color = AColor.rgb(58, 161, 90) }
    private val sandPaint = Paint().apply { color = AColor.rgb(95, 178, 110) }
    private val stroke = Paint().apply { isAntiAlias = true }
    private val srcRect = Rect(); private val dstRect = RectF()

    fun draw(canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int,
             selectedIds: Set<Int>, placingType: com.tinyswords.realmwar.game.BuildingType?,
             placingX: Float?, placingY: Float?) {
        canvas.drawColor(AColor.rgb(20, 28, 44), PorterDuff.Mode.SRC)
        drawTerrain(canvas, state, camera, viewW, viewH)
        drawDecor(canvas, state, camera, viewW, viewH)
        drawResources(canvas, state, camera, viewW, viewH)
        drawBuildings(canvas, state, camera, viewW, viewH, selectedIds)
        drawUnits(canvas, state, camera, viewW, viewH, selectedIds)
        drawProjectiles(canvas, state, camera)
        drawEffects(canvas, state, camera)
        if (placingType != null && placingX != null && placingY != null) {
            drawPlacementGhost(canvas, state, camera, placingType, placingX, placingY)
        }
    }

    // ----- Terrain ----------------------------------------------------------

    private fun drawTerrain(canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int) {
        val zoom = camera.zoom
        val tilePx = TILE_PX * zoom
        val startTx = max(0, (camera.x / TILE_PX).toInt() - 1)
        val startTy = max(0, (camera.y / TILE_PX).toInt() - 1)
        val endTx = min(state.landCols - 1, ((camera.x + viewW / zoom) / TILE_PX).toInt() + 1)
        val endTy = min(state.landRows - 1, ((camera.y + viewH / zoom) / TILE_PX).toInt() + 1)

        for (ty in startTy..endTy) {
            for (tx in startTx..endTx) {
                val land = state.landMap[ty * state.landCols + tx].toInt() == 1
                val px = (tx * TILE_PX - camera.x) * zoom
                val py = (ty * TILE_PX - camera.y) * zoom
                if (land) {
                    val variant = state.groundVariant[ty * state.landCols + tx].toInt() and 0xFF
                    val edge = variant >= 40
                    val paint = if (edge) sandPaint else grassPaint
                    canvas.drawRect(px, py, px + tilePx + 1, py + tilePx + 1, paint)
                    // procedural noise dots
                    if ((variant and 0x07) == 0) {
                        canvas.drawCircle(px + tilePx * 0.3f, py + tilePx * 0.4f, max(1f, tilePx * 0.06f),
                            Paint().apply { color = AColor.argb(60, 30, 90, 50) })
                    }
                } else {
                    canvas.drawRect(px, py, px + tilePx + 1, py + tilePx + 1, waterPaint)
                    // wave highlights
                    if (((tx + ty + (state.time * 2).toInt()) and 7) == 0) {
                        val foam = Paint().apply { color = AColor.argb(80, 230, 240, 255) }
                        canvas.drawCircle(px + tilePx * 0.5f, py + tilePx * 0.5f,
                            max(1f, tilePx * 0.15f), foam)
                    }
                }
            }
        }
    }

    private fun drawDecor(canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int) {
        for (d in state.decor) {
            val sx = (d.ix - camera.x) * camera.zoom
            val sy = (d.iy - camera.y) * camera.zoom
            if (sx < -64 || sy < -64 || sx > viewW + 64 || sy > viewH + 64) continue
            drawSprite(canvas, d.kind, sx, sy, 64f, 64f, d.scale * camera.zoom)
        }
    }

    private fun drawResources(canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int) {
        for (r in state.resources) {
            if (r.dead) continue
            val sx = (r.x - camera.x) * camera.zoom
            val sy = (r.y - camera.y) * camera.zoom
            if (sx < -96 || sy < -96 || sx > viewW + 96 || sy > viewH + 96) continue
            val key = when (r.resType) {
                ResourceType.WOOD -> "tree${1 + (r.variant % 4)}"
                ResourceType.GOLD -> "gold${1 + (r.variant % 4)}"
                ResourceType.FOOD -> "sheepIdle"
            }
            drawSprite(canvas, key, sx, sy, 64f, 64f, 0.8f * camera.zoom)
            // HP/yield bar when damaged
            if (r.amount in 1..199 && r.resType != ResourceType.FOOD) {
                drawTinyBar(canvas, sx, sy - 36f * camera.zoom, 36f * camera.zoom,
                    r.amount.toFloat() / 200f, AColor.rgb(180, 220, 80))
            }
        }
    }

    private fun drawBuildings(
        canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int,
        selectedIds: Set<Int>,
    ) {
        for (b in state.buildings) {
            if (b.dead) continue
            val sx = (b.x - camera.x) * camera.zoom
            val sy = (b.y - camera.y) * camera.zoom
            if (sx < -160 || sy < -160 || sx > viewW + 160 || sy > viewH + 160) continue
            val factionKey = FACTIONS.firstOrNull { it.id == b.faction }?.key ?: FactionKey.BLUE
            val def = b.def
            val key = AssetPaths.building(factionKey, b.type)
            drawSprite(canvas, key, sx, sy, def.w.toFloat() * camera.zoom, def.h.toFloat() * camera.zoom,
                def.scale * camera.zoom)
            if (b.phase == BuildingPhase.FOUNDATION) {
                val ghost = Paint().apply { color = AColor.argb(120, 230, 220, 100) }
                canvas.drawRect(sx - 30 * camera.zoom, sy - 16 * camera.zoom,
                    sx + 30 * camera.zoom, sy + 24 * camera.zoom, ghost)
                drawTinyBar(canvas, sx, sy - 30 * camera.zoom, 56f * camera.zoom,
                    b.buildProgress, AColor.rgb(255, 220, 100))
            } else {
                if (b.hp < b.maxHp) {
                    drawTinyBar(canvas, sx, sy - 36 * camera.zoom, 56f * camera.zoom,
                        b.hp / b.maxHp, AColor.rgb(220, 60, 60))
                }
                if (b.id in selectedIds) drawSelectionRing(canvas, sx, sy + 6 * camera.zoom,
                    36f * camera.zoom)
                if (b.trainingType != null) {
                    val def2 = com.tinyswords.realmwar.game.UNIT_DEFS[b.trainingType]!!
                    val pct = 1f - (b.trainingTimeLeft / def2.buildTimeSec)
                    drawTinyBar(canvas, sx, sy - 24 * camera.zoom, 56f * camera.zoom, pct,
                        AColor.rgb(110, 200, 240))
                }
            }
        }
    }

    private fun drawUnits(
        canvas: Canvas, state: GameState, camera: Camera, viewW: Int, viewH: Int,
        selectedIds: Set<Int>,
    ) {
        for (u in state.units) {
            if (u.dead) continue
            val sx = (u.x - camera.x) * camera.zoom
            val sy = (u.y - camera.y) * camera.zoom
            if (sx < -64 || sy < -64 || sx > viewW + 64 || sy > viewH + 64) continue
            val factionKey = FACTIONS.firstOrNull { it.id == u.faction }?.key ?: FactionKey.BLUE

            // Faction-tinted shadow
            val faction = FACTIONS.firstOrNull { it.id == u.faction }
            if (faction != null) {
                val shadow = Paint().apply {
                    color = AColor.argb(110,
                        AColor.red(faction.dark.toArgb()) and 0xFF,
                        AColor.green(faction.dark.toArgb()) and 0xFF,
                        AColor.blue(faction.dark.toArgb()) and 0xFF,
                    )
                }
                canvas.drawOval(sx - 14 * camera.zoom, sy - 4 * camera.zoom,
                    sx + 14 * camera.zoom, sy + 4 * camera.zoom, shadow)
            }

            val moving = u.state == UnitState.MOVING || u.state == UnitState.GATHERING ||
                u.state == UnitState.RETURNING
            val attacking = u.state == UnitState.ATTACKING
            val key = when {
                attacking && u.type != UnitType.WORKER -> AssetPaths.unitAttack(u.type, factionKey)
                moving -> AssetPaths.unitRun(u.type, factionKey)
                else -> AssetPaths.unitIdle(u.type, factionKey)
            }
            val sprite = cache.bitmap(key)
            if (sprite != null) drawAnimatedSprite(canvas, sprite, sx, sy, u.animTime, u.facing,
                size = 48f * camera.zoom)
            else {
                // Fallback: solid colored circle so debug builds show something
                val p = Paint().apply { color = (faction?.color?.toArgb()) ?: AColor.WHITE }
                canvas.drawCircle(sx, sy, 12f * camera.zoom, p)
            }

            if (u.hp < u.maxHp) {
                drawTinyBar(canvas, sx, sy - 30 * camera.zoom, 28f * camera.zoom,
                    u.hp / u.maxHp, AColor.rgb(220, 60, 60))
            }
            if (u.id in selectedIds) drawSelectionRing(canvas, sx, sy + 6 * camera.zoom,
                18f * camera.zoom)
            if (u.carryAmount > 0) {
                val pp = Paint().apply { color = when (u.carry) {
                    ResourceType.WOOD -> AColor.rgb(156, 203, 119)
                    ResourceType.GOLD -> AColor.rgb(247, 220, 98)
                    ResourceType.FOOD -> AColor.rgb(246, 161, 103)
                    null -> AColor.WHITE
                } }
                canvas.drawCircle(sx + 12 * camera.zoom, sy - 18 * camera.zoom, 4f * camera.zoom, pp)
            }
        }
    }

    private fun drawAnimatedSprite(
        canvas: Canvas, sprite: Bitmap, sx: Float, sy: Float, animTime: Float,
        facing: Float, size: Float,
    ) {
        // Sprite sheets are 6 frames × 1 row at 192×192 each (Tiny Swords pack convention).
        val frameW = 192
        val frameH = 192
        val frames = max(1, sprite.width / frameW)
        val frame = ((animTime * 8f).toInt() % frames).coerceAtLeast(0)
        srcRect.set(frame * frameW, 0, frame * frameW + frameW, frameH)
        val half = size * 0.55f
        if (facing < 0f) {
            canvas.save()
            canvas.scale(-1f, 1f, sx, sy)
            dstRect.set(sx - half, sy - size, sx + half, sy + size * 0.18f)
            canvas.drawBitmap(sprite, srcRect, dstRect, pixelPaint)
            canvas.restore()
        } else {
            dstRect.set(sx - half, sy - size, sx + half, sy + size * 0.18f)
            canvas.drawBitmap(sprite, srcRect, dstRect, pixelPaint)
        }
    }

    private fun drawProjectiles(canvas: Canvas, state: GameState, camera: Camera) {
        val arrow = Paint().apply { color = AColor.rgb(255, 230, 180); strokeWidth = 2f }
        for (p in state.projectiles) {
            val sx = (p.x - camera.x) * camera.zoom
            val sy = (p.y - camera.y) * camera.zoom
            val dx = p.vx; val dy = p.vy
            val len = max(0.0001f, kotlin.math.hypot(dx, dy))
            canvas.drawLine(sx, sy, sx + dx / len * 12f, sy + dy / len * 12f, arrow)
        }
    }

    private fun drawEffects(canvas: Canvas, state: GameState, camera: Camera) {
        for (e in state.effects) {
            val sx = (e.x - camera.x) * camera.zoom
            val sy = (e.y - camera.y) * camera.zoom
            val t = (e.time / e.max).coerceIn(0f, 1f)
            when (e.kind) {
                EffectKind.HIT -> {
                    val p = Paint().apply { color = AColor.argb((255 * t).toInt(), 255, 80, 80) }
                    canvas.drawCircle(sx, sy, 6f + (1f - t) * 10f, p)
                }
                EffectKind.HEAL -> {
                    val p = Paint().apply { color = AColor.argb((180 * t).toInt(), 120, 220, 120) }
                    canvas.drawCircle(sx, sy, 8f + (1f - t) * 14f, p)
                }
                EffectKind.EXPLOSION -> {
                    val p = Paint().apply { color = AColor.argb((220 * t).toInt(), 250, 200, 80) }
                    canvas.drawCircle(sx, sy, 10f + (1f - t) * 22f, p)
                }
                EffectKind.FOUNDATION -> {
                    val p = Paint().apply { color = AColor.argb(120, 220, 180, 90) }
                    canvas.drawRect(sx - 18f, sy - 12f, sx + 18f, sy + 12f, p)
                }
            }
        }
    }

    private fun drawPlacementGhost(
        canvas: Canvas, state: GameState, camera: Camera,
        type: com.tinyswords.realmwar.game.BuildingType, wx: Float, wy: Float,
    ) {
        val sx = (wx - camera.x) * camera.zoom
        val sy = (wy - camera.y) * camera.zoom
        val def = BUILDING_DEFS[type]!!
        val ok = state.isLand(wx, wy)
        val p = Paint().apply {
            color = if (ok) AColor.argb(180, 80, 200, 110) else AColor.argb(180, 220, 70, 70)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(
            sx - def.placeW / 2f * camera.zoom,
            sy - def.placeH / 2f * camera.zoom + def.placeYOffset * camera.zoom,
            sx + def.placeW / 2f * camera.zoom,
            sy + def.placeH / 2f * camera.zoom + def.placeYOffset * camera.zoom,
            p,
        )
    }

    private fun drawSprite(canvas: Canvas, key: String, sx: Float, sy: Float, w: Float, h: Float, scale: Float) {
        val sprite = cache.bitmap(key) ?: return
        val drawW = sprite.width * scale
        val drawH = sprite.height * scale
        srcRect.set(0, 0, sprite.width, sprite.height)
        dstRect.set(sx - drawW / 2f, sy - drawH * 0.85f, sx + drawW / 2f, sy + drawH * 0.15f)
        canvas.drawBitmap(sprite, srcRect, dstRect, pixelPaint)
    }

    private fun drawTinyBar(canvas: Canvas, cx: Float, cy: Float, w: Float, pct: Float, color: Int) {
        val barH = max(2f, w * 0.12f)
        val bg = Paint().apply { this.color = AColor.argb(180, 0, 0, 0) }
        canvas.drawRect(cx - w / 2f, cy, cx + w / 2f, cy + barH, bg)
        val fg = Paint().apply { this.color = color }
        canvas.drawRect(cx - w / 2f, cy, cx - w / 2f + w * pct.coerceIn(0f, 1f), cy + barH, fg)
    }

    private fun drawSelectionRing(canvas: Canvas, sx: Float, sy: Float, r: Float) {
        val p = Paint().apply { color = AColor.argb(180, 255, 240, 120); style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawOval(sx - r, sy - r * 0.4f, sx + r, sy + r * 0.4f, p)
    }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
    val a = (alpha * 255 + 0.5f).toInt()
    val r = (red * 255 + 0.5f).toInt()
    val g = (green * 255 + 0.5f).toInt()
    val b = (blue * 255 + 0.5f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
