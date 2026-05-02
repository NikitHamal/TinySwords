// World generation, faction spawning, and entity creation.
Game.prototype.reset = function() {
  gid = 1;
  this.units = [];
  this.buildings = [];
  this.resources = [];
  this.decor = [];
  this.projectiles = [];
  this.effects = [];
  this.navVersion = 1;
  this.pathGrid = null;
  this.resourceBuckets = new Map();
  this.factions = FACTIONS.map((f) => ({
    ...f,
    res: (() => {
      const diff = DIFFICULTY_PRESETS[this.worldSettings?.difficulty || 'normal'] || DIFFICULTY_PRESETS.normal;
      const mult = f.ai ? diff.aiResourceMult : 1;
      return f.ai
        ? { wood: Math.round(520 * mult), gold: Math.round(430 * mult), food: Math.round(12 * mult) }
        : { wood: 500, gold: 390, food: 10 };
    })(),
    alive: true,
    aiState: {
      timer: 0,
      buildTimer: 0,
      attackTimer: (DIFFICULTY_PRESETS[this.worldSettings?.difficulty || 'normal'] || DIFFICULTY_PRESETS.normal).aiAttackDelay + Math.random() * 10,
      rallyAngle: Math.random() * Math.PI * 2,
      expansion: 0,
      squadGoal: null,
      squadMode: 'stage',
      lastTargetId: null,
      economyBias: Math.random()
    },
    underAttack: 0
  }));
  this.generateWorld();
  for (const f of this.factions) if (f.id === 0 || f.ai) this.spawnFaction(f);
  this.markNavDirty && this.markNavDirty();
  this.rebuildDecorSpatialIndex && this.rebuildDecorSpatialIndex();
  this.buildMinimapTerrainCache && this.buildMinimapTerrainCache();
  this.camera.x = clamp(this.factions[0].base.x - VIEW_W / this.camera.zoom / 2, 0, WORLD_W - VIEW_W / this.camera.zoom);
  this.camera.y = clamp(this.factions[0].base.y - VIEW_H / this.camera.zoom / 2, 0, WORLD_H - VIEW_H / this.camera.zoom);
};

Game.prototype.generateWorld = function() {
  this.resources.length = 0;
  this.decor.length = 0;
  this.generateTerrain();

  const densityScale = RESOURCE_DENSITY_PRESETS[this.worldSettings?.resourceDensity || 'normal'] || 1;
  const graphicsScale = getGraphicsDensityMultiplier(this.worldSettings || {});
  const worldScale = (WORLD_W * WORLD_H) / (8200 * 6000) * densityScale * graphicsScale;
  const naturalDecor = NATURAL_DECOR_KINDS;

  const addRing = (kind, cx, cy, count, minR, maxR, arc = Math.PI * 2, start = 0) => {
    let made = 0;
    for (let i = 0; i < count * 10 && made < count; i++) {
      const a = start + (i / Math.max(1, count)) * arc + (Math.random() - .5) * .72;
      const r = minR + Math.random() * (maxR - minR);
      const x = cx + Math.cos(a) * r + (Math.random() - .5) * 104;
      const y = cy + Math.sin(a) * r + (Math.random() - .5) * 104;
      if (!this.isSafeLand(x, y, 42) || this.occupiedByBase(x, y, 180) || this.tooCloseResource(x, y, kind === 'tree' ? 54 : 64)) continue;
      if (kind === 'decor') {
        this.decor.push({ id: gid++, entity: 'decor', kind: choose(naturalDecor), x, y, scale: .54 + Math.random() * .24, front: false, dead: false });
      } else this.addResource(kind, x, y);
      made++;
    }
  };

  for (const f of FACTIONS) {
    const b = f.base;
    addRing('tree', b.x - 180, b.y + 340, 34, 300, 850, Math.PI * 1.55, Math.PI * .06);
    addRing('gold', b.x + 310, b.y - 230, 12, 320, 780, Math.PI * 1.05, -Math.PI * .38);
    addRing('food', b.x - 430, b.y - 150, 12, 300, 760, Math.PI * 1.25, Math.PI * .72);
    addRing('decor', b.x, b.y, 24, 470, 980);
  }

  const neutralClusterCount = Math.round(52 * worldScale);
  for (let g = 0; g < neutralClusterCount; g++) {
    const p = this.randomLandPoint(340);
    if (!p || this.occupiedByBase(p.x, p.y, 540)) continue;
    const roll = rngHash(g, Math.floor(p.x / 97), Math.floor(p.y / 89));
    const kind = roll < .58 ? 'tree' : roll < .80 ? 'gold' : 'food';
    const count = kind === 'tree' ? 12 + Math.floor(Math.random() * 18) : kind === 'gold' ? 4 + Math.floor(Math.random() * 7) : 5 + Math.floor(Math.random() * 8);
    const spread = kind === 'tree' ? 290 : kind === 'gold' ? 190 : 230;
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const r = Math.random() * spread;
      const x = p.x + Math.cos(a) * r;
      const y = p.y + Math.sin(a) * r;
      if (!this.isSafeLand(x, y, 38) || this.occupiedByBase(x, y, 260) || this.tooCloseResource(x, y, kind === 'tree' ? 48 : 62)) continue;
      this.addResource(kind, x, y);
    }
  }

  const decorCount = Math.round(185 * worldScale);
  for (let i = 0; i < decorCount; i++) {
    const p = this.randomLandPoint(260);
    if (!p || !this.isSafeLand(p.x, p.y, 34) || this.occupiedByBase(p.x, p.y, 330) || this.tooCloseResource(p.x, p.y, 38)) continue;
    this.decor.push({ id: gid++, entity: 'decor', kind: choose(naturalDecor), x: p.x, y: p.y, scale: .52 + Math.random() * .30, front: false, dead: false });
  }

  this.addWaterDetails();
  this.pruneInvalidWorldObjects();
};

Game.prototype.generateTerrain = function() {
  this.landCols = Math.ceil(WORLD_W / TILE);
  this.landRows = Math.ceil(WORLD_H / TILE);
  const cols = this.landCols, rows = this.landRows;
  this.landMap = new Uint8Array(cols * rows);
  this.groundVariant = new Uint8Array(cols * rows);

  const setLand = (tx, ty, v = 1) => {
    if (tx >= 0 && ty >= 0 && tx < cols && ty < rows) this.landMap[ty * cols + tx] = v;
  };
  const paintEllipse = (cx, cy, rx, ry, v = 1, wobble = .08) => {
    const minX = Math.floor((cx - rx) / TILE) - 2, maxX = Math.ceil((cx + rx) / TILE) + 2;
    const minY = Math.floor((cy - ry) / TILE) - 2, maxY = Math.ceil((cy + ry) / TILE) + 2;
    for (let ty = minY; ty <= maxY; ty++) for (let tx = minX; tx <= maxX; tx++) {
      const x = tx * TILE + TILE / 2, y = ty * TILE + TILE / 2;
      const n = (rngHash(tx, ty, 902) - .5) * wobble + Math.sin((tx * 1.7 + ty * .9) * .55) * wobble * .22;
      const d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
      if (d < 1 + n) setLand(tx, ty, v);
    }
  };
  const paintLine = (a, b, width, v = 1, bend = 0) => {
    const steps = Math.ceil(Math.hypot(a.x - b.x, a.y - b.y) / (TILE * .32));
    for (let i = 0; i <= steps; i++) {
      const t = i / Math.max(1, steps);
      const wob = Math.sin(t * Math.PI * 2) * bend;
      const x = a.x + (b.x - a.x) * t + Math.cos(t * Math.PI * 3.2) * wob;
      const y = a.y + (b.y - a.y) * t + Math.sin(t * Math.PI * 2.1) * wob;
      paintEllipse(x, y, width, width * .74, v, .035);
    }
  };

  const center = { x: WORLD_W / 2, y: WORLD_H / 2 };
  for (const f of FACTIONS) {
    const sideX = f.base.x < center.x ? 1 : -1;
    const sideY = f.base.y < center.y ? 1 : -1;
    paintEllipse(f.base.x, f.base.y + 24, 1080, 760, 1, .055);
    paintEllipse(f.base.x + sideX * 470, f.base.y + sideY * 410, 650, 440, 1, .075);
  }

  paintEllipse(center.x, center.y, 1500, 1080, 1, .07);
  paintEllipse(center.x - 1600, center.y - 360, 760, 500, 1, .08);
  paintEllipse(center.x + 1600, center.y + 360, 780, 520, 1, .08);
  paintEllipse(center.x - 1600, center.y + 360, 760, 500, 1, .08);
  paintEllipse(center.x + 1600, center.y - 360, 780, 520, 1, .08);

  for (const f of FACTIONS) paintLine(f.base, center, 220, 1, 124);
  paintLine(FACTIONS[0].base, FACTIONS[1].base, 175, 1, 180);
  paintLine(FACTIONS[2].base, FACTIONS[3].base, 175, 1, 180);
  paintLine(FACTIONS[0].base, FACTIONS[2].base, 165, 1, 135);
  paintLine(FACTIONS[1].base, FACTIONS[3].base, 165, 1, 135);
  paintLine(FACTIONS[0].base, FACTIONS[3].base, 135, 1, 170);
  paintLine(FACTIONS[1].base, FACTIONS[2].base, 135, 1, 170);

  const satellites = [
    [.08,.50,380,260], [.18,.26,430,285], [.18,.74,430,285], [.31,.14,480,310], [.31,.86,480,310],
    [.50,.08,360,240], [.50,.92,380,250], [.69,.14,480,310], [.69,.86,480,310], [.82,.26,430,285],
    [.82,.74,430,285], [.92,.50,400,270], [.38,.34,430,285], [.62,.66,450,300], [.38,.66,430,285],
    [.62,.34,450,300], [.50,.28,360,240], [.50,.72,360,240]
  ];
  for (const [px, py, rx, ry] of satellites) paintEllipse(WORLD_W * px, WORLD_H * py, rx, ry, 1, .12);

  const coves = [
    [.06,.10,440,330], [.20,.07,390,270], [.43,.13,400,270], [.57,.87,420,280], [.80,.07,430,295], [.94,.25,430,340],
    [.06,.74,410,320], [.20,.94,470,300], [.43,.87,390,270], [.57,.13,420,285], [.80,.94,470,300], [.94,.75,430,340],
    [.35,.43,400,285], [.65,.57,470,320], [.35,.57,420,300], [.65,.43,470,320], [.50,.50,310,220]
  ];
  for (const [px, py, rx, ry] of coves) paintEllipse(WORLD_W * px, WORLD_H * py, rx, ry, 0, .09);

  for (let pass = 0; pass < 2; pass++) {
    const src = this.landMap.slice();
    for (let ty = 1; ty < rows - 1; ty++) for (let tx = 1; tx < cols - 1; tx++) {
      const idx = ty * cols + tx;
      let n = 0;
      for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) if (src[(ty + oy) * cols + tx + ox]) n++;
      if (src[idx] && n <= 3) this.landMap[idx] = 0;
      if (!src[idx] && n >= 7) this.landMap[idx] = 1;
    }
  }

  for (const f of FACTIONS) {
    paintEllipse(f.base.x, f.base.y + 20, 1120, 790, 1, .02);
    paintLine(f.base, center, 190, 1, 82);
  }
  paintEllipse(center.x, center.y, 1370, 970, 1, .035);

  const landAt = (tx, ty) => tx >= 0 && ty >= 0 && tx < cols && ty < rows && this.landMap[ty * cols + tx] === 1;
  for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
    const wetEdge = landAt(tx, ty) && (!landAt(tx, ty - 1) || !landAt(tx, ty + 1) || !landAt(tx - 1, ty) || !landAt(tx + 1, ty)) ? 1 : 0;
    this.groundVariant[ty * cols + tx] = Math.floor(rngHash(tx, ty, wetEdge ? 1619 : 919) * 24) + wetEdge * 40;
  }
};

Game.prototype.randomLandPoint = function(margin = 0) {
  for (let i = 0; i < 780; i++) {
    const x = margin + Math.random() * (WORLD_W - margin * 2);
    const y = margin + Math.random() * (WORLD_H - margin * 2);
    if (this.isSafeLand(x, y, 38)) return { x, y };
  }
  return { x: WORLD_W / 2, y: WORLD_H / 2 };
};

Game.prototype.addWaterDetails = function() {
  const graphicsScale = getGraphicsDensityMultiplier(this.worldSettings || {});
  if (!this.landMap) return;
  const nearShoreProps = ['waterRock1','waterRock2','waterRock3','waterRock4','rubberDuck'];
  const clouds = ['cloud1','cloud2','cloud3','cloud4','cloud5','cloud6','cloud7','cloud8'];

  for (let i = 0; i < Math.round(110 * (WORLD_W * WORLD_H) / (8200 * 6000) * graphicsScale); i++) {
    const x = 100 + Math.random() * (WORLD_W - 200);
    const y = 100 + Math.random() * (WORLD_H - 200);
    if (!this.isWater(x, y)) continue;
    const nearShore = [[72,0],[-72,0],[0,72],[0,-72],[96,64],[-96,-64]].some(([ox, oy]) => !this.isWater(x + ox, y + oy));
    if (!nearShore && Math.random() < .64) continue;
    const kind = choose(nearShoreProps, rngHash(Math.floor(x / 43), Math.floor(y / 43), 555));
    const scale = kind === 'rubberDuck' ? .36 : .50 + Math.random() * .24;
    this.decor.push({ id: gid++, entity: 'decor', kind, x, y, scale, water: true, front: false, drift: Math.random() * Math.PI * 2, dead: false });
  }

  for (let i = 0; i < Math.round(32 * (WORLD_W * WORLD_H) / (8200 * 6000) * graphicsScale); i++) {
    const x = 170 + Math.random() * (WORLD_W - 340);
    const y = 170 + Math.random() * (WORLD_H - 340);
    if (!this.isWater(x, y)) continue;
    const kind = choose(clouds, rngHash(Math.floor(x / 113), Math.floor(y / 97), 777));
    this.decor.push({ id: gid++, entity: 'decor', kind, x, y, scale: .22 + Math.random() * .11, sky: true, water: true, front: false, drift: Math.random() * Math.PI * 2, speed: .8 + Math.random() * .8, dead: false });
  }
};

Game.prototype.pruneInvalidWorldObjects = function() {
  this.resources = this.resources.filter(r => !this.isWater(r.x, r.y));
  this.decor = this.decor.filter(d => d.water ? this.isWater(d.x, d.y) : this.isSafeLand(d.x, d.y, 18));
  this.resources = this.resources.filter((r, idx, arr) => {
    const footprint = getResourceFootprint(r);
    for (let i = 0; i < idx; i++) {
      const other = arr[i];
      const min = footprint + getResourceFootprint(other) + 6;
      if (dist2(r.x, r.y, other.x, other.y) < min * min) return false;
    }
    return true;
  });
  this.clearOverlapsAroundStructures();
  this.rebuildDecorSpatialIndex && this.rebuildDecorSpatialIndex();
};

Game.prototype.clearOverlapsAroundStructures = function() {
  if (!this.buildings || !this.buildings.length) return;
  const keepResource = (r) => {
    const fp = getResourceFootprint(r);
    for (const b of this.buildings) {
      if (b.dead) continue;
      const padX = b.w * .5 + fp + 12;
      const padY = b.h * .5 + fp + 18;
      if (Math.abs(r.x - b.x) < padX && Math.abs(r.y - b.y) < padY) return false;
    }
    return true;
  };
  this.resources = this.resources.filter(keepResource);
  this.decor = this.decor.filter((d) => {
    if (d.sky || d.water || PASSABLE_DECOR.has(d.kind)) return true;
    const spec = DECOR_SPECS[d.kind] || {};
    const radius = Math.max(10, ((spec.shadow && spec.shadow[0]) || 14) * (d.scale || 1));
    for (const b of this.buildings) {
      if (b.dead) continue;
      const padX = b.w * .5 + radius + 10;
      const padY = b.h * .5 + radius + 16;
      if (Math.abs(d.x - b.x) < padX && Math.abs(d.y - b.y) < padY) return false;
    }
    return true;
  });
};

Game.prototype.isSafeLand = function(x, y, radius = 28) {
  if (this.isWater(x, y)) return false;
  const probes = [[radius,0],[-radius,0],[0,radius],[0,-radius],[radius*.7,radius*.7],[-radius*.7,radius*.7],[radius*.7,-radius*.7],[-radius*.7,-radius*.7]];
  return probes.every(([ox, oy]) => !this.isWater(x + ox, y + oy));
};

Game.prototype.chooseAnimalKind = function(x, y) {
  const entries = Object.entries(HUNT_ANIMALS);
  const total = entries.reduce((sum, [, def]) => sum + (def.weight || 1), 0);
  let pick = rngHash(Math.floor(x / 137), Math.floor(y / 149), 4301) * total;
  for (const [kind, def] of entries) {
    pick -= def.weight || 1;
    if (pick <= 0) return kind;
  }
  return entries[0] ? entries[0][0] : null;
};

Game.prototype.addResource = function(type, x, y) {
  x = clamp(x, 90, WORLD_W - 90); y = clamp(y, 90, WORLD_H - 90);
  if (!this.isSafeLand(x, y, type === 'tree' ? 36 : 26)) return null;
  const animalKind = type === 'food' ? this.chooseAnimalKind(x, y) : null;
  const animalDef = animalKind ? HUNT_ANIMALS[animalKind] : null;
  const amount = type === 'tree'
    ? 105 + Math.floor(Math.random() * 80)
    : type === 'gold'
      ? 160 + Math.floor(Math.random() * 120)
      : (animalDef ? animalDef.yield + Math.floor(Math.random() * Math.max(4, animalDef.yield * .35)) : 42 + Math.floor(Math.random() * 16));
  const sprite = type === 'tree'
    ? choose(['tree1','tree2','tree3','tree4'])
    : type === 'gold'
      ? choose(['gold1','gold2','gold3','gold4','gold5','gold6'])
      : (animalDef ? animalDef.idle : 'meat');
  const r = type === 'tree' ? 18 : type === 'gold' ? 21 : (animalDef ? animalDef.radius : 16);
  const dirSeed = rngHash(Math.floor(x), Math.floor(y), 8221);
  const res = {
    id: gid++, entity: 'resource', type, sprite, x, y, r, amount, max: amount, dead: false, depleted: false,
    bob: Math.random() * 6, vx: 0, vy: 0, wander: Math.random() * 3,
    animal: type === 'food', animalKind, animalHp: animalDef ? animalDef.hp : (type === 'food' ? 28 : 0),
    animalMaxHp: animalDef ? animalDef.hp : (type === 'food' ? 28 : 0), animalState: 'idle', animalDir: dirSeed < .25 ? 0 : dirSeed < .5 ? 1 : dirSeed < .75 ? 2 : 3,
    panic: 0, hurtTimer: 0, claimedBy: null, face: Math.random() < .5 ? -1 : 1, flash: 0
  };
  const clearance = getResourceFootprint(res) + 8;
  if (this.tooCloseResource(x, y, clearance, res)) return null;
  this.resources.push(res);
  this.resourceBuckets = null;
  return res;
};

Game.prototype.occupiedByBase = function(x, y, radius) {
  return FACTIONS.some(f => dist2(x, y, f.base.x, f.base.y) < radius * radius);
};

Game.prototype.tooCloseResource = function(x, y, radius, candidate = null) {
  const rr = Math.max(0, radius);
  return this.resources.some(r => {
    if (r.dead || r === candidate) return false;
    const min = rr + getResourceFootprint(r);
    return dist2(x, y, r.x, r.y) < min * min;
  });
};

Game.prototype.addBuilding = function(fid, type, x, y, complete = false) {
  const def = BUILDINGS[type];
  const b = {
    id: gid++, entity: 'building', faction: fid, type, x, y,
    w: def.w, h: def.h, r: Math.max(def.w, def.h) * .46,
    hp: complete ? def.hp : Math.max(12, Math.min(def.hp, def.hp * .28)), maxHp: def.hp,
    build: complete ? 1 : 0, buildTime: def.time, queue: [], rally: (def.trains && def.trains.length) ? { x: x, y: y + 190 } : null,
    sprite: type === 'house' ? choose(['house','house2','house3']) : type,
    cd: Math.random(), garrison: [], dead: false, flash: 0, aiIntent: null
  };
  this.buildings.push(b);
  this.buildingBuckets = null;
  this.clearOverlapsAroundStructures();
  this.markNavDirty && this.markNavDirty();
  this.uiDirty = true;
  return b;
};

Game.prototype.addUnit = function(fid, type, x, y) {
  const def = UNITS[type];
  const p = this.nearestLandPoint(x, y, 80) || { x, y };
  const u = {
    id: gid++, entity: 'unit', faction: fid, type,
    x: p.x, y: p.y, r: def.radius, hp: def.hp, maxHp: def.hp, speed: def.speed,
    order: 'idle', target: null, goal: null, attackMove: false,
    cd: Math.random() * .5, anim: Math.random() * 4, face: 1, carry: null, gather: 0,
    selected: false, dead: false, flash: 0, garrisoned: null, hold: false,
    stuck: 0, lastWaterBounce: 0, pathProbe: 0, path: null, pathGoal: null, pathIndex: 0, pathVersion: 0, pathRetry: 0, huntSwing: 0,
    scanTimer: Math.random() * 0.22
  };
  this.units.push(u);
  this.unitBuckets = null;
  this._shouldRebuildSpatial = true;
  return u;
};

Game.prototype.nearestLandPoint = function(x, y, maxR = 220) {
  if (!this.isWater(x, y)) return { x, y };
  for (let r = 20; r <= maxR; r += 20) {
    for (let i = 0; i < 16; i++) {
      const a = Math.PI * 2 * i / 16;
      const px = clamp(x + Math.cos(a) * r, 20, WORLD_W - 20);
      const py = clamp(y + Math.sin(a) * r, 20, WORLD_H - 20);
      if (!this.isWater(px, py)) return { x: px, y: py };
    }
  }
  return null;
};

Game.prototype.spawnFaction = function(f) {
  const b = f.base;
  this.addBuilding(f.id, 'castle', b.x, b.y, true);
  this.addBuilding(f.id, 'house', b.x - 210, b.y + 84, true);
  this.addBuilding(f.id, 'house', b.x + 205, b.y + 92, true);
  this.addBuilding(f.id, 'barracks', b.x - 160, b.y - 190, true);
  this.addBuilding(f.id, 'tower', b.x + 160, b.y + 200, true); // Add initial tower!
  if (f.ai) this.addBuilding(f.id, 'archery', b.x + 185, b.y - 176, true);
  for (let i = 0; i < (f.ai ? 6 : 5); i++) this.addUnit(f.id, 'worker', b.x + (Math.random() - .5) * 180, b.y + 175 + Math.random() * 100);
  for (let i = 0; i < (f.ai ? 5 : 3); i++) this.addUnit(f.id, i % 3 === 0 ? 'archer' : 'warrior', b.x + 135 + Math.random() * 115, b.y + (Math.random() - .5) * 155);
  this.clearOverlapsAroundStructures();
  this.setAutoWorkerOrders(f.id);
};


// Pass 4: selectable production map layouts.
Game.prototype.generateTerrain = function() {
  this.landCols = Math.ceil(WORLD_W / TILE);
  this.landRows = Math.ceil(WORLD_H / TILE);
  const cols = this.landCols, rows = this.landRows;
  this.landMap = new Uint8Array(cols * rows);
  this.groundVariant = new Uint8Array(cols * rows);
  const style = (this.worldSettings && this.worldSettings.mapStyle) || 'crossroads';
  const center = { x: WORLD_W / 2, y: WORLD_H / 2 };

  const setLand = (tx, ty, v = 1) => {
    if (tx >= 0 && ty >= 0 && tx < cols && ty < rows) this.landMap[ty * cols + tx] = v;
  };
  const paintEllipse = (cx, cy, rx, ry, v = 1, wobble = .08) => {
    const minX = Math.floor((cx - rx) / TILE) - 2, maxX = Math.ceil((cx + rx) / TILE) + 2;
    const minY = Math.floor((cy - ry) / TILE) - 2, maxY = Math.ceil((cy + ry) / TILE) + 2;
    for (let ty = minY; ty <= maxY; ty++) for (let tx = minX; tx <= maxX; tx++) {
      const x = tx * TILE + TILE / 2, y = ty * TILE + TILE / 2;
      const n = (rngHash(tx, ty, 902) - .5) * wobble + Math.sin((tx * 1.7 + ty * .9) * .55) * wobble * .22;
      const d = ((x - cx) / Math.max(1, rx)) ** 2 + ((y - cy) / Math.max(1, ry)) ** 2;
      if (d < 1 + n) setLand(tx, ty, v);
    }
  };
  const paintLine = (a, b, width, v = 1, bend = 0, wobbleSeed = 0) => {
    const steps = Math.ceil(Math.hypot(a.x - b.x, a.y - b.y) / (TILE * .34));
    for (let i = 0; i <= steps; i++) {
      const t = i / Math.max(1, steps);
      const wob = Math.sin(t * Math.PI * 2 + wobbleSeed) * bend;
      const x = a.x + (b.x - a.x) * t + Math.cos(t * Math.PI * 3.2 + wobbleSeed) * wob;
      const y = a.y + (b.y - a.y) * t + Math.sin(t * Math.PI * 2.1 + wobbleSeed) * wob;
      paintEllipse(x, y, width, width * .72, v, .035);
    }
  };
  const activeBases = FACTIONS.filter(f => f.id === 0 || f.ai).map(f => f.base);
  const allBases = FACTIONS.map(f => f.base);

  const drawCrossroads = () => {
    for (const b of allBases) {
      const sideX = b.x < center.x ? 1 : -1, sideY = b.y < center.y ? 1 : -1;
      paintEllipse(b.x, b.y + 24, 1080, 760, 1, .055);
      paintEllipse(b.x + sideX * 470, b.y + sideY * 410, 650, 440, 1, .075);
    }
    paintEllipse(center.x, center.y, 1500, 1080, 1, .07);
    [[-1600,-360,760,500],[1600,360,780,520],[-1600,360,760,500],[1600,-360,780,520]].forEach(([ox,oy,rx,ry]) => paintEllipse(center.x+ox, center.y+oy, rx, ry, 1, .08));
    for (const b of allBases) paintLine(b, center, 220, 1, 124);
    paintLine(FACTIONS[0].base, FACTIONS[1].base, 175, 1, 180);
    paintLine(FACTIONS[2].base, FACTIONS[3].base, 175, 1, 180);
    paintLine(FACTIONS[0].base, FACTIONS[2].base, 165, 1, 135);
    paintLine(FACTIONS[1].base, FACTIONS[3].base, 165, 1, 135);
    paintLine(FACTIONS[0].base, FACTIONS[3].base, 135, 1, 170);
    paintLine(FACTIONS[1].base, FACTIONS[2].base, 135, 1, 170);
  };

  const drawArchipelago = () => {
    for (const b of allBases) {
      paintEllipse(b.x, b.y, 880, 680, 1, .10);
      paintEllipse(b.x + (center.x > b.x ? 390 : -390), b.y + (center.y > b.y ? 250 : -250), 440, 330, 1, .11);
      paintLine(b, center, 130, 1, 78);
    }
    paintEllipse(center.x, center.y, 1260, 920, 1, .10);
    for (let i = 0; i < 18; i++) {
      const a = i / 18 * Math.PI * 2;
      const r = 1650 + (i % 3) * 520;
      paintEllipse(center.x + Math.cos(a) * r, center.y + Math.sin(a) * r * .72, 330 + (i % 4) * 60, 230 + (i % 3) * 48, 1, .16);
    }
    paintLine({x: WORLD_W*.20, y: WORLD_H*.50}, {x: WORLD_W*.80, y: WORLD_H*.50}, 115, 1, 220, 3);
    paintLine({x: WORLD_W*.50, y: WORLD_H*.18}, {x: WORLD_W*.50, y: WORLD_H*.82}, 115, 1, 220, 5);
  };

  const drawTwinRivers = () => {
    paintEllipse(center.x, center.y, WORLD_W * .48, WORLD_H * .45, 1, .045);
    for (const b of allBases) paintEllipse(b.x, b.y, 1120, 820, 1, .05);
    for (const b of allBases) paintLine(b, center, 240, 1, 95);
    paintLine({x: WORLD_W*.18, y: -220}, {x: WORLD_W*.40, y: WORLD_H+220}, 190, 0, 260, 1.7);
    paintLine({x: WORLD_W*.60, y: -220}, {x: WORLD_W*.82, y: WORLD_H+220}, 190, 0, 260, 2.9);
    for (const y of [.24, .50, .76]) {
      paintLine({x: WORLD_W*.12, y: WORLD_H*y}, {x: WORLD_W*.88, y: WORLD_H*y}, 110, 1, 150, y * 12);
    }
  };

  const drawFourCorners = () => {
    for (const b of allBases) {
      paintEllipse(b.x, b.y, 1040, 780, 1, .08);
      paintEllipse(b.x + (center.x > b.x ? 520 : -520), b.y, 480, 330, 1, .12);
      paintEllipse(b.x, b.y + (center.y > b.y ? 520 : -520), 480, 330, 1, .12);
      paintLine(b, center, 170, 1, 120);
    }
    paintEllipse(center.x, center.y, 1380, 980, 1, .09);
    paintEllipse(center.x, center.y, 360, 260, 0, .06);
    paintLine({x: WORLD_W*.15, y: center.y}, {x: WORLD_W*.85, y: center.y}, 150, 1, 110, 4);
    paintLine({x: center.x, y: WORLD_H*.15}, {x: center.x, y: WORLD_H*.85}, 150, 1, 110, 6);
  };

  const drawKingRoad = () => {
    paintLine({x: WORLD_W*.05, y: center.y}, {x: WORLD_W*.95, y: center.y}, 430, 1, 85, 0);
    paintLine({x: center.x, y: WORLD_H*.05}, {x: center.x, y: WORLD_H*.95}, 330, 1, 90, 2);
    paintEllipse(center.x, center.y, 1500, 1120, 1, .04);
    for (const b of allBases) {
      paintEllipse(b.x, b.y, 980, 760, 1, .05);
      paintLine(b, center, 230, 1, 70);
    }
    for (let i = 0; i < 10; i++) {
      const t = (i + .5) / 10;
      paintEllipse(WORLD_W * t, center.y + (i % 2 ? 720 : -720), 420, 300, 1, .12);
    }
  };

  const drawSpiral = () => {
    paintEllipse(center.x, center.y, 1280, 960, 1, .08);
    for (const b of allBases) paintEllipse(b.x, b.y, 910, 660, 1, .08);
    let prev = { x: center.x + 260, y: center.y };
    for (let i = 1; i <= 72; i++) {
      const a = i * .34;
      const r = 260 + i * 34;
      const cur = { x: center.x + Math.cos(a) * r, y: center.y + Math.sin(a) * r * .72 };
      paintLine(prev, cur, 190, 1, 12, i);
      prev = cur;
    }
    for (const b of allBases) paintLine(b, center, 160, 1, 95);
  };

  const drawGoldRush = () => {
    paintEllipse(center.x, center.y, WORLD_W * .46, WORLD_H * .42, 1, .06);
    paintEllipse(center.x, center.y, 1180, 820, 1, .02);
    for (const b of allBases) {
      paintEllipse(b.x, b.y, 920, 700, 1, .055);
      paintLine(b, center, 240, 1, 80);
    }
    const holes = [[.28,.50,360,260],[.72,.50,360,260],[.50,.28,340,230],[.50,.72,340,230]];
    holes.forEach(([px,py,rx,ry]) => paintEllipse(WORLD_W*px, WORLD_H*py, rx, ry, 0, .09));
  };

  const drawHighlands = () => {
    paintEllipse(center.x, center.y, WORLD_W * .47, WORLD_H * .43, 1, .07);
    for (const b of allBases) {
      paintEllipse(b.x, b.y, 1020, 740, 1, .07);
      paintLine(b, center, 205, 1, 110);
    }
    const lakes = [[.30,.32,420,300],[.70,.32,420,300],[.30,.68,420,300],[.70,.68,420,300],[.50,.50,360,260],[.50,.18,280,210],[.50,.82,280,210]];
    lakes.forEach(([px,py,rx,ry]) => paintEllipse(WORLD_W*px, WORLD_H*py, rx, ry, 0, .10));
    for (let i = 0; i < 16; i++) {
      const a = Math.PI * 2 * i / 16;
      paintEllipse(center.x + Math.cos(a) * 1760, center.y + Math.sin(a) * 1260, 360, 250, 1, .16);
    }
  };

  ({
    archipelago: drawArchipelago,
    twinrivers: drawTwinRivers,
    fourcorners: drawFourCorners,
    kingroad: drawKingRoad,
    spiral: drawSpiral,
    goldrush: drawGoldRush,
    highlands: drawHighlands,
    crossroads: drawCrossroads
  }[style] || drawCrossroads)();

  const satellites = [
    [.08,.50,380,260], [.18,.26,430,285], [.18,.74,430,285], [.31,.14,480,310], [.31,.86,480,310],
    [.50,.08,360,240], [.50,.92,380,250], [.69,.14,480,310], [.69,.86,480,310], [.82,.26,430,285],
    [.82,.74,430,285], [.92,.50,400,270], [.38,.34,430,285], [.62,.66,450,300], [.38,.66,430,285],
    [.62,.34,450,300], [.50,.28,360,240], [.50,.72,360,240]
  ];
  for (const [px, py, rx, ry] of satellites) if (rngHash(Math.floor(px*100), Math.floor(py*100), style.length) > .25) paintEllipse(WORLD_W * px, WORLD_H * py, rx, ry, 1, .12);

  for (let pass = 0; pass < 2; pass++) {
    const src = this.landMap.slice();
    for (let ty = 1; ty < rows - 1; ty++) for (let tx = 1; tx < cols - 1; tx++) {
      const idx = ty * cols + tx;
      let n = 0;
      for (let oy = -1; oy <= 1; oy++) for (let ox = -1; ox <= 1; ox++) if (src[(ty + oy) * cols + tx + ox]) n++;
      if (src[idx] && n <= 3) this.landMap[idx] = 0;
      if (!src[idx] && n >= 7) this.landMap[idx] = 1;
    }
  }

  // Always guarantee clean spawn plazas and a navigable path from every active base.
  for (const b of activeBases) {
    paintEllipse(b.x, b.y + 20, 1120, 790, 1, .02);
    paintLine(b, center, style === 'archipelago' ? 135 : 190, 1, 82);
  }
  paintEllipse(center.x, center.y, 1240, 900, 1, .035);

  const landAt = (tx, ty) => tx >= 0 && ty >= 0 && tx < cols && ty < rows && this.landMap[ty * cols + tx] === 1;
  for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
    const wetEdge = landAt(tx, ty) && (!landAt(tx, ty - 1) || !landAt(tx, ty + 1) || !landAt(tx - 1, ty) || !landAt(tx + 1, ty)) ? 1 : 0;
    const styleSalt = hashStringSeed(style) % 9973;
    this.groundVariant[ty * cols + tx] = Math.floor(rngHash(tx, ty, wetEdge ? 1619 + styleSalt : 919 + styleSalt) * 24) + wetEdge * 40;
  }
};
