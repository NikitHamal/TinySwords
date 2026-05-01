# Tiny Swords: Realm War RTS - Hunting World Pass

A self-contained HTML5 top-down RTS using the uploaded Tiny Swords free asset pack and the user-provided CraftPix top-down hunting animal sprite pack.

This build removes the previously generated prop/terrain assets, expands the world substantially, and integrates real animated hunting animals into the economy loop.

## Run

Open `index.html` in a modern browser. For the cleanest result, run a local static server from this folder and open the localhost URL.

Node-only option, no install required:

```bash
node -e "const http=require('http'),fs=require('fs'),path=require('path');const root=process.cwd();const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png'};http.createServer((req,r)=>{let u=req.url==='/'?'/index.html':decodeURI(req.url.split('?')[0]);let f=path.join(root,u);if(!f.startsWith(root)||!fs.existsSync(f)){r.writeHead(404);return r.end('not found')}r.writeHead(200,{'Content-Type':mime[path.extname(f)]||'application/octet-stream'});fs.createReadStream(f).pipe(r)}).listen(8080,()=>console.log('http://localhost:8080'))"
```


## Production pass in this build

This build adds the requested production shell and simulation upgrades:

- Front title screen with a pixel-art styled background, game title, Continue, Single Player, and Settings.
- Single Player world manager with unlimited browser-local world slots, named or unnamed worlds, seed, world size, difficulty, rival count, resource density, graphics, and autosave settings.
- Generating/loading screen with staged progress messages before entering a world.
- Save/load system backed by `localStorage`, including Ctrl+S/manual Save, autosave, Menu return save, world metadata, entity state, resources, buildings, units, camera, AI state, and selection restoration.
- Larger scalable worlds via size presets: Standard, Large, and Massive. Faction bases normalize to the selected world dimensions.
- Pathfinding v2: coarse A* grid over terrain with dynamic blockers, path caching per unit, nearest-walkable fallbacks, and steering fallback for local avoidance.
- AI tactics v2: threat response, tower garrisoning, repair response, staging, retreats for wounded units, strategic target scoring, raids, and difficulty-scaled aggression.
- Performance scaling: unit/resource/building spatial buckets, cached minimap terrain, dynamic navigation invalidation, and reduced O(n²) wildlife collision checks.
- Bug fixes around world startup, rally/order path clearing, save-safe target references, stale path invalidation, and world generation settings.


## What changed in this pass

- Removed the previously generated `assets/Imported Free Pixel Pack` folder and removed all code references to that pack.
- Added CraftPix hunting animals as runtime game assets: deer, boar, hare, fox, and black grouse.
- Replaced single-sheep hunting spawns with species-weighted wildlife spawning.
- Added species-specific animal HP, food yield, walk speed, panic speed, visual scale, and collision radius.
- Added directional animal rendering using four-row top-down sprite sheets: down, up, right, left.
- Added separate CraftPix shadow rendering so wildlife shadows align with the actual clickable/gameplay base.
- Added animal hurt flashes and panic movement after worker strikes.
- Added light boar retaliation so hunting boars is meaningfully riskier than hunting hares, foxes, grouse, or deer.
- Expanded the world from 8200 x 6000 to 12400 x 9000.
- Moved faction bases outward to match the larger world.
- Rebuilt terrain generation around larger base lands, central staging land, more satellite islands, longer tactical corridors, and more water coves.
- Scaled neutral resource clusters, wildlife density, natural decor, water details, and clouds with world area.
- Kept the canonical no-override architecture from the prior production pass.

## Module map

```text
js/core/config.js           constants, asset paths, sprite anchor specs, animal specs, helpers
js/core/audio.js            WebAudio sfx
js/core/game-state.js       Game state constructor
js/systems/world.js         terrain generation, wildlife/resources, spawns, decor placement
js/systems/input.js         input, selection, orders, placement
js/systems/simulation.js    economy, hunting, combat, AI, movement, update loop
js/ui/hud.js                HUD, selection panel, command buttons
js/render/world-renderer.js canvas terrain, anchored sprites, animals, entities, FX, minimap
js/main.js                  bootstrapping
```

## Objective

Build an economy, hunt wildlife, train troops, garrison towers with archers, defend your base, then destroy rival nations.

## Controls

- Left click: select
- Drag: box-select units
- Right click: move, harvest, attack, repair, or set rally point
- B: build menu
- M: minimap expand/collapse
- H: help
- Space: pause
- Ctrl+A: select army
- 0: select all units

## Assets and licensing notes

Tiny Swords assets are by Pixel Frog from the uploaded user-provided pack. Review the original itch.io page/license before publishing or redistributing beyond local/private development.

CraftPix hunting animal assets come from the user-provided CraftPix free top-down hunt animals pixel sprite pack. The pack includes a license pointer to CraftPix file licenses. Only game-used PNG sheets are bundled here; source Aseprite files and unused pack contents are not included.
