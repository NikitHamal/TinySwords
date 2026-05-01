// Persistent world-slot and save/load support.
'use strict';

const SAVE_SCHEMA_VERSION = 2;
const SAVE_INDEX_KEY = 'tinyswords.world.index.v2';
const SAVE_SETTINGS_KEY = 'tinyswords.global.settings.v2';
const SAVE_PREFIX = 'tinyswords.world.v2.';

const TinySwordsStorage = {
  safeRead(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (err) {
      console.warn('Save read failed:', key, err);
      return fallback;
    }
  },
  safeWrite(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (err) {
      console.warn('Save write failed:', key, err);
      return false;
    }
  },
  worldKey(id) { return SAVE_PREFIX + id; },
  listWorlds() {
    const index = this.safeRead(SAVE_INDEX_KEY, []);
    return Array.isArray(index)
      ? index.filter(w => w && w.id).sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
      : [];
  },
  latestWorld() { return this.listWorlds()[0] || null; },
  loadWorld(id) { return this.safeRead(this.worldKey(id), null); },
  saveWorldRecord(record) {
    if (!record || !record.id) return false;
    const now = Date.now();
    record.updatedAt = now;
    record.schema = SAVE_SCHEMA_VERSION;
    const ok = this.safeWrite(this.worldKey(record.id), record);
    if (!ok) return false;
    const current = this.listWorlds().filter(w => w.id !== record.id);
    const meta = this.metaFromRecord(record);
    current.unshift(meta);
    return this.safeWrite(SAVE_INDEX_KEY, current.slice(0, 48));
  },
  metaFromRecord(record) {
    return {
      id: record.id,
      name: record.name || 'Unnamed World',
      createdAt: record.createdAt || Date.now(),
      updatedAt: record.updatedAt || Date.now(),
      seed: record.seed || '',
      settings: normalizedWorldSettings(record.settings || {}),
      playTime: record.playTime || 0,
      state: record.state ? 'Saved' : 'Created'
    };
  },
  createWorld(name, settings) {
    const now = Date.now();
    const normalized = normalizedWorldSettings(settings || {});
    if (!normalized.seed) normalized.seed = `${name || 'tinyswords'}-${now.toString(36)}`;
    const id = `world-${now.toString(36)}-${Math.floor(Math.random() * 1e8).toString(36)}`;
    const record = {
      id,
      schema: SAVE_SCHEMA_VERSION,
      name: (name || '').trim() || `Unnamed World ${this.listWorlds().length + 1}`,
      createdAt: now,
      updatedAt: now,
      seed: normalized.seed,
      settings: normalized,
      playTime: 0,
      state: null
    };
    this.saveWorldRecord(record);
    return record;
  },
  deleteWorld(id) {
    try { localStorage.removeItem(this.worldKey(id)); } catch (err) { console.warn(err); }
    const index = this.listWorlds().filter(w => w.id !== id);
    this.safeWrite(SAVE_INDEX_KEY, index);
  },
  duplicateWorld(id) {
    const record = this.loadWorld(id);
    if (!record) return null;
    const copy = JSON.parse(JSON.stringify(record));
    copy.id = `world-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e8).toString(36)}`;
    copy.name = `${record.name || 'World'} Copy`;
    copy.createdAt = Date.now();
    copy.updatedAt = Date.now();
    this.saveWorldRecord(copy);
    return copy;
  },
  globalSettings() {
    return { autosave: true, graphics: 'balanced', edgePan: true, volume: .8, ...this.safeRead(SAVE_SETTINGS_KEY, {}) };
  },
  saveGlobalSettings(settings) {
    return this.safeWrite(SAVE_SETTINGS_KEY, { ...this.globalSettings(), ...(settings || {}) });
  }
};

function refForEntity(e) {
  return e && e.entity && e.id ? { entity: e.entity, id: e.id } : null;
}

function stripRuntimeEntity(e) {
  const out = {};
  for (const [key, value] of Object.entries(e)) {
    if (key === 'target') out.targetRef = refForEntity(value);
    else if (key === 'selected') out.selected = false;
    else if (key === 'path') continue;
    else if (key === 'pathGoal') continue;
    else if (key === 'pathRetry') continue;
    else if (key === 'pathIndex') continue;
    else if (typeof value !== 'function') out[key] = value;
  }
  return out;
}

Game.prototype.createSavePayload = function() {
  const selectedRefs = this.selected.map(refForEntity).filter(Boolean);
  return {
    schema: SAVE_SCHEMA_VERSION,
    world: { w: WORLD_W, h: WORLD_H, settings: this.worldSettings, seed: this.worldSeed },
    time: this.time,
    gid,
    camera: { ...this.camera },
    factions: this.factions.map(f => ({
      id: f.id,
      res: { ...f.res },
      alive: f.alive,
      aiState: { ...f.aiState },
      underAttack: f.underAttack || 0
    })),
    buildings: this.buildings.map(stripRuntimeEntity),
    units: this.units.map(stripRuntimeEntity),
    resources: this.resources.map(stripRuntimeEntity),
    decor: this.decor.map(stripRuntimeEntity),
    selectedRefs,
    formationMode: this.formationMode || 'box',
    controlGroups: Object.fromEntries(Object.entries(this.controlGroups || {}).map(([k, ids]) => [k, Array.isArray(ids) ? ids.slice() : []]))
  };
};

Game.prototype.applySavePayload = function(payload) {
  if (!payload || payload.schema > SAVE_SCHEMA_VERSION) return false;
  const byId = new Map();
  const hydrate = (arr) => arr.map(item => {
    const e = { ...item };
    if (e.targetRef) { e.target = null; delete e.targetRef; }
    byId.set(e.id, e);
    return e;
  });

  this.time = Number(payload.time) || 0;
  gid = Math.max(Number(payload.gid) || gid, gid);
  if (payload.camera) this.camera = { ...this.camera, ...payload.camera };
  if (Array.isArray(payload.factions)) {
    for (const saved of payload.factions) {
      const f = this.factions[saved.id];
      if (!f) continue;
      f.res = { ...f.res, ...(saved.res || {}) };
      f.alive = saved.alive !== false;
      f.aiState = { ...f.aiState, ...(saved.aiState || {}) };
      f.underAttack = saved.underAttack || 0;
    }
  }
  if (Array.isArray(payload.buildings)) this.buildings = hydrate(payload.buildings);
  if (Array.isArray(payload.units)) this.units = hydrate(payload.units);
  if (Array.isArray(payload.resources)) this.resources = hydrate(payload.resources);
  if (Array.isArray(payload.decor)) this.decor = hydrate(payload.decor);
  const all = [...this.units, ...this.buildings, ...this.resources, ...this.decor];
  const lookup = new Map(all.map(e => [e.id, e]));
  const resolveTargets = (arr, sourceArr) => {
    for (let i = 0; i < arr.length; i++) {
      const saved = sourceArr[i];
      if (saved && saved.targetRef) arr[i].target = lookup.get(saved.targetRef.id) || null;
    }
  };
  if (Array.isArray(payload.units)) resolveTargets(this.units, payload.units);
  if (Array.isArray(payload.buildings)) resolveTargets(this.buildings, payload.buildings);
  if (Array.isArray(payload.resources)) resolveTargets(this.resources, payload.resources);
  this.formationMode = FORMATION_MODES[payload.formationMode] ? payload.formationMode : (this.formationMode || 'box');
  this.controlGroups = {};
  if (payload.controlGroups && typeof payload.controlGroups === 'object') {
    for (const [key, ids] of Object.entries(payload.controlGroups)) {
      if (/^[1-9]$/.test(key) && Array.isArray(ids)) this.controlGroups[key] = ids.filter(id => lookup.has(id));
    }
  }
  this.selected = [];
  if (Array.isArray(payload.selectedRefs)) {
    for (const ref of payload.selectedRefs) {
      const e = lookup.get(ref.id);
      if (isAlive(e)) this.selected.push(e);
    }
  }
  for (const u of this.units) {
    u.selected = this.selected.includes(u);
    u.path = null;
    u.pathGoal = null;
    u.pathIndex = 0;
    u.pathRetry = 0;
  }
  this.projectiles = [];
  this.effects = [];
  this.markNavDirty && this.markNavDirty();
  this.buildMinimapTerrainCache && this.buildMinimapTerrainCache();
  this.uiDirty = true;
  return true;
};

Game.prototype.saveToWorldRecord = function(reason = 'manual') {
  if (!this.worldRecord || !this.worldRecord.id) return false;
  this.worldRecord.state = this.createSavePayload();
  this.worldRecord.playTime = (this.worldRecord.playTime || 0) + Math.max(0, this.time - (this.lastSavedGameTime || 0));
  this.lastSavedGameTime = this.time;
  this.worldRecord.settings = this.worldSettings;
  this.worldRecord.seed = this.worldSeed;
  const ok = TinySwordsStorage.saveWorldRecord(this.worldRecord);
  if (ok && reason !== 'autosave') this.toast('World saved.', 1.2);
  if (!ok && reason !== 'autosave') this.toast('Save failed: browser storage is full or unavailable.', 2.4);
  return ok;
};

Game.prototype.autosaveIfDue = function(dt) {
  if (!this.worldSettings || this.worldSettings.autosave === false) return;
  this.autosaveTimer = (this.autosaveTimer || 45) - dt;
  if (this.autosaveTimer <= 0) {
    this.autosaveTimer = 45;
    this.saveToWorldRecord('autosave');
  }
};
