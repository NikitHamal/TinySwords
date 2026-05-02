package com.tinyswords.realmwar.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Per-frame systems that mutate [GameState]. Mirrors the JS `simulation.js`
 * but a lot tighter — many of the original micro-optimizations (per-bucket
 * spatial hashing) are dropped on Android because Kotlin lists with int
 * hashing are already fast enough for ~600 entity counts.
 */
internal class SimulationSystems(private val engine: GameEngine) {
    private val state get() = engine.state

    fun updateUnits(dt: Float) {
        val toRemove = ArrayList<Int>()
        for (u in state.units) {
            if (u.dead) { toRemove += state.units.indexOf(u); continue }
            u.cooldown = max(0f, u.cooldown - dt)
            u.attackTick = max(0f, u.attackTick - dt)
            updateOneUnit(u, dt)
            u.animTime += dt
        }
        // Remove dead units
        state.units.removeAll { it.dead }
    }

    private fun updateOneUnit(u: Unit, dt: Float) {
        when (u.state) {
            UnitState.IDLE -> idleBehavior(u)
            UnitState.MOVING -> movingBehavior(u, dt)
            UnitState.ATTACKING -> attackingBehavior(u, dt)
            UnitState.GATHERING -> gatheringBehavior(u, dt)
            UnitState.RETURNING -> returningBehavior(u, dt)
            UnitState.BUILDING -> buildingBehavior(u, dt)
            UnitState.DEAD -> Unit
        }
    }

    private fun idleBehavior(u: Unit) {
        if (u.def.role == UnitRole.HEALER) {
            // Look for a wounded ally nearby
            val wounded = state.units.firstOrNull {
                it.faction == u.faction && !it.dead && it !== u && it.hp < it.maxHp &&
                    distance(u.x, u.y, it.x, it.y) < u.def.rangePx
            }
            if (wounded != null) {
                u.targetId = wounded.id
                u.state = UnitState.ATTACKING
                return
            }
        }
        // Auto-engage hostiles within sight
        if (u.def.role != UnitRole.WORKER) {
            val sight = u.def.rangePx + 30f
            val enemy = nearestEnemy(u, sight)
            if (enemy != null) {
                u.targetId = enemy.id
                u.state = UnitState.ATTACKING
            }
        }
    }

    private fun movingBehavior(u: Unit, dt: Float) {
        val tx = u.targetX
        val ty = u.targetY
        if (tx == null || ty == null) {
            u.state = UnitState.IDLE; return
        }
        if (advance(u, tx, ty, dt, u.def.speedPx)) {
            u.targetX = null; u.targetY = null
            u.state = UnitState.IDLE
        }
    }

    private fun attackingBehavior(u: Unit, dt: Float) {
        val target = engine.entityById(u.targetId)
        if (target == null || target.dead) {
            u.state = UnitState.IDLE; u.targetId = -1; return
        }
        val d = distance(u.x, u.y, target.x, target.y)
        val rng = u.def.rangePx
        if (d > rng) {
            advance(u, target.x, target.y, dt, u.def.speedPx)
            return
        }
        if (u.cooldown > 0f) return
        u.cooldown = u.def.cooldownSec

        when (u.def.role) {
            UnitRole.RANGED -> fireProjectile(u, target)
            UnitRole.HEALER -> {
                // Heal: damage is negative
                if (target is Unit && target.faction == u.faction) {
                    target.hp = min(target.maxHp, target.hp - u.def.damage)
                    state.effects += Effect(EffectKind.HEAL, target.x, target.y - 18f, 0.6f)
                    engine.playSound("heal")
                }
            }
            UnitRole.MELEE, UnitRole.WORKER -> {
                target.hp -= u.def.damage.toFloat()
                state.effects += Effect(EffectKind.HIT, target.x, target.y, 0.25f)
                if (u.attackTick <= 0f) { engine.playSound("sword"); u.attackTick = 0.45f }
                if (target.hp <= 0f) target.dead = true
            }
        }
    }

    private fun fireProjectile(u: Unit, target: Entity) {
        val dx = target.x - u.x; val dy = target.y - u.y
        val len = hypot(dx, dy).coerceAtLeast(0.1f)
        val sp = 360f
        state.projectiles += Projectile(
            x = u.x, y = u.y - 6f,
            vx = dx / len * sp, vy = dy / len * sp,
            damage = u.def.damage, faction = u.faction,
            targetId = target.id, kind = ProjectileKind.ARROW,
        )
        engine.playSound("arrow")
    }

    private fun gatheringBehavior(u: Unit, dt: Float) {
        val res = state.resources.firstOrNull { it.id == u.targetId }
        if (res == null || res.dead || res.amount <= 0) {
            // Find replacement of same role
            val role = u.assignedRole
            val replacement = if (role != null) {
                state.resources
                    .filter { !it.dead && it.amount > 0 && it.resType == role }
                    .minByOrNull { distance(u.x, u.y, it.x, it.y) }
            } else null
            if (replacement != null) {
                u.targetId = replacement.id
                u.targetX = replacement.x; u.targetY = replacement.y
            } else {
                u.state = UnitState.IDLE; u.targetId = -1
            }
            return
        }
        val d = distance(u.x, u.y, res.x, res.y)
        if (d > 28f) {
            advance(u, res.x, res.y, dt, u.def.speedPx)
            u.harvesting = false
            return
        }
        u.harvesting = true
        // Tick harvesting
        u.cooldown -= dt
        if (u.cooldown <= 0f) {
            u.cooldown = 0.55f
            val take = 2
            res.amount -= take
            res.flash = 0.4f
            res.hurt = 0.3f
            u.carry = res.resType
            u.carryAmount = (u.carryAmount + take).coerceAtMost(8)
            if (u.carryAmount >= 8 || res.amount <= 0) {
                if (res.amount <= 0) { res.dead = true; res.depleted = true }
                u.state = UnitState.RETURNING
                u.harvesting = false
            }
        }
    }

    private fun returningBehavior(u: Unit, dt: Float) {
        val deposit = nearestDeposit(u) ?: run {
            u.state = UnitState.IDLE; return
        }
        val d = distance(u.x, u.y, deposit.x, deposit.y)
        if (d > 70f) {
            advance(u, deposit.x, deposit.y, dt, u.def.speedPx)
            return
        }
        // Deposit and resume harvesting
        val faction = state.factions.firstOrNull { it.def.id == u.faction } ?: return
        when (u.carry) {
            ResourceType.WOOD -> faction.res.wood += u.carryAmount
            ResourceType.GOLD -> faction.res.gold += u.carryAmount
            ResourceType.FOOD -> faction.res.food += u.carryAmount
            null -> Unit
        }
        u.carryAmount = 0
        // Resume gathering same resource
        u.state = UnitState.GATHERING
    }

    private fun buildingBehavior(u: Unit, dt: Float) {
        val b = state.buildings.firstOrNull { it.id == u.targetId } ?: run {
            u.state = UnitState.IDLE; return
        }
        if (b.dead || b.phase == BuildingPhase.COMPLETE) {
            u.state = UnitState.IDLE; u.targetId = -1; return
        }
        val d = distance(u.x, u.y, b.x, b.y)
        if (d > 40f) {
            advance(u, b.x, b.y, dt, u.def.speedPx)
            return
        }
        // Build
        b.buildProgress = min(1f, b.buildProgress + dt / b.def.buildTimeSec)
        b.hp = min(b.maxHp, b.maxHp * (0.18f + 0.82f * b.buildProgress))
        if (b.buildProgress >= 1f) {
            b.phase = BuildingPhase.COMPLETE
            engine.showToast("${b.def.type.displayName} completed!", 2f)
            u.state = UnitState.IDLE
            u.targetId = -1
        }
    }

    private fun advance(u: Unit, tx: Float, ty: Float, dt: Float, speed: Float): Boolean {
        val dx = tx - u.x; val dy = ty - u.y
        val d = hypot(dx, dy)
        if (d < 1.5f) return true
        val step = min(d, speed * dt)
        u.x += dx / d * step
        u.y += dy / d * step
        u.facing = if (dx < 0f) -1f else 1f
        u.vx = dx / d * speed; u.vy = dy / d * speed
        return d <= step + 0.5f
    }

    private fun nearestEnemy(u: Unit, range: Float): Entity? {
        var best: Entity? = null
        var bestD = range * range
        for (other in state.units) {
            if (other.dead || other.faction == u.faction) continue
            val dx = other.x - u.x; val dy = other.y - u.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = other }
        }
        for (b in state.buildings) {
            if (b.dead || b.faction == u.faction) continue
            val dx = b.x - u.x; val dy = b.y - u.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) { bestD = d2; best = b }
        }
        return best
    }

    private fun nearestDeposit(u: Unit): Building? {
        return state.buildings
            .filter { !it.dead && it.faction == u.faction &&
                (it.type == BuildingType.CASTLE || it.type == BuildingType.HOUSE) &&
                it.phase == BuildingPhase.COMPLETE }
            .minByOrNull { distance(u.x, u.y, it.x, it.y) }
    }

    fun updateBuildings(dt: Float) {
        val finished = ArrayList<Pair<Building, UnitType>>()
        for (b in state.buildings) {
            if (b.dead) continue
            // Tower auto-attack
            if (b.def.tower) towerAttack(b, dt)
            // Training
            val type = b.trainingType
            if (type != null) {
                b.trainingTimeLeft -= dt
                if (b.trainingTimeLeft <= 0f) {
                    finished += b to type
                    b.trainingType = b.trainQueue.removeFirstOrNull()
                    b.trainingTimeLeft = b.trainingType?.let { UNIT_DEFS[it]!!.buildTimeSec } ?: 0f
                }
            }
        }
        // Spawn finished units
        for ((b, type) in finished) {
            val def = UNIT_DEFS[type]!!
            val a = state.rng.nextFloat() * (Math.PI * 2).toFloat()
            val rr = b.def.placeH * 0.7f + 32f
            val sx = b.x + cos(a) * rr
            val sy = b.y + sin(a) * rr + 40f
            val u = Unit(state.nextEntityId(), type, def)
            u.x = sx; u.y = sy
            u.maxHp = def.maxHp.toFloat(); u.hp = u.maxHp
            u.faction = b.faction
            state.units += u
            val faction = state.factions.firstOrNull { it.def.id == b.faction } ?: continue
            faction.popUsed += def.popCost
            // Walk to rally if set
            val rx = b.rallyX; val ry = b.rallyY
            if (rx != null && ry != null) {
                u.targetX = rx; u.targetY = ry
                u.state = UnitState.MOVING
            }
        }
    }

    private fun towerAttack(b: Building, dt: Float) {
        b.attackCd -= dt
        if (b.attackCd > 0f) return
        val range = b.def.range
        val target = state.units.firstOrNull {
            !it.dead && it.faction != b.faction && distance(it.x, it.y, b.x, b.y) < range
        } ?: return
        b.attackCd = 1.1f
        val dx = target.x - b.x; val dy = target.y - b.y
        val len = hypot(dx, dy).coerceAtLeast(0.1f)
        state.projectiles += Projectile(
            x = b.x, y = b.y - 18f,
            vx = dx / len * 380f, vy = dy / len * 380f,
            damage = 14, faction = b.faction, targetId = target.id, kind = ProjectileKind.ARROW,
        )
        engine.playSound("arrow")
    }

    fun updateProjectiles(dt: Float) {
        val it = state.projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            val target = engine.entityById(p.targetId)
            val hit = target != null && !target.dead &&
                distance(p.x, p.y, target.x, target.y) < 14f
            if (hit) {
                if (target!!.faction != p.faction) {
                    target.hp -= p.damage
                    if (target.hp <= 0f) target.dead = true
                    state.effects += Effect(EffectKind.HIT, p.x, p.y, 0.30f)
                    engine.playSound("arrowHit")
                }
                it.remove()
            } else if (p.life <= 0f) it.remove()
        }
    }

    fun updateEffects(dt: Float) {
        val it = state.effects.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.time -= dt
            if (e.time <= 0f) it.remove()
        }
    }

    fun updateResources(dt: Float) {
        for (r in state.resources) {
            r.flash = max(0f, r.flash - dt * 3.5f)
            r.hurt = max(0f, r.hurt - dt)
            // Sheep wander randomly
            if (r.animal && !r.dead && r.amount > 0) {
                r.wanderCd -= dt
                if (r.wanderCd <= 0f) {
                    r.wanderCd = 1.6f + state.rng.nextFloat() * 2.5f
                    val ang = state.rng.nextFloat() * (Math.PI * 2).toFloat()
                    val sp = 6f + state.rng.nextFloat() * 6f
                    val nx = r.x + cos(ang) * sp * 4f
                    val ny = r.y + sin(ang) * sp * 4f
                    if (state.isLand(nx, ny)) {
                        r.x = nx; r.y = ny
                        r.face = if (cos(ang) < 0f) -1f else 1f
                    }
                }
            }
        }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)
}

/** AI commander for a single hostile faction. */
internal class AiSystems(private val engine: GameEngine) {
    private val state get() = engine.state

    fun update(dt: Float) {
        for (faction in state.factions) {
            if (!faction.ai || !faction.alive) continue
            faction.ai_state.timer += dt
            faction.ai_state.buildTimer += dt
            faction.ai_state.attackTimer -= dt

            // Economic loop: keep workers busy
            keepEconomyRunning(faction)

            // Build training queue at production buildings
            if (faction.ai_state.timer > 6f) {
                faction.ai_state.timer = 0f
                queueTroopProduction(faction)
            }
            // Build new buildings when affordable
            if (faction.ai_state.buildTimer > 14f) {
                faction.ai_state.buildTimer = 0f
                tryAiBuild(faction)
            }
            // Launch attack waves
            if (faction.ai_state.attackTimer <= 0f) {
                launchAttackWave(faction)
                faction.ai_state.attackTimer = state.settings.difficulty.aiAttackDelay +
                    state.rng.nextFloat() * 12f
            }
        }
    }

    private fun keepEconomyRunning(faction: Faction) {
        val workers = state.units.filter { it.faction == faction.def.id && it.type == UnitType.WORKER && !it.dead }
        for (w in workers) {
            if (w.state == UnitState.IDLE && w.assignedRole == null) {
                val role = pickRole(faction)
                val target = closestResourceForRole(role, w.x, w.y) ?: continue
                w.assignedRole = role
                w.targetId = target.id
                w.targetX = target.x; w.targetY = target.y
                w.state = UnitState.GATHERING
            }
        }
    }

    private fun pickRole(faction: Faction): ResourceType {
        val r = faction.res
        val woodNeed = if (r.wood < 200) 3 else 1
        val goldNeed = if (r.gold < 250) 4 else 2
        val foodNeed = if (r.food < 60) 2 else 1
        val total = woodNeed + goldNeed + foodNeed
        val pick = state.rng.nextInt(total)
        return when {
            pick < woodNeed -> ResourceType.WOOD
            pick < woodNeed + goldNeed -> ResourceType.GOLD
            else -> ResourceType.FOOD
        }
    }

    private fun closestResourceForRole(role: ResourceType, x: Float, y: Float): ResourceNode? =
        state.resources
            .filter { !it.dead && it.amount > 0 && it.resType == role }
            .minByOrNull { hypot(it.x - x, it.y - y) }

    private fun queueTroopProduction(faction: Faction) {
        val barracks = state.buildings.firstOrNull {
            it.faction == faction.def.id && !it.dead && it.type == BuildingType.BARRACKS &&
                it.phase == BuildingPhase.COMPLETE
        }
        val archery = state.buildings.firstOrNull {
            it.faction == faction.def.id && !it.dead && it.type == BuildingType.ARCHERY &&
                it.phase == BuildingPhase.COMPLETE
        }
        val castle = state.buildings.firstOrNull {
            it.faction == faction.def.id && !it.dead && it.type == BuildingType.CASTLE &&
                it.phase == BuildingPhase.COMPLETE
        }

        // Always have ~6 workers per castle
        val workers = state.units.count { it.faction == faction.def.id && it.type == UnitType.WORKER && !it.dead }
        if (castle != null && workers < 6 && faction.popUsed + 1 <= faction.popCap) {
            tryAiTrain(castle, faction, UnitType.WORKER)
        }

        if (barracks != null) tryAiTrain(barracks, faction, UnitType.WARRIOR)
        if (archery != null) tryAiTrain(archery, faction, UnitType.ARCHER)
    }

    private fun tryAiTrain(b: Building, faction: Faction, type: UnitType) {
        val def = UNIT_DEFS[type]!!
        if (faction.popUsed + def.popCost > faction.popCap) return
        if (!faction.res.canAfford(def.cost)) return
        if (b.trainingType != null && b.trainQueue.size > 1) return
        faction.res.pay(def.cost)
        b.trainQueue.addLast(type)
        if (b.trainingType == null) {
            b.trainingType = b.trainQueue.removeFirst()
            b.trainingTimeLeft = def.buildTimeSec
        }
    }

    private fun tryAiBuild(faction: Faction) {
        val have = mutableMapOf<BuildingType, Int>().withDefault { 0 }
        for (b in state.buildings) if (b.faction == faction.def.id && !b.dead)
            have[b.type] = (have[b.type] ?: 0) + 1

        val priority = listOf(
            BuildingType.HOUSE,
            BuildingType.BARRACKS,
            BuildingType.ARCHERY,
            BuildingType.HOUSE,
            BuildingType.TOWER,
            BuildingType.MONASTERY,
        )
        val target = priority.firstOrNull { type ->
            val cap = when (type) {
                BuildingType.HOUSE -> 4
                BuildingType.BARRACKS -> 1
                BuildingType.ARCHERY -> 1
                BuildingType.MONASTERY -> 1
                BuildingType.TOWER -> 3
                else -> 0
            }
            (have[type] ?: 0) < cap && faction.res.canAfford(BUILDING_DEFS[type]!!.cost)
        } ?: return

        val def = BUILDING_DEFS[target]!!
        val rr = state.rng.nextFloat() * (Math.PI * 2).toFloat()
        val rad = 220f + state.rng.nextFloat() * 280f
        val wx = faction.basePos.x + cos(rr) * rad
        val wy = faction.basePos.y + sin(rr) * rad
        if (!state.isLand(wx, wy)) return
        if (!engine.isValidPlacement(target, wx, wy)) return
        faction.res.pay(def.cost)
        val b = Building(state.nextEntityId(), target, def)
        b.x = wx; b.y = wy
        b.maxHp = def.hp.toFloat(); b.hp = b.maxHp
        b.faction = faction.def.id
        b.phase = BuildingPhase.COMPLETE
        b.buildProgress = 1f
        state.buildings += b
        if (target == BuildingType.HOUSE) faction.popCap += def.popProvided
    }

    private fun launchAttackWave(faction: Faction) {
        val troops = state.units.filter {
            it.faction == faction.def.id && !it.dead &&
                (it.type == UnitType.WARRIOR || it.type == UnitType.ARCHER || it.type == UnitType.LANCER)
        }
        if (troops.size < state.settings.difficulty.aiSquadMin) return
        val player = state.playerFaction()
        val targetX = player.basePos.x
        val targetY = player.basePos.y
        for (t in troops) {
            t.targetX = targetX + (state.rng.nextFloat() - 0.5f) * 240f
            t.targetY = targetY + (state.rng.nextFloat() - 0.5f) * 240f
            t.state = UnitState.MOVING
            t.targetId = -1
        }
        engine.showToast("${faction.def.displayName} marches on your realm!", 3f)
        engine.playSound("battle")
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)
}
