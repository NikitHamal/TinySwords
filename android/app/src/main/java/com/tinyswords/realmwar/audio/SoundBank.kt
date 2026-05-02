package com.tinyswords.realmwar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.IOException

/**
 * Compact SFX manager for the in-game sounds. We use SoundPool because the latency requirements
 * are tiny (100ms or less) and the bundled .mp3 files are short.
 */
class SoundBank(private val ctx: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val ids = HashMap<String, Int>()
    var volume: Float = 0.8f

    init {
        loadAll()
    }

    private fun loadAll() {
        val files = mapOf(
            "sword" to "sounds/tinyswords/sword.mp3",
            "run" to "sounds/tinyswords/run.mp3",
            "heal" to "sounds/tinyswords/heal.mp3",
            "battle" to "sounds/tinyswords/battle.mp3",
            "arrow" to "sounds/tinyswords/arrow.mp3",
            "arrowHit" to "sounds/tinyswords/arrow_hit.mp3"
        )
        for ((key, path) in files) {
            try {
                val afd = ctx.assets.openFd(path)
                val id = pool.load(afd, 1)
                ids[key] = id
                afd.close()
            } catch (_: IOException) {
                // Asset missing - silently skip so we never block startup.
            }
        }
    }

    fun play(key: String, volMul: Float = 1f) {
        val id = ids[key] ?: return
        val v = volume * volMul
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
