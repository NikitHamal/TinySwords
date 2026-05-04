# Tiny Swords Audit and Optimization Report

## Scope
Audited both the web game and Android app, focused on performance, simulation cost, AI behavior cost, queue/economy correctness, and icon/logo integration.

## High-Impact Problems Found

### 1) AI repeatedly rescanned the whole world
Both the web and Android versions were doing many repeated `filter`, `map`, `sort`, and lookup passes across the full unit/building lists during each AI think step. That multiplies badly as the world grows.

**Fix applied:**
- Added a faction overview snapshot in the web game (`js/systems/simulation.js`) and in Android (`AISystem.kt`).
- AI now builds one summarized view per faction think and reuses it for economy, build, train, tactical, and worker reassignment logic.

### 2) Spatial index rebuild cadence was too eager / not adaptive
The game already had spatial rebuilding, but it was effectively on a short fixed cadence. Big worlds still paid too much overhead.

**Fix applied:**
- Added adaptive spatial refresh intervals based on simulated world pressure (units + buildings + resources + projectiles).
- The heavier the world, the less often the indices rebuild, while still keeping gameplay responsive.

### 3) Population-cap bug in unit queues
Both codebases were checking only *current* used population when queuing units, not *already queued* population. This allows over-queueing and can distort economy pacing.

**Fix applied:**
- Added queued-population tracking in both web and Android training code.
- Unit training now blocks correctly when queued population would exceed capacity.

### 4) Web defaults were too heavy by default
The web world-creation defaults favored larger/richer setups, which increases pathing, rendering, and AI overhead immediately.

**Fix applied:**
- Default world size changed from `large` to `standard`.
- Default resource density changed from `rich` to `normal`.

### 5) Android entity index rebuilds happened too often
Dead-entity cleanup in Android rebuilt the entity index even if nothing was actually removed.

**Fix applied:**
- Cleanup now rebuilds the entity index only when something was actually removed.

## Performance Improvements Applied

### Web
- Added `queuedPopulation()` helper.
- Added `buildFactionOverview()` helper.
- Reworked `aiThink`, `aiEconomyEmergency`, `aiBuild`, `aiTrain`, `aiTactics`, and `reassignIdleWorkers` to use overview snapshots.
- Reworked manual `queueTrain()` to honor queued population.
- Reworked `update()` to use adaptive spatial cadence.
- Lowered default world-generation load.

### Android
- Added queued-population validation in `EconomySystem.trainUnit`.
- Rewrote `AISystem` around a single faction overview pass.
- Added adaptive spatial rebuild interval in `GameSimulation`.
- Reduced cleanup overhead by avoiding unconditional entity-index rebuilds.
- Integrated the new game logo into the launcher foreground and title screen.

## Logic / Balance / Bug Review Notes

### Fixed
- Population queue overflow bug.
- Overly expensive AI scans.
- Needlessly frequent Android entity-index rebuilds.

### Notable observations / improvement opportunities
- **Tower HP looks very low (`62`)** in both web and Android configs. This may be intentional, but it stands out compared with other building HP values and may make towers feel weak or disposable.
- **AI build/training logic is now much cheaper**, but it can still be further improved later with strategic heuristics tied to map control and opponent scouting.
- **Renderer-side optimization** could go even further in a later pass with stronger sprite batching / offscreen prerendering of more static layers if you want another round of deeper optimization.
- **Pathfinding throttling / path request deduplication** already exists in the Android codebase; this is good. If later profiling still shows spikes, the next best step would be a similar deeper pathing audit on the web version.

## New Logo / Icon
A new no-text minimal emblem/logo was generated and integrated into:
- Web favicon / logo image
- Android title screen
- Android launcher foreground icon

## Files Changed (high level)
- `js/core/config.js`
- `js/systems/simulation.js`
- `index.html`
- `assets/icons/*`
- `android/app/src/main/java/com/tinyswords/app/game/economy/EconomySystem.kt`
- `android/app/src/main/java/com/tinyswords/app/game/ai/AISystem.kt`
- `android/app/src/main/java/com/tinyswords/app/engine/GameSimulation.kt`
- `android/app/src/main/java/com/tinyswords/app/ui/screens/TitleScreen.kt`
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `android/app/src/main/res/drawable-nodpi/*`

## Validation note
A full Android Gradle compile could not be completed in this environment because Gradle needed to download its distribution from the internet, which is blocked here. The changed files were syntax-checked where feasible (for example, the web simulation file was checked with `node --check`).
