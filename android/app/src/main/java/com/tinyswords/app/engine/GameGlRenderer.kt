package com.tinyswords.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedHashMap
import javax.microedition.khronos.egl.EGLConfig
import kotlin.math.*

/**
 * OpenGL ES 2.0 battlefield renderer for the Android build.
 *
 * This replaces the old Canvas battlefield path with a retained GL texture cache
 * plus a CPU sprite-batching layer. The gameplay simulation remains in Kotlin so
 * save files, AI, economy, input commands and Compose HUD code stay compatible.
 */
class GameGlRenderer(private val assets: AssetManager) {
    private val batch = SpriteBatch()
    private val projection = FloatArray(16)
    private val textures = GlTextureCache(assets)

    private val biomeTileKeys = arrayOf("tileGrass", "tileWarm", "tileAlt", "tileMoss", "tileDeep")
    private val drawablesBuffer = ArrayList<DrawableEntity>(1536)
    private val drawablePool = ArrayList<DrawableEntity>(1536)
    private val drawableComparator = Comparator<DrawableEntity> { a, b -> a.sortY.compareTo(b.sortY) }
    private val resourceQueryBuffer = ArrayList<GameResource>(768)
    private val buildingQueryBuffer = ArrayList<GameBuilding>(160)
    private val unitQueryBuffer = ArrayList<GameUnit>(640)
    private val decorQueryBuffer = ArrayList<GameDecor>(384)
    private val ghostBuildingBuffer = ArrayList<GameBuilding>(96)
    private val ghostResourceBuffer = ArrayList<GameResource>(160)


    private var viewW = 1f
    private var viewH = 1f
    private var camLeft = 0f
    private var camTop = 0f
    private var camRight = 0f
    private var camBottom = 0f
    private var zoom = 1f
    private var camX = 0f
    private var camY = 0f

    private var minimapTerrainKey = ""
    private var minimapTerrainTexture: Int = 0
    private var minimapTerrainW = 0
    private var minimapTerrainH = 0
    private var minimapEntityStamp = -999f

    // Per-frame caches populated at the start of render() to avoid repeated
    // HashMap/string lookups inside the hot tile loop.
    private var perfMode = false
    private val biomeTileCache = arrayOfNulls<GlTexture>(5)
    private var waterTileCache: GlTexture? = null
    private var foamTileCache: GlTexture? = null

    // Cached per-frame time to avoid System.nanoTime() syscall inside hot loops
    private var frameTimeNano = 0L
    private var frameTimeSec = 0f

    // ── Terrain chunk cache ──
    // Pre-renders 16×16-tile chunks into GL textures so terrain isn't redrawn per-tile every frame.
    private val terrainChunkSize = 16
    private val terrainChunkCache = LinkedHashMap<Long, TerrainChunk>(128, 0.75f, true)
    private var terrainChunkSeed = ""
    private var terrainChunkMaxCount = 96

    private class TerrainChunk(val textureId: Int, val cx: Int, val cy: Int)

    private fun terrainChunkKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private val EDGE_FLAG = 1 shl 16

    class DrawableEntity {
        var entity: GameEntity? = null
        var sortY: Float = 0f
        var isSky: Boolean = false
    }

    fun onSurfaceCreated() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Android's BitmapFactory + GLUtils.texImage2D produces premultiplied-alpha
        // textures. Using GL_SRC_ALPHA would double-multiply the alpha on semi-
        // transparent pixels, causing bright/white fringing on building sprites
        // (the "whitish squares" bug). GL_ONE is correct for premultiplied data.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(20f / 255f, 51f / 255f, 64f / 255f, 1f)
        batch.create()
        textures.clear()
        minimapTerrainTexture = 0
        minimapTerrainKey = ""
        textures.buildAtlases()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewW = width.coerceAtLeast(1).toFloat()
        viewH = height.coerceAtLeast(1).toFloat()
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        Matrix.orthoM(projection, 0, 0f, viewW, viewH, 0f, -1f, 1f)
        batch.setProjection(projection)
    }

    fun render(state: GameState, minimapExpanded: Boolean) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        batch.begin()

        // Cache time once per frame — avoids System.nanoTime() syscall inside hot tile/entity loops
        frameTimeNano = System.nanoTime()
        frameTimeSec = state.time

        val cam = state.camera
        zoom = cam.zoom.coerceIn(CAMERA_MIN_ZOOM, CAMERA_MAX_ZOOM)
        camX = cam.x
        camY = cam.y
        camLeft = cam.x - (viewW / 2f) / zoom
        camTop = cam.y - (viewH / 2f) / zoom
        camRight = cam.x + (viewW / 2f) / zoom
        camBottom = cam.y + (viewH / 2f) / zoom

        // Refresh per-frame texture/setting caches once instead of N times
        // per tile inside drawTerrain.
        perfMode = state.settings.safeGraphics() == "performance"
        for (i in 0 until 5) biomeTileCache[i] = textures.get(biomeTileKeys[i])
        waterTileCache = textures.get("water")
        foamTileCache = textures.get("waterFoam")

        drawTerrain(state)

        val drawables = drawablesBuffer
        drawables.clear()
        collectDrawables(state, drawables, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawables.sortWith(drawableComparator)

        val perf = perfMode
        if (!perf) {
            for (d in drawables) if (!d.isSky) drawShadow(d)
        }
        for (d in drawables) if (!d.isSky) drawEntity(state, d)
        drawProjectiles(state, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawEffects(state, camLeft - 180f, camTop - 180f, camRight + 180f, camBottom + 180f)
        drawSelectionLinks(state)
        drawPlacementGhost(state)
        for (d in drawables) if (d.isSky) drawEntity(state, d)
        drawAtmosphere(state)
        drawMinimap(state, minimapExpanded)

        batch.end()
    }

    fun destroy() {
        textures.clear()
        if (minimapTerrainTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(minimapTerrainTexture), 0)
            minimapTerrainTexture = 0
        }
        // Clean up terrain chunk cache
        if (terrainChunkCache.isNotEmpty()) {
            val ids = IntArray(terrainChunkCache.size)
            var i = 0
            for (chunk in terrainChunkCache.values) ids[i++] = chunk.textureId
            GLES20.glDeleteTextures(ids.size, ids, 0)
            terrainChunkCache.clear()
        }
        batch.destroy()
    }

    private fun drawTerrain(state: GameState) {
        batch.drawRect(0f, 0f, viewW, viewH, 72, 170, 168, 255)

        val startCol = floor(camLeft / TILE).toInt() - 1
        val endCol = ceil(camRight / TILE).toInt() + 1
        val startRow = floor(camTop / TILE).toInt() - 1
        val endRow = ceil(camBottom / TILE).toInt() + 1
        val perf = perfMode
        val landCols = state.landCols
        val biomeMap = state.biomeMap
        val biomeMapSize = biomeMap.size

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val x = col * TILE
                val y = row * TILE
                if (landAtTile(state, col, row)) {
                    val idx = row * landCols + col
                    val biome = if (idx in 0 until biomeMapSize) biomeMap[idx].coerceIn(0, 4) else 0
                    drawGrassTile(state, col, row, x, y, biome)
                } else {
                    drawWaterTile(state, col, row, x, y, perf)
                }
            }
        }
    }

    private fun drawWaterTile(state: GameState, col: Int, row: Int, x: Float, y: Float, perf: Boolean) {
        val water = waterTileCache
        if (water != null) {
            drawTextureWorld(water, 0, 0, min(64, water.width), min(64, water.height), x, y, x + TILE, y + TILE, 255)
        } else {
            drawWorldRect(x, y, x + TILE, y + TILE, 72, 170, 168, 255)
        }
        if (perf) return
        val foam = foamTileCache ?: return
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
        val sx = fsx
        if (sx + 64 <= foam.width && fsy + 64 <= foam.height) {
            drawTextureWorld(foam, sx, fsy, 64, 64, x, y, x + TILE, y + TILE, 198)
        }
    }

    private fun drawGrassTile(state: GameState, col: Int, row: Int, x: Float, y: Float, biome: Int) {
        val tile = biomeTileCache[biome]
        val edge = edgeSourcePacked(state, col, row)
        val sx = edge and 0xFF
        val sy = (edge ushr 8) and 0xFF
        val isEdge = (edge and EDGE_FLAG) != 0
        if (tile != null) {
            drawTextureWorld(tile, sx, sy, 64, 64, x, y, x + TILE, y + TILE, 255)
        } else {
            when (biome) {
                1 -> drawWorldRect(x, y, x + TILE, y + TILE, 194, 186, 114, 255)
                2 -> drawWorldRect(x, y, x + TILE, y + TILE, 168, 194, 85, 255)
                3 -> drawWorldRect(x, y, x + TILE, y + TILE, 106, 144, 96, 255)
                4 -> drawWorldRect(x, y, x + TILE, y + TILE, 90, 144, 80, 255)
                else -> drawWorldRect(x, y, x + TILE, y + TILE, 130, 187, 106, 255)
            }
        }
        val variant = (col * 7 + row * 13 + 131) and 255
        if (!perfMode && !isEdge && (variant > 214 || variant % 24 > 18)) {
            drawWorldRect(x + 8f + (variant % 11), y + 14f, x + 38f + (variant % 11), y + 17f, 244, 239, 141, 19)
        }
    }

    private fun collectDrawables(state: GameState, out: MutableList<DrawableEntity>, left: Float, top: Float, right: Float, bottom: Float) {
        state.resourceIndex.queryRect(left, top, right, bottom, resourceQueryBuffer)
        for (r in resourceQueryBuffer) {
            if (r.dead) continue
            pushDrawable(out, r, r.y + if (r.type == ResourceType.TREE) -10f else 0f)
        }

        state.decorRenderIndex.queryRect(left - 420f, top - 420f, right + 420f, bottom + 420f, decorQueryBuffer)
        for (d in decorQueryBuffer) {
            if (d.dead) continue
            if (d.isSky) pushDrawable(out, d, d.y + 900000f, true) else pushDrawable(out, d, d.y - 18f)
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

    private fun drawShadow(d: DrawableEntity) {
        when (val e = d.entity ?: return) {
            is GameUnit -> {
                val def = UNITS[e.type] ?: return
                val halfW = if (e.type == "lancer") 24f else def.radius * 1.15f
                val halfH = if (e.type == "lancer") 8f else 8f
                drawGroundShadow(e.x, e.y + 3f, halfW, halfH, 92)
            }
            is GameBuilding -> {
                val def = BUILDINGS[e.type] ?: return
                val halfW = def.w * buildingShadowWFactor(e.type)
                val halfH = def.h * buildingShadowHFactor(e.type)
                val centerY = e.y + def.h * buildingShadowYOffsetFactor(e.type)
                drawGroundShadow(e.x, centerY, halfW, halfH, 68)
            }
            is GameResource -> {
                if (e.isAnimal && e.animalHp > 0f) drawAnimalShadow(e) else if (!e.isAnimal && e.type == ResourceType.FOOD) drawGroundShadow(e.x, e.y, 13f, 4f, 60)
            }
            is GameDecor -> if (!e.isSky && !e.isWater && e.kind.startsWith("bush")) drawGroundShadow(e.x, e.y, 18f, 5f, 54)
        }
    }

    private fun drawGroundShadow(x: Float, y: Float, halfW: Float, halfH: Float, alpha: Int) {
        val shadow = textures.get("shadow")
        if (shadow != null) {
            drawTextureWorld(shadow, 0, 0, shadow.width, shadow.height, x - halfW * 1.5f, y - halfH * 1.5f, x + halfW * 1.5f, y + halfH * 1.5f, alpha, 0f, 0f, 0f)
        } else {
            drawEllipseWorld(x, y, halfW, halfH, 0, 0, 0, alpha)
        }
    }

    private fun drawEntity(state: GameState, d: DrawableEntity) {
        when (val e = d.entity ?: return) {
            is GameUnit -> drawUnit(e)
            is GameBuilding -> drawBuilding(e)
            is GameResource -> drawResource(state, e)
            is GameDecor -> drawDecor(state, e)
        }
    }

    private fun drawUnit(unit: GameUnit) {
        val def = UNITS[unit.type] ?: return
        val fKey = FACTIONS.getOrNull(unit.faction)?.key ?: "blue"
        val key = getUnitAnimKey(unit, fKey)
        val tex = textures.get(key) ?: textures.get("u_${fKey}_${unit.type}_idle")
        val scale = def.scale * SPRITE_BOOST * MOBILE_WORLD_VISUAL_SCALE
        if (unit.selected) {
            drawCircleOutlineWorld(unit.x, unit.y, unit.range, 2f, 255, 236, 158, 88)
            drawSelectionOval(unit.x, unit.y, (def.radius * MOBILE_WORLD_VISUAL_SCALE + 8f).coerceAtLeast(16f))
        }
        if (tex != null) {
            val frames = (tex.width / def.fw).coerceAtLeast(1)
            val fps = unitAnimationFps(unit, key)
            val frame = ((unit.animTime * fps).toInt() % frames).coerceIn(0, frames - 1)
            if (unit.flash > 0f) drawAnchoredFrame(tex, frame * def.fw, 0, def.fw, def.fh, unit.x, unit.y, scale, unitVisualBaseline(unit.type), unit.face, 255, 1f, 1f, 1f)
            else drawAnchoredFrame(tex, frame * def.fw, 0, def.fw, def.fh, unit.x, unit.y, scale, unitVisualBaseline(unit.type), unit.face)
        } else {
            val color = FACTIONS.getOrNull(unit.faction)?.color ?: Color.BLUE
            drawEllipseWorld(unit.x, unit.y, def.radius * MOBILE_WORLD_VISUAL_SCALE, def.radius * MOBILE_WORLD_VISUAL_SCALE, Color.red(color), Color.green(color), Color.blue(color), 255)
        }
        if (unit.hp < unit.maxHp || unit.selected) {
            val barY = unit.y - unitVisualHeight(unit.type) * scale - 8f
            drawHpBar(unit.x, barY, unit.hp.toFloat() / unit.maxHp, 34f)
        }
    }

    private fun drawBuilding(building: GameBuilding) {
        val def = BUILDINGS[building.type] ?: return
        val fKey = FACTIONS.getOrNull(building.faction)?.key ?: "blue"
        val tex = textures.get("b_${fKey}_${building.type}")
        val visualW = def.w * MOBILE_BUILDING_VISUAL_SCALE
        val visualH = def.h * MOBILE_BUILDING_VISUAL_SCALE
        val drawX = building.x - visualW / 2f
        val drawY = building.y - visualH + def.placeYOffset
        val alpha = if (building.buildProgress < 1f) 150 else 255
        if (tex != null) drawTextureWorld(tex, 0, 0, tex.width, tex.height, drawX, drawY, drawX + visualW, drawY + visualH, alpha)
        else {
            val color = FACTIONS.getOrNull(building.faction)?.color ?: Color.BLUE
            drawWorldRect(drawX, drawY, drawX + visualW, drawY + visualH, Color.red(color), Color.green(color), Color.blue(color), alpha)
        }
        if (building.flash > 0f) drawWorldRect(drawX, drawY, drawX + visualW, drawY + visualH, 255, 255, 255, 96)
        if (defensiveArcherCount(building) > 0 && building.buildProgress >= 1f && !building.dead) drawBuildingArchers(building, fKey, def)
        if (building.selected) {
            drawSelectionBox(drawX - 4f, drawY - 4f, drawX + visualW + 4f, drawY + visualH + 4f)
            if (building.hasRally && !def.isTower && def.trains.isNotEmpty()) {
                val color = FACTIONS.getOrNull(building.faction)?.color ?: Color.WHITE
                drawEllipseWorld(building.rallyX, building.rallyY, 8f, 8f, Color.red(color), Color.green(color), Color.blue(color), 230)
                drawWorldLine(building.x, building.y, building.rallyX, building.rallyY, 2.0f, 245, 211, 125, 165)
            }
            val range = defensiveBuildingRange(building)
            if (range > 0f) drawCircleOutlineWorld(building.x, building.y, range, 2f, 255, 236, 158, 88)
        }
        if (building.buildProgress < 1f) {
            drawProgressBar(building.x, drawY - 8f, building.buildProgress, 48f)
        }
    }

    private data class BuildingArcherRenderSlot(val x: Float, val y: Float, val index: Int)

    private fun defenderIdleDrift(building: GameBuilding, index: Int, strengthX: Float, strengthY: Float): Pair<Float, Float> {
        val shooting = index < building.defenderShotUntil.size && frameTimeSec < building.defenderShotUntil[index]
        if (shooting) return Pair(0f, 0f)
        val seed = building.id * 1.731f + index * 2.417f
        val dx = sin(frameTimeSec * 0.72f + seed) * strengthX + sin(frameTimeSec * 0.31f + seed * 1.8f) * strengthX * 0.45f
        val dy = cos(frameTimeSec * 0.58f + seed) * strengthY
        return Pair(dx, dy)
    }

    private fun buildingArcherRenderSlots(building: GameBuilding, visualW: Float, visualH: Float, drawY: Float): List<BuildingArcherRenderSlot> {
        val count = defensiveArcherCount(building)
        if (count <= 0) return emptyList()
        if (building.type == "castle") {
            val pattern = when (count.coerceAtMost(3)) {
                1 -> listOf(0.00f to 0.10f)
                2 -> listOf(-0.20f to 0.12f, 0.20f to 0.12f)
                else -> listOf(-0.25f to 0.12f, 0.00f to 0.08f, 0.25f to 0.12f)
            }
            return (0 until count).map { i ->
                val slot = pattern[i.coerceAtMost(pattern.lastIndex)]
                val drift = defenderIdleDrift(building, i, 4.2f, 1.6f)
                BuildingArcherRenderSlot(building.x + visualW * slot.first + drift.first, drawY + visualH * slot.second + drift.second, i)
            }
        }
        val pattern = if (count == 1) listOf(0.00f) else listOf(-0.15f, 0.15f)
        return (0 until count).map { i ->
            val drift = defenderIdleDrift(building, i, 2.0f, 0.9f)
            BuildingArcherRenderSlot(building.x + visualW * pattern[i.coerceAtMost(pattern.lastIndex)] + drift.first, drawY + visualH * 0.12f + drift.second, i)
        }
    }

    private fun drawBuildingArchers(building: GameBuilding, fKey: String, buildingDef: BuildingDef) {
        val udef = UNITS["archer"] ?: return
        val idleTex = textures.get("u_${fKey}_archer_idle") ?: return
        val shootTex = textures.get("u_${fKey}_archer_shoot") ?: idleTex
        val visualW = buildingDef.w * MOBILE_BUILDING_VISUAL_SCALE
        val visualH = buildingDef.h * MOBILE_BUILDING_VISUAL_SCALE
        val drawY = building.y - visualH + buildingDef.placeYOffset
        val slots = buildingArcherRenderSlots(building, visualW, visualH, drawY)
        if (slots.isEmpty()) return
        val scale = udef.scale * SPRITE_BOOST * MOBILE_WORLD_VISUAL_SCALE
        val baseline = unitVisualBaseline("archer")
        for (slot in slots) {
            val shooting = slot.index < building.defenderShotUntil.size && frameTimeSec < building.defenderShotUntil[slot.index]
            val tex = if (shooting) shootTex else idleTex
            val frames = (tex.width / udef.fw).coerceAtLeast(1)
            val fps = if (shooting) 12f else 4f
            val rawFrame = if (shooting) {
                val remaining = (building.defenderShotUntil[slot.index] - frameTimeSec).coerceIn(0f, 0.52f)
                ((1f - remaining / 0.52f) * fps).toInt()
            } else ((frameTimeSec * fps).toInt() + building.id)
            val frame = rawFrame.floorMod(frames)
            val face = if (shooting && slot.index < building.defenderShotFace.size) building.defenderShotFace[slot.index] else 1
            drawAnchoredFrame(tex, frame * udef.fw, 0, udef.fw, udef.fh, slot.x, slot.y, scale, baseline, face)
        }
    }

    private fun drawResource(state: GameState, res: GameResource) {
        if (res.isAnimal && res.animalHp > 0f) {
            drawAnimal(state, res)
            return
        }
        if (res.depleted && res.type != ResourceType.TREE) return
        when (res.type) {
            ResourceType.TREE -> {
                val treeIndex = 1 + res.variant % 4
                val tex = if (res.depleted) textures.get("stump${1 + res.variant % 2}") else textures.get("tree$treeIndex")
                val fw = 192
                val fh = if (!res.depleted && (treeIndex == 3 || treeIndex == 4)) 192 else 256
                val scale = (if (res.depleted) 0.46f else 0.60f) * SPRITE_BOOST
                if (tex != null) {
                    val frameCount = (tex.width / fw).coerceAtLeast(1)
                    val frame = if (res.depleted) 0 else ((state.time * 4f + (res.id % frameCount)).toInt() % frameCount)
                    val baseline = if (res.depleted) stumpBaseline(1 + res.variant % 2) else treeBaseline(treeIndex)
                    drawAnchoredFrame(tex, frame * fw, 0, fw, fh, res.x, res.y, scale, baseline)
                } else fallbackResource(res)
            }
            ResourceType.GOLD -> {
                val variant = 1 + res.variant % 6
                val tex = textures.get("gold$variant")
                if (tex != null) {
                    drawAnchoredFrame(tex, 0, 0, 128, 128, res.x, res.y, 0.60f * SPRITE_BOOST, 79f)
                    val shine = textures.get("gold${variant}_hl")
                    if (shine != null) {
                        val a = ((sin(state.time * 1.5f + res.id) + 1f) * 0.5f * 150f).toInt().coerceIn(0, 150)
                        drawAnchoredFrame(shine, 0, 0, 128, 128, res.x, res.y, 0.60f * SPRITE_BOOST, 79f, 1, a)
                    }
                } else fallbackResource(res)
            }
            ResourceType.FOOD -> {
                val tex = textures.get("meat")
                if (tex != null) drawAnchoredFrame(tex, 0, 0, 64, 64, res.x, res.y, 0.68f * SPRITE_BOOST, 52f) else fallbackResource(res)
            }
        }
        if (res in state.selected) drawSelectionOval(resourceInteractionX(res), resourceInteractionY(res), resourceFootprint(res) + 8f)
    }

    private fun drawAnimal(state: GameState, res: GameResource) {
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
        val tex = textures.get(animalSpriteKey(res, suffix, moving, hurt)) ?: textures.get(animalSpriteKey(res, "Idle", moving = false, hurt = false))
        if (res in state.selected) drawSelectionOval(res.x, res.y, (def.radius + 8f).coerceAtLeast(16f))
        if (tex != null) {
            val frames = (tex.width / def.fw).coerceAtLeast(1)
            val rows = (tex.height / def.fh).coerceAtLeast(1)
            val fps = when { hurt -> def.fpsHurt; moving && res.panic > 0f -> def.fpsRun; moving -> def.fpsWalk; else -> def.fpsIdle }
            val frameSeed = if (moving || hurt) (res.id % frames).toFloat() else ((res.id * 31) % frames).toFloat()
            val frame = ((res.animTime * fps + frameSeed).toInt() % frames).coerceIn(0, frames - 1)
            val row = if (rows == 1) 0 else res.animalDir.coerceIn(0, rows - 1)
            val bob = if (moving) sin(state.time * 5f + res.id) * 1.1f else 0f
            val face = if (def.flipByFacing && (res.animalDir == 2 || res.vx < -1f)) -1 else 1
            if (res.flash > 0f || hurt) drawAnchoredFrame(tex, frame * def.fw, row * def.fh, def.fw, def.fh, res.x, res.y + bob, def.scale, def.baseline, face, 255, 1f, 0.45f, 0.45f)
            else drawAnchoredFrame(tex, frame * def.fw, row * def.fh, def.fw, def.fh, res.x, res.y + bob, def.scale, def.baseline, face)
        } else fallbackResource(res)
        if (res.animalHp < res.animalMaxHp || res in state.selected) drawHpBar(res.x, res.y - 36f, res.animalHp / res.animalMaxHp, 28f)
    }

    private fun drawAnimalShadow(res: GameResource) {
        val def = HUNT_ANIMALS[res.animalKind] ?: return
        val alpha = if (res.animalKind == "sheep") 42 else 64
        drawGroundShadow(res.x, res.y, def.shadowW, def.shadowH, alpha)
    }

    private fun drawDecor(state: GameState, decor: GameDecor) {
        val tex = textures.get(decor.kind) ?: return
        val isCloud = decor.isSky
        val spec = when {
            decor.kind.startsWith("bush") -> DecorSpec(128, 128, 79f, 0.55f, 1.05f)
            decor.kind.startsWith("rock") -> DecorSpec(64, 64, 51f, 0.85f, 0f)
            decor.kind.startsWith("waterRock") -> DecorSpec(64, 64, 56f, 0.92f, 3.5f)
            decor.kind == "rubberDuck" -> DecorSpec(32, 32, 29f, 0.9f, 2.2f)
            isCloud -> DecorSpec(tex.width, tex.height, tex.height * 0.70f, 0.42f * CLOUD_BOOST, 0f)
            else -> DecorSpec(tex.width, tex.height, tex.height.toFloat(), 1f, 0f)
        }
        val frames = (tex.width / spec.fw).coerceAtLeast(1)
        val frame = if (spec.fps > 0f) ((state.time * spec.fps + decor.id).toInt() % frames) else 0
        val bob = if (decor.isWater) sin(state.time * 1.35f + decor.id) * 2.2f else 0f
        val drift = if (decor.isSky) sin(state.time * 0.22f + decor.drift) * 18f else 0f
        drawAnchoredFrame(tex, frame * spec.fw, 0, spec.fw, spec.fh, decor.x + drift, decor.y + bob, spec.scale * decor.scale, spec.baseline, 1, if (isCloud) 178 else 255)
    }

    private fun drawProjectiles(state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        for (p in state.projectiles) {
            if (p.x < left || p.x > right || p.y < top || p.y > bottom) continue
            val fKey = FACTIONS.getOrNull(p.factionId)?.key ?: "blue"
            val arrow = textures.get("u_${fKey}_arrow")
            if (arrow != null) {
                drawRotatedTextureWorld(arrow, 8, 23, 48, 18, p.x, p.y, 34f, 12f, p.angle, 255)
            } else {
                drawWorldLine(p.x, p.y, p.x - cos(p.angle) * 10f, p.y - sin(p.angle) * 10f, 2.0f, 74, 48, 32, 255)
                drawWorldLine(p.x, p.y, p.x - cos(p.angle) * 9f, p.y - sin(p.angle) * 9f, 1.0f, 244, 231, 168, 255)
            }
        }
    }

    private fun drawEffects(state: GameState, left: Float, top: Float, right: Float, bottom: Float) {
        for (e in state.effects) {
            if (e.x < left || e.x > right || e.y < top || e.y > bottom) continue
            val progress = (e.time / e.maxTime).coerceIn(0f, 1f)
            val alpha = ((1f - progress) * 190).toInt().coerceIn(0, 255)
            when (e.kind) {
                "dust" -> drawEllipseWorld(e.x, e.y, 8f + progress * 20f, 8f + progress * 20f, 180, 160, 130, alpha)
                "hit" -> drawEllipseWorld(e.x, e.y, 4f + progress * 9f, 4f + progress * 9f, 255, 200, 50, alpha)
                "heal" -> drawEllipseWorld(e.x, e.y - progress * 10f, 6f + progress * 14f, 6f + progress * 14f, 100, 255, 150, alpha)
                "explosion" -> drawEllipseWorld(e.x, e.y, 10f + progress * 40f, 10f + progress * 40f, 255, 120, 30, alpha)
                "upgrade" -> drawCircleOutlineWorld(e.x, e.y, 14f + progress * 42f, 3f, 255, 232, 136, alpha)
                "moveMark" -> {
                    val r = (9f + progress * 15f) * e.scale
                    val cursor = textures.get("cursorAction")
                    if (cursor != null) {
                        val size = r * 2.1f
                        drawTextureWorld(cursor, 0, 0, cursor.width, cursor.height, e.x - size / 2f, e.y - size / 2f, e.x + size / 2f, e.y + size / 2f, alpha)
                    } else drawCircleOutlineWorld(e.x, e.y, r, 2f, 245, 211, 125, alpha)
                }
            }
        }
    }


    private fun drawAtmosphere(state: GameState) {
        val cycle = ((state.time % 180f) / 180f).coerceIn(0f, 1f)
        val night = kotlin.math.max(0f, kotlin.math.cos((cycle - 0.5f) * Math.PI.toFloat() * 2f))
        val dawn = (1f - kotlin.math.abs(cycle - 0.25f) / 0.10f).coerceIn(0f, 1f)
        val dusk = (1f - kotlin.math.abs(cycle - 0.75f) / 0.10f).coerceIn(0f, 1f)
        if (night > 0.02f) batch.drawRect(0f, 0f, viewW, viewH, 9, 18, 42, ((0.08f + night * 0.34f) * 255).toInt().coerceIn(0, 255))
        val warm = kotlin.math.max(dawn, dusk)
        if (warm > 0.02f) batch.drawRect(0f, 0f, viewW, viewH, 246, 153, 75, (warm * 31).toInt().coerceIn(0, 255))
        val weatherT = (state.time + 11f) % 92f
        if (weatherT < 18f) {
            batch.drawRect(0f, 0f, viewW, viewH, 18, 34, 48, 30)
            val rain = weatherT < 13f
            val count = if (rain) 80 else 55
            for (i in 0 until count) {
                val x = ((i * 73f + state.time * if (rain) -95f else 11f) % (viewW + 80f)) - 40f
                val y = ((i * 41f + state.time * if (rain) 610f else 68f) % (viewH + 70f)) - 35f
                if (rain) batch.drawLine(x, y, x - 5f, y + 18f, 1f, 185, 220, 255, 125)
                else batch.drawRect(x, y, x + 2.4f, y + 2.4f, 245, 250, 255, 160)
            }
        }
    }

    private fun drawSelectionLinks(state: GameState) {
        for (sel in state.selected) {
            if (sel is GameBuilding && sel.hasRally) {
                val def = BUILDINGS[sel.type]
                if (def != null && !def.isTower && def.trains.isNotEmpty()) drawWorldLine(sel.x, sel.y, sel.rallyX, sel.rallyY, 2f, 255, 255, 200, 120)
            }
        }
    }

    private fun drawPlacementGhost(state: GameState) {
        val type = state.placingBuilding ?: return
        val def = BUILDINGS[type] ?: return
        val x = state.pointerWorldX
        val y = state.pointerWorldY
        if (!x.isFinite() || !y.isFinite()) return
        val valid = canPlaceGhost(state, type, x, y)
        if (valid) {
            drawWorldRect(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, 90, 255, 140, 70)
            drawWorldRectOutline(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, 2.5f, 115, 255, 160, 210)
        } else {
            drawWorldRect(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, 255, 90, 80, 80)
            drawWorldRectOutline(x - def.placeW / 2f, y - def.placeH / 2f, x + def.placeW / 2f, y + def.placeH / 2f, 2.5f, 255, 100, 90, 220)
        }
        val fKey = FACTIONS.getOrNull(0)?.key ?: "blue"
        val tex = textures.get("b_${fKey}_$type")
        if (tex != null) {
            val visualW = def.w * MOBILE_BUILDING_VISUAL_SCALE
            val visualH = def.h * MOBILE_BUILDING_VISUAL_SCALE
            val drawX = x - visualW / 2f
            val drawY = y - visualH + def.placeYOffset
            drawTextureWorld(tex, 0, 0, tex.width, tex.height, drawX, drawY, drawX + visualW, drawY + visualH, if (valid) 130 else 95)
        }
    }

    private fun drawMinimap(state: GameState, expanded: Boolean) {
        val mapW = if (expanded) (viewW * 0.30f).coerceIn(220f, 340f) else (viewW * 0.18f).coerceIn(150f, 220f)
        val mapH = (mapW * 0.68f).coerceIn(96f, if (expanded) 230f else 150f)
        val x = viewW - mapW - 12f
        val y = 12f
        batch.drawRect(x, y, x + mapW, y + mapH, 18, 35, 42, 210)
        drawMinimapTerrain(state, x, y, mapW, mapH)
        drawMinimapEntities(state, x, y, mapW, mapH, expanded)
        val visibleW = viewW / state.camera.zoom
        val visibleH = viewH / state.camera.zoom
        val sx = mapW / state.worldW
        val sy = mapH / state.worldH
        val left = x + (state.camera.x - visibleW / 2f) * sx
        val top = y + (state.camera.y - visibleH / 2f) * sy
        val right = x + (state.camera.x + visibleW / 2f) * sx
        val bottom = y + (state.camera.y + visibleH / 2f) * sy
        batch.drawRectOutline(x + 1f, y + 1f, x + mapW - 1f, y + mapH - 1f, 2.3f, 170, 137, 96, 210)
        batch.drawRectOutline(left, top, right, bottom, 1.7f, 255, 246, 96, 255)
    }

    private fun drawMinimapTerrain(state: GameState, x: Float, y: Float, w: Float, h: Float) {
        val tw = w.toInt().coerceAtLeast(1)
        val th = h.toInt().coerceAtLeast(1)
        val key = "$tw:$th:${state.landCols}:${state.landRows}:${state.worldW}:${state.worldH}:${state.settings.seed}:${state.landMap.size}"
        if (key != minimapTerrainKey || minimapTerrainTexture == 0 || minimapTerrainW != tw || minimapTerrainH != th) {
            minimapTerrainKey = key
            minimapTerrainW = tw
            minimapTerrainH = th
            if (minimapTerrainTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(minimapTerrainTexture), 0)
            minimapTerrainTexture = buildMinimapTerrainTexture(state, tw, th)
        }
        if (minimapTerrainTexture != 0) batch.drawTexture(minimapTerrainTexture, x, y, x + w, y + h, 0f, 0f, 1f, 1f, 255)
    }

    private fun buildMinimapTerrainTexture(state: GameState, w: Int, h: Int): Int {
        if (state.landCols <= 0 || state.landRows <= 0) return 0
        val pixels = IntArray(w * h)
        val biomeColors = intArrayOf(
            Color.rgb(130, 187, 106), Color.rgb(194, 186, 114), Color.rgb(168, 194, 85), Color.rgb(106, 144, 96), Color.rgb(90, 144, 80)
        )
        val biomeEdgeColors = intArrayOf(
            Color.rgb(111, 167, 90), Color.rgb(154, 154, 90), Color.rgb(138, 170, 69), Color.rgb(90, 128, 80), Color.rgb(78, 122, 72)
        )
        for (py in 0 until h) {
            val row = ((py / h.toFloat()) * state.landRows).toInt().coerceIn(0, state.landRows - 1)
            for (px in 0 until w) {
                val col = ((px / w.toFloat()) * state.landCols).toInt().coerceIn(0, state.landCols - 1)
                val idx = row * state.landCols + col
                val land = idx in state.landMap.indices && state.landMap[idx].toInt() == 1
                if (!land) {
                    pixels[py * w + px] = Color.rgb(48, 128, 152)
                } else {
                    val biome = state.biomeMap.getOrElse(idx) { 0 }.coerceIn(0, 4)
                    val edge = !landAtTile(state, col - 1, row) || !landAtTile(state, col + 1, row) || !landAtTile(state, col, row - 1) || !landAtTile(state, col, row + 1)
                    pixels[py * w + px] = if (edge) biomeEdgeColors[biome] else biomeColors[biome]
                }
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return ids[0]
    }

    private fun drawMinimapEntities(state: GameState, x: Float, y: Float, w: Float, h: Float, expanded: Boolean) {
        minimapEntityStamp = state.time
        val sx = w / state.worldW
        val sy = h / state.worldH
        val resourceStep = when {
            expanded && !perfMode -> 1
            state.resources.size > 1800 -> 5
            state.resources.size > 1200 -> 4
            state.resources.size > 760 -> 3
            state.resources.size > 420 -> 2
            else -> 1
        }
        var ri = 0
        while (ri < state.resources.size) {
            val r = state.resources[ri]
            if (!r.dead && !r.depleted) {
                val rx = x + r.x * sx
                val ry = y + r.y * sy
                when (r.type) {
                    ResourceType.TREE -> batch.drawRect(rx - 1f, ry - 1f, rx + 1f, ry + 1f, 26, 90, 0, 255)
                    ResourceType.GOLD -> batch.drawRect(rx - 1.5f, ry - 1.5f, rx + 1.5f, ry + 1.5f, 212, 160, 23, 255)
                    ResourceType.FOOD -> batch.drawRect(rx - 1f, ry - 1f, rx + 1f, ry + 1f, 204, 102, 51, 255)
                }
            }
            ri += resourceStep
        }
        // Single pass over buildings/units; lookup faction colors once.
        val factionR = IntArray(5)
        val factionG = IntArray(5)
        val factionB = IntArray(5)
        for (i in 0 until 5) {
            val c = FACTIONS.getOrNull(i)?.color ?: Color.BLUE
            factionR[i] = Color.red(c); factionG[i] = Color.green(c); factionB[i] = Color.blue(c)
        }
        for (b in state.buildings) {
            if (b.dead) continue
            val f = b.faction
            if (f < 0 || f >= 5) continue
            val s = if (b.type == "castle") 4.8f else 3f
            val bx = x + b.x * sx; val by = y + b.y * sy
            batch.drawRect(bx - s, by - s, bx + s, by + s, factionR[f], factionG[f], factionB[f], 255)
        }
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue
            val f = u.faction
            if (f < 0 || f >= 5) continue
            val ux = x + u.x * sx; val uy = y + u.y * sy
            batch.drawRect(ux - 1.5f, uy - 1.5f, ux + 1.5f, uy + 1.5f, factionR[f], factionG[f], factionB[f], 255)
        }
    }

    private fun getUnitAnimKey(unit: GameUnit, fKey: String): String {
        val carryingCode = when (unit.carrying) { "wood" -> 1; "gold" -> 2; "food" -> 3; else -> 0 }
        val orderCode = unit.order.ordinal
        val closeEnough = when (unit.order) {
            UnitOrder.HARVEST -> {
                val target = unit.target
                if (target is GameResource) hypot(unit.x - resourceInteractionX(target), unit.y - resourceInteractionY(target)) <= 30f else false
            }
            UnitOrder.ATTACK -> {
                val target = unit.target
                if (target != null) hypot(unit.x - target.x, unit.y - target.y) <= ((UNITS[unit.type]?.range ?: 22f) + 8f) else false
            }
            UnitOrder.REPAIR -> {
                val target = unit.target as? GameBuilding
                if (target != null) isUnitAtBuildingWorkRange(unit, target) else false
            }
            else -> false
        }
        val stateHash = (unit.type.hashCode().toLong() shl 32) or (carryingCode.toLong() shl 16) or (orderCode.toLong() shl 8) or (if (closeEnough) 1L else 0L)
        if (unit.spriteStateHash == stateHash && unit.currentSpriteKey != null) return unit.currentSpriteKey!!
        unit.spriteStateHash = stateHash
        val suffix = when (unit.type) {
            "worker" -> when {
                unit.carrying != null && unit.order != UnitOrder.IDLE -> "carry_${unit.carrying}"
                unit.carrying != null -> "idle_${unit.carrying}"
                unit.order == UnitOrder.HARVEST -> {
                    val target = unit.target
                    if (target is GameResource && closeEnough) {
                        when (target.type) { ResourceType.TREE -> "chop"; ResourceType.GOLD -> "mine"; ResourceType.FOOD -> "fight" }
                    } else if (target is GameResource) "run" else "idle"
                }
                unit.order == UnitOrder.REPAIR -> if (closeEnough) "build" else "run"
                unit.order == UnitOrder.MOVE || unit.order == UnitOrder.ATTACK_MOVE || unit.order == UnitOrder.GARRISON -> "run"
                unit.order == UnitOrder.ATTACK -> if (closeEnough) "fight" else "run"
                else -> "idle"
            }
            "warrior" -> unitCombatAnim(unit, "attack")
            "archer" -> unitCombatAnim(unit, "shoot")
            "lancer" -> unitCombatAnim(unit, "attack")
            "monk" -> unitCombatAnim(unit, "heal")
            else -> "idle"
        }
        val key = "u_${fKey}_${unit.type}_$suffix"
        unit.currentSpriteKey = key
        return key
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

    private fun unitAnimationFps(unit: GameUnit, animKey: String): Float = when (unit.order) {
        UnitOrder.IDLE -> 4f
        UnitOrder.MOVE, UnitOrder.ATTACK_MOVE, UnitOrder.GARRISON -> 8f
        UnitOrder.ATTACK -> if (animKey.endsWith("_run")) 8f else 6f
        UnitOrder.HARVEST, UnitOrder.REPAIR -> if (animKey.endsWith("_run")) 8f else 6f
    }

    private fun isUnitAtBuildingWorkRange(unit: GameUnit, building: GameBuilding): Boolean {
        val def = BUILDINGS[building.type] ?: return hypot(unit.x - building.x, unit.y - building.y) <= 28f
        val left = building.x - def.placeW / 2f
        val right = building.x + def.placeW / 2f
        val top = building.y - def.placeH / 2f
        val bottom = building.y + def.placeH / 2f
        val dx = max(max(left - unit.x, 0f), unit.x - right)
        val dy = max(max(top - unit.y, 0f), unit.y - bottom)
        return sqrt(dx * dx + dy * dy) <= 42f
    }

    private fun animalSpriteKey(res: GameResource, suffix: String, moving: Boolean, hurt: Boolean): String {
        if (res.animalKind == "sheep") {
            return when {
                !moving && !hurt && ((res.id * 1103515245 + 12345) ushr 28) > 12 -> "sheepGrass"
                suffix == "Walk" || suffix == "Run" || suffix == "Flight" -> "sheepMove"
                else -> "sheepIdle"
            }
        }
        return "animal${animalKeyName(res.animalKind)}$suffix"
    }

    private fun animalKeyName(kind: String): String = when (kind) {
        "grouse" -> "Grouse"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    private fun resourceInteractionX(res: GameResource): Float = res.x
    private fun resourceInteractionY(res: GameResource): Float = when {
        res.isAnimal -> res.y
        res.type == ResourceType.TREE && res.depleted -> res.y - 14f
        res.type == ResourceType.TREE -> res.y - 42f
        res.type == ResourceType.GOLD -> res.y - 16f
        else -> res.y - 2f
    }

    private fun resourceFootprint(res: GameResource): Float = when {
        res.isAnimal -> ((HUNT_ANIMALS[res.animalKind]?.radius ?: 12f) + 2f).coerceAtLeast(12f)
        res.type == ResourceType.TREE && res.depleted -> 18f
        res.type == ResourceType.TREE -> 34f
        res.type == ResourceType.GOLD -> 24f
        else -> 16f
    }

    private fun fallbackResource(res: GameResource) {
        when (res.type) {
            ResourceType.TREE -> drawEllipseWorld(res.x, res.y, 14f, 14f, 42, 106, 16, 255)
            ResourceType.GOLD -> drawEllipseWorld(res.x, res.y, 14f, 14f, 230, 202, 89, 255)
            ResourceType.FOOD -> drawEllipseWorld(res.x, res.y, 14f, 14f, 204, 80, 60, 255)
        }
    }

    private fun unitVisualBaseline(type: String): Float = when (type) { "worker" -> 135f; "warrior" -> 137f; "archer" -> 136f; "lancer" -> 198f; "monk" -> 134f; else -> 136f }
    private fun unitVisualHeight(type: String): Float = when (type) { "worker" -> 72f; "warrior" -> 90f; "archer" -> 88f; "lancer" -> 150f; "monk" -> 70f; else -> 76f }
    private fun buildingShadowWFactor(type: String): Float = when (type) { "castle" -> 0.42f; "house" -> 0.42f; "barracks", "archery", "monastery" -> 0.44f; "tower" -> 0.34f; else -> 0.40f }
    private fun buildingShadowHFactor(type: String): Float = when (type) { "castle" -> 0.125f; "house" -> 0.125f; "tower" -> 0.105f; else -> 0.12f }
    private fun buildingShadowYOffsetFactor(type: String): Float = when (type) { "castle" -> 0.18f; "house" -> 0.17f; "tower" -> 0.16f; else -> 0.17f }
    private fun treeBaseline(index: Int): Float = when (index) { 1 -> 241f; 2 -> 249f; 3 -> 170f; 4 -> 168f; else -> 241f }
    private fun stumpBaseline(index: Int): Float = when (index) { 1 -> 240f; 2 -> 245f; 3 -> 232f; 4 -> 228f; else -> 240f }

    data class DecorSpec(val fw: Int, val fh: Int, val baseline: Float, val scale: Float, val fps: Float)

    private fun edgeSourcePacked(state: GameState, col: Int, row: Int): Int {
        val n = !landAtTile(state, col, row - 1)
        val s = !landAtTile(state, col, row + 1)
        val w = !landAtTile(state, col - 1, row)
        val e = !landAtTile(state, col + 1, row)
        var sx = 64
        var sy = 64
        var edge = true
        when {
            n && w -> { sx = 0; sy = 0 }
            n && e -> { sx = 128; sy = 0 }
            s && w -> { sx = 0; sy = 128 }
            s && e -> { sx = 128; sy = 128 }
            n -> { sx = 64; sy = 0 }
            s -> { sx = 64; sy = 128 }
            w -> { sx = 0; sy = 64 }
            e -> { sx = 128; sy = 64 }
            else -> { edge = false }
        }
        return sx or (sy shl 8) or (if (edge) EDGE_FLAG else 0)
    }

    private fun landAtTile(state: GameState, col: Int, row: Int): Boolean {
        if (col < 0 || row < 0 || col >= state.landCols || row >= state.landRows) return false
        val idx = row * state.landCols + col
        return idx in state.landMap.indices && state.landMap[idx].toInt() == 1
    }

    private fun canPlaceGhost(state: GameState, type: String, x: Float, y: Float): Boolean {
        val def = BUILDINGS[type] ?: return false
        if (!state.isSafeLand(x, y, def.placeW / 2f)) return false
        val pad = max(def.placeW, def.placeH) + 96f
        state.buildingIndex.queryRect(x - pad, y - pad, x + pad, y + pad, ghostBuildingBuffer)
        for (b in ghostBuildingBuffer) {
            if (b.dead) continue
            val bd = BUILDINGS[b.type] ?: continue
            if (rectsOverlapCentered(x, y, def.placeW, def.placeH, b.x, b.y, bd.placeW, bd.placeH)) return false
        }
        state.resourceIndex.queryRect(x - pad, y - pad, x + pad, y + pad, ghostResourceBuffer)
        val minD = def.placeW / 2f + 24f
        val minD2 = minD * minD
        for (r in ghostResourceBuffer) {
            if (!r.dead && !r.depleted) {
                val dx = x - r.x
                val dy = y - r.y
                if (dx * dx + dy * dy < minD2) return false
            }
        }
        return true
    }

    private fun rectsOverlapCentered(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return ax - aw / 2f < bx + bw / 2f && ax + aw / 2f > bx - bw / 2f && ay - ah / 2f < by + bh / 2f && ay + ah / 2f > by - bh / 2f
    }

    private fun drawAnchoredFrame(tex: GlTexture, sx: Int, sy: Int, fw: Int, fh: Int, x: Float, baseY: Float, scale: Float, baseline: Float, face: Int = 1, alpha: Int = 255, r: Float = 1f, g: Float = 1f, b: Float = 1f) {
        val safeSx = sx.coerceIn(0, (tex.width - 1).coerceAtLeast(0))
        val safeSy = sy.coerceIn(0, (tex.height - 1).coerceAtLeast(0))
        val safeFw = fw.coerceAtMost(tex.width - safeSx).coerceAtLeast(1)
        val safeFh = fh.coerceAtMost(tex.height - safeSy).coerceAtLeast(1)
        val w = safeFw * scale
        val h = safeFh * scale
        drawTextureWorld(tex, safeSx, safeSy, safeFw, safeFh, x - w / 2f, baseY - baseline * scale, x + w / 2f, baseY - baseline * scale + h, alpha, r, g, b, face < 0)
    }

    private fun drawRotatedTextureWorld(tex: GlTexture, sx: Int, sy: Int, fw: Int, fh: Int, cx: Float, cy: Float, w: Float, h: Float, angle: Float, alpha: Int) {
        val scx = worldXToScreen(cx)
        val scy = worldYToScreen(cy)
        val sw = w * zoom
        val sh = h * zoom
        batch.drawRotatedTexture(tex.id, scx, scy, sw, sh, angle, tex.u(sx), tex.v(sy), tex.u(sx + fw), tex.v(sy + fh), alpha)
    }

    private fun drawTextureWorld(tex: GlTexture, sx: Int, sy: Int, fw: Int, fh: Int, left: Float, top: Float, right: Float, bottom: Float, alpha: Int, r: Float = 1f, g: Float = 1f, b: Float = 1f, flipX: Boolean = false) {
        val x1 = worldXToScreen(left)
        val y1 = worldYToScreen(top)
        val x2 = worldXToScreen(right)
        val y2 = worldYToScreen(bottom)
        batch.drawTexture(tex.id, x1, y1, x2, y2, tex.u(sx), tex.v(sy), tex.u(sx + fw), tex.v(sy + fh), alpha, r, g, b, flipX)
    }

    private fun drawWorldRect(left: Float, top: Float, right: Float, bottom: Float, r: Int, g: Int, b: Int, a: Int) {
        batch.drawRect(worldXToScreen(left), worldYToScreen(top), worldXToScreen(right), worldYToScreen(bottom), r, g, b, a)
    }

    private fun drawWorldRectOutline(left: Float, top: Float, right: Float, bottom: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
        val x1 = worldXToScreen(left); val y1 = worldYToScreen(top); val x2 = worldXToScreen(right); val y2 = worldYToScreen(bottom)
        batch.drawRectOutline(x1, y1, x2, y2, thickness, r, g, b, a)
    }

    private fun drawWorldLine(x1w: Float, y1w: Float, x2w: Float, y2w: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
        val (x1, y1) = worldToScreen(x1w, y1w)
        val (x2, y2) = worldToScreen(x2w, y2w)
        batch.drawLine(x1, y1, x2, y2, thickness, r, g, b, a)
    }

    private fun drawEllipseWorld(cx: Float, cy: Float, rx: Float, ry: Float, r: Int, g: Int, b: Int, a: Int) {
        val strips = if (perfMode) 6 else 8
        for (i in -strips..strips) {
            val yn = i / strips.toFloat()
            val half = rx * sqrt((1f - yn * yn).coerceAtLeast(0f))
            val y1 = cy + (yn - 0.5f / strips) * ry
            val y2 = cy + (yn + 0.5f / strips) * ry
            drawWorldRect(cx - half, y1, cx + half, y2, r, g, b, a)
        }
    }

    private fun drawEllipseOutlineWorld(cx: Float, cy: Float, rx: Float, ry: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
        val segs = if (perfMode) 12 else 18
        var prevX = cx + rx
        var prevY = cy
        for (i in 1..segs) {
            val t = (i / segs.toFloat()) * (Math.PI.toFloat() * 2f)
            val x = cx + cos(t) * rx
            val y = cy + sin(t) * ry
            drawWorldLine(prevX, prevY, x, y, thickness, r, g, b, a)
            prevX = x; prevY = y
        }
    }

    private fun drawCircleOutlineWorld(cx: Float, cy: Float, radius: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
        val segs = if (perfMode) 24 else 36
        var prevX = cx + radius
        var prevY = cy
        for (i in 1..segs) {
            val t = (i / segs.toFloat()) * (Math.PI.toFloat() * 2f)
            val x = cx + cos(t) * radius
            val y = cy + sin(t) * radius
            drawWorldLine(prevX, prevY, x, y, thickness, r, g, b, a)
            prevX = x; prevY = y
        }
    }

    private fun drawSelectionOval(x: Float, y: Float, r: Float) {
        val cursor = textures.get("cursorSelect")
        if (cursor != null) {
            val w = r * 2.2f
            val h = r * 1.5f
            drawCursorCorners(x - w / 2f, y - h / 2f + 4f, x + w / 2f, y + h / 2f + 4f, cursor, 230)
        } else drawEllipseOutlineWorld(x, y + 4f, r, r * 0.45f, 2.2f, 245, 211, 125, 170)
    }

    private fun drawSelectionBox(left: Float, top: Float, right: Float, bottom: Float) {
        val cursor = textures.get("cursorSelect")
        if (cursor != null) drawCursorCorners(left, top, right, bottom, cursor, 230) else drawWorldRectOutline(left, top, right, bottom, 2.2f, 245, 211, 125, 170)
    }

    private fun drawCursorCorners(left: Float, top: Float, right: Float, bottom: Float, cursor: GlTexture, alpha: Int) {
        val cw = 42
        val ch = 42
        val dw = min(32f, max(10f, (right - left) / 2f - 1f))
        val dh = min(32f, max(10f, (bottom - top) / 2f - 1f))
        drawTextureWorld(cursor, 0, 0, cw, ch, left, top, left + dw, top + dh, alpha)
        drawTextureWorld(cursor, 128 - cw, 0, cw, ch, right - dw, top, right, top + dh, alpha)
        drawTextureWorld(cursor, 0, 128 - ch, cw, ch, left, bottom - dh, left + dw, bottom, alpha)
        drawTextureWorld(cursor, 128 - cw, 128 - ch, cw, ch, right - dw, bottom - dh, right, bottom, alpha)
    }

    private fun drawHpBar(x: Float, y: Float, pct: Float, width: Float) {
        val p = pct.coerceIn(0f, 1f)
        val height = 4.5f
        val left = x - width / 2f
        drawWorldRect(left - 1f, y - 1f, left + width + 1f, y + height + 1f, 24, 22, 18, 190)
        when {
            p > 0.6f -> drawWorldRect(left, y, left + width * p, y + height, 64, 192, 64, 255)
            p > 0.3f -> drawWorldRect(left, y, left + width * p, y + height, 192, 192, 64, 255)
            else -> drawWorldRect(left, y, left + width * p, y + height, 192, 64, 64, 255)
        }
    }

    private fun drawProgressBar(x: Float, y: Float, pct: Float, width: Float) {
        val height = 3.5f
        val left = x - width / 2f
        drawWorldRect(left - 1f, y - 1f, left + width + 1f, y + height + 1f, 24, 22, 18, 190)
        drawWorldRect(left, y, left + width * pct.coerceIn(0f, 1f), y + height, 96, 160, 255, 255)
    }


    private fun Int.floorMod(divisor: Int): Int {
        if (divisor <= 0) return 0
        val r = this % divisor
        return if (r < 0) r + divisor else r
    }

    private fun worldXToScreen(x: Float): Float = (x - camX) * zoom + viewW / 2f
    private fun worldYToScreen(y: Float): Float = (y - camY) * zoom + viewH / 2f
    private fun worldToScreen(x: Float, y: Float): Pair<Float, Float> = Pair(worldXToScreen(x), worldYToScreen(y))

    data class GlTexture(
        val id: Int,
        val width: Int,
        val height: Int,
        val atlasWidth: Int = width,
        val atlasHeight: Int = height,
        val atlasX: Int = 0,
        val atlasY: Int = 0
    ) {
        fun u(sourceX: Int): Float = (atlasX + sourceX.coerceIn(0, width)).toFloat() / atlasWidth.toFloat()
        fun v(sourceY: Int): Float = (atlasY + sourceY.coerceIn(0, height)).toFloat() / atlasHeight.toFloat()
    }

    private class GlTextureCache(private val assets: AssetManager) {
        private val regions = HashMap<String, GlTexture>(384)
        private val standalone = object : LinkedHashMap<String, GlTexture>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GlTexture>?): Boolean {
                val remove = size > 96
                if (remove) eldest?.value?.let { GLES20.glDeleteTextures(1, intArrayOf(it.id), 0) }
                return remove
            }
        }
        private val atlasTextureIds = ArrayList<Int>(8)
        private var atlasesReady = false

        fun buildAtlases() {
            clear()
            val maxTexture = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            val pageSize = maxTexture[0].coerceAtLeast(2048).coerceAtMost(4096)
            val sourceByPath = LinkedHashMap<String, AtlasSource>(384)
            for (key in assets.registeredKeys()) {
                val path = assets.pathForKey(key) ?: continue
                val existing = sourceByPath[path]
                if (existing != null) {
                    existing.keys.add(key)
                } else {
                    val bounds = assets.textureBounds(key) ?: continue
                    sourceByPath[path] = AtlasSource(path, bounds.first, bounds.second, ArrayList<String>(2).also { it.add(key) })
                }
            }
            val sources = sourceByPath.values.sortedWith(compareByDescending<AtlasSource> { it.height }.thenByDescending { it.width })
            var page = AtlasPage(pageSize, assets)
            val oversize = ArrayList<AtlasSource>()

            fun flushPage() {
                if (page.isEmpty()) return
                uploadAtlasPage(page)
                page.dispose()
                page = AtlasPage(pageSize, assets)
            }

            for (src in sources) {
                val w = src.width
                val h = src.height
                if (w + AtlasPage.PADDING * 2 > pageSize || h + AtlasPage.PADDING * 2 > pageSize) {
                    oversize.add(src)
                    continue
                }
                if (!page.place(src)) {
                    flushPage()
                    if (!page.place(src)) {
                        oversize.add(src)
                    }
                }
            }
            flushPage()
            for (src in oversize) uploadStandaloneSource(src)
            assets.releaseDecodedBitmaps()
            atlasesReady = true
        }

        fun get(key: String): GlTexture? {
            regions[key]?.let { return it }
            standalone[key]?.let { return it }
            if (!atlasesReady) buildAtlases()
            regions[key]?.let { return it }
            val bitmap = assets.decodeForTexture(key) ?: return null
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                bitmap.recycleSafely()
                return null
            }
            val tex = uploadSingleBitmap(bitmap)
            bitmap.recycleSafely()
            standalone[key] = tex
            return tex
        }

        fun clear() {
            if (atlasTextureIds.isNotEmpty()) {
                val ids = atlasTextureIds.toIntArray()
                GLES20.glDeleteTextures(ids.size, ids, 0)
                atlasTextureIds.clear()
            }
            if (standalone.isNotEmpty()) {
                val ids = IntArray(standalone.size)
                var i = 0
                for (tex in standalone.values) ids[i++] = tex.id
                GLES20.glDeleteTextures(ids.size, ids, 0)
                standalone.clear()
            }
            regions.clear()
            atlasesReady = false
        }

        private fun uploadAtlasPage(page: AtlasPage) {
            val textureId = uploadBitmap(page.bitmap)
            atlasTextureIds.add(textureId)
            for (placement in page.placements) {
                val src = placement.source
                for (key in src.keys) {
                    regions[key] = GlTexture(
                        id = textureId,
                        width = src.width,
                        height = src.height,
                        atlasWidth = page.size,
                        atlasHeight = page.size,
                        atlasX = placement.x,
                        atlasY = placement.y
                    )
                }
            }
        }

        private fun uploadStandaloneSource(src: AtlasSource) {
            val key = src.keys.firstOrNull() ?: return
            val bitmap = assets.decodeForTexture(key) ?: return
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                bitmap.recycleSafely()
                return
            }
            val tex = uploadSingleBitmap(bitmap)
            bitmap.recycleSafely()
            for (alias in src.keys) standalone[alias] = tex
        }

        private fun uploadSingleBitmap(bitmap: Bitmap): GlTexture {
            val textureId = uploadBitmap(bitmap)
            return GlTexture(textureId, bitmap.width, bitmap.height)
        }

        private fun uploadBitmap(bitmap: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return ids[0]
        }

        private data class AtlasSource(val path: String, val width: Int, val height: Int, val keys: ArrayList<String>)

        private data class AtlasPlacement(val source: AtlasSource, val x: Int, val y: Int)

        private class AtlasPage(val size: Int, private val assets: AssetManager) {
            val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            private val canvas = Canvas(bitmap)
            val placements = ArrayList<AtlasPlacement>(128)
            private var rowX = PADDING
            private var rowY = PADDING
            private var rowH = 0

            fun isEmpty(): Boolean = placements.isEmpty()

            fun place(source: AtlasSource): Boolean {
                val w = source.width
                val h = source.height
                if (rowX + w + PADDING > size) {
                    rowX = PADDING
                    rowY += rowH + PADDING
                    rowH = 0
                }
                if (rowY + h + PADDING > size) return false
                val key = source.keys.firstOrNull() ?: return false
                val sourceBitmap = assets.decodeForTexture(key) ?: return false
                if (sourceBitmap.isRecycled || sourceBitmap.width <= 0 || sourceBitmap.height <= 0) {
                    recycleBitmap(sourceBitmap)
                    return false
                }
                val x = rowX
                val y = rowY
                canvas.drawBitmap(sourceBitmap, x.toFloat(), y.toFloat(), null)
                duplicateEdges(sourceBitmap, x, y)
                recycleBitmap(sourceBitmap)
                placements.add(AtlasPlacement(source, x, y))
                rowX += w + PADDING
                rowH = max(rowH, h)
                return true
            }

            private fun duplicateEdges(src: Bitmap, x: Int, y: Int) {
                val w = src.width
                val h = src.height
                if (x <= 0 || y <= 0 || x + w >= size || y + h >= size) return
                for (ix in 0 until w) {
                    bitmap.setPixel(x + ix, y - 1, src.getPixel(ix, 0))
                    bitmap.setPixel(x + ix, y + h, src.getPixel(ix, h - 1))
                }
                for (iy in 0 until h) {
                    bitmap.setPixel(x - 1, y + iy, src.getPixel(0, iy))
                    bitmap.setPixel(x + w, y + iy, src.getPixel(w - 1, iy))
                }
                bitmap.setPixel(x - 1, y - 1, src.getPixel(0, 0))
                bitmap.setPixel(x + w, y - 1, src.getPixel(w - 1, 0))
                bitmap.setPixel(x - 1, y + h, src.getPixel(0, h - 1))
                bitmap.setPixel(x + w, y + h, src.getPixel(w - 1, h - 1))
            }

            fun dispose() = recycleBitmap(bitmap)

            private fun recycleBitmap(value: Bitmap) {
                if (!value.isRecycled) value.recycle()
            }

            companion object { const val PADDING = 2 }
        }

        private fun Bitmap.recycleSafely() {
            if (!isRecycled) recycle()
        }
    }

    private class SpriteBatch {
        private val strideFloats = 8
        private val maxQuads = 8192
        private val data = FloatArray(maxQuads * 6 * strideFloats)
        private val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private var vertexCount = 0
        private var activeTexture = -1
        private var program = 0
        private var whiteTexture = 0
        private var aPosition = 0
        private var aTexCoord = 0
        private var aColor = 0
        private var uProjection = 0
        private var uTexture = 0
        private val projection = FloatArray(16)

        fun create() {
            destroy()
            val vertex = """
                uniform mat4 uProjection;
                attribute vec2 aPosition;
                attribute vec2 aTexCoord;
                attribute vec4 aColor;
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
                    vTexCoord = aTexCoord;
                    vColor = aColor;
                }
            """.trimIndent()
            val fragment = """
                precision mediump float;
                uniform sampler2D uTexture;
                varying vec2 vTexCoord;
                varying vec4 vColor;
                void main() {
                    vec4 texel = texture2D(uTexture, vTexCoord);
                    // Textures are premultiplied-alpha (Android default).
                    // Vertex color tint + alpha must premultiply into the output
                    // so GL_ONE / GL_ONE_MINUS_SRC_ALPHA blending is correct.
                    gl_FragColor = vec4(texel.rgb * vColor.rgb * vColor.a,
                                       texel.a * vColor.a);
                }
            """.trimIndent()
            program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertex), compileShader(GLES20.GL_FRAGMENT_SHADER, fragment))
            aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            aColor = GLES20.glGetAttribLocation(program, "aColor")
            uProjection = GLES20.glGetUniformLocation(program, "uProjection")
            uTexture = GLES20.glGetUniformLocation(program, "uTexture")
            whiteTexture = createWhiteTexture()
        }

        fun setProjection(matrix: FloatArray) {
            System.arraycopy(matrix, 0, projection, 0, 16)
        }

        fun begin() {
            vertexCount = 0
            activeTexture = -1
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(uProjection, 1, false, projection, 0)
            GLES20.glUniform1i(uTexture, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }

        fun end() { flush() }

        fun drawRect(x1: Float, y1: Float, x2: Float, y2: Float, r: Int, g: Int, b: Int, a: Int) {
            drawTexture(whiteTexture, x1, y1, x2, y2, 0f, 0f, 1f, 1f, a, r / 255f, g / 255f, b / 255f)
        }

        fun drawRectOutline(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
            drawRect(x1, y1, x2, y1 + thickness, r, g, b, a)
            drawRect(x1, y2 - thickness, x2, y2, r, g, b, a)
            drawRect(x1, y1, x1 + thickness, y2, r, g, b, a)
            drawRect(x2 - thickness, y1, x2, y2, r, g, b, a)
        }

        fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, r: Int, g: Int, b: Int, a: Int) {
            val dx = x2 - x1
            val dy = y2 - y1
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
            val nx = -dy / len * thickness * 0.5f
            val ny = dx / len * thickness * 0.5f
            drawQuad(whiteTexture, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, 0f, 0f, 1f, 1f, a, r / 255f, g / 255f, b / 255f)
        }

        fun drawTexture(texture: Int, x1: Float, y1: Float, x2: Float, y2: Float, u1In: Float, v1: Float, u2In: Float, v2: Float, alpha: Int, r: Float = 1f, g: Float = 1f, b: Float = 1f, flipX: Boolean = false) {
            val u1 = if (flipX) u2In else u1In
            val u2 = if (flipX) u1In else u2In
            drawQuad(texture, x1, y1, x2, y1, x2, y2, x1, y2, u1, v1, u2, v2, alpha, r, g, b)
        }

        fun drawRotatedTexture(texture: Int, cx: Float, cy: Float, w: Float, h: Float, angle: Float, u1: Float, v1: Float, u2: Float, v2: Float, alpha: Int) {
            val hw = w * 0.5f
            val hh = h * 0.5f
            val c = cos(angle)
            val s = sin(angle)
            fun rx(x: Float, y: Float) = cx + x * c - y * s
            fun ry(x: Float, y: Float) = cy + x * s + y * c
            drawQuad(texture, rx(-hw, -hh), ry(-hw, -hh), rx(hw, -hh), ry(hw, -hh), rx(hw, hh), ry(hw, hh), rx(-hw, hh), ry(-hw, hh), u1, v1, u2, v2, alpha, 1f, 1f, 1f)
        }

        private fun drawQuad(texture: Int, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, u1: Float, v1: Float, u2: Float, v2: Float, alpha: Int, r: Float, g: Float, b: Float) {
            if (activeTexture != texture || vertexCount + 6 >= maxQuads * 6) {
                flush()
                activeTexture = texture
            }
            val a = alpha.coerceIn(0, 255) / 255f
            putVertex(x1, y1, u1, v1, r, g, b, a)
            putVertex(x2, y2, u2, v1, r, g, b, a)
            putVertex(x3, y3, u2, v2, r, g, b, a)
            putVertex(x1, y1, u1, v1, r, g, b, a)
            putVertex(x3, y3, u2, v2, r, g, b, a)
            putVertex(x4, y4, u1, v2, r, g, b, a)
        }

        private fun putVertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
            val i = vertexCount * strideFloats
            data[i] = x; data[i + 1] = y; data[i + 2] = u; data[i + 3] = v; data[i + 4] = r; data[i + 5] = g; data[i + 6] = b; data[i + 7] = a
            vertexCount++
        }

        private fun flush() {
            if (vertexCount == 0 || activeTexture == -1 || program == 0) return
            buffer.clear()
            buffer.put(data, 0, vertexCount * strideFloats)
            buffer.position(0)
            GLES20.glUseProgram(program)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeTexture)
            GLES20.glEnableVertexAttribArray(aPosition)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            GLES20.glEnableVertexAttribArray(aColor)
            buffer.position(0)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, strideFloats * 4, buffer)
            buffer.position(2)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, strideFloats * 4, buffer)
            buffer.position(4)
            GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, strideFloats * 4, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            vertexCount = 0
        }

        fun destroy() {
            if (whiteTexture != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(whiteTexture), 0)
                whiteTexture = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun createWhiteTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val pixel = ByteBuffer.allocateDirect(4)
            pixel.put(255.toByte()).put(255.toByte()).put(255.toByte()).put(255.toByte()).position(0)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
            return ids[0]
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("OpenGL shader compile failed: $log")
            }
            return shader
        }

        private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
            val linked = GLES20.glCreateProgram()
            GLES20.glAttachShader(linked, vertexShader)
            GLES20.glAttachShader(linked, fragmentShader)
            GLES20.glLinkProgram(linked)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            val status = IntArray(1)
            GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(linked)
                GLES20.glDeleteProgram(linked)
                throw IllegalStateException("OpenGL program link failed: $log")
            }
            return linked
        }
    }
}
