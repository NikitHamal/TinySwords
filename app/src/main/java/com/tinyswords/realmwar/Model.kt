package com.tinyswords.realmwar

import androidx.compose.ui.graphics.Color
import kotlin.math.max

const val WORLD_WIDTH = 16_000f
const val WORLD_HEIGHT = 11_200f
const val TILE_SIZE = 64f
const val SPRITE_BOOST = 1.08f

data class Cost(val wood: Int = 0, val gold: Int = 0, val food: Int = 0) {
    fun canPay(stock: Stock) = stock.wood >= wood && stock.gold >= gold && stock.food >= food
}

data class Stock(var wood: Int, var gold: Int, var food: Int) {
    fun pay(cost: Cost): Boolean {
        if (!cost.canPay(this)) return false
        wood -= cost.wood
        gold -= cost.gold
        food -= cost.food
        return true
    }

    fun add(kind: ResourceKind, amount: Int) {
        when (kind) {
            ResourceKind.Wood -> wood += amount
            ResourceKind.Gold -> gold += amount
            ResourceKind.Food -> food += amount
        }
    }
}

enum class ResourceKind(val label: String) { Wood("Wood"), Gold("Gold"), Food("Food") }

enum class FactionId(
    val key: String,
    val title: String,
    val folder: String,
    val color: Color,
    val dark: Color,
    val ai: Boolean,
    val baseFx: Float,
    val baseFy: Float
) {
    Blue("blue", "Blue Realm", "Blue", Color(0xff61b7d9), Color(0xff1f5670), false, .135f, .155f),
    Red("red", "Red Dominion", "Red", Color(0xffdb6060), Color(0xff78232b), true, .865f, .155f),
    Yellow("yellow", "Golden Clan", "Yellow", Color(0xffe6ca59), Color(0xff80651e), true, .135f, .845f),
    Purple("purple", "Violet Order", "Purple", Color(0xffb071df), Color(0xff4a246e), true, .865f, .845f),
    Black("black", "Iron Pact", "Black", Color(0xffaeb3bd), Color(0xff30353d), true, .50f, .50f)
}

enum class UnitKind(
    val label: String,
    val role: String,
    val hp: Float,
    val speed: Float,
    val range: Float,
    val damage: Float,
    val cooldown: Float,
    val cost: Cost,
    val trainTime: Float,
    val pop: Int,
    val frameW: Int,
    val frameH: Int,
    val scale: Float,
    val radius: Float,
    val hotkey: String
) {
    Worker("Worker", "worker", 55f, 96f, 24f, 5f, .65f, Cost(gold = 35, food = 1), 8f, 1, 192, 192, .34f, 12f, "1"),
    Warrior("Warrior", "melee", 95f, 78f, 30f, 15f, .78f, Cost(gold = 65, food = 1), 10f, 1, 192, 192, .35f, 13f, "2"),
    Archer("Archer", "ranged", 62f, 74f, 290f, 12f, 1.18f, Cost(wood = 40, gold = 70, food = 1), 12f, 1, 192, 192, .34f, 12f, "3"),
    Lancer("Lancer", "melee", 135f, 88f, 44f, 24f, 1.05f, Cost(wood = 55, gold = 95, food = 2), 16f, 2, 320, 320, .40f, 18f, "4"),
    Monk("Monk", "healer", 64f, 70f, 215f, -16f, 1.1f, Cost(wood = 25, gold = 110, food = 1), 14f, 1, 192, 192, .34f, 12f, "5")
}

enum class BuildingKind(
    val label: String,
    val file: String,
    val hp: Float,
    val pop: Int,
    val cost: Cost,
    val buildTime: Float,
    val trains: Set<UnitKind>,
    val drawW: Float,
    val drawH: Float,
    val placeW: Float,
    val placeH: Float,
    val placeYOffset: Float,
    val hotkey: String,
    val range: Float = 0f
) {
    Castle("Castle", "Castle.png", 1200f, 12, Cost(wood = 280, gold = 160), 32f, setOf(UnitKind.Worker, UnitKind.Warrior), 180f, 132f, 152f, 58f, 38f, "C"),
    House("House", "House1.png", 260f, 8, Cost(wood = 70, gold = 15), 12f, emptySet(), 84f, 74f, 66f, 38f, 24f, "H"),
    Barracks("Barracks", "Barracks.png", 520f, 0, Cost(wood = 145, gold = 85), 22f, setOf(UnitKind.Warrior, UnitKind.Lancer), 106f, 90f, 84f, 46f, 28f, "R"),
    Archery("Archery", "Archery.png", 440f, 0, Cost(wood = 120, gold = 95), 20f, setOf(UnitKind.Archer), 106f, 90f, 84f, 46f, 28f, "A"),
    Tower("Tower", "Tower.png", 620f, 0, Cost(wood = 110, gold = 115), 20f, emptySet(), 60f, 96f, 42f, 38f, 30f, "T", range = 360f),
    Monastery("Monastery", "Monastery.png", 420f, 0, Cost(wood = 120, gold = 165), 24f, setOf(UnitKind.Monk), 102f, 106f, 70f, 44f, 34f, "M")
}

enum class AnimalKind(
    val label: String,
    val folder: String,
    val hp: Float,
    val yield: Int,
    val radius: Float,
    val scale: Float,
    val runSpeed: Float,
    val weight: Float,
    val retaliate: Float = 0f
) {
    Deer("Deer", "Deer", 42f, 24, 13f, 1.10f, 76f, 1.05f),
    Boar("Boar", "Boar", 54f, 28, 14f, 1.04f, 62f, .82f, retaliate = 4f),
    Hare("Hare", "Hare", 18f, 12, 10f, .68f, 92f, 1.38f),
    Fox("Fox", "Fox", 26f, 16, 12f, .86f, 84f, .72f),
    Grouse("Black Grouse", "Black_grouse", 20f, 14, 11f, .58f, 80f, .78f)
}

enum class Order { Idle, Move, Attack, AttackMove, Harvest, ReturnCargo, Build, Repair, Heal }

enum class TargetKind { Unit, Building, Resource }

data class TargetRef(val kind: TargetKind, val id: Int)

data class Vec2(var x: Float = 0f, var y: Float = 0f)

data class FactionState(
    val id: FactionId,
    val base: Vec2,
    val res: Stock,
    var alive: Boolean = true,
    var aiTimer: Float = 1f,
    var attackTimer: Float = 16f
)

data class UnitEntity(
    val id: Int,
    val faction: FactionId,
    val kind: UnitKind,
    var x: Float,
    var y: Float,
    var hp: Float = kind.hp,
    var order: Order = Order.Idle,
    var goal: Vec2? = null,
    var target: TargetRef? = null,
    var cooldown: Float = 0f,
    var anim: Float = 0f,
    var face: Float = 1f,
    var selected: Boolean = false,
    var carryKind: ResourceKind? = null,
    var carryAmount: Int = 0,
    var gatherTimer: Float = 0f,
    var flash: Float = 0f,
    var dead: Boolean = false
) {
    val radius: Float get() = kind.radius
    val maxHp: Float get() = kind.hp
}

data class TrainItem(val kind: UnitKind, var remaining: Float)

data class BuildingEntity(
    val id: Int,
    val faction: FactionId,
    val kind: BuildingKind,
    var x: Float,
    var y: Float,
    var hp: Float = kind.hp,
    var build: Float = 1f,
    val queue: MutableList<TrainItem> = mutableListOf(),
    var rally: Vec2? = if (kind.trains.isNotEmpty()) Vec2(x, y + 190f) else null,
    var spriteVariant: Int = 1,
    var cooldown: Float = 0f,
    var flash: Float = 0f,
    var dead: Boolean = false
) {
    val maxHp: Float get() = kind.hp
    val footprintLeft: Float get() = x - kind.placeW * .5f
    val footprintTop: Float get() = y + kind.placeYOffset - kind.placeH * .5f
    val footprintRight: Float get() = x + kind.placeW * .5f
    val footprintBottom: Float get() = y + kind.placeYOffset + kind.placeH * .5f
}

data class ResourceEntity(
    val id: Int,
    val kind: ResourceKind,
    val sprite: String,
    var x: Float,
    var y: Float,
    var amount: Int,
    val maxAmount: Int,
    val animal: AnimalKind? = null,
    var animalHp: Float = animal?.hp ?: 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var panic: Float = 0f,
    var directionRow: Int = 0,
    var flash: Float = 0f,
    var dead: Boolean = false
) {
    val radius: Float get() = animal?.radius ?: if (kind == ResourceKind.Gold) 21f else 18f
    val depleted: Boolean get() = amount <= 0 || dead
}

data class Projectile(
    val id: Int,
    val faction: FactionId,
    var x: Float,
    var y: Float,
    val target: TargetRef,
    val damage: Float,
    var ttl: Float = 1.6f
)

data class Effect(val kind: String, var x: Float, var y: Float, var ttl: Float, val maxTtl: Float = ttl)

data class Population(val used: Int, val cap: Int) {
    val capped: Boolean get() = used >= cap
}

fun Stock.copyMutable() = Stock(wood, gold, food)

fun hpPercent(hp: Float, maxHp: Float): Float = if (maxHp <= 0f) 0f else (hp / maxHp).coerceIn(0f, 1f)

fun maxPop(baseCap: Int) = max(4, baseCap)
