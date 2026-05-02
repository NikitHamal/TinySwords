package com.tinyswords.app.game

import android.graphics.Color

// ── Display ──
const val VIEW_W = 1280f
const val VIEW_H = 720f
const val TILE = 64f
const val SPRITE_BOOST = 1.08f
const val CLOUD_BOOST = 3.0f
const val MAX_DT = 1f / 24f
const val PATH_CELL = 32f

// ── Camera ──
const val CAMERA_MIN_ZOOM = 0.72f
const val CAMERA_MAX_ZOOM = 1.32f
const val CAMERA_ZOOM_SPEED = 8f
const val CAMERA_PAN_SPEED = 520f

// ── Combat ──
const val PROJECTILE_SPEED = 510f
const val PROJECTILE_LIFE = 2.2f
const val PROJECTILE_HIT_DIST = 18f
const val FLASH_DURATION = 0.18f
const val ANIMAL_PANIC_DURATION = 2.0f
const val STUCK_TIMEOUT = 1.8f
const val TRAFFIC_JAM_TIMEOUT = 1.5f

// ── Economy ──
const val AUTOSAVE_INTERVAL = 45f
const val MONASTERY_HEAL_RANGE = 240f
const val MONASTERY_HEAL_AMOUNT = 10f
const val MONASTERY_HEAL_CD = 1.2f
const val DROPOFF_RANGE = 800f

// ── Spatial Buckets ──
const val UNIT_BUCKET_SIZE = 72f
const val RESOURCE_BUCKET_SIZE = 128f
const val BUILDING_BUCKET_SIZE = 192f
const val DECOR_BUCKET_SIZE = 128f

// ── World ──
data class WorldPreset(val width: Float, val height: Float, val areaScale: Float)

val WORLD_PRESETS = mapOf(
    "standard" to WorldPreset(12400f, 9000f, 1.0f),
    "large" to WorldPreset(16000f, 11200f, 1.45f),
    "massive" to WorldPreset(20480f, 14400f, 2.65f)
)

// ── Difficulty ──
data class DifficultyPreset(
    val aiResourceMult: Float,
    val aiAttackDelay: Float,
    val aiSquadMin: Int,
    val aggression: Float
)

val DIFFICULTY_PRESETS = mapOf(
    "peaceful" to DifficultyPreset(0.72f, 9999f, 99, 0.20f),
    "easy" to DifficultyPreset(0.84f, 18f, 9, 0.55f),
    "normal" to DifficultyPreset(1.0f, 10f, 7, 1.0f),
    "hard" to DifficultyPreset(1.22f, 7f, 6, 1.28f)
)

val RESOURCE_DENSITY_PRESETS = mapOf(
    "sparse" to 0.72f,
    "normal" to 1.0f,
    "rich" to 1.25f,
    "abundant" to 1.55f
)

// ── Formation Modes ──
data class FormationMode(val label: String, val spacing: Float)

val FORMATION_MODES = mapOf(
    "line" to FormationMode("Line", 44f),
    "box" to FormationMode("Box", 42f),
    "wedge" to FormationMode("Wedge", 42f),
    "split" to FormationMode("Split", 44f)
)

// ── Units ──
data class UnitDef(
    val label: String,
    val role: String,
    val hp: Int,
    val speed: Float,
    val range: Float,
    val damage: Int,
    val cd: Float,
    val costWood: Int,
    val costGold: Int,
    val costFood: Int,
    val trainTime: Float,
    val pop: Int,
    val fw: Int,
    val fh: Int,
    val scale: Float,
    val radius: Float,
    val drawYOffset: Float = 0f,
    val shadowX: Float = 14f,
    val shadowY: Float = 6f
)

val UNITS = mapOf(
    "worker" to UnitDef("Worker", "worker", 55, 96f, 22f, 5, 0.65f, 0, 35, 1, 8f, 1, 192, 192, 0.34f, 12f),
    "warrior" to UnitDef("Warrior", "melee", 95, 78f, 28f, 15, 0.78f, 0, 65, 1, 10f, 1, 192, 192, 0.35f, 13f),
    "archer" to UnitDef("Archer", "ranged", 62, 74f, 290f, 12, 1.18f, 40, 70, 1, 12f, 1, 192, 192, 0.34f, 12f),
    "lancer" to UnitDef("Lancer", "melee", 135, 88f, 44f, 24, 1.05f, 55, 95, 2, 16f, 2, 320, 320, 0.40f, 18f, 27f, 24f, 8f),
    "monk" to UnitDef("Monk", "healer", 64, 70f, 215f, -16, 1.1f, 25, 110, 1, 14f, 1, 192, 192, 0.34f, 12f)
)

// ── Buildings ──
data class BuildingDef(
    val label: String,
    val scale: Float,
    val w: Float,
    val h: Float,
    val hp: Int,
    val pop: Int,
    val costWood: Int,
    val costGold: Int,
    val costFood: Int,
    val buildTime: Float,
    val trains: List<String>,
    val placeW: Float,
    val placeH: Float,
    val placeYOffset: Float,
    val isTower: Boolean = false,
    val towerRange: Float = 0f,
    val builtInArcher: Boolean = false
)

val BUILDINGS = mapOf(
    "castle" to BuildingDef("Castle", 0.53f, 180f, 132f, 1200, 12, 280, 160, 0, 32f, listOf("worker", "warrior"), 152f, 58f, 38f),
    "house" to BuildingDef("House", 0.56f, 84f, 74f, 260, 8, 70, 15, 0, 12f, emptyList(), 66f, 38f, 24f),
    "barracks" to BuildingDef("Barracks", 0.50f, 106f, 90f, 520, 0, 145, 85, 0, 22f, listOf("warrior", "lancer"), 84f, 46f, 28f),
    "archery" to BuildingDef("Archery", 0.50f, 106f, 90f, 440, 0, 120, 95, 0, 20f, listOf("archer"), 84f, 46f, 28f),
    "tower" to BuildingDef("Tower", 0.54f, 60f, 96f, 62, 0, 110, 115, 0, 20f, emptyList(), 42f, 38f, 30f, true, 360f, true),
    "monastery" to BuildingDef("Monastery", 0.46f, 102f, 106f, 420, 0, 120, 165, 0, 24f, listOf("monk"), 70f, 44f, 34f)
)

// ── Hunt Animals ──
data class AnimalDef(
    val hp: Int,
    val yield: Int,
    val radius: Float,
    val scale: Float,
    val walkSpeedMin: Float,
    val walkSpeedMax: Float,
    val runSpeedMin: Float,
    val runSpeedMax: Float,
    val retaliation: Int = 0,
    val baseline: Float = 28f,
    val shadowW: Float = 14f,
    val shadowH: Float = 4f,
    val fpsIdle: Float = 2.3f,
    val fpsWalk: Float = 6.4f,
    val fpsRun: Float = 9.2f,
    val fpsHurt: Float = 5.5f
)

val HUNT_ANIMALS = mapOf(
    "deer" to AnimalDef(42, 24, 13f, 1.10f, 14f, 25f, 56f, 84f, baseline = 28f, shadowW = 14f, shadowH = 4f, fpsIdle = 2.3f, fpsWalk = 6.4f, fpsRun = 9.2f, fpsHurt = 5.5f),
    "boar" to AnimalDef(54, 28, 14f, 1.04f, 12f, 22f, 48f, 70f, 4, baseline = 28f, shadowW = 14f, shadowH = 4f, fpsIdle = 2.2f, fpsWalk = 6.2f, fpsRun = 8.6f, fpsHurt = 5.4f),
    "hare" to AnimalDef(18, 12, 10f, 0.68f, 18f, 30f, 68f, 96f, baseline = 28f, shadowW = 9f, shadowH = 3f, fpsIdle = 2.8f, fpsWalk = 7.2f, fpsRun = 10.8f, fpsHurt = 6f),
    "fox" to AnimalDef(26, 16, 12f, 0.86f, 16f, 27f, 62f, 90f, baseline = 28f, shadowW = 11f, shadowH = 4f, fpsIdle = 2.5f, fpsWalk = 6.8f, fpsRun = 10.2f, fpsHurt = 6f),
    "grouse" to AnimalDef(20, 14, 11f, 0.58f, 14f, 26f, 58f, 86f, baseline = 28f, shadowW = 8f, shadowH = 3f, fpsIdle = 2.6f, fpsWalk = 6.8f, fpsRun = 9.5f, fpsHurt = 6f)
)

// ── Factions ──
data class FactionDef(
    val id: Int,
    val key: String,
    val name: String,
    val color: Int,
    val dark: Int,
    val isAi: Boolean
)

val FACTIONS = listOf(
    FactionDef(0, "blue", "Blue Realm", Color.parseColor("#61b7d9"), Color.parseColor("#1f5670"), false),
    FactionDef(1, "red", "Red Dominion", Color.parseColor("#db6060"), Color.parseColor("#78232b"), true),
    FactionDef(2, "yellow", "Golden Clan", Color.parseColor("#e6ca59"), Color.parseColor("#80651e"), true),
    FactionDef(3, "purple", "Violet Order", Color.parseColor("#b071df"), Color.parseColor("#4a246e"), true),
    FactionDef(4, "black", "Iron Pact", Color.parseColor("#aeb3bd"), Color.parseColor("#30353d"), true)
)

// ── Map Presets ──
enum class MapStyle(val label: String) {
    CROSSROADS("Crossroads"),
    ARCHIPELAGO("Archipelago"),
    TWINRIVERS("Twin Rivers"),
    FOURCORNERS("Four Corners"),
    KINGROAD("King's Road"),
    SPIRAL("Spiral"),
    GOLDRUSH("Gold Rush"),
    HIGHLANDS("Highlands")
}
