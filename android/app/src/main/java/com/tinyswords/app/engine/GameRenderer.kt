package com.tinyswords.app.engine

import android.graphics.*
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.clamp
import com.tinyswords.app.util.dist2
import kotlin.math.*

class GameRenderer(private val assets: AssetManager) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f
        typeface = Typeface.MONOSPACE
    }
    private val shadowPaint = Paint().apply {
        color = Color.argb(115, 0, 0, 0)
    }
    private val selectionPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dragSelectPaint = Paint().apply {
        color = Color.argb(60, 100, 255, 100)
        style = Paint.Style.FILL
    }
    private val dragSelectBorderPaint = Paint().apply {
        color = Color.argb(200, 100, 255, 100)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val hpBarBgPaint = Paint().apply { color = Color.argb(180, 30, 30, 30) }
    private val hpBarPaint = Paint().apply { color = Color.GREEN }

    private val srcRect = Rect()
    private val dstRect = RectF()

    // Biome tile keys
    private val biomeTileKeys = arrayOf("tileGrass", "tileWarm", "tileAlt", "tileMoss", "tileDeep")

    fun render(canvas: Canvas, state: GameState, viewW: Float, viewH: Float) {
        canvas.save()
        canvas.drawColor(Color.parseColor("#1a3a4a")) // Water base color

        val cam = state.camera
        val zoom = cam.zoom
        val camLeft = cam.x - (viewW / 2f) / zoom
        val camTop = cam.y - (viewH / 2f) / zoom
        val camRight = cam.x + (viewW / 2f) / zoom
        val camBottom = cam.y + (viewH / 2f) / zoom

        // Apply camera transform
        canvas.translate(viewW / 2f, viewH / 2f)
        canvas.scale(zoom, zoom)
        canvas.translate(-cam.x, -cam.y)

        // Draw terrain
        drawTerrain(canvas, state, camLeft, camTop, camRight, camBottom)

        // Collect visible drawable entities, sorted by Y for depth
        val drawables = mutableListOf<DrawableEntity>()
        collectDrawables(state, drawables, camLeft - 100f, camTop - 100f, camRight + 100f, camBottom + 100f)
        drawables.sortBy { it.sortY }

        // Draw ground shadows first
        for (d in drawables) {
            if (!d.isSky) drawShadow(canvas, d)
        }

        // Draw entities
        for (d in drawables) {
            if (!d.isSky) drawEntity(canvas, state, d)
        }

        // Draw projectiles
        drawProjectiles(canvas, state)

        // Draw effects
        drawEffects(canvas, state)

        // Draw selection box/markers
        drawSelection(canvas, state)

        // Draw sky entities (clouds)
        for (d in drawables) {
            if (d.isSky) drawEntity(canvas, state, d)
        }

        // Draw placement ghost
        if (state.placingBuilding != null) {
            drawPlacementGhost(canvas, state)
        }

        canvas.restore()
    }

    private fun drawTerrain(canvas: Canvas, state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        val startCol = (left / TILE).toInt().coerceAtLeast(0)
        val endCol = (right / TILE).toInt().coerceAtMost(state.landCols - 1)
        val startRow = (top / TILE).toInt().coerceAtLeast(0)
        val endRow = (bottom / TILE).toInt().coerceAtMost(state.landRows - 1)

        val waterColor = Color.parseColor("#2a5a6a")
        val waterPaint = Paint().apply { color = waterColor }

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val idx = row * state.landCols + col
                val isLand = state.landMap[idx].toInt() == 1
                val tx = col * TILE
                val ty = row * TILE

                if (!isLand) {
                    // Draw water tile
                    drawWaterTile(canvas, tx, ty, state.time)
                } else {
                    // Draw grass tile with biome color
                    val biome = state.biomeMap.getOrElse(idx) { 0 }
                    val biomeKey = biomeTileKeys.getOrElse(biome.coerceIn(0, 4)) { "tileGrass" }
                    drawGrassTile(canvas, tx, ty, biomeKey, col, row)
                }
            }
        }
    }

    private fun drawWaterTile(canvas: Canvas, tx: Float, ty: Float, time: Float) {
        val waterBitmap = assets.get("water")
        if (waterBitmap != null) {
            val frameSize = 64
            val frame = ((time * 5.4f).toInt() % 4).coerceIn(0, 3)
            srcRect.set(frame * frameSize, 0, (frame + 1) * frameSize, frameSize)
            dstRect.set(tx, ty, tx + TILE, ty + TILE)
            canvas.drawBitmap(waterBitmap, srcRect, dstRect, pixelPaint)
        } else {
            val p = Paint().apply { color = Color.parseColor("#2a5a6a") }
            canvas.drawRect(tx, ty, tx + TILE, ty + TILE, p)
        }

        // Water foam on edges
        drawWaterFoam(canvas, tx, ty, time)
    }

    private fun drawWaterFoam(canvas: Canvas, tx: Float, ty: Float, time: Float) {
        val foam = assets.get("waterFoam") ?: return
        val cols = (tx / TILE).toInt()
        val rows = (ty / TILE).toInt()
        val hash = (cols * 31 + rows * 17) and 15
        val frame = ((time * 5.4f).toInt() + hash) % 16
        val fw = foam.width / 16
        val fh = foam.height

        if (fw > 0 && fh > 0) {
            srcRect.set(frame * fw, 0, (frame + 1) * fw, fh)
            dstRect.set(tx - 8f, ty - 8f, tx + TILE + 8f, ty + TILE + 8f)
            canvas.drawBitmap(foam, srcRect, dstRect, pixelPaint)
        }
    }

    private fun drawGrassTile(canvas: Canvas, tx: Float, ty: Float, biomeKey: String, col: Int, row: Int) {
        val tileBitmap = assets.get(biomeKey)
        if (tileBitmap != null) {
            val tilesPerRow = tileBitmap.width / 64
            if (tilesPerRow > 0) {
                // Select tile variant based on position hash
                val variant = ((col * 7 + row * 13) % tilesPerRow.coerceAtLeast(1)).coerceAtLeast(0)
                val sx = (variant % tilesPerRow) * 64
                val sy = 0
                srcRect.set(sx, sy, sx + 64, sy + 64)
                dstRect.set(tx, ty, tx + TILE, ty + TILE)
                canvas.drawBitmap(tileBitmap, srcRect, dstRect, pixelPaint)
            }
        } else {
            val colors = mapOf(
                "tileGrass" to Color.parseColor("#5a9a30"),
                "tileWarm" to Color.parseColor("#8a7030"),
                "tileAlt" to Color.parseColor("#6a8a20"),
                "tileMoss" to Color.parseColor("#4a7040"),
                "tileDeep" to Color.parseColor("#3a5a3a")
            )
            val p = Paint().apply { color = colors[biomeKey] ?: Color.GREEN }
            canvas.drawRect(tx, ty, tx + TILE, ty + TILE, p)
        }
    }

    data class DrawableEntity(
        val entity: GameEntity,
        val sortY: Float,
        val isSky: Boolean = false
    )

    private fun collectDrawables(state: GameState, list: MutableList<DrawableEntity>,
                                  left: Float, top: Float, right: Float, bottom: Float) {
        // Resources
        for (r in state.resources) {
            if (r.dead) continue
            if (r.x < left || r.x > right || r.y < top || r.y > bottom) continue
            list.add(DrawableEntity(r, r.y))
        }

        // Decor
        for (d in state.decor) {
            if (d.dead) continue
            if (d.isSky) {
                list.add(DrawableEntity(d, d.y, isSky = true))
                continue
            }
            if (d.x < left || d.x > right || d.y < top || d.y > bottom) continue
            list.add(DrawableEntity(d, d.y))
        }

        // Buildings
        for (b in state.buildings) {
            if (b.dead) continue
            val bdef = BUILDINGS[b.type] ?: continue
            if (b.x < left - bdef.w || b.x > right + bdef.w || b.y < top - bdef.h || b.y > bottom + bdef.h) continue
            list.add(DrawableEntity(b, b.y + bdef.h * 0.34f))
        }

        // Units
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            if (u.x < left || u.x > right || u.y < top || u.y > bottom) continue
            list.add(DrawableEntity(u, u.y))
        }
    }

    private fun drawShadow(canvas: Canvas, d: DrawableEntity) {
        val e = d.entity
        val sx: Float
        val sy: Float
        val sw: Float
        val sh: Float

        when (e) {
            is GameUnit -> {
                val def = UNITS[e.type] ?: return
                val scale = def.scale * SPRITE_BOOST
                sw = def.radius * 2.5f * scale
                sh = def.radius * 1.2f * scale
                sx = e.x - sw / 2 + def.shadowX * scale * 0.3f
                sy = e.y - sh / 2 + def.shadowY * scale * 0.3f
            }
            is GameBuilding -> {
                val def = BUILDINGS[e.type] ?: return
                sw = def.placeW * 0.8f
                sh = def.placeH * 0.4f
                sx = e.x - sw / 2
                sy = e.y + def.placeYOffset * 0.3f - sh / 2
            }
            is GameResource -> {
                sw = 30f
                sh = 14f
                sx = e.x - sw / 2
                sy = e.y - sh / 2 + 4f
            }
            is GameDecor -> {
                sw = 24f
                sh = 10f
                sx = e.x - sw / 2
                sy = e.y - sh / 2
            }
            else -> return
        }

        canvas.drawOval(sx, sy, sx + sw, sy + sh, shadowPaint)
    }

    private fun drawEntity(canvas: Canvas, state: GameState, d: DrawableEntity) {
        when (val e = d.entity) {
            is GameUnit -> drawUnit(canvas, state, e)
            is GameBuilding -> drawBuilding(canvas, state, e)
            is GameResource -> drawResource(canvas, state, e)
            is GameDecor -> drawDecor(canvas, state, e)
        }
    }

    private fun drawUnit(canvas: Canvas, state: GameState, unit: GameUnit) {
        val def = UNITS[unit.type] ?: return
        val fKey = FACTIONS.getOrNull(unit.faction)?.key ?: "blue"
        val scale = def.scale * SPRITE_BOOST

        // Determine animation key
        val animKey = getUnitAnimKey(unit, fKey)
        val sprite = assets.get(animKey)

        if (sprite != null) {
            val fw = def.fw
            val fh = def.fh
            val framesInSheet = sprite.width / fw
            val fps = when (unit.order) {
                UnitOrder.IDLE -> 4f
                UnitOrder.MOVE, UnitOrder.ATTACK_MOVE -> 8f
                UnitOrder.ATTACK -> 6f
                UnitOrder.HARVEST -> 6f
                UnitOrder.REPAIR -> 6f
                else -> 4f
            }
            val frame = ((unit.animTime * fps).toInt() % framesInSheet.coerceAtLeast(1))

            val drawW = fw * scale
            val drawH = fh * scale
            val drawX = unit.x - drawW / 2
            val drawY = unit.y - drawH + def.radius * scale + def.drawYOffset * scale

            // Flash effect
            if (unit.flash > 0f) {
                paint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            }

            srcRect.set(frame * fw, 0, (frame + 1) * fw, fh)

            // Flip based on facing
            if (unit.face < 0) {
                canvas.save()
                canvas.scale(-1f, 1f, unit.x, unit.y)
                dstRect.set(drawX, drawY, drawX + drawW, drawY + drawH)
                canvas.drawBitmap(sprite, srcRect, dstRect, paint)
                canvas.restore()
            } else {
                dstRect.set(drawX, drawY, drawX + drawW, drawY + drawH)
                canvas.drawBitmap(sprite, srcRect, dstRect, paint)
            }

            paint.colorFilter = null
        } else {
            // Fallback: colored circle
            val fColor = FACTIONS.getOrNull(unit.faction)?.color ?: Color.BLUE
            val p = Paint().apply { color = fColor }
            canvas.drawCircle(unit.x, unit.y, def.radius * scale, p)
        }

        // Selection indicator
        if (unit.selected) {
            val selR = def.radius * scale * 1.5f
            selectionPaint.color = Color.argb(160, 255, 255, 255)
            canvas.drawCircle(unit.x, unit.y + 2f, selR, selectionPaint)
        }

        // HP bar
        if (unit.hp < unit.maxHp || unit.selected) {
            drawHpBar(canvas, unit.x, unit.y - def.fh * scale * 0.4f, unit.hp.toFloat() / unit.maxHp, 32f)
        }
    }

    private fun getUnitAnimKey(unit: GameUnit, fKey: String): String {
        return when (unit.type) {
            "worker" -> {
                val base = "u_${fKey}_worker"
                when {
                    unit.carrying != null && unit.order != UnitOrder.IDLE -> {
                        "${base}_carry_${unit.carrying}"
                    }
                    unit.carrying != null -> "${base}_idle_${unit.carrying}"
                    unit.order == UnitOrder.HARVEST -> {
                        val target = unit.target
                        if (target is GameResource) {
                            when (target.type) {
                                ResourceType.TREE -> "${base}_chop"
                                ResourceType.GOLD -> "${base}_mine"
                                ResourceType.FOOD -> "${base}_fight"
                            }
                        } else "${base}_idle"
                    }
                    unit.order == UnitOrder.REPAIR -> "${base}_build"
                    unit.order == UnitOrder.MOVE || unit.order == UnitOrder.ATTACK_MOVE -> "${base}_run"
                    unit.order == UnitOrder.ATTACK -> "${base}_fight"
                    else -> "${base}_idle"
                }
            }
            "warrior" -> {
                val base = "u_${fKey}_warrior"
                when (unit.order) {
                    UnitOrder.ATTACK -> "${base}_attack"
                    UnitOrder.MOVE, UnitOrder.ATTACK_MOVE -> "${base}_run"
                    else -> "${base}_idle"
                }
            }
            "archer" -> {
                val base = "u_${fKey}_archer"
                when (unit.order) {
                    UnitOrder.ATTACK -> "${base}_shoot"
                    UnitOrder.MOVE, UnitOrder.ATTACK_MOVE -> "${base}_run"
                    else -> "${base}_idle"
                }
            }
            "lancer" -> {
                val base = "u_${fKey}_lancer"
                when (unit.order) {
                    UnitOrder.ATTACK -> "${base}_attack"
                    UnitOrder.MOVE, UnitOrder.ATTACK_MOVE -> "${base}_run"
                    else -> "${base}_idle"
                }
            }
            "monk" -> {
                val base = "u_${fKey}_monk"
                when (unit.order) {
                    UnitOrder.ATTACK -> "${base}_heal"
                    UnitOrder.MOVE, UnitOrder.ATTACK_MOVE -> "${base}_run"
                    else -> "${base}_idle"
                }
            }
            else -> "u_${fKey}_worker_idle"
        }
    }

    private fun drawBuilding(canvas: Canvas, state: GameState, building: GameBuilding) {
        val def = BUILDINGS[building.type] ?: return
        val fKey = FACTIONS.getOrNull(building.faction)?.key ?: "blue"
        val key = "b_${fKey}_${building.type}"
        val sprite = assets.get(key)

        val drawW = def.w
        val drawH = def.h
        val drawX = building.x - drawW / 2
        val drawY = building.y - drawH + def.placeYOffset

        // Under construction: translucent
        if (building.buildProgress < 1f) {
            paint.alpha = (255 * 0.58f).toInt()
        }

        if (sprite != null) {
            dstRect.set(drawX, drawY, drawX + drawW, drawY + drawH)
            canvas.drawBitmap(sprite, null, dstRect, paint)
        } else {
            // Fallback: colored rectangle
            val fColor = FACTIONS.getOrNull(building.faction)?.color ?: Color.BLUE
            val p = Paint().apply { color = fColor; alpha = paint.alpha }
            canvas.drawRect(drawX, drawY, drawX + drawW, drawY + drawH, p)
        }

        if (building.flash > 0f) {
            val flashPaint = Paint().apply { color = Color.argb(100, 255, 255, 255) }
            canvas.drawRect(drawX, drawY, drawX + drawW, drawY + drawH, flashPaint)
        }

        paint.alpha = 255

        // Selection indicator
        if (building.selected) {
            selectionPaint.color = Color.argb(100, 255, 255, 255)
            canvas.drawRect(drawX - 3f, drawY - 3f, drawX + drawW + 3f, drawY + drawH + 3f, selectionPaint)

            // Rally flag
            if (building.hasRally) {
                val fColor = FACTIONS.getOrNull(building.faction)?.color ?: Color.WHITE
                val flagPaint = Paint().apply { color = fColor; style = Paint.Style.FILL }
                canvas.drawCircle(building.rallyX, building.rallyY, 8f, flagPaint)
                canvas.drawLine(building.rallyX, building.rallyY, building.rallyX, building.rallyY - 20f, flagPaint)
            }

            // Tower range circle
            if (def.isTower) {
                val rangePaint = Paint().apply {
                    color = Color.argb(30, 255, 100, 100)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(building.x, building.y, def.towerRange, rangePaint)
                val rangeStroke = Paint().apply {
                    color = Color.argb(60, 255, 100, 100)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawCircle(building.x, building.y, def.towerRange, rangeStroke)
            }
        }

        // HP bar
        if (building.hp < building.maxHp || building.selected) {
            drawHpBar(canvas, building.x, drawY - 8f, building.hp.toFloat() / building.maxHp, 44f)
        }

        // Construction progress bar
        if (building.buildProgress < 1f) {
            drawProgressBar(canvas, building.x, drawY - 18f, building.buildProgress, 44f)
        }
    }

    private fun drawResource(canvas: Canvas, state: GameState, resource: GameResource) {
        if (resource.depleted && !resource.isAnimal) {
            // Draw stump or empty
            val stump = if (resource.type == ResourceType.TREE) assets.get("stump${1 + resource.variant % 2}") else null
            if (stump != null) {
                val w = 32f
                val h = 32f
                dstRect.set(resource.x - w / 2, resource.y - h / 2, resource.x + w / 2, resource.y + h / 2)
                canvas.drawBitmap(stump, null, dstRect, pixelPaint)
            }
            return
        }

        if (resource.isAnimal && resource.animalHp > 0f) {
            drawAnimal(canvas, state, resource)
            return
        }

        // Static resource
        val spriteKey = when (resource.type) {
            ResourceType.TREE -> "tree${1 + resource.variant % 4}"
            ResourceType.GOLD -> "gold${1 + resource.variant % 6}"
            ResourceType.FOOD -> "meat"
        }

        val sprite = assets.get(spriteKey)
        if (sprite != null) {
            val scale = when (resource.type) {
                ResourceType.TREE -> 0.45f
                ResourceType.GOLD -> 0.50f
                ResourceType.FOOD -> 0.40f
            }
            val w = sprite.width * scale
            val h = sprite.height * scale
            dstRect.set(resource.x - w / 2, resource.y - h, resource.x + w / 2, resource.y)
            canvas.drawBitmap(sprite, null, dstRect, pixelPaint)
        } else {
            // Fallback colored circles
            val color = when (resource.type) {
                ResourceType.TREE -> Color.parseColor("#2a6a10")
                ResourceType.GOLD -> Color.parseColor("#d4a017")
                ResourceType.FOOD -> Color.parseColor("#cc3333")
            }
            val p = Paint().apply { this.color = color }
            canvas.drawCircle(resource.x, resource.y, 14f, p)
        }
    }

    private fun drawAnimal(canvas: Canvas, state: GameState, res: GameResource) {
        val def = HUNT_ANIMALS[res.animalKind] ?: return
        val kindCap = res.animalKind.replaceFirstChar { it.uppercase() }
        val animSuffix = when {
            res.hurtTimer > 0f -> "Hurt"
            res.panic > 0f -> "Run"
            abs(res.vx) > 2f || abs(res.vy) > 2f -> "Walk"
            else -> "Idle"
        }
        val fps = when {
            res.hurtTimer > 0f -> 5.5f
            res.panic > 0f -> 9.2f
            abs(res.vx) > 2f || abs(res.vy) > 2f -> def.fpsWalk
            else -> 2.5f
        }

        val key = "animal${kindCap}${animSuffix}"
        val sprite = assets.get(key)

        if (sprite != null) {
            val fw = def.fw
            val fh = def.fh
            val framesInSheet = (sprite.width / fw).coerceAtLeast(1)
            val rowsInSheet = (sprite.height / fh).coerceAtLeast(1)

            val frame = ((res.animTime * fps).toInt() % framesInSheet)
            val dirRow = res.animalDir.coerceIn(0, rowsInSheet - 1)

            val scale = def.scale * 1.5f // Adjusted visual scale closer to web version
            val drawW = fw * scale
            val drawH = fh * scale
            // The baseline in web version is around 28px on a 32px frame, so bottom is shifted slightly
            val baselineShift = (fh - 28f) * scale

            srcRect.set(frame * fw, dirRow * fh, (frame + 1) * fw, (dirRow + 1) * fh)
            dstRect.set(res.x - drawW / 2, res.y - drawH + baselineShift, res.x + drawW / 2, res.y + baselineShift)

            if (res.flash > 0f) {
                paint.colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP)
            }

            canvas.drawBitmap(sprite, srcRect, dstRect, paint)
            paint.colorFilter = null
        } else {
            // Fallback
            val p = Paint().apply { color = Color.parseColor("#8B4513") }
            canvas.drawCircle(res.x, res.y, def.radius, p)
        }

        // Animal HP bar
        if (res.animalHp < res.animalMaxHp) {
            drawHpBar(canvas, res.x, res.y - 20f, res.animalHp / res.animalMaxHp, 24f)
        }
    }

    private fun drawDecor(canvas: Canvas, state: GameState, decor: GameDecor) {
        val sprite = assets.get(decor.kind)
        val scale = if (decor.isSky) CLOUD_BOOST * 0.4f else 0.5f

        if (sprite != null) {
            val w = sprite.width * scale
            val h = sprite.height * scale
            val x = decor.x + if (decor.isSky) decor.drift else 0f
            dstRect.set(x - w / 2, decor.y - h / 2, x + w / 2, decor.y + h / 2)

            if (decor.isSky) {
                paint.alpha = 140
            }
            canvas.drawBitmap(sprite, null, dstRect, paint)
            paint.alpha = 255
        }

        // Update cloud drift
        if (decor.isSky) {
            decor.drift += decor.driftSpeed * 0.016f
            if (decor.drift > state.worldW * 0.3f) decor.drift = -state.worldW * 0.3f
        }
    }

    private fun drawProjectiles(canvas: Canvas, state: GameState) {
        val projPaint = Paint().apply { color = Color.parseColor("#4a3020") }

        for (p in state.projectiles) {
            // Try to draw arrow sprite
            val fKey = FACTIONS.getOrNull(p.factionId)?.key ?: "blue"
            val arrowSprite = assets.get("u_${fKey}_arrow")

            if (arrowSprite != null) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(Math.toDegrees(p.angle.toDouble()).toFloat())
                val s = 0.25f
                dstRect.set(-arrowSprite.width * s / 2, -arrowSprite.height * s / 2,
                    arrowSprite.width * s / 2, arrowSprite.height * s / 2)
                canvas.drawBitmap(arrowSprite, null, dstRect, pixelPaint)
                canvas.restore()
            } else {
                // Fallback: line
                val endX = p.x - cos(p.angle) * 12f
                val endY = p.y - sin(p.angle) * 12f
                projPaint.strokeWidth = 2.5f
                canvas.drawLine(p.x, p.y, endX, endY, projPaint)
            }
        }
    }

    private fun drawEffects(canvas: Canvas, state: GameState) {
        for (e in state.effects) {
            val progress = e.time / e.maxTime

            when (e.kind) {
                "dust" -> {
                    val alpha = ((1f - progress) * 150).toInt().coerceIn(0, 255)
                    val radius = 8f + progress * 20f
                    val p = Paint().apply { color = Color.argb(alpha, 180, 160, 130) }
                    canvas.drawCircle(e.x, e.y, radius, p)
                }
                "hit" -> {
                    val alpha = ((1f - progress) * 200).toInt().coerceIn(0, 255)
                    val radius = 4f + progress * 8f
                    val p = Paint().apply { color = Color.argb(alpha, 255, 200, 50) }
                    canvas.drawCircle(e.x, e.y, radius, p)
                }
                "heal" -> {
                    val alpha = ((1f - progress) * 180).toInt().coerceIn(0, 255)
                    val radius = 6f + progress * 14f
                    val p = Paint().apply { color = Color.argb(alpha, 100, 255, 150) }
                    canvas.drawCircle(e.x, e.y - progress * 10f, radius, p)
                }
                "explosion" -> {
                    val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                    val radius = 10f + progress * 40f
                    val p = Paint().apply { color = Color.argb(alpha, 255, 120, 30) }
                    canvas.drawCircle(e.x, e.y, radius, p)
                    val p2 = Paint().apply { color = Color.argb(alpha / 2, 255, 200, 80) }
                    canvas.drawCircle(e.x, e.y, radius * 0.6f, p2)
                }
            }
        }
    }

    private fun drawSelection(canvas: Canvas, state: GameState) {
        // Draw rally lines for selected buildings
        for (sel in state.selected) {
            if (sel is GameBuilding && sel.hasRally) {
                val linePaint = Paint().apply {
                    color = Color.argb(100, 255, 255, 200)
                    strokeWidth = 1.5f
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                canvas.drawLine(sel.x, sel.y, sel.rallyX, sel.rallyY, linePaint)
            }
        }

        if (state.dragSelectActive) {
            val left = min(state.dragSelectStartX, state.dragSelectEndX)
            val top = min(state.dragSelectStartY, state.dragSelectEndY)
            val right = max(state.dragSelectStartX, state.dragSelectEndX)
            val bottom = max(state.dragSelectStartY, state.dragSelectEndY)
            canvas.drawRect(left, top, right, bottom, dragSelectPaint)
            canvas.drawRect(left, top, right, bottom, dragSelectBorderPaint)
        }
    }

    private fun drawPlacementGhost(canvas: Canvas, state: GameState) {
        val type = state.placingBuilding ?: return
        val def = BUILDINGS[type] ?: return
        val ghostPaint = Paint().apply {
            color = Color.argb(80, 100, 255, 100)
            style = Paint.Style.FILL
        }
        // Ghost drawn at pointer position - handled by input system
    }

    private fun drawHpBar(canvas: Canvas, x: Float, y: Float, pct: Float, width: Float) {
        val height = 4f
        val left = x - width / 2
        val top = y

        // Background
        canvas.drawRect(left - 1f, top - 1f, left + width + 1f, top + height + 1f, hpBarBgPaint)

        // Fill
        hpBarPaint.color = when {
            pct > 0.6f -> Color.parseColor("#40c040")
            pct > 0.3f -> Color.parseColor("#c0c040")
            else -> Color.parseColor("#c04040")
        }
        canvas.drawRect(left, top, left + width * pct, top + height, hpBarPaint)
    }

    private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, pct: Float, width: Float) {
        val height = 3f
        val left = x - width / 2
        canvas.drawRect(left - 1f, y - 1f, left + width + 1f, y + height + 1f, hpBarBgPaint)
        val p = Paint().apply { color = Color.parseColor("#60a0ff") }
        canvas.drawRect(left, y, left + width * pct, y + height, p)
    }

    // ── Minimap Rendering ──

    fun renderMinimap(canvas: Canvas, state: GameState, mapW: Float, mapH: Float) {
        canvas.drawColor(Color.parseColor("#1a3a4a"))

        val scaleX = mapW / state.worldW
        val scaleY = mapH / state.worldH

        // Draw land
        val tileW = TILE * scaleX
        val tileH = TILE * scaleY
        val p = Paint()

        for (row in 0 until state.landRows) {
            for (col in 0 until state.landCols) {
                val idx = row * state.landCols + col
                if (state.landMap[idx].toInt() == 0) continue

                val biome = state.biomeMap.getOrElse(idx) { 0 }
                p.color = when (biome) {
                    0 -> Color.parseColor("#5a9a30")
                    1 -> Color.parseColor("#8a5a30")
                    2 -> Color.parseColor("#8a8a20")
                    3 -> Color.parseColor("#6a4a8a")
                    4 -> Color.parseColor("#5a5a5a")
                    else -> Color.parseColor("#5a9a30")
                }
                canvas.drawRect(col * tileW, row * tileH, (col + 1) * tileW, (row + 1) * tileH, p)
            }
        }

        // Draw resources as dots
        val resPaint = Paint()
        for (r in state.resources) {
            if (r.dead || r.depleted) continue
            resPaint.color = when (r.type) {
                ResourceType.TREE -> Color.parseColor("#1a5a00")
                ResourceType.GOLD -> Color.parseColor("#d4a017")
                ResourceType.FOOD -> Color.parseColor("#cc6633")
            }
            canvas.drawCircle(r.x * scaleX, r.y * scaleY, 1.5f, resPaint)
        }

        // Draw buildings
        val bldPaint = Paint()
        for (b in state.buildings) {
            if (b.dead) continue
            bldPaint.color = FACTIONS.getOrNull(b.faction)?.color ?: Color.BLUE
            val size = if (b.type == "castle") 5f else 3f
            canvas.drawRect(b.x * scaleX - size, b.y * scaleY - size,
                b.x * scaleX + size, b.y * scaleY + size, bldPaint)
        }

        // Draw units as small dots
        val unitPaint = Paint()
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            unitPaint.color = FACTIONS.getOrNull(u.faction)?.color ?: Color.BLUE
            canvas.drawCircle(u.x * scaleX, u.y * scaleY, 1.5f, unitPaint)
        }

        // Draw camera viewport rectangle
        val cam = state.camera
        val camPaint = Paint().apply {
            color = Color.parseColor("#ffff60")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val viewW = VIEW_W / cam.zoom
        val viewH = VIEW_H / cam.zoom
        val left = (cam.x - viewW / 2) * scaleX
        val top = (cam.y - viewH / 2) * scaleY
        val right = (cam.x + viewW / 2) * scaleX
        val bottom = (cam.y + viewH / 2) * scaleY
        canvas.drawRect(left, top, right, bottom, camPaint)
    }
}
