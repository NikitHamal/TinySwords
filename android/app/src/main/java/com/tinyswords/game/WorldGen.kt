package com.tinyswords.game

import kotlin.math.*
import kotlin.random.Random

/** Procedural world generator. Produces:
 *   - landMap (1 = walkable land, 0 = water)
 *   - biome map (which faction's grass tint to draw under each tile)
 *   - faction starts (castle position + a worker cluster)
 *   - resource clumps (wood, gold, food/animals) seeded around each faction
 *   - decorations (rocks, bushes, water rocks) for visual flavor
 *
 * Mirrors the web game's "Crossroads" preset: each faction sits at a corner of the world,
 * with a contested center. Generation is deterministic given the seed string.
 */
class WorldGen(val seed: String, val factionCount: Int = 2) {
    val cols = C.WORLD_W / C.TILE
    val rows = C.WORLD_H / C.TILE
    val land = ByteArray(cols * rows)
    val biome = ByteArray(cols * rows) { 4 } // default neutral
    val starts = mutableListOf<Pair<Float, Float>>()

    private val rng = Random(seed.hashCode().toLong())

    fun generate(): WorldData {
        // Place faction bases at evenly-spaced anchors so layouts feel symmetric.
        val anchors = listOf(
            0.18f to 0.78f,  // bottom-left = player
            0.82f to 0.22f,  // top-right = AI
            0.18f to 0.22f,
            0.82f to 0.78f
        ).take(factionCount)
        for ((fx, fy) in anchors) {
            starts += (fx * C.WORLD_W) to (fy * C.WORLD_H)
        }

        // Paint base landmasses around each anchor + a central island.
        for (i in 0 until cols * rows) land[i] = 0
        for ((sx, sy) in starts) paintEllipse(sx, sy, 1500f, 1100f, 1)
        paintEllipse(C.WORLD_W * 0.5f, C.WORLD_H * 0.5f, 1700f, 1100f, 1)
        // Connecting corridors between bases keep ground-troops mobile.
        for (i in starts.indices) for (j in i + 1 until starts.size) {
            connectLand(starts[i], starts[j])
        }
        // Wobble shoreline.
        wobbleShore()

        // Voronoi-style biome assignment: each tile gets the nearest faction's tint.
        for (ty in 0 until rows) for (tx in 0 until cols) {
            if (land[ty * cols + tx].toInt() == 0) continue
            val cx = tx * C.TILE + C.TILE * 0.5f
            val cy = ty * C.TILE + C.TILE * 0.5f
            var best = -1; var bestD = Float.MAX_VALUE
            for ((i, s) in starts.withIndex()) {
                val d = (s.first - cx).let { it * it } + (s.second - cy).let { it * it }
                if (d < bestD) { bestD = d; best = i }
            }
            biome[ty * cols + tx] = best.toByte()
        }

        val resources = mutableListOf<GameResource>()
        var nextId = 1
        // Per-faction resource rings.
        for ((sx, sy) in starts) {
            spawnTreeClump(sx - 360, sy - 80, 14, resources) { nextId++ }
            spawnTreeClump(sx + 80, sy - 320, 11, resources) { nextId++ }
            spawnGoldClump(sx - 80, sy + 360, 6, resources) { nextId++ }
            spawnGoldClump(sx + 320, sy + 240, 4, resources) { nextId++ }
            spawnAnimals(sx - 480, sy + 380, 6, resources) { nextId++ }
        }
        // Neutral scatter.
        repeat(46) { spawnTreeClump(rngWorldX(), rngWorldY(), 6 + rng.nextInt(6), resources) { nextId++ } }
        repeat(14) { spawnGoldClump(rngWorldX(), rngWorldY(), 3 + rng.nextInt(4), resources) { nextId++ } }
        repeat(9)  { spawnAnimals(rngWorldX(), rngWorldY(), 4 + rng.nextInt(3), resources) { nextId++ } }

        val decors = mutableListOf<Decor>()
        repeat(80) {
            val x = rngWorldX(); val y = rngWorldY()
            if (isLandPx(x, y)) decors += Decor(x, y, DecorKind.entries.random(rng))
        }
        repeat(50) {
            val x = rngWorldX(); val y = rngWorldY()
            if (!isLandPx(x, y)) decors += Decor(x, y, DecorKind.WATER_ROCK)
        }

        return WorldData(land, biome, starts, resources, decors)
    }

    private fun rngWorldX() = 200 + rng.nextFloat() * (C.WORLD_W - 400)
    private fun rngWorldY() = 200 + rng.nextFloat() * (C.WORLD_H - 400)

    private fun paintEllipse(cx: Float, cy: Float, rx: Float, ry: Float, value: Byte) {
        val minX = ((cx - rx) / C.TILE).toInt().coerceAtLeast(0)
        val maxX = ((cx + rx) / C.TILE).toInt().coerceAtMost(cols - 1)
        val minY = ((cy - ry) / C.TILE).toInt().coerceAtLeast(0)
        val maxY = ((cy + ry) / C.TILE).toInt().coerceAtMost(rows - 1)
        for (ty in minY..maxY) for (tx in minX..maxX) {
            val px = tx * C.TILE + C.TILE * 0.5f
            val py = ty * C.TILE + C.TILE * 0.5f
            val dx = (px - cx) / rx
            val dy = (py - cy) / ry
            if (dx * dx + dy * dy <= 1f) land[ty * cols + tx] = value
        }
    }

    private fun connectLand(a: Pair<Float, Float>, b: Pair<Float, Float>) {
        val steps = 18
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val cx = a.first * (1 - t) + b.first * t
            val cy = a.second * (1 - t) + b.second * t
            paintEllipse(cx, cy, 460f, 460f, 1)
        }
    }

    private fun wobbleShore() {
        // One pass of cellular noise: tiles near the shoreline get a random nibble or addition.
        val copy = land.copyOf()
        for (ty in 1 until rows - 1) for (tx in 1 until cols - 1) {
            val i = ty * cols + tx
            val n = copy[(ty - 1) * cols + tx].toInt() + copy[(ty + 1) * cols + tx].toInt() +
                copy[ty * cols + tx - 1].toInt() + copy[ty * cols + tx + 1].toInt()
            if (copy[i].toInt() == 1 && n <= 1 && rng.nextFloat() < 0.4f) land[i] = 0
            if (copy[i].toInt() == 0 && n >= 3 && rng.nextFloat() < 0.4f) land[i] = 1
        }
    }

    fun isLandPx(x: Float, y: Float): Boolean {
        val tx = (x / C.TILE).toInt().coerceIn(0, cols - 1)
        val ty = (y / C.TILE).toInt().coerceIn(0, rows - 1)
        return land[ty * cols + tx].toInt() == 1
    }

    private inline fun spawnTreeClump(cx: Float, cy: Float, n: Int, out: MutableList<GameResource>, idGen: () -> Int) {
        repeat(n) {
            val a = rng.nextFloat() * (Math.PI * 2).toFloat()
            val r = rng.nextFloat() * 110f
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (!isLandPx(x, y)) return@repeat
            val amount = 240 + rng.nextInt(120)
            out += GameResource(idGen(), ResourceKind.WOOD, x, y, amount = amount, maxAmount = amount, variant = rng.nextInt(4))
        }
    }

    private inline fun spawnGoldClump(cx: Float, cy: Float, n: Int, out: MutableList<GameResource>, idGen: () -> Int) {
        repeat(n) {
            val a = rng.nextFloat() * (Math.PI * 2).toFloat()
            val r = rng.nextFloat() * 70f
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (!isLandPx(x, y)) return@repeat
            val amount = 120 + rng.nextInt(60)
            out += GameResource(idGen(), ResourceKind.GOLD, x, y, amount = amount, maxAmount = amount, variant = rng.nextInt(6))
        }
    }

    private inline fun spawnAnimals(cx: Float, cy: Float, n: Int, out: MutableList<GameResource>, idGen: () -> Int) {
        val species = AnimalKind.ALL.random(rng)
        repeat(n) {
            val a = rng.nextFloat() * (Math.PI * 2).toFloat()
            val r = rng.nextFloat() * 90f
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (!isLandPx(x, y)) return@repeat
            out += GameResource(
                idGen(), ResourceKind.FOOD, x, y,
                hp = species.baseHp.toFloat(), maxHp = species.baseHp.toFloat(),
                amount = species.yield, maxAmount = species.yield,
                variant = 0, animal = species,
                animDir = rng.nextInt(4)
            )
        }
    }
}

class WorldData(
    val land: ByteArray,
    val biome: ByteArray,
    val starts: List<Pair<Float, Float>>,
    val resources: MutableList<GameResource>,
    val decors: MutableList<Decor>
) {
    val cols = C.WORLD_W / C.TILE
    val rows = C.WORLD_H / C.TILE
    fun isLandTile(tx: Int, ty: Int): Boolean {
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) return false
        return land[ty * cols + tx].toInt() == 1
    }
    fun isLandPx(x: Float, y: Float): Boolean =
        x >= 0f && y >= 0f && x < C.WORLD_W && y < C.WORLD_H &&
            isLandTile((x / C.TILE).toInt(), (y / C.TILE).toInt())
    fun biomeAt(tx: Int, ty: Int): Int {
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) return 4
        return biome[ty * cols + tx].toInt() and 0xFF
    }
}

data class Decor(val x: Float, val y: Float, val kind: DecorKind)
enum class DecorKind { ROCK, BUSH, WATER_ROCK }
