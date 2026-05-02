package com.tinyswords.app.util

import com.tinyswords.app.game.entities.GameEntity

class SpatialIndex<T : GameEntity>(private val cellSize: Float) {
    private val buckets = HashMap<Long, MutableList<T>>()
    private val resultBuffer = ArrayList<T>(64)

    fun clear() {
        buckets.clear()
    }

    fun insert(entity: T) {
        val key = bucketKey(entity.x, entity.y)
        buckets.getOrPut(key) { mutableListOf() }.add(entity)
    }

    fun rebuild(entities: List<T>) {
        clear()
        for (e in entities) {
            if (!e.dead) insert(e)
        }
    }

    fun query(x: Float, y: Float, radius: Int = 1): List<T> {
        resultBuffer.clear()
        val bx = (x / cellSize).toInt()
        val by = (y / cellSize).toInt()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val key = packKey(bx + dx, by + dy)
                buckets[key]?.let { resultBuffer.addAll(it) }
            }
        }
        return resultBuffer
    }

    fun queryRange(x: Float, y: Float, range: Float): List<T> {
        val reach = kotlin.math.ceil(range / cellSize).toInt().coerceAtLeast(1)
        return query(x, y, reach)
    }

    private fun bucketKey(x: Float, y: Float): Long = packKey((x / cellSize).toInt(), (y / cellSize).toInt())

    private fun packKey(bx: Int, by: Int): Long = (bx.toLong() shl 32) or (by.toLong() and 0xFFFFFFFFL)
}
