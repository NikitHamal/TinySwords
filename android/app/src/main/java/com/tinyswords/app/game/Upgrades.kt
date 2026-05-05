package com.tinyswords.app.game

import com.tinyswords.app.game.entities.GameBuilding
import com.tinyswords.app.game.entities.GameUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class UpgradeCost(val wood: Int, val gold: Int, val food: Int = 0)

data class BuildingUpgradeSpec(
    val maxLevel: Int,
    val costs: List<UpgradeCost>,
    val hpMult: List<Float> = listOf(1f),
    val popBonus: List<Int> = listOf(0),
    val trainMult: List<Float> = listOf(1f),
    val rangeBonus: List<Float> = listOf(0f),
    val damageBonus: List<Int> = listOf(0),
    val archerCount: List<Int> = listOf(0),
    val workerHpBonus: List<Int> = listOf(0),
    val workerDamageBonus: List<Int> = listOf(0),
    val workerSpeedBonus: List<Float> = listOf(0f),
    val archerHpBonus: List<Int> = listOf(0),
    val archerDamageBonus: List<Int> = listOf(0),
    val archerRangeBonus: List<Float> = listOf(0f)
)

val BUILDING_UPGRADE_SPECS = mapOf(
    "tower" to BuildingUpgradeSpec(
        maxLevel = 2,
        costs = listOf(UpgradeCost(120, 135)),
        hpMult = listOf(1.00f, 2.05f),
        rangeBonus = listOf(0f, 60f),
        damageBonus = listOf(0, 4),
        archerCount = listOf(1, 2)
    ),
    "castle" to BuildingUpgradeSpec(
        maxLevel = 4,
        costs = listOf(UpgradeCost(180, 140), UpgradeCost(260, 240), UpgradeCost(360, 340)),
        hpMult = listOf(1.00f, 1.22f, 1.48f, 1.78f),
        popBonus = listOf(0, 4, 8, 12),
        trainMult = listOf(1.00f, 0.90f, 0.80f, 0.70f),
        rangeBonus = listOf(300f, 335f, 370f, 405f),
        damageBonus = listOf(0, 2, 5, 8),
        archerCount = listOf(1, 2, 3, 3)
    ),
    "house" to BuildingUpgradeSpec(
        maxLevel = 2,
        costs = listOf(UpgradeCost(95, 55)),
        hpMult = listOf(1.00f, 1.70f),
        popBonus = listOf(0, 6),
        workerHpBonus = listOf(0, 14),
        workerDamageBonus = listOf(0, 2),
        workerSpeedBonus = listOf(0f, 8f)
    ),
    "barracks" to BuildingUpgradeSpec(
        maxLevel = 2,
        costs = listOf(UpgradeCost(150, 120)),
        hpMult = listOf(1.00f, 1.40f),
        trainMult = listOf(1.00f, 0.78f)
    ),
    "archery" to BuildingUpgradeSpec(
        maxLevel = 2,
        costs = listOf(UpgradeCost(145, 135)),
        hpMult = listOf(1.00f, 1.35f),
        trainMult = listOf(1.00f, 0.78f),
        archerHpBonus = listOf(0, 16),
        archerDamageBonus = listOf(0, 4),
        archerRangeBonus = listOf(0f, 38f)
    ),
    "monastery" to BuildingUpgradeSpec(
        maxLevel = 2,
        costs = listOf(UpgradeCost(135, 145)),
        hpMult = listOf(1.00f, 1.35f),
        trainMult = listOf(1.00f, 0.82f)
    )
)

fun upgradeSpec(type: String): BuildingUpgradeSpec? = BUILDING_UPGRADE_SPECS[type]
fun buildingUpgradeMaxLevel(type: String): Int = upgradeSpec(type)?.maxLevel ?: 1
fun upgradeLevel(building: GameBuilding): Int = building.level.coerceIn(1, buildingUpgradeMaxLevel(building.type))
fun upgradeCostFor(building: GameBuilding): UpgradeCost? {
    val spec = upgradeSpec(building.type) ?: return null
    val currentLevel = upgradeLevel(building)
    if (currentLevel >= spec.maxLevel) return null
    return spec.costs.getOrNull(currentLevel - 1)
}

private fun <T> levelValue(values: List<T>, level: Int, fallback: T): T {
    if (values.isEmpty()) return fallback
    val index = (level - 1).coerceIn(0, values.lastIndex)
    return values[index]
}

fun buildingMaxHpFor(type: String, level: Int = 1): Int {
    val base = BUILDINGS[type]?.hp ?: 100
    val mult = levelValue(upgradeSpec(type)?.hpMult ?: listOf(1f), level, 1f)
    return max(1, (base * mult).roundToInt())
}

fun normalizeBuildingStats(building: GameBuilding, preserveRatio: Boolean = true) {
    val oldMax = max(1, building.maxHp)
    val oldHp = building.hp.coerceIn(0, oldMax)
    building.level = upgradeLevel(building)
    val nextMax = buildingMaxHpFor(building.type, building.level)
    val wasStructurallyFull = building.buildProgress >= 1f && oldHp >= oldMax - 1
    building.maxHp = nextMax
    building.hp = if (preserveRatio) {
        val ratio = oldHp.toFloat() / oldMax.toFloat()
        (nextMax * ratio).roundToInt().coerceIn(if (building.buildProgress < 1f) 0 else 1, nextMax)
    } else if (wasStructurallyFull && nextMax > oldMax) {
        nextMax
    } else {
        oldHp.coerceIn(0, nextMax)
    }
}

fun buildingPopulationCapacity(building: GameBuilding): Int {
    if (building.dead || building.buildProgress < 1f) return 0
    val def = BUILDINGS[building.type] ?: return 0
    val bonus = levelValue(upgradeSpec(building.type)?.popBonus ?: listOf(0), upgradeLevel(building), 0)
    return def.pop + bonus
}

fun buildingTrainTime(building: GameBuilding, unitType: String): Float {
    val base = UNITS[unitType]?.trainTime ?: 1f
    val mult = levelValue(upgradeSpec(building.type)?.trainMult ?: listOf(1f), upgradeLevel(building), 1f)
    return max(1f, base * mult)
}

fun defensiveArcherCount(building: GameBuilding): Int {
    if (building.dead || building.buildProgress < 1f) return 0
    val fallback = if (BUILDINGS[building.type]?.builtInArcher == true) 1 else 0
    return levelValue(upgradeSpec(building.type)?.archerCount ?: listOf(fallback), upgradeLevel(building), fallback)
}

fun defensiveBuildingRange(building: GameBuilding): Float {
    if (defensiveArcherCount(building) <= 0) return 0f
    val rangeBonus = levelValue(upgradeSpec(building.type)?.rangeBonus ?: listOf(0f), upgradeLevel(building), 0f)
    return if (building.type == "tower") (BUILDINGS["tower"]?.towerRange ?: 0f) + rangeBonus else rangeBonus
}

fun defensiveBuildingDamage(building: GameBuilding): Int {
    val base = UNITS["archer"]?.damage ?: 12
    val bonus = levelValue(upgradeSpec(building.type)?.damageBonus ?: listOf(0), upgradeLevel(building), 0)
    return base + bonus
}

fun defensiveBuildingCooldown(building: GameBuilding): Float {
    val level = upgradeLevel(building)
    val base = if (building.type == "tower") 1.18f * 0.92f else 1.18f * 1.08f
    val levelHaste = if (building.type == "castle") max(0, level - 1) * 0.04f else 0f
    return max(0.72f, base - levelHaste)
}

data class UnitUpgradeBonuses(
    val workerHp: Int = 0,
    val workerDamage: Int = 0,
    val workerSpeed: Float = 0f,
    val archerHp: Int = 0,
    val archerDamage: Int = 0,
    val archerRange: Float = 0f
)

fun factionUnitUpgradeBonuses(state: GameState, factionId: Int): UnitUpgradeBonuses {
    var workerHp = 0
    var workerDamage = 0
    var workerSpeed = 0f
    var archerHp = 0
    var archerDamage = 0
    var archerRange = 0f
    for (b in state.buildings) {
        if (b.dead || b.faction != factionId || b.buildProgress < 1f) continue
        val spec = upgradeSpec(b.type) ?: continue
        val level = upgradeLevel(b)
        workerHp = max(workerHp, levelValue(spec.workerHpBonus, level, 0))
        workerDamage = max(workerDamage, levelValue(spec.workerDamageBonus, level, 0))
        workerSpeed = max(workerSpeed, levelValue(spec.workerSpeedBonus, level, 0f))
        archerHp = max(archerHp, levelValue(spec.archerHpBonus, level, 0))
        archerDamage = max(archerDamage, levelValue(spec.archerDamageBonus, level, 0))
        archerRange = max(archerRange, levelValue(spec.archerRangeBonus, level, 0f))
    }
    return UnitUpgradeBonuses(workerHp, workerDamage, workerSpeed, archerHp, archerDamage, archerRange)
}

fun applyFactionUnitUpgrades(state: GameState, unit: GameUnit, preserveRatio: Boolean = true) {
    val def = UNITS[unit.type] ?: return
    val oldMax = max(1, unit.maxHp)
    val oldHp = unit.hp.coerceIn(0, oldMax)
    val bonus = factionUnitUpgradeBonuses(state, unit.faction)
    var nextMax = def.hp
    var nextDamage = def.damage
    var nextSpeed = def.speed
    var nextRange = def.range
    when (unit.type) {
        "worker" -> {
            nextMax += bonus.workerHp
            nextDamage += bonus.workerDamage
            nextSpeed += bonus.workerSpeed
        }
        "archer" -> {
            nextMax += bonus.archerHp
            nextDamage += bonus.archerDamage
            nextRange += bonus.archerRange
        }
    }
    unit.maxHp = nextMax
    unit.damage = nextDamage
    unit.speed = nextSpeed
    unit.range = nextRange
    unit.maxCd = def.cd
    unit.hp = if (preserveRatio) {
        val ratio = oldHp.toFloat() / oldMax.toFloat()
        (nextMax * ratio).roundToInt().coerceIn(1, nextMax)
    } else {
        oldHp.coerceIn(1, nextMax)
    }
}

fun applyFactionUnitUpgradesToAll(state: GameState, factionId: Int) {
    for (unit in state.units) if (!unit.dead && unit.faction == factionId) applyFactionUnitUpgrades(state, unit)
}
