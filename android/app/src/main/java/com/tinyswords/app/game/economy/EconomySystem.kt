package com.tinyswords.app.game.economy

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import kotlin.math.*

class EconomySystem(private val state: GameState) {

    fun updateBuildings(dt: Float) {
        for (b in state.buildings) {
            if (b.dead) continue

            // Construction progress
            if (b.buildProgress < 1f) {
                // Buildings don't progress on their own; workers must repair them
                continue
            }

            // Training queue
            if (b.queue.isNotEmpty()) {
                val slot = b.queue[0]
                slot.progress += dt / slot.trainTime
                if (slot.progress >= 1f) {
                    spawnTrainedUnit(b, slot.unitType)
                    b.queue.removeAt(0)
                }
            }
        }
    }

    fun trainUnit(building: GameBuilding, unitType: String): Boolean {
        val udef = UNITS[unitType] ?: return false
        val bdef = BUILDINGS[building.type] ?: return false
        if (!bdef.trains.contains(unitType)) return false
        if (building.buildProgress < 1f || building.dead) return false

        val faction = state.factions[building.faction]
        if (!faction.canAfford(udef.costWood, udef.costGold, udef.costFood)) return false

        val (used, cap) = state.population(building.faction)
        if (used + queuedPopulation(building.faction) + udef.pop > cap) return false

        faction.pay(udef.costWood, udef.costGold, udef.costFood)
        building.queue.add(TrainSlot(unitType, 0f, udef.trainTime))
        return true
    }

    private fun spawnTrainedUnit(building: GameBuilding, unitType: String) {
        val spawn = findTrainingSpawnPoint(building, unitType)
        val unit = GameUnit.create(unitType, building.faction, spawn.first, spawn.second, state::nextId)
        state.units.add(unit)
        state.rebuildEntityIndex()

        // Units should visibly leave the castle/barracks instead of appearing inside the footprint.
        val exit = if (building.hasRally) {
            Pair(building.rallyX, building.rallyY)
        } else {
            findSpawnExitPoint(building, spawn.first, spawn.second)
        }
        if (exit != null) {
            unit.order = UnitOrder.MOVE
            unit.goalX = exit.first
            unit.goalY = exit.second
            unit.hasGoal = true
        }
    }

    private fun findTrainingSpawnPoint(building: GameBuilding, unitType: String): Pair<Float, Float> {
        val bdef = BUILDINGS[building.type] ?: return Pair(building.x, building.y + 70f)
        val radius = UNITS[unitType]?.radius ?: 12f
        val rallyDx = if (building.hasRally) building.rallyX - building.x else 0f
        val rallyDy = if (building.hasRally) building.rallyY - building.y else 1f
        val rallyLen = sqrt(rallyDx * rallyDx + rallyDy * rallyDy).coerceAtLeast(0.001f)
        val preferred = Pair(rallyDx / rallyLen, rallyDy / rallyLen)
        val directions = listOf(
            preferred,
            Pair(0f, 1f),
            Pair(0.75f, 0.65f),
            Pair(-0.75f, 0.65f),
            Pair(1f, 0f),
            Pair(-1f, 0f),
            Pair(0f, -1f),
            Pair(0.7f, -0.7f),
            Pair(-0.7f, -0.7f)
        ).distinctBy { Pair((it.first * 10f).roundToInt(), (it.second * 10f).roundToInt()) }

        val baseGapX = bdef.placeW / 2f + radius + 18f
        val baseGapY = bdef.placeH / 2f + radius + 22f
        val extraSteps = floatArrayOf(0f, 24f, 48f, 80f, 120f, 168f, 224f)
        for (extra in extraSteps) {
            for ((dx, dy) in directions) {
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val nx = dx / len
                val ny = dy / len
                val x = building.x + nx * (baseGapX + extra)
                val y = building.y + ny * (baseGapY + extra)
                if (isSpawnClear(x, y, radius)) return Pair(x, y)
            }
        }
        return Pair(building.x, building.y + bdef.placeH / 2f + radius + 34f)
    }

    private fun findSpawnExitPoint(building: GameBuilding, spawnX: Float, spawnY: Float): Pair<Float, Float>? {
        val dx = spawnX - building.x
        val dy = spawnY - building.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = dx / len
        val ny = dy / len
        val distances = floatArrayOf(48f, 78f, 116f, 156f)
        for (distAway in distances) {
            val x = spawnX + nx * distAway
            val y = spawnY + ny * distAway
            if (state.isSafeLand(x, y, 18f)) return Pair(x, y)
        }
        return null
    }

    private fun isSpawnClear(x: Float, y: Float, radius: Float): Boolean {
        if (!state.isSafeLand(x, y, radius + 3f)) return false
        for (b in state.buildings) {
            if (b.dead) continue
            val bd = BUILDINGS[b.type] ?: continue
            val pad = radius + 8f
            if (x > b.x - bd.placeW / 2f - pad && x < b.x + bd.placeW / 2f + pad &&
                y > b.y - bd.placeH / 2f - pad && y < b.y + bd.placeH / 2f + pad) {
                return false
            }
        }
        for (r in state.resourceIndex.queryRange(x, y, radius + 44f)) {
            if (!r.dead && !r.depleted && dist2(x, y, r.x, r.y) < (radius + 28f) * (radius + 28f)) {
                return false
            }
        }
        return true
    }

    private fun queuedPopulation(factionId: Int): Int {
        var queued = 0
        for (b in state.buildings) {
            if (b.dead || b.faction != factionId || b.queue.isEmpty()) continue
            for (slot in b.queue) queued += UNITS[slot.unitType]?.pop ?: 0
        }
        return queued
    }

    fun placeBuilding(type: String, factionId: Int, x: Float, y: Float, asFoundation: Boolean = false): GameBuilding? {
        val bdef = BUILDINGS[type] ?: return null
        val faction = state.factions[factionId]

        if (!faction.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)) return null

        // Check placement validity
        if (!canPlaceBuilding(type, x, y)) return null

        faction.pay(bdef.costWood, bdef.costGold, bdef.costFood)

        val building = GameBuilding.create(type, factionId, x, y, state::nextId, built = !asFoundation)
        state.buildings.add(building)

        // Invalidate nav
        state.navVersion++

        return building
    }

    fun canPlaceBuilding(type: String, x: Float, y: Float): Boolean {
        val bdef = BUILDINGS[type] ?: return false

        // Must be on land
        if (!state.isSafeLand(x, y, bdef.placeW / 2)) return false

        // Check overlap with existing buildings
        val pw = bdef.placeW
        val ph = bdef.placeH
        for (b in state.buildings) {
            if (b.dead) continue
            val bd = BUILDINGS[b.type] ?: continue
            if (rectsOverlap(x, y, pw, ph, b.x, b.y, bd.placeW, bd.placeH)) return false
        }

        // Check overlap with resources
        for (r in state.resourceIndex.queryRange(x, y, pw)) {
            if (!r.dead && !r.depleted) {
                val d = dist(x, y, r.x, r.y)
                if (d < pw / 2 + 20f) return false
            }
        }

        return true
    }

    private fun rectsOverlap(ax: Float, ay: Float, aw: Float, ah: Float,
                             bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        val aleft = ax - aw / 2; val aright = ax + aw / 2; val atop = ay - ah / 2; val abottom = ay + ah / 2
        val bleft = bx - bw / 2; val bright = bx + bw / 2; val btop = by - bh / 2; val bbottom = by + bh / 2
        return aleft < bright && aright > bleft && atop < bbottom && abottom > btop
    }

    fun nearestDropoff(factionId: Int, x: Float, y: Float): GameBuilding? {
        var best: GameBuilding? = null
        var bestDist = DROPOFF_RANGE * DROPOFF_RANGE

        for (b in state.buildings) {
            if (b.dead || b.faction != factionId) continue
            if (b.buildProgress < 1f) continue
            if (b.type != "castle" && b.type != "house") continue
            val d = dist2(x, y, b.x, b.y)
            if (d < bestDist) {
                bestDist = d
                best = b
            }
        }
        return best
    }

    fun nearestResource(x: Float, y: Float, type: ResourceType, range: Float = 600f): GameResource? {
        var best: GameResource? = null
        var bestDist = range * range

        for (r in state.resourceIndex.queryRange(x, y, range)) {
            if (r.dead || r.depleted || r.type != type) continue
            val d = dist2(x, y, r.x, r.y)
            if (d < bestDist) {
                bestDist = d
                best = r
            }
        }
        return best
    }

    fun workerRoleCounts(factionId: Int): Map<WorkerRole, Int> {
        val counts = mutableMapOf<WorkerRole, Int>()
        for (role in WorkerRole.values()) counts[role] = 0

        for (u in state.units) {
            if (u.dead || u.faction != factionId || u.type != "worker") continue
            val role = u.workerRole
            counts[role] = (counts[role] ?: 0) + 1
        }
        return counts
    }
}
