package com.tinyswords.realmwar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tinyswords.realmwar.assets.AssetPaths

/**
 * Tiny wrapper around [SoundPool] that mirrors the web build's [`SoundBank`].
 *
 * Sounds are short MP3s (sword clash, arrow loose, arrow hit, heal, battle
 * cry, footsteps); we load each once and play with rate + volume jitter to
 * avoid the "machine-gun click" effect the web version had on hot battles.
 */
class SoundBank(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = mutableMapOf<String, Int>()
    private val cooldowns = mutableMapOf<String, Long>()
    var masterVolume: Float = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }
    var enabled: Boolean = true

    init {
        for ((name, path) in AssetPaths.sounds) {
            try {
                context.assets.openFd(path).use { afd ->
                    ids[name] = pool.load(afd, 1)
                }
            } catch (_: Throwable) { /* missing sound is non-fatal */ }
        }
    }

    private fun play(name: String, volume: Float, rate: Float, cooldownMs: Long): Boolean {
        if (!enabled) return false
        val sid = ids[name] ?: return false
        val now = System.currentTimeMillis()
        val last = cooldowns[name] ?: 0L
        if (now - last < cooldownMs) return false
        cooldowns[name] = now
        val v = (volume * masterVolume).coerceIn(0f, 1f)
        pool.play(sid, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        return true
    }

    fun sword(gain: Float = 1f) = play("sword", 0.32f * gain, 0.96f + Math.random().toFloat() * 0.08f, 45)
    fun arrow(gain: Float = 1f) = play("arrow", 0.28f * gain, 0.98f + Math.random().toFloat() * 0.06f, 40)
    fun arrowHit(gain: Float = 1f) = play("arrowHit", 0.28f * gain, 1f, 35)
    fun heal(gain: Float = 1f) = play("heal", 0.28f * gain, 1f, 60)
    fun battle(gain: Float = 1f) = play("battle", 0.30f * gain, 1f, 1200)
    fun run(gain: Float = 1f) = play("run", 0.22f * gain, 1f, 250)

    fun release() = pool.release()
}
