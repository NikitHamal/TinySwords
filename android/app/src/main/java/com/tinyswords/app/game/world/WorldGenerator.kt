package com.tinyswords.app.game.world

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.rngHash
import kotlin.math.*
import kotlin.random.Random

class WorldGenerator(private val state: GameState) {
    private val rng = Random(state.settings.seed.hashCode().toLong())
    private val worldW = state.worldW
    private val worldH = state.worldH
    private val tileCols = (worldW / TILE).toInt()
    private val tileRows = (worldH / TILE).toInt()

    fun generate() {
        generateLandmass()
        generateBiomes()
        setupPathGrid()
        spawnFactions()
        spawnResources()
        spawnDecor()
        state.rebuildSpatialIndices()
        invalidateNav()
    }

    private fun generateLandmass() {
        state.landCols = tileCols
        state.landRows = tileRows
        state.landMap = ByteArray(tileCols * tileRows)

        // Base: all water
        state.landMap.fill(0)

        // Faction base positions based on map preset
        val bases = getFactionBases()

        // Paint land around each base
        for (base in bases) {
            paintEllipse(base.first, base.second, 2800f + rng.nextFloat() * 600f, 2200f + rng.nextFloat() * 500f)
        }

        // Central landmass connector
        val cx = worldW / 2
        val cy = worldH / 2
        paintEllipse(cx, cy, 2400f + rng.nextFloat() * 800f, 1800f + rng.nextFloat() * 600f)

        // Connect bases to center with land bridges
        for (base in bases) {
            paintLine(base.first, base.second, cx, cy, 400f + rng.nextFloat() * 200f)
        }

        // Satellite islands
        val islands = (3 + rng.nextInt(4)) * state.resourceDensity.toInt().coerceAtLeast(1)
        repeat(islands) {
            val ix = 400f + rng.nextFloat() * (worldW - 800f)
            val iy = 400f + rng.nextFloat() * (worldH - 800f)
            paintEllipse(ix, iy, 600f + rng.nextFloat() * 400f, 500f + rng.nextFloat() * 300f)
        }

        // Smooth edges (2 passes cellular automata)
        repeat(2) { smoothLandmap() }
    }

    private fun paintEllipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        val startCol = ((cx - rx) / TILE).toInt().coerceAtLeast(0)
        val endCol = ((cx + rx) / TILE).toInt().coerceAtMost(tileCols - 1)
        val startRow = ((cy - ry) / TILE).toInt().coerceAtLeast(0)
        val endRow = ((cy + ry) / TILE).toInt().coerceAtMost(tileRows - 1)

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val tx = (col + 0.5f) * TILE
                val ty = (row + 0.5f) * TILE
                val dx = (tx - cx) / rx
                val dy = (ty - cy) / ry
                if (dx * dx + dy * dy <= 1f) {
                    state.landMap[row * tileCols + col] = 1
                }
            }
        }
    }

    private fun paintLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)
        val steps = (length / (TILE * 0.5f)).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val px = x1 + dx * t
            val py = y1 + dy * t
            paintEllipse(px, py, width, width * 0.8f)
        }
    }

    private fun smoothLandmap() {
        val newMap = state.landMap.copyOf()
        for (row in 1 until tileRows - 1) {
            for (col in 1 until tileCols - 1) {
                var neighbors = 0
                for (dr in -1..1) for (dc in -1..1) {
                    if (state.landMap[(row + dr) * tileCols + (col + dc)].toInt() == 1) neighbors++
                }
                newMap[row * tileCols + col] = if (neighbors >= 5) 1 else 0
            }
        }
        state.landMap = newMap
    }

    private fun generateBiomes() {
        state.biomeMap = IntArray(tileCols * tileRows)
        val bases = getFactionBases()
        val activeFactions = (1 + state.settings.rivals).coerceAtMost(5)

        for (row in 0 until tileRows) {
            for (col in 0 until tileCols) {
                if (state.landMap[row * tileCols + col].toInt() == 0) {
                    state.biomeMap[row * tileCols + col] = -1
                    continue
                }

                val tx = (col + 0.5f) * TILE
                val ty = (row + 0.5f) * TILE
                var closestFaction = 0
                var closestDist = Float.MAX_VALUE

                for (i in 0 until activeFactions) {
                    val bx = bases.getOrNull(i)?.first ?: (worldW / 2)
                    val by = bases.getOrNull(i)?.second ?: (worldH / 2)
                    val d = (tx - bx) * (tx - bx) + (ty - by) * (ty - by)
                    if (d < closestDist) {
                        closestDist = d
                        closestFaction = i
                    }
                }
                state.biomeMap[row * tileCols + col] = closestFaction
            }
        }
    }

    private fun setupPathGrid() {
        state.pathCols = (worldW / PATH_CELL).toInt()
        state.pathRows = (worldH / PATH_CELL).toInt()
        state.pathGrid = ByteArray(state.pathCols * state.pathRows)
        rebuildPathGrid()
    }

    fun rebuildPathGrid() {
        val pc = state.pathCols
        val pr = state.pathRows

        for (row in 0 until pr) {
            for (col in 0 until pc) {
                val wx = (col + 0.5f) * PATH_CELL
                val wy = (row + 0.5f) * PATH_CELL
                state.pathGrid[row * pc + col] = if (state.isWater(wx, wy)) 1 else 0
            }
        }

        // Mark buildings as blocked
        for (b in state.buildings) {
            if (b.dead) continue
            val def = BUILDINGS[b.type] ?: continue
            val pad = 10f
            val left = b.x - def.placeW / 2 - pad
            val right = b.x + def.placeW / 2 + pad
            val top = b.y - def.placeH / 2 - pad
            val bottom = b.y + def.placeH / 2 + pad

            val c0 = (left / PATH_CELL).toInt().coerceAtLeast(0)
            val c1 = (right / PATH_CELL).toInt().coerceAtMost(pc - 1)
            val r0 = (top / PATH_CELL).toInt().coerceAtLeast(0)
            val r1 = (bottom / PATH_CELL).toInt().coerceAtMost(pr - 1)
            for (r in r0..r1) for (c in c0..c1) {
                state.pathGrid[r * pc + c] = 1
            }
        }

        // Mark non-depleted static resources
        for (res in state.resources) {
            if (res.dead || res.depleted || res.isAnimal) continue
            val col = (res.x / PATH_CELL).toInt()
            val row = (res.y / PATH_CELL).toInt()
            if (col in 0 until pc && row in 0 until pr) {
                state.pathGrid[row * pc + col] = 1
            }
        }

        state.navVersion++
    }

    private fun invalidateNav() {
        rebuildPathGrid()
    }

    private fun spawnFactions() {
        val bases = getFactionBases()
        val activeFactions = (1 + state.settings.rivals).coerceAtMost(5)

        for (i in 0 until activeFactions) {
            val (bx, by) = bases[i]
            val f = state.factions[i]
            f.wood = 200f
            f.gold = 200f
            f.food = 10f
            f.alive = true

            if (i > 0) {
                f.aiState.attackTimer = state.difficulty.aiAttackDelay * (0.8f + rng.nextFloat() * 0.4f)
                f.aiState.economyBias = rng.nextFloat()
            }

            // Castle
            val castle = GameBuilding.create("castle", i, bx, by, state::nextId)
            state.buildings.add(castle)

            // Starting house
            val house = GameBuilding.create("house", i, bx + 120f, by - 60f, state::nextId)
            state.buildings.add(house)

            // Starting workers (3)
            repeat(3) { w ->
                val angle = (w.toFloat() / 3f) * 2f * PI.toFloat()
                val ux = bx + cos(angle) * 60f
                val uy = by + sin(angle) * 60f + 40f
                val worker = GameUnit.create("worker", i, ux, uy, state::nextId)
                worker.workerRole = when (w) {
                    0, 1 -> WorkerRole.WOOD
                    else -> WorkerRole.GOLD
                }
                state.units.add(worker)
            }

            // Starting warrior
            val warrior = GameUnit.create("warrior", i, bx - 40f, by + 50f, state::nextId)
            state.units.add(warrior)

            // Center camera on player base
            if (i == 0) {
                state.camera.x = bx
                state.camera.y = by
            }
        }

        // Mark dead factions that aren't in play
        for (i in activeFactions until 5) {
            state.factions[i].alive = false
        }
    }

    private fun spawnResources() {
        val bases = getFactionBases()
        val activeFactions = (1 + state.settings.rivals).coerceAtMost(5)
        val density = state.resourceDensity

        // Per-base resources
        for (i in 0 until activeFactions) {
            val (bx, by) = bases[i]

            // Trees around base (southwest bias)
            repeat((34 * density).toInt()) {
                val angle = PI.toFloat() + rng.nextFloat() * PI.toFloat() * 0.8f - 0.4f * PI.toFloat()
                val dist = 280f + rng.nextFloat() * 600f
                val rx = bx + cos(angle) * dist
                val ry = by + sin(angle) * dist
                if (rx > 100f && rx < worldW - 100f && ry > 100f && ry < worldH - 100f && state.isLand(rx, ry)) {
                    state.resources.add(GameResource.createTree(rx, ry, rng.nextInt(4), state::nextId))
                }
            }

            // Gold near base (northeast bias)
            repeat((12 * density).toInt()) {
                val angle = -PI.toFloat() * 0.25f + rng.nextFloat() * PI.toFloat() * 0.5f
                val dist = 300f + rng.nextFloat() * 500f
                val rx = bx + cos(angle) * dist
                val ry = by + sin(angle) * dist
                if (rx > 100f && rx < worldW - 100f && ry > 100f && ry < worldH - 100f && state.isLand(rx, ry)) {
                    state.resources.add(GameResource.createGold(rx, ry, rng.nextInt(6), state::nextId))
                }
            }

            // Animals near base (northwest bias)
            repeat((12 * density).toInt()) {
                val angle = PI.toFloat() * 0.75f + rng.nextFloat() * PI.toFloat() * 0.5f
                val dist = 350f + rng.nextFloat() * 500f
                val rx = bx + cos(angle) * dist
                val ry = by + sin(angle) * dist
                if (rx > 100f && rx < worldW - 100f && ry > 100f && ry < worldH - 100f && state.isLand(rx, ry)) {
                    val kinds = listOf("deer", "boar", "hare", "fox", "grouse", "sheep", "sheep")
                    state.resources.add(GameResource.createAnimal(kinds[rng.nextInt(kinds.size)], rx, ry, state::nextId))
                }
            }
        }

        // Neutral resource clusters
        val neutralClusters = (8 * density * (WORLD_PRESETS[state.settings.size]?.areaScale ?: 1f)).toInt()
        repeat(neutralClusters) {
            val cx = 600f + rng.nextFloat() * (worldW - 1200f)
            val cy = 600f + rng.nextFloat() * (worldH - 1200f)
            if (state.isLand(cx, cy)) {
                // Small tree cluster
                repeat(6 + rng.nextInt(8)) {
                    val ox = cx + (rng.nextFloat() - 0.5f) * 400f
                    val oy = cy + (rng.nextFloat() - 0.5f) * 300f
                    if (state.isLand(ox, oy)) {
                        state.resources.add(GameResource.createTree(ox, oy, rng.nextInt(4), state::nextId))
                    }
                }
                // Some gold
                repeat(2 + rng.nextInt(3)) {
                    val ox = cx + (rng.nextFloat() - 0.5f) * 300f
                    val oy = cy + (rng.nextFloat() - 0.5f) * 200f
                    if (state.isLand(ox, oy)) {
                        state.resources.add(GameResource.createGold(ox, oy, rng.nextInt(6), state::nextId))
                    }
                }
            }
        }
    }

    private fun spawnDecor() {
        val areaScale = WORLD_PRESETS[state.settings.size]?.areaScale ?: 1f

        // Bushes (passable)
        repeat((60 * areaScale).toInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isLand(x, y)) {
                state.decor.add(GameDecor.create("bush${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }

        // Rocks (solid)
        repeat((30 * areaScale).toInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isLand(x, y)) {
                state.decor.add(GameDecor.create("rock${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }

        // Clouds (sky layer)
        repeat((20 * areaScale).toInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            state.decor.add(GameDecor.create("cloud${1 + rng.nextInt(8)}", x, y, state::nextId))
        }

        // Water rocks
        repeat((20 * areaScale).toInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isWater(x, y)) {
                state.decor.add(GameDecor.create("waterRock${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }
    }

    fun getFactionBases(): List<Pair<Float, Float>> {
        val margin = 1200f
        val cx = worldW / 2
        val cy = worldH / 2

        return when (state.settings.mapStyle) {
            "archipelago" -> listOf(
                Pair(margin, margin),
                Pair(worldW - margin, margin),
                Pair(margin, worldH - margin),
                Pair(worldW - margin, worldH - margin),
                Pair(cx, cy)
            )
            "twinrivers" -> listOf(
                Pair(margin, cy),
                Pair(worldW - margin, cy),
                Pair(cx, margin),
                Pair(cx, worldH - margin),
                Pair(cx, cy)
            )
            "fourcorners" -> listOf(
                Pair(margin, margin),
                Pair(worldW - margin, margin),
                Pair(worldW - margin, worldH - margin),
                Pair(margin, worldH - margin),
                Pair(cx, cy)
            )
            "spiral" -> listOf(
                Pair(cx, margin),
                Pair(worldW - margin, cy),
                Pair(cx, worldH - margin),
                Pair(margin, cy),
                Pair(cx, cy)
            )
            else -> { // crossroads, kingroad, goldrush, highlands
                listOf(
                    Pair(margin, worldH - margin),
                    Pair(worldW - margin, margin),
                    Pair(margin, margin),
                    Pair(worldW - margin, worldH - margin),
                    Pair(cx, cy)
                )
            }
        }
    }
}
