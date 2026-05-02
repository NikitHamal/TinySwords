package com.tinyswords.game

import kotlin.math.*

/**
 * Top-level game state. Holds the world, all live entities, factions, and the camera.
 * Mutated by [Simulation] each tick. UI reads via the Compose [snapshot] flow.
 */
class Game(seed: String) {
    val world: WorldData
    val pathfinder: Pathfinder
    val factions: Array<Faction>

    val units = mutableListOf<GameUnit>()
    val buildings = mutableListOf<Building>()
    var resources = mutableListOf<GameResource>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<Effect>()

    val playerFaction = 0

    private var nextId = 1
    fun nextEntityId(): Int = nextId++

    var time = 0f
    var paused = false
    var gameOver: Int = -1 // faction id of winner, -1 = ongoing

    val camera = Camera()
    val selection = mutableListOf<Int>()  // entity ids
    var dirtyPath = true                  // request a pathfinder rebuild next tick

    init {
        val gen = WorldGen(seed, factionCount = 2)
        val data = gen.generate()
        world = data
        pathfinder = Pathfinder(world)
        factions = Array(gen.factionCount) { Faction(it, isAI = it != 0) }
        resources = data.resources

        // Place each faction's starting castle + 4 workers.
        for ((idx, start) in data.starts.withIndex()) {
            val cb = createBuilding(BuildingType.CASTLE, start.first, start.second, idx, complete = true)
            cb.rallyX = start.first - 100; cb.rallyY = start.second + 80
            factions[idx].popCap = BuildingType.CASTLE.popCap
            for (k in 0 until 4) {
                val a = (Math.PI * 2 * k / 4).toFloat()
                spawnUnit(UnitType.WORKER, start.first + cos(a) * 90f, start.second + sin(a) * 90f, idx)
            }
        }

        // Center camera on the player's castle.
        camera.x = data.starts[0].first - C.WORLD_W * 0.10f
        camera.y = data.starts[0].second - C.WORLD_H * 0.10f
        pathfinder.rebuild(buildings, resources)
    }

    fun spawnUnit(type: UnitType, x: Float, y: Float, faction: Int): GameUnit {
        val u = GameUnit(
            id = nextEntityId(),
            type = type,
            x = x, y = y,
            faction = faction,
            hp = type.hp.toFloat(), maxHp = type.hp.toFloat(),
            goalX = x, goalY = y
        )
        units += u
        factions[faction].popUsed += type.pop
        return u
    }

    fun createBuilding(type: BuildingType, x: Float, y: Float, faction: Int, complete: Boolean = false): Building {
        val b = Building(
            id = nextEntityId(),
            type = type,
            x = x, y = y,
            faction = faction,
            hp = if (complete) type.hp.toFloat() else 1f,
            maxHp = type.hp.toFloat(),
            buildProgress = if (complete) 1f else 0f
        )
        buildings += b
        if (complete) {
            factions[faction].popCap += type.popCap
        }
        dirtyPath = true
        return b
    }

    fun findEntity(id: Int): Entity? {
        units.firstOrNull { it.id == id }?.let { return it }
        buildings.firstOrNull { it.id == id }?.let { return it }
        return resources.firstOrNull { it.id == id }
    }

    /** Return the closest enemy of [faction] within [range] of point (x, y), or null. */
    fun nearestEnemy(x: Float, y: Float, faction: Int, range: Float): GameUnit? {
        var best: GameUnit? = null
        var bestD = range * range
        for (u in units) {
            if (!u.alive || u.faction == faction) continue
            val dx = u.x - x; val dy = u.y - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = u }
        }
        return best
    }

    fun nearestEnemyBuilding(x: Float, y: Float, faction: Int, range: Float): Building? {
        var best: Building? = null
        var bestD = range * range
        for (b in buildings) {
            if (!b.alive || b.faction == faction) continue
            val dx = b.x - x; val dy = b.y - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = b }
        }
        return best
    }

    fun nearestResource(x: Float, y: Float, kind: ResourceKind, range: Float = 2400f): GameResource? {
        var best: GameResource? = null
        var bestD = range * range
        for (r in resources) {
            if (!r.alive || r.kind != kind || r.amount <= 0) continue
            val dx = r.x - x; val dy = r.y - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = r }
        }
        return best
    }

    fun nearestDropoff(x: Float, y: Float, faction: Int): Building? {
        var best: Building? = null
        var bestD = Float.MAX_VALUE
        for (b in buildings) {
            if (!b.alive || b.faction != faction) continue
            if (b.type != BuildingType.CASTLE && b.type != BuildingType.HOUSE) continue
            val dx = b.x - x; val dy = b.y - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = b }
        }
        return best
    }

    /** Snapshot used by Compose to schedule redraws — increment when state changes. */
    var revision = 0L
        private set
    fun bumpRevision() { revision++ }
}

class Camera {
    var x: Float = 0f
    var y: Float = 0f
    var zoom: Float = 0.9f
    var targetZoom: Float = 0.9f
    var viewW: Float = 1280f
    var viewH: Float = 720f

    fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> =
        (x + sx / zoom) to (y + sy / zoom)

    fun worldToScreen(wx: Float, wy: Float): Pair<Float, Float> =
        ((wx - x) * zoom) to ((wy - y) * zoom)

    fun clamp() {
        val maxX = (C.WORLD_W - viewW / zoom).coerceAtLeast(0f)
        val maxY = (C.WORLD_H - viewH / zoom).coerceAtLeast(0f)
        x = x.coerceIn(0f, maxX); y = y.coerceIn(0f, maxY)
    }
}
