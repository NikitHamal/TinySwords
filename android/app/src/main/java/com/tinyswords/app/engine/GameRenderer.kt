package com.tinyswords.app.engine

import android.graphics.*
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import kotlin.math.*
import java.util.LinkedHashMap

class GameRenderer(private val assets: AssetManager) {
    private val spritePaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val alphaPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val overlayPaint = Paint().apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 245, 211, 125)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val hpBarBgPaint = Paint().apply { color = Color.argb(190, 24, 22, 18) }
    private val hpBarPaint = Paint()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val biomeTileKeys = arrayOf("tileGrass", "tileWarm", "tileAlt", "tileMoss", "tileDeep")
    private val minimapBiomeColors = intArrayOf(
        Color.rgb(130, 187, 106),
        Color.rgb(194, 186, 114),
        Color.rgb(168, 194, 85),
        Color.rgb(106, 144, 96),
        Color.rgb(90, 144, 80)
    )
    private val minimapBiomeEdgeColors = intArrayOf(
        Color.rgb(111, 167, 90),
        Color.rgb(154, 154, 90),
        Color.rgb(138, 170, 69),
        Color.rgb(90, 128, 80),
        Color.rgb(78, 122, 72)
    )

    private var minimapTerrain: Bitmap? = null
    private var minimapKey: String = ""

    private val drawablesBuffer = ArrayList<DrawableEntity>(1536)
    private val drawablePool = ArrayList<DrawableEntity>(1536)
    private val drawableComparator = Comparator<DrawableEntity> { a, b -> a.sortY.compareTo(b.sortY) }
    private val resourceQueryBuffer = ArrayList<GameResource>(768)
    private val buildingQueryBuffer = ArrayList<GameBuilding>(128)
    private val unitQueryBuffer = ArrayList<GameUnit>(512)
    private var terrainCacheKey: String = ""
    private val terrainChunkTiles = 12
    private val terrainChunkPx = (TILE * terrainChunkTiles).toInt()
    private val terrainChunks = object : LinkedHashMap<Long, Bitmap>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean {
            val remove = size > 96
            if (remove) eldest?.value?.let { if (!it.isRecycled) it.recycle() }
            return remove
        }
    }

    data class EdgeSource(val sx: Int, val sy: Int, val edge: Boolean)
    class DrawableEntity {
        var entity: GameEntity? = null
        var sortY: Float = 0f
        var isSky: Boolean = false
    }

    fun render(canvas: Canvas, state: GameState, viewW: Float, viewH: Float) {
        canvas.drawColor(Color.rgb(20, 51, 64))

        val cam = state.camera
        val zoom = cam.zoom.coerceIn(CAMERA_MIN_ZOOM, CAMERA_MAX_ZOOM)
        val camLeft = cam.x - (viewW / 2f) / zoom
        val camTop = cam.y - (viewH / 2f) / zoom
        val camRight = cam.x + (viewW / 2f) / zoom
        val camBottom = cam.y + (viewH / 2f) / zoom

        canvas.save()
        canvas.translate(viewW / 2f, viewH / 2f)
        canvas.scale(zoom, zoom)
        canvas.translate(-cam.x, -cam.y)

        drawTerrain(canvas, state, camLeft, camTop, camRight, camBottom)

        val drawables = drawablesBuffer
        drawables.clear()
        collectDrawables(state, drawables, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawables.sortWith(drawableComparator)

        for (d in drawables) if (!d.isSky) drawShadow(canvas, d)
        for (d in drawables) if (!d.isSky) drawEntity(canvas, state, d)
        drawProjectiles(canvas, state, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawEffects(canvas, state, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawSelection(canvas, state)
        drawPlacementGhost(canvas, state)
        for (d in drawables) if (d.isSky) drawEntity(canvas, state, d)

        canvas.restore()
    }

    private fun drawTerrain(canvas: Canvas, state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        val worldKey = "${state.landCols}:${state.landRows}:${state.worldW}:${state.worldH}:${state.settings.seed}:${state.landMap.size}"
        if (worldKey != terrainCacheKey) {
            clearTerrainChunks()
            terrainCacheKey = worldKey
        }

        fillPaint.color = Color.rgb(72, 170, 168)
        canvas.drawRect(left - 180f, top - 180f, right + 180f, bottom + 180f, fillPaint)

        val startChunkCol = floor(left / terrainChunkPx).toInt() - 1
        val endChunkCol = ceil(right / terrainChunkPx).toInt() + 1
        val startChunkRow = floor(top / terrainChunkPx).toInt() - 1
        val endChunkRow = ceil(bottom / terrainChunkPx).toInt() + 1

        for (chunkRow in startChunkRow..endChunkRow) {
            for (chunkCol in startChunkCol..endChunkCol) {
                val bitmap = terrainChunkBitmap(state, chunkCol, chunkRow) ?: continue
                canvas.drawBitmap(bitmap, (chunkCol * terrainChunkPx).toFloat(), (chunkRow * terrainChunkPx).toFloat(), spritePaint)
            }
        }
    }

    private fun terrainChunkBitmap(state: GameState, chunkCol: Int, chunkRow: Int): Bitmap? {
        val key = (chunkCol.toLong() shl 32) xor (chunkRow.toLong() and 0xFFFFFFFFL)
        terrainChunks[key]?.let { if (!it.isRecycled) return it }
        if (terrainChunkPx <= 0) return null
        val bitmap = Bitmap.createBitmap(terrainChunkPx, terrainChunkPx, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        fillPaint.color = Color.rgb(72, 170, 168)
        c.drawRect(0f, 0f, terrainChunkPx.toFloat(), terrainChunkPx.toFloat(), fillPaint)
        val baseCol = chunkCol * terrainChunkTiles
        val baseRow = chunkRow * terrainChunkTiles

        for (rowOff in 0 until terrainChunkTiles) {
            for (colOff in 0 until terrainChunkTiles) {
                val col = baseCol + colOff
                val row = baseRow + rowOff
                if (!landAtTile(state, col, row)) {
                    drawWaterTile(c, state, col, row, colOff * TILE, rowOff * TILE)
                }
            }
        }
        for (rowOff in 0 until terrainChunkTiles) {
            for (colOff in 0 until terrainChunkTiles) {
                val col = baseCol + colOff
                val row = baseRow + rowOff
                if (landAtTile(state, col, row)) {
                    val idx = row * state.landCols + col
                    val biome = state.biomeMap.getOrElse(idx) { 0 }.coerceIn(0, 4)
                    drawGrassTile(c, state, col, row, colOff * TILE, rowOff * TILE, biomeTileKeys[biome])
                }
            }
        }
        terrainChunks[key] = bitmap
        return bitmap
    }

    private fun clearTerrainChunks() {
        for ((_, bitmap) in terrainChunks) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        terrainChunks.clear()
    }

    private fun landAtTile(state: GameState, col: Int, row: Int): Boolean {
        if (col < 0 || row < 0 || col >= state.landCols || row >= state.landRows) return false
        val idx = row * state.landCols + col
        return idx in state.landMap.indices && state.landMap[idx].toInt() == 1
    }

    private fun edgeSource(state: GameState, col: Int, row: Int): EdgeSource {
        val n = !landAtTile(state, col, row - 1)
        val s = !landAtTile(state, col, row + 1)
        val w = !landAtTile(state, col - 1, row)
        val e = !landAtTile(state, col + 1, row)
        return when {
            n && w -> EdgeSource(0, 0, true)
            n && e -> EdgeSource(128, 0, true)
            s && w -> EdgeSource(0, 128, true)
            s && e -> EdgeSource(128, 128, true)
            n -> EdgeSource(64, 0, true)
            s -> EdgeSource(64, 128, true)
            w -> EdgeSource(0, 64, true)
            e -> EdgeSource(128, 64, true)
            else -> EdgeSource(64, 64, false)
        }
    }

    private fun drawWaterTile(canvas: Canvas, state: GameState, col: Int, row: Int, x: Float, y: Float) {
        val water = assets.get("water")
        if (water != null) {
            srcRect.set(0, 0, 64, 64)
            dstRect.set(x, y, x + TILE, y + TILE)
            canvas.drawBitmap(water, srcRect, dstRect, spritePaint)
        } else {
            fillPaint.color = Color.rgb(72, 170, 168)
            canvas.drawRect(x, y, x + TILE, y + TILE, fillPaint)
        }

        val foam = assets.get("waterFoam") ?: return
        val landN = landAtTile(state, col, row - 1)
        val landS = landAtTile(state, col, row + 1)
        val landW = landAtTile(state, col - 1, row)
        val landE = landAtTile(state, col + 1, row)
        if (!(landN || landS || landW || landE)) return

        var fsx = 64
        var fsy = 64
        if (landN && !landS && !landW && !landE) { fsx = 64; fsy = 128 }
        else if (landS && !landN && !landW && !landE) { fsx = 64; fsy = 0 }
        else if (landW && !landN && !landS && !landE) { fsx = 128; fsy = 64 }
        else if (landE && !landN && !landS && !landW) { fsx = 0; fsy = 64 }
        else if (landN && landW) { fsx = 128; fsy = 128 }
        else if (landN && landE) { fsx = 0; fsy = 128 }
        else if (landS && landW) { fsx = 128; fsy = 0 }
        else if (landS && landE) { fsx = 0; fsy = 0 }

        val frame = ((state.time * 5.4f).toInt() + ((col * 31 + row * 17) and 15)) and 15
        srcRect.set(frame * 192 + fsx, fsy, frame * 192 + fsx + 64, fsy + 64)
        dstRect.set(x, y, x + TILE, y + TILE)
        alphaPaint.alpha = 198
        canvas.drawBitmap(foam, srcRect, dstRect, alphaPaint)
        alphaPaint.alpha = 255
    }

    private fun drawGrassTile(canvas: Canvas, state: GameState, col: Int, row: Int, x: Float, y: Float, biomeKey: String) {
        val tile = assets.get(biomeKey)
        val edge = edgeSource(state, col, row)
        if (tile != null) {
            srcRect.set(edge.sx, edge.sy, edge.sx + 64, edge.sy + 64)
            dstRect.set(x, y, x + TILE, y + TILE)
            canvas.drawBitmap(tile, srcRect, dstRect, spritePaint)
        } else {
            fillPaint.color = when (biomeKey) {
                "tileWarm" -> Color.rgb(194, 186, 114)
                "tileAlt" -> Color.rgb(168, 194, 85)
                "tileMoss" -> Color.rgb(106, 144, 96)
                "tileDeep" -> Color.rgb(90, 144, 80)
                else -> Color.rgb(130, 187, 106)
            }
            canvas.drawRect(x, y, x + TILE, y + TILE, fillPaint)
        }

        val variant = (col * 7 + row * 13 + 131) and 255
        if (!edge.edge && (variant > 214 || variant % 24 > 18)) {
            overlayPaint.color = Color.argb(19, 244, 239, 141)
            canvas.drawRect(x + 8f + (variant % 11), y + 14f, x + 38f + (variant % 11), y + 17f, overlayPaint)
        }
    }

    private fun collectDrawables(state: GameState, out: MutableList<DrawableEntity>, left: Float, top: Float, right: Float, bottom: Float) {
        state.resourceIndex.queryRect(left, top, right, bottom, resourceQueryBuffer)
        for (r in resourceQueryBuffer) {
            if (r.dead) continue
            pushDrawable(out, r, r.y + if (r.type == ResourceType.TREE) -10f else 0f)
        }

        // Decor is intentionally light-weight in the generated worlds, so scanning it
        // directly avoids maintaining another moving index for clouds/water props while
        // still culling all off-screen sprites before sort/draw.
        for (d in state.decor) {
            if (d.dead) continue
            if (d.isSky) {
                pushDrawable(out, d, d.y + 900000f, true)
            } else if (d.x >= left && d.x <= right && d.y >= top && d.y <= bottom) {
                pushDrawable(out, d, d.y - 18f)
            }
        }

        state.buildingIndex.queryRect(left - 260f, top - 280f, right + 260f, bottom + 260f, buildingQueryBuffer)
        for (b in buildingQueryBuffer) {
            if (b.dead) continue
            val def = BUILDINGS[b.type] ?: continue
            if (b.x < left - def.w || b.x > right + def.w || b.y < top - def.h || b.y > bottom + def.h) continue
            pushDrawable(out, b, b.y + def.h * 0.34f)
        }

        state.unitIndex.queryRect(left, top, right, bottom, unitQueryBuffer)
        for (u in unitQueryBuffer) {
            if (u.dead || u.garrisoned) continue
            pushDrawable(out, u, u.y)
        }
    }

    private fun pushDrawable(out: MutableList<DrawableEntity>, entity: GameEntity, sortY: Float, isSky: Boolean = false) {
        val index = out.size
        val d = if (index < drawablePool.size) drawablePool[index] else DrawableEntity().also { drawablePool.add(it) }
        d.entity = entity
        d.sortY = sortY
        d.isSky = isSky
        out.add(d)
    }

    private fun drawShadow(canvas: Canvas, d: DrawableEntity) {
        when (val e = d.entity ?: return) {
            is GameUnit -> {
                val def = UNITS[e.type] ?: return
                val halfW = unitShadowHalfW(e.type, def)
                val halfH = unitShadowHalfH(e.type)
                val centerY = e.y + 3f
                canvas.drawOval(e.x - halfW, centerY - halfH, e.x + halfW, centerY + halfH, shadowPaint)
            }
            is GameBuilding -> {
                val def = BUILDINGS[e.type] ?: return
                val halfW = def.w * 0.50f
                val halfH = def.h * 0.18f
                val centerY = e.y + def.h * 0.18f
                canvas.drawOval(e.x - halfW, centerY - halfH, e.x + halfW, centerY + halfH, shadowPaint)
            }
            is GameResource -> {
                if (!e.isAnimal && e.type == ResourceType.FOOD) {
                    canvas.drawOval(e.x - 13f, e.y - 4f, e.x + 13f, e.y + 4f, shadowPaint)
                }
            }
            is GameDecor -> {
                if (!e.isSky && !e.isWater && e.kind.startsWith("bush")) canvas.drawOval(e.x - 18f, e.y - 4f, e.x + 18f, e.y + 5f, shadowPaint)
            }
        }
    }

    private fun drawEntity(canvas: Canvas, state: GameState, d: DrawableEntity) {
        when (val e = d.entity ?: return) {
            is GameUnit -> drawUnit(canvas, e)
            is GameBuilding -> drawBuilding(canvas, e)
            is GameResource -> drawResource(canvas, state, e)
            is GameDecor -> drawDecor(canvas, state, e)
        }
    }

    private fun drawUnit(canvas: Canvas, unit: GameUnit) {
        val def = UNITS[unit.type] ?: return
        val fKey = FACTIONS.getOrNull(unit.faction)?.key ?: "blue"
        val key = getUnitAnimKey(unit, fKey)
        val sprite = assets.get(key) ?: assets.get("u_${fKey}_${unit.type}_idle")
        val scale = def.scale * SPRITE_BOOST
        if (unit.selected) {
            val r = (def.radius + 8f).coerceAtLeast(16f)
            selectionPaint.color = Color.argb(150, 245, 211, 125)
            canvas.drawOval(unit.x - r, unit.y - r * 0.44f, unit.x + r, unit.y + r * 0.44f, selectionPaint)
        }
        if (sprite != null) {
            val frames = (sprite.width / def.fw).coerceAtLeast(1)
            val fps = unitAnimationFps(unit, key)
            val frame = ((unit.animTime * fps).toInt() % frames).coerceIn(0, frames - 1)
            if (unit.flash > 0f) spritePaint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            drawAnchoredFrame(canvas, sprite, frame * def.fw, 0, def.fw, def.fh, unit.x, unit.y, scale, unitVisualBaseline(unit.type), unit.face)
            spritePaint.colorFilter = null
        } else {
            fillPaint.color = FACTIONS.getOrNull(unit.faction)?.color ?: Color.BLUE
            canvas.drawCircle(unit.x, unit.y, def.radius, fillPaint)
        }
        if (unit.hp < unit.maxHp || unit.selected) {
            val barY = unit.y - unitVisualHeight(unit.type) * scale - 8f
            drawHpBar(canvas, unit.x, barY, unit.hp.toFloat() / unit.maxHp, 34f)
        }
    }

    private fun getUnitAnimKey(unit: GameUnit, fKey: String): String {
        return when (unit.type) {
            "worker" -> {
                val base = "u_${fKey}_worker"
                when {
                    unit.carrying != null && unit.order != UnitOrder.IDLE -> "${base}_carry_${unit.carrying}"
                    unit.carrying != null -> "${base}_idle_${unit.carrying}"
                    unit.order == UnitOrder.HARVEST -> {
                        val target = unit.target
                        if (target is GameResource) {
                            val workX = resourceInteractionX(target)
                            val workY = resourceInteractionY(target)
                            val closeEnough = hypot(unit.x - workX, unit.y - workY) <= 30f
                            if (!closeEnough) "${base}_run" else when (target.type) {
                                ResourceType.TREE -> "${base}_chop"
                                ResourceType.GOLD -> "${base}_mine"
                                ResourceType.FOOD -> "${base}_fight"
                            }
                        } else "${base}_idle"
                    }
                    unit.order == UnitOrder.REPAIR -> {
                        val target = unit.target as? GameBuilding
                        if (target != null && isUnitAtBuildingWorkRange(unit, target)) "${base}_build" else "${base}_run"
                    }
                    unit.order == UnitOrder.MOVE || unit.order == UnitOrder.ATTACK_MOVE || unit.order == UnitOrder.GARRISON -> "${base}_run"
                    unit.order == UnitOrder.ATTACK -> {
                        val target = unit.target
                        val closeEnough = target != null && hypot(unit.x - target.x, unit.y - target.y) <= ((UNITS[unit.type]?.range ?: 22f) + 8f)
                        if (closeEnough) "${base}_fight" else "${base}_run"
                    }
                    else -> "${base}_idle"
                }
            }
            "warrior" -> "u_${fKey}_warrior_" + unitCombatAnim(unit, "attack")
            "archer" -> "u_${fKey}_archer_" + unitCombatAnim(unit, "shoot")
            "lancer" -> "u_${fKey}_lancer_" + unitCombatAnim(unit, "attack")
            "monk" -> "u_${fKey}_monk_" + unitCombatAnim(unit, "heal")
            else -> "u_${fKey}_worker_idle"
        }
    }

    private fun unitCombatAnim(unit: GameUnit, attackAnim: String): String {
        val def = UNITS[unit.type] ?: return "idle"
        if (unit.order == UnitOrder.MOVE || unit.order == UnitOrder.ATTACK_MOVE || unit.order == UnitOrder.GARRISON) return "run"
        if (unit.order == UnitOrder.ATTACK) {
            val target = unit.target
            val closeEnough = target != null && hypot(unit.x - target.x, unit.y - target.y) <= def.range + 8f
            return if (closeEnough) attackAnim else "run"
        }
        return "idle"
    }

    private fun unitAnimationFps(unit: GameUnit, animKey: String): Float {
        return when (unit.order) {
            UnitOrder.IDLE -> 4f
            UnitOrder.MOVE, UnitOrder.ATTACK_MOVE, UnitOrder.GARRISON -> 8f
            UnitOrder.ATTACK -> if (animKey.endsWith("_run")) 8f else 6f
            UnitOrder.HARVEST, UnitOrder.REPAIR -> if (animKey.endsWith("_run")) 8f else 6f
        }
    }

    private fun isUnitAtBuildingWorkRange(unit: GameUnit, building: GameBuilding): Boolean {
        val def = BUILDINGS[building.type] ?: return hypot(unit.x - building.x, unit.y - building.y) <= 28f
        val left = building.x - def.placeW / 2f
        val right = building.x + def.placeW / 2f
        val top = building.y - def.placeH / 2f
        val bottom = building.y + def.placeH / 2f
        val dx = max(max(left - unit.x, 0f), unit.x - right)
        val dy = max(max(top - unit.y, 0f), unit.y - bottom)
        return sqrt(dx * dx + dy * dy) <= 26f
    }

    private fun resourceInteractionX(res: GameResource): Float = res.x

    private fun resourceInteractionY(res: GameResource): Float = when {
        res.isAnimal -> res.y
        res.type == ResourceType.TREE && res.depleted -> res.y - 14f
        res.type == ResourceType.TREE -> res.y - 42f
        res.type == ResourceType.GOLD -> res.y - 16f
        else -> res.y - 2f
    }

    private fun unitVisualBaseline(type: String): Float = when (type) {
        "worker" -> 135f
        "warrior" -> 137f
        "archer" -> 136f
        "lancer" -> 198f
        "monk" -> 134f
        else -> 136f
    }

    private fun unitVisualHeight(type: String): Float = when (type) {
        "worker" -> 72f
        "warrior" -> 90f
        "archer" -> 88f
        "lancer" -> 150f
        "monk" -> 70f
        else -> 76f
    }

    private fun unitShadowHalfW(type: String, def: UnitDef): Float = when (type) {
        "lancer" -> 24f
        else -> def.radius * 1.15f
    }

    private fun unitShadowHalfH(type: String): Float = if (type == "lancer") 8f else 8f

    private fun drawBuilding(canvas: Canvas, building: GameBuilding) {
        val def = BUILDINGS[building.type] ?: return
        val fKey = FACTIONS.getOrNull(building.faction)?.key ?: "blue"
        val sprite = assets.get("b_${fKey}_${building.type}")
        val drawX = building.x - def.w / 2f
        val drawY = building.y - def.h + def.placeYOffset
        if (building.buildProgress < 1f) alphaPaint.alpha = 150 else alphaPaint.alpha = 255
        if (sprite != null) {
            dstRect.set(drawX, drawY, drawX + def.w, drawY + def.h)
            canvas.drawBitmap(sprite, null, dstRect, alphaPaint)
        } else {
            fillPaint.color = FACTIONS.getOrNull(building.faction)?.color ?: Color.BLUE
            fillPaint.alpha = alphaPaint.alpha
            canvas.drawRect(drawX, drawY, drawX + def.w, drawY + def.h, fillPaint)
            fillPaint.alpha = 255
        }
        alphaPaint.alpha = 255
        if (building.flash > 0f) {
            overlayPaint.color = Color.argb(96, 255, 255, 255)
            canvas.drawRect(drawX, drawY, drawX + def.w, drawY + def.h, overlayPaint)
        }
        if (building.selected) {
            selectionPaint.color = Color.argb(170, 245, 211, 125)
            canvas.drawRect(drawX - 4f, drawY - 4f, drawX + def.w + 4f, drawY + def.h + 4f, selectionPaint)
            if (building.hasRally) {
                fillPaint.color = FACTIONS.getOrNull(building.faction)?.color ?: Color.WHITE
                canvas.drawCircle(building.rallyX, building.rallyY, 8f, fillPaint)
                strokePaint.color = Color.argb(190, 245, 211, 125)
                strokePaint.strokeWidth = 2f
                canvas.drawLine(building.x, building.y, building.rallyX, building.rallyY, strokePaint)
            }
            if (def.isTower) {
                fillPaint.color = Color.argb(26, 255, 110, 110)
                canvas.drawCircle(building.x, building.y, def.towerRange, fillPaint)
                strokePaint.color = Color.argb(70, 255, 130, 130)
                strokePaint.strokeWidth = 1.6f
                canvas.drawCircle(building.x, building.y, def.towerRange, strokePaint)
            }
        }
        if (building.hp < building.maxHp || building.selected) drawHpBar(canvas, building.x, drawY - 8f, building.hp.toFloat() / building.maxHp, 48f)
        if (building.buildProgress < 1f) drawProgressBar(canvas, building.x, drawY - 18f, building.buildProgress, 48f)
    }

    private fun drawResource(canvas: Canvas, state: GameState, res: GameResource) {
        if (res.isAnimal && res.animalHp > 0f) {
            drawAnimal(canvas, state, res)
            return
        }
        if (res.depleted && res.type != ResourceType.TREE) return

        when (res.type) {
            ResourceType.TREE -> {
                val sprite = if (res.depleted) assets.get("stump${1 + res.variant % 2}") else assets.get("tree${1 + res.variant % 4}")
                val fw = 192
                val fh = 256
                val scale = (if (res.depleted) 0.46f else 0.60f) * SPRITE_BOOST
                val frameCount = ((sprite?.width ?: fw) / fw).coerceAtLeast(1)
                val frame = if (res.depleted) 0 else ((state.time * 4f + (res.id % frameCount)).toInt() % frameCount)
                if (sprite != null) drawAnchoredFrame(canvas, sprite, frame * fw, 0, fw, fh, res.x, res.y, scale, 241f) else fallbackResource(canvas, res)
            }
            ResourceType.GOLD -> {
                val variant = 1 + res.variant % 6
                val sprite = assets.get("gold$variant")
                val scale = 0.60f * SPRITE_BOOST
                if (sprite != null) {
                    drawAnchoredFrame(canvas, sprite, 0, 0, 128, 128, res.x, res.y, scale, 79f)
                    val shine = assets.get("gold${variant}_hl")
                    if (shine != null) {
                        val a = ((sin(state.time * 1.5f + res.id) + 1f) * 0.5f * 150f).toInt().coerceIn(0, 150)
                        alphaPaint.alpha = a
                        drawAnchoredFrame(canvas, shine, 0, 0, 128, 128, res.x, res.y, scale, 79f, paint = alphaPaint)
                        alphaPaint.alpha = 255
                    }
                } else fallbackResource(canvas, res)
            }
            ResourceType.FOOD -> {
                val sprite = assets.get("meat")
                if (sprite != null) drawAnchoredFrame(canvas, sprite, 0, 0, 64, 64, res.x, res.y, 0.68f * SPRITE_BOOST, 52f) else fallbackResource(canvas, res)
            }
        }
        if (res in state.selected) {
            selectionPaint.color = Color.argb(170, 245, 211, 125)
            val r = when (res.type) { ResourceType.TREE -> 38f; ResourceType.GOLD -> 30f; ResourceType.FOOD -> 22f }
            canvas.drawOval(res.x - r, res.y - r * 0.45f, res.x + r, res.y + r * 0.45f, selectionPaint)
        }
    }

    private fun fallbackResource(canvas: Canvas, res: GameResource) {
        fillPaint.color = when (res.type) {
            ResourceType.TREE -> Color.rgb(42, 106, 16)
            ResourceType.GOLD -> Color.rgb(230, 202, 89)
            ResourceType.FOOD -> Color.rgb(204, 80, 60)
        }
        canvas.drawCircle(res.x, res.y, 14f, fillPaint)
    }

    private fun drawAnimal(canvas: Canvas, state: GameState, res: GameResource) {
        val def = HUNT_ANIMALS[res.animalKind] ?: return
        val moving = abs(res.vx) > 7f || abs(res.vy) > 7f
        val hurt = res.hurtTimer > 0f
        val suffix = when {
            hurt -> "Hurt"
            moving && res.panic > 0f && res.animalKind == "grouse" -> "Flight"
            moving && res.panic > 0f -> "Run"
            moving -> "Walk"
            else -> "Idle"
        }
        val spriteKey = animalSpriteKey(res, suffix, moving, hurt)
        val sprite = assets.get(spriteKey) ?: assets.get(animalSpriteKey(res, "Idle", moving = false, hurt = false))
        if (sprite != null) {
            val fw = def.fw
            val fh = def.fh
            val frames = (sprite.width / fw).coerceAtLeast(1)
            val rows = (sprite.height / fh).coerceAtLeast(1)
            val fps = when {
                hurt -> def.fpsHurt
                moving && res.panic > 0f -> def.fpsRun
                moving -> def.fpsWalk
                else -> def.fpsIdle
            }
            val frameSeed = if (moving || hurt) (res.id % frames).toFloat() else ((res.id * 31) % frames).toFloat()
            val frame = ((res.animTime * fps + frameSeed).toInt() % frames).coerceIn(0, frames - 1)
            val row = if (rows == 1) 0 else res.animalDir.coerceIn(0, rows - 1)
            val bob = if (moving) sin(state.time * 5f + res.id) * 1.1f else 0f
            val face = if (def.flipByFacing && (res.animalDir == 2 || res.vx < -1f)) -1 else 1
            drawAnimalShadow(canvas, res)
            if (res in state.selected) {
                val sr = (def.radius + 8f).coerceAtLeast(16f)
                selectionPaint.color = Color.argb(160, 245, 211, 125)
                canvas.drawOval(res.x - sr, res.y - sr * 0.42f, res.x + sr, res.y + sr * 0.42f, selectionPaint)
            }
            if (res.flash > 0f || hurt) spritePaint.colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP)
            drawAnchoredFrame(canvas, sprite, frame * fw, row * fh, fw, fh, res.x, res.y + bob, def.scale, def.baseline, face)
            spritePaint.colorFilter = null
        } else fallbackResource(canvas, res)
        if (res.animalHp < res.animalMaxHp || res in state.selected) drawHpBar(canvas, res.x, res.y - 36f, res.animalHp / res.animalMaxHp, 28f)
    }

    private fun animalSpriteKey(res: GameResource, suffix: String, moving: Boolean, hurt: Boolean): String {
        if (res.animalKind == "sheep") {
            return when {
                !moving && !hurt && ((res.id * 1103515245 + 12345) ushr 28) > 12 -> "sheepGrass"
                suffix == "Walk" || suffix == "Run" || suffix == "Flight" -> "sheepMove"
                else -> "sheepIdle"
            }
        }
        val kindCap = animalKeyName(res.animalKind)
        return "animal${kindCap}$suffix"
    }

    private fun animalKeyName(kind: String): String = when (kind) {
        "grouse" -> "Grouse"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    private fun drawAnimalShadow(canvas: Canvas, res: GameResource) {
        val def = HUNT_ANIMALS[res.animalKind] ?: return
        // The CraftPix sprite-shadow sheets have inconsistent internal padding on
        // Android Canvas and were the cause of the drifting/double-shadow look.
        // Match the stabilized web build: one procedural ground-contact oval tied
        // to the animal baseline, never to the transparent sprite frame bounds.
        val alpha = if (res.animalKind == "sheep") 92 else 104
        shadowPaint.alpha = alpha
        canvas.drawOval(res.x - def.shadowW, res.y - def.shadowH, res.x + def.shadowW, res.y + def.shadowH, shadowPaint)
        shadowPaint.alpha = 255
    }

    private fun drawDecor(canvas: Canvas, state: GameState, decor: GameDecor) {
        val sprite = assets.get(decor.kind) ?: return
        val isCloud = decor.isSky
        val (fw, fh, baseline, scale, fps) = when {
            decor.kind.startsWith("bush") -> DecorSpec(128, 128, 79f, 0.55f, 1.05f)
            decor.kind.startsWith("rock") -> DecorSpec(64, 64, 51f, 0.85f, 0f)
            decor.kind.startsWith("waterRock") -> DecorSpec(64, 64, 56f, 0.92f, 3.5f)
            decor.kind == "rubberDuck" -> DecorSpec(32, 32, 29f, 0.9f, 2.2f)
            isCloud -> DecorSpec(sprite.width, sprite.height, sprite.height * 0.70f, 0.42f * CLOUD_BOOST, 0f)
            else -> DecorSpec(sprite.width, sprite.height, sprite.height.toFloat(), 1f, 0f)
        }
        val frames = (sprite.width / fw).coerceAtLeast(1)
        val frame = if (fps > 0f) ((state.time * fps + decor.id).toInt() % frames) else 0
        val bob = if (decor.isWater) sin(state.time * 1.35f + decor.id) * 2.2f else 0f
        val drift = if (decor.isSky) sin(state.time * 0.22f + decor.drift) * 18f else 0f
        alphaPaint.alpha = if (isCloud) 178 else 255
        drawAnchoredFrame(canvas, sprite, frame * fw, 0, fw, fh, decor.x + drift, decor.y + bob, scale * decor.scale, baseline, paint = if (isCloud) alphaPaint else spritePaint)
        alphaPaint.alpha = 255
    }

    data class DecorSpec(val fw: Int, val fh: Int, val baseline: Float, val scale: Float, val fps: Float)

    private fun drawProjectiles(canvas: Canvas, state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        strokePaint.color = Color.rgb(74, 48, 32)
        strokePaint.strokeWidth = 2.4f
        for (p in state.projectiles) {
            if (p.x < left || p.x > right || p.y < top || p.y > bottom) continue
            val fKey = FACTIONS.getOrNull(p.factionId)?.key ?: "blue"
            val arrow = assets.get("u_${fKey}_arrow")
            if (arrow != null) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(Math.toDegrees(p.angle.toDouble()).toFloat())
                val s = 0.25f
                dstRect.set(-arrow.width * s / 2f, -arrow.height * s / 2f, arrow.width * s / 2f, arrow.height * s / 2f)
                canvas.drawBitmap(arrow, null, dstRect, spritePaint)
                canvas.restore()
            } else {
                canvas.drawLine(p.x, p.y, p.x - cos(p.angle) * 12f, p.y - sin(p.angle) * 12f, strokePaint)
            }
        }
    }

    private fun drawEffects(canvas: Canvas, state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        for (e in state.effects) {
            if (e.x < left || e.x > right || e.y < top || e.y > bottom) continue
            val progress = (e.time / e.maxTime).coerceIn(0f, 1f)
            val alpha = ((1f - progress) * 190).toInt().coerceIn(0, 255)
            when (e.kind) {
                "dust" -> { fillPaint.color = Color.argb(alpha, 180, 160, 130); canvas.drawCircle(e.x, e.y, 8f + progress * 20f, fillPaint) }
                "hit" -> { fillPaint.color = Color.argb(alpha, 255, 200, 50); canvas.drawCircle(e.x, e.y, 4f + progress * 9f, fillPaint) }
                "heal" -> { fillPaint.color = Color.argb(alpha, 100, 255, 150); canvas.drawCircle(e.x, e.y - progress * 10f, 6f + progress * 14f, fillPaint) }
                "explosion" -> { fillPaint.color = Color.argb(alpha, 255, 120, 30); canvas.drawCircle(e.x, e.y, 10f + progress * 40f, fillPaint) }
                "moveMark" -> {
                    strokePaint.color = Color.argb(alpha, 245, 211, 125)
                    strokePaint.strokeWidth = 2.0f
                    val r = (7f + progress * 16f) * e.scale
                    canvas.drawCircle(e.x, e.y, r, strokePaint)
                    fillPaint.color = Color.argb((alpha * 0.26f).toInt().coerceIn(0, 255), 245, 211, 125)
                    canvas.drawCircle(e.x, e.y, 3.2f * e.scale, fillPaint)
                }
            }
        }
    }

    private fun drawSelection(canvas: Canvas, state: GameState) {
        for (sel in state.selected) {
            if (sel is GameBuilding && sel.hasRally) {
                strokePaint.color = Color.argb(120, 255, 255, 200)
                strokePaint.strokeWidth = 2f
                canvas.drawLine(sel.x, sel.y, sel.rallyX, sel.rallyY, strokePaint)
            }
        }
    }

    private fun drawPlacementGhost(canvas: Canvas, state: GameState) {
        val type = state.placingBuilding ?: return
        val def = BUILDINGS[type] ?: return
        val x = state.pointerWorldX
        val y = state.pointerWorldY
        if (!x.isFinite() || !y.isFinite()) return
        val valid = canPlaceGhost(state, type, x, y)
        fillPaint.color = if (valid) Color.argb(70, 90, 255, 140) else Color.argb(80, 255, 90, 80)
        strokePaint.color = if (valid) Color.argb(210, 115, 255, 160) else Color.argb(220, 255, 100, 90)
        strokePaint.strokeWidth = 2.5f
        canvas.drawRect(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, fillPaint)
        canvas.drawRect(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, strokePaint)
        val fKey = FACTIONS.getOrNull(0)?.key ?: "blue"
        val sprite = assets.get("b_${fKey}_$type")
        if (sprite != null) {
            alphaPaint.alpha = if (valid) 130 else 95
            val drawX = x - def.w / 2f
            val drawY = y - def.h + def.placeYOffset
            dstRect.set(drawX, drawY, drawX + def.w, drawY + def.h)
            canvas.drawBitmap(sprite, null, dstRect, alphaPaint)
            alphaPaint.alpha = 255
        }
    }

    private fun canPlaceGhost(state: GameState, type: String, x: Float, y: Float): Boolean {
        val def = BUILDINGS[type] ?: return false
        if (!state.isSafeLand(x, y, def.placeW / 2f)) return false
        for (b in state.buildings) {
            if (b.dead) continue
            val bd = BUILDINGS[b.type] ?: continue
            if (rectsOverlapCentered(x, y, def.placeW, def.placeH, b.x, b.y, bd.placeW, bd.placeH)) return false
        }
        for (r in state.resources) {
            if (!r.dead && !r.depleted && hypot(x - r.x, y - r.y) < def.placeW / 2f + 24f) return false
        }
        return true
    }

    private fun rectsOverlapCentered(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return ax - aw / 2f < bx + bw / 2f && ax + aw / 2f > bx - bw / 2f && ay - ah / 2f < by + bh / 2f && ay + ah / 2f > by - bh / 2f
    }

    private fun drawHpBar(canvas: Canvas, x: Float, y: Float, pct: Float, width: Float) {
        val p = pct.coerceIn(0f, 1f)
        val height = 4.5f
        val left = x - width / 2f
        canvas.drawRect(left - 1f, y - 1f, left + width + 1f, y + height + 1f, hpBarBgPaint)
        hpBarPaint.color = when {
            p > 0.6f -> Color.rgb(64, 192, 64)
            p > 0.3f -> Color.rgb(192, 192, 64)
            else -> Color.rgb(192, 64, 64)
        }
        canvas.drawRect(left, y, left + width * p, y + height, hpBarPaint)
    }

    private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, pct: Float, width: Float) {
        val height = 3.5f
        val left = x - width / 2f
        canvas.drawRect(left - 1f, y - 1f, left + width + 1f, y + height + 1f, hpBarBgPaint)
        hpBarPaint.color = Color.rgb(96, 160, 255)
        canvas.drawRect(left, y, left + width * pct.coerceIn(0f, 1f), y + height, hpBarPaint)
    }

    private fun drawAnchoredFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        sx: Int,
        sy: Int,
        fw: Int,
        fh: Int,
        x: Float,
        baseY: Float,
        scale: Float,
        baseline: Float,
        face: Int = 1,
        paint: Paint = spritePaint
    ) {
        val safeSx = sx.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val safeSy = sy.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val safeFw = fw.coerceAtMost(bitmap.width - safeSx).coerceAtLeast(1)
        val safeFh = fh.coerceAtMost(bitmap.height - safeSy).coerceAtLeast(1)
        srcRect.set(safeSx, safeSy, safeSx + safeFw, safeSy + safeFh)
        val w = safeFw * scale
        val h = safeFh * scale
        if (face < 0) {
            canvas.save()
            canvas.scale(-1f, 1f, x, baseY)
            dstRect.set(x - w / 2f, baseY - baseline * scale, x + w / 2f, baseY - baseline * scale + h)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            canvas.restore()
        } else {
            dstRect.set(x - w / 2f, baseY - baseline * scale, x + w / 2f, baseY - baseline * scale + h)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }
    }

    fun renderMinimap(canvas: Canvas, state: GameState, mapW: Float, mapH: Float, viewW: Float, viewH: Float) {
        val bitmap = getMinimapTerrain(state, mapW.toInt().coerceAtLeast(1), mapH.toInt().coerceAtLeast(1))
        if (bitmap != null) canvas.drawBitmap(bitmap, 0f, 0f, null) else canvas.drawColor(Color.rgb(20, 51, 64))

        val scaleX = mapW / state.worldW
        val scaleY = mapH / state.worldH

        for (r in state.resources) {
            if (r.dead || r.depleted) continue
            fillPaint.color = when (r.type) {
                ResourceType.TREE -> Color.rgb(26, 90, 0)
                ResourceType.GOLD -> Color.rgb(212, 160, 23)
                ResourceType.FOOD -> Color.rgb(204, 102, 51)
            }
            canvas.drawCircle(r.x * scaleX, r.y * scaleY, 1.7f, fillPaint)
        }
        for (b in state.buildings) {
            if (b.dead) continue
            fillPaint.color = FACTIONS.getOrNull(b.faction)?.color ?: Color.BLUE
            val s = if (b.type == "castle") 4.8f else 3f
            canvas.drawRect(b.x * scaleX - s, b.y * scaleY - s, b.x * scaleX + s, b.y * scaleY + s, fillPaint)
        }
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            fillPaint.color = FACTIONS.getOrNull(u.faction)?.color ?: Color.BLUE
            canvas.drawCircle(u.x * scaleX, u.y * scaleY, 1.6f, fillPaint)
        }

        val cam = state.camera
        strokePaint.color = Color.rgb(255, 246, 96)
        strokePaint.strokeWidth = 1.7f
        val visibleW = viewW / cam.zoom
        val visibleH = viewH / cam.zoom
        val left = (cam.x - visibleW / 2f) * scaleX
        val top = (cam.y - visibleH / 2f) * scaleY
        val right = (cam.x + visibleW / 2f) * scaleX
        val bottom = (cam.y + visibleH / 2f) * scaleY
        canvas.drawRect(left, top, right, bottom, strokePaint)
    }

    private fun getMinimapTerrain(state: GameState, w: Int, h: Int): Bitmap? {
        val key = "$w:$h:${state.landCols}:${state.landRows}:${state.worldW}:${state.worldH}:${state.settings.seed}:${state.landMap.size}"
        if (key == minimapKey && minimapTerrain?.isRecycled == false) return minimapTerrain
        minimapTerrain?.recycle()
        minimapKey = key
        if (w <= 0 || h <= 0 || state.landCols <= 0 || state.landRows <= 0) return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bitmap)
        c.drawColor(Color.rgb(24, 64, 78))
        val sx = w.toFloat() / state.landCols
        val sy = h.toFloat() / state.landRows
        val p = Paint().apply { style = Paint.Style.FILL }
        for (row in 0 until state.landRows) {
            for (col in 0 until state.landCols) {
                val idx = row * state.landCols + col
                if (idx !in state.landMap.indices || state.landMap[idx].toInt() != 1) continue
                val biome = state.biomeMap.getOrElse(idx) { 0 }.coerceIn(0, 4)
                p.color = if (edgeSource(state, col, row).edge) minimapBiomeEdgeColors[biome] else minimapBiomeColors[biome]
                c.drawRect(col * sx, row * sy, (col + 1) * sx + 0.6f, (row + 1) * sy + 0.6f, p)
            }
        }
        minimapTerrain = bitmap
        return bitmap
    }

    fun destroy() {
        minimapTerrain?.recycle()
        minimapTerrain = null
        clearTerrainChunks()
    }
}
