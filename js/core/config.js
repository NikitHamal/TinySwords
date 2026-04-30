// Tiny Swords RTS configuration, assets, and shared helpers.
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
const WORLD_W = 8200;
const WORLD_H = 6000;
const TILE = 64;
const SPRITE_BOOST = 1.08;
const CLOUD_BOOST = 3.0;
const BASE = 'assets/Tiny Swords (Free Pack)/';
const MAX_DT = 1 / 24;

const FACTIONS = [
  { id: 0, key: 'blue', name: 'Blue Realm', folder: 'Blue', ai: false, color: '#61b7d9', dark: '#1f5670', base: { x: 1420, y: 1280 } },
  { id: 1, key: 'red', name: 'Red Dominion', folder: 'Red', ai: true, color: '#db6060', dark: '#78232b', base: { x: 6780, y: 1280 } },
  { id: 2, key: 'yellow', name: 'Golden Clan', folder: 'Yellow', ai: true, color: '#e6ca59', dark: '#80651e', base: { x: 1420, y: 4820 } },
  { id: 3, key: 'purple', name: 'Violet Order', folder: 'Purple', ai: true, color: '#b071df', dark: '#4a246e', base: { x: 6780, y: 4820 } },
  { id: 4, key: 'black', name: 'Iron Pact', folder: 'Black', ai: true, color: '#aeb3bd', dark: '#30353d', base: { x: 4100, y: 3000 } }
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
  tileMoss: BASE + 'Terrain/Tileset/Tilemap_color3.png',
  tileDeep: BASE + 'Terrain/Tileset/Tilemap_color4.png',
  tileWarm: BASE + 'Terrain/Tileset/Tilemap_color5.png',
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
  gold6: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 6.png',
  sheepIdle: BASE + 'Terrain/Resources/Meat/Sheep/Sheep_Idle.png',
  sheepMove: BASE + 'Terrain/Resources/Meat/Sheep/Sheep_Move.png',
  sheepGrass: BASE + 'Terrain/Resources/Meat/Sheep/Sheep_Grass.png',
  meat: BASE + 'Terrain/Resources/Meat/Meat Resource/Meat Resource.png',
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
  cloud4: BASE + 'Terrain/Decorations/Clouds/Clouds_04.png',
  cloud5: BASE + 'Terrain/Decorations/Clouds/Clouds_05.png',
  cloud6: BASE + 'Terrain/Decorations/Clouds/Clouds_06.png',
  cloud7: BASE + 'Terrain/Decorations/Clouds/Clouds_07.png',
  cloud8: BASE + 'Terrain/Decorations/Clouds/Clouds_08.png',
  rubberDuck: BASE + 'Terrain/Decorations/Rubber Duck/Rubber duck.png',
  dust: BASE + 'Particle FX/Dust_01.png',
  explosion: BASE + 'Particle FX/Explosion_01.png',
  fire: BASE + 'Particle FX/Fire_03.png',
  waterSplash: BASE + 'Particle FX/Water Splash.png',
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
  IMAGE_PATHS[`b_${f.key}_house2`] = `${BASE}Buildings/${bf}/House2.png`;
  IMAGE_PATHS[`b_${f.key}_house3`] = `${BASE}Buildings/${bf}/House3.png`;
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

