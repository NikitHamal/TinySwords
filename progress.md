Original prompt: Android-only performance and UI overhaul for TinySwords. Deeply audit and patch lag, stutter, zoom performance, worker building construction, duplicate building progress/HP bars, title screen cleanup, minimal in-game HUD, remove right-side command/formations UI, and make selected-unit/build actions compact bottom bars.

Progress:
- Started Android native audit. Existing `OPTIMIZATION_AUDIT.md` shows prior broad pass, but current screenshots and request require deeper Android renderer, simulation, construction, and Compose HUD cleanup.
- Patched Android renderer hot paths: cached terrain edge source lookups, skipped sky/cloud draw in performance mode, limited atlas prebuild to smaller/static textures, and changed foundations to show a single construction progress bar instead of HP plus progress.
- Patched Android simulation/building flow: worker repair orders now persist as build role, construction advances continuously from `buildTime`, foundations dirty the nav grid asynchronously, and building placement/move avoids synchronous full path-grid rebuild jank.
- Patched Android touch picking to use spatial indices instead of full scans across units/buildings/resources.
- Removed Android formation UI/API references and replaced movement offsets with a compact internal group offset.
- Rebuilt Android HUD: compact top-center resources, bottom-left selection card, bottom icon-only action strip, and bottom scrollable build strip.
- Cleaned title screen per screenshots: transparent logo asset, removed Realm War/native edition/descriptive copy/menu icons/button subtitles.
- Manual verification: `git diff --check` passes; `rg` found no remaining Android formation UI/API references; transparent logo alpha verified with Pillow.
- Build note: Gradle was intentionally not rerun after user request. Earlier attempt was blocked by local Java 25.0.2 incompatibility with the Kotlin/Gradle toolchain, not by source diagnostics.
