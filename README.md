# Tiny Swords: Realm War RTS - Revamped Build

A self-contained HTML5 top-down RTS prototype using Pixel Frog's Tiny Swords free asset pack.

This version was rebuilt around the look of the Tiny Swords reference: water-first island terrain, animated shoreline foam, correctly cropped sprite-sheet frames, smaller readable props, and modular game systems instead of one giant script.

## Run

Open `index.html` in a modern browser. For the cleanest result, run a local static server from this folder and open the localhost URL.

Node-only option, no install required:

```bash
node -e "const http=require('http'),fs=require('fs'),path=require('path');const root=process.cwd();const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png'};http.createServer((req,res)=>{let p=decodeURIComponent(req.url.split('?')[0]);if(p==='/')p='/index.html';p=path.normalize(path.join(root,p));if(!p.startsWith(root)){res.writeHead(403);return res.end();}fs.readFile(p,(e,d)=>{if(e){res.writeHead(404);return res.end('not found');}res.writeHead(200,{'Content-Type':mime[path.extname(p)]||'application/octet-stream'});res.end(d);});}).listen(8080,()=>console.log('http://localhost:8080'));"
```

## Controls

- WASD / Arrow keys: pan camera
- Mouse edge: pan camera
- Mouse wheel: zoom
- Left click: select unit, building, or resource
- Drag left mouse: box-select your units
- Shift + select: add/remove from selection
- Right click ground: move selected units or set selected building rally flag
- Right click resource: selected workers gather
- Right click enemy: attack
- Right click tower with archers selected: archers walk over and garrison
- B: open build menu
- H: help
- M: expand/collapse minimap
- Space: pause
- Esc: close/cancel
- Ctrl + A: select army
- 0: select all your units

## What was revamped

### Visual pass

- Rebuilt terrain generation into an ocean-first island/archipelago map with connected base islands, scenic satellites, coves, and central conflict lanes.
- Reworked water rendering with animated shimmer, shore foam frames, splashes, water rocks, clouds, and rubber duck details.
- Fixed the huge asset-sheet rendering issue: trees, bushes, water rocks, clouds, sheep, and stumps now draw cropped frames instead of the full sheet.
- Added terrain color variation using all Tiny Swords tile palette variants.
- Added house variants so villages no longer repeat the same building sprite.
- Removed the remote Google Fonts dependency so the build works offline.

### Gameplay / systems pass

- Added water-aware movement so units steer around shorelines and get nudged back onto land instead of walking straight through the ocean.
- Reworked tower garrisoning: archers now walk to towers before entering instead of teleporting in from anywhere.
- Added projectile hit sparks and water splash feedback.
- Improved minimap rendering so it uses the actual generated land/water map.
- Increased starting resources slightly so the first minute feels better.

### Refactor pass

The old single large script has been split into feature modules:

```text
js/core/config.js          constants, asset paths, helpers
js/core/audio.js           WebAudio sfx
js/core/game-state.js      Game state constructor
js/systems/world.js        terrain generation, resources, spawns
js/systems/input.js        input, selection, orders, placement
js/systems/simulation.js   economy, combat, AI, movement, cleanup
js/ui/hud.js               HUD and command buttons
js/render/world-renderer.js canvas terrain, entities, FX, minimap
js/main.js                 bootstrapping
```

## Objective

Build an economy, train troops, garrison towers with archers, defend your base, then destroy rival nations.

## Assets

Tiny Swords assets are by Pixel Frog. Check the original asset page for license terms if you publish or redistribute beyond local/private play.
