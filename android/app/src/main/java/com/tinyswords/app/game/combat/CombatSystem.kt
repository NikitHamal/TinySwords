package com.tinyswords.app.game.combat

import com.tinyswords.app.game.*
import com.tinyswords.app.game.entities.*
import com.tinyswords.app.util.dist
import com.tinyswords.app.util.dist2
import kotlin.math.*

class CombatSystem(private val state: GameState) {

    fun updateProjectiles(dt: Float) {
        val iter = state.projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) {
                iter.remove()
                continue
            }

            val target = state.aliveEntity(p.targetId)
            if (target == null) {
                iter.remove()
                continue
            }

            val tx = target.x
            val ty = target.y - 20f
            val dx = tx - p.x
            val dy = ty - p.y
            val d = sqrt(dx * dx + dy * dy)
            p.angle = atan2(dy, dx)

            if (d < PROJECTILE_HIT_DIST) {
                applyDamage(target, p.damage, p.factionId)
                state.effects.add(GameEffect("hit", target.x, target.y - 10f, maxTime = 0.4f))
                iter.remove()
            } else {
                val move = p.speed * dt
                p.x += (dx / d) * move
                p.y += (dy / d) * move
            }
        }
    }

    fun spawnProjectile(factionId: Int, x: Float, y: Float, target: GameEntity, damage: Int) {
        state.projectiles.add(Projectile(
            x = x, y = y - 34f,
            targetId = target.id,
            factionId = factionId,
            damage = damage
        ))
    }

    fun applyDamage(target: GameEntity, amount: Int, attackerFaction: Int) {
        if (amount < 0) {
            // Healing
            when (target) {
                is GameUnit -> {
                    target.hp = min(target.maxHp, target.hp - amount)
                    state.effects.add(GameEffect("heal", target.x, target.y - 10f, maxTime = 0.5f))
                }
                is GameBuilding -> {
                    target.hp = min(target.maxHp, target.hp - amount)
                }
            }
            return
        }

        target.flash = FLASH_DURATION

        when (target) {
            is GameUnit -> {
                target.hp = max(0, target.hp - amount)
                if (target.hp <= 0) {
                    killUnit(target, attackerFaction)
                }
            }
            is GameBuilding -> {
                target.hp = max(0, target.hp - amount)
                // Mark faction as under attack
                if (target.faction != attackerFaction) {
                    state.factions[target.faction].underAttack = 5f
                }
                if (target.hp <= 0) {
                    killBuilding(target, attackerFaction)
                }
            }
            is GameResource -> {
                if (target.isAnimal) {
                    target.animalHp = max(0f, target.animalHp - amount)
                    target.hurtTimer = 0.30f
                    target.panic = ANIMAL_PANIC_DURATION
                    if (target.animalHp <= 0f) {
                        killAnimal(target)
                    }
                }
            }
        }
    }

    fun attackTarget(attacker: GameUnit, target: GameEntity) {
        val def = UNITS[attacker.type] ?: return
        if (attacker.cd > 0f) return

        val tx = target.x
        attacker.face = if (tx >= attacker.x) 1 else -1
        attacker.cd = attacker.maxCd

        if (def.role == "ranged") {
            spawnProjectile(attacker.faction, attacker.x, attacker.y, target, attacker.damage)
        } else if (def.role == "healer") {
            applyDamage(target, attacker.damage, attacker.faction) // Negative = heal
        } else {
            applyDamage(target, attacker.damage, attacker.faction)
        }
    }

    private fun defensiveProjectileOrigin(building: GameBuilding, index: Int, count: Int): Pair<Float, Float> {
        val def = BUILDINGS[building.type]
        val offset = (index - (count - 1) / 2f) * if (building.type == "castle") 38f else 18f
        val yOffset = if (building.type == "castle") (def?.h ?: 132f) * 0.68f else (def?.h ?: 96f) * 0.62f
        return Pair(building.x + offset, building.y - yOffset)
    }

    private fun defensiveCandidates(building: GameBuilding, range: Float): List<GameEntity> {
        val candidates = ArrayList<GameEntity>(12)
        val rangeSquared = range * range
        for (u in state.unitIndex.queryRange(building.x, building.y, range)) {
            if (u.dead || u.faction == building.faction || u.garrisoned) continue
            if (!state.factions[u.faction].alive) continue
            if (dist2(building.x, building.y, u.x, u.y) <= rangeSquared) candidates.add(u)
        }
        for (b in state.buildingIndex.queryRange(building.x, building.y, range)) {
            if (b.dead || b.faction == building.faction) continue
            if (!state.factions[b.faction].alive) continue
            if (dist2(building.x, building.y, b.x, b.y) <= rangeSquared) candidates.add(b)
        }
        return candidates
    }

    private fun pickDefensiveTarget(originX: Float, originY: Float, candidates: List<GameEntity>, usedIds: Set<Int>): GameEntity? {
        var best: GameEntity? = null
        var bestDist = Float.MAX_VALUE
        for (target in candidates) {
            if (target.id in usedIds) continue
            val d = dist2(originX, originY, target.x, target.y)
            if (d < bestDist) {
                bestDist = d
                best = target
            }
        }
        if (best != null) return best
        for (target in candidates) {
            val d = dist2(originX, originY, target.x, target.y)
            if (d < bestDist) {
                bestDist = d
                best = target
            }
        }
        return best
    }

    fun updateTowerCombat(building: GameBuilding, dt: Float) {
        if (building.buildProgress < 1f || building.dead) return
        val archerCount = defensiveArcherCount(building)
        val range = defensiveBuildingRange(building)
        if (archerCount <= 0 || range <= 0f) return

        building.towerCd -= dt
        if (building.towerCd > 0f) return

        val candidates = defensiveCandidates(building, range)
        if (candidates.isEmpty()) return

        val damage = defensiveBuildingDamage(building)
        val usedIds = HashSet<Int>(archerCount)
        var fired = 0
        for (index in 0 until archerCount) {
            val origin = defensiveProjectileOrigin(building, index, archerCount)
            val target = pickDefensiveTarget(origin.first, origin.second, candidates, usedIds) ?: break
            usedIds.add(target.id)
            spawnProjectile(building.faction, origin.first, origin.second + 34f, target, damage)
            if (index < building.defenderShotUntil.size) {
                building.defenderShotUntil[index] = state.time + 0.52f
                building.defenderShotFace[index] = if (target.x >= origin.first) 1 else -1
            }
            fired++
        }
        if (fired > 0) building.towerCd = defensiveBuildingCooldown(building)
    }

    fun updateMonasteryHeal(building: GameBuilding, dt: Float) {
        if (building.type != "monastery" || building.buildProgress < 1f || building.dead) return

        building.healCd -= dt
        if (building.healCd > 0f) return

        var healed = false
        val rangeSquared = MONASTERY_HEAL_RANGE * MONASTERY_HEAL_RANGE

        for (u in state.unitIndex.queryRange(building.x, building.y, MONASTERY_HEAL_RANGE)) {
            if (u.faction == building.faction && !u.dead && u.hp < u.maxHp) {
                val d = dist2(u.x, u.y, building.x, building.y)
                if (d < rangeSquared) {
                    u.hp = min(u.maxHp, u.hp + MONASTERY_HEAL_AMOUNT.toInt())
                    healed = true
                }
            }
        }

        if (healed) {
            building.healCd = MONASTERY_HEAL_CD
            state.effects.add(GameEffect("heal", building.x, building.y - 20f, maxTime = 0.4f))
        }
    }

    fun nearestEnemy(unit: GameUnit, range: Float, includeBuildings: Boolean = false): GameEntity? {
        var best: GameEntity? = null
        var bestDist = range * range

        for (u in state.unitIndex.queryRange(unit.x, unit.y, range)) {
            if (u.dead || u.faction == unit.faction || u.garrisoned) continue
            if (!state.factions[u.faction].alive) continue
            val d = dist2(unit.x, unit.y, u.x, u.y)
            if (d < bestDist) {
                bestDist = d
                best = u
            }
        }

        if (includeBuildings) {
            for (b in state.buildingIndex.queryRange(unit.x, unit.y, range)) {
                if (b.dead || b.faction == unit.faction) continue
                if (!state.factions[b.faction].alive) continue
                val d = dist2(unit.x, unit.y, b.x, b.y)
                if (d < bestDist) {
                    bestDist = d
                    best = b
                }
            }
        }

        return best
    }

    fun lowestHurtAlly(unit: GameUnit, range: Float): GameUnit? {
        var best: GameUnit? = null
        var bestPct = 1f

        for (u in state.unitIndex.queryRange(unit.x, unit.y, range)) {
            if (u.dead || u.faction != unit.faction || u.id == unit.id || u.garrisoned) continue
            val pct = u.hp.toFloat() / u.maxHp
            if (pct < 1f && pct < bestPct) {
                bestPct = pct
                best = u
            }
        }

        return best
    }

    private fun killUnit(unit: GameUnit, attackerFaction: Int) {
        unit.dead = true
        unit.selected = false
        state.selected.remove(unit)
        state.effects.add(GameEffect("dust", unit.x, unit.y, maxTime = 0.5f))
        checkWinCondition()
    }

    private fun killBuilding(building: GameBuilding, attackerFaction: Int) {
        building.dead = true
        building.selected = false
        state.selected.remove(building)
        state.effects.add(GameEffect("explosion", building.x, building.y, maxTime = 0.8f))
        // Release garrisoned units
        for (g in building.garrison) {
            g.garrisoned = false
            g.x = building.x + (Math.random().toFloat() - 0.5f) * 40f
            g.y = building.y + 30f
        }
        building.garrison.clear()
        checkWinCondition()
    }

    private fun killAnimal(resource: GameResource) {
        resource.isAnimal = false
        // Convert to butchered food pile
        resource.amount = (HUNT_ANIMALS[resource.animalKind]?.yield ?: 14).toFloat()
        state.effects.add(GameEffect("dust", resource.x, resource.y, maxTime = 0.4f))
    }

    private fun checkWinCondition() {
        var playerAlive = false
        val aliveAi = mutableListOf<Int>()

        for (i in 0 until 5) {
            if (!state.factions[i].alive) continue
            val hasBuildings = state.buildings.any { it.faction == i && !it.dead }
            val hasUnits = state.units.any { it.faction == i && !it.dead }
            if (!hasBuildings && !hasUnits) {
                state.factions[i].alive = false
            } else {
                if (i == 0) playerAlive = true
                else aliveAi.add(i)
            }
        }

        if (!playerAlive) {
            state.gameOver = true
            state.winnerFaction = if (aliveAi.isNotEmpty()) aliveAi[0] else -1
        } else if (aliveAi.isEmpty()) {
            state.gameOver = true
            state.winnerFaction = 0
        }
    }
}
