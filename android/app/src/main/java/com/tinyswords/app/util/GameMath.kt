package com.tinyswords.app.util

import kotlin.math.*

fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)

fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = len(bx - ax, by - ay)

fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    return dx * dx + dy * dy
}

fun rngHash(x: Int, y: Int, seed: Int = 11): Float {
    var h = (x * 374761393 + y * 668265263 + seed * 2147483647)
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / 2147483647f
}

fun rngHashF(x: Float, y: Float, seed: Int = 11): Float = rngHash(x.toInt(), y.toInt(), seed)

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float): Float = atan2(by - ay, bx - ax)

fun rectsOverlap(
    ax: Float, ay: Float, aw: Float, ah: Float,
    bx: Float, by: Float, bw: Float, bh: Float
): Boolean {
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
}

fun formationOffset(index: Int, count: Int, spacing: Float, mode: String = "box"): Pair<Float, Float> {
    if (count <= 1) return Pair(0f, 0f)

    return when (mode) {
        "line" -> {
            val half = (count - 1) / 2f
            Pair((index - half) * spacing, 0f)
        }
        "wedge" -> {
            val row = index / 2
            val side = if (index % 2 == 0) -1f else 1f
            Pair(side * (row + 1) * spacing * 0.5f, row * spacing * 0.6f)
        }
        "split" -> {
            val half = count / 2
            if (index < half) {
                val lineCount = half
                val lineHalf = (lineCount - 1) / 2f
                Pair((index - lineHalf) * spacing, -spacing * 0.5f)
            } else {
                val lineCount = count - half
                val lineHalf = (lineCount - 1) / 2f
                Pair((index - half - lineHalf) * spacing, spacing * 0.5f)
            }
        }
        else -> { // box
            val cols = ceil(sqrt(count.toFloat())).toInt()
            val row = index / cols
            val col = index % cols
            val rowCount = if (row < count / cols) cols else count % cols
            val halfCol = (rowCount - 1) / 2f
            val halfRow = (ceil(count.toFloat() / cols) - 1) / 2f
            Pair((col - halfCol) * spacing, (row - halfRow) * spacing)
        }
    }
}
