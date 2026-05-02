package com.tinyswords.realmwar

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SpriteBank(context: Context) {
    private val assets = context.assets
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(path: String): ImageBitmap? = cache.getOrPut(path) {
        runCatching {
            assets.open(path).use { BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull() ?: return null
    }

    fun building(faction: FactionId, kind: BuildingKind, variant: Int = 1): ImageBitmap? {
        val file = if (kind == BuildingKind.House && variant in 2..3) "House$variant.png" else kind.file
        return get("Tiny Swords (Free Pack)/Buildings/${faction.folder} Buildings/$file")
    }

    fun unit(faction: FactionId, kind: UnitKind, anim: String, carry: ResourceKind? = null): ImageBitmap? {
        val folder = "Tiny Swords (Free Pack)/Units/${faction.folder} Units"
        val file = when (kind) {
            UnitKind.Worker -> when {
                carry == ResourceKind.Wood -> "Pawn/Pawn_Run Wood.png"
                carry == ResourceKind.Gold -> "Pawn/Pawn_Run Gold.png"
                carry == ResourceKind.Food -> "Pawn/Pawn_Run Meat.png"
                anim == "mine" -> "Pawn/Pawn_Interact Pickaxe.png"
                anim == "chop" -> "Pawn/Pawn_Interact Axe.png"
                anim == "build" -> "Pawn/Pawn_Interact Hammer.png"
                anim == "fight" -> "Pawn/Pawn_Interact Knife.png"
                anim == "run" -> "Pawn/Pawn_Run.png"
                else -> "Pawn/Pawn_Idle.png"
            }
            UnitKind.Warrior -> when (anim) {
                "run" -> "Warrior/Warrior_Run.png"
                "attack" -> "Warrior/Warrior_Attack1.png"
                else -> "Warrior/Warrior_Idle.png"
            }
            UnitKind.Archer -> when (anim) {
                "run" -> "Archer/Archer_Run.png"
                "attack" -> "Archer/Archer_Shoot.png"
                else -> "Archer/Archer_Idle.png"
            }
            UnitKind.Lancer -> when (anim) {
                "run" -> "Lancer/Lancer_Run.png"
                "attack" -> "Lancer/Lancer_Right_Attack.png"
                else -> "Lancer/Lancer_Idle.png"
            }
            UnitKind.Monk -> when (anim) {
                "run" -> "Monk/Run.png"
                "attack" -> "Monk/Heal.png"
                else -> "Monk/Idle.png"
            }
        }
        return get("$folder/$file")
    }

    fun animal(kind: AnimalKind, anim: String): ImageBitmap? {
        val base = "CraftPix Hunt Animals/${kind.folder}"
        val prefix = when (kind) {
            AnimalKind.Grouse -> "Black_grouse"
            else -> kind.label
        }
        val file = when (anim) {
            "run" -> if (kind == AnimalKind.Grouse) "${prefix}_Flight.png" else "${prefix}_Run.png"
            "walk" -> if (kind == AnimalKind.Fox) "Fox_walk.png" else "${prefix}_Walk.png"
            "hurt" -> "${prefix}_Hurt.png"
            "death" -> "${prefix}_Death.png"
            else -> "${prefix}_Idle.png"
        }
        return get("$base/$file")
    }

    fun terrain(name: String): ImageBitmap? = get("Tiny Swords (Free Pack)/Terrain/$name")
    fun ui(name: String): ImageBitmap? = get("Tiny Swords (Free Pack)/UI Elements/UI Elements/$name")
}

class TinySwordsEngine(private val context: Context) {
    val sprites = SpriteBank(context)
    private val rng = Random(7727)
    private var nextId = 1
    private val cols = ceil(WORLD_WIDTH / TILE_SIZE).toInt()
    private val rows = ceil(WORLD_HEIGHT / TILE_SIZE).toInt()
    private val land = BooleanArray(cols * rows)

    val factions = FactionId.entries.associateWith { faction ->
        FactionState(
            faction,
            Vec2(WORLD_WIDTH * faction.baseFx, WORLD_HEIGHT * faction.baseFy),
            if (faction == FactionId.Blue) Stock(wood = 420, gold = 360, food = 18) else Stock(wood = 650, gold = 540, food = 35)
        )
    }
    val units = mutableListOf<UnitEntity>()
    val buildings = mutableListOf<BuildingEntity>()
    val resources = mutableListOf<ResourceEntity>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<Effect>()
    val selected = mutableListOf<Any>()
    val camera = Vec2(700f, 720f)
    var zoom = 1f
    var time = 0f
    var paused = false
    var placing: BuildingKind? = null
    var toast = "Scout, hunt wildlife, gather, build, train, and conquer rival realms."
    var toastTimer = 5f
    var gameOver = false

    init {
        generateWorld()
        spawnStartingBases()
        spawnResources()
        camera.x = factions.getValue(FactionId.Blue).base.x - 640f
        camera.y = factions.getValue(FactionId.Blue).base.y - 360f
    }

    fun update(dtIn: Float) {
        if (paused || gameOver) return
        val dt = dtIn.coerceIn(0f, 1f / 24f)
        time += dt
        toastTimer = max(0f, toastTimer - dt)
        updateAnimals(dt)
        updateBuildings(dt)
        updateUnits(dt)
        updateProjectiles(dt)
        updateEffects(dt)
        updateAi(dt)
        cleanup()
        checkVictory()
    }

    fun playerStock(): Stock = factions.getValue(FactionId.Blue).res

    fun population(fid: FactionId): Population {
        val used = units.filter { it.faction == fid && !it.dead }.sumOf { it.kind.pop }
        val cap = maxPop(buildings.filter { it.faction == fid && !it.dead && it.build >= 1f }.sumOf { it.kind.pop })
        return Population(used, cap)
    }

    fun train(kind: UnitKind) {
        val producer = selected.filterIsInstance<BuildingEntity>()
            .filter { it.faction == FactionId.Blue && it.kind.trains.contains(kind) && it.build >= 1f && !it.dead }
            .minByOrNull { it.queue.size }
        if (producer == null) {
            show("Select a ${producerLabel(kind)} first.")
            return
        }
        val pop = population(FactionId.Blue)
        if (pop.used + kind.pop > pop.cap) {
            show("Population cap reached. Build houses.")
            return
        }
        if (!playerStock().pay(kind.cost)) {
            show("Not enough resources for ${kind.label}.")
            return
        }
        producer.queue += TrainItem(kind, kind.trainTime)
        show("Training ${kind.label}.", 1.2f)
    }

    fun startPlacing(kind: BuildingKind) {
        placing = kind
        show("Place ${kind.label} on grass near clear ground.")
    }

    fun cancelPlacing() {
        placing = null
    }

    fun handleTap(screenX: Float, screenY: Float, screenW: Float, screenH: Float) {
        val world = screenToWorld(screenX, screenY, screenW, screenH)
        placing?.let { kind ->
            if (placeBuilding(kind, world.x, world.y)) placing = null
            return
        }
        val hit = pick(world.x, world.y)
        if (hit != null && isPlayerSelectable(hit)) {
            clearSelection()
            select(hit)
            return
        }
        if (selected.filterIsInstance<UnitEntity>().isNotEmpty()) {
            issueSmartOrder(world.x, world.y, hit)
        } else if (hit != null) {
            clearSelection()
            select(hit)
        }
    }

    fun dragSelect(x0: Float, y0: Float, x1: Float, y1: Float, screenW: Float, screenH: Float) {
        val a = screenToWorld(x0, y0, screenW, screenH)
        val b = screenToWorld(x1, y1, screenW, screenH)
        val left = min(a.x, b.x)
        val right = max(a.x, b.x)
        val top = min(a.y, b.y)
        val bottom = max(a.y, b.y)
        clearSelection()
        units.filter { it.faction == FactionId.Blue && !it.dead && it.x in left..right && it.y in top..bottom }.forEach { select(it) }
        if (selected.isEmpty()) pick((a.x + b.x) * .5f, (a.y + b.y) * .5f)?.takeIf(::isPlayerSelectable)?.let(::select)
    }

    fun pan(dx: Float, dy: Float) {
        camera.x = (camera.x - dx / zoom).coerceIn(0f, WORLD_WIDTH - 400f)
        camera.y = (camera.y - dy / zoom).coerceIn(0f, WORLD_HEIGHT - 300f)
    }

    fun focusHome() {
        val base = factions.getValue(FactionId.Blue).base
        camera.x = (base.x - 640f / zoom).coerceIn(0f, WORLD_WIDTH)
        camera.y = (base.y - 360f / zoom).coerceIn(0f, WORLD_HEIGHT)
    }

    fun adjustZoom(delta: Float) {
        zoom = (zoom + delta).coerceIn(.7f, 1.6f)
    }

    private fun generateWorld() {
        fun addEllipse(cx: Float, cy: Float, rx: Float, ry: Float) {
            val minX = floor((cx - rx) / TILE_SIZE).toInt().coerceAtLeast(0)
            val maxX = ceil((cx + rx) / TILE_SIZE).toInt().coerceAtMost(cols - 1)
            val minY = floor((cy - ry) / TILE_SIZE).toInt().coerceAtLeast(0)
            val maxY = ceil((cy + ry) / TILE_SIZE).toInt().coerceAtMost(rows - 1)
            for (ty in minY..maxY) for (tx in minX..maxX) {
                val x = tx * TILE_SIZE + TILE_SIZE * .5f
                val y = ty * TILE_SIZE + TILE_SIZE * .5f
                val wobble = .86f + hash(tx, ty, 17) * .28f
                val dx = (x - cx) / (rx * wobble)
                val dy = (y - cy) / (ry * wobble)
                if (dx * dx + dy * dy < 1f) land[ty * cols + tx] = true
            }
        }
        fun addBridge(a: Vec2, b: Vec2, width: Float) {
            val steps = (hypot(a.x - b.x, a.y - b.y) / (TILE_SIZE * .3f)).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t + sin(t * PI * 4).toFloat() * 55f
                addEllipse(x, y, width, width * .72f)
            }
        }
        val bases = FactionId.entries.map { factions.getValue(it).base }
        bases.forEach { addEllipse(it.x, it.y, 1080f, 820f) }
        val center = Vec2(WORLD_WIDTH * .5f, WORLD_HEIGHT * .5f)
        addEllipse(center.x, center.y, 1750f, 1180f)
        bases.forEach { addBridge(it, center, 270f) }
        addBridge(bases[0], bases[1], 235f)
        addBridge(bases[0], bases[2], 235f)
        addBridge(bases[1], bases[3], 235f)
        addBridge(bases[2], bases[3], 235f)
        repeat(34) {
            addEllipse(
                rng.nextFloat() * WORLD_WIDTH,
                rng.nextFloat() * WORLD_HEIGHT,
                rng.nextFloat() * 520f + 230f,
                rng.nextFloat() * 360f + 190f
            )
        }
    }

    private fun spawnStartingBases() {
        FactionId.entries.forEachIndexed { index, faction ->
            val base = factions.getValue(faction).base
            addBuilding(faction, BuildingKind.Castle, base.x, base.y, true)
            addBuilding(faction, BuildingKind.House, base.x - 210f, base.y + 110f, true)
            addBuilding(faction, BuildingKind.Barracks, base.x + 230f, base.y + 100f, true)
            if (index % 2 == 0) addBuilding(faction, BuildingKind.Archery, base.x + 115f, base.y - 155f, true)
            addBuilding(faction, BuildingKind.Tower, base.x - 280f, base.y - 105f, true)
            val unitCount = if (faction == FactionId.Blue) 5 else 8
            repeat(unitCount) { addUnit(faction, UnitKind.Worker, base.x - 90f + it * 26f, base.y + 210f) }
            addUnit(faction, UnitKind.Warrior, base.x + 130f, base.y + 220f)
            addUnit(faction, UnitKind.Archer, base.x + 165f, base.y + 245f)
        }
    }

    private fun spawnResources() {
        FactionId.entries.forEach { faction ->
            val base = factions.getValue(faction).base
            repeat(24) {
                val angle = rng.nextFloat() * PI.toFloat() * 2f
                val radius = 360f + rng.nextFloat() * 660f
                addResource(ResourceKind.Wood, base.x + cos(angle) * radius, base.y + sin(angle) * radius)
            }
            repeat(9) {
                val angle = rng.nextFloat() * PI.toFloat() * 2f
                val radius = 470f + rng.nextFloat() * 520f
                addResource(ResourceKind.Gold, base.x + cos(angle) * radius, base.y + sin(angle) * radius)
            }
            repeat(12) {
                val angle = rng.nextFloat() * PI.toFloat() * 2f
                val radius = 520f + rng.nextFloat() * 860f
                addResource(ResourceKind.Food, base.x + cos(angle) * radius, base.y + sin(angle) * radius)
            }
        }
        repeat(280) {
            val kind = when {
                it % 5 == 0 -> ResourceKind.Gold
                it % 3 == 0 -> ResourceKind.Food
                else -> ResourceKind.Wood
            }
            addResource(kind, rng.nextFloat() * WORLD_WIDTH, rng.nextFloat() * WORLD_HEIGHT)
        }
    }

    private fun addResource(kind: ResourceKind, xIn: Float, yIn: Float): ResourceEntity? {
        val point = nearestLand(xIn, yIn, 260f) ?: return null
        if (buildings.any { !it.dead && distance2(point.x, point.y, it.x, it.y) < 210f * 210f }) return null
        if (resources.any { !it.dead && distance2(point.x, point.y, it.x, it.y) < (it.radius + 28f) * (it.radius + 28f) }) return null
        val animal = if (kind == ResourceKind.Food) chooseAnimal(point.x, point.y) else null
        val amount = when (kind) {
            ResourceKind.Wood -> 105 + rng.nextInt(80)
            ResourceKind.Gold -> 160 + rng.nextInt(120)
            ResourceKind.Food -> (animal?.yield ?: 18) + rng.nextInt(8)
        }
        val sprite = when (kind) {
            ResourceKind.Wood -> "Terrain/Resources/Wood/Trees/Tree${1 + rng.nextInt(4)}.png"
            ResourceKind.Gold -> "Terrain/Resources/Gold/Gold Stones/Gold Stone ${1 + rng.nextInt(6)}.png"
            ResourceKind.Food -> "Terrain/Resources/Meat/Meat Resource/Meat Resource.png"
        }
        return ResourceEntity(next(), kind, sprite, point.x, point.y, amount, amount, animal, directionRow = rng.nextInt(4)).also { resources += it }
    }

    private fun addBuilding(fid: FactionId, kind: BuildingKind, x: Float, y: Float, complete: Boolean): BuildingEntity {
        val point = nearestLand(x, y, 300f) ?: Vec2(x, y)
        val building = BuildingEntity(
            next(), fid, kind, point.x, point.y,
            hp = if (complete) kind.hp else kind.hp * .28f,
            build = if (complete) 1f else 0f,
            spriteVariant = 1 + rng.nextInt(3)
        )
        buildings += building
        return building
    }

    private fun addUnit(fid: FactionId, kind: UnitKind, x: Float, y: Float): UnitEntity {
        val point = nearestLand(x, y, 220f) ?: Vec2(x, y)
        return UnitEntity(next(), fid, kind, point.x, point.y, cooldown = rng.nextFloat() * .4f, anim = rng.nextFloat() * 5f).also { units += it }
    }

    private fun updateAnimals(dt: Float) {
        resources.forEach { res ->
            val animal = res.animal ?: return@forEach
            if (res.dead) return@forEach
            res.flash = max(0f, res.flash - dt * 4f)
            if (res.panic > 0f) {
                res.panic -= dt
                val nearest = units.filter { it.faction == FactionId.Blue && !it.dead }.minByOrNull { distance2(it.x, it.y, res.x, res.y) }
                if (nearest != null) {
                    val away = normalized(res.x - nearest.x, res.y - nearest.y)
                    res.vx += away.x * animal.runSpeed * dt * 2f
                    res.vy += away.y * animal.runSpeed * dt * 2f
                }
            } else if (rng.nextFloat() < dt * .18f) {
                val a = rng.nextFloat() * PI.toFloat() * 2f
                res.vx += cos(a) * animal.runSpeed * .22f
                res.vy += sin(a) * animal.runSpeed * .22f
            }
            res.x += res.vx * dt
            res.y += res.vy * dt
            if (isWater(res.x, res.y)) {
                val landPoint = nearestLand(res.x, res.y, 160f)
                if (landPoint != null) {
                    res.x = landPoint.x
                    res.y = landPoint.y
                }
                res.vx *= -.35f
                res.vy *= -.35f
            }
            res.vx *= .92f
            res.vy *= .92f
            if (abs(res.vx) + abs(res.vy) > 3f) res.directionRow = directionRow(res.vx, res.vy)
        }
    }

    private fun updateBuildings(dt: Float) {
        buildings.forEach { b ->
            if (b.dead) return@forEach
            b.flash = max(0f, b.flash - dt * 3f)
            if (b.build < 1f) b.build = (b.build + dt / b.kind.buildTime).coerceAtMost(1f)
            if (b.queue.isNotEmpty() && b.build >= 1f) {
                val item = b.queue.first()
                item.remaining -= dt
                if (item.remaining <= 0f) {
                    b.queue.removeAt(0)
                    val rally = b.rally ?: Vec2(b.x, b.y + 180f)
                    val unit = addUnit(b.faction, item.kind, b.x, b.y + 120f)
                    commandMove(unit, rally.x, rally.y)
                }
            }
            if (b.kind.range > 0f && b.build >= 1f) {
                b.cooldown -= dt
                val target = nearestEnemy(b.faction, b.x, b.y, b.kind.range, includeBuildings = false)
                if (target != null && b.cooldown <= 0f) {
                    b.cooldown = .95f
                    projectiles += Projectile(next(), b.faction, b.x, b.y - 72f, target, 18f)
                }
            }
        }
    }

    private fun updateUnits(dt: Float) {
        units.forEach { u ->
            if (u.dead) return@forEach
            u.cooldown = max(0f, u.cooldown - dt)
            u.flash = max(0f, u.flash - dt * 4f)
            u.anim += dt * animationSpeed(u)
            when (u.order) {
                Order.Idle -> autoAcquire(u)
                Order.Move, Order.AttackMove -> updateMove(u, dt)
                Order.Attack -> updateAttack(u, dt)
                Order.Harvest -> updateHarvest(u, dt)
                Order.ReturnCargo -> updateReturnCargo(u, dt)
                Order.Build, Order.Repair -> updateRepairBuild(u, dt)
                Order.Heal -> updateHeal(u, dt)
            }
        }
    }

    private fun updateMove(u: UnitEntity, dt: Float) {
        val goal = u.goal ?: run { u.order = Order.Idle; return }
        moveToward(u, goal.x, goal.y, dt)
        if (distance2(u.x, u.y, goal.x, goal.y) < 24f * 24f) {
            u.goal = null
            u.order = Order.Idle
        } else if (u.order == Order.AttackMove) autoAcquire(u)
    }

    private fun updateAttack(u: UnitEntity, dt: Float) {
        val target = resolveTarget(u.target) ?: run { u.order = Order.Idle; u.target = null; return }
        if (target.faction == u.faction) { u.order = Order.Idle; return }
        val d2 = distance2(u.x, u.y, target.x, target.y)
        val range = u.kind.range + target.radius + 8f
        if (d2 > range * range) {
            moveToward(u, target.x, target.y, dt)
            return
        }
        u.face = if (target.x < u.x) -1f else 1f
        if (u.cooldown <= 0f) {
            u.cooldown = u.kind.cooldown
            if (u.kind == UnitKind.Archer) {
                projectiles += Projectile(next(), u.faction, u.x, u.y - 36f, u.target!!, u.kind.damage)
            } else if (u.kind == UnitKind.Monk) {
                u.order = Order.Heal
            } else {
                damage(target, u.kind.damage, u.faction)
            }
        }
    }

    private fun updateHeal(u: UnitEntity, dt: Float) {
        val ally = units.filter { it.faction == u.faction && !it.dead && it.hp < it.maxHp }
            .minByOrNull { distance2(u.x, u.y, it.x, it.y) } ?: run { u.order = Order.Idle; return }
        val d2 = distance2(u.x, u.y, ally.x, ally.y)
        if (d2 > u.kind.range * u.kind.range) moveToward(u, ally.x, ally.y, dt) else if (u.cooldown <= 0f) {
            u.cooldown = u.kind.cooldown
            ally.hp = (ally.hp + 16f).coerceAtMost(ally.maxHp)
            effects += Effect("heal", ally.x, ally.y - 34f, .6f)
        }
    }

    private fun updateHarvest(u: UnitEntity, dt: Float) {
        val res = resources.firstOrNull { it.id == u.target?.id && !it.dead } ?: run { u.order = Order.Idle; return }
        val interaction = res.radius + u.radius + 20f
        if (distance2(u.x, u.y, res.x, res.y) > interaction * interaction) {
            moveToward(u, res.x, res.y, dt)
            return
        }
        u.face = if (res.x < u.x) -1f else 1f
        u.gatherTimer += dt
        if (u.gatherTimer >= .8f) {
            u.gatherTimer = 0f
            if (res.animal != null) {
                res.animalHp -= 11f
                res.flash = 1f
                res.panic = 2.5f
                val away = normalized(res.x - u.x, res.y - u.y)
                res.vx += away.x * res.animal.runSpeed
                res.vy += away.y * res.animal.runSpeed
                effects += Effect("hit", res.x, res.y - 20f, .22f)
                if (res.animal.retaliate > 0f && distance2(u.x, u.y, res.x, res.y) < 55f * 55f && rng.nextFloat() < .42f) {
                    damage(LiveTarget.UnitTarget(u), res.animal.retaliate, FactionId.Black)
                }
                if (res.animalHp > 0f) return
            }
            val take = min(12, res.amount)
            res.amount -= take
            u.carryKind = res.kind
            u.carryAmount = take
            if (res.amount <= 0) res.dead = true
            u.order = Order.ReturnCargo
            val drop = nearestDropoff(u.faction, u.x, u.y)
            u.goal = drop?.let { Vec2(it.x, it.y) }
        }
    }

    private fun updateReturnCargo(u: UnitEntity, dt: Float) {
        val drop = nearestDropoff(u.faction, u.x, u.y) ?: run { u.order = Order.Idle; return }
        if (distance2(u.x, u.y, drop.x, drop.y) > 95f * 95f) {
            moveToward(u, drop.x, drop.y, dt)
            return
        }
        val carry = u.carryKind
        if (carry != null && u.carryAmount > 0) factions.getValue(u.faction).res.add(carry, u.carryAmount)
        u.carryKind = null
        u.carryAmount = 0
        val next = resources.filter { !it.dead }.minByOrNull { distance2(drop.x, drop.y, it.x, it.y) }
        if (next != null && u.faction != FactionId.Blue) {
            u.target = TargetRef(TargetKind.Resource, next.id)
            u.order = Order.Harvest
        } else u.order = Order.Idle
    }

    private fun updateRepairBuild(u: UnitEntity, dt: Float) {
        val b = buildings.firstOrNull { it.id == u.target?.id && !it.dead } ?: run { u.order = Order.Idle; return }
        val range = hypot(b.kind.placeW * .5f, b.kind.placeH * .5f) + u.radius + 16f
        if (distance2(u.x, u.y, b.x, b.y) > range * range) {
            moveToward(u, b.x, b.y, dt)
            return
        }
        u.gatherTimer += dt
        if (u.gatherTimer >= .25f) {
            u.gatherTimer = 0f
            if (b.build < 1f) b.build = (b.build + .035f).coerceAtMost(1f)
            b.hp = (b.hp + 5f).coerceAtMost(b.maxHp)
            if (b.build >= 1f && b.hp >= b.maxHp) u.order = Order.Idle
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val target = resolveTarget(p.target)
            p.ttl -= dt
            if (target == null || p.ttl <= 0f) {
                iterator.remove()
                continue
            }
            val dir = normalized(target.x - p.x, target.y - 18f - p.y)
            p.x += dir.x * 520f * dt
            p.y += dir.y * 520f * dt
            if (distance2(p.x, p.y, target.x, target.y - 18f) < 26f * 26f) {
                damage(target, p.damage, p.faction)
                effects += Effect("hit", target.x, target.y - 18f, .2f)
                iterator.remove()
            }
        }
    }

    private fun updateEffects(dt: Float) {
        effects.forEach { it.ttl -= dt }
        effects.removeAll { it.ttl <= 0f }
    }

    private fun updateAi(dt: Float) {
        FactionId.entries.filter { it.ai }.forEach { fid ->
            val faction = factions.getValue(fid)
            if (!faction.alive) return@forEach
            faction.aiTimer -= dt
            faction.attackTimer -= dt
            if (faction.aiTimer <= 0f) {
                faction.aiTimer = 2.2f + rng.nextFloat() * 1.4f
                runAiEconomy(fid)
            }
            if (faction.attackTimer <= 0f) {
                faction.attackTimer = 16f + rng.nextFloat() * 12f
                runAiAttack(fid)
            }
        }
    }

    private fun runAiEconomy(fid: FactionId) {
        val faction = factions.getValue(fid)
        faction.res.wood += 16
        faction.res.gold += 14
        faction.res.food += 3
        val pop = population(fid)
        val owned = buildings.filter { it.faction == fid && !it.dead }
        val workers = units.count { it.faction == fid && it.kind == UnitKind.Worker && !it.dead }
        if (pop.used + 3 >= pop.cap) tryAiBuild(fid, BuildingKind.House)
        if (owned.count { it.kind == BuildingKind.Barracks } < 2) tryAiBuild(fid, BuildingKind.Barracks)
        if (owned.count { it.kind == BuildingKind.Archery } < 1) tryAiBuild(fid, BuildingKind.Archery)
        if (workers < 10) aiTrain(fid, UnitKind.Worker)
        listOf(UnitKind.Warrior, UnitKind.Archer, UnitKind.Lancer, UnitKind.Monk).shuffled(rng).take(2).forEach { aiTrain(fid, it) }
        units.filter { it.faction == fid && it.kind == UnitKind.Worker && it.order == Order.Idle && !it.dead }.forEach { worker ->
            resources.filter { !it.dead }.minByOrNull { distance2(worker.x, worker.y, it.x, it.y) }?.let {
                worker.target = TargetRef(TargetKind.Resource, it.id)
                worker.order = Order.Harvest
            }
        }
    }

    private fun runAiAttack(fid: FactionId) {
        val army = units.filter { it.faction == fid && !it.dead && it.kind != UnitKind.Worker }
        if (army.size < 6) return
        val target = buildings.filter { it.faction != fid && !it.dead }.minByOrNull { b ->
            val base = factions.getValue(fid).base
            distance2(base.x, base.y, b.x, b.y) + if (b.kind == BuildingKind.Castle) -250_000f else 0f
        } ?: return
        army.shuffled(rng).take(10 + rng.nextInt(8)).forEach {
            it.target = TargetRef(TargetKind.Building, target.id)
            it.order = Order.Attack
        }
        if (target.faction == FactionId.Blue) show("${fid.title} is marching on your realm!", 2.5f)
    }

    private fun aiTrain(fid: FactionId, kind: UnitKind) {
        val pop = population(fid)
        if (pop.used + kind.pop > pop.cap) return
        val building = buildings.filter { it.faction == fid && it.build >= 1f && !it.dead && it.kind.trains.contains(kind) }.minByOrNull { it.queue.size } ?: return
        if (factions.getValue(fid).res.pay(kind.cost)) building.queue += TrainItem(kind, kind.trainTime)
    }

    private fun tryAiBuild(fid: FactionId, kind: BuildingKind): Boolean {
        val stock = factions.getValue(fid).res
        if (!stock.pay(kind.cost)) return false
        val base = factions.getValue(fid).base
        repeat(18) {
            val a = rng.nextFloat() * PI.toFloat() * 2f
            val r = 250f + rng.nextFloat() * 520f
            val x = base.x + cos(a) * r
            val y = base.y + sin(a) * r
            if (placementIssue(kind, x, y) == null) {
                addBuilding(fid, kind, x, y, false)
                return true
            }
        }
        stock.wood += kind.cost.wood
        stock.gold += kind.cost.gold
        stock.food += kind.cost.food
        return false
    }

    private fun placeBuilding(kind: BuildingKind, x: Float, y: Float): Boolean {
        val issue = placementIssue(kind, x, y)
        if (issue != null) {
            show(issue)
            return false
        }
        if (!playerStock().pay(kind.cost)) {
            show("Not enough resources for ${kind.label}.")
            return false
        }
        val b = addBuilding(FactionId.Blue, kind, x, y, false)
        selected.filterIsInstance<UnitEntity>().filter { it.kind == UnitKind.Worker }.forEach {
            it.target = TargetRef(TargetKind.Building, b.id)
            it.order = Order.Build
        }
        clearSelection()
        select(b)
        show("${kind.label} foundation placed.")
        return true
    }

    fun placementIssue(kind: BuildingKind, x: Float, y: Float): String? {
        if (!safeLand(x, y + kind.placeYOffset, 42f)) return "Buildings need clear grass."
        val left = x - kind.placeW * .5f
        val right = x + kind.placeW * .5f
        val top = y + kind.placeYOffset - kind.placeH * .5f
        val bottom = y + kind.placeYOffset + kind.placeH * .5f
        if (buildings.any { !it.dead && rectOverlap(left, top, right, bottom, it.footprintLeft - 10f, it.footprintTop - 10f, it.footprintRight + 10f, it.footprintBottom + 10f) }) return "Blocked by a building."
        if (resources.any { !it.dead && distance2(x, y, it.x, it.y) < (it.radius + 42f) * (it.radius + 42f) }) return "Too close to resources."
        return null
    }

    private fun issueSmartOrder(x: Float, y: Float, hit: Any?) {
        val selectedUnits = selected.filterIsInstance<UnitEntity>().filter { it.faction == FactionId.Blue && !it.dead }
        when {
            hit is ResourceEntity -> selectedUnits.filter { it.kind == UnitKind.Worker }.forEach {
                it.target = TargetRef(TargetKind.Resource, hit.id)
                it.order = Order.Harvest
            }
            hit is BuildingEntity && hit.faction == FactionId.Blue && hit.hp < hit.maxHp -> selectedUnits.filter { it.kind == UnitKind.Worker }.forEach {
                it.target = TargetRef(TargetKind.Building, hit.id)
                it.order = Order.Repair
            }
            hit is BuildingEntity && hit.faction != FactionId.Blue -> selectedUnits.forEach {
                it.target = TargetRef(TargetKind.Building, hit.id)
                it.order = Order.Attack
            }
            hit is UnitEntity && hit.faction != FactionId.Blue -> selectedUnits.forEach {
                it.target = TargetRef(TargetKind.Unit, hit.id)
                it.order = Order.Attack
            }
            else -> formationMove(selectedUnits, x, y)
        }
    }

    private fun formationMove(unitsToMove: List<UnitEntity>, x: Float, y: Float) {
        if (unitsToMove.isEmpty()) return
        val columns = ceil(kotlin.math.sqrt(unitsToMove.size.toFloat())).toInt().coerceAtLeast(1)
        unitsToMove.forEachIndexed { index, unit ->
            val row = index / columns
            val col = index % columns
            val ox = (col - (columns - 1) * .5f) * 42f
            val oy = row * 42f
            commandMove(unit, x + ox, y + oy)
        }
    }

    private fun commandMove(u: UnitEntity, x: Float, y: Float) {
        val p = nearestLand(x, y, 160f) ?: Vec2(x, y)
        u.goal = p
        u.target = null
        u.order = Order.Move
        u.carryKind = u.carryKind
    }

    private fun moveToward(u: UnitEntity, tx: Float, ty: Float, dt: Float) {
        val dx = tx - u.x
        val dy = ty - u.y
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val step = min(d, u.kind.speed * dt)
        val nx = u.x + dx / d * step
        val ny = u.y + dy / d * step
        if (!isWater(nx, ny)) {
            u.x = nx
            u.y = ny
            avoidCrowd(u, dt)
        } else nearestLand(u.x, u.y, 120f)?.let { u.x = it.x; u.y = it.y }
        u.face = if (dx < 0f) -1f else 1f
    }

    private fun avoidCrowd(u: UnitEntity, dt: Float) {
        units.forEach { other ->
            if (other === u || other.dead) return@forEach
            val minDistance = u.radius + other.radius + 2f
            val d2 = distance2(u.x, u.y, other.x, other.y)
            if (d2 in 1f..(minDistance * minDistance)) {
                val d = kotlin.math.sqrt(d2)
                u.x += (u.x - other.x) / d * 18f * dt
                u.y += (u.y - other.y) / d * 18f * dt
            }
        }
    }

    private fun autoAcquire(u: UnitEntity) {
        if (u.kind == UnitKind.Worker || u.kind == UnitKind.Monk) return
        val target = nearestEnemy(u.faction, u.x, u.y, u.kind.range + 95f, includeBuildings = false) ?: return
        u.target = target
        u.order = Order.Attack
    }

    private fun nearestEnemy(fid: FactionId, x: Float, y: Float, range: Float, includeBuildings: Boolean): TargetRef? {
        var best: TargetRef? = null
        var bestD = range * range
        units.forEach {
            if (!it.dead && it.faction != fid) {
                val d = distance2(x, y, it.x, it.y)
                if (d < bestD) { bestD = d; best = TargetRef(TargetKind.Unit, it.id) }
            }
        }
        if (includeBuildings) buildings.forEach {
            if (!it.dead && it.faction != fid) {
                val d = distance2(x, y, it.x, it.y)
                if (d < bestD) { bestD = d; best = TargetRef(TargetKind.Building, it.id) }
            }
        }
        return best
    }

    private fun damage(target: LiveTarget, amount: Float, source: FactionId) {
        when (target) {
            is LiveTarget.UnitTarget -> {
                target.unit.hp -= amount
                target.unit.flash = 1f
                if (target.unit.hp <= 0f) target.unit.dead = true
            }
            is LiveTarget.BuildingTarget -> {
                target.building.hp -= amount
                target.building.flash = 1f
                if (target.building.hp <= 0f) {
                    target.building.dead = true
                    effects += Effect("boom", target.building.x, target.building.y - 48f, .75f)
                }
            }
        }
        if (source != FactionId.Blue && target.faction == FactionId.Blue) show("Your realm is under attack!", 1.4f)
    }

    private fun nearestDropoff(fid: FactionId, x: Float, y: Float): BuildingEntity? = buildings
        .filter { it.faction == fid && !it.dead && it.build >= 1f && (it.kind == BuildingKind.Castle || it.kind == BuildingKind.House) }
        .minByOrNull { distance2(x, y, it.x, it.y) }

    private fun resolveTarget(ref: TargetRef?): LiveTarget? = when (ref?.kind) {
        TargetKind.Unit -> units.firstOrNull { it.id == ref.id && !it.dead }?.let { LiveTarget.UnitTarget(it) }
        TargetKind.Building -> buildings.firstOrNull { it.id == ref.id && !it.dead }?.let { LiveTarget.BuildingTarget(it) }
        else -> null
    }

    private fun pick(x: Float, y: Float): Any? {
        units.asReversed().firstOrNull { !it.dead && distance2(x, y, it.x, it.y) < (it.radius + 14f) * (it.radius + 14f) }?.let { return it }
        buildings.asReversed().firstOrNull { !it.dead && x in it.footprintLeft - 12f..it.footprintRight + 12f && y in it.footprintTop - 32f..it.footprintBottom + 18f }?.let { return it }
        resources.asReversed().firstOrNull { !it.dead && distance2(x, y, it.x, it.y) < (it.radius + 18f) * (it.radius + 18f) }?.let { return it }
        return null
    }

    private fun isPlayerSelectable(item: Any): Boolean = when (item) {
        is UnitEntity -> item.faction == FactionId.Blue
        is BuildingEntity -> item.faction == FactionId.Blue
        is ResourceEntity -> true
        else -> false
    }

    private fun select(item: Any) {
        selected += item
        if (item is UnitEntity) item.selected = true
    }

    fun clearSelection() {
        selected.filterIsInstance<UnitEntity>().forEach { it.selected = false }
        selected.clear()
    }

    private fun animationSpeed(u: UnitEntity): Float = when (u.order) {
        Order.Move, Order.AttackMove, Order.ReturnCargo -> 10f
        Order.Attack, Order.Harvest, Order.Build, Order.Repair, Order.Heal -> 8f
        else -> 3.5f
    }

    private fun producerLabel(kind: UnitKind): String = BuildingKind.entries.firstOrNull { it.trains.contains(kind) }?.label ?: "training building"

    private fun cleanup() {
        units.removeAll { it.dead }
        buildings.removeAll { it.dead }
        resources.removeAll { it.dead && it.kind != ResourceKind.Food }
    }

    private fun checkVictory() {
        FactionId.entries.forEach { fid ->
            factions.getValue(fid).alive = buildings.any { it.faction == fid && it.kind == BuildingKind.Castle && !it.dead }
        }
        if (!factions.getValue(FactionId.Blue).alive) {
            gameOver = true
            show("Your castle has fallen. The realm is lost.", 100f)
        } else if (FactionId.entries.filter { it.ai }.none { factions.getValue(it).alive }) {
            gameOver = true
            show("Victory! Every rival realm has fallen.", 100f)
        }
    }

    fun screenToWorld(screenX: Float, screenY: Float, screenW: Float, screenH: Float): Vec2 {
        val viewW = screenW / zoom
        val viewH = screenH / zoom
        return Vec2(
            (camera.x + screenX / zoom).coerceIn(0f, WORLD_WIDTH),
            (camera.y + screenY / zoom).coerceIn(0f, WORLD_HEIGHT)
        ).also {
            camera.x = camera.x.coerceIn(0f, max(0f, WORLD_WIDTH - viewW))
            camera.y = camera.y.coerceIn(0f, max(0f, WORLD_HEIGHT - viewH))
        }
    }

    fun worldToScreen(x: Float, y: Float): Vec2 = Vec2((x - camera.x) * zoom, (y - camera.y) * zoom)

    fun isWater(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || x >= WORLD_WIDTH || y >= WORLD_HEIGHT) return true
        val tx = (x / TILE_SIZE).toInt().coerceIn(0, cols - 1)
        val ty = (y / TILE_SIZE).toInt().coerceIn(0, rows - 1)
        return !land[ty * cols + tx]
    }

    fun landAt(tx: Int, ty: Int): Boolean = tx in 0 until cols && ty in 0 until rows && land[ty * cols + tx]
    fun landCols() = cols
    fun landRows() = rows

    private fun nearestLand(x: Float, y: Float, maxR: Float): Vec2? {
        if (!isWater(x, y)) return Vec2(x.coerceIn(20f, WORLD_WIDTH - 20f), y.coerceIn(20f, WORLD_HEIGHT - 20f))
        var r = 24f
        while (r <= maxR) {
            repeat(16) { i ->
                val a = PI.toFloat() * 2f * i / 16f
                val px = (x + cos(a) * r).coerceIn(20f, WORLD_WIDTH - 20f)
                val py = (y + sin(a) * r).coerceIn(20f, WORLD_HEIGHT - 20f)
                if (!isWater(px, py)) return Vec2(px, py)
            }
            r += 24f
        }
        return null
    }

    private fun safeLand(x: Float, y: Float, radius: Float): Boolean {
        if (isWater(x, y)) return false
        val probes = arrayOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, .7f to .7f, -.7f to .7f, .7f to -.7f, -.7f to -.7f)
        return probes.all { !isWater(x + it.first * radius, y + it.second * radius) }
    }

    private fun chooseAnimal(x: Float, y: Float): AnimalKind {
        val total = AnimalKind.entries.sumOf { it.weight.toDouble() }.toFloat()
        var pick = hash((x / 137f).toInt(), (y / 149f).toInt(), 4301) * total
        AnimalKind.entries.forEach {
            pick -= it.weight
            if (pick <= 0f) return it
        }
        return AnimalKind.Deer
    }

    private fun directionRow(dx: Float, dy: Float): Int = if (abs(dx) > abs(dy)) {
        if (dx > 0f) 3 else 2
    } else if (dy > 0f) 0 else 1

    private fun show(message: String, seconds: Float = 1.8f) {
        toast = message
        toastTimer = seconds
    }

    private fun next() = nextId++
}

sealed class LiveTarget(val x: Float, val y: Float, val radius: Float, val faction: FactionId) {
    class UnitTarget(val unit: UnitEntity) : LiveTarget(unit.x, unit.y, unit.radius, unit.faction)
    class BuildingTarget(val building: BuildingEntity) : LiveTarget(building.x, building.y, max(building.kind.placeW, building.kind.placeH) * .5f, building.faction)
}

private fun distance2(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return dx * dx + dy * dy
}

private fun normalized(dx: Float, dy: Float): Vec2 {
    val d = hypot(dx, dy).coerceAtLeast(.001f)
    return Vec2(dx / d, dy / d)
}

private fun rectOverlap(aLeft: Float, aTop: Float, aRight: Float, aBottom: Float, bLeft: Float, bTop: Float, bRight: Float, bBottom: Float): Boolean =
    aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop

private fun hash(x: Int, y: Int, seed: Int): Float {
    var n = x * 374761393 xor y * 668265263 xor seed * 1442695041
    n = n xor (n ushr 13)
    n *= 1274126177
    return ((n xor (n ushr 16)).toLong() and 0xffffffffL) / 4294967295f
}
