package com.tinyswords.realmwar.game

/**
 * Static gameplay configuration mirroring js/core/config.js. Keeping this in one file lets the
 * rest of the engine stay tiny: every system reads from here.
 */
object GameConfig {
    const val TILE = 64
    const val MAX_DT = 1f / 24f

    // World presets ---------------------------------------------------------
    enum class WorldSize(val label: String, val width: Int, val height: Int) {
        STANDARD("Standard Realm", 9600, 7200),
        LARGE("Large Realm", 12800, 9600),
        MASSIVE("Massive Realm", 16000, 12000)
    }

    enum class Difficulty(
        val label: String,
        val aiResourceMult: Float,
        val aiAttackDelay: Float,
        val aiSquadMin: Int,
        val aggression: Float
    ) {
        PEACEFUL("Peaceful", .72f, 9999f, 99, .20f),
        EASY("Easy", .84f, 18f, 9, .55f),
        NORMAL("Normal", 1.0f, 10f, 7, 1.0f),
        HARD("Hard", 1.22f, 7f, 6, 1.28f)
    }

    enum class ResourceDensity(val label: String, val mult: Float) {
        SPARSE("Sparse", .72f),
        NORMAL("Normal", 1.0f),
        RICH("Rich", 1.25f),
        ABUNDANT("Abundant", 1.55f)
    }

    enum class GraphicsQuality(val label: String, val densityMult: Float) {
        PERFORMANCE("Performance", .72f),
        BALANCED("Balanced", 1.0f),
        HIGH("High Detail", 1.12f)
    }

    enum class MapStyle(val label: String, val description: String, val basesNorm: Array<FloatArray>) {
        CROSSROADS("Crossroads Kingdom",
            "Balanced mainland lanes with side islands.",
            arrayOf(floatArrayOf(.135f, .155f), floatArrayOf(.865f, .155f), floatArrayOf(.135f, .845f), floatArrayOf(.865f, .845f), floatArrayOf(.50f, .50f))),
        ARCHIPELAGO("Crown Archipelago",
            "Large island starts linked by bridges.",
            arrayOf(floatArrayOf(.14f, .20f), floatArrayOf(.86f, .20f), floatArrayOf(.14f, .80f), floatArrayOf(.86f, .80f), floatArrayOf(.50f, .50f))),
        TWIN_RIVERS("Twin Rivers",
            "Two broad rivers divide expansion routes.",
            arrayOf(floatArrayOf(.14f, .18f), floatArrayOf(.86f, .18f), floatArrayOf(.14f, .82f), floatArrayOf(.86f, .82f), floatArrayOf(.50f, .50f))),
        FOUR_CORNERS("Four Corner War",
            "Fast corner starts and a dangerous center gold basin.",
            arrayOf(floatArrayOf(.12f, .12f), floatArrayOf(.88f, .12f), floatArrayOf(.12f, .88f), floatArrayOf(.88f, .88f), floatArrayOf(.50f, .50f))),
        KING_ROAD("King Road",
            "A long central highway rewards scouting and harassment.",
            arrayOf(floatArrayOf(.16f, .50f), floatArrayOf(.84f, .50f), floatArrayOf(.50f, .16f), floatArrayOf(.50f, .84f), floatArrayOf(.50f, .50f))),
        SPIRAL_ISLES("Spiral Isles",
            "Curving lanes wrap around the center.",
            arrayOf(floatArrayOf(.18f, .24f), floatArrayOf(.82f, .24f), floatArrayOf(.18f, .76f), floatArrayOf(.82f, .76f), floatArrayOf(.50f, .50f))),
        GOLD_RUSH("Gold Rush Basin",
            "Safe wood at home, exposed gold fields, contested middle.",
            arrayOf(floatArrayOf(.16f, .18f), floatArrayOf(.84f, .18f), floatArrayOf(.16f, .82f), floatArrayOf(.84f, .82f), floatArrayOf(.50f, .50f))),
        HIGHLANDS("Highland Lakes",
            "Patchwork plateaus around lakes with many flank paths.",
            arrayOf(floatArrayOf(.18f, .18f), floatArrayOf(.82f, .18f), floatArrayOf(.18f, .82f), floatArrayOf(.82f, .82f), floatArrayOf(.50f, .50f)))
    }

    // Factions --------------------------------------------------------------
    data class FactionDef(
        val id: Int,
        val key: String,
        val name: String,
        val folder: String,
        val color: Long,
        val dark: Long,
        val isDefaultAi: Boolean
    )

    val FACTIONS = listOf(
        FactionDef(0, "blue", "Blue Realm", "Blue", 0xFF61B7D9, 0xFF1F5670, isDefaultAi = false),
        FactionDef(1, "red", "Red Dominion", "Red", 0xFFDB6060, 0xFF78232B, isDefaultAi = true),
        FactionDef(2, "yellow", "Golden Clan", "Yellow", 0xFFE6CA59, 0xFF80651E, isDefaultAi = true),
        FactionDef(3, "purple", "Violet Order", "Purple", 0xFFB071DF, 0xFF4A246E, isDefaultAi = true),
        FactionDef(4, "black", "Iron Pact", "Black", 0xFFAEB3BD, 0xFF30353D, isDefaultAi = true)
    )

    // Buildings -------------------------------------------------------------
    data class BuildingDef(
        val type: String,
        val label: String,
        val file: String,
        val w: Int,
        val h: Int,
        val placeW: Int,
        val placeH: Int,
        val placeYOffset: Int,
        val hp: Int,
        val popProvided: Int,
        val cost: Cost,
        val time: Float,
        val trains: List<String>,
        val tower: Boolean = false,
        val range: Float = 0f,
        val builtInArcher: Boolean = false
    )

    data class Cost(val wood: Int, val gold: Int, val food: Int = 0)

    val BUILDINGS = mapOf(
        "castle" to BuildingDef("castle", "Castle", "Castle.png", 180, 132, 152, 58, 38, 1200, 12, Cost(280, 160), 32f, listOf("worker", "warrior")),
        "house" to BuildingDef("house", "House", "House1.png", 84, 74, 66, 38, 24, 260, 8, Cost(70, 15), 12f, emptyList()),
        "barracks" to BuildingDef("barracks", "Barracks", "Barracks.png", 106, 90, 84, 46, 28, 520, 0, Cost(145, 85), 22f, listOf("warrior", "lancer")),
        "archery" to BuildingDef("archery", "Archery", "Archery.png", 106, 90, 84, 46, 28, 440, 0, Cost(120, 95), 20f, listOf("archer")),
        "tower" to BuildingDef("tower", "Tower", "Tower.png", 60, 96, 42, 38, 30, 620, 0, Cost(110, 115), 20f, emptyList(), tower = true, range = 360f, builtInArcher = true),
        "monastery" to BuildingDef("monastery", "Monastery", "Monastery.png", 102, 106, 70, 44, 34, 420, 0, Cost(120, 165), 24f, listOf("monk"))
    )

    // Units -----------------------------------------------------------------
    enum class UnitRole { WORKER, MELEE, RANGED, HEALER }

    data class UnitDef(
        val type: String,
        val label: String,
        val role: UnitRole,
        val hp: Int,
        val speed: Float,
        val range: Float,
        val damage: Int,
        val cooldown: Float,
        val cost: Cost,
        val buildTime: Float,
        val pop: Int,
        val radius: Float
    )

    val UNITS = mapOf(
        "worker" to UnitDef("worker", "Worker", UnitRole.WORKER, 55, 96f, 22f, 5, .65f, Cost(0, 35, 1), 8f, 1, 12f),
        "warrior" to UnitDef("warrior", "Warrior", UnitRole.MELEE, 95, 78f, 28f, 15, .78f, Cost(0, 65, 1), 10f, 1, 13f),
        "archer" to UnitDef("archer", "Archer", UnitRole.RANGED, 62, 74f, 290f, 12, 1.18f, Cost(40, 70, 1), 12f, 1, 12f),
        "lancer" to UnitDef("lancer", "Lancer", UnitRole.MELEE, 135, 88f, 44f, 24, 1.05f, Cost(55, 95, 2), 16f, 2, 18f),
        "monk" to UnitDef("monk", "Monk", UnitRole.HEALER, 64, 70f, 215f, -16, 1.1f, Cost(25, 110, 1), 14f, 1, 12f)
    )

    // Hunt animal definitions ----------------------------------------------
    data class HuntAnimalDef(
        val key: String,
        val label: String,
        val weight: Float,
        val hp: Int,
        val foodYield: Int,
        val radius: Float,
        val walkSpeed: Float,
        val runSpeed: Float,
        val retaliation: Int = 0
    )

    val HUNT_ANIMALS = listOf(
        HuntAnimalDef("deer", "Deer", 1.05f, 42, 24, 13f, 19f, 70f),
        HuntAnimalDef("boar", "Boar", .82f, 54, 28, 14f, 17f, 60f, retaliation = 4),
        HuntAnimalDef("hare", "Hare", 1.38f, 18, 12, 10f, 24f, 84f),
        HuntAnimalDef("fox", "Fox", .72f, 26, 16, 12f, 22f, 76f),
        HuntAnimalDef("grouse", "Black Grouse", .78f, 20, 14, 11f, 20f, 72f)
    )

    // Resource gather info -------------------------------------------------
    object Gather {
        const val WOOD_PER_TRIP = 8
        const val GOLD_PER_TRIP = 8
        const val FOOD_PER_HIT = 4
        const val GATHER_TIME = 1.4f
        const val DEPOSIT_TIME = 0.4f
    }
}
