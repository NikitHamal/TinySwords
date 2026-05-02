package com.tinyswords.game

/**
 * Authoritative game constants. Mirrors the web game's `js/core/config.js` so that the same
 * tuning values drive both ports — change values here, they ripple through simulation, AI,
 * and rendering.
 */
object C {
    // World scale.
    const val TILE = 64
    const val PATH_CELL = 32

    // Default world dimensions (single-medium preset; the web game has 3 sizes).
    const val WORLD_W = 6400
    const val WORLD_H = 4800

    // Camera zoom range.
    const val ZOOM_MIN = 0.45f
    const val ZOOM_MAX = 1.6f

    // Sim cap to keep physics stable when the renderer hitches.
    const val MAX_DT = 1f / 24f

    // Faction palette: id 0 is the player, the rest are AI rivals (red/yellow/purple/black).
    val FACTION_COLORS = intArrayOf(
        0xFF4f8ad6.toInt(), // player blue
        0xFFc8504a.toInt(), // red
        0xFFd9b94a.toInt(), // yellow
        0xFF8a4ab8.toInt(), // purple
        0xFF323036.toInt()  // black/neutral
    )
    val FACTION_NAMES = arrayOf("Blue", "Red", "Yellow", "Purple", "Black")

    // Starting resources per faction.
    const val START_WOOD = 220
    const val START_GOLD = 220
    const val START_FOOD = 8
    const val START_POP_CAP = 12 // first castle adds this much
}

enum class UnitType(
    val display: String,
    val hp: Int,
    val speed: Float,
    val range: Float,
    val damage: Int,
    val cooldown: Float,
    val costWood: Int,
    val costGold: Int,
    val costFood: Int,
    val pop: Int,
    val radius: Float,
    val ranged: Boolean = false,
    val healer: Boolean = false
) {
    WORKER ("Worker",  55,  96f,  22f,   5, 0.65f,  0,  35, 1, 1, 12f),
    WARRIOR("Warrior", 95,  78f,  28f,  15, 0.78f,  0,  65, 1, 1, 13f),
    ARCHER ("Archer",  62,  74f, 290f,  12, 1.18f, 40,  70, 1, 1, 12f, ranged = true),
    LANCER ("Lancer", 135,  88f,  44f,  24, 1.05f, 55,  95, 1, 2, 18f),
    MONK   ("Monk",    64,  70f, 215f,  16, 1.10f, 25, 110, 1, 1, 12f, healer = true);

    companion object { val ALL = values() }
}

enum class BuildingType(
    val display: String,
    val hp: Int,
    val popCap: Int,
    val costWood: Int,
    val costGold: Int,
    val buildTime: Float,
    val width: Float,
    val height: Float,
    val trains: Array<UnitType>
) {
    CASTLE   ("Castle",    1200, 12, 280, 160, 32f, 180f, 132f, arrayOf(UnitType.WORKER, UnitType.WARRIOR)),
    HOUSE    ("House",      260,  8,  70,  15, 12f,  84f,  74f, emptyArray()),
    BARRACKS ("Barracks",   520,  0, 145,  85, 22f, 106f,  90f, arrayOf(UnitType.WARRIOR, UnitType.LANCER)),
    ARCHERY  ("Archery",    440,  0, 120,  95, 20f, 106f,  90f, arrayOf(UnitType.ARCHER)),
    TOWER    ("Tower",      620,  0, 110, 115, 20f,  60f,  96f, emptyArray()),
    MONASTERY("Monastery",  420,  0, 120, 165, 24f, 102f, 106f, arrayOf(UnitType.MONK));
    companion object { val ALL = values() }
}

enum class ResourceKind(val display: String) {
    WOOD("Wood"),
    GOLD("Gold"),
    FOOD("Food")
}

/** Issued orders. The simulator interprets these against [Unit.target] / [Unit.goalX]. */
enum class Order { IDLE, MOVE, ATTACK, ATTACK_MOVE, HARVEST, RETURN_RESOURCE, BUILD, REPAIR, HEAL, FLEE }
