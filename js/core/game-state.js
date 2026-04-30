// Game state container. Behavior lives in the systems files.
class Game {
  constructor() {
    this.sfx = new SoundBank();
    this.camera = { x: 700, y: 720, zoom: 0.84, targetZoom: 0.84 };
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
}
