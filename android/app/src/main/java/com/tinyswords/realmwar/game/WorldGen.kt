package com.tinyswords.realmwar.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates the terrain land mask, faction starts, scattered resources, hunting animals, and
 * decorative pixel-art props. Produces a [GameWorld] ready to be ticked by the simulation.
 */
class WorldGen(
    private val settings: WorldSettings,
    private val rng: SeededRandom
) {
    fun generate(): GameWorld {
        val sizeDef = settings.size
        val world = GameWorld(
            width = sizeDef.width,
            height = sizeDef.height,
            tile = GameConfig.TILE,
            settings = settings
        )
        IdGen.reset()
        generateTerrain(world)
        spawnFactionsAndStartingUnits(world)
        scatterResources(world)
        spawnWildlife(world)
        scatterDecor(world)
        return world
    }

    private fun generateTerrain(w: GameWorld) {
        val cols = w.cols
        val rows = w.rows
        val land = w.landMap
        val center = floatArrayOf(w.width * 0.5f, w.height * 0.5f)

        fun setLand(tx: Int, ty: Int) {
            if (tx in 0 until cols && ty in 0 until rows) land[ty * cols + tx] = 1
        }

        fun paintEllipse(cx: Float, cy: Float, rx: Float, ry: Float, wobble: Float = .07f) {
            val tile = GameConfig.TILE
            val minX = ((cx - rx) / tile).toInt() - 2
            val maxX = ((cx + rx) / tile).toInt() + 2
            val minY = ((cy - ry) / tile).toInt() - 2
            val maxY = ((cy + ry) / tile).toInt() + 2
            for (ty in minY..maxY) for (tx in minX..maxX) {
                val x = tx * tile + tile * 0.5f
                val y = ty * tile + tile * 0.5f
                val n = (rng.nextFloat01() - 0.5f) * wobble
                val d = ((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it }
                if (d < 1f + n) setLand(tx, ty)
            }
        }

        // Paint base lands
        for (i in GameConfig.FACTIONS.indices) {
            val faction = GameConfig.FACTIONS[i]
            val basesNorm = settings.mapStyle.basesNorm[i]
            val bx = w.width * basesNorm[0]
            val by = w.height * basesNorm[1]
            paintEllipse(bx, by + 24f, 1080f, 760f)
            val sideX = if (bx < center[0]) 1f else -1f
            val sideY = if (by < center[1]) 1f else -1f
            paintEllipse(bx + sideX * 470f, by + sideY * 410f, 650f, 440f)
        }
        paintEllipse(center[0], center[1], 1500f, 1080f)
        paintEllipse(center[0] - 1600f, center[1] - 360f, 760f, 500f)
        paintEllipse(center[0] + 1600f, center[1] + 360f, 780f, 520f)
        paintEllipse(center[0] - 1600f, center[1] + 360f, 760f, 500f)
        paintEllipse(center[0] + 1600f, center[1] - 360f, 780f, 520f)

        // Connect bases through the centre with gentle corridors
        for (i in GameConfig.FACTIONS.indices) {
            val basesNorm = settings.mapStyle.basesNorm[i]
            val bx = w.width * basesNorm[0]
            val by = w.height * basesNorm[1]
            paintCorridor(w, bx, by, center[0], center[1])
        }
    }

    private fun paintCorridor(w: GameWorld, ax: Float, ay: Float, bx: Float, by: Float) {
        val len = hypot(ax - bx, ay - by)
        val steps = (len / 40f).toInt().coerceAtLeast(1)
        val tile = GameConfig.TILE
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = ax + (bx - ax) * t
            val y = ay + (by - ay) * t
            val r = 220f
            val minX = ((x - r) / tile).toInt() - 1
            val maxX = ((x + r) / tile).toInt() + 1
            val minY = ((y - r) / tile).toInt() - 1
            val maxY = ((y + r) / tile).toInt() + 1
            for (ty in minY..maxY) for (tx in minX..maxX) {
                if (tx !in 0 until w.cols || ty !in 0 until w.rows) continue
                val px = tx * tile + tile / 2f
                val py = ty * tile + tile / 2f
                val d = hypot(px - x, py - y)
                if (d < r * (0.85f + rng.nextFloat01() * 0.18f)) w.landMap[ty * w.cols + tx] = 1
            }
        }
    }

    private fun spawnFactionsAndStartingUnits(world: GameWorld) {
        val rivals = settings.rivals
        for (i in GameConfig.FACTIONS.indices) {
            val def = GameConfig.FACTIONS[i]
            val isAi = def.id != 0 && def.id <= rivals
            val basesNorm = settings.mapStyle.basesNorm[i]
            val bx = world.width * basesNorm[0]
            val by = world.height * basesNorm[1]
            val mult = if (isAi) settings.difficulty.aiResourceMult else 1f
            val faction = Faction(
                def = def,
                isAi = isAi,
                resWood = (if (isAi) 520 else 500) * 1.coerceAtLeast(1) * 1, // baseline; cast w/ mult below
                resGold = if (isAi) (430 * mult).toInt() else 390,
                resFood = if (isAi) (12 * mult).toInt() else 10
            )
            faction.resWood = if (isAi) (520 * mult).toInt() else 500
            faction.basePos[0] = bx
            faction.basePos[1] = by
            faction.aiState.attackTimer = settings.difficulty.aiAttackDelay + rng.nextFloat01() * 10f
            faction.aiState.economyBias = rng.nextFloat01()
            faction.aiState.rallyAngle = rng.nextFloat01() * (Math.PI * 2).toFloat()
            world.factions.add(faction)

            if (def.id == 0 || isAi) {
                spawnStartingBuildings(world, faction, bx, by)
                spawnStartingUnits(world, faction, bx, by)
            } else {
                faction.alive = false
            }
        }
    }

    private fun spawnStartingBuildings(world: GameWorld, faction: Faction, bx: Float, by: Float) {
        val castleDef = GameConfig.BUILDINGS["castle"]!!
        val castle = Building("castle", castleDef, faction.id, bx, by)
        castle.built = true
        castle.buildProgress = 1f
        world.buildings.add(castle)

        val houseDef = GameConfig.BUILDINGS["house"]!!
        for (k in 0 until 2) {
            val angle = (k * 1.5f - 0.5f).toFloat()
            val hx = bx + cos(angle) * 220f
            val hy = by + sin(angle) * 200f
            val house = Building("house", houseDef, faction.id, hx, hy)
            world.buildings.add(house)
        }
    }

    private fun spawnStartingUnits(world: GameWorld, faction: Faction, bx: Float, by: Float) {
        val workerDef = GameConfig.UNITS["worker"]!!
        for (i in 0 until 4) {
            val a = i * 1.55f
            val u = Unit("worker", workerDef, faction.id, bx + cos(a) * 70f, by + sin(a) * 90f + 100f)
            world.units.add(u)
        }
        if (faction.id == 0) {
            // Player starts with one warrior so it feels alive immediately
            val warriorDef = GameConfig.UNITS["warrior"]!!
            world.units.add(Unit("warrior", warriorDef, faction.id, bx + 80f, by + 60f))
        }
    }

    private fun scatterResources(world: GameWorld) {
        val densityScale = settings.density.mult * settings.graphics.densityMult
        val worldArea = world.width.toFloat() * world.height
        val scale = worldArea / (8200f * 6000f) * densityScale

        for (faction in world.factions) {
            val bx = faction.basePos[0]
            val by = faction.basePos[1]
            spawnRing(world, ResourceType.TREE, bx - 180f, by + 340f, 30, 280f, 820f)
            spawnRing(world, ResourceType.GOLD, bx + 310f, by - 230f, 10, 320f, 760f)
            spawnRing(world, ResourceType.FOOD, bx - 430f, by - 150f, 10, 300f, 740f)
        }

        val neutralClusters = (52 * scale).toInt()
        repeat(neutralClusters) {
            val px = rng.nextFloat01() * world.width
            val py = rng.nextFloat01() * world.height
            if (!world.isLand(px, py)) return@repeat
            if (occupiedByBase(world, px, py, 540f)) return@repeat
            val roll = rng.nextFloat01()
            val type = when {
                roll < 0.58f -> ResourceType.TREE
                roll < 0.80f -> ResourceType.GOLD
                else -> ResourceType.FOOD
            }
            val count = when (type) {
                ResourceType.TREE -> 8 + rng.nextInt(14)
                ResourceType.GOLD -> 3 + rng.nextInt(6)
                ResourceType.FOOD -> 4 + rng.nextInt(7)
            }
            val spread = when (type) {
                ResourceType.TREE -> 280f
                ResourceType.GOLD -> 180f
                ResourceType.FOOD -> 220f
            }
            for (i in 0 until count) {
                val a = rng.nextFloat01() * (Math.PI * 2).toFloat()
                val r = rng.nextFloat01() * spread
                val rx = px + cos(a) * r
                val ry = py + sin(a) * r
                if (!world.isLand(rx, ry)) continue
                if (tooCloseResource(world, rx, ry, 50f)) continue
                if (occupiedByBase(world, rx, ry, 240f)) continue
                addResource(world, type, rx, ry)
            }
        }
    }

    private fun spawnRing(world: GameWorld, type: ResourceType, cx: Float, cy: Float, count: Int, minR: Float, maxR: Float) {
        var made = 0
        var attempts = 0
        while (made < count && attempts < count * 10) {
            attempts++
            val a = rng.nextFloat01() * (Math.PI * 2).toFloat()
            val r = minR + rng.nextFloat01() * (maxR - minR)
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (!world.isLand(x, y)) continue
            if (tooCloseResource(world, x, y, 50f)) continue
            if (occupiedByBase(world, x, y, 200f)) continue
            addResource(world, type, x, y)
            made++
        }
    }

    private fun addResource(world: GameWorld, type: ResourceType, x: Float, y: Float) {
        val amount = when (type) {
            ResourceType.TREE -> 80
            ResourceType.GOLD -> 200
            ResourceType.FOOD -> 60
        }
        world.resources.add(Resource(type, x, y, amount))
    }

    private fun spawnWildlife(world: GameWorld) {
        val targetCount = ((world.width * world.height) / 720000f * settings.density.mult).toInt()
        var attempts = 0
        while (world.resources.count { it.isAnimal } < targetCount && attempts < targetCount * 6) {
            attempts++
            val px = rng.nextFloat01() * world.width
            val py = rng.nextFloat01() * world.height
            if (!world.isLand(px, py)) continue
            if (occupiedByBase(world, px, py, 320f)) continue
            if (tooCloseResource(world, px, py, 80f)) continue
            val def = pickAnimal()
            val animal = HuntAnimalState(def)
            val res = Resource(ResourceType.FOOD, px, py, def.foodYield, animal = animal)
            world.resources.add(res)
        }
    }

    private fun pickAnimal(): GameConfig.HuntAnimalDef {
        val totalWeight = GameConfig.HUNT_ANIMALS.sumOf { it.weight.toDouble() }.toFloat()
        var roll = rng.nextFloat01() * totalWeight
        for (def in GameConfig.HUNT_ANIMALS) {
            roll -= def.weight
            if (roll <= 0) return def
        }
        return GameConfig.HUNT_ANIMALS.last()
    }

    private fun scatterDecor(world: GameWorld) {
        val kinds = listOf("bush1", "bush2", "bush3", "bush4", "rock1", "rock2", "rock3", "rock4")
        val count = ((world.width * world.height) / 380000f * settings.density.mult).toInt()
        repeat(count) {
            val px = rng.nextFloat01() * world.width
            val py = rng.nextFloat01() * world.height
            if (!world.isLand(px, py)) return@repeat
            if (occupiedByBase(world, px, py, 340f)) return@repeat
            if (tooCloseResource(world, px, py, 38f)) return@repeat
            val k = kinds[rng.nextInt(kinds.size)]
            world.decor.add(Decor(k, px, py, 0.55f + rng.nextFloat01() * 0.30f))
        }
    }

    private fun occupiedByBase(world: GameWorld, x: Float, y: Float, radius: Float): Boolean {
        for (b in world.buildings) {
            if (hypot(b.x - x, b.y - y) < radius) return true
        }
        for (f in world.factions) {
            if (hypot(f.basePos[0] - x, f.basePos[1] - y) < radius) return true
        }
        return false
    }

    private fun tooCloseResource(world: GameWorld, x: Float, y: Float, radius: Float): Boolean {
        // Very simple linear scan; acceptable for generation time
        for (r in world.resources) {
            if (hypot(r.x - x, r.y - y) < radius) return true
        }
        return false
    }
}

/**
 * The actual world object holding the entity collections and tile data.
 */
class GameWorld(
    val width: Int,
    val height: Int,
    val tile: Int,
    val settings: WorldSettings
) {
    val cols: Int = (width + tile - 1) / tile
    val rows: Int = (height + tile - 1) / tile
    val landMap: ByteArray = ByteArray(cols * rows)
    val groundVariant: ByteArray = ByteArray(cols * rows)
    val factions: MutableList<Faction> = mutableListOf()
    val units: MutableList<Unit> = mutableListOf()
    val buildings: MutableList<Building> = mutableListOf()
    val resources: MutableList<Resource> = mutableListOf()
    val decor: MutableList<Decor> = mutableListOf()
    val projectiles: MutableList<Projectile> = mutableListOf()
    val effects: MutableList<FxEffect> = mutableListOf()

    var elapsedTime: Float = 0f

    fun isLand(x: Float, y: Float): Boolean {
        val tx = (x / tile).toInt()
        val ty = (y / tile).toInt()
        if (tx !in 0 until cols || ty !in 0 until rows) return false
        return landMap[ty * cols + tx].toInt() != 0
    }

    fun playerFaction(): Faction = factions[0]

    fun aliveFactions(): List<Faction> = factions.filter { it.alive && (it.def.id == 0 || it.isAi) }
}

/**
 * High level world settings selected on the create-world screen.
 */
data class WorldSettings(
    val name: String,
    val seed: String,
    val size: GameConfig.WorldSize,
    val mapStyle: GameConfig.MapStyle,
    val difficulty: GameConfig.Difficulty,
    val density: GameConfig.ResourceDensity,
    val graphics: GameConfig.GraphicsQuality,
    val rivals: Int,
    val autosave: Boolean
)
