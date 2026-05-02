package com.tinyswords.game

import kotlin.math.*
import kotlin.random.Random

/**
 * The fixed-step simulator. Runs all per-tick logic — unit movement & combat, projectile
 * physics, building construction & training queues, animal AI, harvesting, and the AI faction
 * brain (delegated to [enemyAI]).
 *
 * One pass per [step] call. The renderer is decoupled and reads [Game] state directly.
 */
class Simulation(val game: Game) {
    private val rng = Random(System.nanoTime())
    private var pathRebuildAccum = 0f
    private var aiAccum = 0f

    fun step(rawDt: Float) {
        if (game.paused || game.gameOver != -1) return
        val dt = min(rawDt, C.MAX_DT)
        game.time += dt

        // Throttled rebuild — pathfinding cares about static obstacles and constructed buildings,
        // both of which only change at human pace. Half-second cadence is fast enough.
        pathRebuildAccum += dt
        if (game.dirtyPath || pathRebuildAccum > 0.5f) {
            game.pathfinder.rebuild(game.buildings, game.resources)
            game.dirtyPath = false
            pathRebuildAccum = 0f
        }

        updateBuildings(dt)
        updateResources(dt)
        updateUnits(dt)
        updateProjectiles(dt)
        updateEffects(dt)

        aiAccum += dt
        if (aiAccum > 0.6f) {
            enemyAI(game, aiAccum)
            aiAccum = 0f
        }

        cleanup()
        checkVictory()
        game.bumpRevision()
    }

    // ---------------------------------------------------------------- buildings

    private fun updateBuildings(dt: Float) {
        for (b in game.buildings) {
            if (!b.alive) continue
            // Construction handled by builder workers — see updateUnits/buildOrder.
            // Tower passive: shoot at enemies in range.
            if (b.type == BuildingType.TOWER && b.buildProgress >= 1f) {
                b.towerCd -= dt
                if (b.towerCd <= 0f) {
                    val target = game.nearestEnemy(b.x, b.y, b.faction, 360f)
                    if (target != null) {
                        game.projectiles += Projectile(
                            b.x, b.y - 24f, target.x, target.y - 18f,
                            target.id, b.faction, 14
                        )
                        b.towerCd = 1.4f
                    }
                }
            }
            // Train queue: progress the head item, spawn unit when done if pop allows.
            if (b.queue.isNotEmpty() && b.buildProgress >= 1f) {
                val head = b.queue.first()
                head.progress += dt / unitTrainTime(head.type)
                if (head.progress >= 1f) {
                    val f = game.factions[b.faction]
                    val needsPop = head.type.pop
                    if (f.popUsed + needsPop <= f.popCap) {
                        // Spawn at rally if set, else at building edge.
                        val angle = rng.nextFloat() * (Math.PI * 2).toFloat()
                        val sx = b.x + cos(angle) * (b.w * 0.55f)
                        val sy = b.y + sin(angle) * (b.h * 0.55f) + 14f
                        val u = game.spawnUnit(head.type, sx, sy, b.faction)
                        if (b.rallyX > 0f) {
                            issueMove(u, b.rallyX, b.rallyY)
                        }
                        b.queue.removeAt(0)
                    }
                }
            }
        }
    }

    private fun unitTrainTime(t: UnitType) = when (t) {
        UnitType.WORKER -> 6f
        UnitType.WARRIOR -> 9f
        UnitType.ARCHER -> 11f
        UnitType.LANCER -> 14f
        UnitType.MONK -> 13f
    }

    // ---------------------------------------------------------------- resources / animals

    private fun updateResources(dt: Float) {
        for (r in game.resources) {
            if (!r.alive) continue
            r.animTimer += dt
            if (r.animal != null) {
                // Animals wander, panic when hp lost.
                val sp = if (r.panic > 0f) r.animal.run else r.animal.walk
                if (r.panic > 0f) r.panic -= dt
                if (rng.nextFloat() < dt * 0.6f) {
                    val a = rng.nextFloat() * (Math.PI * 2).toFloat()
                    r.vx = cos(a) * sp; r.vy = sin(a) * sp
                    r.animDir = if (abs(r.vx) > abs(r.vy)) (if (r.vx < 0) 2 else 3) else (if (r.vy < 0) 1 else 0)
                }
                val nx = r.x + r.vx * dt
                val ny = r.y + r.vy * dt
                if (game.world.isLandPx(nx, ny)) { r.x = nx; r.y = ny }
                else { r.vx = -r.vx; r.vy = -r.vy }
                r.animState = if (r.panic > 0f) 2 else if (abs(r.vx) + abs(r.vy) > 6f) 1 else 0
            }
        }
    }

    // ---------------------------------------------------------------- units

    private fun updateUnits(dt: Float) {
        for (u in game.units) {
            if (!u.alive) continue
            u.cd = max(0f, u.cd - dt)
            u.anim += dt

            // Idle units passively engage nearby enemies.
            if (u.order == Order.IDLE && u.type != UnitType.WORKER) {
                val e = game.nearestEnemy(u.x, u.y, u.faction, 240f)
                if (e != null) { u.order = Order.ATTACK; u.targetId = e.id }
            }

            when (u.order) {
                Order.IDLE -> { u.animState = 0 }
                Order.MOVE -> stepMove(u, dt)
                Order.ATTACK -> stepAttack(u, dt)
                Order.ATTACK_MOVE -> stepAttackMove(u, dt)
                Order.HARVEST -> stepHarvest(u, dt)
                Order.RETURN_RESOURCE -> stepReturn(u, dt)
                Order.BUILD, Order.REPAIR -> stepBuild(u, dt)
                Order.HEAL -> stepHeal(u, dt)
                Order.FLEE -> stepMove(u, dt)
            }

            // Monk passive heal aura.
            if (u.type == UnitType.MONK && u.cd <= 0f) {
                var did = false
                for (a in game.units) {
                    if (a.alive && a.faction == u.faction && a.id != u.id && a.hp < a.maxHp) {
                        val dx = a.x - u.x; val dy = a.y - u.y
                        if (dx * dx + dy * dy < 200f * 200f) {
                            a.hp = (a.hp + 8f).coerceAtMost(a.maxHp)
                            did = true
                        }
                    }
                }
                if (did) {
                    u.cd = 1.2f
                    game.effects += Effect(u.x, u.y - 20f, 0f, 0.4f, EffectKind.HEAL)
                }
            }
        }
    }

    /** Move along [GameUnit.path]; if no path, pathfind to (goalX, goalY). */
    private fun stepMove(u: GameUnit, dt: Float): Boolean {
        if (u.path.isEmpty()) {
            val p = game.pathfinder.find(u.x, u.y, u.goalX, u.goalY) ?: run {
                u.order = Order.IDLE; return false
            }
            u.path = p; u.pathIndex = 2 // skip the start point
        }
        if (u.pathIndex >= u.path.size) {
            u.order = Order.IDLE; u.animState = 0; return true
        }
        val tx = u.path[u.pathIndex]; val ty = u.path[u.pathIndex + 1]
        val dx = tx - u.x; val dy = ty - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 4f) { u.pathIndex += 2; return false }
        val sp = u.type.speed
        val mv = min(sp * dt, d)
        u.x += dx / d * mv; u.y += dy / d * mv
        u.facing = if (dx < 0) -1 else 1
        u.animState = 1
        return false
    }

    private fun stepAttack(u: GameUnit, dt: Float) {
        val target = game.findEntity(u.targetId)
        if (target == null || !target.alive) {
            u.order = Order.IDLE; u.targetId = -1; return
        }
        val dx = target.x - u.x; val dy = target.y - u.y
        val d = sqrt(dx * dx + dy * dy)
        val targetReach = (target as? Building)?.let { sqrt(it.w * it.w + it.h * it.h) * 0.5f } ?: 0f
        val effectiveRange = u.type.range + targetReach
        if (d > effectiveRange * 0.94f) {
            // Approach.
            u.goalX = target.x; u.goalY = target.y
            if (u.path.isEmpty()) {
                val p = game.pathfinder.find(u.x, u.y, target.x, target.y)
                if (p != null) { u.path = p; u.pathIndex = 2 }
            }
            stepMove(u, dt)
            u.animState = 1
        } else {
            // In range — attack on cooldown.
            u.path.clear()
            u.facing = if (dx < 0) -1 else 1
            u.animState = 2
            if (u.cd <= 0f) {
                if (u.type.ranged) {
                    game.projectiles += Projectile(
                        u.x, u.y - 18f, target.x, target.y - 16f,
                        target.id, u.faction, u.type.damage
                    )
                } else {
                    target.hp -= u.type.damage.toFloat()
                    game.effects += Effect(target.x, target.y - 18f, 0f, 0.25f, EffectKind.HIT)
                    if (target.hp <= 0f) target.alive = false
                }
                u.cd = u.type.cooldown
            }
        }
    }

    private fun stepAttackMove(u: GameUnit, dt: Float) {
        val nearby = game.nearestEnemy(u.x, u.y, u.faction, 280f)
        if (nearby != null) {
            u.targetId = nearby.id
            stepAttack(u, dt)
        } else if (stepMove(u, dt)) {
            u.order = Order.IDLE
        }
    }

    private fun stepHarvest(u: GameUnit, dt: Float) {
        if (u.type != UnitType.WORKER) { u.order = Order.IDLE; return }
        var res = game.resources.firstOrNull { it.id == u.assignedResourceId && it.alive && it.amount > 0 }
        if (res == null) {
            // Auto-find another node of the same kind.
            res = game.nearestResource(u.x, u.y, u.carrying ?: ResourceKind.WOOD)
                ?: run { u.order = Order.IDLE; return }
            u.assignedResourceId = res.id
            u.carrying = res.kind
        }
        val dx = res.x - u.x; val dy = res.y - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d > 28f) {
            u.goalX = res.x; u.goalY = res.y
            if (u.path.isEmpty()) {
                val p = game.pathfinder.find(u.x, u.y, res.x, res.y)
                if (p != null) { u.path = p; u.pathIndex = 2 }
            }
            stepMove(u, dt)
        } else {
            u.path.clear()
            u.facing = if (dx < 0) -1 else 1
            u.animState = 3
            u.swingPhase += dt
            if (u.swingPhase > 0.7f) {
                u.swingPhase = 0f
                val take = min(8, res.amount)
                res.amount -= take
                u.carryAmount += take
                if (res.animal != null) { res.hp -= 12f; res.panic = 6f; if (res.hp <= 0f) res.alive = false }
                if (res.amount <= 0) { res.alive = false; game.dirtyPath = true }
                if (u.carryAmount >= 14) {
                    u.order = Order.RETURN_RESOURCE
                    u.path.clear()
                }
            }
        }
    }

    private fun stepReturn(u: GameUnit, dt: Float) {
        val drop = game.nearestDropoff(u.x, u.y, u.faction) ?: run { u.order = Order.IDLE; return }
        val dx = drop.x - u.x; val dy = drop.y - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d > drop.w * 0.55f) {
            u.goalX = drop.x; u.goalY = drop.y
            if (u.path.isEmpty()) {
                val p = game.pathfinder.find(u.x, u.y, drop.x, drop.y)
                if (p != null) { u.path = p; u.pathIndex = 2 }
            }
            stepMove(u, dt)
        } else {
            // Deposit and head back to harvest.
            val f = game.factions[u.faction]
            when (u.carrying) {
                ResourceKind.WOOD -> f.wood += u.carryAmount
                ResourceKind.GOLD -> f.gold += u.carryAmount
                ResourceKind.FOOD -> f.food += u.carryAmount
                null -> {}
            }
            u.carryAmount = 0
            val again = u.assignedResourceId
            if (again >= 0 && game.resources.any { it.id == again && it.alive && it.amount > 0 }) {
                u.order = Order.HARVEST
            } else {
                val r = game.nearestResource(u.x, u.y, u.carrying ?: ResourceKind.WOOD)
                if (r != null) { u.assignedResourceId = r.id; u.order = Order.HARVEST }
                else u.order = Order.IDLE
            }
            u.path.clear()
        }
    }

    private fun stepBuild(u: GameUnit, dt: Float) {
        val site = game.buildings.firstOrNull { it.id == u.targetId && it.alive }
            ?: run { u.order = Order.IDLE; return }
        val dx = site.x - u.x; val dy = site.y - u.y
        val d = sqrt(dx * dx + dy * dy)
        val reach = max(site.w, site.h) * 0.55f
        if (d > reach) {
            u.goalX = site.x; u.goalY = site.y
            if (u.path.isEmpty()) {
                val p = game.pathfinder.find(u.x, u.y, site.x, site.y)
                if (p != null) { u.path = p; u.pathIndex = 2 }
            }
            stepMove(u, dt)
        } else {
            u.path.clear()
            u.animState = 3 // hammer
            if (site.buildProgress < 1f) {
                site.buildProgress = (site.buildProgress + dt / site.type.buildTime).coerceAtMost(1f)
                site.hp = (site.hp + (site.type.hp / site.type.buildTime) * dt).coerceAtMost(site.type.hp.toFloat())
                if (site.buildProgress >= 1f) {
                    game.factions[site.faction].popCap += site.type.popCap
                    game.dirtyPath = true
                }
            } else if (site.hp < site.maxHp) {
                site.hp = (site.hp + 14f * dt).coerceAtMost(site.maxHp)
            } else {
                u.order = Order.IDLE
            }
        }
    }

    private fun stepHeal(u: GameUnit, dt: Float) {
        // Routed via passive heal aura. Just idle in place.
        u.animState = 4
    }

    // ---------------------------------------------------------------- projectiles / effects

    private fun updateProjectiles(dt: Float) {
        val it = game.projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            val target = game.findEntity(p.targetId)
            if (target != null && target.alive) {
                p.tx = target.x; p.ty = target.y - 16f
            }
            val dx = p.tx - p.x; val dy = p.ty - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < 14f) {
                if (target != null && target.alive && target.faction != p.faction) {
                    target.hp -= p.damage.toFloat()
                    game.effects += Effect(target.x, target.y - 16f, 0f, 0.25f, EffectKind.HIT)
                    if (target.hp <= 0f) target.alive = false
                }
                it.remove(); continue
            }
            val mv = p.speed * dt
            p.x += dx / d * mv; p.y += dy / d * mv
        }
    }

    private fun updateEffects(dt: Float) {
        val it = game.effects.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.t += dt
            if (e.t > e.ttl) it.remove()
        }
    }

    private fun cleanup() {
        var i = game.units.size - 1
        while (i >= 0) {
            val u = game.units[i]
            if (!u.alive) {
                game.factions[u.faction].popUsed -= u.type.pop
                game.units.removeAt(i)
                game.effects += Effect(u.x, u.y - 16f, 0f, 0.5f, EffectKind.BOOM)
                game.selection.remove(u.id)
            }
            i--
        }
        i = game.buildings.size - 1
        while (i >= 0) {
            val b = game.buildings[i]
            if (!b.alive) {
                if (b.buildProgress >= 1f) game.factions[b.faction].popCap -= b.type.popCap
                game.buildings.removeAt(i)
                game.effects += Effect(b.x, b.y, 0f, 0.8f, EffectKind.BOOM)
                game.selection.remove(b.id)
                game.dirtyPath = true
            }
            i--
        }
        i = game.resources.size - 1
        while (i >= 0) {
            if (!game.resources[i].alive) game.resources.removeAt(i)
            i--
        }
    }

    private fun checkVictory() {
        for (f in game.factions) {
            f.alive = game.buildings.any { it.faction == f.id && it.alive } || game.units.any { it.faction == f.id && it.alive }
        }
        val survivors = game.factions.filter { it.alive }
        if (survivors.size == 1 && game.gameOver == -1) {
            game.gameOver = survivors[0].id
            game.paused = true
        }
    }

    // ---------------------------------------------------------------- public order helpers

    fun issueMove(u: GameUnit, x: Float, y: Float) {
        u.order = Order.MOVE; u.goalX = x; u.goalY = y; u.path.clear(); u.targetId = -1
    }
    fun issueAttack(u: GameUnit, target: Entity) {
        u.order = Order.ATTACK; u.targetId = target.id; u.path.clear()
    }
    fun issueAttackMove(u: GameUnit, x: Float, y: Float) {
        u.order = Order.ATTACK_MOVE; u.goalX = x; u.goalY = y; u.path.clear(); u.targetId = -1
    }
    fun issueHarvest(u: GameUnit, r: GameResource) {
        if (u.type != UnitType.WORKER) { issueAttack(u, r); return }
        u.order = Order.HARVEST; u.assignedResourceId = r.id; u.carrying = r.kind; u.path.clear()
    }
    fun issueBuild(u: GameUnit, site: Building) {
        u.order = Order.BUILD; u.targetId = site.id; u.path.clear()
    }
    fun issueStop(u: GameUnit) {
        u.order = Order.IDLE; u.path.clear(); u.targetId = -1
    }
}
