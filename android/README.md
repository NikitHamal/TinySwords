# Tiny Swords — Android (Kotlin / Jetpack Compose)

A native Android port of the Tiny Swords web game. No WebView; the entire renderer is
Compose Canvas talking directly to the Android `Bitmap`/`Canvas` APIs, and the simulation
is plain Kotlin running on a coroutine.

## Project layout

```
android/
├── app/
│   ├── build.gradle.kts            # AGP + Compose + serialization
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/tinyswords/
│       │   ├── MainActivity.kt           # screen routing (menu / game / about)
│       │   ├── audio/SoundBank.kt        # SoundPool wrapper around shared sfx
│       │   ├── data/SaveLoad.kt          # JSON persistence
│       │   ├── game/                     # pure-Kotlin engine
│       │   │   ├── Constants.kt          # tuning values, unit/building tables
│       │   │   ├── Models.kt             # Entity hierarchy + DTOs
│       │   │   ├── WorldGen.kt           # procedural map + biome painter
│       │   │   ├── Pathfinding.kt        # A* on 32px cells
│       │   │   ├── Game.kt               # top-level game state + camera
│       │   │   ├── Simulation.kt         # per-tick logic, combat, harvesting
│       │   │   └── AI.kt                 # enemy faction brain
│       │   ├── input/InputController.kt  # touch routing (tap, pinch, long-press)
│       │   ├── render/
│       │   │   ├── SpriteCache.kt        # asset path catalog + bitmap cache
│       │   │   └── GameRenderer.kt       # Compose-Canvas world renderer
│       │   └── ui/                       # Compose HUD + menus
│       │       ├── Theme.kt
│       │       ├── MainMenu.kt
│       │       ├── GameScreen.kt
│       │       ├── HUD.kt
│       │       ├── BuildMenu.kt
│       │       └── Minimap.kt
│       └── res/
├── keystore/release.jks            # default signing key (committed; private repo)
├── keystore.properties             # references the .jks above
├── build.gradle.kts                # root project
├── settings.gradle.kts
└── gradle/wrapper/gradle-wrapper.properties
```

## Asset sharing

The Android module reuses the web game's asset pack via `sourceSets["main"].assets.srcDirs`
in `app/build.gradle.kts`. The reference points at `../assets`, which is the same folder the
web build serves. There is no asset duplication.

## Building locally

```bash
cd android
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/tinyswords-release-<sha>.apk
```

The first build will download the Gradle 8.9 distribution and the Android Gradle Plugin.

## Signing

The repository ships a self-signed release keystore at `android/keystore/release.jks`. Its
credentials live in `android/keystore.properties`. Both files are committed intentionally
because the repo is private and the user requested a single, ready-to-use signing config.

If you need to rotate the key:

```bash
keytool -genkeypair -v \
  -keystore android/keystore/release.jks \
  -storepass tinyswords -keypass tinyswords \
  -alias tinyswords -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=TinySwords, OU=Game, O=TinySwords, L=Local, S=Local, C=US"
```

## CI / CD

`/.github/workflows/android-build.yml` triggers on every push to any branch. It:

1. Checks out the repo
2. Computes the short commit hash
3. Sets up JDK 17 + Android SDK 34
4. Caches `~/.gradle/`
5. Creates `gradlew` if missing (`gradle wrapper --gradle-version 8.9`)
6. Runs `./gradlew :app:assembleRelease` (signed with the committed keystore)
7. Verifies the APK signature with `apksigner`
8. Uploads the APK as `tinyswords-<short-sha>.apk`

The Gradle build itself renames the APK after the commit hash:

```kotlin
output.outputFileName.set("tinyswords-$flavor-${gitCommitHash}.apk")
```

When CI runs, `GITHUB_SHA` is read directly; locally we shell out to `git rev-parse`.

## Controls

| Action            | Gesture                  |
|-------------------|--------------------------|
| Pan camera        | Drag with one finger     |
| Zoom              | Pinch                    |
| Select entity     | Tap                      |
| Issue order       | Long-press on target     |
| Build / Train     | Action dock buttons      |
| Pause / Save      | Top-right state readout  |

## Game systems

Mirrors the web build:

- 5 unit types (Worker, Warrior, Archer, Lancer, Monk) with the same HP, speed, damage,
  cooldown, and cost tables — see `Constants.kt`.
- 6 building types (Castle, House, Barracks, Archery, Tower, Monastery), each with its
  own HP, build time, training options, and cost.
- 3 resource kinds (Wood, Gold, Food) plus 6 huntable animals.
- A* pathfinding on a 32 px grid with line-of-sight smoothing.
- Voronoi biome painting around faction starts.
- Persistent saves via JSON (`kotlinx.serialization`).
- Light AI faction that builds an economy, trains an army, and sends attack waves.

## Notes

- The renderer falls back to colored geometric shapes for any sprite that fails to load —
  useful while the asset pack is being filled in.
- No NDK / native libs; the entire game is Kotlin + Compose.
- ProGuard/R8 only strips unused code in release; serialization classes are kept.
