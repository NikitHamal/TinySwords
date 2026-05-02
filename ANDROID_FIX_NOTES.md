# Tiny Swords Android Native Fix Notes

This package keeps the Android game fully native: Kotlin, Compose UI, and Canvas rendering only. No WebView has been added.

## Main fixes

- Rebuilt the native renderer to use the Tiny Swords tileset correctly, including terrain edge tiles, shore foam, buildings, units, resources, animals, decor, projectiles, placement ghosts, health bars, and a cached minimap.
- Replaced incorrect Android asset paths with a canonical lazy-loading asset manifest so missing/incorrect sprites no longer spam the render loop or degrade visuals.
- Reworked touch controls for mobile RTS play: tap to select/order, tap enemies to attack, tap resources with workers to harvest, long-press attack-move/context order, pinch zoom, drag pan, minimap camera navigation, building placement, rally points, and quick select buttons.
- Reworked game loop stability: synchronized state mutation, queued UI commands, safer canvas locking, FPS pacing, error logging, and minimap terrain caching to reduce stutters and crashes.
- Updated Compose screens for landscape phones: responsive title/new/load/settings panels, scrollable option rows, working settings screen, and non-dead menu controls.

## Validation performed

- Verified the Android source no longer references WebView.
- Verified all canonical Android asset paths used by the new manifest exist in `android/app/src/main/assets`.
- Created full-project and changed-files-only zip packages.

Gradle build/test commands were intentionally not run, per the requested environment limit.
