package com.tinyswords.game

import kotlin.math.*
import kotlin.random.Random

/**
 * Lightweight enemy faction AI. Three-stage cycle:
 *   1. Build economy: train workers, place houses/barracks when affordable
 *   2. Train army: warriors and archers when buildings exist
 *   3. Attack: send a squad toward the player's nearest building
 *
 * Mirrors the web game's `simulation.js#updateAI` decision tree without copying its exact
 * code — values rebalanced for the smaller world we ship with.
 */
private val rng = Random.Default

fun enemyAI(game: Game, dt: Float) {
    for (f in game.factions) {
        if (!f.isAI || !f.alive) continue
        f.aiTimer -= dt
        f.aiBuildTimer -= dt
        f.aiAttackTimer -= dt

        val mine = game.buildings.filter { it.faction == f.id && it.alive }
        val castle = mine.firstOrNull { it.type == BuildingType.CASTLE } ?: continue
        val workers = game.units.count { it.faction == f.id && it.type == UnitType.WORKER }
        val army = game.units.filter { it.faction == f.id && it.type != UnitType.WORKER }

        // 1. Train more workers up to a soft cap.
        if (workers < 8 && castle.queue.size < 2 && f.gold >= 35 && f.popUsed < f.popCap) {
            castle.queue += TrainOrder(UnitType.WORKER)
            f.gold -= 35
        }

        // 2. Auto-assign idle workers to harvest the right thing.
        for (u in game.units) {
            if (u.faction != f.id || u.type != UnitType.WORKER) continue
            if (u.order == Order.IDLE && u.carryAmount == 0) {
                val needWood = f.wood < 200
                val needGold = f.gold < 200
                val kind = if (needWood && (!needGold || rng.nextBoolean())) ResourceKind.WOOD
                else if (needGold) ResourceKind.GOLD else ResourceKind.WOOD
                val r = game.nearestResource(u.x, u.y, kind) ?: continue
                u.order = Order.HARVEST; u.assignedResourceId = r.id; u.carrying = r.kind
            }
        }

        // 3. Build a House when popCap is tight.
        if (f.aiBuildTimer <= 0f) {
            f.aiBuildTimer = 14f
            if (f.popUsed + 2 >= f.popCap && f.wood >= 70 && f.gold >= 15) {
                placeAIBuilding(game, f, castle, BuildingType.HOUSE)
            } else if (mine.none { it.type == BuildingType.BARRACKS } && f.wood >= 145 && f.gold >= 85) {
                placeAIBuilding(game, f, castle, BuildingType.BARRACKS)
            } else if (mine.none { it.type == BuildingType.ARCHERY } && army.size >= 2 && f.wood >= 120 && f.gold >= 95) {
                placeAIBuilding(game, f, castle, BuildingType.ARCHERY)
            } else if (mine.count { it.type == BuildingType.TOWER } < 2 && f.wood >= 110 && f.gold >= 115) {
                placeAIBuilding(game, f, castle, BuildingType.TOWER)
            }
        }

        // 4. Train army.
        val barracks = mine.firstOrNull { it.type == BuildingType.BARRACKS && it.buildProgress >= 1f }
        if (barracks != null && barracks.queue.size < 2 && f.gold >= 65 && f.popUsed < f.popCap) {
            barracks.queue += TrainOrder(UnitType.WARRIOR)
            f.gold -= 65
        }
        val archery = mine.firstOrNull { it.type == BuildingType.ARCHERY && it.buildProgress >= 1f }
        if (archery != null && archery.queue.size < 2 && f.wood >= 40 && f.gold >= 70 && f.popUsed < f.popCap) {
            archery.queue += TrainOrder(UnitType.ARCHER)
            f.wood -= 40; f.gold -= 70
        }

        // 5. Attack waves.
        if (f.aiAttackTimer <= 0f && army.size >= 5) {
            f.aiAttackTimer = 38f
            val target = game.buildings.firstOrNull { it.faction == 0 && it.alive } ?: continue
            for (u in army) {
                u.order = Order.ATTACK_MOVE
                u.goalX = target.x; u.goalY = target.y; u.path.clear(); u.targetId = -1
            }
        }
    }
}

private fun placeAIBuilding(game: Game, f: Faction, castle: Building, type: BuildingType): Boolean {
    // Spiral search for a placement spot near the castle.
    for (r in 1..10) {
        repeat(8) {
            val a = rng.nextFloat() * (Math.PI * 2).toFloat()
            val px = castle.x + cos(a) * (r * 90f)
            val py = castle.y + sin(a) * (r * 90f)
            if (!game.world.isLandPx(px, py)) return@repeat
            val overlap = game.buildings.any { b ->
                val dx = b.x - px; val dy = b.y - py
                abs(dx) < (b.w + type.width) * 0.55f && abs(dy) < (b.h + type.height) * 0.55f
            }
            if (overlap) return@repeat
            if (f.wood < type.costWood || f.gold < type.costGold) return false
            f.wood -= type.costWood; f.gold -= type.costGold
            val site = game.createBuilding(type, px, py, f.id, complete = false)
            // Send the nearest idle worker to build it.
            val worker = game.units
                .filter { it.faction == f.id && it.type == UnitType.WORKER }
                .minByOrNull { (it.x - px).let { d -> d * d } + (it.y - py).let { d -> d * d } }
            if (worker != null) {
                worker.order = Order.BUILD; worker.targetId = site.id; worker.path.clear()
            }
            return true
        }
    }
    return false
}
