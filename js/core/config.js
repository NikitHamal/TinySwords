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
  economy: document.getElementById('economyBody'),
  selectionHeader: document.getElementById('selectionHeader'),
  selectionBody: document.getElementById('selectionBody'),
  actionTitle: document.getElementById('actionTitle'),
  actionBar: document.getElementById('actionBar'),
  buildMenu: document.getElementById('buildMenu'),
  buildButtons: document.getElementById('buildButtons'),
  message: document.getElementById('messageToast'),
  help: document.getElementById('helpOverlay'),
  miniWrap: document.getElementById('miniWrap'),
  workerRoles: document.getElementById('workerRolesPanel'),
  workerRoleTitle: document.getElementById('workerRoleTitle'),
  workerRoleBody: document.getElementById('workerRoleBody'),
  workerRoleClose: document.getElementById('workerRoleClose'),
  loading: document.getElementById('loading'),
  miniToggle: document.getElementById('miniToggle'),
  helpClose: document.getElementById('helpClose'),
  pauseOverlay: document.getElementById('pauseOverlay'),
  pauseResume: document.getElementById('pauseResume'),
  pauseSettings: document.getElementById('pauseSettings'),
  pauseExit: document.getElementById('pauseExit'),
  pauseSettingsBody: document.getElementById('pauseSettingsBody'),
  pauseVolume: document.getElementById('pauseVolume'),
  pauseAutosave: document.getElementById('pauseAutosave')
};

const VIEW_W = 1280;
const VIEW_H = 720;
let WORLD_W = 16000;
let WORLD_H = 11200;

const WORLD_PRESETS = {
  standard: { label: 'Standard Realm', width: 12400, height: 9000, areaScale: 1.0 },
  large: { label: 'Large Realm', width: 16000, height: 11200, areaScale: 1.45 },
  massive: { label: 'Massive Realm', width: 20480, height: 14400, areaScale: 2.65 }
};

const MAP_PRESETS = Object.freeze({
  crossroads: {
    label: 'Crossroads Kingdom',
    desc: 'Balanced mainland lanes with side islands, safe openings, and contested center fields.',
    bases: [[0.135, 0.155], [0.865, 0.155], [0.135, 0.845], [0.865, 0.845], [0.50, 0.50]]
  },
  archipelago: {
    label: 'Crown Archipelago',
    desc: 'Large island starts linked by bridges, rich shoreline pockets, and risky center crossings.',
    bases: [[0.14, 0.20], [0.86, 0.20], [0.14, 0.80], [0.86, 0.80], [0.50, 0.50]]
  },
  twinrivers: {
    label: 'Twin Rivers',
    desc: 'Two broad rivers divide expansion routes; bridges become natural siege objectives.',
    bases: [[0.14, 0.18], [0.86, 0.18], [0.14, 0.82], [0.86, 0.82], [0.50, 0.50]]
  },
  fourcorners: {
    label: 'Four Corner War',
    desc: 'Fast corner starts, open side lanes, and a dangerous center gold basin.',
    bases: [[0.12, 0.12], [0.88, 0.12], [0.12, 0.88], [0.88, 0.88], [0.50, 0.50]]
  },
  kingroad: {
    label: 'King Road',
    desc: 'A long central highway rewards scouting, harassment, and forward towers.',
    bases: [[0.16, 0.50], [0.84, 0.50], [0.50, 0.16], [0.50, 0.84], [0.50, 0.50]]
  },
  spiral: {
    label: 'Spiral Isles',
    desc: 'Curving lanes wrap around the center, creating ambush turns and layered defenses.',
    bases: [[0.18, 0.24], [0.82, 0.24], [0.18, 0.76], [0.82, 0.76], [0.50, 0.50]]
  },
  goldrush: {
    label: 'Gold Rush Basin',
    desc: 'Safe wood at home, exposed gold fields, and a wealthy middle that forces conflict.',
    bases: [[0.16, 0.18], [0.84, 0.18], [0.16, 0.82], [0.84, 0.82], [0.50, 0.50]]
  },
  highlands: {
    label: 'Highland Lakes',
    desc: 'Patchwork grass plateaus around lakes with many short attack angles and flank paths.',
    bases: [[0.18, 0.18], [0.82, 0.18], [0.18, 0.82], [0.82, 0.82], [0.50, 0.50]]
  }
});

const FORMATION_MODES = Object.freeze({
  line: { label: 'Line', spacing: 44 },
  box: { label: 'Box', spacing: 42 },
  wedge: { label: 'Wedge', spacing: 42 },
  split: { label: 'Split', spacing: 44 }
});

const DEFAULT_WORLD_SETTINGS = Object.freeze({
  size: 'standard',
  mapStyle: 'crossroads',
  difficulty: 'normal',
  resourceDensity: 'normal',
  rivals: 4,
  seed: '',
  autosave: true,
  graphics: 'balanced'
});

const DIFFICULTY_PRESETS = {
  peaceful: { label: 'Peaceful', aiResourceMult: .72, aiAttackDelay: 9999, aiSquadMin: 99, aggression: .20 },
  easy: { label: 'Easy', aiResourceMult: .84, aiAttackDelay: 18, aiSquadMin: 9, aggression: .55 },
  normal: { label: 'Normal', aiResourceMult: 1.0, aiAttackDelay: 10, aiSquadMin: 7, aggression: 1.0 },
  hard: { label: 'Hard', aiResourceMult: 1.22, aiAttackDelay: 7, aiSquadMin: 6, aggression: 1.28 }
};

const RESOURCE_DENSITY_PRESETS = { sparse: .72, normal: 1.0, rich: 1.25, abundant: 1.55 };

function normalizedWorldSettings(settings = {}) {
  const out = { ...DEFAULT_WORLD_SETTINGS, ...(settings || {}) };
  if (!WORLD_PRESETS[out.size]) out.size = DEFAULT_WORLD_SETTINGS.size;
  if (!MAP_PRESETS[out.mapStyle]) out.mapStyle = DEFAULT_WORLD_SETTINGS.mapStyle;
  if (!DIFFICULTY_PRESETS[out.difficulty]) out.difficulty = DEFAULT_WORLD_SETTINGS.difficulty;
  if (!RESOURCE_DENSITY_PRESETS[out.resourceDensity]) out.resourceDensity = DEFAULT_WORLD_SETTINGS.resourceDensity;
  const rivalsNumber = Number(out.rivals);
  out.rivals = clamp(Number.isFinite(rivalsNumber) ? rivalsNumber : DEFAULT_WORLD_SETTINGS.rivals, 0, 4);
  out.seed = String(out.seed || '').trim();
  out.autosave = out.autosave !== false;
  out.graphics = ['performance', 'balanced', 'high'].includes(out.graphics) ? out.graphics : 'balanced';
  return out;
}

function applyWorldSettings(settings = {}) {
  const normalized = normalizedWorldSettings(settings);
  const preset = WORLD_PRESETS[normalized.size];
  WORLD_W = preset.width;
  WORLD_H = preset.height;
  const mapPreset = MAP_PRESETS[normalized.mapStyle] || MAP_PRESETS.crossroads;
  const bases = mapPreset.bases || MAP_PRESETS.crossroads.bases;
  for (let i = 0; i < FACTIONS.length; i++) {
    FACTIONS[i].base = { x: Math.round(WORLD_W * bases[i][0]), y: Math.round(WORLD_H * bases[i][1]) };
    FACTIONS[i].ai = i !== 0 && i <= normalized.rivals;
  }
  return normalized;
}

function hashStringSeed(text) {
  let h = 2166136261;
  const str = String(text || 'tinyswords');
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function makeSeededRandom(seedText) {
  let s = hashStringSeed(seedText || `${Date.now()}-${Math.random()}`) || 1;
  return function seededRandom() {
    s |= 0;
    s = (s + 0x6D2B79F5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function withSeededRandom(seedText, fn) {
  const previous = Math.random;
  Math.random = makeSeededRandom(seedText);
  try { return fn(); } finally { Math.random = previous; }
}
const TILE = 64;
const SPRITE_BOOST = 1.08;
const CLOUD_BOOST = 3.0;
const BASE = 'assets/Tiny Swords (Free Pack)/';
const CRAFTPIX_BASE = 'assets/CraftPix Hunt Animals/';

const DECOR_SPECS = {
  bush1: { fw: 128, fh: 128, baseline: 79, shadow: [18, 5], fps: 1.05 },
  bush2: { fw: 128, fh: 128, baseline: 79, shadow: [18, 5], fps: 1.05 },
  bush3: { fw: 128, fh: 128, baseline: 79, shadow: [18, 5], fps: 1.05 },
  bush4: { fw: 128, fh: 128, baseline: 79, shadow: [18, 5], fps: 1.05 },
  rock1: { fw: 64, fh: 64, baseline: 51, shadow: [14, 4], fps: 0 },
  rock2: { fw: 64, fh: 64, baseline: 51, shadow: [14, 4], fps: 0 },
  rock3: { fw: 64, fh: 64, baseline: 51, shadow: [14, 4], fps: 0 },
  rock4: { fw: 64, fh: 64, baseline: 51, shadow: [14, 4], fps: 0 },
  waterRock1: { fw: 64, fh: 64, baseline: 56, shadow: [0, 0], fps: 3.5 },
  waterRock2: { fw: 64, fh: 64, baseline: 56, shadow: [0, 0], fps: 3.5 },
  waterRock3: { fw: 64, fh: 64, baseline: 56, shadow: [0, 0], fps: 3.5 },
  waterRock4: { fw: 64, fh: 64, baseline: 56, shadow: [0, 0], fps: 3.5 },
  rubberDuck: { fw: 32, fh: 32, baseline: 29, shadow: [0, 0], fps: 2.2 }
};

const NATURAL_DECOR_KINDS = ['bush1','bush2','bush3','bush4','rock1','rock2','rock3','rock4'];
const PASSABLE_DECOR = new Set(NATURAL_DECOR_KINDS);
const LIGHT_DECOR = new Set();

const RESOURCE_SPECS = {
  tree: { fw: 192, fh: 256, baseline: 241, scale: 0.60 * SPRITE_BOOST, shadow: [0, 0], proceduralShadow: false, footprint: 34, interactionOffsetY: -42 },
  treeDepleted: { fw: 192, fh: 256, baseline: 241, scale: 0.46 * SPRITE_BOOST, shadow: [0, 0], proceduralShadow: false, footprint: 18, interactionOffsetY: -14 },
  gold: { fw: 128, fh: 128, baseline: 79, scale: 0.60 * SPRITE_BOOST, shadow: [0, 0], proceduralShadow: false, footprint: 24, interactionOffsetY: -16 },
  meat: { fw: 64, fh: 64, baseline: 52, scale: 0.68 * SPRITE_BOOST, shadow: [13, 4], proceduralShadow: true, footprint: 16, interactionOffsetY: -2 }
};

const HUNT_ANIMALS = {
  deer: {
    label: 'Deer', folder: 'Deer', prefix: 'Deer', weight: 1.05, hp: 42, yield: 24, radius: 13,
    scale: 1.10, baseline: 28, shadow: [14, 4], walkSpeed: [14, 25], runSpeed: [56, 84], fps: { idle: 2.3, walk: 6.4, run: 9.2, hurt: 5.5 },
    idle: 'animalDeerIdle', walk: 'animalDeerWalk', run: 'animalDeerRun', hurt: 'animalDeerHurt', death: 'animalDeerDeath', shadowKey: 'animalDeerShadow',
    files: { idle: 'Deer_Idle.png', walk: 'Deer_Walk.png', run: 'Deer_Run.png', hurt: 'Deer_Hurt.png', death: 'Deer_Death.png', shadow: 'Deer_Shadow.png' }
  },
  boar: {
    label: 'Boar', folder: 'Boar', prefix: 'Boar', weight: .82, hp: 54, yield: 28, radius: 14, retaliation: 4,
    scale: 1.04, baseline: 28, shadow: [14, 4], walkSpeed: [12, 22], runSpeed: [48, 70], fps: { idle: 2.2, walk: 6.2, run: 8.6, hurt: 5.4 },
    idle: 'animalBoarIdle', walk: 'animalBoarWalk', run: 'animalBoarRun', hurt: 'animalBoarHurt', death: 'animalBoarDeath', attack: 'animalBoarAttack', shadowKey: 'animalBoarShadow',
    files: { idle: 'Boar_Idle.png', walk: 'Boar_Walk.png', run: 'Boar_Run.png', hurt: 'Boar_Hurt.png', death: 'Boar_Death.png', attack: 'Boar_Attack.png', shadow: 'Boar_shadow.png' }
  },
  hare: {
    label: 'Hare', folder: 'Hare', prefix: 'Hare', weight: 1.38, hp: 18, yield: 12, radius: 10,
    scale: 0.68, baseline: 28, shadow: [9, 3], walkSpeed: [18, 30], runSpeed: [68, 96], fps: { idle: 2.8, walk: 7.2, run: 10.8, hurt: 6 },
    idle: 'animalHareIdle', walk: 'animalHareWalk', run: 'animalHareRun', hurt: 'animalHareHurt', death: 'animalHareDeath', shadowKey: 'animalHareShadow',
    files: { idle: 'Hare_Idle.png', walk: 'Hare_Walk.png', run: 'Hare_Run.png', hurt: 'Hare_Hurt.png', death: 'Hare_Death.png', shadow: 'Hare_Shadow.png' }
  },
  fox: {
    label: 'Fox', folder: 'Fox', prefix: 'Fox', weight: .72, hp: 26, yield: 16, radius: 12,
    scale: 0.86, baseline: 28, shadow: [11, 4], walkSpeed: [16, 27], runSpeed: [62, 90], fps: { idle: 2.5, walk: 6.8, run: 10.2, hurt: 6 },
    idle: 'animalFoxIdle', walk: 'animalFoxWalk', run: 'animalFoxRun', hurt: 'animalFoxHurt', death: 'animalFoxDeath', shadowKey: 'animalFoxShadow',
    files: { idle: 'Fox_Idle.png', walk: 'Fox_walk.png', run: 'Fox_Run.png', hurt: 'Fox_Hurt.png', death: 'Fox_Death.png', shadow: 'Fox_Shadow.png' }
  },
  grouse: {
    label: 'Black Grouse', folder: 'Black_grouse', prefix: 'Black_grouse', weight: .78, hp: 20, yield: 14, radius: 11,
    scale: 0.58, baseline: 28, shadow: [8, 3], walkSpeed: [14, 26], runSpeed: [58, 86], fps: { idle: 2.6, walk: 6.8, run: 9.5, hurt: 6 },
    idle: 'animalGrouseIdle', walk: 'animalGrouseWalk', run: 'animalGrouseFlight', hurt: 'animalGrouseHurt', death: 'animalGrouseDeath', shadowKey: 'animalGrouseShadow',
    files: { idle: 'Black_grouse_Idle.png', walk: 'Black_grouse_Walk.png', run: 'Black_grouse_Flight.png', hurt: 'Black_grouse_Hurt.png', death: 'Black_grouse_Death.png', shadow: 'Black_grouse_Shadow.png' }
  },
  sheep: {
    label: 'Sheep', folder: 'Sheep', prefix: 'Sheep', weight: 1.0, hp: 28, yield: 14, radius: 14,
    fw: 128, fh: 128,
    scale: 0.30 * SPRITE_BOOST, baseline: 86, shadow: [12, 4], walkSpeed: [10, 18], runSpeed: [30, 42], fps: { idle: 2.5, walk: 6, run: 8, hurt: 2.5 },
    idle: 'sheepIdle', walk: 'sheepMove', run: 'sheepMove', hurt: 'sheepIdle', death: 'sheepIdle', shadowKey: null, flipByFacing: true,
    files: null
  }
};

const ANIMAL_DIRECTION_ROWS = { down: 0, up: 1, left: 2, right: 3 };

const MAX_DT = 1 / 24;

const FACTIONS = [
  { id: 0, key: 'blue', name: 'Blue Realm', folder: 'Blue', ai: false, color: '#61b7d9', dark: '#1f5670', base: { x: 1700, y: 1500 } },
  { id: 1, key: 'red', name: 'Red Dominion', folder: 'Red', ai: true, color: '#db6060', dark: '#78232b', base: { x: 10700, y: 1500 } },
  { id: 2, key: 'yellow', name: 'Golden Clan', folder: 'Yellow', ai: true, color: '#e6ca59', dark: '#80651e', base: { x: 1700, y: 7500 } },
  { id: 3, key: 'purple', name: 'Violet Order', folder: 'Purple', ai: true, color: '#b071df', dark: '#4a246e', base: { x: 10700, y: 7500 } },
  { id: 4, key: 'black', name: 'Iron Pact', folder: 'Black', ai: true, color: '#aeb3bd', dark: '#30353d', base: { x: 6200, y: 4500 } }
];

const RESOURCES = {
  wood: { label: 'Wood', icon: 'resWood', tint: '#9ccb77' },
  gold: { label: 'Gold', icon: 'resGold', tint: '#f7dc62' },
  food: { label: 'Food', icon: 'resFood', tint: '#f6a167' }
};

const BUILDINGS = {
  castle: { label: 'Castle', file: 'Castle.png', scale: 0.53, w: 180, h: 132, hp: 1200, pop: 12, cost: { wood: 280, gold: 160, food: 0 }, time: 32, trains: ['worker', 'warrior'], key: 'C', icon: 'iconCastle' },
  house: { label: 'House', file: 'House1.png', scale: 0.56, w: 84, h: 74, hp: 260, pop: 8, cost: { wood: 70, gold: 15, food: 0 }, time: 12, trains: [], key: 'H', icon: 'iconHouse' },
  barracks: { label: 'Barracks', file: 'Barracks.png', scale: 0.50, w: 106, h: 90, hp: 520, pop: 0, cost: { wood: 145, gold: 85, food: 0 }, time: 22, trains: ['warrior', 'lancer'], key: 'R', icon: 'iconBarracks' },
  archery: { label: 'Archery', file: 'Archery.png', scale: 0.50, w: 106, h: 90, hp: 440, pop: 0, cost: { wood: 120, gold: 95, food: 0 }, time: 20, trains: ['archer'], key: 'A', icon: 'iconArchery' },
  tower: { label: 'Tower', file: 'Tower.png', scale: 0.54, w: 60, h: 96, hp: 62, pop: 0, cost: { wood: 110, gold: 115, food: 0 }, time: 20, trains: [], key: 'T', icon: 'iconTower', tower: true, range: 360, garrisonCap: 0, builtInArcher: true },
  monastery: { label: 'Monastery', file: 'Monastery.png', scale: 0.46, w: 102, h: 106, hp: 420, pop: 0, cost: { wood: 120, gold: 165, food: 0 }, time: 24, trains: ['monk'], key: 'M', icon: 'iconMonastery' }
};

// Collision footprint calibration: placement/pathing use the visible grass-contact base, not tall roof silhouettes.
Object.assign(BUILDINGS.castle, { placeW: 152, placeH: 58, placeYOffset: 38 });
Object.assign(BUILDINGS.house, { placeW: 66, placeH: 38, placeYOffset: 24 });
Object.assign(BUILDINGS.barracks, { placeW: 84, placeH: 46, placeYOffset: 28 });
Object.assign(BUILDINGS.archery, { placeW: 84, placeH: 46, placeYOffset: 28 });
Object.assign(BUILDINGS.tower, { placeW: 42, placeH: 38, placeYOffset: 30 });
Object.assign(BUILDINGS.monastery, { placeW: 70, placeH: 44, placeYOffset: 34 });

const UNITS = {
  worker: { label: 'Worker', role: 'worker', hp: 55, speed: 96, range: 22, damage: 5, cd: 0.65, cost: { wood: 0, gold: 35, food: 1 }, time: 8, pop: 1, fw: 192, fh: 192, scale: 0.34, radius: 12, icon: 'iconWorker', hotkey: '1' },
  warrior: { label: 'Warrior', role: 'melee', hp: 95, speed: 78, range: 28, damage: 15, cd: 0.78, cost: { wood: 0, gold: 65, food: 1 }, time: 10, pop: 1, fw: 192, fh: 192, scale: 0.35, radius: 13, icon: 'iconWarrior', hotkey: '2' },
  archer: { label: 'Archer', role: 'ranged', hp: 62, speed: 74, range: 290, damage: 12, cd: 1.18, cost: { wood: 40, gold: 70, food: 1 }, time: 12, pop: 1, fw: 192, fh: 192, scale: 0.34, radius: 12, icon: 'iconArcher', hotkey: '3' },
  lancer: { label: 'Lancer', role: 'melee', hp: 135, speed: 88, range: 44, damage: 24, cd: 1.05, cost: { wood: 55, gold: 95, food: 2 }, time: 16, pop: 2, fw: 320, fh: 320, scale: 0.34, radius: 17, icon: 'iconLancer', hotkey: '4' },
  monk: { label: 'Monk', role: 'healer', hp: 64, speed: 70, range: 215, damage: -16, cd: 1.1, cost: { wood: 25, gold: 110, food: 1 }, time: 14, pop: 1, fw: 192, fh: 192, scale: 0.34, radius: 12, icon: 'iconMonk', hotkey: '5' }
};

// Lancer sprite calibration: the 320px sheet paints the horse high in-frame, so draw offset keeps feet aligned.
UNITS.lancer.scale = 0.40;
UNITS.lancer.radius = 18;
UNITS.lancer.drawYOffset = 27;
UNITS.lancer.shadow = [24, 8];

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
  iconUpgrade: BASE + 'UI Elements/UI Elements/Icons/Icon_10.png',
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
  gold1_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 1_Highlight.png',
  gold2_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 2_Highlight.png',
  gold3_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 3_Highlight.png',
  gold4_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 4_Highlight.png',
  gold5_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 5_Highlight.png',
  gold6_hl: BASE + 'Terrain/Resources/Gold/Gold Stones/Gold Stone 6_Highlight.png',
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
  cursorSelect: BASE + 'UI Elements/UI Elements/Cursors/Cursor_04.png',
  cursorAction: BASE + 'UI Elements/UI Elements/Cursors/Cursor_02.png',
  uiBarBase: BASE + 'UI Elements/UI Elements/Bars/SmallBar_Base.png',
  uiBarFill: BASE + 'UI Elements/UI Elements/Bars/SmallBar_Fill.png'
};

Object.assign(IMAGE_PATHS, ICON_PATHS);

for (const def of Object.values(HUNT_ANIMALS)) {
  if (!def.files) continue;
  const base = `${CRAFTPIX_BASE}${def.folder}/`;
  IMAGE_PATHS[def.idle] = base + def.files.idle;
  IMAGE_PATHS[def.walk] = base + def.files.walk;
  IMAGE_PATHS[def.run] = base + def.files.run;
  IMAGE_PATHS[def.hurt] = base + def.files.hurt;
  IMAGE_PATHS[def.death] = base + def.files.death;
  if (def.attack && def.files.attack) IMAGE_PATHS[def.attack] = base + def.files.attack;
  IMAGE_PATHS[def.shadowKey] = base + def.files.shadow;
}

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
  IMAGE_PATHS[`u_${f.key}_worker_build`] = `${BASE}Units/${uf}/Pawn/Pawn_Interact Hammer.png`;
  IMAGE_PATHS[`u_${f.key}_worker_fight`] = `${BASE}Units/${uf}/Pawn/Pawn_Interact Knife.png`;
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

function getBuildingFootprintRect(typeOrBuilding, x, y, pad = 0) {
  const type = typeof typeOrBuilding === 'string' ? typeOrBuilding : typeOrBuilding.type;
  const def = BUILDINGS[type];
  const bx = x === undefined ? typeOrBuilding.x : x;
  const by = y === undefined ? typeOrBuilding.y : y;
  // The Tiny Swords building sprites have tall roofs, but only a compact ground footprint.
  // Collision and placement should follow the base on the grass, not the whole roof silhouette.
  const w = Math.max(34, (def.placeW || def.w * .82) + pad * 2);
  const h = Math.max(30, (def.placeH || def.h * .58) + pad * 2);
  const yOffset = def.placeYOffset === undefined ? def.h * .10 : def.placeYOffset;
  return { x: bx - w / 2, y: by - h / 2 + yOffset, w, h };
}

function getBuildingDisplayRect(typeOrBuilding, x, y, pad = 0) {
  const type = typeof typeOrBuilding === 'string' ? typeOrBuilding : typeOrBuilding.type;
  const def = BUILDINGS[type];
  const bx = x === undefined ? typeOrBuilding.x : x;
  const by = y === undefined ? typeOrBuilding.y : y;
  return { x: bx - def.w / 2 - pad, y: by - def.h / 2 - pad, w: def.w + pad * 2, h: def.h + pad * 2 };
}

function isAlive(e) {
  if (!e || e.dead) return false;
  if (e.entity === 'resource') return e.amount > 0 || e.depleted;
  if (e.entity === 'decor' || (!e.entity && e.kind)) return true;
  return e.hp > 0;
}
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

function formationOffset(index, count, spacing = 44) {
  if (count <= 1) return { x: 0, y: 0 };
  const cols = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / cols);
  return {
    x: ((index % cols) - (cols - 1) / 2) * spacing,
    y: (Math.floor(index / cols) - (rows - 1) / 2) * spacing
  };
}

function entityBaseY(e) {
  if (!e) return 0;
  if (e.entity === 'building') return e.y;
  return e.y;
}

function getHuntAnimal(kind) {
  return HUNT_ANIMALS[kind] || null;
}

function getAnimalLabel(r) {
  const spec = getHuntAnimal(r && r.animalKind);
  return spec ? spec.label : 'Wild Animal';
}

function getResourceVisualSpec(r) {
  if (!r) return RESOURCE_SPECS.meat;
  if (r.type === 'tree') return r.depleted ? RESOURCE_SPECS.treeDepleted : RESOURCE_SPECS.tree;
  if (r.type === 'gold') return RESOURCE_SPECS.gold;
  if (r.type === 'food' && r.animal) return getHuntAnimal(r.animalKind) || { fw: 32, fh: 32, baseline: 28, scale: 1.4, shadow: [16, 5] };
  return RESOURCE_SPECS.meat;
}

function getResourceFootprint(r) {
  const spec = getResourceVisualSpec(r);
  if (r && r.type === 'food' && r.animal) return Math.max(r.r || 0, (spec.radius || r.r || 12) + 2);
  return Math.max(r && r.r || 0, spec.footprint || (r && r.r) || 16);
}

function getResourceInteractionPoint(r) {
  const spec = getResourceVisualSpec(r);
  return { x: r.x, y: r.y + (spec.interactionOffsetY || 0) };
}

function getResourceBlockingRadius(r) {
  return Math.max(10, getResourceFootprint(r) + (r && r.type === 'gold' ? 10 : r && r.type === 'tree' ? 12 : 4));
}

function shouldDrawResourceGroundShadow(r) {
  const spec = getResourceVisualSpec(r);
  return spec.proceduralShadow !== false;
}

function getGraphicsDensityMultiplier(settings = {}) {
  const graphics = normalizedWorldSettings(settings).graphics;
  return graphics === 'performance' ? 0.72 : graphics === 'high' ? 1.12 : 1.0;
}


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

