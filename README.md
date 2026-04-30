# Tiny Swords: Realm War RTS - Revamped Build

A self-contained HTML5 RTS prototype using the Tiny Swords free asset pack.

## How to play

Open `index.html` in a modern browser. If your browser blocks local asset loading, run a tiny local server from this folder and open the shown localhost URL.

Example with Node installed:

```bash
npx serve .
```

## Controls

- **WASD / Arrow keys**: pan camera
- **Mouse edge**: pan camera
- **Mouse wheel**: zoom
- **Left click**: select unit/building/resource
- **Drag left mouse**: box-select your units
- **Shift + select**: add/remove from selection
- **Right click ground**: move selected units or set selected building rally flag
- **Right click resource**: selected workers gather
- **Right click enemy**: attack
- **Right click tower with archers selected**: garrison archers
- **B**: open build menu
- **H**: help
- **M**: expand/collapse minimap
- **Space**: pause
- **Esc**: close/cancel
- **Ctrl + A**: select army
- **0**: select all your units

## Revamp notes

This build focuses on readability and RTS usability:

- Reworked terrain generation into a mostly-land strategic continent with rivers, lakes, base plateaus and crossings.
- Removed the broken random dark tile holes and heavy grid/checkerboard look.
- Reduced tree, rock, bush, unit and building scale for better visibility.
- Workers visibly chop trees and leave stumps when depleted.
- Sheep/food animals wander on the map.
- Rebuilt HUD styling so panels use compact game-themed command cards instead of giant repeated texture blocks.
- Minimap is now a small circular radar by default and expands into a centered large map with **M**.
- Help/controls are kept in the **H** overlay instead of occupying play space.

## Objective

Build an economy, train troops, garrison towers with archers, defend your base, then destroy rival nations.

## Assets

Tiny Swords assets are by Pixel Frog. Check the original asset page for license terms if you publish or redistribute beyond local/private play.
