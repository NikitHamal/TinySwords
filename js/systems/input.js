// Input, selection, orders, build placement, camera centering.
Game.prototype.bindEvents = function() {
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
      if (e.ctrlKey && e.key.toLowerCase() === 's') { e.preventDefault(); this.saveToWorldRecord && this.saveToWorldRecord('manual'); }
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
    canvas.addEventListener('mouseenter', () => this.pointer.inside = true);
    canvas.addEventListener('mouseleave', () => {
      this.pointer.inside = false;
      this.pointer.down = false;
      this.pointer.dragging = false;
    });
    canvas.addEventListener('contextmenu', (e) => { e.preventDefault(); this.updatePointer(e); this.contextOrder(this.pointer.wx, this.pointer.wy); });
    canvas.addEventListener('wheel', (e) => {
      e.preventDefault();
      const before = screenToWorld(this, this.pointer.x, this.pointer.y);
      this.camera.targetZoom = clamp(this.camera.targetZoom * (e.deltaY < 0 ? 1.09 : 0.92), 0.72, 1.32);
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
  
};

Game.prototype.updatePointer = function(e) {
    const rect = canvas.getBoundingClientRect();
    this.pointer.x = clamp((e.clientX - rect.left) / rect.width * VIEW_W, 0, VIEW_W);
    this.pointer.y = clamp((e.clientY - rect.top) / rect.height * VIEW_H, 0, VIEW_H);
    const w = screenToWorld(this, this.pointer.x, this.pointer.y);
    this.pointer.wx = w.x; this.pointer.wy = w.y;
  
};

Game.prototype.cancelModes = function() {
    if (this.placing) { this.placing = null; this.toast('Build cancelled.', 1.1); }
    HUD.buildMenu.classList.add('hidden');
    HUD.help.classList.add('hidden');
    this.uiDirty = true;
  
};

Game.prototype.toggleHelp = function(force) {
    const show = force === undefined ? HUD.help.classList.contains('hidden') : force;
    HUD.help.classList.toggle('hidden', !show);
  
};

Game.prototype.toggleMini = function() { HUD.miniWrap.classList.toggle('expanded'); document.body.classList.toggle('map-open', HUD.miniWrap.classList.contains('expanded')); this.resizeMini(); 
};

Game.prototype.toggleBuildMenu = function() { HUD.buildMenu.classList.toggle('hidden'); this.placing = null; this.sfx.click(); 
};

Game.prototype.resizeMini = function() {
    const r = mini.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) { mini.width = Math.floor(r.width); mini.height = Math.floor(r.height); mctx.imageSmoothingEnabled = false; }
  
};

Game.prototype.buildStaticMenus = function() {
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
  
};

Game.prototype.makeButton = function({ className = 'command', icon, title, sub, onClick, disabled = false }) {
    const b = document.createElement('button');
    b.className = className + (disabled ? ' disabled' : '');
    b.type = 'button';
    b.innerHTML = `<img src="${IMAGE_PATHS[icon] || IMAGE_PATHS.iconMove}" alt=""><span class="txt"><b>${title}</b><span>${sub || ''}</span></span>`;
    b.addEventListener('click', () => { if (b.classList.contains('disabled')) { this.sfx.deny(); return; } this.sfx.click(); onClick && onClick(); });
    return b;
  
};

Game.prototype.startPlacing = function(type) {
    this.placing = type;
    HUD.buildMenu.classList.add('hidden');
    this.toast(`Placing ${BUILDINGS[type].label}: left click land, right click/Esc cancels.`, 2.2);
    this.uiDirty = true;
  
};

Game.prototype.tryPlace = function(type, x, y) {
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
  
};


Game.prototype.clickSelect = function(add) {
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
  
};

Game.prototype.dragSelect = function(add) {
    const x1 = Math.min(this.pointer.startWx, this.pointer.wx);
    const y1 = Math.min(this.pointer.startWy, this.pointer.wy);
    const x2 = Math.max(this.pointer.startWx, this.pointer.wx);
    const y2 = Math.max(this.pointer.startWy, this.pointer.wy);
    const hits = this.units.filter(u => u.faction === 0 && !u.garrisoned && u.x >= x1 && u.x <= x2 && u.y >= y1 && u.y <= y2);
    if (add) this.select([...new Set([...this.selected.filter(e => e.faction === 0), ...hits])]);
    else this.select(hits);
  
};

Game.prototype.select = function(list) {
    this.selected.forEach(e => { if (e.entity === 'unit') e.selected = false; });
    this.selected = list.filter(isAlive);
    this.selected.forEach(e => { if (e.entity === 'unit') e.selected = true; });
    this.uiDirty = true;
  
};

Game.prototype.selectUnits = function(list) { this.select(list); this.centerOnSelection(false); 
};

Game.prototype.pickEntity = function(x, y) {
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
      if (r.dead || r.amount <= 0) continue;
      const spec = getResourceVisualSpec(r);
      const visualCenterY = r.y - ((spec.baseline || spec.fh || 0) * (spec.scale || 1)) * .48;
      const visualRadius = Math.max(r.r + 10, Math.min(58, (spec.fh || r.r * 2) * (spec.scale || 1) * .38));
      if (dist2(x, y, r.x, r.y) <= (r.r + 9) * (r.r + 9)
        || dist2(x, y, r.x, visualCenterY) <= visualRadius * visualRadius) return r;
    }
    return null;

};

Game.prototype.contextOrder = function(x, y) {
    if (this.placing) { this.placing = null; this.uiDirty = true; return; }
    const target = this.pickEntity(x, y);
    const ownBuildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1);
    const ownUnits = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && !e.garrisoned);
    if (ownBuildings.length && (!target || target.entity !== 'resource')) {
      const rally = this.nearestLandPoint(x, y, 320) || { x, y };
      for (const b of ownBuildings) b.rally = { x: rally.x, y: rally.y };
      this.effects.push({ kind: 'flag', x: rally.x, y: rally.y, time: 1.2, max: 1.2 });
      this.toast('Rally flag set.', 1.1);
      this.sfx.click();
    }
    if (!ownUnits.length) return;
    if (target && target.entity === 'building' && target.faction === 0 && target.type === 'tower') {
      this.garrisonArchers(ownUnits.filter(u => u.type === 'archer'), target);
    }
    if (target && target.entity === 'building' && target.faction === 0 && (target.build < 1 || target.hp < target.maxHp)) {
      const workers = ownUnits.filter(u => u.type === 'worker');
      if (workers.length) {
        for (const u of workers) { this.clearUnitPath && this.clearUnitPath(u); u.order = 'repair'; u.target = target; u.goal = null; }
        this.sfx.click();
        this.toast(`${workers.length} worker(s) moving to build/repair.`, 1.4);
        return;
      }
    }
    if (target && target.entity === 'building' && target.faction === 0 && target.type === 'tower') return;
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
  
};


Game.prototype.orderAttack = function(u, target, attackMove) {
    u.target = target; u.order = 'attack'; u.goal = null; u.attackMove = attackMove; u.hold = false;
  
};

Game.prototype.orderHarvest = function(u, res) {
    if (u.type !== 'worker') return;
    this.clearUnitPath && this.clearUnitPath(u);
    u.order = 'harvest'; u.target = res; u.goal = null; u.gather = 0; u.hold = false;
  
};

Game.prototype.garrisonArchers = function(archers, tower, silent = false) {
    if (!tower || tower.type !== 'tower' || tower.dead) return;
    let queued = 0, entered = 0;
    for (const a of archers) {
      if (!a || a.dead || a.type !== 'archer' || a.garrisoned || a.faction !== tower.faction) continue;
      const queuedForTower = this.units.filter(u => u.order === 'garrison' && u.target === tower && !u.dead && !u.garrisoned).length;
      if (tower.garrison.length + queuedForTower >= BUILDINGS.tower.garrisonCap) break;
      this.clearUnitPath && this.clearUnitPath(a);
      a.order = 'garrison';
      a.target = tower;
      a.goal = { x: tower.x, y: tower.y + 42 };
      a.attackMove = false;
      a.hold = false;
      if (dist2(a.x, a.y, tower.x, tower.y + 42) <= 48 * 48 && tower.garrison.length < BUILDINGS.tower.garrisonCap) {
        this.finishGarrison(a, tower);
        entered++;
      } else queued++;
    }
    if (entered || queued) {
      if (!silent && tower.faction === 0) {
        this.toast(entered ? `${entered} archer(s) in tower${queued ? `, ${queued} moving` : ''}.` : `${queued} archer(s) moving to tower.`, 1.6);
        this.sfx.build();
        this.select(this.selected.filter(e => !(e.entity === 'unit' && e.garrisoned)));
      }
    } else if (!silent) { this.toast('Select friendly archers and right click a tower.', 1.5); this.sfx.deny(); }
};

Game.prototype.activateHotkey = function(key) {
    const buttons = [...HUD.actionBar.querySelectorAll('button[data-hotkey]')];
    const b = buttons.find(el => el.dataset.hotkey === key.toLowerCase());
    if (b) b.click();
  
};

Game.prototype.centerOnSelection = function(instant = true) {
    const own = this.selected.filter(e => e.x !== undefined);
    if (!own.length) return;
    const x = own.reduce((s, e) => s + e.x, 0) / own.length;
    const y = own.reduce((s, e) => s + e.y, 0) / own.length;
    this.centerCamera(x, y, instant);
  
};

Game.prototype.centerCamera = function(x, y) {
    this.camera.x = clamp(x - VIEW_W / this.camera.zoom / 2, 0, WORLD_W - VIEW_W / this.camera.zoom);
    this.camera.y = clamp(y - VIEW_H / this.camera.zoom / 2, 0, WORLD_H - VIEW_H / this.camera.zoom);
  
};

Game.prototype.toast = function(text, time = 2) {
    HUD.message.textContent = text;
    HUD.message.classList.remove('hidden');
    this.toastTimer = time;
  
};


