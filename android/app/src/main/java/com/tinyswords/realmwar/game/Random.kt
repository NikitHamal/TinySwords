package com.tinyswords.realmwar.game

import kotlin.random.Random

/**
 * Tiny xorshift-style seeded RNG matching the JS `makeSeededRandom` so map generation is
 * deterministic per seed across runs and platforms.
 */
class SeededRandom(seedText: String) : Random() {
    private var state: Long = hashSeed(seedText).let { if (it == 0L) 1L else it }

    override fun nextBits(bitCount: Int): Int {
        state = (state + 0x6D2B79F5L) and 0xFFFFFFFFL
        var t = state.toInt()
        t = t xor (t ushr 15)
        t = (t * 1) xor (t * 1)
        t = ((t + ((t xor (t ushr 7)) * 61)) xor t)
        t = t xor (t ushr 14)
        return (t ushr (32 - bitCount))
    }

    fun nextFloat01(): Float = nextDouble().toFloat()

    companion object {
        fun hashSeed(text: String?): Long {
            var h = 2166136261L
            val str = (text ?: "tinyswords").ifEmpty { "tinyswords" }
            for (c in str) {
                h = h xor c.code.toLong()
                h = (h * 16777619L) and 0xFFFFFFFFL
            }
            return h
        }
    }
}
