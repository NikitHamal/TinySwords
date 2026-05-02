package com.tinyswords.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Compact sound bank backed by Android's [SoundPool] (mostly fire-and-forget short sfx).
 * Loads the same WAV/MP3 set the web game uses from `assets/sounds/tinyswords/`.
 *
 * Per-sound cooldowns avoid the "machine-gun arrow" problem when many archers fire at once.
 */
class SoundBank(private val ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids = HashMap<String, Int>()
    private val cooldowns = HashMap<String, Long>()
    var volume: Float = 0.6f

    init {
        for (name in listOf("arrow", "arrow_hit", "battle", "heal", "run", "sword")) {
            try {
                val afd = ctx.assets.openFd("sounds/tinyswords/$name.mp3")
                ids[name] = pool.load(afd, 1)
                afd.close()
            } catch (e: Exception) {
                Log.i("SoundBank", "missing sfx $name: ${e.message}")
            }
        }
    }

    fun play(name: String, minGapMs: Long = 60) {
        val now = System.currentTimeMillis()
        val last = cooldowns[name] ?: 0L
        if (now - last < minGapMs) return
        cooldowns[name] = now
        val id = ids[name] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
