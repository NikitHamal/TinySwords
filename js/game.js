(() => {
  'use strict';

  const canvas = document.getElementById('gameCanvas');
  const ctx = canvas.getContext('2d', { alpha: false });
  const mini = document.getElementById('miniMap');
  const mctx = mini.getContext('2d', { alpha: false });
  ctx.imageSmoothingEnabled = false;
  mctx.imageSmoothingEnabled = false;

  const HUD = {
    root: document.getElementById('hud'),
    resources: document.getElementById('resources'),
    state: document.getElementById('stateReadout'),
    selectionHeader: document.getElementById('selectionHeader'),
    selectionBody: document.getElementById('selectionBody'),
    actionTitle: document.getElementById('actionTitle'),
    actionBar: document.getElementById('actionBar'),
    buildMenu: document.getElementById('buildMenu'),
    buildButtons: document.getElementById('buildButtons'),
    message: document.getElementById('messageToast'),
    help: document.getElementById('helpOverlay'),
    miniWrap: document.getElementById('miniWrap'),
    loading: document.getElementById('loading'),
    miniToggle: document.getElementById('miniToggle'),
    helpClose: document.getElementById('helpClose')
  };

  const VIEW_W = 1280;
  const VIEW_H = 720;
  const WORLD_W = 7200;
  const WORLD_H = 5200;
  const TILE = 64;
  const BASE = 'assets/Tiny Swords (Free Pack)/';
  const MAX_DT = 1 / 24;

  const FACTIONS = [
    { id: 0, key: 'blue', name: 'Blue Realm', folder: 'Blue', ai: false, color: '#61b7d9', dark: '#1f5670', base: { x: 1250, y: 1150 } },
    { id: 1, key: 'red', name: 'Red Dominion', folder: 'Red', ai: true, color: '#db6060', dark: '#78232b', base: { x: 6040, y: 1120 } },
    { id: 2, key: 'yellow', name: 'Golden Clan', folder: 'Yellow', ai: true, color: '#e6ca59', dark: '#80651e', base: { x: 1180, y: 4280 } },
    { id: 3, key: 'purple', name: 'Violet Order', folder: 'Purple', ai: true, color: '#b071df', dark: '#4a246e', base: { x: 6040, y: 4200 } },
    { id: 4, key: 'black', name: 'Iron Pact', folder: 'Black', ai: true, color: '#aeb3bd', dark: '#30353d', base: { x: 3600, y: 2700 } }
  ];

  const RESOURCES = {
    wood: { label: 'Wood', icon: 'resWood', tint: '#9ccb77' },
    gold: { label: 'Gold', icon: 'resGold', tint: '#f7dc62' },
    food: { label: 'Food', icon: 'resFood', tint: '#f6a167' }
  };

  const BUILDINGS = {
    castle: { label: 'Castle', file: 'Castle.png', scale: 0.38, w: 138, h: 106, hp: 1200, pop: 12, cost: { wood: 280, gold: 160, food: 0 }, time: 32, trains: ['worker', 'warrior'], key: 'C', icon: 'iconCastle' },
    house: { label: 'House', file: 'House1.png', scale: 0.38, w: 58, h: 52, hp: 260, pop: 8, cost: { wood: 70, gold: 15, food: 0 }, time: 12, trains: [], key: 'H', icon: 'iconHouse' },
    barracks: { label: 'Barracks', file: 'Barracks.png', scale: 0.35, w: 76, h: 68, hp: 520, pop: 0, cost: { wood: 145, gold: 85, food: 0 }, time: 22, trains: ['warrior', 'lancer'], key: 'R', icon: 'iconBarracks' },
    archery: { label: 'Archery', file: 'Archery.png', scale: 0.35, w: 76, h: 68, hp: 440, pop: 0, cost: { wood: 120, gold: 95, food: 0 }, time: 20, trains: ['archer'], key: 'A', icon: 'iconArchery' },
    tower: { label: 'Tower', file: 'Tower.png', scale: 0.36, w: 46, h: 76, hp: 640, pop: 0, cost: { wood: 110, gold: 115, food: 0 }, time: 20, trains: [], key: 'T', icon: 'iconTower', tower: true, range: 360, garrisonCap: 2 },
    monastery: { label: 'Monastery', file: 'Monastery.png', scale: 0.32, w: 78, h: 90, hp: 420, pop: 0, cost: { wood: 120, gold: 165, food: 0 }, time: 24, trains: ['monk'], key: 'M', icon: 'iconMonastery' }
  };

  const UNITS = {
    worker: { label: 'Worker', role: 'worker', hp: 55, speed: 96, range: 22, damage: 5, cd: 0.65, cost: { wood: 0, gold: 35, food: 1 }, time: 8, pop: 1, fw: 192, fh: 192, scale: 0.27, radius: 10, icon: 'iconWorker', hotkey: '1' },
    warrior: { label: 'Warrior', role: 'melee', hp: 95, speed: 78, range: 28, damage: 15, cd: 0.78, cost: { wood: 0, gold: 65, food: 1 }, time: 10, pop: 1, fw: 192, fh: 192, scale: 0.28, radius: 11, icon: 'iconWarrior', hotkey: '2' },
    archer: { label: 'Archer', role: 'ranged', hp: 62, speed: 74, range: 290, damage: 12, cd: 1.18, cost: { wood: 40, gold: 70, food: 1 }, time: 12, pop: 1, fw: 192, fh: 192, scale: 0.27, radius: 10, icon: 'iconArcher', hotkey: '3' },
    lancer: { label: 'Lancer', role: 'melee', hp: 135, speed: 88, range: 36, damage: 23, cd: 1.05, cost: { wood: 55, gold: 95, food: 2 }, time: 16, pop: 2, fw: 320, fh: 320, scale: 0.20, radius: 13, icon: 'iconLancer', hotkey: '4' },
    monk: { label: 'Monk', role: 'healer', hp: 64, speed: 70, range: 215, damage: -16, cd: 1.1, cost: { wood: 25, gold: 110, food: 1 }, time: 14, pop: 1, fw: 192, fh: 192, scale: 0.27, radius: 10, icon: 'iconMonk', hotkey: '5' }
  };

  const ICON_PATHS = {
    resWood: BASE + 'Terrain/Resources/Wood/Wood Resource/Wood Resource.png',
    resGold: BASE + 'Terrain/Resources/Gold/Gold Resource/Gold_Resource.png',
    resFood: BASE + 'Terrain/Resources/Meat/Meat Resource/Meat Resource.png',
    iconWorker: BASE + 'Units/Blue Units/Pawn/Pawn_Idle.png',
    iconWarrior: BASE + 'Units/Blue Units/Warrior/Warrior_Idle.png',
    iconArcher: BASE + 'Units/Blue Units/Archer/Archer_Idle.png',
    iconLancer: BASE + 'Units/Blue Units/Lancer/Lancer_Idle.png',
    iconMonk: BASE + 'Units/Blue Units/Monk/Idle.png',
    iconCastle: BASE + 'Buildings/Blue Buildings/Castle.png',
    iconHouse: BASE + 'Buildings/Blue Buildings/House1.png',
    iconBarracks: BASE + 'Buildings/Blue Buildings/Barracks.png',
    iconArchery: BASE + 'Buildings/Blue Buildings/Archery.png',
    iconTower: BASE + 'Buildings/Blue Buildings/Tower.png',
    iconMonastery: BASE + 'Buildings/Blue Buildings/Monastery.png',
    iconMove: BASE + 'UI Elements/UI Elements/Icons/Icon_01.png',
    iconAttack: BASE + 'UI Elements/UI Elements/Swords/Swords.png',
    iconStop: BASE + 'UI Elements/UI Elements/Buttons/TinyRoundRedButton.png',
    iconBuild: BASE + 'UI Elements/UI Elements/Icons/Icon_08.png',
    iconRally: BASE + 'UI Elements/UI Elements/Ribbons/SmallRibbons.png',
    iconRepair: BASE + 'Terrain/Resources/Tools/Tool_04.png',
    iconGarrison: BASE + 'UI Elements/UI Elements/Icons/Icon_05.png'
  };

  const IMAGE_PATHS = {
    tileGrass: BASE + 'Terrain/Tileset/Tilemap_color1.png',
    tileAlt: BASE + 'Terrain/Tileset/Tilemap_color2.png',
    water: BASE + 'Terrain/Tileset/Water Background color.png',
    waterFoam: BASE + 'Terrain/Tileset/Water Foam.png',
    shadow: BASE + 'Terrain/Tileset/Shadow.png',
    tree1: BASE + 'Terrain/Resources/Wood/Trees/Tree1.png',
    tree2: BASE + 'Terrain/Resources/Wood/Trees/Tree2.png',
    tree3: BASE + 'Terrain/Resources/Wood/Trees/Tree3.png',
    tree4: BASE + 'Terrain/Resources/Wood/Trees/Tree4.png',
    stump1: BASE + 'Terrain/Resources/Wood/Trees/Stump 1.png',
    stump2: BASE + 'Terrain/Resources/Wood/Trees/Stump 2.png',
    gold1: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 1.png',
    gold2: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 2.png',
    gold3: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 3.png',
    gold4: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 4.png',
    gold5: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 5.png',
    sheepIdle: BASE + 'Terrain/Resources/Meat/Sheep/Sheep_Idle.png',
    sheepMove: BASE + 'Terrain/Resources/Meat/Sheep/Sheep_Move.png',
    bush1: BASE + 'Terrain/Decorations/Bushes/Bushe1.png',
    bush2: BASE + 'Terrain/Decorations/Bushes/Bushe2.png',
    bush3: BASE + 'Terrain/Decorations/Bushes/Bushe3.png',
    bush4: BASE + 'Terrain/Decorations/Bushes/Bushe4.png',
    rock1: BASE + 'Terrain/Decorations/Rocks/Rock1.png',
    rock2: BASE + 'Terrain/Decorations/Rocks/Rock2.png',
    rock3: BASE + 'Terrain/Decorations/Rocks/Rock3.png',
    rock4: BASE + 'Terrain/Decorations/Rocks/Rock4.png',
    waterRock1: BASE + 'Terrain/Decorations/Rocks in the Water/Water Rocks_01.png',
    waterRock2: BASE + 'Terrain/Decorations/Rocks in the Water/Water Rocks_02.png',
    waterRock3: BASE + 'Terrain/Decorations/Rocks in the Water/Water Rocks_03.png',
    waterRock4: BASE + 'Terrain/Decorations/Rocks in the Water/Water Rocks_04.png',
    cloud1: BASE + 'Terrain/Decorations/Clouds/Clouds_01.png',
    cloud2: BASE + 'Terrain/Decorations/Clouds/Clouds_02.png',
    cloud3: BASE + 'Terrain/Decorations/Clouds/Clouds_03.png',
    dust: BASE + 'Particle FX/Dust_01.png',
    explosion: BASE + 'Particle FX/Explosion_01.png',
    fire: BASE + 'Particle FX/Fire_03.png',
    healFx: BASE + 'Units/Blue Units/Monk/Heal_Effect.png',
    blueArrow: BASE + 'Units/Blue Units/Archer/Arrow.png',
    redArrow: BASE + 'Units/Red Units/Archer/Arrow.png',
    yellowArrow: BASE + 'Units/Yellow Units/Archer/Arrow.png',
    purpleArrow: BASE + 'Units/Purple Units/Archer/Arrow.png',
    blackArrow: BASE + 'Units/Black Units/Archer/Arrow.png',
    cursorMove: BASE + 'UI Elements/UI Elements/Cursors/Cursor_02.png'
  };

  Object.assign(IMAGE_PATHS, ICON_PATHS);

  for (const f of FACTIONS) {
    const bf = `${f.folder} Buildings`;
    for (const [type, def] of Object.entries(BUILDINGS)) {
      IMAGE_PATHS[`b_${f.key}_${type}`] = `${BASE}Buildings/${bf}/${def.file}`;
    }
    const uf = `${f.folder} Units`;
    IMAGE_PATHS[`u_${f.key}_worker_idle`] = `${BASE}Units/${uf}/Pawn/Pawn_Idle.png`;
    IMAGE_PATHS[`u_${f.key}_worker_run`] = `${BASE}Units/${uf}/Pawn/Pawn_Run.png`;
    IMAGE_PATHS[`u_${f.key}_worker_chop`] = `${BASE}Units/${uf}/Pawn/Pawn_Interact Axe.png`;
    IMAGE_PATHS[`u_${f.key}_worker_mine`] = `${BASE}Units/${uf}/Pawn/Pawn_Interact Pickaxe.png`;
    IMAGE_PATHS[`u_${f.key}_worker_carryWood`] = `${BASE}Units/${uf}/Pawn/Pawn_Run Wood.png`;
    IMAGE_PATHS[`u_${f.key}_worker_carryGold`] = `${BASE}Units/${uf}/Pawn/Pawn_Run Gold.png`;
    IMAGE_PATHS[`u_${f.key}_worker_carryFood`] = `${BASE}Units/${uf}/Pawn/Pawn_Run Meat.png`;
    IMAGE_PATHS[`u_${f.key}_warrior_idle`] = `${BASE}Units/${uf}/Warrior/Warrior_Idle.png`;
    IMAGE_PATHS[`u_${f.key}_warrior_run`] = `${BASE}Units/${uf}/Warrior/Warrior_Run.png`;
    IMAGE_PATHS[`u_${f.key}_warrior_attack`] = `${BASE}Units/${uf}/Warrior/Warrior_Attack1.png`;
    IMAGE_PATHS[`u_${f.key}_archer_idle`] = `${BASE}Units/${uf}/Archer/Archer_Idle.png`;
    IMAGE_PATHS[`u_${f.key}_archer_run`] = `${BASE}Units/${uf}/Archer/Archer_Run.png`;
    IMAGE_PATHS[`u_${f.key}_archer_attack`] = `${BASE}Units/${uf}/Archer/Archer_Shoot.png`;
    IMAGE_PATHS[`u_${f.key}_lancer_idle`] = `${BASE}Units/${uf}/Lancer/Lancer_Idle.png`;
    IMAGE_PATHS[`u_${f.key}_lancer_run`] = `${BASE}Units/${uf}/Lancer/Lancer_Run.png`;
    IMAGE_PATHS[`u_${f.key}_lancer_attack`] = `${BASE}Units/${uf}/Lancer/Lancer_Right_Attack.png`;
    IMAGE_PATHS[`u_${f.key}_monk_idle`] = `${BASE}Units/${uf}/Monk/Idle.png`;
    IMAGE_PATHS[`u_${f.key}_monk_run`] = `${BASE}Units/${uf}/Monk/Run.png`;
    IMAGE_PATHS[`u_${f.key}_monk_attack`] = `${BASE}Units/${uf}/Monk/Heal.png`;
  }

  const assets = {};
  const keys = new Set();
  let gid = 1;

  function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }
  function len(x, y) { return Math.hypot(x, y); }
  function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }
  function dist2(ax, ay, bx, by) { const dx = ax - bx; const dy = ay - by; return dx * dx + dy * dy; }
  function rngHash(x, y, seed = 11) {
    let n = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(seed, 1442695041);
    n = (n ^ (n >>> 13)) | 0;
    n = Math.imul(n, 1274126177);
    return ((n ^ (n >>> 16)) >>> 0) / 4294967295;
  }
  function choose(arr, n) { return arr[Math.floor((n === undefined ? Math.random() : n) * arr.length) % arr.length]; }
  function rectsOverlap(a, b) { return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y; }
  function isAlive(e) { return e && !e.dead && e.hp > 0; }
  function faction(id) { return FACTIONS[id]; }
  function fmtCost(cost) {
    return `W${cost.wood || 0} G${cost.gold || 0}${cost.food ? ` F${cost.food}` : ''}`;
  }
  function canAfford(f, cost) {
    return f.res.wood >= (cost.wood || 0) && f.res.gold >= (cost.gold || 0) && f.res.food >= (cost.food || 0);
  }
  function pay(f, cost) {
    if (!canAfford(f, cost)) return false;
    f.res.wood -= cost.wood || 0;
    f.res.gold -= cost.gold || 0;
    f.res.food -= cost.food || 0;
    return true;
  }
  function addRes(f, type, amount) { f.res[type] = Math.min(9999, f.res[type] + amount); }
  function screenToWorld(game, x, y) { return { x: game.camera.x + x / game.camera.zoom, y: game.camera.y + y / game.camera.zoom }; }
  function worldToScreen(game, x, y) { return { x: (x - game.camera.x) * game.camera.zoom, y: (y - game.camera.y) * game.camera.zoom }; }

  function loadImages(paths) {
    const entries = Object.entries(paths);
    let loaded = 0;
    return Promise.all(entries.map(([key, src]) => new Promise((resolve) => {
      const img = new Image();
      img.onload = () => { assets[key] = img; loaded++; HUD.loading.textContent = `Loading Tiny Swords assets... ${Math.round(loaded / entries.length * 100)}%`; resolve(); };
      img.onerror = () => { console.warn('Missing asset:', key, src); assets[key] = null; loaded++; resolve(); };
      img.src = src;
    })));
  }

  class SoundBank {
    constructor() { this.ctx = null; this.muted = localStorage.getItem('tiny-swords-rts-muted') === '1'; }
    resume() { if (this.muted) return; if (!this.ctx) this.ctx = new (window.AudioContext || window.webkitAudioContext)(); if (this.ctx.state === 'suspended') this.ctx.resume(); }
    tone(freq, time = .08, type = 'square', gain = .025, slide = 1) {
      if (this.muted) return;
      this.resume();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const o = this.ctx.createOscillator();
      const g = this.ctx.createGain();
      o.type = type; o.frequency.setValueAtTime(freq, now); if (slide !== 1) o.frequency.exponentialRampToValueAtTime(Math.max(20, freq * slide), now + time);
      g.gain.setValueAtTime(gain, now); g.gain.exponentialRampToValueAtTime(0.0001, now + time);
      o.connect(g).connect(this.ctx.destination); o.start(now); o.stop(now + time + .04);
    }
    click() { this.tone(560, .04, 'triangle', .018, 1.3); }
    deny() { this.tone(120, .12, 'square', .03, .7); }
    build() { this.tone(260, .08, 'triangle', .028, 1.4); setTimeout(() => this.tone(410, .1, 'triangle', .02, 1.2), 70); }
    attack() { this.tone(180, .07, 'sawtooth', .018, 1.6); }
    alert() { this.tone(170, .15, 'sawtooth', .03, 1.7); setTimeout(() => this.tone(220, .15, 'sawtooth', .02, 1.2), 120); }
  }

  class Game {
    constructor() {
      this.sfx = new SoundBank();
      this.camera = { x: 700, y: 720, zoom: 0.76, targetZoom: 0.76 };
      this.pointer = { x: 0, y: 0, wx: 0, wy: 0, down: false, dragging: false, startX: 0, startY: 0, startWx: 0, startWy: 0 };
      this.paused = false;
      this.fast = false;
      this.uiDirty = true;
      this.uiTimer = 0;
      this.time = 0;
      this.toastTimer = 0;
      this.selected = [];
      this.placing = null;
      this.aiTick = 0;
      this.lastFrame = 0;
      this.reset();
      this.bindEvents();
      this.buildStaticMenus();
      this.toast('Scout, build, gather, train, and conquer. Press H for help.', 5);
    }

    reset() {
      gid = 1;
      this.units = [];
      this.buildings = [];
      this.resources = [];
      this.decor = [];
      this.projectiles = [];
      this.effects = [];
      this.factions = FACTIONS.map((f) => ({
        ...f,
        res: f.ai ? { wood: 300, gold: 260, food: 6 } : { wood: 280, gold: 240, food: 5 },
        alive: true,
        aiState: { timer: 0, buildTimer: 0, attackTimer: 3 + Math.random() * 8, rallyAngle: Math.random() * Math.PI * 2, expansion: 0 },
        underAttack: 0
      }));
      this.generateWorld();
      for (const f of this.factions) this.spawnFaction(f);
      this.camera.x = clamp(this.factions[0].base.x - VIEW_W / this.camera.zoom / 2, 0, WORLD_W - VIEW_W / this.camera.zoom);
      this.camera.y = clamp(this.factions[0].base.y - VIEW_H / this.camera.zoom / 2, 0, WORLD_H - VIEW_H / this.camera.zoom);
    }

    generateWorld() {
      this.resources.length = 0;
      this.decor.length = 0;
      this.generateTerrain();
      const addRing = (kind, cx, cy, count, minR, maxR, arc = Math.PI * 2, start = 0) => {
        let made = 0;
        for (let i = 0; i < count * 5 && made < count; i++) {
          const a = start + (i / Math.max(1, count)) * arc + (Math.random() - .5) * .65;
          const r = minR + Math.random() * (maxR - minR);
          const x = cx + Math.cos(a) * r + (Math.random() - .5) * 85;
          const y = cy + Math.sin(a) * r + (Math.random() - .5) * 85;
          if (this.isWater(x, y) || this.occupiedByBase(x, y, 160) || this.tooCloseResource(x, y, kind === 'tree' ? 46 : 54)) continue;
          if (kind === 'decor') this.decor.push({ id: gid++, kind: choose(['bush1','bush2','bush3','bush4','rock1','rock2','rock3','rock4']), x, y, scale: .20 + Math.random() * .14, front: false });
          else this.addResource(kind, x, y);
          made++;
        }
      };
      for (const f of FACTIONS) {
        const b = f.base;
        addRing('tree', b.x - 120, b.y + 260, 18, 240, 620, Math.PI * 1.45, Math.PI * .08);
        addRing('gold', b.x + 220, b.y - 160, 7, 250, 570, Math.PI * .95, -Math.PI * .35);
        addRing('food', b.x - 320, b.y - 120, 7, 230, 520, Math.PI * 1.15, Math.PI * .78);
        addRing('decor', b.x, b.y, 14, 360, 760);
      }
      for (let g = 0; g < 34; g++) {
        const p = this.randomLandPoint(260);
        if (!p || this.occupiedByBase(p.x, p.y, 420)) continue;
        const roll = rngHash(g, 55, 301);
        const kind = roll < .64 ? 'tree' : roll < .84 ? 'gold' : 'food';
        const count = kind === 'tree' ? 9 + Math.floor(Math.random() * 13) : 3 + Math.floor(Math.random() * 5);
        const spread = kind === 'tree' ? 230 : 150;
        for (let i = 0; i < count; i++) {
          const a = Math.random() * Math.PI * 2;
          const r = Math.random() * spread;
          const x = p.x + Math.cos(a) * r;
          const y = p.y + Math.sin(a) * r;
          if (this.isWater(x, y) || this.occupiedByBase(x, y, 220) || this.tooCloseResource(x, y, kind === 'tree' ? 42 : 54)) continue;
          this.addResource(kind, x, y);
        }
      }
      for (let i = 0; i < 150; i++) {
        const p = this.randomLandPoint(190);
        if (!p || this.occupiedByBase(p.x, p.y, 280) || this.tooCloseResource(p.x, p.y, 34)) continue;
        this.decor.push({ id: gid++, kind: choose(['bush1','bush2','bush3','bush4','rock1','rock2','rock3','rock4']), x: p.x, y: p.y, scale: .6 + Math.random() * .3, front: false });
      }
      this.addWaterDetails();
    }
    generateTerrain() {
      this.landCols = Math.ceil(WORLD_W / TILE);
      this.landRows = Math.ceil(WORLD_H / TILE);
      const cols = this.landCols, rows = this.landRows;
      this.landMap = new Uint8Array(cols * rows);
      this.groundVariant = new Uint8Array(cols * rows);
      const setLand = (tx, ty, v = 1) => { if (tx >= 0 && ty >= 0 && tx < cols && ty < rows) this.landMap[ty * cols + tx] = v; };
      const paintEllipse = (cx, cy, rx, ry, v = 1, wobble = .06) => {
        const minX = Math.floor((cx - rx) / TILE) - 2, maxX = Math.ceil((cx + rx) / TILE) + 2;
        const minY = Math.floor((cy - ry) / TILE) - 2, maxY = Math.ceil((cy + ry) / TILE) + 2;
        for (let ty = minY; ty <= maxY; ty++) for (let tx = minX; tx <= maxX; tx++) {
          const x = tx * TILE + TILE / 2, y = ty * TILE + TILE / 2;
          const n = (rngHash(tx, ty, 902) - .5) * wobble;
          const d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
          if (d < 1 + n) setLand(tx, ty, v);
        }
      };
      const paintLine = (a, b, width, v = 1, bend = 0) => {
        const steps = Math.ceil(Math.hypot(a.x - b.x, a.y - b.y) / (TILE * .35));
        for (let i = 0; i <= steps; i++) {
          const t = i / Math.max(1, steps);
          const wob = Math.sin(t * Math.PI * 2) * bend;
          const x = a.x + (b.x - a.x) * t + Math.cos(t * Math.PI * 3) * wob;
          const y = a.y + (b.y - a.y) * t + Math.sin(t * Math.PI * 2.4) * wob;
          paintEllipse(x, y, width, width * .82, v, .03);
        }
      };
      for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) setLand(tx, ty, 1);
      paintEllipse(-360, WORLD_H * .5, 620, WORLD_H * .56, 0, .03);
      paintEllipse(WORLD_W + 360, WORLD_H * .52, 690, WORLD_H * .58, 0, .03);
      paintEllipse(WORLD_W * .5, -280, WORLD_W * .42, 520, 0, .03);
      paintEllipse(WORLD_W * .5, WORLD_H + 320, WORLD_W * .46, 560, 0, .03);
      paintEllipse(850, 320, 720, 420, 0, .05);
      paintEllipse(6250, 4860, 680, 430, 0, .05);
      paintLine({x: 200, y: 2700}, {x: WORLD_W - 200, y: 2440}, 130, 0, 110);
      paintLine({x: 3550, y: 250}, {x: 3850, y: WORLD_H - 250}, 110, 0, 75);
      paintEllipse(2520, 1600, 410, 250, 0, .08);
      paintEllipse(4940, 3530, 520, 310, 0, .08);
      paintEllipse(1750, 3660, 430, 260, 0, .07);
      const bridges = [
        {x: 1180, y: 2660, rx: 270, ry: 120}, {x: 3600, y: 2510, rx: 310, ry: 130}, {x: 6040, y: 2350, rx: 290, ry: 125},
        {x: 3560, y: 1120, rx: 120, ry: 285}, {x: 3740, y: 4200, rx: 120, ry: 285}
      ];
      for (const b of bridges) paintEllipse(b.x, b.y, b.rx, b.ry, 1, .02);
      for (const f of FACTIONS) paintEllipse(f.base.x, f.base.y + 40, 780, 560, 1, .02);
      for (const f of FACTIONS) paintLine(f.base, {x: WORLD_W / 2, y: WORLD_H / 2}, 120, 1, 50);
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
      for (const f of FACTIONS) paintEllipse(f.base.x, f.base.y + 40, 800, 580, 1, .01);
      for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) this.groundVariant[ty * cols + tx] = Math.floor(rngHash(tx, ty, 919) * 12);
    }
    randomLandPoint(margin = 0) {
      for (let i = 0; i < 180; i++) {
        const x = margin + Math.random() * (WORLD_W - margin * 2);
        const y = margin + Math.random() * (WORLD_H - margin * 2);
        if (!this.isWater(x, y)) return { x, y };
      }
      return { x: WORLD_W / 2, y: WORLD_H / 2 };
    }
    addWaterDetails() {
      if (!this.landMap) return;
      for (let i = 0; i < 95; i++) {
        const x = 120 + Math.random() * (WORLD_W - 240);
        const y = 120 + Math.random() * (WORLD_H - 240);
        if (!this.isWater(x, y)) continue;
        const kind = choose(['waterRock1','waterRock2','waterRock3','waterRock4','cloud1','cloud2','cloud3']);
        const scale = kind.startsWith('cloud') ? .8 + Math.random() * .6 : .5 + Math.random() * .3;
        this.decor.push({ id: gid++, kind, x, y, scale, water: true, front: false });
      }
    }
    spawnFaction(f) {
      const b = f.base;
      this.addBuilding(f.id, 'castle', b.x, b.y, true);
      this.addBuilding(f.id, 'house', b.x - 190, b.y + 72, true);
      this.addBuilding(f.id, 'house', b.x + 188, b.y + 84, true);
      this.addBuilding(f.id, 'barracks', b.x - 140, b.y - 174, true);
      if (f.ai) this.addBuilding(f.id, 'archery', b.x + 160, b.y - 160, true);
      for (let i = 0; i < (f.ai ? 5 : 4); i++) this.addUnit(f.id, 'worker', b.x + (Math.random() - .5) * 160, b.y + 165 + Math.random() * 85);
      for (let i = 0; i < (f.ai ? 4 : 2); i++) this.addUnit(f.id, i % 3 === 0 ? 'archer' : 'warrior', b.x + 120 + Math.random() * 90, b.y + (Math.random() - .5) * 140);
      this.setAutoWorkerOrders(f.id);
    }

    addResource(type, x, y) {
      x = clamp(x, 90, WORLD_W - 90); y = clamp(y, 90, WORLD_H - 90);
      if (this.isWater(x, y)) return null;
      const amount = type === 'tree' ? 90 + Math.floor(Math.random() * 70) : type === 'gold' ? 140 + Math.floor(Math.random() * 110) : 80 + Math.floor(Math.random() * 50);
      const sprite = type === 'tree' ? choose(['tree1','tree2','tree3','tree4']) : type === 'gold' ? choose(['gold1','gold2','gold3','gold4','gold5']) : 'sheepIdle';
      const r = type === 'tree' ? 18 : type === 'gold' ? 21 : 16;
      const res = { id: gid++, entity: 'resource', type, sprite, x, y, r, amount, max: amount, dead: false, depleted: false, bob: Math.random() * 6, vx: 0, vy: 0, wander: Math.random() * 3 };
      this.resources.push(res);
      return res;
    }

    occupiedByBase(x, y, radius) {
      return FACTIONS.some(f => dist2(x, y, f.base.x, f.base.y) < radius * radius);
    }

    tooCloseResource(x, y, radius) {
      const rr = radius * radius;
      return this.resources.some(r => !r.dead && dist2(x, y, r.x, r.y) < rr);
    }
    addBuilding(fid, type, x, y, complete = false) {
      const def = BUILDINGS[type];
      const b = {
        id: gid++, entity: 'building', faction: fid, type, x, y,
        w: def.w, h: def.h, r: Math.max(def.w, def.h) * .46,
        hp: complete ? def.hp : Math.max(80, def.hp * .28), maxHp: def.hp,
        build: complete ? 1 : 0, buildTime: def.time, queue: [], rally: { x: x, y: y + 180 },
        cd: Math.random(), garrison: [], dead: false, flash: 0
      };
      this.buildings.push(b);
      this.uiDirty = true;
      return b;
    }

    addUnit(fid, type, x, y) {
      const def = UNITS[type];
      const u = {
        id: gid++, entity: 'unit', faction: fid, type,
        x, y, r: def.radius, hp: def.hp, maxHp: def.hp, speed: def.speed,
        order: 'idle', target: null, goal: null, attackMove: false,
        cd: Math.random() * .5, anim: Math.random() * 4, face: 1, carry: null, gather: 0,
        selected: false, dead: false, flash: 0, garrisoned: null, hold: false
      };
      this.units.push(u);
      return u;
    }

    bindEvents() {
      window.addEventListener('keydown', (e) => {
        const tag = document.activeElement && document.activeElement.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
        keys.add(e.key.toLowerCase());
        if (e.key === ' ') { e.preventDefault(); this.paused = !this.paused; this.toast(this.paused ? 'Paused' : 'Resumed', 1.1); }
        if (e.key.toLowerCase() === 'h') this.toggleHelp();
        if (e.key.toLowerCase() === 'm') this.toggleMini();
        if (e.key.toLowerCase() === 'b') this.toggleBuildMenu();
        if (e.key === 'Escape') this.cancelModes();
        if (e.key === '0') this.selectUnits(this.units.filter(u => u.faction === 0 && !u.garrisoned));
        if (e.ctrlKey && e.key.toLowerCase() === 'a') { e.preventDefault(); this.selectUnits(this.units.filter(u => u.faction === 0 && u.type !== 'worker' && !u.garrisoned)); }
        if (/^[1-9]$/.test(e.key)) this.activateHotkey(e.key);
      });
      window.addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));
      window.addEventListener('blur', () => keys.clear());

      canvas.addEventListener('mousemove', (e) => {
        this.updatePointer(e);
        if (this.pointer.down) {
          const dx = Math.abs(this.pointer.x - this.pointer.startX);
          const dy = Math.abs(this.pointer.y - this.pointer.startY);
          this.pointer.dragging = dx + dy > 8;
        }
      });
      canvas.addEventListener('mousedown', (e) => {
        this.sfx.resume();
        this.updatePointer(e);
        if (e.button === 0) {
          if (this.placing) {
            this.tryPlace(this.placing, this.pointer.wx, this.pointer.wy);
            return;
          }
          this.pointer.down = true; this.pointer.dragging = false;
          this.pointer.startX = this.pointer.x; this.pointer.startY = this.pointer.y;
          this.pointer.startWx = this.pointer.wx; this.pointer.startWy = this.pointer.wy;
        }
      });
      canvas.addEventListener('mouseup', (e) => {
        this.updatePointer(e);
        if (e.button === 0 && this.pointer.down) {
          if (this.pointer.dragging) this.dragSelect(e.shiftKey);
          else this.clickSelect(e.shiftKey);
        }
        this.pointer.down = false; this.pointer.dragging = false;
      });
      canvas.addEventListener('contextmenu', (e) => { e.preventDefault(); this.updatePointer(e); this.contextOrder(this.pointer.wx, this.pointer.wy); });
      canvas.addEventListener('wheel', (e) => {
        e.preventDefault();
        const before = screenToWorld(this, this.pointer.x, this.pointer.y);
        this.camera.targetZoom = clamp(this.camera.targetZoom * (e.deltaY < 0 ? 1.09 : 0.92), 0.55, 1.15);
        this.camera.zoom = this.camera.targetZoom;
        this.camera.x = clamp(before.x - this.pointer.x / this.camera.zoom, 0, WORLD_W - VIEW_W / this.camera.zoom);
        this.camera.y = clamp(before.y - this.pointer.y / this.camera.zoom, 0, WORLD_H - VIEW_H / this.camera.zoom);
      }, { passive: false });

      mini.addEventListener('click', (e) => {
        const r = mini.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width * WORLD_W;
        const y = (e.clientY - r.top) / r.height * WORLD_H;
        this.centerCamera(x, y);
      });
      HUD.miniToggle.addEventListener('click', () => this.toggleMini());
      HUD.helpClose.addEventListener('click', () => this.toggleHelp(false));
      window.addEventListener('resize', () => this.resizeMini());
    }

    updatePointer(e) {
      const rect = canvas.getBoundingClientRect();
      this.pointer.x = clamp((e.clientX - rect.left) / rect.width * VIEW_W, 0, VIEW_W);
      this.pointer.y = clamp((e.clientY - rect.top) / rect.height * VIEW_H, 0, VIEW_H);
      const w = screenToWorld(this, this.pointer.x, this.pointer.y);
      this.pointer.wx = w.x; this.pointer.wy = w.y;
    }

    cancelModes() {
      if (this.placing) { this.placing = null; this.toast('Build cancelled.', 1.1); }
      HUD.buildMenu.classList.add('hidden');
      HUD.help.classList.add('hidden');
      this.uiDirty = true;
    }

    toggleHelp(force) {
      const show = force === undefined ? HUD.help.classList.contains('hidden') : force;
      HUD.help.classList.toggle('hidden', !show);
    }

    toggleMini() { HUD.miniWrap.classList.toggle('expanded'); document.body.classList.toggle('map-open', HUD.miniWrap.classList.contains('expanded')); this.resizeMini(); }
    toggleBuildMenu() { HUD.buildMenu.classList.toggle('hidden'); this.placing = null; this.sfx.click(); }

    resizeMini() {
      const r = mini.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) { mini.width = Math.floor(r.width); mini.height = Math.floor(r.height); mctx.imageSmoothingEnabled = false; }
    }

    buildStaticMenus() {
      HUD.buildButtons.innerHTML = '';
      for (const [type, def] of Object.entries(BUILDINGS)) {
        if (type === 'castle') continue;
        const btn = this.makeButton({
          className: 'build-card', icon: def.icon, title: `${def.key}: ${def.label}`, sub: fmtCost(def.cost),
          onClick: () => { this.startPlacing(type); }
        });
        btn.dataset.type = type;
        HUD.buildButtons.appendChild(btn);
      }
    }

    makeButton({ className = 'command', icon, title, sub, onClick, disabled = false }) {
      const b = document.createElement('button');
      b.className = className + (disabled ? ' disabled' : '');
      b.type = 'button';
      b.innerHTML = `<img src="${IMAGE_PATHS[icon] || IMAGE_PATHS.iconMove}" alt=""><span class="txt"><b>${title}</b><span>${sub || ''}</span></span>`;
      b.addEventListener('click', () => { if (b.classList.contains('disabled')) { this.sfx.deny(); return; } this.sfx.click(); onClick && onClick(); });
      return b;
    }

    startPlacing(type) {
      this.placing = type;
      HUD.buildMenu.classList.add('hidden');
      this.toast(`Placing ${BUILDINGS[type].label}: left click land, right click/Esc cancels.`, 2.2);
      this.uiDirty = true;
    }

    tryPlace(type, x, y) {
      const f = this.factions[0];
      const def = BUILDINGS[type];
      if (!canAfford(f, def.cost)) { this.toast('Not enough resources.', 1.4); this.sfx.deny(); return false; }
      if (!this.canPlace(type, x, y)) { this.toast('Cannot build there.', 1.4); this.sfx.deny(); return false; }
      pay(f, def.cost);
      const b = this.addBuilding(0, type, x, y, false);
      this.effects.push({ kind: 'dust', x, y, time: .8, max: .8 });
      this.placing = null;
      this.select([b]);
      this.toast(`${def.label} foundation placed.`, 1.8);
      this.sfx.build();
      return true;
    }

    canPlace(type, x, y) {
      const def = BUILDINGS[type];
      if (x < 120 || y < 120 || x > WORLD_W - 120 || y > WORLD_H - 120) return false;
      if (this.isWater(x, y)) return false;
      const rect = { x: x - def.w / 2 - 12, y: y - def.h / 2 - 18, w: def.w + 24, h: def.h + 34 };
      for (const b of this.buildings) if (!b.dead && rectsOverlap(rect, { x: b.x - b.w / 2, y: b.y - b.h / 2, w: b.w, h: b.h })) return false;
      for (const r of this.resources) if (!r.dead && r.amount > 0 && rectsOverlap(rect, { x: r.x - r.r, y: r.y - r.r, w: r.r * 2, h: r.r * 2 })) return false;
      return true;
    }

    clickSelect(add) {
      const e = this.pickEntity(this.pointer.wx, this.pointer.wy);
      if (!e) { if (!add) this.select([]); return; }
      if (e.entity === 'resource') { this.select([e]); return; }
      if (e.faction === 0) {
        if (add) {
          const set = new Set(this.selected);
          if (set.has(e)) set.delete(e); else set.add(e);
          this.select([...set]);
        } else this.select([e]);
      } else this.select([e]);
    }

    dragSelect(add) {
      const x1 = Math.min(this.pointer.startWx, this.pointer.wx);
      const y1 = Math.min(this.pointer.startWy, this.pointer.wy);
      const x2 = Math.max(this.pointer.startWx, this.pointer.wx);
      const y2 = Math.max(this.pointer.startWy, this.pointer.wy);
      const hits = this.units.filter(u => u.faction === 0 && !u.garrisoned && u.x >= x1 && u.x <= x2 && u.y >= y1 && u.y <= y2);
      if (add) this.select([...new Set([...this.selected.filter(e => e.faction === 0), ...hits])]);
      else this.select(hits);
    }

    select(list) {
      this.selected.forEach(e => { if (e.entity === 'unit') e.selected = false; });
      this.selected = list.filter(isAlive);
      this.selected.forEach(e => { if (e.entity === 'unit') e.selected = true; });
      this.uiDirty = true;
    }
    selectUnits(list) { this.select(list); this.centerOnSelection(false); }

    pickEntity(x, y) {
      for (let i = this.units.length - 1; i >= 0; i--) {
        const u = this.units[i];
        if (!u.dead && !u.garrisoned && dist2(x, y, u.x, u.y) <= (u.r + 7) * (u.r + 7)) return u;
      }
      for (let i = this.buildings.length - 1; i >= 0; i--) {
        const b = this.buildings[i];
        if (!b.dead && Math.abs(x - b.x) <= b.w * .62 && Math.abs(y - b.y) <= b.h * .62) return b;
      }
      for (let i = this.resources.length - 1; i >= 0; i--) {
        const r = this.resources[i];
        if (!r.dead && r.amount > 0 && dist2(x, y, r.x, r.y) <= (r.r + 9) * (r.r + 9)) return r;
      }
      return null;
    }

    contextOrder(x, y) {
      if (this.placing) { this.placing = null; this.uiDirty = true; return; }
      const target = this.pickEntity(x, y);
      const ownBuildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1);
      const ownUnits = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && !e.garrisoned);
      if (ownBuildings.length && (!target || target.entity !== 'resource')) {
        for (const b of ownBuildings) b.rally = { x, y };
        this.effects.push({ kind: 'flag', x, y, time: 1.2, max: 1.2 });
        this.toast('Rally flag set.', 1.1);
        this.sfx.click();
      }
      if (!ownUnits.length) return;
      if (target && target.entity === 'building' && target.faction === 0 && target.type === 'tower') {
        this.garrisonArchers(ownUnits.filter(u => u.type === 'archer'), target);
        return;
      }
      if (target && target.entity === 'resource') {
        const workers = ownUnits.filter(u => u.type === 'worker');
        for (const u of workers) this.orderHarvest(u, target);
        if (workers.length) this.toast(`${workers.length} worker(s) harvesting ${target.type === 'tree' ? 'wood' : target.type}.`, 1.4);
        return;
      }
      if (target && target.faction !== undefined && target.faction !== 0) {
        for (const u of ownUnits) this.orderAttack(u, target, false);
        this.sfx.attack();
        return;
      }
      this.orderMoveFormation(ownUnits, x, y, false);
      this.sfx.click();
    }

    orderMoveFormation(units, x, y, attackMove) {
      const n = units.length;
      const cols = Math.ceil(Math.sqrt(n));
      const spacing = 42;
      units.forEach((u, i) => {
        const ox = ((i % cols) - (cols - 1) / 2) * spacing;
        const oy = (Math.floor(i / cols) - Math.floor(n / cols) / 2) * spacing;
        u.goal = { x: clamp(x + ox, 30, WORLD_W - 30), y: clamp(y + oy, 30, WORLD_H - 30) };
        u.order = attackMove ? 'attackMove' : 'move';
        u.target = null; u.attackMove = attackMove; u.hold = false;
      });
      this.effects.push({ kind: attackMove ? 'attack' : 'move', x, y, time: .7, max: .7 });
    }

    orderAttack(u, target, attackMove) {
      u.target = target; u.order = 'attack'; u.goal = null; u.attackMove = attackMove; u.hold = false;
    }

    orderHarvest(u, res) {
      if (u.type !== 'worker') return;
      u.order = 'harvest'; u.target = res; u.goal = null; u.gather = 0; u.hold = false;
    }

    garrisonArchers(archers, tower, silent = false) {
      if (!tower || tower.type !== 'tower') return;
      let moved = 0;
      for (const a of archers) {
        if (tower.garrison.length >= BUILDINGS.tower.garrisonCap) break;
        a.garrisoned = tower.id;
        a.order = 'garrison'; a.target = tower; a.selected = false;
        tower.garrison.push(a.id);
        moved++;
      }
      if (moved) {
        if (!silent && tower.faction === 0) { this.toast(`${moved} archer(s) assigned to tower.`, 1.6); this.sfx.build(); this.select(this.selected.filter(e => !(e.entity === 'unit' && e.garrisoned))); }
      } else if (!silent) { this.toast('Select archers and right click a tower.', 1.5); this.sfx.deny(); }
    }

    activateHotkey(key) {
      const buttons = [...HUD.actionBar.querySelectorAll('button[data-hotkey]')];
      const b = buttons.find(el => el.dataset.hotkey === key.toLowerCase());
      if (b) b.click();
    }

    centerOnSelection(instant = true) {
      const own = this.selected.filter(e => e.x !== undefined);
      if (!own.length) return;
      const x = own.reduce((s, e) => s + e.x, 0) / own.length;
      const y = own.reduce((s, e) => s + e.y, 0) / own.length;
      this.centerCamera(x, y, instant);
    }

    centerCamera(x, y) {
      this.camera.x = clamp(x - VIEW_W / this.camera.zoom / 2, 0, WORLD_W - VIEW_W / this.camera.zoom);
      this.camera.y = clamp(y - VIEW_H / this.camera.zoom / 2, 0, WORLD_H - VIEW_H / this.camera.zoom);
    }

    toast(text, time = 2) {
      HUD.message.textContent = text;
      HUD.message.classList.remove('hidden');
      this.toastTimer = time;
    }

    run(ts) {
      const dt = Math.min(MAX_DT, (ts - this.lastFrame) / 1000 || 0);
      this.lastFrame = ts;
      this.update(dt * (this.fast ? 1.7 : 1));
      this.draw();
      requestAnimationFrame(t => this.run(t));
    }

    update(dt) {
      this.time += dt;
      if (this.toastTimer > 0) { this.toastTimer -= dt; if (this.toastTimer <= 0) HUD.message.classList.add('hidden'); }
      this.updateCamera(dt);
      if (!this.paused) {
        this.updateBuildings(dt);
        this.updateResources(dt);
        this.updateUnits(dt);
        this.updateProjectiles(dt);
        this.updateEffects(dt);
        this.updateAI(dt);
        this.cleanup();
      }
      this.uiTimer -= dt;
      if (this.uiDirty || this.uiTimer <= 0) { this.renderUI(); this.uiTimer = .25; this.uiDirty = false; }
    }

    updateCamera(dt) {
      const margin = 18;
      const sp = (keys.has('shift') ? 760 : 520) * dt / this.camera.zoom;
      let dx = 0, dy = 0;
      if (keys.has('a') || keys.has('arrowleft')) dx -= sp;
      if (keys.has('d') || keys.has('arrowright')) dx += sp;
      if (keys.has('w') || keys.has('arrowup')) dy -= sp;
      if (keys.has('s') || keys.has('arrowdown')) dy += sp;
      if (this.pointer.x < margin) dx -= sp * .75;
      if (this.pointer.x > VIEW_W - margin) dx += sp * .75;
      if (this.pointer.y < margin) dy -= sp * .75;
      if (this.pointer.y > VIEW_H - margin) dy += sp * .75;
      this.camera.x = clamp(this.camera.x + dx, 0, WORLD_W - VIEW_W / this.camera.zoom);
      this.camera.y = clamp(this.camera.y + dy, 0, WORLD_H - VIEW_H / this.camera.zoom);
      this.camera.zoom += (this.camera.targetZoom - this.camera.zoom) * Math.min(1, dt * 8);
    }

    updateBuildings(dt) {
      for (const b of this.buildings) {
        if (b.dead) continue;
        b.flash = Math.max(0, b.flash - dt * 3);
        if (b.build < 1) {
          b.build = Math.min(1, b.build + dt / b.buildTime);
          b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * .9);
          if (b.build >= 1 && b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} completed.`, 1.4); this.sfx.build(); }
          continue;
        }
        if (b.queue.length) {
          const q = b.queue[0]; q.time -= dt;
          if (q.time <= 0) {
            b.queue.shift();
            const u = this.addUnit(b.faction, q.type, b.x + (Math.random() - .5) * 60, b.y + b.h * .48 + 26);
            if (b.rally) this.orderMoveFormation([u], b.rally.x, b.rally.y, false);
            if (b.faction === 0) this.uiDirty = true;
          }
        }
        if (b.type === 'tower' && b.garrison.length) this.towerAttack(b, dt);
        if (b.type === 'monastery') this.passiveHeal(b, dt);
      }
    }

    towerAttack(b, dt) {
      b.cd -= dt;
      if (b.cd > 0) return;
      const target = this.nearestEnemy({ x: b.x, y: b.y, faction: b.faction }, BUILDINGS.tower.range, true);
      if (!target) return;
      b.cd = Math.max(.42, 1.15 - b.garrison.length * .22);
      this.spawnProjectile(b.faction, b.x, b.y - 70, target, 15 + b.garrison.length * 4);
    }

    passiveHeal(b, dt) {
      b.cd -= dt;
      if (b.cd > 0) return;
      let healed = false;
      for (const u of this.units) {
        if (u.faction === b.faction && !u.dead && !u.garrisoned && u.hp < u.maxHp && dist2(u.x, u.y, b.x, b.y) < 240 * 240) {
          u.hp = Math.min(u.maxHp, u.hp + 10); healed = true;
        }
      }
      if (healed) { b.cd = 1.2; this.effects.push({ kind: 'heal', x: b.x, y: b.y - 10, time: .9, max: .9 }); }
    }

    updateResources(dt) {
      for (const r of this.resources) {
        if (r.dead || r.type !== 'food' || r.amount <= 0) continue;
        r.wander -= dt;
        if (r.wander <= 0) {
          r.wander = 1.2 + Math.random() * 3.4;
          const a = Math.random() * Math.PI * 2;
          const sp = 10 + Math.random() * 22;
          r.vx = Math.cos(a) * sp;
          r.vy = Math.sin(a) * sp;
        }
        const nx = r.x + r.vx * dt;
        const ny = r.y + r.vy * dt;
        if (!this.isWater(nx, ny) && !this.occupiedByBase(nx, ny, 90)) { r.x = nx; r.y = ny; }
        else { r.vx *= -0.6; r.vy *= -0.6; r.wander = .4; }
        r.vx *= .985; r.vy *= .985;
      }
    }

    updateUnits(dt) {
      for (const u of this.units) {
        if (u.dead || u.garrisoned) continue;
        u.flash = Math.max(0, u.flash - dt * 4);
        u.cd = Math.max(0, u.cd - dt);
        u.anim += dt * (u.order === 'move' || u.order === 'attackMove' ? 8 : u.order === 'attack' ? 7 : 4);
        if (u.type === 'monk') this.updateMonk(u, dt);
        else if (u.type === 'worker') this.updateWorker(u, dt);
        else this.updateFighter(u, dt);
        this.separate(u, dt);
        u.x = clamp(u.x, 20, WORLD_W - 20); u.y = clamp(u.y, 20, WORLD_H - 20);
      }
    }

    updateWorker(u, dt) {
      if (u.order === 'harvest') {
        const res = u.target;
        if (!res || res.dead || res.amount <= 0) { u.carry = null; u.order = 'idle'; u.target = null; return; }
        if (u.carry) {
          const drop = this.nearestDropoff(u.faction, u.x, u.y);
          if (!drop) { u.order = 'idle'; return; }
          if (this.moveToward(u, drop.x, drop.y + 36, dt, 24)) {
            addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
            u.carry = null; u.gather = 0;
          }
          return;
        }
        if (this.moveToward(u, res.x, res.y, dt, res.r + 8)) {
          u.gather += dt;
          if (u.gather >= (res.type === 'tree' ? 1.4 : res.type === 'gold' ? 1.65 : 1.1)) {
            const amount = res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14;
            res.amount -= amount;
            u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
            u.gather = 0;
            if (res.amount <= 0) {
              if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; }
              else res.dead = true;
              this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
            }
          }
        }
        return;
      }
      this.updateFighter(u, dt);
      if (u.order === 'idle' && this.factions[u.faction].ai) this.autoGather(u);
    }

    updateMonk(u, dt) {
      const ally = this.lowestHurtAlly(u, UNITS.monk.range);
      if (ally && u.cd <= 0) {
        u.face = ally.x >= u.x ? 1 : -1;
        ally.hp = Math.min(ally.maxHp, ally.hp + 18);
        u.cd = UNITS.monk.cd;
        this.effects.push({ kind: 'heal', x: ally.x, y: ally.y - 28, time: .6, max: .6 });
        u.order = 'heal';
        return;
      }
      if (u.order === 'heal') u.order = 'idle';
      this.updateFighter(u, dt);
    }

    updateFighter(u, dt) {
      if ((u.order === 'idle' || u.order === 'attackMove') && !u.hold) {
        const enemy = this.nearestEnemy(u, u.attackMove ? 420 : 310, true);
        if (enemy) this.orderAttack(u, enemy, u.order === 'attackMove');
      }
      if (u.order === 'attack' && isAlive(u.target)) {
        const def = UNITS[u.type];
        const d = dist(u, u.target);
        if (d > def.range * .94) this.moveToward(u, u.target.x, u.target.y, dt, def.range * .82);
        else this.attackTarget(u, u.target);
        return;
      }
      if (u.order === 'attack' && (!u.target || !isAlive(u.target))) {
        u.target = null; u.order = u.attackMove ? 'attackMove' : 'idle';
      }
      if ((u.order === 'move' || u.order === 'attackMove') && u.goal) {
        if (this.moveToward(u, u.goal.x, u.goal.y, dt, 8)) { if (u.order === 'move') u.order = 'idle'; u.goal = null; }
      }
    }

    moveToward(u, x, y, dt, stop = 6) {
      const dx = x - u.x, dy = y - u.y;
      const d = Math.hypot(dx, dy);
      if (d <= stop) return true;
      const sp = u.speed * dt * (u.carry ? .77 : 1);
      u.x += dx / d * Math.min(sp, d - stop);
      u.y += dy / d * Math.min(sp, d - stop);
      u.face = dx >= 0 ? 1 : -1;
      return false;
    }

    separate(u, dt) {
      let sx = 0, sy = 0;
      for (const v of this.units) {
        if (v === u || v.dead || v.garrisoned) continue;
        const min = u.r + v.r + 3;
        const dx = u.x - v.x, dy = u.y - v.y;
        const d2 = dx * dx + dy * dy;
        if (d2 > 0 && d2 < min * min) { const d = Math.sqrt(d2); sx += dx / d * (min - d); sy += dy / d * (min - d); }
      }
      u.x += sx * dt * 2.2; u.y += sy * dt * 2.2;
    }

    attackTarget(u, target) {
      const def = UNITS[u.type];
      if (u.cd > 0) return;
      u.face = target.x >= u.x ? 1 : -1;
      u.cd = def.cd;
      if (u.type === 'archer') this.spawnProjectile(u.faction, u.x, u.y - 34, target, def.damage);
      else this.damage(target, def.damage, u.faction);
    }

    spawnProjectile(fid, x, y, target, damage) {
      this.projectiles.push({ id: gid++, x, y, faction: fid, target, damage, speed: 510, life: 2.2, dead: false });
    }

    updateProjectiles(dt) {
      for (const p of this.projectiles) {
        if (p.dead) continue;
        p.life -= dt;
        if (p.life <= 0 || !isAlive(p.target)) { p.dead = true; continue; }
        const tx = p.target.x, ty = p.target.y - (p.target.entity === 'building' ? 38 : 28);
        const dx = tx - p.x, dy = ty - p.y, d = Math.hypot(dx, dy);
        if (d < 18) { this.damage(p.target, p.damage, p.faction); p.dead = true; }
        else { p.x += dx / d * p.speed * dt; p.y += dy / d * p.speed * dt; }
      }
    }

    damage(target, amount, sourceFaction) {
      if (!isAlive(target)) return;
      target.hp -= amount;
      target.flash = 1;
      if (target.entity === 'building') this.factions[target.faction].underAttack = 5;
      if (target.faction === 0 && sourceFaction !== 0 && Math.random() < .08) this.sfx.alert();
      if (target.hp <= 0) {
        target.dead = true;
        this.effects.push({ kind: 'boom', x: target.x, y: target.y - 30, time: 1.0, max: 1.0 });
        if (target.entity === 'building' && target.type === 'tower') {
          for (const id of target.garrison) {
            const u = this.units.find(unit => unit.id === id);
            if (u && !u.dead) { u.garrisoned = null; u.x = target.x + (Math.random() - .5) * 50; u.y = target.y + 40; u.order = 'idle'; }
          }
        }
        this.checkDefeat(target.faction);
      }
    }

    checkDefeat(fid) {
      const f = this.factions[fid];
      if (!f || !f.alive) return;
      const hasCastle = this.buildings.some(b => b.faction === fid && b.type === 'castle' && !b.dead);
      const hasUnits = this.units.some(u => u.faction === fid && !u.dead);
      if (!hasCastle && !hasUnits) {
        f.alive = false;
        this.toast(`${f.name} has fallen.`, 3);
        if (fid === 0) this.toast('Your realm has fallen. Press refresh for another war.', 8);
      }
    }

    nearestEnemy(u, range, includeBuildings) {
      let best = null, bd = range * range;
      for (const v of this.units) {
        if (v.dead || v.garrisoned || v.faction === u.faction || !this.factions[v.faction].alive) continue;
        const d = dist2(u.x, u.y, v.x, v.y);
        if (d < bd) { bd = d; best = v; }
      }
      if (includeBuildings) {
        for (const b of this.buildings) {
          if (b.dead || b.faction === u.faction || !this.factions[b.faction].alive || b.build < 1) continue;
          const d = dist2(u.x, u.y, b.x, b.y);
          if (d < bd) { bd = d; best = b; }
        }
      }
      return best;
    }

    lowestHurtAlly(u, range) {
      let best = null, pct = 1;
      for (const v of this.units) {
        if (v.dead || v.garrisoned || v.faction !== u.faction || v.hp >= v.maxHp) continue;
        if (dist2(u.x, u.y, v.x, v.y) > range * range) continue;
        const p = v.hp / v.maxHp;
        if (p < pct) { pct = p; best = v; }
      }
      return best;
    }

    nearestDropoff(fid, x, y) {
      let best = null, bd = Infinity;
      for (const b of this.buildings) {
        if (b.faction !== fid || b.dead || b.build < 1) continue;
        if (b.type !== 'castle' && b.type !== 'house') continue;
        const d = dist2(x, y, b.x, b.y);
        if (d < bd) { bd = d; best = b; }
      }
      return best;
    }

    autoGather(u) {
      const f = this.factions[u.faction];
      const need = f.res.wood < 180 ? 'tree' : f.res.gold < 160 ? 'gold' : f.res.food < 4 ? 'food' : choose(['tree', 'gold', 'food']);
      const r = this.nearestResource(u.x, u.y, need, 900) || this.nearestResource(u.x, u.y, null, 1400);
      if (r) this.orderHarvest(u, r);
    }

    nearestResource(x, y, type, range) {
      let best = null, bd = range * range;
      for (const r of this.resources) {
        if (r.dead || r.amount <= 0) continue;
        if (type && r.type !== type) continue;
        const d = dist2(x, y, r.x, r.y);
        if (d < bd) { bd = d; best = r; }
      }
      return best;
    }

    setAutoWorkerOrders(fid) {
      for (const u of this.units) if (u.faction === fid && u.type === 'worker' && u.order === 'idle') this.autoGather(u);
    }

    updateAI(dt) {
      for (const f of this.factions) {
        if (!f.ai || !f.alive) continue;
        f.underAttack = Math.max(0, f.underAttack - dt);
        f.aiState.timer -= dt;
        if (f.aiState.timer <= 0) {
          f.aiState.timer = .9 + Math.random() * .8;
          this.aiThink(f);
        }
      }
    }

    aiThink(f) {
      this.setAutoWorkerOrders(f.id);
      this.aiBuild(f);
      this.aiTrain(f);
      this.aiTactics(f);
    }

    aiBuild(f) {
      const ownB = this.buildings.filter(b => b.faction === f.id && !b.dead);
      const count = (t) => ownB.filter(b => b.type === t).length;
      const pop = this.population(f.id);
      const candidates = [];
      if (pop.cap - pop.used < 5) candidates.push('house');
      if (count('barracks') < 1) candidates.push('barracks');
      if (count('archery') < 1 && count('barracks') >= 1) candidates.push('archery');
      if (count('tower') < 2 + f.aiState.expansion) candidates.push('tower');
      if (count('monastery') < 1 && pop.used > 10) candidates.push('monastery');
      if (pop.used > 20 && count('barracks') < 2) candidates.push('barracks');
      if (!candidates.length && Math.random() < .25) candidates.push(choose(['house','tower','archery']));
      for (const t of candidates) {
        const def = BUILDINGS[t];
        if (!canAfford(f, def.cost)) continue;
        const pos = this.findBuildSpot(f.base.x, f.base.y, t, f.aiState.expansion);
        if (pos && pay(f, def.cost)) { this.addBuilding(f.id, t, pos.x, pos.y, false); return; }
      }
    }

    findBuildSpot(cx, cy, type, ring = 0) {
      const base = 170 + ring * 60;
      for (let i = 0; i < 20; i++) {
        const a = Math.random() * Math.PI * 2;
        const r = base + Math.random() * 560;
        const x = clamp(cx + Math.cos(a) * r, 120, WORLD_W - 120);
        const y = clamp(cy + Math.sin(a) * r, 120, WORLD_H - 120);
        if (this.canPlace(type, x, y)) return { x, y };
      }
      return null;
    }

    aiTrain(f) {
      const pop = this.population(f.id);
      for (const b of this.buildings) {
        if (b.faction !== f.id || b.dead || b.build < 1 || b.queue.length >= 2) continue;
        const trains = BUILDINGS[b.type].trains;
        if (!trains.length) continue;
        let desired = null;
        const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead).length;
        const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead).length;
        if (b.type === 'castle' && workers < 9 + Math.floor(army / 8)) desired = 'worker';
        else if (b.type === 'barracks') desired = Math.random() < .32 ? 'lancer' : 'warrior';
        else if (b.type === 'archery') desired = 'archer';
        else if (b.type === 'monastery' && army > 8 && Math.random() < .65) desired = 'monk';
        if (!desired) continue;
        const def = UNITS[desired];
        if (pop.used + def.pop > pop.cap) continue;
        if (pay(f, def.cost)) b.queue.push({ type: desired, time: def.time });
      }
    }

    aiTactics(f) {
      const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead && !u.garrisoned);
      const idleArmy = army.filter(u => u.order === 'idle' || u.order === 'move' || u.order === 'attackMove');
      const threat = this.nearestThreatToBase(f.id, f.base.x, f.base.y, 780);
      if (threat && idleArmy.length) {
        for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 14))) this.orderAttack(u, threat, false);
        return;
      }
      f.aiState.attackTimer -= 1;
      if (f.aiState.attackTimer <= 0 && idleArmy.length >= 6) {
        f.aiState.attackTimer = 8 + Math.random() * 9;
        const target = this.pickStrategicTarget(f.id);
        if (target) {
          const squad = idleArmy.slice(0, Math.min(idleArmy.length, 8 + Math.floor(Math.random() * 8)));
          for (const u of squad) this.orderAttack(u, target, true);
        }
      }
      const towers = this.buildings.filter(b => b.faction === f.id && b.type === 'tower' && b.build >= 1 && b.garrison.length < BUILDINGS.tower.garrisonCap);
      for (const tw of towers) {
        const ar = idleArmy.find(u => u.type === 'archer' && dist2(u.x, u.y, tw.x, tw.y) < 600 * 600);
        if (ar) this.garrisonArchers([ar], tw, true);
      }
    }

    nearestThreatToBase(fid, x, y, range) {
      let best = null, bd = range * range;
      for (const u of this.units) {
        if (u.dead || u.faction === fid || u.garrisoned) continue;
        const d = dist2(x, y, u.x, u.y);
        if (d < bd) { bd = d; best = u; }
      }
      return best;
    }

    pickStrategicTarget(fid) {
      let best = null, score = Infinity;
      const own = this.factions[fid];
      for (const b of this.buildings) {
        if (b.dead || b.faction === fid || !this.factions[b.faction].alive || b.build < 1) continue;
        const baseD = Math.sqrt(dist2(own.base.x, own.base.y, b.x, b.y));
        const factionWeakness = this.population(b.faction).used;
        const s = baseD + factionWeakness * 18 + Math.random() * 380 - (b.type === 'castle' ? 160 : 0);
        if (s < score) { score = s; best = b; }
      }
      return best;
    }

    population(fid) {
      let used = 0, cap = 0;
      for (const u of this.units) if (u.faction === fid && !u.dead) used += UNITS[u.type].pop;
      for (const b of this.buildings) if (b.faction === fid && !b.dead && b.build >= 1) cap += BUILDINGS[b.type].pop || 0;
      return { used, cap: Math.max(4, cap) };
    }

    queueTrain(type) {
      const buildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1 && BUILDINGS[e.type].trains.includes(type));
      if (!buildings.length) return;
      const f = this.factions[0], def = UNITS[type], pop = this.population(0);
      if (pop.used + def.pop > pop.cap) { this.toast('Population cap reached. Build houses.', 1.6); this.sfx.deny(); return; }
      if (!pay(f, def.cost)) { this.toast('Not enough resources.', 1.3); this.sfx.deny(); return; }
      buildings.sort((a, b) => a.queue.length - b.queue.length)[0].queue.push({ type, time: def.time });
      this.uiDirty = true;
    }

    repairSelected() {
      const f = this.factions[0];
      const damaged = this.selected.find(e => e.entity === 'building' && e.faction === 0 && e.hp < e.maxHp && e.build >= 1);
      if (!damaged) return;
      const cost = { wood: 28, gold: 18, food: 0 };
      if (!pay(f, cost)) { this.toast('Need wood and gold to repair.', 1.2); this.sfx.deny(); return; }
      damaged.hp = Math.min(damaged.maxHp, damaged.hp + damaged.maxHp * .28);
      this.effects.push({ kind: 'dust', x: damaged.x, y: damaged.y, time: .5, max: .5 });
      this.sfx.build();
    }

    stopSelected() {
      for (const e of this.selected) {
        if (e.entity === 'unit' && e.faction === 0) { e.order = 'idle'; e.target = null; e.goal = null; e.attackMove = false; e.hold = false; }
      }
    }

    holdSelected() {
      for (const e of this.selected) if (e.entity === 'unit' && e.faction === 0) { e.hold = true; e.order = 'idle'; e.goal = null; e.target = null; }
      this.toast('Selected units holding position.', 1.2);
    }

    ungarrisonSelected() {
      const towers = this.selected.filter(e => e.entity === 'building' && e.type === 'tower' && e.faction === 0);
      for (const t of towers) {
        for (const id of t.garrison.splice(0)) {
          const u = this.units.find(x => x.id === id);
          if (u && !u.dead) { u.garrisoned = null; u.x = t.x + (Math.random() - .5) * 50; u.y = t.y + 58; u.order = 'idle'; }
        }
      }
      this.uiDirty = true;
    }

    cleanup() {
      this.projectiles = this.projectiles.filter(p => !p.dead);
      this.effects = this.effects.filter(e => e.time > 0);
      this.selected = this.selected.filter(isAlive);
      for (const b of this.buildings) b.garrison = b.garrison.filter(id => this.units.some(u => u.id === id && !u.dead && u.garrisoned === b.id));
    }

    updateEffects(dt) { for (const e of this.effects) e.time -= dt; }

    isWater(x, y) {
      if (x < 0 || y < 0 || x >= WORLD_W || y >= WORLD_H) return true;
      if (!this.landMap) return false;
      const tx = Math.floor(x / TILE), ty = Math.floor(y / TILE);
      if (tx < 0 || ty < 0 || tx >= this.landCols || ty >= this.landRows) return true;
      return this.landMap[ty * this.landCols + tx] !== 1;
    }

    renderUI() {
      const f = this.factions[0];
      const pop = this.population(0);
      HUD.resources.innerHTML = Object.keys(RESOURCES).map(k => {
        const r = RESOURCES[k];
        return `<div class="res-pill"><img src="${IMAGE_PATHS[r.icon]}" alt="${r.label}"><span>${Math.floor(f.res[k])}</span></div>`;
      }).join('') + `<div class="res-pill"><span>Pop</span><span>${pop.used}/${pop.cap}</span></div>`;
      const enemiesAlive = this.factions.filter(x => x.id !== 0 && x.alive).length;
      HUD.state.innerHTML = `${this.paused ? 'PAUSED' : 'LIVE RTS'}<br>${enemiesAlive} rival nation${enemiesAlive === 1 ? '' : 's'} standing<br>H help | B build | M minimap`;
      this.renderSelectionPanel();
      this.renderActions();
    }

    renderSelectionPanel() {
      const s = this.selected.filter(isAlive);
      if (!s.length) {
        HUD.selectionHeader.textContent = 'No selection';
        HUD.selectionBody.innerHTML = 'Drag-select units, click buildings, press <b>B</b> to build. Right-click orders are contextual.';
        return;
      }
      const first = s[0];
      if (s.length > 1) {
        const groups = {};
        for (const e of s) groups[e.type] = (groups[e.type] || 0) + 1;
        HUD.selectionHeader.textContent = `${s.length} selected`;
        HUD.selectionBody.innerHTML = Object.entries(groups).map(([t, n]) => `<div class="selection-row"><span>${n} x ${UNITS[t]?.label || BUILDINGS[t]?.label || t}</span></div>`).join('');
        return;
      }
      if (first.entity === 'resource') {
        HUD.selectionHeader.textContent = first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : 'Sheep';
        HUD.selectionBody.innerHTML = `<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`;
        return;
      }
      const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
      const owner = faction(first.faction);
      const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
      const extra = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Archers inside</span><b>${first.garrison.length}/${BUILDINGS.tower.garrisonCap}</b></div>` : '';
      const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div>` : '';
      const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
      HUD.selectionHeader.textContent = `${def.label} - ${owner.name}`;
      HUD.selectionBody.innerHTML = `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${extra}${queue}`;
    }

    renderActions() {
      HUD.actionBar.innerHTML = '';
      const own = this.selected.filter(e => e.faction === 0 && isAlive(e));
      if (!own.length) {
        HUD.actionTitle.textContent = 'Commands';
        HUD.actionBar.appendChild(this.makeAction('B', 'Build', 'Open build menu', 'iconBuild', () => this.toggleBuildMenu()));
        HUD.actionBar.appendChild(this.makeAction('H', 'Help', 'Controls and rules', 'iconRally', () => this.toggleHelp(true)));
        return;
      }
      const units = own.filter(e => e.entity === 'unit');
      const buildings = own.filter(e => e.entity === 'building');
      HUD.actionTitle.textContent = units.length ? 'Unit Commands' : 'Building Commands';
      if (units.length) {
        HUD.actionBar.appendChild(this.makeAction('B', 'Build', 'Workers construct', 'iconBuild', () => this.toggleBuildMenu(), !units.some(u => u.type === 'worker')));
        HUD.actionBar.appendChild(this.makeAction('A', 'Attack Move', 'Right click with enemies', 'iconAttack', () => { this.orderMoveFormation(units, this.pointer.wx, this.pointer.wy, true); }));
        HUD.actionBar.appendChild(this.makeAction('S', 'Stop', 'Cancel orders', 'iconStop', () => this.stopSelected()));
        HUD.actionBar.appendChild(this.makeAction('D', 'Hold', 'Defensive stance', 'iconRally', () => this.holdSelected()));
        const archers = units.filter(u => u.type === 'archer');
        const tower = this.nearestOwnTower(this.pointer.wx, this.pointer.wy, 260);
        HUD.actionBar.appendChild(this.makeAction('G', 'Garrison', 'Into nearest tower', 'iconGarrison', () => this.garrisonArchers(archers, tower), !archers.length || !tower));
      }
      if (buildings.length) {
        const trainSet = new Set();
        for (const b of buildings) if (b.build >= 1) BUILDINGS[b.type].trains.forEach(t => trainSet.add(t));
        let n = 1;
        for (const t of trainSet) {
          const d = UNITS[t];
          const disabled = !canAfford(this.factions[0], d.cost) || this.population(0).used + d.pop > this.population(0).cap;
          HUD.actionBar.appendChild(this.makeAction(String(n), `Train ${d.label}`, fmtCost(d.cost), d.icon, () => this.queueTrain(t), disabled));
          n++;
        }
        HUD.actionBar.appendChild(this.makeAction('F', 'Rally Flag', 'Right click map', 'iconRally', () => this.toast('Right click the map to set rally flags.', 1.4)));
        HUD.actionBar.appendChild(this.makeAction('R', 'Repair', '28W 18G', 'iconRepair', () => this.repairSelected()));
        if (buildings.some(b => b.type === 'tower' && b.garrison.length)) HUD.actionBar.appendChild(this.makeAction('U', 'Ungarrison', 'Release archers', 'iconGarrison', () => this.ungarrisonSelected()));
      }
    }

    makeAction(hotkey, title, sub, icon, fn, disabled = false) {
      const b = this.makeButton({ icon, title: `<span class="keytag">${hotkey}</span> ${title}`, sub, onClick: fn, disabled });
      b.dataset.hotkey = String(hotkey).toLowerCase();
      return b;
    }

    nearestOwnTower(x, y, range) {
      let best = null, bd = range * range;
      for (const b of this.buildings) {
        if (b.faction !== 0 || b.type !== 'tower' || b.dead || b.garrison.length >= BUILDINGS.tower.garrisonCap) continue;
        const d = dist2(x, y, b.x, b.y);
        if (d < bd) { bd = d; best = b; }
      }
      return best;
    }

    draw() {
      ctx.clearRect(0, 0, VIEW_W, VIEW_H);
      ctx.fillStyle = '#172c34'; ctx.fillRect(0, 0, VIEW_W, VIEW_H);
      ctx.save();
      ctx.scale(this.camera.zoom, this.camera.zoom);
      ctx.translate(-this.camera.x, -this.camera.y);
      this.drawTerrain();
      this.drawWorldEntities();
      this.drawPlacementGhost();
      ctx.restore();
      this.drawScreenOverlays();
      this.drawMinimap();
    }

    drawTerrain() {
      const sx = Math.floor(this.camera.x / TILE) - 2;
      const sy = Math.floor(this.camera.y / TILE) - 2;
      const ex = Math.ceil((this.camera.x + VIEW_W / this.camera.zoom) / TILE) + 2;
      const ey = Math.ceil((this.camera.y + VIEW_H / this.camera.zoom) / TILE) + 2;
      const cols = this.landCols || Math.ceil(WORLD_W / TILE);
      const rows = this.landRows || Math.ceil(WORLD_H / TILE);
      ctx.fillStyle = '#47aca8';
      ctx.fillRect(this.camera.x - 160, this.camera.y - 160, VIEW_W / this.camera.zoom + 320, VIEW_H / this.camera.zoom + 320);
      for (let ty = sy; ty <= ey; ty++) for (let tx = sx; tx <= ex; tx++) {
        const x = tx * TILE, y = ty * TILE;
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) continue;
        const land = this.landMap && this.landMap[ty * cols + tx] === 1;
        if (!land) this.drawWaterTile(tx, ty, x, y);
        else this.drawGrassGround(tx, ty, x, y);
      }
      this.drawShoreLines(sx, sy, ex, ey);
    }
    drawGrassGround(tx, ty, x, y) {
      const img = assets.tileGrass;
      if (img) {
        // Draw the CENTER tile (1, 1) and randomly flip to break up grid repetition
        ctx.save();
        ctx.translate(x + TILE/2, y + TILE/2);
        ctx.scale(rngHash(tx, ty, 1) > 0.5 ? 1 : -1, rngHash(tx, ty, 2) > 0.5 ? 1 : -1);
        ctx.drawImage(img, 64, 64, 64, 64, -TILE/2, -TILE/2, TILE + 1, TILE + 1);
        ctx.restore();
      } else {
        ctx.fillStyle = '#87bd62';
        ctx.fillRect(x, y, TILE + 1, TILE + 1);
      }
      
      const n = rngHash(tx, ty, 1200);
      if (!img) {
        if (n < .20) { ctx.fillStyle = 'rgba(231, 238, 130, .13)'; ctx.fillRect(x + 10, y + 12, 32, 4); }
        if (n > .70) { ctx.fillStyle = 'rgba(48, 103, 65, .11)'; ctx.fillRect(x + 16, y + 48, 30, 5); }
      }
    }

    drawWaterTile(tx, ty, x, y) {
      const img = assets.water;
      if (img) {
        ctx.drawImage(img, 0, 0, 64, 64, x, y, TILE + 1, TILE + 1);
      } else {
        ctx.fillStyle = '#47aaa6';
        ctx.fillRect(x, y, TILE + 1, TILE + 1);
      }
    }
    
    drawShoreLines(sx, sy, ex, ey) {
      if (!this.landMap) return;
      const cols = this.landCols, rows = this.landRows;
      const landAt = (tx, ty) => tx >= 0 && ty >= 0 && tx < cols && ty < rows && this.landMap[ty * cols + tx] === 1;
      const img = assets.tileGrass;
      
      for (let ty = sy; ty <= ey; ty++) for (let tx = sx; tx <= ex; tx++) {
        // Draw shorelines ON THE LAND TILES!
        if (!landAt(tx, ty)) continue;
        const x = tx * TILE, y = ty * TILE;
        
        // n means there is water above (not land)
        const n = !landAt(tx, ty - 1), so = !landAt(tx, ty + 1), w = !landAt(tx - 1, ty), e = !landAt(tx + 1, ty);
        
        if (!(n || so || w || e)) continue;
        
        if (img) {
          // Autotile logic based on standard 3x3 layout
          let srcX = -1, srcY = -1;
          
          if (n && w) { srcX = 0; srcY = 0; }
          else if (n && e) { srcX = 128; srcY = 0; }
          else if (so && w) { srcX = 0; srcY = 128; }
          else if (so && e) { srcX = 128; srcY = 128; }
          else if (n) { srcX = 64; srcY = 0; }
          else if (so) { srcX = 64; srcY = 128; }
          else if (w) { srcX = 0; srcY = 64; }
          else if (e) { srcX = 128; srcY = 64; }
          
          if (srcX !== -1) {
            // 1. Draw solid water base to clear the inner grass
            if (assets.water) {
              ctx.drawImage(assets.water, 0, 0, 64, 64, x, y, TILE + 1, TILE + 1);
            } else {
              ctx.fillStyle = '#47aaa6';
              ctx.fillRect(x, y, TILE + 1, TILE + 1);
            }
            
            // 2. Draw the correctly sliced foam tile for this specific edge!
            if (assets.waterFoam) {
              const foam = assets.waterFoam;
              const frames = 16;
              const frame = Math.floor(this.time * 6) % frames;
              const fX = (frame * 192) + srcX;
              const fY = srcY;
              ctx.drawImage(foam, fX, fY, 64, 64, x, y, TILE + 1, TILE + 1);
            }
            
            // 3. Draw edge overlay ON TOP. The transparent parts will show the foam!
            ctx.drawImage(img, srcX, srcY, 64, 64, x, y, TILE + 1, TILE + 1);
          }
        } else {
          // Fallback legacy solid drawing
          ctx.fillStyle = 'rgba(52, 112, 80, .58)';
          if (n) ctx.fillRect(x, y, TILE + 1, 8);
          if (so) ctx.fillRect(x, y + TILE - 8, TILE + 1, 8);
          if (w) ctx.fillRect(x, y, 8, TILE + 1);
          if (e) ctx.fillRect(x + TILE - 8, y, 8, TILE + 1);
        }
      }
    }
    drawWorldEntities() {
      const drawables = [];
      const inView = (x, y, pad = 180) => x > this.camera.x - pad && y > this.camera.y - pad && x < this.camera.x + VIEW_W / this.camera.zoom + pad && y < this.camera.y + VIEW_H / this.camera.zoom + pad;
      for (const d of this.decor) if (inView(d.x, d.y, 90)) drawables.push({ y: d.y + (d.front ? 6 : -18), kind: 'decor', item: d });
      for (const r of this.resources) if (!r.dead && inView(r.x, r.y, 110)) drawables.push({ y: r.y + (r.type === 'tree' ? -10 : 0), kind: 'resource', item: r });
      for (const b of this.buildings) if (!b.dead && inView(b.x, b.y, 250)) drawables.push({ y: b.y + b.h * .34, kind: 'building', item: b });
      for (const u of this.units) if (!u.dead && !u.garrisoned && inView(u.x, u.y, 120)) drawables.push({ y: u.y, kind: 'unit', item: u });
      drawables.sort((a, b) => a.y - b.y);
      for (const d of drawables) {
        if (d.kind === 'decor') this.drawDecor(d.item);
        else if (d.kind === 'resource') this.drawResource(d.item);
        else if (d.kind === 'building') this.drawBuilding(d.item);
        else this.drawUnit(d.item);
      }
      for (const p of this.projectiles) this.drawProjectile(p);
      for (const e of this.effects) this.drawEffect(e);
    }
    drawShadow(x, y, w, h) {
      ctx.fillStyle = 'rgba(0,0,0,.22)';
      ctx.beginPath(); ctx.ellipse(x, y, w, h, 0, 0, Math.PI * 2); ctx.fill();
    }

    drawDecor(d) {
      const img = assets[d.kind];
      if (!img) return;
      const w = img.width * d.scale, h = img.height * d.scale;
      if (!d.water) this.drawShadow(d.x, d.y + 4, Math.min(22, w * .15), 5);
      ctx.globalAlpha = d.water && d.kind.startsWith('cloud') ? .86 : 1;
      ctx.drawImage(img, d.x - w / 2, d.y - h + 8, w, h);
      ctx.globalAlpha = 1;
    }
    drawResource(r) {
      const img = assets[r.sprite];
      const scale = r.depleted ? .45 : r.type === 'tree' ? .45 : r.type === 'gold' ? .55 : .48;
      const moving = r.type === 'food' && Math.hypot(r.vx || 0, r.vy || 0) > 6;
      const sprite = moving ? assets.sheepMove : img;
      const bob = r.type === 'food' ? Math.sin(this.time * 2 + r.bob) * 2 : 0;
      this.drawShadow(r.x, r.y + 4, r.type === 'tree' ? 14 : r.r * .75, 7);
      if (sprite) {
        const frameW = r.type === 'food' ? 128 : sprite.width;
        const frameH = r.type === 'food' ? 128 : sprite.height;
        const frames = r.type === 'food' ? Math.max(1, Math.floor(sprite.width / frameW)) : 1;
        const fr = r.type === 'food' ? Math.floor(this.time * (moving ? 6 : 3) + r.bob) % frames : 0;
        const w = frameW * scale, h = frameH * scale;
        ctx.drawImage(sprite, fr * frameW, 0, frameW, frameH, r.x - w / 2, r.y - h + 14 + bob, w, h);
      } else { ctx.fillStyle = r.type === 'gold' ? '#e6ca59' : '#6fa75a'; ctx.fillRect(r.x - r.r, r.y - r.r, r.r * 2, r.r * 2); }
      if (this.selected.includes(r)) this.drawSelectionCircle(r.x, r.y, r.r + 8, '#f5d37d');
    }
    drawBuilding(b) {
      const def = BUILDINGS[b.type];
      const img = assets[`b_${faction(b.faction).key}_${b.type}`];
      this.drawShadow(b.x, b.y + b.h * .30, b.w * .48, b.h * .18);
      if (img) {
        const w = img.width * def.scale, h = img.height * def.scale;
        ctx.globalAlpha = b.build < 1 ? .58 + .36 * b.build : 1;
        ctx.drawImage(img, b.x - w / 2, b.y - h + b.h * .46, w, h);
        ctx.globalAlpha = 1;
      } else { ctx.fillStyle = faction(b.faction).color; ctx.fillRect(b.x - b.w / 2, b.y - b.h / 2, b.w, b.h); }
      if (b.flash > 0) { ctx.fillStyle = `rgba(255,255,255,${b.flash * .25})`; ctx.fillRect(b.x - b.w / 2, b.y - b.h, b.w, b.h); }
      if (b.build < 1) this.drawProgress(b.x, b.y - b.h * .65, b.build, '#e8c965');
      this.drawHpBar(b.x, b.y - b.h * .88, b.hp / b.maxHp, b.faction);
      if (b.selected || this.selected.includes(b)) this.drawSelectionRect(b.x, b.y, b.w, b.h, faction(b.faction).color);
      if (b.rally && b.faction === 0 && this.selected.includes(b)) this.drawRallyFlag(b.rally.x, b.rally.y, faction(b.faction).color);
      if (b.type === 'tower' && b.garrison.length) {
        ctx.fillStyle = '#fff4b8'; ctx.font = '14px monospace'; ctx.fillText(`x${b.garrison.length}`, b.x + 20, b.y - b.h * .75);
      }
    }

    drawUnit(u) {
      const f = faction(u.faction);
      const def = UNITS[u.type];
      let anim = 'idle';
      if (u.order === 'move' || u.order === 'attackMove' || u.carry) anim = 'run';
      if (u.order === 'attack' && u.target && dist(u, u.target) <= def.range + 8) anim = 'attack';
      if (u.type === 'worker' && u.order === 'harvest' && !u.carry && u.gather > 0) anim = u.target && u.target.type === 'gold' ? 'mine' : 'chop';
      let key = `u_${f.key}_${u.type}_${anim}`;
      if (u.type === 'worker') {
        if (u.carry) key = `u_${f.key}_worker_carry${u.carry.type[0].toUpperCase()}${u.carry.type.slice(1)}`;
        else if (anim === 'mine') key = `u_${f.key}_worker_mine`;
        else if (anim === 'chop') key = `u_${f.key}_worker_chop`;
        else key = `u_${f.key}_worker_${anim === 'run' ? 'run' : 'idle'}`;
      }
      const img = assets[key] || assets[`u_${f.key}_${u.type}_idle`];
      this.drawShadow(u.x, u.y + 6, u.r * 1.1, 8);
      if (u.selected) this.drawSelectionCircle(u.x, u.y, u.r + 8, '#f5d37d');
      if (img) {
        const fw = def.fw, fh = def.fh;
        const frames = Math.max(1, Math.floor(img.width / fw));
        const frame = Math.floor(u.anim) % frames;
        const w = fw * def.scale, h = fh * def.scale;
        ctx.save();
        ctx.translate(u.x, u.y + 10);
        ctx.scale(u.face, 1);
        ctx.globalAlpha = u.flash > 0 ? .75 : 1;
        ctx.drawImage(img, frame * fw, 0, fw, fh, -w / 2, -h + 16, w, h);
        ctx.globalAlpha = 1;
        ctx.restore();
      } else { ctx.fillStyle = f.color; ctx.beginPath(); ctx.arc(u.x, u.y, u.r, 0, Math.PI * 2); ctx.fill(); }
      if (u.hp < u.maxHp || u.faction !== 0) this.drawHpBar(u.x, u.y - 58, u.hp / u.maxHp, u.faction, 34);
    }

    drawProjectile(p) {
      const f = faction(p.faction);
      const img = assets[`${f.key}Arrow`] || assets.blueArrow;
      const target = p.target || p;
      const a = Math.atan2((target.y - 20) - p.y, target.x - p.x);
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(a);
      if (img) ctx.drawImage(img, -10, -5, 28, 10);
      else { ctx.strokeStyle = '#f4e7a8'; ctx.beginPath(); ctx.moveTo(-8,0); ctx.lineTo(10,0); ctx.stroke(); }
      ctx.restore();
    }

    drawEffect(e) {
      const t = clamp(e.time / e.max, 0, 1);
      if (e.kind === 'move' || e.kind === 'attack' || e.kind === 'flag') {
        ctx.strokeStyle = e.kind === 'attack' ? `rgba(255,95,80,${t})` : `rgba(246,218,116,${t})`;
        ctx.lineWidth = 3;
        ctx.beginPath(); ctx.arc(e.x, e.y, 12 + (1 - t) * 24, 0, Math.PI * 2); ctx.stroke();
        if (e.kind === 'flag') this.drawRallyFlag(e.x, e.y, '#f5d37d');
        return;
      }
      const img = e.kind === 'boom' ? assets.explosion : e.kind === 'heal' ? assets.healFx : assets.dust;
      if (img) {
        const fw = e.kind === 'heal' ? 192 : 192;
        const fh = e.kind === 'heal' ? 192 : 192;
        const frames = Math.max(1, Math.floor(img.width / fw));
        const frame = Math.floor((1 - t) * frames) % frames;
        const s = e.kind === 'boom' ? .8 : .42;
        ctx.globalAlpha = t;
        ctx.drawImage(img, frame * fw, 0, fw, fh, e.x - fw * s / 2, e.y - fh * s / 2, fw * s, fh * s);
        ctx.globalAlpha = 1;
      }
    }

    drawSelectionCircle(x, y, r, color) {
      ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.setLineDash([8, 5]); ctx.beginPath(); ctx.ellipse(x, y + 4, r, r * .48, 0, 0, Math.PI * 2); ctx.stroke(); ctx.setLineDash([]);
    }

    drawSelectionRect(x, y, w, h, color) {
      ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.setLineDash([7, 5]); ctx.strokeRect(x - w / 2 - 6, y - h / 2 - 10, w + 12, h + 16); ctx.setLineDash([]);
    }

    drawHpBar(x, y, pct, fid, width = 58) {
      pct = clamp(pct, 0, 1);
      ctx.fillStyle = 'rgba(31,15,20,.76)'; ctx.fillRect(x - width / 2, y, width, 6);
      ctx.fillStyle = faction(fid).color; ctx.fillRect(x - width / 2 + 1, y + 1, (width - 2) * pct, 4);
      ctx.strokeStyle = 'rgba(0,0,0,.55)'; ctx.strokeRect(x - width / 2, y, width, 6);
    }

    drawProgress(x, y, pct, color) {
      ctx.fillStyle = 'rgba(0,0,0,.65)'; ctx.fillRect(x - 32, y, 64, 6);
      ctx.fillStyle = color; ctx.fillRect(x - 31, y + 1, 62 * clamp(pct, 0, 1), 4);
    }

    drawRallyFlag(x, y, color) {
      ctx.strokeStyle = '#0b111c'; ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - 38); ctx.stroke();
      ctx.strokeStyle = color; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - 38); ctx.stroke();
      ctx.fillStyle = color; ctx.beginPath(); ctx.moveTo(x + 2, y - 38); ctx.lineTo(x + 34, y - 30); ctx.lineTo(x + 2, y - 22); ctx.closePath(); ctx.fill();
    }

    drawPlacementGhost() {
      if (!this.placing) return;
      const type = this.placing, def = BUILDINGS[type], ok = this.canPlace(type, this.pointer.wx, this.pointer.wy) && canAfford(this.factions[0], def.cost);
      ctx.globalAlpha = .62;
      ctx.fillStyle = ok ? 'rgba(94,211,105,.28)' : 'rgba(225,60,60,.32)';
      ctx.fillRect(this.pointer.wx - def.w / 2, this.pointer.wy - def.h / 2, def.w, def.h);
      const img = assets[`b_blue_${type}`];
      if (img) {
        const w = img.width * def.scale, h = img.height * def.scale;
        ctx.drawImage(img, this.pointer.wx - w / 2, this.pointer.wy - h + def.h * .46, w, h);
      }
      ctx.globalAlpha = 1;
    }

    drawScreenOverlays() {
      if (this.pointer.down && this.pointer.dragging) {
        const x = Math.min(this.pointer.startX, this.pointer.x), y = Math.min(this.pointer.startY, this.pointer.y);
        const w = Math.abs(this.pointer.x - this.pointer.startX), h = Math.abs(this.pointer.y - this.pointer.startY);
        ctx.fillStyle = 'rgba(104, 183, 217, .14)'; ctx.fillRect(x, y, w, h);
        ctx.strokeStyle = 'rgba(244, 218, 128, .85)'; ctx.lineWidth = 2; ctx.strokeRect(x, y, w, h);
      }
      if (this.paused) {
        ctx.fillStyle = 'rgba(0,0,0,.28)'; ctx.fillRect(0, 0, VIEW_W, VIEW_H);
        ctx.fillStyle = '#fff2b8'; ctx.font = 'bold 42px monospace'; ctx.textAlign = 'center'; ctx.fillText('PAUSED', VIEW_W / 2, VIEW_H / 2); ctx.textAlign = 'left';
      }
    }

    drawMinimap() {
      this.resizeMini();
      const w = mini.width, h = mini.height;
      if (!w || !h) return;
      mctx.fillStyle = '#182b36'; mctx.fillRect(0, 0, w, h);
      for (let i = 0; i < 800; i += 6) {
        const x = (i * 92821 % WORLD_W) / WORLD_W * w;
        const y = (i * 37117 % WORLD_H) / WORLD_H * h;
        mctx.fillStyle = this.isWater(x / w * WORLD_W, y / h * WORLD_H) ? '#315e6e' : '#5b8b4d';
        mctx.fillRect(x, y, 5, 5);
      }
      for (const r of this.resources) if (!r.dead) { mctx.fillStyle = r.type === 'gold' ? '#e8ca4d' : r.type === 'tree' ? '#4d8b48' : '#e8a765'; mctx.fillRect(r.x / WORLD_W * w, r.y / WORLD_H * h, 1.6, 1.6); }
      for (const b of this.buildings) if (!b.dead) { mctx.fillStyle = faction(b.faction).color; mctx.fillRect(b.x / WORLD_W * w - 2, b.y / WORLD_H * h - 2, b.type === 'castle' ? 6 : 4, b.type === 'castle' ? 6 : 4); }
      for (const u of this.units) if (!u.dead && !u.garrisoned) { mctx.fillStyle = faction(u.faction).color; mctx.fillRect(u.x / WORLD_W * w, u.y / WORLD_H * h, 2, 2); }
      mctx.strokeStyle = '#fff3bd'; mctx.lineWidth = 1.5;
      mctx.strokeRect(this.camera.x / WORLD_W * w, this.camera.y / WORLD_H * h, (VIEW_W / this.camera.zoom) / WORLD_W * w, (VIEW_H / this.camera.zoom) / WORLD_H * h);
    }
  }

  loadImages(IMAGE_PATHS).then(() => {
    document.body.classList.add('ready');
    HUD.root.classList.remove('hidden');
    canvas.width = VIEW_W; canvas.height = VIEW_H;
    ctx.imageSmoothingEnabled = false;
    const game = new Game();
    window.tinySwordsGame = game;
    requestAnimationFrame(t => game.run(t));
  });
})();
