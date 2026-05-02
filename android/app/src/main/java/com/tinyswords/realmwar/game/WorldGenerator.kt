package com.tinyswords.realmwar.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Procedural world generator: paints the land/water tiles, drops resource
 * clusters around faction bases, scatters neutral wood/gold/food, and seeds
 * decorative bushes and rocks. Faithful port of the web build's
 * `world.js#generateWorld()` adapted for mobile-friendly densities.
 */
class WorldGenerator(private val state: GameState) {
    private val rng: Random = state.rng
    private val cols get() = state.landCols
    private val rows get() = state.landRows

    fun generate() {
        state.units.clear()
        state.buildings.clear()
        state.resources.clear()
        state.decor.clear()
        state.projectiles.clear()
        state.effects.clear()

        buildFactions()
        carveTerrain()
        scatterResources()
        scatterDecor()
        spawnFactions()
    }

    // ----- Factions ---------------------------------------------------------

    private fun buildFactions() {
        state.factions.clear()
        val rivals = state.settings.rivals.coerceIn(0, 4)
        val bases = state.settings.mapStyle.bases
        FACTIONS.forEachIndexed { i, def ->
            val baseFrac = bases[i]
            val base = Vec2(state.worldW * baseFrac.first, state.worldH * baseFrac.second)
            val isAi = i != 0 && i <= rivals
            val mult = if (isAi) state.settings.difficulty.aiResourceMult else 1f
            val res = if (isAi)
                Stockpile((520 * mult).toInt(), (430 * mult).toInt(), (12 * mult).toInt())
            else
                Stockpile(500, 390, 10)
            val faction = Faction(
                def = def, ai = isAi, res = res, basePos = base,
                alive = isAi || i == 0
            )
            faction.popCap = Pop.starting()
            faction.ai_state.attackTimer = state.settings.difficulty.aiAttackDelay + rng.nextFloat() * 10f
            faction.ai_state.economyBias = rng.nextFloat()
            faction.ai_state.rallyAngle = rng.nextFloat() * (Math.PI * 2).toFloat()
            state.factions += faction
        }
    }

    // ----- Terrain ----------------------------------------------------------

    private fun carveTerrain() {
        java.util.Arrays.fill(state.landMap, 0)
        val centerX = state.worldW / 2f
        val centerY = state.worldH / 2f

        for (faction in state.factions) {
            paintEllipse(faction.basePos.x, faction.basePos.y + 24f, 1080f, 760f, 1)
            val sx = if (faction.basePos.x < centerX) 1 else -1
            val sy = if (faction.basePos.y < centerY) 1 else -1
            paintEllipse(faction.basePos.x + sx * 470f, faction.basePos.y + sy * 410f, 650f, 440f, 1)
        }

        paintEllipse(centerX, centerY, 1500f, 1080f, 1)
        for (offset in listOf(-1f, 1f)) {
            paintEllipse(centerX + 1600f * offset, centerY + 360f * offset, 760f, 500f, 1)
            paintEllipse(centerX - 1600f * offset, centerY + 360f * offset, 760f, 500f, 1)
        }

        for (faction in state.factions) {
            paintLine(faction.basePos.x, faction.basePos.y, centerX, centerY, 220f)
        }

        // Smooth a couple of times
        repeat(2) {
            val src = state.landMap.copyOf()
            for (ty in 1 until rows - 1) for (tx in 1 until cols - 1) {
                val idx = ty * cols + tx
                var n = 0
                for (oy in -1..1) for (ox in -1..1)
                    if (src[(ty + oy) * cols + tx + ox].toInt() != 0) n++
                if (src[idx].toInt() == 1 && n <= 3) state.landMap[idx] = 0
                if (src[idx].toInt() == 0 && n >= 7) state.landMap[idx] = 1
            }
        }

        // Reinforce base areas + connecting roads
        for (faction in state.factions) {
            paintEllipse(faction.basePos.x, faction.basePos.y + 20f, 1120f, 790f, 1)
            paintLine(faction.basePos.x, faction.basePos.y, centerX, centerY, 190f)
        }
        paintEllipse(centerX, centerY, 1370f, 970f, 1)

        // Variant / wet-edge map for later rendering
        for (ty in 0 until rows) for (tx in 0 until cols) {
            val idx = ty * cols + tx
            val land = state.landMap[idx].toInt() == 1
            val wetEdge = land && (
                !landAt(tx, ty - 1) || !landAt(tx, ty + 1) ||
                !landAt(tx - 1, ty) || !landAt(tx + 1, ty)
            )
            val noise = (rng.nextInt(24)) + (if (wetEdge) 40 else 0)
            state.groundVariant[idx] = noise.toByte()
        }
    }

    private fun landAt(tx: Int, ty: Int): Boolean {
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) return false
        return state.landMap[ty * cols + tx].toInt() == 1
    }

    private fun paintEllipse(cx: Float, cy: Float, rx: Float, ry: Float, value: Int) {
        val minX = ((cx - rx) / TILE_PX).toInt() - 2
        val maxX = ((cx + rx) / TILE_PX).toInt() + 2
        val minY = ((cy - ry) / TILE_PX).toInt() - 2
        val maxY = ((cy + ry) / TILE_PX).toInt() + 2
        for (ty in minY..maxY) for (tx in minX..maxX) {
            if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) continue
            val px = tx * TILE_PX + TILE_PX / 2f
            val py = ty * TILE_PX + TILE_PX / 2f
            val dx = (px - cx) / rx
            val dy = (py - cy) / ry
            val noise = (rng.nextFloat() - 0.5f) * 0.06f
            if (dx * dx + dy * dy < 1f + noise) state.landMap[ty * cols + tx] = value.toByte()
        }
    }

    private fun paintLine(ax: Float, ay: Float, bx: Float, by: Float, width: Float) {
        val len = hypot(ax - bx, ay - by)
        val steps = (len / (TILE_PX * 0.32f)).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = ax + (bx - ax) * t
            val y = ay + (by - ay) * t
            paintEllipse(x, y, width, width * 0.74f, 1)
        }
    }

    // ----- Resources --------------------------------------------------------

    private fun scatterResources() {
        val densityScale = state.settings.resourceDensity.multiplier *
            state.settings.graphics.densityMult
        val worldScale = (state.worldW * state.worldH).toFloat() / (8200f * 6000f) * densityScale

        // Per-base resource rings
        for (faction in state.factions) {
            ring("tree", faction.basePos.x - 180f, faction.basePos.y + 340f, 26, 300f, 850f)
            ring("gold", faction.basePos.x + 310f, faction.basePos.y - 230f, 10, 320f, 780f)
            ring("food", faction.basePos.x - 430f, faction.basePos.y - 150f, 10, 300f, 760f)
        }

        // Neutral clusters across the map
        val clusterCount = (40 * worldScale).toInt().coerceAtLeast(8)
        repeat(clusterCount) {
            val p = randomLandPoint(340f) ?: return@repeat
            val roll = rng.nextFloat()
            val type = when {
                roll < 0.58f -> "tree"
                roll < 0.80f -> "gold"
                else -> "food"
            }
            val count = when (type) {
                "tree" -> 10 + rng.nextInt(15)
                "gold" -> 4 + rng.nextInt(6)
                else -> 5 + rng.nextInt(7)
            }
            val spread = when (type) { "tree" -> 290f; "gold" -> 190f; else -> 230f }
            repeat(count) {
                val a = rng.nextFloat() * (Math.PI * 2).toFloat()
                val r = rng.nextFloat() * spread
                val x = p.x + cos(a) * r
                val y = p.y + sin(a) * r
                if (state.isLand(x, y) && !occupiedByBase(x, y, 260f) && !tooCloseResource(x, y, 56f)) {
                    addResource(type, x, y)
                }
            }
        }
    }

    private fun ring(kind: String, cx: Float, cy: Float, count: Int, minR: Float, maxR: Float) {
        var made = 0
        var attempts = 0
        while (made < count && attempts < count * 12) {
            attempts++
            val a = rng.nextFloat() * (Math.PI * 2).toFloat()
            val r = minR + rng.nextFloat() * (maxR - minR)
            val x = cx + cos(a) * r + (rng.nextFloat() - 0.5f) * 100f
            val y = cy + sin(a) * r + (rng.nextFloat() - 0.5f) * 100f
            if (!state.isLand(x, y)) continue
            if (occupiedByBase(x, y, 200f)) continue
            if (tooCloseResource(x, y, 56f)) continue
            addResource(kind, x, y); made++
        }
    }

    private fun addResource(kind: String, x: Float, y: Float) {
        val type = when (kind) {
            "tree" -> ResourceType.WOOD
            "gold" -> ResourceType.GOLD
            else -> ResourceType.FOOD
        }
        val node = ResourceNode(state.nextEntityId(), type)
        node.x = x; node.y = y
        node.amount = when (type) {
            ResourceType.WOOD -> 110 + rng.nextInt(50)
            ResourceType.GOLD -> 90 + rng.nextInt(45)
            ResourceType.FOOD -> 60 + rng.nextInt(35)
        }
        node.maxHp = 1000f
        node.hp = 1000f
        node.radius = when (type) {
            ResourceType.WOOD -> 18
            ResourceType.GOLD -> 16
            ResourceType.FOOD -> 14
        }
        node.variant = rng.nextInt(4)
        if (type == ResourceType.FOOD) {
            node.animal = true
            node.animalKind = "sheep"
            node.face = if (rng.nextBoolean()) 1f else -1f
        }
        state.resources += node
    }

    private fun scatterDecor() {
        val worldScale = state.settings.graphics.densityMult
        val total = (180 * worldScale).toInt()
        val kinds = listOf("bush1", "bush2", "bush3", "bush4", "rock1", "rock2", "rock3", "rock4")
        repeat(total) {
            val p = randomLandPoint(260f) ?: return@repeat
            if (occupiedByBase(p.x, p.y, 320f)) return@repeat
            if (tooCloseResource(p.x, p.y, 38f)) return@repeat
            val kind = kinds[rng.nextInt(kinds.size)]
            state.decor += Decor(kind, p.x, p.y, 0.52f + rng.nextFloat() * 0.30f)
        }
    }

    // ----- Initial spawn ----------------------------------------------------

    private fun spawnFactions() {
        for (faction in state.factions) {
            if (!faction.alive) continue
            val def = BUILDING_DEFS[BuildingType.CASTLE]!!
            val castle = Building(state.nextEntityId(), BuildingType.CASTLE, def)
            castle.x = faction.basePos.x
            castle.y = faction.basePos.y
            castle.maxHp = def.hp.toFloat()
            castle.hp = castle.maxHp
            castle.faction = faction.def.id
            castle.phase = BuildingPhase.COMPLETE
            state.buildings += castle
            faction.popCap = def.popProvided

            // Spawn 3 starting workers around the castle
            repeat(3) {
                val a = rng.nextFloat() * (Math.PI * 2).toFloat()
                val r = 110f + rng.nextFloat() * 40f
                spawnUnit(faction, UnitType.WORKER,
                    faction.basePos.x + cos(a) * r,
                    faction.basePos.y + sin(a) * r)
            }
            // One starter warrior for AI factions, none for player
            if (faction.ai) {
                repeat(2) {
                    val a = rng.nextFloat() * (Math.PI * 2).toFloat()
                    val r = 150f + rng.nextFloat() * 30f
                    spawnUnit(faction, UnitType.WARRIOR,
                        faction.basePos.x + cos(a) * r,
                        faction.basePos.y + sin(a) * r)
                }
            }
        }
    }

    private fun spawnUnit(faction: Faction, type: UnitType, x: Float, y: Float) {
        val def = UNIT_DEFS[type]!!
        val unit = Unit(state.nextEntityId(), type, def)
        unit.x = x; unit.y = y
        unit.maxHp = def.maxHp.toFloat()
        unit.hp = unit.maxHp
        unit.faction = faction.def.id
        state.units += unit
        faction.popUsed += def.popCost
    }

    // ----- Helpers ----------------------------------------------------------

    private fun occupiedByBase(x: Float, y: Float, pad: Float): Boolean {
        for (faction in state.factions) {
            if (hypot(x - faction.basePos.x, y - faction.basePos.y) < 280f + pad) return true
        }
        return false
    }

    private fun tooCloseResource(x: Float, y: Float, pad: Float): Boolean {
        val pad2 = pad * pad
        for (r in state.resources) {
            val dx = r.x - x; val dy = r.y - y
            if (dx * dx + dy * dy < pad2) return true
        }
        return false
    }

    private fun randomLandPoint(margin: Float): Vec2? {
        repeat(80) {
            val x = margin + rng.nextFloat() * (state.worldW - margin * 2)
            val y = margin + rng.nextFloat() * (state.worldH - margin * 2)
            if (state.isLand(x, y)) return Vec2(x, y)
        }
        return null
    }

    @Suppress("unused")
    private fun lengthSquared(dx: Float, dy: Float) = dx * dx + dy * dy

    @Suppress("unused")
    private fun length(dx: Float, dy: Float) = sqrt(dx * dx + dy * dy)
}
