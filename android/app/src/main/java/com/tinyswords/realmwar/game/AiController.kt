package com.tinyswords.realmwar.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * AI controller for non-player factions. Mirrors js/systems/simulation.js AI sections: workers
 * gather, town-halls train armies, attack waves muster + push to enemy. Easy to follow because
 * each AI is fully encapsulated and ticked independently.
 */
class AiController(private val world: GameWorld) {

    fun update(dt: Float) {
        val playerCastle = world.buildings.firstOrNull { it.type == "castle" && it.factionId == 0 && !it.dead }
        for (f in world.factions) {
            if (!f.alive || !f.isAi) continue
            updateAi(f, dt, playerCastle)
        }
        // Idle worker brain for the player faction so they harvest after being told once
        ensurePlayerWorkersStayUseful(dt)
    }

    private fun ensurePlayerWorkersStayUseful(dt: Float) {
        val player = world.factions.firstOrNull { it.id == 0 } ?: return
        for (u in world.units) {
            if (u.factionId != player.id || u.dead) continue
            if (u.def.role != GameConfig.UnitRole.WORKER) continue
            if (u.orderTargetResource != null || u.orderTargetUnit != null) continue
            if (u.aiTask == "manual") continue
            if (u.moveTargetX != null) continue
            // Auto-pick nearest live resource within sight
            val res = nearestResource(u.x, u.y, range = 600f)
            if (res != null) {
                u.orderTargetResource = res
            }
        }
    }

    private fun updateAi(f: Faction, dt: Float, playerCastle: Building?) {
        f.aiState.timer += dt
        f.aiState.buildTimer += dt
        f.aiState.attackTimer -= dt

        val ownUnits = world.units.filter { it.factionId == f.id && !it.dead }
        val ownBuildings = world.buildings.filter { it.factionId == f.id && !it.dead && it.built }
        val workerCount = ownUnits.count { it.def.role == GameConfig.UnitRole.WORKER }
        val armyCount = ownUnits.count { it.def.role != GameConfig.UnitRole.WORKER }

        // Workers harvest nearest resources
        for (u in ownUnits) {
            if (u.def.role != GameConfig.UnitRole.WORKER) continue
            if (u.orderTargetResource != null && !u.orderTargetResource!!.dead) continue
            val res = nearestResourceForFaction(f, u, 1400f)
            if (res != null) u.orderTargetResource = res
        }

        // Build economy / military based on need
        if (f.aiState.buildTimer > 4f) {
            f.aiState.buildTimer = 0f
            tryBuild(f, ownBuildings, ownUnits)
        }
        // Train units in available buildings
        for (b in ownBuildings) {
            if (b.production != null) continue
            if (b.def.trains.isEmpty()) continue
            val want = decideTrain(f, b, workerCount, armyCount)
            if (want != null) {
                val ud = GameConfig.UNITS[want] ?: continue
                if (faCanAfford(f, ud.cost)) {
                    payCost(f, ud.cost)
                    b.production = ProductionOrder(want, ud)
                }
            }
        }

        // Attack wave
        val readyForAttack = f.aiState.attackTimer <= 0f && armyCount >= world.settings.difficulty.aiSquadMin
        if (readyForAttack && playerCastle != null) {
            f.aiState.attackTimer = world.settings.difficulty.aiAttackDelay + (Math.random().toFloat() * 6f)
            for (u in ownUnits) {
                if (u.def.role == GameConfig.UnitRole.WORKER) continue
                u.orderTargetBuilding = playerCastle
                u.orderTargetUnit = null
                u.orderTargetResource = null
                u.aggressive = true
            }
        }
    }

    private fun tryBuild(f: Faction, ownBuildings: List<Building>, ownUnits: List<Unit>) {
        // Strategy: build first house if pop usage > 70%, then barracks, archery, then more houses, towers.
        val pop = ownUnits.sumOf { it.def.pop }
        val popCap = ownBuildings.sumOf { it.def.popProvided }
        val have: (String) -> Int = { type -> ownBuildings.count { it.type == type } }
        val priorityList = mutableListOf<String>()
        if (pop > popCap - 4) priorityList += "house"
        if (have("barracks") < 1) priorityList += "barracks"
        if (have("archery") < 1) priorityList += "archery"
        if (have("monastery") < 1) priorityList += "monastery"
        if (have("tower") < 2) priorityList += "tower"
        if (have("house") < 3) priorityList += "house"

        for (type in priorityList) {
            val def = GameConfig.BUILDINGS[type] ?: continue
            if (!faCanAfford(f, def.cost)) continue
            // pick spot near base
            val angle = (Math.random().toFloat() * (Math.PI * 2).toFloat())
            val r = 240f + Math.random().toFloat() * 240f
            val bx = f.basePos[0] + cos(angle) * r
            val by = f.basePos[1] + sin(angle) * r
            if (!world.isLand(bx, by)) continue
            if (overlapsAnyBuilding(bx, by, def)) continue
            payCost(f, def.cost)
            val b = Building(type, def, f.id, bx, by)
            b.built = false
            b.buildProgress = 0.4f // simulated worker assist
            world.buildings.add(b)
            return
        }
    }

    private fun decideTrain(f: Faction, b: Building, workerCount: Int, armyCount: Int): String? {
        val trains = b.def.trains
        if (trains.isEmpty()) return null
        if ("worker" in trains && workerCount < 8) return "worker"
        // Diversify army composition based on current size
        return when {
            "warrior" in trains && armyCount % 3 == 0 -> "warrior"
            "lancer" in trains && armyCount % 4 == 1 -> "lancer"
            "archer" in trains -> "archer"
            "monk" in trains && armyCount > 6 -> "monk"
            else -> trains.first()
        }
    }

    private fun overlapsAnyBuilding(x: Float, y: Float, def: GameConfig.BuildingDef): Boolean {
        val r = (def.placeW + def.placeH).toFloat() * 0.5f + 28f
        for (b in world.buildings) {
            if (hypot(b.x - x, b.y - y) < r + b.radius) return true
        }
        return false
    }

    private fun nearestResource(x: Float, y: Float, range: Float): Resource? {
        var best: Resource? = null
        var bestD = range * range
        for (r in world.resources) {
            if (r.dead || r.amount <= 0) continue
            val d = (r.x - x).let { it * it } + (r.y - y).let { it * it }
            if (d < bestD) { bestD = d; best = r }
        }
        return best
    }

    private fun nearestResourceForFaction(f: Faction, u: Unit, range: Float): Resource? {
        // Bias toward resource type the AI is short on
        val short = listOf(
            Triple("wood", f.resWood, ResourceType.TREE),
            Triple("gold", f.resGold, ResourceType.GOLD),
            Triple("food", f.resFood, ResourceType.FOOD)
        ).minBy { it.second }
        var best: Resource? = null
        var bestD = range * range
        for (r in world.resources) {
            if (r.dead || r.amount <= 0) continue
            val priority = if (r.type == short.third) 0.6f else 1.0f
            val d = ((r.x - u.x).let { it * it } + (r.y - u.y).let { it * it }) * priority
            if (d < bestD) { bestD = d; best = r }
        }
        return best
    }

    private fun faCanAfford(f: Faction, cost: GameConfig.Cost): Boolean =
        f.resWood >= cost.wood && f.resGold >= cost.gold && f.resFood >= cost.food

    private fun payCost(f: Faction, cost: GameConfig.Cost) {
        f.resWood -= cost.wood
        f.resGold -= cost.gold
        f.resFood -= cost.food
    }
}
