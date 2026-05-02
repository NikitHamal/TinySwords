package com.tinyswords.realmwar.game

import kotlin.math.hypot
import kotlin.random.Random

/**
 * Mutable in-game entities. Plain classes (not data classes) — they live in
 * tight loops, so we want identity equality and avoid hashCode/equals work.
 */

class Faction(
    val def: FactionDef,
    var ai: Boolean,
    val res: Stockpile = Stockpile(0, 0, 0),
    var basePos: Vec2 = Vec2(0f, 0f),
    var alive: Boolean = true,
    val ai_state: AiState = AiState(),
    var underAttack: Float = 0f,
    var popUsed: Int = 0,
    var popCap: Int = 0,
)

class AiState(
    var timer: Float = 0f,
    var buildTimer: Float = 0f,
    var attackTimer: Float = 8f,
    var rallyAngle: Float = 0f,
    var economyBias: Float = 0.5f,
    var lastTargetId: Int = -1,
    var squadMode: SquadMode = SquadMode.STAGE,
)

enum class SquadMode { STAGE, RALLY, ATTACK }

data class Vec2(var x: Float, var y: Float) {
    fun distanceTo(o: Vec2) = hypot(x - o.x, y - o.y)
}

abstract class Entity(val id: Int) {
    var x: Float = 0f
    var y: Float = 0f
    var dead: Boolean = false
    var hp: Float = 0f
    var maxHp: Float = 0f
    var faction: Int = -1
}

enum class UnitState { IDLE, MOVING, ATTACKING, GATHERING, RETURNING, BUILDING, DEAD }

class Unit(id: Int, var type: UnitType, val def: UnitDef) : Entity(id) {
    var state: UnitState = UnitState.IDLE
    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Float = 1f                    // -1 left, +1 right
    var targetX: Float? = null
    var targetY: Float? = null
    var targetId: Int = -1                    // building / unit / resource id
    var carry: ResourceType? = null
    var carryAmount: Int = 0
    var cooldown: Float = 0f
    var animTime: Float = 0f
    var assignedRole: ResourceType? = null    // worker harvesting role
    var depositId: Int = -1                   // for workers, nearest deposit (castle)
    var harvesting: Boolean = false
    var attackTick: Float = 0f                // sound throttling
}

enum class BuildingPhase { FOUNDATION, COMPLETE, RUBBLE }

class Building(id: Int, var type: BuildingType, val def: BuildingDef) : Entity(id) {
    var phase: BuildingPhase = BuildingPhase.COMPLETE
    var buildProgress: Float = 0f
    var trainingType: UnitType? = null
    var trainingTimeLeft: Float = 0f
    var trainQueue: ArrayDeque<UnitType> = ArrayDeque()
    var rallyX: Float? = null
    var rallyY: Float? = null
    var passiveCd: Float = 0f
    var attackCd: Float = 0f
}

class ResourceNode(id: Int, val resType: ResourceType) : Entity(id) {
    var amount: Int = 0
    var depleted: Boolean = false
    var radius: Int = 16
    var variant: Int = 0
    var animal: Boolean = false
    var animalKind: String = ""
    var face: Float = 1f
    var flash: Float = 0f
    var hurt: Float = 0f
    var panic: Float = 0f
    var wanderCd: Float = 0f
}

class Decor(val kind: String, var ix: Float, var iy: Float, val scale: Float)

enum class ProjectileKind { ARROW, HEAL }
class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Int,
    val faction: Int,
    val targetId: Int,
    val kind: ProjectileKind,
    var life: Float = 2.0f,
)

enum class EffectKind { HIT, EXPLOSION, HEAL, FOUNDATION }
class Effect(val kind: EffectKind, var x: Float, var y: Float, var time: Float, val max: Float = time)

/** Full mutable per-game state. Owned by [GameEngine]. */
class GameState(
    val settings: WorldSettings,
    val seed: String,
) {
    var worldW: Int = settings.size.width
    var worldH: Int = settings.size.height
    var nextId: Int = 1

    val factions: MutableList<Faction> = mutableListOf()
    val units: MutableList<Unit> = mutableListOf()
    val buildings: MutableList<Building> = mutableListOf()
    val resources: MutableList<ResourceNode> = mutableListOf()
    val decor: MutableList<Decor> = mutableListOf()
    val projectiles: MutableList<Projectile> = mutableListOf()
    val effects: MutableList<Effect> = mutableListOf()

    /** 1 = land, 0 = water, addressed by [tx + ty * landCols]. */
    var landCols: Int = (worldW / TILE_PX) + 1
    var landRows: Int = (worldH / TILE_PX) + 1
    var landMap: ByteArray = ByteArray(landCols * landRows)
    var groundVariant: ByteArray = ByteArray(landCols * landRows)

    /** RNG keyed by world seed; shared across world gen + AI tactics. */
    val rng: Random = Random(seedAsLong(seed))

    var time: Float = 0f
    var pendingPlayerToast: String? = null

    fun nextEntityId(): Int = nextId++

    fun playerFaction(): Faction = factions[0]

    fun isLand(wx: Float, wy: Float): Boolean {
        val tx = (wx / TILE_PX).toInt()
        val ty = (wy / TILE_PX).toInt()
        if (tx < 0 || ty < 0 || tx >= landCols || ty >= landRows) return false
        return landMap[ty * landCols + tx].toInt() == 1
    }

    private fun seedAsLong(s: String): Long {
        var h = 2166136261L
        for (c in s.ifBlank { "tinyswords" }) {
            h = h xor c.code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h
    }
}
