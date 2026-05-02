package com.tinyswords.realmwar.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stateless update step that mutates [GameWorld]. Stays in pure logic so it can be unit tested
 * without UI. Mirrors the major pieces of js/systems/simulation.js: orders, pathfinding, attack
 * resolution, projectiles, hunting, AI build/attack waves, healing, deposit/gather flows.
 */
class Simulation(
    private val world: GameWorld
) {
    private val ai = AiController(world)
    private val pathfinder = SteeringPathfinder(world)

    fun step(dtRaw: Float, fastForward: Boolean) {
        val dt = if (fastForward) (dtRaw * 2f).coerceAtMost(GameConfig.MAX_DT * 2f) else dtRaw.coerceAtMost(GameConfig.MAX_DT)
        world.elapsedTime += dt
        // Tick faction systems
        for (f in world.factions) {
            if (!f.alive) continue
            f.underAttackTimer = (f.underAttackTimer - dt).coerceAtLeast(0f)
        }
        updateUnits(dt)
        updateBuildings(dt)
        updateResources(dt)
        updateProjectiles(dt)
        updateEffects(dt)
        ai.update(dt)
        cullDead()
        checkVictory()
    }

    // -------------------------------------------------------------------- units
    private fun updateUnits(dt: Float) {
        for (u in world.units) {
            if (u.dead) continue
            u.attackCooldown = (u.attackCooldown - dt).coerceAtLeast(0f)
            u.hurtFlash = (u.hurtFlash - dt).coerceAtLeast(0f)
            u.animTime += dt

            if (u.hp <= 0) {
                u.dead = true
                world.effects.add(FxEffect(u.x, u.y, "explosion", 0.45f))
                continue
            }
            // Acquire a target if aggressive idle
            if (u.aggressive && u.orderTargetUnit == null && u.orderTargetBuilding == null && u.orderTargetResource == null && u.moveTargetX == null) {
                acquireAutoTarget(u)
            }
            handleUnitOrders(u, dt)
            steerToTarget(u, dt)
            updateFacing(u)
        }
    }

    private fun acquireAutoTarget(u: Unit) {
        val rangeSq = if (u.def.role == GameConfig.UnitRole.RANGED) u.def.range * u.def.range else (220f * 220f)
        var best: Unit? = null
        var bestD = rangeSq
        for (other in world.units) {
            if (other.dead || other.factionId == u.factionId) continue
            val d = (other.x - u.x).let { it * it } + (other.y - u.y).let { it * it }
            if (d < bestD) {
                bestD = d
                best = other
            }
        }
        if (best != null) {
            u.orderTargetUnit = best
            return
        }
        // Healer idle target: find low-HP friendly nearby
        if (u.def.role == GameConfig.UnitRole.HEALER) {
            var bestF: Unit? = null
            var bestFd = (260f * 260f)
            for (other in world.units) {
                if (other.dead || other.factionId != u.factionId || other === u) continue
                if (other.hp >= other.maxHp) continue
                val d = (other.x - u.x).let { it * it } + (other.y - u.y).let { it * it }
                if (d < bestFd) {
                    bestFd = d
                    bestF = other
                }
            }
            if (bestF != null) u.orderTargetUnit = bestF
        }
    }

    private fun handleUnitOrders(u: Unit, dt: Float) {
        val target = u.orderTargetUnit
        if (target != null && !target.dead) {
            val dist = hypot(target.x - u.x, target.y - u.y)
            if (dist <= u.def.range + target.radius) {
                u.moveTargetX = null
                u.moveTargetY = null
                tryAttack(u, target, dt)
            } else {
                u.moveTargetX = target.x
                u.moveTargetY = target.y
            }
            return
        }
        val tBld = u.orderTargetBuilding
        if (tBld != null && !tBld.dead) {
            val dist = hypot(tBld.x - u.x, tBld.y - u.y)
            if (dist <= u.def.range + tBld.radius) {
                u.moveTargetX = null
                u.moveTargetY = null
                tryAttackBuilding(u, tBld, dt)
            } else {
                u.moveTargetX = tBld.x
                u.moveTargetY = tBld.y
            }
            return
        }
        val res = u.orderTargetResource
        if (res != null && !res.dead) {
            handleHarvestOrder(u, res, dt)
        }
    }

    private fun handleHarvestOrder(u: Unit, res: Resource, dt: Float) {
        if (u.def.role != GameConfig.UnitRole.WORKER) {
            u.orderTargetResource = null
            return
        }
        val carryFull = u.carryAmount >= 8
        if (carryFull) {
            // deposit at nearest castle/house of own faction
            val depot = nearestDepot(u)
            if (depot == null) { u.carryAmount = 0; u.carryType = null; return }
            val dist = hypot(depot.x - u.x, depot.y - u.y)
            if (dist <= depot.radius + u.def.radius + 6f) {
                val faction = world.factions.first { it.id == u.factionId }
                when (u.carryType) {
                    "wood" -> faction.resWood += u.carryAmount
                    "gold" -> faction.resGold += u.carryAmount
                    "food" -> faction.resFood += u.carryAmount
                }
                u.carryAmount = 0
                u.carryType = null
                u.gatherTimer = 0f
                u.moveTargetX = res.x
                u.moveTargetY = res.y
            } else {
                u.moveTargetX = depot.x
                u.moveTargetY = depot.y
            }
            return
        }

        val dist = hypot(res.x - u.x, res.y - u.y)
        if (dist > u.def.radius + res.r + 6f) {
            u.moveTargetX = res.x
            u.moveTargetY = res.y
            return
        }
        u.moveTargetX = null
        u.moveTargetY = null
        u.gatherTimer += dt
        val gatherTime = if (res.isAnimal) 0.6f else GameConfig.Gather.GATHER_TIME
        if (u.gatherTimer >= gatherTime) {
            u.gatherTimer = 0f
            val takePerTrip = when (res.type) {
                ResourceType.TREE -> GameConfig.Gather.WOOD_PER_TRIP
                ResourceType.GOLD -> GameConfig.Gather.GOLD_PER_TRIP
                ResourceType.FOOD -> if (res.isAnimal) GameConfig.Gather.FOOD_PER_HIT else 6
            }
            val take = min(takePerTrip, res.amount)
            res.amount -= take
            u.carryAmount += take
            u.carryType = when (res.type) { ResourceType.TREE -> "wood"; ResourceType.GOLD -> "gold"; ResourceType.FOOD -> "food" }
            if (res.isAnimal) {
                res.animal!!.hp -= 16f
                res.animal.hurtFlash = 0.3f
                res.animal.panicTimer = 5f
                if (res.animal.hp <= 0f) {
                    res.amount = 0
                    res.dead = true
                }
                if (res.animal.def.retaliation > 0 && res.animal.hp > 0f) {
                    u.hp -= res.animal.def.retaliation.toFloat()
                    u.hurtFlash = 0.25f
                }
            }
            if (res.amount <= 0) {
                res.dead = true
                u.orderTargetResource = null
            }
        }
    }

    private fun nearestDepot(u: Unit): Building? {
        var best: Building? = null
        var bestD = Float.MAX_VALUE
        for (b in world.buildings) {
            if (b.factionId != u.factionId || b.dead || !b.built) continue
            if (b.type != "castle" && b.type != "house") continue
            val d = (b.x - u.x).let { it * it } + (b.y - u.y).let { it * it }
            if (d < bestD) { bestD = d; best = b }
        }
        return best
    }

    private fun tryAttack(u: Unit, target: Unit, dt: Float) {
        if (u.attackCooldown > 0f) return
        u.attackCooldown = u.def.cooldown
        if (u.def.role == GameConfig.UnitRole.RANGED) {
            world.projectiles.add(Projectile(u.factionId, u.x, u.y, target.x, target.y, u.def.damage, targetUnitId = target.id))
        } else if (u.def.role == GameConfig.UnitRole.HEALER) {
            // Heal: damage is negative
            target.hp = (target.hp - u.def.damage).coerceAtMost(target.maxHp)
            world.effects.add(FxEffect(target.x, target.y, "heal", 0.5f))
        } else {
            // Melee resolves immediately with a small swing fx
            target.hp -= u.def.damage
            target.hurtFlash = 0.25f
            world.effects.add(FxEffect((u.x + target.x) / 2f, (u.y + target.y) / 2f, "swing", 0.18f))
        }
    }

    private fun tryAttackBuilding(u: Unit, b: Building, dt: Float) {
        if (u.attackCooldown > 0f) return
        u.attackCooldown = u.def.cooldown
        val dmg = if (u.def.role == GameConfig.UnitRole.WORKER) max(2, u.def.damage / 2) else u.def.damage
        if (u.def.role == GameConfig.UnitRole.RANGED) {
            world.projectiles.add(Projectile(u.factionId, u.x, u.y, b.x, b.y, dmg, targetBuildingId = b.id))
        } else {
            b.hp -= dmg
            // Mark faction under attack
            val faction = world.factions.firstOrNull { it.id == b.factionId }
            faction?.underAttackTimer = 8f
        }
    }

    // ---------------------------------------------------------------- buildings
    private fun updateBuildings(dt: Float) {
        for (b in world.buildings) {
            if (b.dead) continue
            if (b.hp <= 0f) {
                b.dead = true
                world.effects.add(FxEffect(b.x, b.y, "explosion", 0.7f))
                continue
            }
            if (!b.built) {
                b.buildProgress += dt / max(1f, b.def.time)
                if (b.buildProgress >= 1f) {
                    b.buildProgress = 1f
                    b.built = true
                    if (b.def.builtInArcher) {
                        val archerDef = GameConfig.UNITS["archer"]!!
                        val archer = Unit("archer", archerDef, b.factionId, b.x, b.y + 12f)
                        world.units.add(archer)
                    }
                }
                continue
            }
            // production
            b.production?.let { prod ->
                prod.time += dt
                if (prod.time >= prod.total) {
                    val u = Unit(prod.unitType, prod.unitDef, b.factionId, b.x + 30f, b.y + b.radius + 10f)
                    u.moveTargetX = b.rallyX
                    u.moveTargetY = b.rallyY
                    world.units.add(u)
                    b.production = null
                }
            }
            // tower auto-attack
            if (b.def.tower) {
                b.towerCooldown = (b.towerCooldown - dt).coerceAtLeast(0f)
                if (b.towerCooldown <= 0f) {
                    val target = nearestEnemyUnit(b.x, b.y, b.factionId, b.def.range)
                    if (target != null) {
                        b.towerCooldown = 1.05f
                        world.projectiles.add(Projectile(b.factionId, b.x, b.y - 30f, target.x, target.y, 14, targetUnitId = target.id))
                    }
                }
            }
        }
    }

    private fun nearestEnemyUnit(x: Float, y: Float, factionId: Int, range: Float): Unit? {
        val r2 = range * range
        var best: Unit? = null
        var bestD = r2
        for (u in world.units) {
            if (u.dead || u.factionId == factionId) continue
            val d = (u.x - x).let { it * it } + (u.y - y).let { it * it }
            if (d < bestD) { bestD = d; best = u }
        }
        return best
    }

    // -------------------------------------------------------------- resources
    private fun updateResources(dt: Float) {
        for (r in world.resources) {
            if (r.dead) continue
            val animal = r.animal ?: continue
            animal.hurtFlash = (animal.hurtFlash - dt).coerceAtLeast(0f)
            animal.panicTimer = (animal.panicTimer - dt).coerceAtLeast(0f)
            val speed = if (animal.panicTimer > 0f) animal.def.runSpeed else animal.def.walkSpeed
            // Wandering brain
            animal.moveT -= dt
            if (animal.moveT <= 0f) {
                animal.moveT = 1.5f + Math.random().toFloat() * 4f
                animal.heading = Math.random().toFloat() * (Math.PI * 2).toFloat()
            }
            val nx = r.x + cos(animal.heading) * speed * dt
            val ny = r.y + sin(animal.heading) * speed * dt
            if (world.isLand(nx, ny)) {
                r.x = nx
                r.y = ny
            } else {
                animal.heading += (Math.PI / 1.6).toFloat()
            }
            // Update facing
            animal.facing = directionFromVec(cos(animal.heading), sin(animal.heading))
            animal.stride += dt * speed * 0.06f
        }
    }

    // -------------------------------------------------------------- projectiles
    private fun updateProjectiles(dt: Float) {
        val it = world.projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (p.dead) { it.remove(); continue }
            p.t += dt
            val travelled = p.speed * p.t
            if (travelled >= p.totalDist) {
                resolveProjectile(p)
                p.dead = true
                it.remove()
                continue
            }
        }
    }

    private fun resolveProjectile(p: Projectile) {
        if (p.targetUnitId != null) {
            val target = world.units.firstOrNull { it.id == p.targetUnitId && !it.dead } ?: return
            target.hp -= p.damage
            target.hurtFlash = 0.25f
            world.effects.add(FxEffect(target.x, target.y, "hit", 0.18f))
            return
        }
        if (p.targetBuildingId != null) {
            val target = world.buildings.firstOrNull { it.id == p.targetBuildingId && !it.dead } ?: return
            target.hp -= p.damage
            world.effects.add(FxEffect(target.x, target.y - 20f, "hit", 0.25f))
        }
    }

    // -------------------------------------------------------------- effects
    private fun updateEffects(dt: Float) {
        val it = world.effects.iterator()
        while (it.hasNext()) {
            val fx = it.next()
            fx.t += dt
            if (fx.t >= fx.duration) it.remove()
        }
    }

    // -------------------------------------------------------------- movement
    private fun steerToTarget(u: Unit, dt: Float) {
        val targetX = u.moveTargetX ?: return
        val targetY = u.moveTargetY ?: return
        val dx = targetX - u.x
        val dy = targetY - u.y
        val d = hypot(dx, dy)
        if (d < 4f) {
            u.moveTargetX = null
            u.moveTargetY = null
            u.vx = 0f; u.vy = 0f
            return
        }
        val invD = 1f / d
        var stepX = dx * invD
        var stepY = dy * invD
        // simple separation
        val sep = pathfinder.separation(u, 22f)
        stepX += sep[0] * 0.4f
        stepY += sep[1] * 0.4f
        val len = hypot(stepX, stepY)
        if (len > 0f) {
            stepX /= len
            stepY /= len
        }
        val speed = u.def.speed * dt
        val nx = u.x + stepX * speed
        val ny = u.y + stepY * speed
        if (world.isLand(nx, ny)) {
            u.x = nx
            u.y = ny
            u.vx = stepX
            u.vy = stepY
        } else {
            // try one axis
            if (world.isLand(nx, u.y)) { u.x = nx; u.vx = stepX; u.vy = 0f }
            else if (world.isLand(u.x, ny)) { u.y = ny; u.vx = 0f; u.vy = stepY }
            else { u.moveTargetX = null; u.moveTargetY = null }
        }
    }

    private fun updateFacing(u: Unit) {
        if (u.vx == 0f && u.vy == 0f) return
        u.facing = directionFromVec(u.vx, u.vy)
    }

    private fun directionFromVec(vx: Float, vy: Float): Int {
        val ang = atan2(vy, vx) // -PI..PI
        // 0=down(+y), 1=up(-y), 2=left(-x), 3=right(+x)
        return when {
            kotlin.math.abs(vx) > kotlin.math.abs(vy) -> if (vx > 0) 3 else 2
            else -> if (vy > 0) 0 else 1
        }
    }

    // -------------------------------------------------------------- cleanup
    private fun cullDead() {
        world.units.removeAll { it.dead }
        world.buildings.removeAll { it.dead }
        world.resources.removeAll { it.dead }
    }

    private fun checkVictory() {
        for (f in world.factions) {
            if (!f.alive) continue
            val hasCastle = world.buildings.any { it.factionId == f.id && it.type == "castle" && !it.dead }
            if (!hasCastle) f.alive = false
        }
    }
}

class SteeringPathfinder(private val world: GameWorld) {
    private val tmp = floatArrayOf(0f, 0f)
    fun separation(u: Unit, radius: Float): FloatArray {
        var sx = 0f
        var sy = 0f
        val r2 = radius * radius
        for (other in world.units) {
            if (other === u || other.dead) continue
            val dx = u.x - other.x
            val dy = u.y - other.y
            val d2 = dx * dx + dy * dy
            if (d2 > 0f && d2 < r2) {
                val w = (1f - d2 / r2)
                sx += dx * w
                sy += dy * w
            }
        }
        val l = hypot(sx, sy)
        if (l > 0f) { sx /= l; sy /= l }
        tmp[0] = sx; tmp[1] = sy
        return tmp
    }
}
