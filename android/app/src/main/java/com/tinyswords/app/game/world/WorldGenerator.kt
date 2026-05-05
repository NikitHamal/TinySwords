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

        state.landMap.fill(0)

        val bases = getFactionBases()
        val areaScale = WORLD_PRESETS[state.settings.size]?.areaScale ?: 1f
        val landScale = sqrt(areaScale).coerceAtLeast(1f)

        for (base in bases) {
            paintEllipse(
                base.first,
                base.second,
                (2850f + rng.nextFloat() * 700f) * landScale,
                (2250f + rng.nextFloat() * 580f) * landScale
            )
        }

        val cx = worldW / 2f
        val cy = worldH / 2f
        paintEllipse(cx, cy, (2550f + rng.nextFloat() * 900f) * landScale, (1950f + rng.nextFloat() * 650f) * landScale)

        for (base in bases) {
            paintLine(base.first, base.second, cx, cy, (430f + rng.nextFloat() * 230f) * landScale)
        }

        // Rich/abundant used to be truncated with toInt(), so they produced the same number of islands.
        // Scale islands with density and map area so larger/richer worlds actually feel larger/richer.
        val islands = ((4 + rng.nextInt(5)) * state.resourceDensity * landScale).roundToInt().coerceAtLeast(5)
        repeat(islands) {
            val margin = (500f * landScale).coerceAtMost(min(worldW, worldH) * 0.18f)
            val ix = margin + rng.nextFloat() * (worldW - margin * 2f)
            val iy = margin + rng.nextFloat() * (worldH - margin * 2f)
            paintEllipse(ix, iy, (650f + rng.nextFloat() * 520f) * landScale, (520f + rng.nextFloat() * 390f) * landScale)
        }

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
                    val bx = bases.getOrNull(i)?.first ?: (worldW / 2f)
                    val by = bases.getOrNull(i)?.second ?: (worldH / 2f)
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

        for (b in state.buildings) {
            if (b.dead) continue
            val def = BUILDINGS[b.type] ?: continue
            val pad = 10f
            val left = b.x - def.placeW / 2f - pad
            val right = b.x + def.placeW / 2f + pad
            val top = b.y - def.placeH / 2f - pad
            val bottom = b.y + def.placeH / 2f + pad

            val c0 = (left / PATH_CELL).toInt().coerceAtLeast(0)
            val c1 = (right / PATH_CELL).toInt().coerceAtMost(pc - 1)
            val r0 = (top / PATH_CELL).toInt().coerceAtLeast(0)
            val r1 = (bottom / PATH_CELL).toInt().coerceAtMost(pr - 1)
            for (r in r0..r1) for (c in c0..c1) {
                state.pathGrid[r * pc + c] = 1
            }
        }

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

    private fun nearestSafeLandPoint(x: Float, y: Float, maxRadius: Float = 260f, probeRadius: Float = 30f): Pair<Float, Float>? {
        if (state.isSafeLand(x, y, probeRadius)) return Pair(x, y)
        var r = 32f
        while (r <= maxRadius) {
            val steps = max(10, (r / 18f).roundToInt())
            for (i in 0 until steps) {
                val a = (i.toFloat() / steps) * 2f * PI.toFloat()
                val px = x + cos(a) * r
                val py = y + sin(a) * r
                if (state.isSafeLand(px, py, probeRadius)) return Pair(px, py)
            }
            r += 32f
        }
        return null
    }

    private fun randomLandPoint(margin: Float = 420f, attempts: Int = 700, probeRadius: Float = 34f): Pair<Float, Float>? {
        val safeMargin = margin.coerceAtMost(min(worldW, worldH) * 0.22f)
        repeat(attempts) {
            val x = safeMargin + rng.nextFloat() * (worldW - safeMargin * 2f)
            val y = safeMargin + rng.nextFloat() * (worldH - safeMargin * 2f)
            if (state.isSafeLand(x, y, probeRadius)) return Pair(x, y)
        }
        return null
    }

    private fun spawnFactions() {
        val bases = getFactionBases()
        val activeFactions = (1 + state.settings.rivals).coerceAtMost(5)

        fun addBuiltBuilding(type: String, factionId: Int, x: Float, y: Float) {
            val def = BUILDINGS[type] ?: return
            val p = nearestSafeLandPoint(x, y, 320f, max(def.placeW, def.placeH) * 0.28f) ?: Pair(x, y)
            state.buildings.add(GameBuilding.create(type, factionId, p.first, p.second, state::nextId))
        }

        for (i in 0 until activeFactions) {
            val (bx, by) = bases[i]
            val f = state.factions[i]
            val aiMult = if (i == 0) 1f else state.difficulty.aiResourceMult
            f.wood = if (i == 0) 420f else 500f * aiMult
            f.gold = if (i == 0) 340f else 430f * aiMult
            f.food = if (i == 0) 12f else 14f * aiMult
            f.alive = true

            if (i > 0) {
                f.aiState.attackTimer = state.difficulty.aiAttackDelay * (0.8f + rng.nextFloat() * 0.4f)
                f.aiState.economyBias = rng.nextFloat()
            }

            addBuiltBuilding("castle", i, bx, by)
            addBuiltBuilding("house", i, bx - 210f, by + 84f)
            addBuiltBuilding("house", i, bx + 205f, by + 92f)
            addBuiltBuilding("barracks", i, bx - 160f, by - 190f)
            addBuiltBuilding("tower", i, bx + 160f, by + 200f)
            if (i > 0) addBuiltBuilding("archery", i, bx + 185f, by - 176f)

            val workerCount = if (i == 0) 5 else 6
            repeat(workerCount) { w ->
                val angle = (w.toFloat() / workerCount) * 2f * PI.toFloat()
                val p = nearestSafeLandPoint(
                    bx + cos(angle) * (95f + rng.nextFloat() * 50f),
                    by + 170f + rng.nextFloat() * 115f,
                    300f,
                    18f
                ) ?: Pair(bx + cos(angle) * 105f, by + 190f)
                val worker = GameUnit.create("worker", i, p.first, p.second, state::nextId)
                worker.workerRole = when (w) {
                    0, 1, 2 -> WorkerRole.WOOD
                    3 -> WorkerRole.GOLD
                    else -> WorkerRole.FOOD
                }
                state.units.add(worker)
            }

            val military = if (i == 0) {
                listOf("archer", "warrior", "warrior")
            } else {
                listOf("archer", "warrior", "warrior", "archer", "warrior")
            }
            military.forEachIndexed { m, type ->
                val p = nearestSafeLandPoint(
                    bx + 130f + rng.nextFloat() * 135f,
                    by - 130f + m * 48f + (rng.nextFloat() - 0.5f) * 34f,
                    300f,
                    20f
                ) ?: Pair(bx + 150f + m * 22f, by - 80f + m * 42f)
                state.units.add(GameUnit.create(type, i, p.first, p.second, state::nextId))
            }

            if (i == 0) {
                state.camera.x = bx
                state.camera.y = by
            }
        }

        for (i in activeFactions until 5) {
            state.factions[i].alive = false
        }
    }

    private fun spawnResources() {
        val bases = getFactionBases()
        val activeFactions = (1 + state.settings.rivals).coerceAtMost(5)
        val density = state.resourceDensity
        val areaScale = WORLD_PRESETS[state.settings.size]?.areaScale ?: 1f
        val graphicsScale = when (state.settings.safeGraphics()) {
            "performance" -> 0.82f
            "high" -> 1.12f
            else -> 1f
        }
        val activeBases = bases.take(activeFactions)
        val animalKinds = listOf("deer", "boar", "hare", "fox", "grouse", "sheep", "sheep")

        fun tooCloseToBase(x: Float, y: Float, minDist: Float): Boolean {
            val minD2 = minDist * minDist
            return activeBases.any { base ->
                val dx = x - base.first
                val dy = y - base.second
                dx * dx + dy * dy < minD2
            }
        }

        fun tooCloseToResource(x: Float, y: Float, minDist: Float): Boolean {
            val minD2 = minDist * minDist
            return state.resources.any { res ->
                if (res.dead || res.depleted) return@any false
                val dx = x - res.x
                val dy = y - res.y
                dx * dx + dy * dy < minD2
            }
        }

        fun addResource(type: ResourceType, x: Float, y: Float): Boolean {
            if (!state.isSafeLand(x, y, 22f)) return false
            val spacing = when (type) {
                ResourceType.TREE -> 42f
                ResourceType.GOLD -> 54f
                ResourceType.FOOD -> 50f
            }
            if (tooCloseToResource(x, y, spacing)) return false
            val resource = when (type) {
                ResourceType.TREE -> GameResource.createTree(x, y, rng.nextInt(4), state::nextId)
                ResourceType.GOLD -> GameResource.createGold(x, y, rng.nextInt(6), state::nextId)
                ResourceType.FOOD -> GameResource.createAnimal(animalKinds[rng.nextInt(animalKinds.size)], x, y, state::nextId)
            }
            state.resources.add(resource)
            return true
        }

        fun addCluster(type: ResourceType, cx: Float, cy: Float, count: Int, spreadX: Float, spreadY: Float) {
            var placed = 0
            val attempts = count * 8
            repeat(attempts) {
                if (placed >= count) return@repeat
                val ox = cx + (rng.nextFloat() - 0.5f) * spreadX
                val oy = cy + (rng.nextFloat() - 0.5f) * spreadY
                if (addResource(type, ox, oy)) placed++
            }
        }

        // Base-adjacent resources keep the opening fair, but counts now scale visibly with density.
        for (i in 0 until activeFactions) {
            val (bx, by) = bases[i]
            val treeCount = (46f * density).roundToInt()
            val goldCount = (17f * density).roundToInt()
            val foodCount = (16f * density).roundToInt()

            repeat(treeCount) {
                val angle = PI.toFloat() + rng.nextFloat() * PI.toFloat() * 0.95f - 0.48f * PI.toFloat()
                val dist = 300f + rng.nextFloat() * 780f
                addResource(ResourceType.TREE, bx + cos(angle) * dist, by + sin(angle) * dist)
            }
            repeat(goldCount) {
                val angle = -PI.toFloat() * 0.3f + rng.nextFloat() * PI.toFloat() * 0.62f
                val dist = 340f + rng.nextFloat() * 690f
                addResource(ResourceType.GOLD, bx + cos(angle) * dist, by + sin(angle) * dist)
            }
            repeat(foodCount) {
                val angle = PI.toFloat() * 0.68f + rng.nextFloat() * PI.toFloat() * 0.68f
                val dist = 390f + rng.nextFloat() * 760f
                addResource(ResourceType.FOOD, bx + cos(angle) * dist, by + sin(angle) * dist)
            }
        }

        // Neutral deposits were previously too few and only created trees/gold. Fill the whole landmass.
        val neutralClusters = (52f * areaScale * density * graphicsScale).roundToInt().coerceAtLeast((24f * density).roundToInt())
        repeat(neutralClusters) { c ->
            val center = randomLandPoint(480f, 900, 34f) ?: return@repeat
            if (tooCloseToBase(center.first, center.second, 620f)) return@repeat
            val roll = rngHash(c, center.first.toInt(), center.second.toInt())
            when {
                roll < 0.58 -> addCluster(
                    ResourceType.TREE,
                    center.first,
                    center.second,
                    (9 + rng.nextInt(8) + density * 3f).roundToInt(),
                    460f,
                    340f
                )
                roll < 0.82 -> addCluster(
                    ResourceType.GOLD,
                    center.first,
                    center.second,
                    (4 + rng.nextInt(5) + density * 1.4f).roundToInt(),
                    360f,
                    280f
                )
                else -> addCluster(
                    ResourceType.FOOD,
                    center.first,
                    center.second,
                    (5 + rng.nextInt(5) + density * 1.8f).roundToInt(),
                    420f,
                    320f
                )
            }
        }

        // A light scatter pass makes exploration rewarding instead of leaving huge barren stretches.
        val scatter = (42f * areaScale * density * graphicsScale).roundToInt()
        repeat(scatter) { i ->
            val point = randomLandPoint(360f, 520, 28f) ?: return@repeat
            if (tooCloseToBase(point.first, point.second, 520f)) return@repeat
            val type = when (i % 7) {
                0 -> ResourceType.GOLD
                1, 2 -> ResourceType.FOOD
                else -> ResourceType.TREE
            }
            addResource(type, point.first, point.second)
        }
    }

    private fun spawnDecor() {
        val areaScale = WORLD_PRESETS[state.settings.size]?.areaScale ?: 1f
        val graphicsScale = when (state.settings.safeGraphics()) {
            "performance" -> 0.7f
            "high" -> 1.25f
            else -> 1f
        }

        repeat((80f * areaScale * graphicsScale).roundToInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isLand(x, y)) {
                state.decor.add(GameDecor.create("bush${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }

        repeat((38f * areaScale * graphicsScale).roundToInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isLand(x, y)) {
                state.decor.add(GameDecor.create("rock${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }

        repeat((20f * areaScale * graphicsScale).roundToInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            state.decor.add(GameDecor.create("cloud${1 + rng.nextInt(8)}", x, y, state::nextId))
        }

        repeat((20f * areaScale * graphicsScale).roundToInt()) {
            val x = rng.nextFloat() * worldW
            val y = rng.nextFloat() * worldH
            if (state.isWater(x, y)) {
                state.decor.add(GameDecor.create("waterRock${1 + rng.nextInt(4)}", x, y, state::nextId))
            }
        }
    }

    fun getFactionBases(): List<Pair<Float, Float>> {
        val ratios = when (state.settings.mapStyle) {
            "archipelago" -> listOf(
                Pair(0.22f, 0.2f),
                Pair(0.78f, 0.22f),
                Pair(0.22f, 0.78f),
                Pair(0.78f, 0.78f),
                Pair(0.5f, 0.5f)
            )
            "twinrivers" -> listOf(
                Pair(0.12f, 0.52f),
                Pair(0.88f, 0.48f),
                Pair(0.5f, 0.16f),
                Pair(0.5f, 0.84f),
                Pair(0.5f, 0.5f)
            )
            "fourcorners" -> listOf(
                Pair(0.135f, 0.155f),
                Pair(0.865f, 0.155f),
                Pair(0.865f, 0.845f),
                Pair(0.135f, 0.845f),
                Pair(0.5f, 0.5f)
            )
            "kingroad" -> listOf(
                Pair(0.5f, 0.14f),
                Pair(0.5f, 0.86f),
                Pair(0.18f, 0.5f),
                Pair(0.82f, 0.5f),
                Pair(0.5f, 0.5f)
            )
            "spiral" -> listOf(
                Pair(0.5f, 0.14f),
                Pair(0.84f, 0.36f),
                Pair(0.68f, 0.84f),
                Pair(0.22f, 0.68f),
                Pair(0.5f, 0.5f)
            )
            "goldrush" -> listOf(
                Pair(0.16f, 0.18f),
                Pair(0.84f, 0.18f),
                Pair(0.18f, 0.82f),
                Pair(0.82f, 0.82f),
                Pair(0.5f, 0.5f)
            )
            "highlands" -> listOf(
                Pair(0.18f, 0.18f),
                Pair(0.82f, 0.22f),
                Pair(0.24f, 0.78f),
                Pair(0.78f, 0.76f),
                Pair(0.5f, 0.5f)
            )
            else -> listOf(
                Pair(0.135f, 0.155f),
                Pair(0.865f, 0.155f),
                Pair(0.135f, 0.845f),
                Pair(0.865f, 0.845f),
                Pair(0.5f, 0.5f)
            )
        }
        val margin = min(900f, min(worldW, worldH) * 0.12f)
        return ratios.map { ratio ->
            Pair(
                (worldW * ratio.first).coerceIn(margin, worldW - margin),
                (worldH * ratio.second).coerceIn(margin, worldH - margin)
            )
        }
    }
}
