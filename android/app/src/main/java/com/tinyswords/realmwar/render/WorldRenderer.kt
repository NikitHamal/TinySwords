package com.tinyswords.realmwar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import com.tinyswords.realmwar.game.Building
import com.tinyswords.realmwar.game.Decor
import com.tinyswords.realmwar.game.FxEffect
import com.tinyswords.realmwar.game.GameConfig
import com.tinyswords.realmwar.game.GameWorld
import com.tinyswords.realmwar.game.Projectile
import com.tinyswords.realmwar.game.Resource
import com.tinyswords.realmwar.game.ResourceType
import com.tinyswords.realmwar.game.Unit
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Native android.graphics.Canvas renderer. We render the world from a top-down RTS camera using
 * the imported pixel-art sprites at integer scaling. Compose draws this via AndroidView into a
 * SurfaceView for performance.
 */
class WorldRenderer(
    private val assets: AssetLibrary
) {
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val groundPaint = Paint().apply { isAntiAlias = false }
    private val waterPaint = Paint().apply { color = Color.parseColor("#3370a8") }
    private val deepWaterPaint = Paint().apply { color = Color.parseColor("#264f78") }
    private val shadowPaint = Paint().apply { color = Color.parseColor("#552a2118"); isAntiAlias = true }
    private val outlinePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.YELLOW }
    private val healthBg = Paint().apply { color = Color.parseColor("#1a1a1a") }
    private val healthFill = Paint().apply { color = Color.parseColor("#3edc6c") }
    private val healthBad = Paint().apply { color = Color.parseColor("#dc3e3e") }
    private val labelPaint = Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true; setShadowLayer(2f, 0f, 1f, Color.BLACK) }
    private val drawFilter = PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0)
    private val tmpRectSrc = Rect()
    private val tmpRectDst = RectF()
    private val tmpDrawables = ArrayList<Drawable>(2048)

    private data class Drawable(
        val baseY: Float,
        val drawer: (Canvas) -> kotlin.Unit
    )

    fun render(canvas: Canvas, world: GameWorld, camera: Camera, viewW: Float, viewH: Float, selectedIds: Set<Long>, placement: PlacementGhost?) {
        canvas.drawFilter = drawFilter
        // Background fill - deep water everywhere then we overlay land tiles
        canvas.drawColor(Color.parseColor("#264f78"))

        drawTerrain(canvas, world, camera, viewW, viewH)
        collectAndDrawEntities(canvas, world, camera, viewW, viewH, selectedIds)
        drawProjectiles(canvas, world.projectiles, camera)
        drawEffects(canvas, world.effects, camera)
        placement?.let { drawPlacement(canvas, world, it, camera) }
    }

    private fun drawTerrain(canvas: Canvas, world: GameWorld, camera: Camera, viewW: Float, viewH: Float) {
        val tile = world.tile.toFloat()
        val zoom = camera.zoom
        val drawTile = tile * zoom
        val minTx = max(0, (camera.x / tile).toInt() - 1)
        val maxTx = min(world.cols - 1, ((camera.x + viewW / zoom) / tile).toInt() + 1)
        val minTy = max(0, (camera.y / tile).toInt() - 1)
        val maxTy = min(world.rows - 1, ((camera.y + viewH / zoom) / tile).toInt() + 1)

        groundPaint.color = Color.parseColor("#69a44f")
        val edgePaint = Paint().apply { color = Color.parseColor("#447236") }
        for (ty in minTy..maxTy) for (tx in minTx..maxTx) {
            if (world.landMap[ty * world.cols + tx].toInt() == 0) continue
            val sx = (tx * tile - camera.x) * zoom
            val sy = (ty * tile - camera.y) * zoom
            tmpRectDst.set(sx, sy, sx + drawTile, sy + drawTile)
            canvas.drawRect(tmpRectDst, groundPaint)
            // Edge variations: darker if neighbor is water
            val isEdge = neighborWater(world, tx, ty)
            if (isEdge) {
                canvas.drawRect(tmpRectDst, edgePaint)
                groundPaint.color = Color.parseColor("#69a44f")
            }
        }
    }

    private fun neighborWater(world: GameWorld, tx: Int, ty: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = tx + dx
            val ny = ty + dy
            if (nx < 0 || ny < 0 || nx >= world.cols || ny >= world.rows) return true
            if (world.landMap[ny * world.cols + nx].toInt() == 0) return true
        }
        return false
    }

    private fun collectAndDrawEntities(canvas: Canvas, world: GameWorld, camera: Camera, viewW: Float, viewH: Float, selectedIds: Set<Long>) {
        tmpDrawables.clear()
        val zoom = camera.zoom
        val cam = camera

        // decor first (also part of y-sort)
        for (d in world.decor) {
            val sx = (d.x - cam.x) * zoom
            val sy = (d.y - cam.y) * zoom
            if (sx < -64 || sy < -64 || sx > viewW + 64 || sy > viewH + 64) continue
            tmpDrawables.add(Drawable(d.y) { c -> drawDecor(c, d, sx, sy, zoom) })
        }
        for (r in world.resources) {
            val sx = (r.x - cam.x) * zoom
            val sy = (r.y - cam.y) * zoom
            if (sx < -96 || sy < -96 || sx > viewW + 96 || sy > viewH + 96) continue
            tmpDrawables.add(Drawable(r.y) { c -> drawResource(c, r, sx, sy, zoom, world.elapsedTime) })
        }
        for (b in world.buildings) {
            val sx = (b.x - cam.x) * zoom
            val sy = (b.y - cam.y) * zoom
            if (sx < -160 || sy < -200 || sx > viewW + 160 || sy > viewH + 200) continue
            tmpDrawables.add(Drawable(b.y + b.def.h * 0.4f) { c -> drawBuilding(c, b, sx, sy, zoom, selectedIds.contains(b.id), world) })
        }
        for (u in world.units) {
            val sx = (u.x - cam.x) * zoom
            val sy = (u.y - cam.y) * zoom
            if (sx < -128 || sy < -128 || sx > viewW + 128 || sy > viewH + 128) continue
            tmpDrawables.add(Drawable(u.y) { c -> drawUnit(c, u, sx, sy, zoom, selectedIds.contains(u.id), world) })
        }
        tmpDrawables.sortBy { it.baseY }
        for (d in tmpDrawables) d.drawer(canvas)
    }

    private fun drawDecor(canvas: Canvas, d: Decor, sx: Float, sy: Float, zoom: Float) {
        val bmp = assets.decor(d.kind)
        if (bmp != null) {
            val w = bmp.width * zoom * d.scale
            val h = bmp.height * zoom * d.scale
            tmpRectDst.set(sx - w / 2f, sy - h * 0.85f, sx + w / 2f, sy + h * 0.15f)
            tmpRectSrc.set(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
        } else {
            paint.color = Color.parseColor("#4a6f3b")
            canvas.drawCircle(sx, sy, 12f * zoom, paint)
        }
    }

    private fun drawResource(canvas: Canvas, r: Resource, sx: Float, sy: Float, zoom: Float, time: Float) {
        if (r.isAnimal) {
            drawAnimal(canvas, r, sx, sy, zoom, time)
            return
        }
        val key = when (r.type) { ResourceType.TREE -> "tree"; ResourceType.GOLD -> "gold"; ResourceType.FOOD -> "food" }
        val bmp = assets.resource(key)
        // ground shadow
        canvas.drawOval(RectF(sx - 18f * zoom, sy - 6f * zoom, sx + 18f * zoom, sy + 6f * zoom), shadowPaint)
        if (bmp != null) {
            val scale = when (r.type) { ResourceType.TREE -> 0.65f * zoom; ResourceType.GOLD -> 0.65f * zoom; ResourceType.FOOD -> 0.7f * zoom }
            val w = bmp.width * scale
            val h = bmp.height * scale
            tmpRectDst.set(sx - w / 2f, sy - h * 0.85f, sx + w / 2f, sy + h * 0.15f)
            tmpRectSrc.set(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
        }
    }

    private fun drawAnimal(canvas: Canvas, r: Resource, sx: Float, sy: Float, zoom: Float, time: Float) {
        val animal = r.animal ?: return
        val key = animal.def.key
        val moving = animal.moveT < 1.4f
        val running = animal.panicTimer > 0f
        val action = when {
            running -> "run"
            moving -> "walk"
            else -> "idle"
        }
        val bmp = assets.animal(key, action)
        canvas.drawOval(RectF(sx - 14f * zoom, sy - 4f * zoom, sx + 14f * zoom, sy + 4f * zoom), shadowPaint)
        if (bmp != null) {
            val frameH = bmp.height / 4
            val frameW = if (bmp.width >= 64) detectFrameWidth(bmp, frameH) else bmp.width
            val cols = (bmp.width / frameW).coerceAtLeast(1)
            val frame = ((time * (if (running) 9f else 5f)).toInt() % cols).coerceAtLeast(0)
            val row = animal.facing.coerceIn(0, 3)
            val srcLeft = frame * frameW
            val srcTop = row * frameH
            tmpRectSrc.set(srcLeft, srcTop, srcLeft + frameW, srcTop + frameH)
            val scale = animal.def.weight * 1.2f * zoom
            val w = frameW * scale
            val h = frameH * scale
            tmpRectDst.set(sx - w / 2f, sy - h * 0.85f, sx + w / 2f, sy + h * 0.15f)
            if (animal.hurtFlash > 0f) {
                paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#cc4040"), PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
            paint.colorFilter = null
        }
    }

    private fun detectFrameWidth(bmp: Bitmap, frameH: Int): Int {
        // Most CraftPix sheets are square frames laid in a row. Use bitmap height/4 as a fair guess
        return frameH
    }

    private fun drawBuilding(canvas: Canvas, b: Building, sx: Float, sy: Float, zoom: Float, selected: Boolean, world: GameWorld) {
        val faction = GameConfig.FACTIONS[b.factionId]
        val bmp = assets.building(faction, b.type)
        // selection halo
        if (selected) {
            outlinePaint.color = Color.YELLOW
            canvas.drawCircle(sx, sy + b.def.placeYOffset * zoom, b.radius * zoom + 6f, outlinePaint)
        }
        // ground shadow
        val placeW = b.def.placeW * zoom * 0.95f
        val placeH = b.def.placeH * zoom * 0.7f
        canvas.drawOval(RectF(sx - placeW / 2f, sy + b.def.placeYOffset * zoom - placeH / 2f, sx + placeW / 2f, sy + b.def.placeYOffset * zoom + placeH / 2f), shadowPaint)
        if (bmp != null) {
            val scale = zoom * 0.5f
            val w = bmp.width * scale
            val h = bmp.height * scale
            tmpRectDst.set(sx - w / 2f, sy - h * 0.65f, sx + w / 2f, sy + h * 0.35f)
            tmpRectSrc.set(0, 0, bmp.width, bmp.height)
            if (!b.built) {
                paint.alpha = (160 + (b.buildProgress * 95f).toInt()).coerceAtMost(255)
            }
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
            paint.alpha = 255
        } else {
            paint.color = faction.color.toInt()
            canvas.drawRect(sx - 30f * zoom, sy - 30f * zoom, sx + 30f * zoom, sy + 30f * zoom, paint)
        }
        // health bar
        if (b.hp < b.maxHp) {
            val barW = (b.def.placeW * zoom * 0.9f).coerceAtLeast(40f)
            val barH = 4f * zoom
            val left = sx - barW / 2f
            val top = sy - b.def.h * zoom * 0.55f
            canvas.drawRect(left, top, left + barW, top + barH, healthBg)
            val pct = (b.hp / b.maxHp).coerceIn(0f, 1f)
            canvas.drawRect(left, top, left + barW * pct, top + barH, if (pct > 0.4f) healthFill else healthBad)
        }
        // production progress
        b.production?.let { prod ->
            val pw = b.def.placeW * zoom * 0.9f
            val left = sx - pw / 2f
            val top = sy - b.def.h * zoom * 0.6f - 6f * zoom
            val pct = (prod.time / prod.total).coerceIn(0f, 1f)
            canvas.drawRect(left, top, left + pw, top + 3f * zoom, healthBg)
            paint.color = Color.parseColor("#f7dc62")
            canvas.drawRect(left, top, left + pw * pct, top + 3f * zoom, paint)
        }
    }

    private fun drawUnit(canvas: Canvas, u: Unit, sx: Float, sy: Float, zoom: Float, selected: Boolean, world: GameWorld) {
        val faction = GameConfig.FACTIONS[u.factionId]
        // shadow
        canvas.drawOval(RectF(sx - 12f * zoom, sy - 4f * zoom, sx + 12f * zoom, sy + 4f * zoom), shadowPaint)
        // selection ring
        if (selected) {
            outlinePaint.color = if (u.factionId == 0) Color.YELLOW else Color.parseColor("#f06464")
            canvas.drawCircle(sx, sy, u.radius * zoom + 4f, outlinePaint)
        }
        val moving = u.vx != 0f || u.vy != 0f
        val attacking = u.attackCooldown > u.def.cooldown * 0.6f
        val carrying = u.carryAmount > 0
        val action = when {
            attacking -> "attack"
            carrying -> u.carryType ?: "run"
            moving -> "run"
            else -> "idle"
        }
        val bmp = assets.unit(faction, u.type, action)
        if (bmp != null) {
            // Tiny Swords character sheets are usually horizontal strips; we sample one frame
            // sized to the sheet height. This keeps animations playable across the asset pack
            // without per-sprite metadata (we never assume 4-row layouts here).
            val frameSize = bmp.height
            val cols = (bmp.width / frameSize).coerceAtLeast(1)
            val frame = ((u.animTime * 8f).toInt() % cols).coerceAtLeast(0)
            tmpRectSrc.set(frame * frameSize, 0, frame * frameSize + frameSize, frameSize)
            val scale = zoom * 0.36f
            val w = frameSize * scale
            val h = frameSize * scale
            val flip = u.facing == 2
            if (flip) {
                canvas.save()
                canvas.scale(-1f, 1f, sx, sy)
                tmpRectDst.set(sx - w / 2f, sy - h * 0.8f, sx + w / 2f, sy + h * 0.20f)
            } else {
                tmpRectDst.set(sx - w / 2f, sy - h * 0.8f, sx + w / 2f, sy + h * 0.20f)
            }
            if (u.hurtFlash > 0f) {
                paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#aacc4040"), PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
            paint.colorFilter = null
            if (flip) canvas.restore()
        } else {
            paint.color = faction.color.toInt()
            canvas.drawCircle(sx, sy, 10f * zoom, paint)
        }
        // health bar
        if (u.hp < u.maxHp) {
            val barW = 28f * zoom
            val barH = 3f * zoom
            val left = sx - barW / 2f
            val top = sy - 26f * zoom
            canvas.drawRect(left, top, left + barW, top + barH, healthBg)
            val pct = (u.hp / u.maxHp).coerceIn(0f, 1f)
            canvas.drawRect(left, top, left + barW * pct, top + barH, if (pct > 0.4f) healthFill else healthBad)
        }
        // faction color pip
        paint.color = faction.color.toInt()
        canvas.drawCircle(sx, sy + 10f * zoom, 2.5f * zoom, paint)
    }

    private fun drawProjectiles(canvas: Canvas, projectiles: List<Projectile>, camera: Camera) {
        for (p in projectiles) {
            val travel = (p.speed * p.t).coerceAtMost(p.totalDist)
            val t = if (p.totalDist > 0) travel / p.totalDist else 0f
            val px = p.x + (p.tx - p.x) * t
            val py = p.y + (p.ty - p.y) * t
            val sx = (px - camera.x) * camera.zoom
            val sy = (py - camera.y) * camera.zoom
            val faction = GameConfig.FACTIONS[p.factionId]
            paint.color = faction.color.toInt()
            val arrowBmp = assets.arrow(faction)
            if (arrowBmp != null) {
                val angle = atan2(p.ty - p.y, p.tx - p.x) * 180f / Math.PI.toFloat()
                canvas.save()
                canvas.translate(sx, sy)
                canvas.rotate(angle)
                val s = camera.zoom * 0.4f
                tmpRectSrc.set(0, 0, arrowBmp.width, arrowBmp.height)
                tmpRectDst.set(-arrowBmp.width * s / 2f, -arrowBmp.height * s / 2f, arrowBmp.width * s / 2f, arrowBmp.height * s / 2f)
                canvas.drawBitmap(arrowBmp, tmpRectSrc, tmpRectDst, paint)
                canvas.restore()
            } else {
                canvas.drawCircle(sx, sy, 3f * camera.zoom, paint)
            }
        }
    }

    private fun drawEffects(canvas: Canvas, effects: List<FxEffect>, camera: Camera) {
        for (fx in effects) {
            val pct = (fx.t / fx.duration).coerceIn(0f, 1f)
            val sx = (fx.x - camera.x) * camera.zoom
            val sy = (fx.y - camera.y) * camera.zoom
            val color = when (fx.kind) {
                "explosion" -> Color.parseColor("#ffba49")
                "swing" -> Color.parseColor("#ffe066")
                "hit" -> Color.parseColor("#ff5050")
                "heal" -> Color.parseColor("#7cf2c8")
                else -> Color.WHITE
            }
            paint.color = color
            paint.alpha = (255f * (1f - pct)).toInt().coerceIn(0, 255)
            val r = 18f * camera.zoom * (0.4f + pct)
            canvas.drawCircle(sx, sy, r, paint)
            paint.alpha = 255
        }
    }

    private fun drawPlacement(canvas: Canvas, world: GameWorld, p: PlacementGhost, camera: Camera) {
        val def = GameConfig.BUILDINGS[p.type] ?: return
        val sx = (p.x - camera.x) * camera.zoom
        val sy = (p.y - camera.y) * camera.zoom
        val bmp = assets.building(GameConfig.FACTIONS[0], p.type)
        if (bmp != null) {
            val scale = camera.zoom * 0.5f
            val w = bmp.width * scale
            val h = bmp.height * scale
            tmpRectDst.set(sx - w / 2f, sy - h * 0.65f, sx + w / 2f, sy + h * 0.35f)
            tmpRectSrc.set(0, 0, bmp.width, bmp.height)
            paint.alpha = 160
            canvas.drawBitmap(bmp, tmpRectSrc, tmpRectDst, paint)
            paint.alpha = 255
        }
        val placeW = def.placeW * camera.zoom
        val placeH = def.placeH * camera.zoom
        outlinePaint.color = if (p.valid) Color.parseColor("#7cf2a4") else Color.parseColor("#f25656")
        canvas.drawRect(sx - placeW / 2f, sy + def.placeYOffset * camera.zoom - placeH / 2f, sx + placeW / 2f, sy + def.placeYOffset * camera.zoom + placeH / 2f, outlinePaint)
    }
}

class Camera(
    var x: Float = 0f,
    var y: Float = 0f,
    var zoom: Float = 1f
)

data class PlacementGhost(
    val type: String,
    val x: Float,
    val y: Float,
    val valid: Boolean
)

