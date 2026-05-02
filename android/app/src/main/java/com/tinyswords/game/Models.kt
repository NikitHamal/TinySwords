package com.tinyswords.game

import kotlinx.serialization.Serializable

/** All entity types share this id space so selection, targeting, and pathfinding can use a single key. */
sealed interface Entity {
    val id: Int
    var x: Float
    var y: Float
    val faction: Int
    var hp: Float
    val maxHp: Float
    var alive: Boolean
}

@Serializable
data class GameUnit(
    override val id: Int,
    val type: UnitType,
    override var x: Float,
    override var y: Float,
    override val faction: Int,
    override var hp: Float,
    override val maxHp: Float,
    override var alive: Boolean = true,
    var order: Order = Order.IDLE,
    var goalX: Float = x,
    var goalY: Float = y,
    var targetId: Int = -1,
    var path: MutableList<Float> = mutableListOf(), // flat [x0,y0,x1,y1,...]
    var pathIndex: Int = 0,
    var cd: Float = 0f,        // attack cooldown
    var anim: Float = 0f,      // animation accumulator
    var facing: Int = 1,       // -1 left, 1 right
    var carrying: ResourceKind? = null,
    var carryAmount: Int = 0,
    var assignedResourceId: Int = -1,
    var dropoffId: Int = -1,
    var animState: Int = 0,    // 0 idle, 1 run, 2 attack, 3 interact, 4 heal
    var swingPhase: Float = 0f,
    var stuck: Float = 0f
) : Entity {
    val radius: Float get() = type.radius
}

@Serializable
data class Building(
    override val id: Int,
    val type: BuildingType,
    override var x: Float,
    override var y: Float,
    override val faction: Int,
    override var hp: Float,
    override val maxHp: Float,
    override var alive: Boolean = true,
    var buildProgress: Float = 1f,        // 0..1, complete buildings sit at 1
    var queue: MutableList<TrainOrder> = mutableListOf(),
    var rallyX: Float = -1f,
    var rallyY: Float = -1f,
    var towerCd: Float = 0f               // tower passive shoot cooldown
) : Entity {
    val w: Float get() = type.width
    val h: Float get() = type.height
}

@Serializable
data class TrainOrder(val type: UnitType, var progress: Float = 0f)

@Serializable
data class GameResource(
    override val id: Int,
    val kind: ResourceKind,
    override var x: Float,
    override var y: Float,
    override var hp: Float = 1f,
    override val maxHp: Float = 1f,
    override var alive: Boolean = true,
    var amount: Int = 280,
    val maxAmount: Int = 280,
    val variant: Int = 0,        // sprite variant index
    val animal: AnimalKind? = null,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var animDir: Int = 0,
    var animState: Int = 0,
    var animTimer: Float = 0f,
    var panic: Float = 0f
) : Entity {
    override val faction: Int get() = -1 // resources are neutral
}

enum class AnimalKind(val baseHp: Int, val yield: Int, val walk: Float, val run: Float) {
    DEER(42, 24, 22f, 70f),
    BOAR(54, 28, 18f, 58f),
    HARE(18, 12, 26f, 84f),
    FOX(26, 16, 24f, 78f),
    GROUSE(20, 14, 22f, 72f),
    SHEEP(28, 14, 14f, 36f);
    companion object { val ALL = values() }
}

@Serializable
data class Projectile(
    var x: Float,
    var y: Float,
    var tx: Float,
    var ty: Float,
    val targetId: Int,
    val faction: Int,
    val damage: Int,
    var life: Float = 2.2f,
    val speed: Float = 510f
)

data class Effect(
    var x: Float,
    var y: Float,
    var t: Float,
    val ttl: Float,
    val kind: EffectKind
)

enum class EffectKind { HIT, BOOM, HEAL, DUST, SPLASH }

@Serializable
data class Faction(
    val id: Int,
    var wood: Int = C.START_WOOD,
    var gold: Int = C.START_GOLD,
    var food: Int = C.START_FOOD,
    var popUsed: Int = 0,
    var popCap: Int = 0,
    var alive: Boolean = true,
    val isAI: Boolean = false,
    var aiTimer: Float = 1.5f,
    var aiAttackTimer: Float = 30f,
    var aiBuildTimer: Float = 12f
)
