package com.tinyswords.app.engine

import com.tinyswords.app.game.*
import com.tinyswords.app.game.ai.AISystem
import com.tinyswords.app.game.combat.CombatSystem
import com.tinyswords.app.game.economy.EconomySystem
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.game.pathfinding.Pathfinder
import com.tinyswords.app.game.world.WorldGenerator
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import com.tinyswords.app.util.formationOffset
import kotlin.math.*

class GameSimulation(val state: GameState) {
    val pathfinder = Pathfinder(state)
    val combat = CombatSystem(state)
    val economy = EconomySystem(state)
    val ai = AISystem(state, economy, combat, pathfinder)
    val worldGenerator = WorldGenerator(state)

    fun initialize() {
        worldGenerator.generate()
    }

    fun update(dt: Float) {
        if (state.paused || state.gameOver) return

        val clampedDt = dt.coerceAtMost(MAX_DT)
        state.time += clampedDt
        state.camera.zoom += (state.camera.targetZoom - state.camera.zoom) * kotlin.math.min(1f, clampedDt * CAMERA_ZOOM_SPEED)

        // Rebuild spatial indices at a fixed cadence instead of every frame. The
        // world is large, but most indices tolerate 80-120ms of staleness and this
        // removes a major source of per-frame allocation and jank on phones.
        state.spatialRebuildTimer -= clampedDt
        if (state.spatialRebuildTimer <= 0f) {
            state.rebuildSpatialIndices()
            state.spatialRebuildTimer = 0.10f
        }

        // Update all systems
        updateResources(clampedDt)
        updateUnits(clampedDt)
        economy.updateBuildings(clampedDt)
        updateBuildingCombat(clampedDt)
        combat.updateProjectiles(clampedDt)
        ai.update(clampedDt)
        updateEffects(clampedDt)
        cleanupDead()
    }

    private fun updateResources(dt: Float) {
        for (res in state.resources) {
            if (res.dead || res.depleted) continue

            if (res.isAnimal) {
                updateAnimal(res, dt)
            }
        }
    }

    private fun updateAnimal(res: GameResource, dt: Float) {
        val def = HUNT_ANIMALS[res.animalKind] ?: return
        res.animTime += dt

        // Hurt recoil
        if (res.hurtTimer > 0f) {
            res.hurtTimer -= dt
        }

        // Panic mode
        if (res.panic > 0f) {
            res.panic -= dt
            val speed = def.runSpeedMin + (def.runSpeedMax - def.runSpeedMin) * 0.5f

            // Run away from nearest threat
            if (abs(res.vx) < 1f && abs(res.vy) < 1f) {
                val angle = Math.random().toFloat() * 2f * PI.toFloat()
                res.vx = cos(angle) * speed
                res.vy = sin(angle) * speed
            }
        } else {
            // Wander
            res.wanderTimer -= dt
            if (res.wanderTimer <= 0f) {
                res.wanderTimer = 0.3f + Math.random().toFloat() * 3.4f
                val speed = def.walkSpeedMin + (def.walkSpeedMax - def.walkSpeedMin) * Math.random().toFloat()
                if (Math.random() < 0.6) {
                    val angle = Math.random().toFloat() * 2f * PI.toFloat()
                    res.vx = cos(angle) * speed
                    res.vy = sin(angle) * speed
                } else {
                    res.vx = 0f
                    res.vy = 0f
                }
            }
        }

        // Apply velocity
        val dampening = if (res.panic > 0f) 0.992f else 0.982f
        res.vx *= dampening
        res.vy *= dampening

        val newX = res.x + res.vx * dt
        val newY = res.y + res.vy * dt

        // Stay in bounds and on land
        if (newX > 50f && newX < state.worldW - 50f && newY > 50f && newY < state.worldH - 50f && state.isLand(newX, newY)) {
            res.x = newX
            res.y = newY
        } else {
            res.vx = -res.vx
            res.vy = -res.vy
        }

        // Update direction for animation
        if (abs(res.vx) > 2f || abs(res.vy) > 2f) {
            res.animalDir = when {
                abs(res.vx) > abs(res.vy) -> if (res.vx > 0) 3 else 2
                else -> if (res.vy > 0) 0 else 1
            }
        }
    }

    private fun updateUnits(dt: Float) {
        for (u in state.units) {
            if (u.dead || u.garrisoned) continue

            u.animTime += dt
            u.cd = max(0f, u.cd - dt)
            u.flash = max(0f, u.flash - dt)

            when (u.order) {
                UnitOrder.IDLE -> updateIdle(u, dt)
                UnitOrder.MOVE -> updateMove(u, dt)
                UnitOrder.ATTACK -> updateAttack(u, dt)
                UnitOrder.ATTACK_MOVE -> updateAttackMove(u, dt)
                UnitOrder.HARVEST -> updateHarvest(u, dt)
                UnitOrder.REPAIR -> updateRepair(u, dt)
                UnitOrder.GARRISON -> updateGarrison(u, dt)
            }
        }
    }

    private fun updateIdle(u: GameUnit, dt: Float) {
        val def = UNITS[u.type] ?: return

        if (def.role == "worker") {
            maybeAutoAssignWorker(u)
            return
        }

        // Auto-acquire targets for military units
        if (def.role != "worker") {
            val scanRange = if (def.role == "healer") def.range + 50f else 310f
            if (def.role == "healer") {
                val ally = combat.lowestHurtAlly(u, scanRange)
                if (ally != null) {
                    u.order = UnitOrder.ATTACK
                    u.target = ally
                    u.targetId = ally.id
                }
            } else {
                val enemy = combat.nearestEnemy(u, scanRange)
                if (enemy != null && !u.hold) {
                    u.order = UnitOrder.ATTACK
                    u.target = enemy
                    u.targetId = enemy.id
                }
            }
        }
    }

    private fun maybeAutoAssignWorker(u: GameUnit) {
        if (u.hold) return
        if (u.carrying != null) {
            u.order = UnitOrder.HARVEST
            return
        }
        if (u.workerRole == WorkerRole.IDLE) return
        val target = when (u.workerRole) {
            WorkerRole.WOOD -> economy.nearestResource(u.x, u.y, ResourceType.TREE, 620f)
            WorkerRole.GOLD -> economy.nearestResource(u.x, u.y, ResourceType.GOLD, 680f)
            WorkerRole.FOOD -> economy.nearestResource(u.x, u.y, ResourceType.FOOD, 720f)
            WorkerRole.BUILD -> nearestOwnFoundation(u)
            WorkerRole.AUTO -> nearestUsefulWorkerTarget(u)
            WorkerRole.IDLE -> null
        }
        when (target) {
            is GameResource -> {
                u.order = UnitOrder.HARVEST
                u.target = target
                u.targetId = target.id
                u.gatherTimer = 0f
                u.path.clear()
            }
            is GameBuilding -> {
                u.order = UnitOrder.REPAIR
                u.target = target
                u.targetId = target.id
                u.gatherTimer = 0f
                u.path.clear()
            }
        }
    }

    private fun nearestOwnFoundation(u: GameUnit): GameBuilding? {
        var best: GameBuilding? = null
        var bestDist = 780f * 780f
        for (b in state.buildings) {
            if (b.dead || b.faction != u.faction || b.buildProgress >= 1f) continue
            val d = dist2(u.x, u.y, b.x, b.y)
            if (d < bestDist) { bestDist = d; best = b }
        }
        return best
    }

    private fun nearestUsefulWorkerTarget(u: GameUnit): GameEntity? {
        nearestOwnFoundation(u)?.let { return it }
        val candidates = listOfNotNull(
            economy.nearestResource(u.x, u.y, ResourceType.TREE, 620f),
            economy.nearestResource(u.x, u.y, ResourceType.GOLD, 680f),
            economy.nearestResource(u.x, u.y, ResourceType.FOOD, 720f)
        )
        return candidates.minByOrNull { dist2(u.x, u.y, it.x, it.y) }
    }

    private fun depositCarriedResources(u: GameUnit) {
        val faction = state.factions[u.faction]
        when (u.carrying) {
            "wood" -> faction.wood += u.carryAmount
            "gold" -> faction.gold += u.carryAmount
            "food" -> faction.food += u.carryAmount
        }
        u.carrying = null
        u.carryAmount = 0f
        u.gatherTimer = 0f
        state.effects.add(GameEffect("dust", u.x, u.y - 4f, maxTime = 0.34f))
    }

    private fun buildingFootprintDistance(u: GameUnit, b: GameBuilding, pad: Float = 0f): Float {
        val def = BUILDINGS[b.type] ?: return dist(u.x, u.y, b.x, b.y)
        val left = b.x - def.placeW / 2f - pad
        val right = b.x + def.placeW / 2f + pad
        val top = b.y - def.placeH / 2f - pad
        val bottom = b.y + def.placeH / 2f + pad
        val dx = max(max(left - u.x, 0f), u.x - right)
        val dy = max(max(top - u.y, 0f), u.y - bottom)
        return sqrt(dx * dx + dy * dy)
    }

    private fun buildingApproachPoint(b: GameBuilding, u: GameUnit, gap: Float = 22f): Pair<Float, Float> {
        val def = BUILDINGS[b.type] ?: return Pair(b.x, b.y)
        val left = b.x - def.placeW / 2f
        val right = b.x + def.placeW / 2f
        val top = b.y - def.placeH / 2f
        val bottom = b.y + def.placeH / 2f
        val candidates = arrayOf(
            Pair(b.x, bottom + gap),
            Pair(left + def.placeW * 0.25f, bottom + gap),
            Pair(right - def.placeW * 0.25f, bottom + gap),
            Pair(b.x, top - gap),
            Pair(left - gap, b.y),
            Pair(right + gap, b.y)
        )
        var best = candidates[0]
        var bestScore = Float.MAX_VALUE
        for (p in candidates) {
            if (!state.isSafeLand(p.first, p.second, 10f)) continue
            val frontBias = if (p.second > b.y) -900f else 0f
            val score = dist2(u.x, u.y, p.first, p.second) + frontBias
            if (score < bestScore) {
                bestScore = score
                best = p
            }
        }
        return best
    }

    private fun updateMove(u: GameUnit, dt: Float) {
        if (!u.hasGoal) {
            u.order = UnitOrder.IDLE
            return
        }

        val arrived = moveToward(u, u.goalX, u.goalY, dt, 14f)
        if (arrived) {
            u.order = UnitOrder.IDLE
            u.hasGoal = false
        }
    }

    private fun updateAttack(u: GameUnit, dt: Float) {
        val target = resolveTarget(u) ?: run {
            u.order = UnitOrder.IDLE
            u.target = null
            return
        }

        val def = UNITS[u.type] ?: return
        val d = dist(u.x, u.y, target.x, target.y)

        if (d <= def.range) {
            combat.attackTarget(u, target)
        } else {
            moveToward(u, target.x, target.y, dt, def.range * 0.9f)
        }
    }

    private fun updateAttackMove(u: GameUnit, dt: Float) {
        val def = UNITS[u.type] ?: return

        // Check for enemies while moving
        val scanRange = if (def.role == "ranged") 320f else 200f
        val enemy = combat.nearestEnemy(u, scanRange, includeBuildings = true)

        if (enemy != null) {
            u.order = UnitOrder.ATTACK
            u.target = enemy
            u.targetId = enemy.id
            return
        }

        // Continue moving to goal
        if (u.hasGoal) {
            val arrived = moveToward(u, u.goalX, u.goalY, dt, 24f)
            if (arrived) {
                u.order = UnitOrder.IDLE
                u.hasGoal = false
                u.attackMove = false
            }
        } else {
            u.order = UnitOrder.IDLE
        }
    }

    private fun updateHarvest(u: GameUnit, dt: Float) {
        if (u.type != "worker") {
            u.order = UnitOrder.IDLE
            return
        }

        // Carrying? Return to dropoff
        if (u.carrying != null) {
            val dropoff = economy.nearestDropoff(u.faction, u.x, u.y)
            if (dropoff == null) {
                u.order = UnitOrder.IDLE
                return
            }

            val approach = buildingApproachPoint(dropoff, u, 22f)
            val closeEnough = buildingFootprintDistance(u, dropoff) <= 26f || dist2(u.x, u.y, approach.first, approach.second) <= 26f * 26f
            if (closeEnough) {
                depositCarriedResources(u)

                // Go back for more, or resume the persistent worker role when the
                // old node was depleted. This mirrors the web game cargo loop.
                val target = resolveTarget(u) as? GameResource
                if (target == null || target.depleted) {
                    u.order = UnitOrder.IDLE
                    u.target = null
                    u.targetId = -1
                }
            } else {
                moveToward(u, approach.first, approach.second, dt, 18f)
            }
            return
        }

        // Not carrying - go to resource
        val target = resolveTarget(u) as? GameResource
        if (target == null || target.dead || target.depleted) {
            u.order = UnitOrder.IDLE
            u.target = null
            return
        }

        val d = dist(u.x, u.y, target.x, target.y)
        if (d < 28f) {
            // Gather
            u.gatherTimer += dt
            val gatherRate = when (target.type) {
                ResourceType.TREE -> 3.5f
                ResourceType.GOLD -> 2.8f
                ResourceType.FOOD -> if (target.isAnimal && target.animalHp > 0) 0f else 3.2f
            }

            // Attack live animals
            if (target.isAnimal && target.animalHp > 0f) {
                if (u.cd <= 0f) {
                    combat.applyDamage(target, 5, u.faction) // Worker damage
                    u.cd = 0.65f
                    u.face = if (target.x >= u.x) 1 else -1
                }
                return
            }

            if (gatherRate > 0f && u.gatherTimer >= 1f) {
                u.gatherTimer = 0f
                val amount = min(gatherRate, target.amount)
                target.amount -= amount
                u.carryAmount += amount

                val carryType = when (target.type) {
                    ResourceType.TREE -> "wood"
                    ResourceType.GOLD -> "gold"
                    ResourceType.FOOD -> "food"
                }

                if (u.carryAmount >= 8f || target.amount <= 0f) {
                    u.carrying = carryType
                    if (target.amount <= 0f) {
                        target.depleted = true
                        state.navVersion++
                    }
                }
            }

            u.face = if (target.x >= u.x) 1 else -1
        } else {
            moveToward(u, target.x, target.y, dt, 22f)
        }
    }

    private fun updateRepair(u: GameUnit, dt: Float) {
        if (u.type != "worker") {
            u.order = UnitOrder.IDLE
            return
        }

        val target = resolveTarget(u) as? GameBuilding
        if (target == null || target.dead) {
            u.order = UnitOrder.IDLE
            u.target = null
            return
        }

        if (target.buildProgress >= 1f && target.hp >= target.maxHp) {
            u.order = UnitOrder.IDLE
            u.target = null
            u.targetId = -1
            return
        }

        if (u.carrying != null) {
            val dropoff = economy.nearestDropoff(u.faction, u.x, u.y)
            if (dropoff != null) {
                val approach = buildingApproachPoint(dropoff, u, 22f)
                val closeEnough = buildingFootprintDistance(u, dropoff) <= 26f || dist2(u.x, u.y, approach.first, approach.second) <= 26f * 26f
                if (closeEnough) depositCarriedResources(u) else moveToward(u, approach.first, approach.second, dt, 18f)
            } else {
                u.carrying = null
                u.carryAmount = 0f
            }
            return
        }

        val d = buildingFootprintDistance(u, target)
        if (d < 24f) {
            // Build/repair
            u.gatherTimer += dt
            if (u.gatherTimer >= 0.5f) {
                u.gatherTimer = 0f
                if (target.buildProgress < 1f) {
                    target.buildProgress = min(1f, target.buildProgress + 0.02f)
                    if (target.buildProgress >= 1f) {
                        target.hp = target.maxHp
                        state.navVersion++
                    }
                } else if (target.hp < target.maxHp) {
                    target.hp = min(target.maxHp, target.hp + 5)
                }
            }
            u.face = if (target.x >= u.x) 1 else -1
        } else {
            val approach = buildingApproachPoint(target, u, 22f)
            moveToward(u, approach.first, approach.second, dt, 16f)
        }
    }

    private fun updateGarrison(u: GameUnit, dt: Float) {
        // Move toward garrison target
        val target = resolveTarget(u) as? GameBuilding
        if (target == null || target.dead) {
            u.order = UnitOrder.IDLE
            return
        }

        val d = dist(u.x, u.y, target.x, target.y + 42f)
        if (d < 48f) {
            u.garrisoned = true
            target.garrison.add(u)
        } else {
            moveToward(u, target.x, target.y + 42f, dt, 40f)
        }
    }

    private fun updateBuildingCombat(dt: Float) {
        for (b in state.buildings) {
            if (b.dead) continue
            combat.updateTowerCombat(b, dt)
            combat.updateMonasteryHeal(b, dt)
        }
    }

    private fun updateEffects(dt: Float) {
        val iter = state.effects.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.time += dt
            if (e.time >= e.maxTime) {
                iter.remove()
            }
        }
    }

    private fun cleanupDead() {
        state.units.removeAll { it.dead }
        state.buildings.removeAll { it.dead }
        state.resources.removeAll { it.dead && it.depleted && !it.isAnimal }
    }

    fun moveToward(unit: GameUnit, targetX: Float, targetY: Float, dt: Float, stopDistance: Float): Boolean {
        val dx = targetX - unit.x
        val dy = targetY - unit.y
        val d = sqrt(dx * dx + dy * dy)

        if (d <= stopDistance) return true

        val def = UNITS[unit.type] ?: return true
        val speed = def.speed * dt

        unit.pathRetryTimer = max(0f, unit.pathRetryTimer - dt)

        // Use pathfinding only when a direct, sampled segment is blocked. This
        // mirrors the web game's cheap-path fast path and prevents dozens of A*
        // searches when the player drags a large army across open ground.
        if (d > 160f && unit.path.isEmpty() && unit.pathRetryTimer <= 0f) {
            val samples = (d / 96f).toInt().coerceIn(9, 36)
            if (!pathfinder.isSegmentWalkable(unit.x, unit.y, targetX, targetY, samples)) {
                val path = pathfinder.prepareUnitPath(unit, targetX, targetY)
                if (path != null) {
                    unit.path = path.toMutableList()
                    unit.pathIndex = 0
                    unit.pathVersion = state.navVersion
                } else {
                    unit.pathRetryTimer = 0.90f
                }
            }
        }

        // Invalidate path if nav changed
        if (unit.path.isNotEmpty() && unit.pathVersion != state.navVersion) {
            unit.path.clear()
            unit.pathIndex = 0
        }

        // Follow path or move directly
        var moveX = dx / d
        var moveY = dy / d

        if (unit.path.isNotEmpty() && unit.pathIndex < unit.path.size) {
            val wp = unit.path[unit.pathIndex]
            val wpDx = wp.x - unit.x
            val wpDy = wp.y - unit.y
            val wpD = sqrt(wpDx * wpDx + wpDy * wpDy)

            if (wpD < 24f) {
                unit.pathIndex++
                if (unit.pathIndex >= unit.path.size) {
                    unit.path.clear()
                }
            } else {
                moveX = wpDx / wpD
                moveY = wpDy / wpD
            }
        }

        var newX = unit.x + moveX * speed
        var newY = unit.y + moveY * speed

        // Soft collision. The first Android pass used hard blocking, which made
        // groups jam and feel unresponsive. A small separation impulse preserves
        // readable formations without freezing the command queue.
        var avoidX = 0f
        var avoidY = 0f
        for (other in state.unitIndex.query(unit.x, unit.y)) {
            if (other.id == unit.id || other.dead || other.garrisoned) continue
            val orDef = UNITS[other.type] ?: continue
            val minDist = (def.radius + orDef.radius) * 0.86f
            val od2 = dist2(newX, newY, other.x, other.y)
            if (od2 > 0.01f && od2 < minDist * minDist) {
                val od = sqrt(od2)
                val push = ((minDist - od) / minDist).coerceIn(0f, 1f) * 8f
                avoidX += ((newX - other.x) / od) * push
                avoidY += ((newY - other.y) / od) * push
            }
        }
        if (avoidX != 0f || avoidY != 0f) {
            newX += avoidX
            newY += avoidY
        }

        if (state.isLand(newX, newY)) {
            unit.lastX = unit.x
            unit.lastY = unit.y
            unit.x = newX
            unit.y = newY
            unit.face = if (moveX >= 0) 1 else -1
            unit.stuck = 0f
        } else {
            unit.stuck += dt
        }

        // Stuck detection
        if (unit.stuck > STUCK_TIMEOUT) {
            unit.order = UnitOrder.IDLE
            unit.stuck = 0f
            unit.path.clear()
            return false
        }

        return false
    }

    private fun resolveTarget(u: GameUnit): GameEntity? {
        val t = u.target
        if (t != null && !t.dead) return t
        val entity = state.aliveEntity(u.targetId)
        u.target = entity
        return entity
    }

    // ── Player Commands ──

    fun orderMove(units: List<GameUnit>, targetX: Float, targetY: Float, attackMove: Boolean = false) {
        val spacing = FORMATION_MODES[state.formationMode]?.spacing ?: 42f
        for ((i, u) in units.withIndex()) {
            if (u.dead || u.garrisoned) continue
            val (ox, oy) = formationOffset(i, units.size, spacing, state.formationMode)
            u.order = if (attackMove) UnitOrder.ATTACK_MOVE else UnitOrder.MOVE
            u.goalX = targetX + ox
            u.goalY = targetY + oy
            u.hasGoal = true
            u.attackMove = attackMove
            u.path.clear()
            u.pathIndex = 0
            u.pathRetryTimer = (i % 12) * 0.035f
            u.stuck = 0f
        }
    }

    fun orderAttack(units: List<GameUnit>, target: GameEntity) {
        for (u in units) {
            if (u.dead || u.garrisoned) continue
            u.order = UnitOrder.ATTACK
            u.target = target
            u.targetId = target.id
            u.path.clear()
        }
    }

    fun orderHarvest(units: List<GameUnit>, resource: GameResource) {
        for (u in units) {
            if (u.dead || u.garrisoned || u.type != "worker") continue
            u.order = UnitOrder.HARVEST
            u.target = resource
            u.targetId = resource.id
            u.gatherTimer = 0f
            u.hasGoal = false
            u.workerRole = when (resource.type) {
                ResourceType.TREE -> WorkerRole.WOOD
                ResourceType.GOLD -> WorkerRole.GOLD
                ResourceType.FOOD -> WorkerRole.FOOD
            }
            // Preserve existing cargo; the worker will deposit it first and then
            // continue to the newly requested node instead of deleting resources.
            u.path.clear()
        }
    }

    fun orderRepair(units: List<GameUnit>, building: GameBuilding) {
        for (u in units) {
            if (u.dead || u.garrisoned || u.type != "worker") continue
            u.order = UnitOrder.REPAIR
            u.target = building
            u.targetId = building.id
            u.gatherTimer = 0f
            u.hasGoal = false
            u.path.clear()
        }
    }

    fun orderStop(units: List<GameUnit>) {
        for (u in units) {
            u.order = UnitOrder.IDLE
            u.target = null
            u.hasGoal = false
            u.path.clear()
        }
    }

    fun orderHold(units: List<GameUnit>) {
        for (u in units) {
            u.hold = !u.hold
            u.order = UnitOrder.IDLE
            u.target = null
            u.path.clear()
        }
    }

    fun setFormation(mode: String) {
        if (FORMATION_MODES.containsKey(mode)) {
            state.formationMode = mode
        }
    }
}
