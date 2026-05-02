package com.tinyswords.realmwar

import android.app.Application
import com.tinyswords.realmwar.assets.SpriteCache
import com.tinyswords.realmwar.audio.SoundBank
import com.tinyswords.realmwar.storage.WorldStorage

/** Single-process [Application] that wires up the long-lived sub-systems. */
class TinySwordsApplication : Application() {
    lateinit var spriteCache: SpriteCache
        private set
    lateinit var soundBank: SoundBank
        private set
    lateinit var storage: WorldStorage
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        spriteCache = SpriteCache(applicationContext)
        soundBank = SoundBank(applicationContext)
        storage = WorldStorage(applicationContext)
    }

    companion object {
        @Volatile
        lateinit var instance: TinySwordsApplication
            private set
    }
}
