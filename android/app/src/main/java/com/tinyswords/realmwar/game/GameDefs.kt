package com.tinyswords.realmwar.game

import androidx.compose.ui.graphics.Color

/**
 * Static, immutable game-design data ported from the web build's `config.js`.
 * Mutable per-game state lives in [com.tinyswords.realmwar.game.GameState].
 */

const val TILE_PX = 64
const val MAX_DT = 1f / 24f

enum class FactionKey { BLUE, RED, YELLOW, PURPLE, BLACK }

data class FactionDef(
    val id: Int,
    val key: FactionKey,
    val displayName: String,
    val folder: String,
    val color: Color,
    val dark: Color,
)

val FACTIONS: List<FactionDef> = listOf(
    FactionDef(0, FactionKey.BLUE, "Blue Realm", "Blue", Color(0xFF61B7D9), Color(0xFF1F5670)),
    FactionDef(1, FactionKey.RED, "Red Dominion", "Red", Color(0xFFDB6060), Color(0xFF78232B)),
    FactionDef(2, FactionKey.YELLOW, "Golden Clan", "Yellow", Color(0xFFE6CA59), Color(0xFF80651E)),
    FactionDef(3, FactionKey.PURPLE, "Violet Order", "Purple", Color(0xFFB071DF), Color(0xFF4A246E)),
    FactionDef(4, FactionKey.BLACK, "Iron Pact", "Black", Color(0xFFAEB3BD), Color(0xFF30353D)),
)

enum class WorldSize(val label: String, val width: Int, val height: Int, val areaScale: Float) {
    STANDARD("Standard Realm", 12_400, 9_000, 1.00f),
    LARGE("Large Realm", 16_000, 11_200, 1.45f),
    MASSIVE("Massive Realm", 20_480, 14_400, 2.65f);
}

enum class MapStyle(val label: String, val description: String, val bases: List<Pair<Float, Float>>) {
    CROSSROADS(
        "Crossroads Kingdom",
        "Balanced mainland lanes with side islands, safe openings, and contested center fields.",
        listOf(0.135f to 0.155f, 0.865f to 0.155f, 0.135f to 0.845f, 0.865f to 0.845f, 0.50f to 0.50f),
    ),
    ARCHIPELAGO(
        "Crown Archipelago",
        "Large island starts linked by bridges, rich shoreline pockets, and risky center crossings.",
        listOf(0.14f to 0.20f, 0.86f to 0.20f, 0.14f to 0.80f, 0.86f to 0.80f, 0.50f to 0.50f),
    ),
    TWIN_RIVERS(
        "Twin Rivers",
        "Two broad rivers divide expansion routes; bridges become natural siege objectives.",
        listOf(0.14f to 0.18f, 0.86f to 0.18f, 0.14f to 0.82f, 0.86f to 0.82f, 0.50f to 0.50f),
    ),
    FOUR_CORNERS(
        "Four Corner War",
        "Fast corner starts, open side lanes, and a dangerous center gold basin.",
        listOf(0.12f to 0.12f, 0.88f to 0.12f, 0.12f to 0.88f, 0.88f to 0.88f, 0.50f to 0.50f),
    ),
    KING_ROAD(
        "King Road",
        "A long central highway rewards scouting, harassment, and forward towers.",
        listOf(0.16f to 0.50f, 0.84f to 0.50f, 0.50f to 0.16f, 0.50f to 0.84f, 0.50f to 0.50f),
    ),
    SPIRAL(
        "Spiral Isles",
        "Curving lanes wrap around the center, creating ambush turns and layered defenses.",
        listOf(0.18f to 0.24f, 0.82f to 0.24f, 0.18f to 0.76f, 0.82f to 0.76f, 0.50f to 0.50f),
    ),
    GOLD_RUSH(
        "Gold Rush Basin",
        "Safe wood at home, exposed gold fields, and a wealthy middle that forces conflict.",
        listOf(0.16f to 0.18f, 0.84f to 0.18f, 0.16f to 0.82f, 0.84f to 0.82f, 0.50f to 0.50f),
    ),
    HIGHLANDS(
        "Highland Lakes",
        "Patchwork grass plateaus around lakes with many short attack angles and flank paths.",
        listOf(0.18f to 0.18f, 0.82f to 0.18f, 0.18f to 0.82f, 0.82f to 0.82f, 0.50f to 0.50f),
    );
}

enum class Difficulty(
    val label: String,
    val aiResourceMult: Float,
    val aiAttackDelay: Float,
    val aiSquadMin: Int,
    val aggression: Float,
) {
    PEACEFUL("Peaceful", 0.72f, 9999f, 99, 0.20f),
    EASY("Easy", 0.84f, 18f, 9, 0.55f),
    NORMAL("Normal", 1.00f, 10f, 7, 1.00f),
    HARD("Hard", 1.22f, 7f, 6, 1.28f);
}

enum class ResourceDensity(val label: String, val multiplier: Float) {
    SPARSE("Sparse", 0.72f),
    NORMAL("Normal", 1.00f),
    RICH("Rich", 1.25f),
    ABUNDANT("Abundant", 1.55f);
}

enum class GraphicsTier(val label: String, val densityMult: Float) {
    PERFORMANCE("Performance", 0.72f),
    BALANCED("Balanced", 1.00f),
    HIGH("High Detail", 1.12f);
}

/** Player-tunable settings carried with each saved world. */
data class WorldSettings(
    val size: WorldSize = WorldSize.LARGE,
    val mapStyle: MapStyle = MapStyle.CROSSROADS,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val resourceDensity: ResourceDensity = ResourceDensity.RICH,
    val rivals: Int = 4,
    val seed: String = "",
    val autosave: Boolean = true,
    val graphics: GraphicsTier = GraphicsTier.BALANCED,
)

/** A bag of (wood, gold, food) ints — used both for stockpiles and costs. */
data class Stockpile(var wood: Int = 0, var gold: Int = 0, var food: Int = 0) {
    fun copyOf() = Stockpile(wood, gold, food)
    fun add(other: Stockpile): Stockpile = apply { wood += other.wood; gold += other.gold; food += other.food }
    fun canAfford(cost: Stockpile): Boolean = wood >= cost.wood && gold >= cost.gold && food >= cost.food
    fun pay(cost: Stockpile): Boolean {
        if (!canAfford(cost)) return false
        wood -= cost.wood; gold -= cost.gold; food -= cost.food
        return true
    }
}

enum class BuildingType(val displayName: String, val key: Char) {
    CASTLE("Castle", 'C'),
    HOUSE("House", 'H'),
    BARRACKS("Barracks", 'R'),
    ARCHERY("Archery", 'A'),
    TOWER("Tower", 'T'),
    MONASTERY("Monastery", 'M'),
}

data class BuildingDef(
    val type: BuildingType,
    val file: String,
    val scale: Float,
    val w: Int,
    val h: Int,
    val hp: Int,
    val popProvided: Int,
    val cost: Stockpile,
    val buildTimeSec: Float,
    val trains: List<UnitType>,
    val tower: Boolean = false,
    val range: Float = 0f,
    val placeW: Int,
    val placeH: Int,
    val placeYOffset: Int,
)

enum class UnitType(val displayName: String, val role: UnitRole, val hotkey: Char) {
    WORKER("Worker", UnitRole.WORKER, '1'),
    WARRIOR("Warrior", UnitRole.MELEE, '2'),
    ARCHER("Archer", UnitRole.RANGED, '3'),
    LANCER("Lancer", UnitRole.MELEE, '4'),
    MONK("Monk", UnitRole.HEALER, '5'),
}

enum class UnitRole { WORKER, MELEE, RANGED, HEALER }

data class UnitDef(
    val type: UnitType,
    val maxHp: Int,
    val speedPx: Float,
    val rangePx: Float,
    val damage: Int,
    val cooldownSec: Float,
    val cost: Stockpile,
    val buildTimeSec: Float,
    val popCost: Int,
    val radius: Int,
)

/** Master unit table, ported faithfully from the web `UNITS` map. */
val UNIT_DEFS: Map<UnitType, UnitDef> = mapOf(
    UnitType.WORKER to UnitDef(UnitType.WORKER, 55, 96f, 22f, 5, 0.65f, Stockpile(0, 35, 1), 8f, 1, 12),
    UnitType.WARRIOR to UnitDef(UnitType.WARRIOR, 95, 78f, 28f, 15, 0.78f, Stockpile(0, 65, 1), 10f, 1, 13),
    UnitType.ARCHER to UnitDef(UnitType.ARCHER, 62, 74f, 290f, 12, 1.18f, Stockpile(40, 70, 1), 12f, 1, 12),
    UnitType.LANCER to UnitDef(UnitType.LANCER, 135, 88f, 44f, 24, 1.05f, Stockpile(55, 95, 2), 16f, 2, 18),
    UnitType.MONK to UnitDef(UnitType.MONK, 64, 70f, 215f, -16, 1.10f, Stockpile(25, 110, 1), 14f, 1, 12),
)

val BUILDING_DEFS: Map<BuildingType, BuildingDef> = mapOf(
    BuildingType.CASTLE to BuildingDef(
        BuildingType.CASTLE, "Castle.png", 0.53f, 180, 132, 1200, 12,
        Stockpile(280, 160, 0), 32f, listOf(UnitType.WORKER, UnitType.WARRIOR), placeW = 152, placeH = 58, placeYOffset = 38,
    ),
    BuildingType.HOUSE to BuildingDef(
        BuildingType.HOUSE, "House1.png", 0.56f, 84, 74, 260, 8,
        Stockpile(70, 15, 0), 12f, emptyList(), placeW = 66, placeH = 38, placeYOffset = 24,
    ),
    BuildingType.BARRACKS to BuildingDef(
        BuildingType.BARRACKS, "Barracks.png", 0.50f, 106, 90, 520, 0,
        Stockpile(145, 85, 0), 22f, listOf(UnitType.WARRIOR, UnitType.LANCER), placeW = 84, placeH = 46, placeYOffset = 28,
    ),
    BuildingType.ARCHERY to BuildingDef(
        BuildingType.ARCHERY, "Archery.png", 0.50f, 106, 90, 440, 0,
        Stockpile(120, 95, 0), 20f, listOf(UnitType.ARCHER), placeW = 84, placeH = 46, placeYOffset = 28,
    ),
    BuildingType.TOWER to BuildingDef(
        BuildingType.TOWER, "Tower.png", 0.54f, 60, 96, 620, 0,
        Stockpile(110, 115, 0), 20f, emptyList(), tower = true, range = 360f,
        placeW = 42, placeH = 38, placeYOffset = 30,
    ),
    BuildingType.MONASTERY to BuildingDef(
        BuildingType.MONASTERY, "Monastery.png", 0.46f, 102, 106, 420, 0,
        Stockpile(120, 165, 0), 24f, listOf(UnitType.MONK), placeW = 70, placeH = 44, placeYOffset = 34,
    ),
)

enum class ResourceType { WOOD, GOLD, FOOD }

object Costs {
    val BUILDING: Map<BuildingType, Stockpile> = BUILDING_DEFS.mapValues { it.value.cost }
    val UNIT: Map<UnitType, Stockpile> = UNIT_DEFS.mapValues { it.value.cost }
}

object Pop {
    /** A starting castle provides this much population by default. */
    fun starting(): Int = BUILDING_DEFS[BuildingType.CASTLE]!!.popProvided
}
