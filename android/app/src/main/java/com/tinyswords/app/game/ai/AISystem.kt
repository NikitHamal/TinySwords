package com.tinyswords.app.game.ai

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.game.economy.EconomySystem
import com.tinyswords.app.game.combat.CombatSystem
import com.tinyswords.app.game.pathfinding.Pathfinder
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

    private data class FactionOverview(
        val factionId: Int,
        val buildings: List<GameBuilding>,
        val finishedBuildings: List<GameBuilding>,
        val foundations: List<GameBuilding>,
        val damagedBuildings: List<GameBuilding>,
        val workers: List<GameUnit>,
        val idleWorkers: List<GameUnit>,
        val army: List<GameUnit>,
        val idleArmy: List<GameUnit>,
        val unitCounts: MutableMap<String, Int>,
        val buildingCounts: MutableMap<String, Int>,
        val popUsed: Int,
        val popCap: Int,
        val queuedPop: Int
    )

    fun update(dt: Float) {
        for (f in state.factions) {
            if (!f.alive || f.id == 0) continue
            f.underAttack = max(0f, f.underAttack - dt)
            f.aiState.timer -= dt

            if (f.aiState.timer <= 0f) {
                f.aiState.timer = 0.65f + rng.nextFloat() * 0.65f
                think(f)
            }
        }
    }

    private fun think(f: FactionState) {
        val overview = buildOverview(f.id)
        economyEmergency(f, overview)
        aiBuild(f, overview)
        aiTrain(f, overview)
        aiTactics(f, overview)
        reassignIdleWorkers(f.id, overview)
    }

    private fun buildOverview(factionId: Int): FactionOverview {
        val buildings = ArrayList<GameBuilding>()
        val finishedBuildings = ArrayList<GameBuilding>()
        val foundations = ArrayList<GameBuilding>()
        val damagedBuildings = ArrayList<GameBuilding>()
        val workers = ArrayList<GameUnit>()
        val idleWorkers = ArrayList<GameUnit>()
        val army = ArrayList<GameUnit>()
        val idleArmy = ArrayList<GameUnit>()
        val unitCounts = HashMap<String, Int>()
        val buildingCounts = HashMap<String, Int>()
        var popUsed = 0
        var popCap = 4
        var queuedPop = 0

        for (u in state.units) {
            if (u.dead || u.faction != factionId) continue
            unitCounts[u.type] = (unitCounts[u.type] ?: 0) + 1
            if (!u.garrisoned) popUsed += UNITS[u.type]?.pop ?: 1
            if (u.type == "worker") {
                workers += u
                if (!u.garrisoned && u.order == UnitOrder.IDLE) idleWorkers += u
            } else if (!u.garrisoned) {
                army += u
                if (u.order == UnitOrder.IDLE || u.order == UnitOrder.MOVE || u.order == UnitOrder.ATTACK_MOVE) idleArmy += u
            }
        }

        for (b in state.buildings) {
            if (b.dead || b.faction != factionId) continue
            buildings += b
            buildingCounts[b.type] = (buildingCounts[b.type] ?: 0) + 1
            for (slot in b.queue) queuedPop += UNITS[slot.unitType]?.pop ?: 0
            if (b.buildProgress >= 1f) {
                finishedBuildings += b
                popCap += BUILDINGS[b.type]?.pop ?: 0
                if (b.hp < b.maxHp) damagedBuildings += b
            } else {
                foundations += b
            }
        }

        return FactionOverview(
            factionId = factionId,
            buildings = buildings,
            finishedBuildings = finishedBuildings,
            foundations = foundations,
            damagedBuildings = damagedBuildings,
            workers = workers,
            idleWorkers = idleWorkers,
            army = army,
            idleArmy = idleArmy,
            unitCounts = unitCounts,
            buildingCounts = buildingCounts,
            popUsed = popUsed,
            popCap = max(4, popCap),
            queuedPop = queuedPop
        )
    }

    private fun economyEmergency(f: FactionState, overview: FactionOverview) {
        val mult = state.difficulty.aiResourceMult
        val dt = 0.65f
        f.wood += 1.2f * mult * dt
        f.gold += 0.8f * mult * dt
        f.food += 0.3f * mult * dt
        if (overview.workers.size < 3) {
            f.wood += 12f
            f.gold += 12f
        }
    }

    private fun aiBuild(f: FactionState, overview: FactionOverview) {
        val count: (String) -> Int = { overview.buildingCounts[it] ?: 0 }
        val popUsed = overview.popUsed + overview.queuedPop
        val popCap = overview.popCap
        val ai = f.aiState

        val toBuild: String? = when {
            popCap - popUsed < 5 && count("house") < 8 -> "house"
            count("barracks") == 0 && count("castle") > 0 -> "barracks"
            count("archery") == 0 && count("barracks") > 0 -> "archery"
            count("tower") < 2 + ai.expansion -> "tower"
            count("monastery") == 0 && overview.popUsed > 12 -> "monastery"
            count("barracks") < 2 && overview.popUsed > 24 -> "barracks"
            count("archery") < 2 && overview.popUsed > 28 -> "archery"
            rng.nextFloat() < 0.18f && count("house") < 6 -> "house"
            else -> null
        }

        if (toBuild != null) {
            val bdef = BUILDINGS[toBuild] ?: return
            if (f.canAfford(bdef.costWood, bdef.costGold, bdef.costFood)) {
                val pos = findBuildPosition(f.id, toBuild, overview)
                if (pos != null) {
                    economy.placeBuilding(toBuild, f.id, pos.first, pos.second, asFoundation = true)?.let { foundation ->
                        assignBuildersToFoundation(f.id, foundation, overview)
                    }
                }
            }
        }
    }

    private fun assignBuildersToFoundation(factionId: Int, foundation: GameBuilding, overview: FactionOverview? = null) {
        val workers = (overview?.workers ?: state.units.filter { !it.dead && !it.garrisoned && it.faction == factionId && it.type == "worker" })
            .sortedBy { unit ->
                val idleBias = if (unit.order == UnitOrder.IDLE || unit.workerRole == WorkerRole.BUILD) -800_000f else 0f
                dist2(unit.x, unit.y, foundation.x, foundation.y) + idleBias
            }
            .take(if (foundation.type == "castle") 4 else 2)
        for (worker in workers) {
            worker.order = UnitOrder.REPAIR
            worker.target = foundation
            worker.targetId = foundation.id
            worker.hasGoal = false
            worker.gatherTimer = 0f
            worker.workerRole = WorkerRole.BUILD
            worker.path.clear()
        }
    }

    private fun findBuildPosition(factionId: Int, type: String, overview: FactionOverview? = null): Pair<Float, Float>? {
        val buildings = overview?.buildings ?: state.buildings.filter { it.faction == factionId && !it.dead }
        if (buildings.isEmpty()) return null

        val castle = buildings.firstOrNull { it.type == "castle" } ?: buildings[0]
        val baseX = castle.x
        val baseY = castle.y
        val ai = state.factions[factionId].aiState

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

    private fun aiTrain(f: FactionState, overview: FactionOverview) {
        val buildings = overview.finishedBuildings
        val counts = overview.unitCounts.toMutableMap()
        var projectedPop = overview.popUsed + overview.queuedPop

        for (b in buildings) {
            if (b.queue.size >= 3) continue
            val bdef = BUILDINGS[b.type] ?: continue
            val workers = counts["worker"] ?: 0
            val army = (counts["warrior"] ?: 0) + (counts["archer"] ?: 0) + (counts["lancer"] ?: 0) + (counts["monk"] ?: 0)

            val desired = when (b.type) {
                "castle" -> {
                    val targetWorkers = 8 + army / 5
                    if (workers < targetWorkers && bdef.trains.contains("worker")) "worker"
                    else if (bdef.trains.contains("warrior") && rng.nextFloat() < 0.3f) "warrior" else null
                }
                "barracks" -> if (rng.nextFloat() < 0.35f && bdef.trains.contains("lancer")) "lancer" else if (bdef.trains.contains("warrior")) "warrior" else null
                "archery" -> if (bdef.trains.contains("archer")) "archer" else null
                "monastery" -> if (bdef.trains.contains("monk") && army > 7 && (counts["monk"] ?: 0) < max(1, ceil(army / 8f).toInt())) "monk" else null
                else -> null
            } ?: continue

            val udef = UNITS[desired] ?: continue
            if (projectedPop + udef.pop > overview.popCap) continue
            if (economy.trainUnit(b, desired)) {
                projectedPop += udef.pop
                counts[desired] = (counts[desired] ?: 0) + 1
            }
        }
    }

    private fun aiTactics(f: FactionState, overview: FactionOverview) {
        val ai = f.aiState
        val army = overview.army
        val idleArmy = overview.idleArmy
        val workers = overview.workers

        ai.attackTimer -= 0.65f

        if (f.underAttack > 0f) {
            val threat = findThreatNearBase(f.id)
            if (threat != null && idleArmy.isNotEmpty()) {
                val defenders = idleArmy.sortedBy { dist2(it.x, it.y, threat.x, threat.y) }
                    .take(min(idleArmy.size, if (f.underAttack > 0f) 24 else 14))
                for (u in defenders) {
                    u.order = UnitOrder.ATTACK
                    u.target = threat
                    u.targetId = threat.id
                    u.hasGoal = false
                }
            } else {
                val castle = overview.finishedBuildings.firstOrNull { it.type == "castle" }
                if (castle != null) {
                    for (u in idleArmy) {
                        u.order = UnitOrder.ATTACK_MOVE
                        u.goalX = castle.x
                        u.goalY = castle.y
                        u.hasGoal = true
                    }
                }
            }
            val repairTarget = overview.damagedBuildings.firstOrNull()
            if (repairTarget != null) assignBuildersToFoundation(f.id, repairTarget, overview)
            return
        }

        for (foundation in overview.foundations) {
            assignBuildersToFoundation(f.id, foundation, overview)
        }

        if (ai.attackTimer <= 0f && army.size >= state.difficulty.aiSquadMin) {
            ai.attackTimer = state.difficulty.aiAttackDelay * (0.8f + rng.nextFloat() * 0.4f)
            val target = findWeakestEnemy(f.id)
            if (target != null) {
                ai.squadGoal = PathPoint(target.x, target.y)
                ai.squadMode = "attack"
                val squadSize = min(idleArmy.size, max(state.difficulty.aiSquadMin, 8 + army.size / 2))
                for (u in idleArmy.take(squadSize)) {
                    u.order = UnitOrder.ATTACK_MOVE
                    u.goalX = target.x + (rng.nextFloat() - 0.5f) * 60f
                    u.goalY = target.y + (rng.nextFloat() - 0.5f) * 60f
                    u.hasGoal = true
                    u.attackMove = true
                }
            }
        }
    }

    private fun findThreatNearBase(factionId: Int): GameEntity? {
        val castle = state.buildings.firstOrNull { it.faction == factionId && !it.dead && it.type == "castle" }
        val baseX = castle?.x ?: (state.worldW * 0.5f)
        val baseY = castle?.y ?: (state.worldH * 0.5f)
        var best: GameEntity? = null
        var bestDist = 1350f * 1350f
        for (u in state.units) {
            if (u.dead || u.garrisoned || u.faction == factionId || !state.factions[u.faction].alive) continue
            val d = dist2(baseX, baseY, u.x, u.y)
            if (d < bestDist) {
                bestDist = d
                best = u
            }
        }
        if (best != null) return best
        for (b in state.buildings) {
            if (b.dead || b.faction == factionId || !state.factions[b.faction].alive || b.buildProgress < 1f) continue
            val d = dist2(baseX, baseY, b.x, b.y)
            if (d < bestDist) {
                bestDist = d
                best = b
            }
        }
        return best
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
        for (b in state.buildings) {
            if (b.dead || b.faction != 0) continue
            if (b.hp < weakestHp) {
                weakestHp = b.hp
                weakest = b
            }
        }
        return weakest
    }

    private fun reassignIdleWorkers(factionId: Int, overview: FactionOverview = buildOverview(factionId)) {
        for (w in overview.idleWorkers) {
            when (w.workerRole) {
                WorkerRole.IDLE -> Unit
                WorkerRole.WOOD, WorkerRole.AUTO -> economy.nearestResource(w.x, w.y, ResourceType.TREE)?.let {
                    w.order = UnitOrder.HARVEST
                    w.target = it
                    w.targetId = it.id
                }
                WorkerRole.GOLD -> economy.nearestResource(w.x, w.y, ResourceType.GOLD)?.let {
                    w.order = UnitOrder.HARVEST
                    w.target = it
                    w.targetId = it.id
                }
                WorkerRole.FOOD -> economy.nearestResource(w.x, w.y, ResourceType.FOOD)?.let {
                    w.order = UnitOrder.HARVEST
                    w.target = it
                    w.targetId = it.id
                }
                WorkerRole.BUILD -> overview.foundations.firstOrNull()?.let {
                    w.order = UnitOrder.REPAIR
                    w.target = it
                    w.targetId = it.id
                }
            }
        }
    }
}
