package com.tinyswords.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import kotlin.math.*

class SoundBank(private val context: Context) {
    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<String, Int>()
    private var volume: Float = 0.8f
    private val lastPlayTime = HashMap<String, Long>()

    fun init() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        // Load sounds from assets
        loadSound("sword", "sounds/tinyswords/sword.mp3")
        loadSound("arrow", "sounds/tinyswords/arrow.mp3")
        loadSound("hit", "sounds/tinyswords/arrow_hit.mp3")
        loadSound("battle", "sounds/tinyswords/battle.mp3")
        loadSound("heal", "sounds/tinyswords/heal.mp3")
        loadSound("run", "sounds/tinyswords/run.mp3")
    }

    private fun loadSound(key: String, path: String) {
        try {
            val afd = context.assets.openFd(path)
            val id = soundPool?.load(afd, 1) ?: return
            soundIds[key] = id
            afd.close()
        } catch (e: Exception) {
            // Sound file not found, skip
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    fun play(key: String, gain: Float = 1f, rate: Float = 1f, cooldownMs: Long = 40) {
        val now = System.currentTimeMillis()
        val last = lastPlayTime[key] ?: 0
        if (now - last < cooldownMs) return
        lastPlayTime[key] = now

        val id = soundIds[key] ?: return
        val vol = (volume * 0.10f * gain).coerceIn(0f, 1f)
        soundPool?.play(id, vol, vol, 1, 0, rate.coerceIn(0.5f, 2.0f))
    }

    fun attack(gain: Float = 1f) {
        play("sword", gain * 0.22f, 0.96f + (Math.random().toFloat() * 0.08f), 45)
    }

    fun arrow(gain: Float = 1f) {
        play("arrow", gain * 0.18f, 0.98f + (Math.random().toFloat() * 0.06f), 40)
    }

    fun hit(gain: Float = 1f) {
        play("hit", gain * 0.18f, 0.98f + (Math.random().toFloat() * 0.06f), 35)
    }

    fun heal(gain: Float = 1f) {
        play("heal", gain * 0.18f, 1f, 60)
    }

    fun alert(gain: Float = 1f) {
        play("battle", gain * 0.18f, 1f, 220)
    }

    fun audioGainAt(x: Float, y: Float, camX: Float, camY: Float, zoom: Float, radius: Float = 1260f): Float {
        val dx = x - camX
        val dy = y - camY
        val d = sqrt(dx * dx + dy * dy)
        val near = 180f / zoom
        if (d <= near) return 1f
        if (d >= radius) return 0f
        val falloff = 1f - (d - near) / (radius - near)
        return falloff.pow(1.65f)
    }

    fun destroy() {
        soundPool?.release()
        soundPool = null
    }
}
