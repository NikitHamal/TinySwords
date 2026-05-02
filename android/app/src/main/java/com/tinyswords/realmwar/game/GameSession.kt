package com.tinyswords.realmwar.game

import com.tinyswords.realmwar.data.SavedBuilding
import com.tinyswords.realmwar.data.SavedFaction
import com.tinyswords.realmwar.data.SavedResource
import com.tinyswords.realmwar.data.SavedUnit
import com.tinyswords.realmwar.data.WorldRecord
import com.tinyswords.realmwar.data.WorldSaveData
import com.tinyswords.realmwar.data.WorldSettingsData
import com.tinyswords.realmwar.render.Camera
import com.tinyswords.realmwar.render.PlacementGhost
import kotlin.math.hypot

/**
 * Drives a single in-progress match: holds the world, simulation, camera, selection & placement
 * state. UI observes this via state objects pushed in [GameSessionViewModel].
 */
class GameSession(
    val record: WorldRecord,
    val settings: WorldSettings,
    val world: GameWorld
) {
    val simulation = Simulation(world)
    val camera = Camera()
    var paused = false
    var fastForward = false
    val selected: MutableSet<Long> = mutableSetOf()
    var placement: PlacementGhost? = null
    var pendingBuild: String? = null
    var autosaveTimer: Float = 0f
    var lastToast: String? = null
    var lastToastTime: Float = 0f

    init {
        val player = world.playerFaction()
        camera.x = player.basePos[0] - 600f
        camera.y = player.basePos[1] - 360f
        camera.zoom = 1f
    }

    fun step(dt: Float) {
        if (!paused) {
            simulation.step(dt, fastForward)
            autosaveTimer += dt
        }
    }

    fun selectUnitsInRect(x0: Float, y0: Float, x1: Float, y1: Float, additive: Boolean) {
        if (!additive) selected.clear()
        val minX = minOf(x0, x1)
        val maxX = maxOf(x0, x1)
        val minY = minOf(y0, y1)
        val maxY = maxOf(y0, y1)
        for (u in world.units) {
            if (u.dead || u.factionId != 0) continue
            if (u.x in minX..maxX && u.y in minY..maxY) selected.add(u.id)
        }
    }

    fun selectAt(worldX: Float, worldY: Float, additive: Boolean): Boolean {
        if (!additive) selected.clear()
        // Try unit first
        var bestUnit: Unit? = null
        var bestD = 24f * 24f
        for (u in world.units) {
            if (u.dead) continue
            val d = (u.x - worldX).let { it * it } + (u.y - worldY).let { it * it }
            if (d < bestD) { bestD = d; bestUnit = u }
        }
        if (bestUnit != null) {
            selected.add(bestUnit.id)
            return true
        }
        // building
        for (b in world.buildings) {
            if (b.dead) continue
            val r = b.radius + 12f
            if (hypot(b.x - worldX, b.y - worldY) < r) {
                selected.add(b.id)
                return true
            }
        }
        return false
    }

    fun selectAllPlayerArmy() {
        selected.clear()
        for (u in world.units) {
            if (u.dead || u.factionId != 0) continue
            if (u.def.role != GameConfig.UnitRole.WORKER) selected.add(u.id)
        }
    }

    fun issueOrder(worldX: Float, worldY: Float) {
        val targetUnit = pickEnemyUnitAt(worldX, worldY)
        val targetBuilding = pickBuildingAt(worldX, worldY)
        val targetResource = pickResourceAt(worldX, worldY)
        var i = 0
        for (id in selected) {
            val u = world.units.firstOrNull { it.id == id && !it.dead } ?: continue
            if (u.factionId != 0) continue
            // clear previous orders
            u.orderTargetUnit = null
            u.orderTargetBuilding = null
            u.orderTargetResource = null
            u.aiTask = "manual"
            if (targetUnit != null && targetUnit.factionId != u.factionId) {
                u.orderTargetUnit = targetUnit
            } else if (targetBuilding != null && targetBuilding.factionId != u.factionId) {
                u.orderTargetBuilding = targetBuilding
            } else if (targetResource != null && u.def.role == GameConfig.UnitRole.WORKER) {
                u.orderTargetResource = targetResource
            } else {
                val o = formationOffset(i, selected.size, 36f)
                u.moveTargetX = worldX + o[0]
                u.moveTargetY = worldY + o[1]
            }
            i++
        }
        // If a single building is selected, set its rally
        val selectedBuilding = selected.firstNotNullOfOrNull { id -> world.buildings.firstOrNull { it.id == id } }
        if (selected.size == 1 && selectedBuilding != null) {
            selectedBuilding.rallyX = worldX
            selectedBuilding.rallyY = worldY
        }
    }

    fun startBuildPlacement(type: String) {
        pendingBuild = type
        placement = PlacementGhost(type, world.playerFaction().basePos[0], world.playerFaction().basePos[1] + 200f, true)
    }

    fun cancelPlacement() { pendingBuild = null; placement = null }

    fun movePlacement(worldX: Float, worldY: Float) {
        val type = pendingBuild ?: return
        val def = GameConfig.BUILDINGS[type] ?: return
        val valid = isPlacementValid(type, worldX, worldY)
        placement = PlacementGhost(type, worldX, worldY, valid)
    }

    fun confirmPlacement(): Boolean {
        val type = pendingBuild ?: return false
        val ghost = placement ?: return false
        if (!ghost.valid) return false
        val def = GameConfig.BUILDINGS[type] ?: return false
        val player = world.playerFaction()
        if (player.resWood < def.cost.wood || player.resGold < def.cost.gold || player.resFood < def.cost.food) return false
        player.resWood -= def.cost.wood
        player.resGold -= def.cost.gold
        player.resFood -= def.cost.food
        val b = Building(type, def, player.id, ghost.x, ghost.y)
        b.built = false
        b.buildProgress = 0.0f
        world.buildings.add(b)
        // Workers will assist (we just bump build progress periodically here for simplicity)
        b.buildProgress = 0.05f
        // Player workers assist if nearby
        for (u in world.units) {
            if (u.factionId == player.id && u.def.role == GameConfig.UnitRole.WORKER && u.id in selected) {
                u.orderTargetBuilding = b // ai-task: builders
                u.aiTask = "build"
            }
        }
        cancelPlacement()
        return true
    }

    fun isPlacementValid(type: String, x: Float, y: Float): Boolean {
        val def = GameConfig.BUILDINGS[type] ?: return false
        if (!world.isLand(x, y)) return false
        // base radius check vs other buildings
        val r = (def.placeW + def.placeH).toFloat() * 0.5f + 14f
        for (b in world.buildings) {
            if (hypot(b.x - x, b.y - y) < r + b.radius) return false
        }
        for (res in world.resources) {
            if (hypot(res.x - x, res.y - y) < r) return false
        }
        return true
    }

    fun trainUnit(buildingId: Long, unitType: String): Boolean {
        val b = world.buildings.firstOrNull { it.id == buildingId && it.factionId == 0 && !it.dead && it.built } ?: return false
        if (b.production != null) return false
        val def = GameConfig.UNITS[unitType] ?: return false
        if (unitType !in b.def.trains) return false
        val player = world.playerFaction()
        if (player.resWood < def.cost.wood || player.resGold < def.cost.gold || player.resFood < def.cost.food) return false
        player.resWood -= def.cost.wood
        player.resGold -= def.cost.gold
        player.resFood -= def.cost.food
        b.production = ProductionOrder(unitType, def)
        return true
    }

    fun pickEnemyUnitAt(x: Float, y: Float): Unit? {
        var best: Unit? = null
        var bestD = (28f * 28f)
        for (u in world.units) {
            if (u.dead || u.factionId == 0) continue
            val d = (u.x - x).let { it * it } + (u.y - y).let { it * it }
            if (d < bestD) { bestD = d; best = u }
        }
        return best
    }

    fun pickBuildingAt(x: Float, y: Float): Building? {
        for (b in world.buildings) {
            if (b.dead) continue
            if (hypot(b.x - x, b.y - y) < b.radius + 16f) return b
        }
        return null
    }

    fun pickResourceAt(x: Float, y: Float): Resource? {
        for (r in world.resources) {
            if (r.dead || r.amount <= 0) continue
            if (hypot(r.x - x, r.y - y) < (r.r + 18f)) return r
        }
        return null
    }

    fun toSaveData(): WorldSaveData {
        val factions = world.factions.map { SavedFaction(it.id, it.resWood, it.resGold, it.resFood, it.alive) }
        val units = world.units.map { SavedUnit(it.type, it.factionId, it.x, it.y, it.hp, it.carryType, it.carryAmount) }
        val buildings = world.buildings.map { SavedBuilding(it.type, it.factionId, it.x, it.y, it.hp, it.built, it.buildProgress) }
        val resources = world.resources.map { SavedResource(if (it.isAnimal) "food" else when (it.type) { ResourceType.TREE -> "tree"; ResourceType.GOLD -> "gold"; ResourceType.FOOD -> "food" }, it.x, it.y, it.amount, it.animal?.def?.key, it.animal?.hp ?: 0f) }
        return WorldSaveData(world.elapsedTime, factions, units, buildings, resources)
    }

    fun applySaveData(data: WorldSaveData) {
        for (savedF in data.factions) {
            val f = world.factions.firstOrNull { it.id == savedF.id } ?: continue
            f.resWood = savedF.wood
            f.resGold = savedF.gold
            f.resFood = savedF.food
            f.alive = savedF.alive
        }
        // We do not replace generated terrain/decor; we re-seed live entities
        world.units.clear()
        world.buildings.clear()
        world.resources.clear()
        for (su in data.units) {
            val def = GameConfig.UNITS[su.type] ?: continue
            val u = Unit(su.type, def, su.factionId, su.x, su.y).apply {
                hp = su.hp
                carryType = su.carryType
                carryAmount = su.carryAmount
            }
            world.units.add(u)
        }
        for (sb in data.buildings) {
            val def = GameConfig.BUILDINGS[sb.type] ?: continue
            val b = Building(sb.type, def, sb.factionId, sb.x, sb.y).apply {
                hp = sb.hp
                built = sb.built
                buildProgress = sb.buildProgress
            }
            world.buildings.add(b)
        }
        for (sr in data.resources) {
            val type = when (sr.type) { "tree" -> ResourceType.TREE; "gold" -> ResourceType.GOLD; else -> ResourceType.FOOD }
            val animal = if (sr.animal != null) {
                val def = GameConfig.HUNT_ANIMALS.firstOrNull { it.key == sr.animal } ?: GameConfig.HUNT_ANIMALS.first()
                HuntAnimalState(def).apply { hp = sr.animalHp }
            } else null
            world.resources.add(Resource(type, sr.x, sr.y, sr.amount, animal = animal))
        }
        world.elapsedTime = data.elapsedTime
    }

    private fun formationOffset(index: Int, count: Int, spacing: Float): FloatArray {
        if (count <= 1) return floatArrayOf(0f, 0f)
        val cols = kotlin.math.ceil(kotlin.math.sqrt(count.toFloat())).toInt()
        val rows = kotlin.math.ceil(count / cols.toFloat()).toInt()
        return floatArrayOf(
            ((index % cols) - (cols - 1) / 2f) * spacing,
            ((index / cols) - (rows - 1) / 2f) * spacing
        )
    }
}

fun parseSettingsFromData(data: WorldSettingsData): WorldSettings = WorldSettings(
    name = "",
    seed = data.seed,
    size = runCatching { GameConfig.WorldSize.valueOf(data.size) }.getOrDefault(GameConfig.WorldSize.LARGE),
    mapStyle = runCatching { GameConfig.MapStyle.valueOf(data.mapStyle) }.getOrDefault(GameConfig.MapStyle.CROSSROADS),
    difficulty = runCatching { GameConfig.Difficulty.valueOf(data.difficulty) }.getOrDefault(GameConfig.Difficulty.NORMAL),
    density = runCatching { GameConfig.ResourceDensity.valueOf(data.density) }.getOrDefault(GameConfig.ResourceDensity.RICH),
    graphics = runCatching { GameConfig.GraphicsQuality.valueOf(data.graphics) }.getOrDefault(GameConfig.GraphicsQuality.BALANCED),
    rivals = data.rivals,
    autosave = data.autosave
)
