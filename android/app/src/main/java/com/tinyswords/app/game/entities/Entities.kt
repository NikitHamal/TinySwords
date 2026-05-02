package com.tinyswords.app.game.entities

import com.tinyswords.app.game.*

// ── Base Entity ──
abstract class GameEntity {
    var id: Int = 0
    var x: Float = 0f
    var y: Float = 0f
    var faction: Int = 0
    var dead: Boolean = false
    var flash: Float = 0f

    abstract val entityType: String
}

// ── Unit Orders ──
enum class UnitOrder {
    IDLE, MOVE, ATTACK, HARVEST, REPAIR, GARRISON, ATTACK_MOVE
}

// ── Worker Roles ──
enum class WorkerRole {
    AUTO, WOOD, GOLD, FOOD, BUILD, IDLE
}

// ── Resource Types ──
enum class ResourceType {
    TREE, GOLD, FOOD
}

// ── Unit Entity ──
class GameUnit : GameEntity() {
    override val entityType = "unit"

    var type: String = "worker"
    var hp: Int = 55
    var maxHp: Int = 55
    var speed: Float = 96f
    var range: Float = 22f
    var damage: Int = 5
    var cd: Float = 0f
    var maxCd: Float = 0.65f

    var order: UnitOrder = UnitOrder.IDLE
    var target: GameEntity? = null
    var targetId: Int = -1
    var goalX: Float = 0f
    var goalY: Float = 0f
    var hasGoal: Boolean = false
    var attackMove: Boolean = false
    var hold: Boolean = false

    var face: Int = 1 // 1 = right, -1 = left
    var animTime: Float = 0f
    var selected: Boolean = false
    var garrisoned: Boolean = false

    // Pathfinding
    var path: MutableList<PathPoint> = mutableListOf()
    var pathIndex: Int = 0
    var pathVersion: Int = -1
    var pathRetryTimer: Float = 0f
    var stuck: Float = 0f
    var trafficJam: Float = 0f
    var lastX: Float = 0f
    var lastY: Float = 0f

    // Worker
    var workerRole: WorkerRole = WorkerRole.AUTO
    var carrying: String? = null // "wood", "gold", "food"
    var carryAmount: Float = 0f
    var gatherTimer: Float = 0f

    // Pop cost
    var pop: Int = 1

    companion object {
        fun create(type: String, factionId: Int, x: Float, y: Float, nextId: () -> Int): GameUnit {
            val def = UNITS[type] ?: UNITS["worker"]!!
            return GameUnit().apply {
                id = nextId()
                this.type = type
                this.faction = factionId
                this.x = x
                this.y = y
                hp = def.hp
                maxHp = def.hp
                speed = def.speed
                range = def.range
                damage = def.damage
                maxCd = def.cd
                pop = def.pop
            }
        }
    }
}

data class PathPoint(val x: Float, val y: Float)

// ── Building Entity ──
class GameBuilding : GameEntity() {
    override val entityType = "building"

    var type: String = "castle"
    var hp: Int = 1200
    var maxHp: Int = 1200
    var buildProgress: Float = 1f // 0..1, 1 = complete
    var buildTime: Float = 32f

    var rallyX: Float = 0f
    var rallyY: Float = 0f
    var hasRally: Boolean = false

    var queue: MutableList<TrainSlot> = mutableListOf()
    var garrison: MutableList<GameUnit> = mutableListOf()
    var selected: Boolean = false

    // Tower specifics
    var towerCd: Float = 0f

    // Monastery healing
    var healCd: Float = 0f

    companion object {
        fun create(type: String, factionId: Int, x: Float, y: Float, nextId: () -> Int, built: Boolean = true): GameBuilding {
            val def = BUILDINGS[type] ?: BUILDINGS["castle"]!!
            return GameBuilding().apply {
                id = nextId()
                this.type = type
                this.faction = factionId
                this.x = x
                this.y = y
                hp = if (built) def.hp else 1
                maxHp = def.hp
                buildProgress = if (built) 1f else 0f
                buildTime = def.buildTime
            }
        }
    }
}

data class TrainSlot(val unitType: String, var progress: Float = 0f, val trainTime: Float)

// ── Resource Entity ──
class GameResource : GameEntity() {
    override val entityType = "resource"

    var type: ResourceType = ResourceType.TREE
    var amount: Float = 100f
    var maxAmount: Float = 100f
    var depleted: Boolean = false
    var variant: Int = 0

    // Animal resource specifics
    var isAnimal: Boolean = false
    var animalKind: String = ""
    var animalHp: Float = 0f
    var animalMaxHp: Float = 0f
    var animalDir: Int = 0 // 0=down, 1=up, 2=left, 3=right
    var vx: Float = 0f
    var vy: Float = 0f
    var panic: Float = 0f
    var wanderTimer: Float = 0f
    var hurtTimer: Float = 0f
    var animTime: Float = 0f

    companion object {
        fun createTree(x: Float, y: Float, variant: Int, nextId: () -> Int): GameResource {
            return GameResource().apply {
                id = nextId()
                this.x = x
                this.y = y
                this.type = ResourceType.TREE
                this.variant = variant
                amount = 80f + (Math.random() * 40f).toFloat()
                maxAmount = amount
            }
        }

        fun createGold(x: Float, y: Float, variant: Int, nextId: () -> Int): GameResource {
            return GameResource().apply {
                id = nextId()
                this.x = x
                this.y = y
                this.type = ResourceType.GOLD
                this.variant = variant
                amount = 120f + (Math.random() * 60f).toFloat()
                maxAmount = amount
            }
        }

        fun createAnimal(kind: String, x: Float, y: Float, nextId: () -> Int): GameResource {
            val def = HUNT_ANIMALS[kind] ?: HUNT_ANIMALS["deer"]!!
            return GameResource().apply {
                id = nextId()
                this.x = x
                this.y = y
                this.type = ResourceType.FOOD
                this.isAnimal = true
                this.animalKind = kind
                this.animalHp = def.hp.toFloat()
                this.animalMaxHp = def.hp.toFloat()
                amount = def.yield.toFloat()
                maxAmount = amount
                wanderTimer = (Math.random() * 3.0).toFloat()
            }
        }
    }
}

// ── Decor Entity ──
class GameDecor : GameEntity() {
    override val entityType = "decor"

    var kind: String = "bush1"
    var scale: Float = 1f
    var isSky: Boolean = false
    var isWater: Boolean = false
    var isSolid: Boolean = false
    var drift: Float = 0f
    var driftSpeed: Float = 0f

    companion object {
        fun create(kind: String, x: Float, y: Float, nextId: () -> Int): GameDecor {
            return GameDecor().apply {
                id = nextId()
                this.x = x
                this.y = y
                this.kind = kind
                isSolid = kind.startsWith("rock")
                isSky = kind.startsWith("cloud")
                isWater = kind.startsWith("waterRock")
                if (isSky) {
                    driftSpeed = 8f + (Math.random() * 12f).toFloat()
                    scale = CLOUD_BOOST
                }
            }
        }
    }
}

// ── Projectile ──
data class Projectile(
    var x: Float,
    var y: Float,
    var targetId: Int,
    var factionId: Int,
    var damage: Int,
    var life: Float = PROJECTILE_LIFE,
    var speed: Float = PROJECTILE_SPEED,
    var angle: Float = 0f
)

// ── Visual Effect ──
data class GameEffect(
    val kind: String,
    var x: Float,
    var y: Float,
    var time: Float = 0f,
    val maxTime: Float = 0.6f,
    val scale: Float = 1f
)

// ── Faction State ──
class FactionState(val id: Int) {
    var wood: Float = 200f
    var gold: Float = 200f
    var food: Float = 10f
    var alive: Boolean = true
    var underAttack: Float = 0f

    val aiState = AIState()

    fun canAfford(costWood: Int, costGold: Int, costFood: Int): Boolean {
        return wood >= costWood && gold >= costGold && food >= costFood
    }

    fun pay(costWood: Int, costGold: Int, costFood: Int) {
        wood -= costWood
        gold -= costGold
        food -= costFood
    }
}

class AIState {
    var timer: Float = 0f
    var buildTimer: Float = 0f
    var attackTimer: Float = 10f
    var rallyAngle: Float = 0f
    var expansion: Int = 0
    var squadGoal: PathPoint? = null
    var squadMode: String = "stage"
    var lastTargetId: Int = -1
    var economyBias: Float = (Math.random()).toFloat()
}
