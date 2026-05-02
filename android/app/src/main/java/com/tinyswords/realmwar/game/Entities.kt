package com.tinyswords.realmwar.game

import androidx.compose.ui.graphics.Color

/**
 * Mutable entity types used by the simulation. Kept as data classes for ergonomic copying when
 * persisting save state. The `id` field is unique within a session.
 */

object IdGen {
    private var next = 1L
    fun next(): Long = next++
    fun reset() { next = 1L }
}

enum class EntityKind { UNIT, BUILDING, RESOURCE, PROJECTILE, EFFECT, DECOR }

class Faction(
    val def: GameConfig.FactionDef,
    val isAi: Boolean,
    var resWood: Int,
    var resGold: Int,
    var resFood: Int,
    var alive: Boolean = true,
    val basePos: FloatArray = floatArrayOf(0f, 0f),
    val aiState: AiState = AiState()
) {
    val color get() = Color(def.color)
    val dark get() = Color(def.dark)
    val name get() = def.name
    val key get() = def.key
    val id get() = def.id

    var underAttackTimer = 0f
}

class AiState(
    var timer: Float = 0f,
    var buildTimer: Float = 0f,
    var attackTimer: Float = 0f,
    var rallyAngle: Float = 0f,
    var economyBias: Float = 0.5f,
    var squadGoalX: Float = 0f,
    var squadGoalY: Float = 0f,
    var squadMode: String = "stage"
)

class Unit(
    val type: String,
    val def: GameConfig.UnitDef,
    val factionId: Int,
    var x: Float,
    var y: Float
) {
    val id: Long = IdGen.next()
    var hp: Float = def.hp.toFloat()
    var maxHp: Float = def.hp.toFloat()
    var facing: Int = 0 // 0=down, 1=up, 2=left, 3=right
    var moveTargetX: Float? = null
    var moveTargetY: Float? = null
    var path: ArrayDeque<FloatArray>? = null
    var orderTargetUnit: Unit? = null
    var orderTargetBuilding: Building? = null
    var orderTargetResource: Resource? = null
    var attackCooldown: Float = 0f
    var hurtFlash: Float = 0f
    var animTime: Float = 0f
    var dead: Boolean = false
    // Worker carry state
    var carryType: String? = null
    var carryAmount: Int = 0
    var gatherTimer: Float = 0f
    var aiTask: String? = null
    // Heading vector for sprite facing
    var vx: Float = 0f
    var vy: Float = 0f
    // Rally / patrol
    var aggressive: Boolean = true
    val radius: Float get() = def.radius
}

class Building(
    val type: String,
    val def: GameConfig.BuildingDef,
    val factionId: Int,
    var x: Float,
    var y: Float
) {
    val id: Long = IdGen.next()
    var hp: Float = def.hp.toFloat()
    var maxHp: Float = def.hp.toFloat()
    var built: Boolean = true
    var buildProgress: Float = 1f // 0..1
    var production: ProductionOrder? = null
    var rallyX: Float = x
    var rallyY: Float = y + 80f
    var dead: Boolean = false
    var towerCooldown: Float = 0f
    val radius: Float get() = (def.placeW + def.placeH).toFloat() * 0.25f
}

class ProductionOrder(
    val unitType: String,
    val unitDef: GameConfig.UnitDef
) {
    var time: Float = 0f
    val total: Float = unitDef.buildTime
}

enum class ResourceType { TREE, GOLD, FOOD }

class Resource(
    val type: ResourceType,
    var x: Float,
    var y: Float,
    var amount: Int,
    var maxAmount: Int = amount,
    val animal: HuntAnimalState? = null,
    val r: Float = when (type) {
        ResourceType.TREE -> 22f
        ResourceType.GOLD -> 26f
        ResourceType.FOOD -> 14f
    }
) {
    val id: Long = IdGen.next()
    var depleted: Boolean = false
    var dead: Boolean = false
    val isAnimal get() = animal != null
}

class HuntAnimalState(
    val def: GameConfig.HuntAnimalDef
) {
    var hp: Float = def.hp.toFloat()
    var panicTimer: Float = 0f
    var heading: Float = (Math.random() * Math.PI * 2).toFloat()
    var hurtFlash: Float = 0f
    var facing: Int = 0
    var moveT: Float = 0f
    var stride: Float = 0f
}

class Projectile(
    val factionId: Int,
    var x: Float,
    var y: Float,
    val tx: Float,
    val ty: Float,
    val damage: Int,
    val targetUnitId: Long? = null,
    val targetBuildingId: Long? = null,
    val speed: Float = 520f,
    var t: Float = 0f
) {
    val id: Long = IdGen.next()
    var dead: Boolean = false
    val totalDist: Float = kotlin.math.hypot(tx - x, ty - y)
}

class FxEffect(
    var x: Float,
    var y: Float,
    val kind: String,
    val duration: Float
) {
    val id: Long = IdGen.next()
    var t: Float = 0f
    var dead: Boolean = false
}

class Decor(
    val kind: String,
    var x: Float,
    var y: Float,
    val scale: Float
) {
    val id: Long = IdGen.next()
}
