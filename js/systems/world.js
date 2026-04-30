// World generation, faction spawning, and entity creation.
Game.prototype.reset = function() {
  gid = 1;
  this.units = [];
  this.buildings = [];
  this.resources = [];
  this.decor = [];
  this.projectiles = [];
  this.effects = [];
  this.factions = FACTIONS.map((f) => ({
    ...f,
    res: f.ai ? { wood: 520, gold: 430, food: 12 } : { wood: 500, gold: 390, food: 10 },
    alive: true,
    aiState: {
      timer: 0,
      buildTimer: 0,
      attackTimer: 9 + Math.random() * 10,
      rallyAngle: Math.random() * Math.PI * 2,
      expansion: 0,
      squadGoal: null,
      economyBias: Math.random()
    },
    underAttack: 0
  }));
  this.generateWorld();
  for (const f of this.factions) this.spawnFaction(f);
  this.camera.x = clamp(this.factions[0].base.x - VIEW_W / this.camera.zoom / 2, 0, WORLD_W - VIEW_W / this.camera.zoom);
  this.camera.y = clamp(this.factions[0].base.y - VIEW_H / this.camera.zoom / 2, 0, WORLD_H - VIEW_H / this.camera.zoom);
};

Game.prototype.generateWorld = function() {
  this.resources.length = 0;
  this.decor.length = 0;
  this.generateTerrain();

  const addRing = (kind, cx, cy, count, minR, maxR, arc = Math.PI * 2, start = 0) => {
    let made = 0;
    for (let i = 0; i < count * 8 && made < count; i++) {
      const a = start + (i / Math.max(1, count)) * arc + (Math.random() - .5) * .7;
      const r = minR + Math.random() * (maxR - minR);
      const x = cx + Math.cos(a) * r + (Math.random() - .5) * 92;
      const y = cy + Math.sin(a) * r + (Math.random() - .5) * 92;
      if (!this.isSafeLand(x, y, 42) || this.occupiedByBase(x, y, 170) || this.tooCloseResource(x, y, kind === 'tree' ? 54 : 64)) continue;
      if (kind === 'decor') this.decor.push({ id: gid++, kind: choose(['bush1','bush2','bush3','bush4','rock1','rock2','rock3','rock4']), x, y, scale: .54 + Math.random() * .22, front: false });
      else this.addResource(kind, x, y);
      made++;
    }
  };

  for (const f of FACTIONS) {
    const b = f.base;
    addRing('tree', b.x - 140, b.y + 300, 22, 260, 690, Math.PI * 1.45, Math.PI * .08);
    addRing('gold', b.x + 260, b.y - 190, 8, 280, 640, Math.PI * .95, -Math.PI * .35);
    addRing('food', b.x - 360, b.y - 130, 7, 260, 610, Math.PI * 1.15, Math.PI * .78);
    addRing('decor', b.x, b.y, 16, 420, 830);
  }

  for (let g = 0; g < 52; g++) {
    const p = this.randomLandPoint(280);
    if (!p || this.occupiedByBase(p.x, p.y, 460)) continue;
    const roll = rngHash(g, 55, 301);
    const kind = roll < .62 ? 'tree' : roll < .84 ? 'gold' : 'food';
    const count = kind === 'tree' ? 10 + Math.floor(Math.random() * 15) : 3 + Math.floor(Math.random() * 6);
    const spread = kind === 'tree' ? 250 : 165;
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const r = Math.random() * spread;
      const x = p.x + Math.cos(a) * r;
      const y = p.y + Math.sin(a) * r;
      if (!this.isSafeLand(x, y, 38) || this.occupiedByBase(x, y, 240) || this.tooCloseResource(x, y, kind === 'tree' ? 48 : 60)) continue;
      this.addResource(kind, x, y);
    }
  }

  for (let i = 0; i < 220; i++) {
    const p = this.randomLandPoint(210);
    if (!p || !this.isSafeLand(p.x, p.y, 34) || this.occupiedByBase(p.x, p.y, 300) || this.tooCloseResource(p.x, p.y, 38)) continue;
    this.decor.push({ id: gid++, kind: choose(['bush1','bush2','bush3','bush4','rock1','rock2','rock3','rock4']), x: p.x, y: p.y, scale: .52 + Math.random() * .28, front: false });
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
    paintEllipse(f.base.x, f.base.y + 24, 940, 680, 1, .055);
    paintEllipse(f.base.x + sideX * 360, f.base.y + sideY * 330, 500, 360, 1, .075);
  }
  paintEllipse(center.x, center.y, 1140, 830, 1, .07);
  paintEllipse(center.x - 930, center.y - 120, 560, 360, 1, .08);
  paintEllipse(center.x + 930, center.y + 110, 590, 380, 1, .08);

  for (const f of FACTIONS) paintLine(f.base, center, 190, 1, 92);
  paintLine(FACTIONS[0].base, FACTIONS[1].base, 150, 1, 148);
  paintLine(FACTIONS[2].base, FACTIONS[3].base, 150, 1, 148);
  paintLine(FACTIONS[0].base, FACTIONS[2].base, 140, 1, 104);
  paintLine(FACTIONS[1].base, FACTIONS[3].base, 140, 1, 104);

  const satellites = [
    [.10, .50, 300, 205], [.29, .18, 390, 260], [.70, .16, 350, 235], [.90, .50, 380, 250],
    [.29, .84, 390, 250], [.70, .84, 400, 260], [.50, .10, 285, 190], [.50, .91, 300, 205],
    [.16, .31, 240, 170], [.84, .70, 250, 180]
  ];
  for (const [px, py, rx, ry] of satellites) paintEllipse(WORLD_W * px, WORLD_H * py, rx, ry, 1, .12);

  const coves = [
    [.075, .095, 360, 270], [.24, .075, 330, 235], [.77, .065, 380, 250], [.94, .27, 350, 300],
    [.075, .72, 320, 270], [.24, .94, 420, 270], [.76, .95, 430, 280], [.94, .73, 370, 280],
    [.39, .37, 360, 250], [.62, .61, 430, 285], [.37, .63, 350, 255]
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
    paintEllipse(f.base.x, f.base.y + 20, 950, 690, 1, .02);
    paintLine(f.base, center, 170, 1, 70);
  }
  paintEllipse(center.x, center.y, 1020, 750, 1, .035);

  const landAt = (tx, ty) => tx >= 0 && ty >= 0 && tx < cols && ty < rows && this.landMap[ty * cols + tx] === 1;
  for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
    const wetEdge = landAt(tx, ty) && (!landAt(tx, ty - 1) || !landAt(tx, ty + 1) || !landAt(tx - 1, ty) || !landAt(tx + 1, ty)) ? 1 : 0;
    this.groundVariant[ty * cols + tx] = Math.floor(rngHash(tx, ty, wetEdge ? 1619 : 919) * 24) + wetEdge * 40;
  }
};

Game.prototype.randomLandPoint = function(margin = 0) {
  for (let i = 0; i < 260; i++) {
    const x = margin + Math.random() * (WORLD_W - margin * 2);
    const y = margin + Math.random() * (WORLD_H - margin * 2);
    if (this.isSafeLand(x, y, 38)) return { x, y };
  }
  return { x: WORLD_W / 2, y: WORLD_H / 2 };
};

Game.prototype.addWaterDetails = function() {
  if (!this.landMap) return;
  const nearShoreProps = ['waterRock1','waterRock2','waterRock3','waterRock4','rubberDuck'];
  const clouds = ['cloud1','cloud2','cloud3','cloud4','cloud5','cloud6','cloud7','cloud8'];

  for (let i = 0; i < 135; i++) {
    const x = 100 + Math.random() * (WORLD_W - 200);
    const y = 100 + Math.random() * (WORLD_H - 200);
    if (!this.isWater(x, y)) continue;
    const nearShore = [[72,0],[-72,0],[0,72],[0,-72],[96,64],[-96,-64]].some(([ox, oy]) => !this.isWater(x + ox, y + oy));
    if (!nearShore && Math.random() < .64) continue;
    const kind = choose(nearShoreProps, rngHash(Math.floor(x / 43), Math.floor(y / 43), 555));
    const scale = kind === 'rubberDuck' ? .36 : .50 + Math.random() * .24;
    this.decor.push({ id: gid++, kind, x, y, scale, water: true, front: false, drift: Math.random() * Math.PI * 2 });
  }

  for (let i = 0; i < 42; i++) {
    const x = 170 + Math.random() * (WORLD_W - 340);
    const y = 170 + Math.random() * (WORLD_H - 340);
    if (!this.isWater(x, y)) continue;
    const kind = choose(clouds, rngHash(Math.floor(x / 113), Math.floor(y / 97), 777));
    this.decor.push({ id: gid++, kind, x, y, scale: .22 + Math.random() * .11, sky: true, water: true, front: false, drift: Math.random() * Math.PI * 2, speed: .8 + Math.random() * .8 });
  }
};

Game.prototype.pruneInvalidWorldObjects = function() {
  this.resources = this.resources.filter(r => !this.isWater(r.x, r.y));
  this.decor = this.decor.filter(d => d.water ? this.isWater(d.x, d.y) : this.isSafeLand(d.x, d.y, 18));
};

Game.prototype.isSafeLand = function(x, y, radius = 28) {
  if (this.isWater(x, y)) return false;
  const probes = [[radius,0],[-radius,0],[0,radius],[0,-radius],[radius*.7,radius*.7],[-radius*.7,radius*.7],[radius*.7,-radius*.7],[-radius*.7,-radius*.7]];
  return probes.every(([ox, oy]) => !this.isWater(x + ox, y + oy));
};

Game.prototype.addResource = function(type, x, y) {
  x = clamp(x, 90, WORLD_W - 90); y = clamp(y, 90, WORLD_H - 90);
  if (!this.isSafeLand(x, y, type === 'tree' ? 30 : 24)) return null;
  const amount = type === 'tree' ? 105 + Math.floor(Math.random() * 80) : type === 'gold' ? 160 + Math.floor(Math.random() * 120) : 42 + Math.floor(Math.random() * 16);
  const sprite = type === 'tree' ? choose(['tree1','tree2','tree3','tree4']) : type === 'gold' ? choose(['gold1','gold2','gold3','gold4','gold5','gold6']) : 'sheepIdle';
  const r = type === 'tree' ? 18 : type === 'gold' ? 21 : 16;
  const res = {
    id: gid++, entity: 'resource', type, sprite, x, y, r, amount, max: amount, dead: false, depleted: false,
    bob: Math.random() * 6, vx: 0, vy: 0, wander: Math.random() * 3,
    animal: type === 'food', animalHp: type === 'food' ? 28 : 0, panic: 0, claimedBy: null
  };
  this.resources.push(res);
  return res;
};

Game.prototype.occupiedByBase = function(x, y, radius) {
  return FACTIONS.some(f => dist2(x, y, f.base.x, f.base.y) < radius * radius);
};

Game.prototype.tooCloseResource = function(x, y, radius) {
  const rr = radius * radius;
  return this.resources.some(r => !r.dead && dist2(x, y, r.x, r.y) < rr);
};

Game.prototype.addBuilding = function(fid, type, x, y, complete = false) {
  const def = BUILDINGS[type];
  const b = {
    id: gid++, entity: 'building', faction: fid, type, x, y,
    w: def.w, h: def.h, r: Math.max(def.w, def.h) * .46,
    hp: complete ? def.hp : Math.max(80, def.hp * .28), maxHp: def.hp,
    build: complete ? 1 : 0, buildTime: def.time, queue: [], rally: { x: x, y: y + 190 },
    sprite: type === 'house' ? choose(['house','house2','house3']) : type,
    cd: Math.random(), garrison: [], dead: false, flash: 0, aiIntent: null
  };
  this.buildings.push(b);
  if (complete && type === 'tower') {
      const u = this.addUnit(fid, 'archer', x, y);
      this.finishGarrison(u, b);
  }
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
    stuck: 0, lastWaterBounce: 0, pathProbe: 0, huntSwing: 0
  };
  this.units.push(u);
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
  this.setAutoWorkerOrders(f.id);
};
