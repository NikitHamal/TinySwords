package com.tinyswords.app.game.economy

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import kotlin.math.min

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
        val spawnX: Float
        val spawnY: Float

        if (building.hasRally) {
            spawnX = building.x
            spawnY = building.y + 30f
        } else {
            spawnX = building.x + (Math.random().toFloat() - 0.5f) * 30f
            spawnY = building.y + 40f
        }

        val unit = GameUnit.create(unitType, building.faction, spawnX, spawnY, state::nextId)

        // If rally point is set, give move order
        if (building.hasRally) {
            unit.order = UnitOrder.MOVE
            unit.goalX = building.rallyX
            unit.goalY = building.rallyY
            unit.hasGoal = true
        }

        state.units.add(unit)
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

        // Queue navigation/index refresh for the simulation thread. Doing the
        // full path-grid rebuild during the UI tap causes noticeable Android jank.
        state.pathGridDirty = true
        state.spatialRebuildTimer = 0f
        state.rebuildEntityIndex()

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
