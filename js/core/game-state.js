// Game state container. Behavior lives in the systems files.
class Game {
  constructor(worldRecord = null) {
    this.worldRecord = worldRecord || TinySwordsStorage.createWorld('Quick Realm', DEFAULT_WORLD_SETTINGS);
    this.worldSettings = applyWorldSettings((this.worldRecord && this.worldRecord.settings) || DEFAULT_WORLD_SETTINGS);
    this.worldSeed = (this.worldRecord && (this.worldRecord.seed || this.worldRecord.settings?.seed)) || this.worldSettings.seed || 'tinyswords';
    this.sfx = new SoundBank();
    this.camera = { x: 700, y: 720, zoom: 1.0, targetZoom: 1.0 };
    this.pointer = { x: VIEW_W / 2, y: VIEW_H / 2, wx: 0, wy: 0, down: false, dragging: false, startX: 0, startY: 0, startWx: 0, startWy: 0, inside: false };
    this.paused = false;
    this.fast = false;
    this.running = true;
    this.uiDirty = true;
    this.uiTimer = 0;
    this.time = 0;
    this.toastTimer = 0;
    this.autosaveTimer = 30;
    this.lastSavedGameTime = 0;
    this.selected = [];
    this.formationMode = 'box';
    this.controlGroups = {};
    this.lastControlGroupTap = {};
this.attackPings = [];
    this.placing = null;
    this.dragBuilding = null;
    this.aiTick = 0;
    this.lastFrame = 0;
    this._unitBucketsArr = [];
    this._unitBucketMap = new Map();
    this._unitBucketCount = 0;
    this._resBucketsArr = [];
    this._resBucketMap = new Map();
    this._resBucketCount = 0;
    this._bldBucketsArr = [];
    this._bldBucketMap = new Map();
    this._bldBucketCount = 0;
    this._decorBucketMap = new Map();
    this.decorBuckets = null;
    this.decorBucketSize = 128;
    this._nearbyBuf = [];
    this._nearbyResBuf = [];
    this._nearbyBldBuf = [];
    this._drawablePool = [];
    this._terrainDirty = true;
    this._lastCamX = -99999;
    this._lastCamY = -99999;
    this._lastCamZoom = -1;
    withSeededRandom(this.worldSeed, () => this.reset());
    if (this.worldRecord && this.worldRecord.state) this.applySavePayload(this.worldRecord.state);
    this.bindEvents();
    this.buildStaticMenus();
    this.toast('Scout, build, gather, train, save your realm, and conquer. Press H for help.', 5);
  }

  destroy() {
    this.running = false;
    this.saveToWorldRecord && this.saveToWorldRecord('autosave');
  }
}
