package com.tinyswords.app.game.ai

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.game.economy.EconomySystem
import com.tinyswords.app.game.combat.CombatSystem
import com.tinyswords.app.game.pathfinding.Pathfinder
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import kotlin.math.*
import kotlin.random.Random

class AISystem(
    private val state: GameState,
    private val economy: EconomySystem,
    private val combat: CombatSystem,
    private val pathfinder: Pathfinder
) {
    private val rng = Random(42)

    fun update(dt: Float) {
        for (f in state.factions) {
            if (!f.alive || f.id == 0) continue // Skip player
            f.underAttack = max(0f, f.underAttack - dt)
            f.aiState.timer -= dt

            if (f.aiState.timer <= 0f) {
                f.aiState.timer = 0.65f + rng.nextFloat() * 0.65f
                think(f)
            }
        }
    }

    private fun think(f: FactionState) {
        economyEmergency(f)
        aiBuild(f)
        aiTrain(f)
        aiTactics(f)
        reassignIdleWorkers(f.id)
    }

    private fun economyEmergency(f: FactionState) {
        // AI gets resource multiplier bonus
        val mult = state.difficulty.aiResourceMult
        val dt = 0.65f // Average tick interval

        // Passive resource trickle for AI
        f.wood += 1.2f * mult * dt
        f.gold += 0.8f * mult * dt
        f.food += 0.3f * mult * dt
    }

    private fun aiBuild(f: FactionState) {
        val buildings = state.buildings.filter { it.faction == f.id && !it.dead }
        val (popUsed, popCap) = state.population(f.id)

        val hasCastle = buildings.any { it.type == "castle" }
        val houseCount = buildings.count { it.type == "house" }
        val barracksCount = buildings.count { it.type == "barracks" }
        val archeryCount = buildings.count { it.type == "archery" }
        val towerCount = buildings.count { it.type == "tower" }
        val monasteryCount = buildings.count { it.type == "monastery" }

        val ai = f.aiState

        // Priority building list
        val toBuild: String? = when {
            popCap - popUsed < 5 && houseCount < 8 -> "house"
            barracksCount == 0 && hasCastle -> "barracks"
            archeryCount == 0 && barracksCount > 0 -> "archery"
            towerCount < 2 + ai.expansion -> "tower"
            monasteryCount == 0 && popUsed > 12 -> "monastery"
            barracksCount < 2 && popUsed > 24 -> "barracks"
            archeryCount < 2 && popUsed > 28 -> "archery"
            rng.nextFloat() < 0.18f && houseCount < 6 -> "house"
            else -> null
        }

        if (toBuild != null) {
            val bdef = BUILDINGS[toBuild] ?: return
            if (f.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)) {
                val pos = findBuildPosition(f.id, toBuild)
                if (pos != null) {
                    economy.placeBuilding(toBuild, f.id, pos.first, pos.second, asFoundation = false)
                }
            }
        }
    }

    private fun findBuildPosition(factionId: Int, type: String): Pair<Float, Float>? {
        val buildings = state.buildings.filter { it.faction == factionId && !it.dead }
        if (buildings.isEmpty()) return null

        val castle = buildings.find { it.type == "castle" } ?: buildings[0]
        val baseX = castle.x
        val baseY = castle.y
        val ai = state.factions[factionId].aiState

        // Try positions in expanding rings around base
        for (ring in 1..6) {
            val radius = 190f * ring
            repeat(12) {
                val angle = ai.rallyAngle + (it * PI.toFloat() * 2f / 12)
                val px = baseX + cos(angle) * radius
                val py = baseY + sin(angle) * radius

                if (economy.canPlaceBuilding(type, px, py)) {
                    ai.rallyAngle += 0.5f
                    return Pair(px, py)
                }
            }
        }
        return null
    }

    private fun aiTrain(f: FactionState) {
        val buildings = state.buildings.filter { it.faction == f.id && !it.dead && it.buildProgress >= 1f }
        val workers = state.units.count { it.faction == f.id && !it.dead && it.type == "worker" }
        val army = state.units.count { it.faction == f.id && !it.dead && it.type != "worker" }
        val (popUsed, popCap) = state.population(f.id)

        for (b in buildings) {
            if (b.queue.size >= 3) continue
            val bdef = BUILDINGS[b.type] ?: continue

            when (b.type) {
                "castle" -> {
                    // Train workers until we have enough
                    val targetWorkers = 8 + army / 5
                    if (workers < targetWorkers && bdef.trains.contains("worker")) {
                        economy.trainUnit(b, "worker")
                    } else if (bdef.trains.contains("warrior") && rng.nextFloat() < 0.3f) {
                        economy.trainUnit(b, "warrior")
                    }
                }
                "barracks" -> {
                    // 35% lancers, 65% warriors
                    val type = if (rng.nextFloat() < 0.35f && bdef.trains.contains("lancer")) "lancer" else "warrior"
                    if (bdef.trains.contains(type)) economy.trainUnit(b, type)
                }
                "archery" -> {
                    if (bdef.trains.contains("archer")) economy.trainUnit(b, "archer")
                }
                "monastery" -> {
                    if (bdef.trains.contains("monk")) economy.trainUnit(b, "monk")
                }
            }
        }
    }

    private fun aiTactics(f: FactionState) {
        val ai = f.aiState
        val army = state.units.filter { it.faction == f.id && !it.dead && it.type != "worker" && !it.garrisoned }

        ai.attackTimer -= 0.65f

        // Under attack: rally defense
        if (f.underAttack > 0f) {
            val castle = state.buildings.find { it.faction == f.id && !it.dead && it.type == "castle" }
            if (castle != null) {
                for (u in army) {
                    if (u.order == UnitOrder.IDLE) {
                        u.order = UnitOrder.ATTACK_MOVE
                        u.goalX = castle.x
                        u.goalY = castle.y
                        u.hasGoal = true
                    }
                }
            }
            return
        }

        // Attack when ready
        if (ai.attackTimer <= 0f && army.size >= state.difficulty.aiSquadMin) {
            ai.attackTimer = state.difficulty.aiAttackDelay * (0.8f + rng.nextFloat() * 0.4f)

            // Find weakest enemy
            val target = findWeakestEnemy(f.id)
            if (target != null) {
                ai.squadGoal = PathPoint(target.x, target.y)
                ai.squadMode = "attack"

                for (u in army) {
                    u.order = UnitOrder.ATTACK_MOVE
                    u.goalX = target.x + (rng.nextFloat() - 0.5f) * 60f
                    u.goalY = target.y + (rng.nextFloat() - 0.5f) * 60f
                    u.hasGoal = true
                    u.attackMove = true
                }
            }
        }
    }

    private fun findWeakestEnemy(factionId: Int): GameEntity? {
        var weakest: GameBuilding? = null
        var weakestHp = Int.MAX_VALUE

        for (b in state.buildings) {
            if (b.dead || b.faction == factionId || b.faction == 0) continue
            if (!state.factions[b.faction].alive) continue
            if (b.hp < weakestHp) {
                weakestHp = b.hp
                weakest = b
            }
        }

        // Also check player buildings
        for (b in state.buildings) {
            if (b.dead || b.faction != 0) continue
            if (b.hp < weakestHp) {
                weakestHp = b.hp
                weakest = b
            }
        }

        return weakest
    }

    private fun reassignIdleWorkers(factionId: Int) {
        val workers = state.units.filter {
            it.faction == factionId && !it.dead && it.type == "worker" && it.order == UnitOrder.IDLE
        }

        for (w in workers) {
            val role = w.workerRole
            when {
                role == WorkerRole.IDLE -> continue
                role == WorkerRole.WOOD || (role == WorkerRole.AUTO) -> {
                    val tree = economy.nearestResource(w.x, w.y, ResourceType.TREE)
                    if (tree != null) {
                        w.order = UnitOrder.HARVEST
                        w.target = tree
                        w.targetId = tree.id
                    }
                }
                role == WorkerRole.GOLD -> {
                    val gold = economy.nearestResource(w.x, w.y, ResourceType.GOLD)
                    if (gold != null) {
                        w.order = UnitOrder.HARVEST
                        w.target = gold
                        w.targetId = gold.id
                    }
                }
                role == WorkerRole.FOOD -> {
                    val food = economy.nearestResource(w.x, w.y, ResourceType.FOOD)
                    if (food != null) {
                        w.order = UnitOrder.HARVEST
                        w.target = food
                        w.targetId = food.id
                    }
                }
                role == WorkerRole.BUILD -> {
                    val foundation = state.buildings.find {
                        it.faction == factionId && !it.dead && it.buildProgress < 1f
                    }
                    if (foundation != null) {
                        w.order = UnitOrder.REPAIR
                        w.target = foundation
                        w.targetId = foundation.id
                    }
                }
            }
        }
    }
}
