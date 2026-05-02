package com.tinyswords.realmwar

import android.app.Application
import com.tinyswords.realmwar.audio.SoundBank
import com.tinyswords.realmwar.data.WorldStorage

/**
 * Application-level singleton that initializes audio + persistent world storage. The Tembo bot
 * deliberately keeps this small: most game state lives inside the [GameSessionViewModel] for the
 * lifetime of the active match.
 */
class TinySwordsApp : Application() {
    val soundBank by lazy { SoundBank(this) }
    val worldStorage by lazy { WorldStorage(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TinySwordsApp
            private set
    }
}
