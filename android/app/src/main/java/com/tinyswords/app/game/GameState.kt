package com.tinyswords.app.game

import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.SpatialIndex

data class WorldSettings(
    val size: String = "standard",
    val mapStyle: String = "crossroads",
    val difficulty: String = "normal",
    val resourceDensity: String = "normal",
    val rivals: Int = 4,
    val autosave: Boolean = true,
    val seed: String = System.currentTimeMillis().toString(),
    val graphics: String = "balanced"
)

data class Camera(
    var x: Float = 700f,
    var y: Float = 720f,
    var zoom: Float = 1.0f,
    var targetZoom: Float = 1.0f
)

class GameState(val settings: WorldSettings = WorldSettings()) {
    // World dimensions
    var worldW: Float = WORLD_PRESETS[settings.size]?.width ?: 12400f
    var worldH: Float = WORLD_PRESETS[settings.size]?.height ?: 9000f

    // Global entity ID counter
    private var nextGid: Int = 1
    fun nextId(): Int = nextGid++
    fun ensureNextIdGreaterThan(id: Int) {
        if (id >= nextGid) nextGid = id + 1
    }

    // Camera
    val camera = Camera()

    // Time tracking
    var time: Float = 0f
    var paused: Boolean = false
    var gameOver: Boolean = false
    var winnerFaction: Int = -1

    // Entity collections
    val units = mutableListOf<GameUnit>()
    val buildings = mutableListOf<GameBuilding>()
    val resources = mutableListOf<GameResource>()
    val decor = mutableListOf<GameDecor>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<GameEffect>()

    // Selection state
    val selected = mutableListOf<GameEntity>()
    var formationMode: String = "box"

    // Last known pointer position in world coordinates, used by native placement ghosts.
    var pointerWorldX: Float = 0f
    var pointerWorldY: Float = 0f

    // Faction states (player = 0, AI = 1-4)
    val factions = Array(5) { FactionState(it) }

    // Terrain data
    var landMap: ByteArray = ByteArray(0)
    var landCols: Int = 0
    var landRows: Int = 0
    var biomeMap: IntArray = IntArray(0)

    // Pathfinding grid
    var pathGrid: ByteArray = ByteArray(0)
    var pathCols: Int = 0
    var pathRows: Int = 0
    var navVersion: Int = 0

    // Spatial indices
    var spatialRebuildTimer: Float = 0f
    val unitIndex = SpatialIndex<GameUnit>(UNIT_BUCKET_SIZE)
    val resourceIndex = SpatialIndex<GameResource>(RESOURCE_BUCKET_SIZE)
    val buildingIndex = SpatialIndex<GameBuilding>(BUILDING_BUCKET_SIZE)
    val decorIndex = SpatialIndex<GameDecor>(DECOR_BUCKET_SIZE)
    private val entityById = HashMap<Int, GameEntity>(1024)

    // Building placement mode
    var placingBuilding: String? = null

    // AI timing
    var aiTick: Int = 0

    // Autosave
    var autosaveTimer: Float = AUTOSAVE_INTERVAL

    // Difficulty
    val difficulty: DifficultyPreset
        get() = DIFFICULTY_PRESETS[settings.difficulty] ?: DIFFICULTY_PRESETS["normal"]!!

    val resourceDensity: Float
        get() = RESOURCE_DENSITY_PRESETS[settings.resourceDensity] ?: 1f

    fun rebuildSpatialIndices() {
        unitIndex.rebuild(units)
        resourceIndex.rebuild(resources)
        buildingIndex.rebuild(buildings)
        decorIndex.clear()
        for (d in decor) if (!d.dead && d.isSolid) decorIndex.insert(d)
        rebuildEntityIndex()
    }

    fun rebuildEntityIndex() {
        entityById.clear()
        for (u in units) if (!u.dead) entityById[u.id] = u
        for (b in buildings) if (!b.dead) entityById[b.id] = b
        for (r in resources) if (!r.dead) entityById[r.id] = r
    }

    fun isWater(x: Float, y: Float): Boolean {
        val col = (x / TILE).toInt()
        val row = (y / TILE).toInt()
        if (col < 0 || col >= landCols || row < 0 || row >= landRows) return true
        return landMap[row * landCols + col].toInt() == 0
    }

    fun isLand(x: Float, y: Float): Boolean = !isWater(x, y)

    fun isSafeLand(x: Float, y: Float, radius: Float = 20f): Boolean {
        if (isWater(x, y)) return false
        // Probe 8 directions
        val probes = floatArrayOf(radius, 0f, -radius, 0f, 0f, radius, 0f, -radius,
            radius * 0.7f, radius * 0.7f, -radius * 0.7f, radius * 0.7f,
            radius * 0.7f, -radius * 0.7f, -radius * 0.7f, -radius * 0.7f)
        for (i in probes.indices step 2) {
            if (isWater(x + probes[i], y + probes[i + 1])) return false
        }
        return true
    }

    fun population(factionId: Int): Pair<Int, Int> {
        var used = 0
        var cap = 0
        for (u in units) {
            if (u.faction == factionId && !u.dead && !u.garrisoned) {
                used += (UNITS[u.type]?.pop ?: 1)
            }
        }
        for (b in buildings) {
            if (b.faction == factionId && !b.dead && b.buildProgress >= 1f) {
                cap += (BUILDINGS[b.type]?.pop ?: 0)
            }
        }
        return Pair(used, cap)
    }

    fun aliveEntity(id: Int): GameEntity? {
        entityById[id]?.let { if (!it.dead) return it }

        // Newly spawned entities may not have reached the next spatial/index rebuild yet.
        // Fall back to a direct scan for correctness, then cache the result.
        for (u in units) if (u.id == id && !u.dead) { entityById[id] = u; return u }
        for (b in buildings) if (b.id == id && !b.dead) { entityById[id] = b; return b }
        for (r in resources) if (r.id == id && !r.dead) { entityById[id] = r; return r }
        return null
    }
}
