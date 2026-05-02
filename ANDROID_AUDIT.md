# Tiny Swords Android Rebuild Audit

## Web Game Audit Summary

The current web version is a canvas RTS with a large seeded top-down realm, Tiny Swords faction sprites, CraftPix hunting wildlife, browser-local save/load, HUD overlays, minimap, economy, building placement, unit training, combat, tower defense, and AI rivals. The implementation is modular across configuration, world generation, input, simulation, pathfinding, rendering, menus, and HUD.

Key gameplay systems ported natively:

- Economy loop: wood, gold, food, workers, carry/return, drop-offs, population cap, and production queues.
- World loop: procedural island/land lanes, safe starting bases, neutral resources, wildlife density, and faction bases.
- Combat loop: melee units, archers with projectiles, monks/healing, towers, target acquisition, damage flashes, deaths, and victory/loss checks.
- Wildlife loop: deer, boar, hare, fox, and black grouse with panic motion, hit feedback, food yield, and boar retaliation.
- RTS controls: tap selection, smart orders, formation movement, drag selection, worker construction, training commands, zoom, home focus, pause, HUD, side panel, command bar, and minimap.

## Android Implementation

The Android version is a fresh native Kotlin + Compose game. It does not embed the web game or use a WebView. Original art assets are packaged as Android assets from the existing `assets/` directory and rendered through Compose `Canvas` with nearest-neighbor pixel scaling.

Primary native files:

- `app/src/main/java/com/tinyswords/realmwar/Model.kt` defines factions, units, buildings, resources, animals, projectiles, effects, and constants.
- `app/src/main/java/com/tinyswords/realmwar/GameEngine.kt` implements world generation, simulation, AI, orders, combat, harvesting, building placement, training, and touch command handling.
- `app/src/main/java/com/tinyswords/realmwar/MainActivity.kt` implements fullscreen Android startup, title screen, game renderer, touch input, HUD, command UI, selection panel, and minimap.
- `app/build.gradle.kts` configures Compose, packaged assets, release signing, and Android build settings.

## Build And Signing

A default public release keystore is committed at `app/signing/tinyswords-public-release.jks` with alias/password defaults of `tinyswords`.

The GitHub Actions workflow at `.github/workflows/android-release.yml` runs on every push to every branch, installs the Android SDK, builds `:app:assembleRelease`, signs with the committed keystore, renames the APK to `tinyswords-realm-war-<short-commit-hash>.apk`, and uploads it as a workflow artifact.

## Local Validation Performed

Using a temporary Android SDK under `/tmp/android-sdk`:

- `./gradlew --no-daemon :app:compileDebugKotlin` passed.
- `./gradlew --no-daemon :app:assembleRelease` passed.
- `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk` passed with one signer and APK Signature Scheme v2.
