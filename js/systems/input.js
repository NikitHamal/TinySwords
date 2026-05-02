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

    // Mouse events for desktop
    canvas.addEventListener('mousemove', (e) => {
      this.updatePointer(e);
      if (this.pointer.down) {
        const dx = Math.abs(this.pointer.x - this.pointer.startX);
        const dy = Math.abs(this.pointer.y - this.pointer.startY);
        if (this.dragBuilding) {
          if (dx + dy > 8) {
            this.dragBuilding.active = true;
            this.dragBuilding.x = this.pointer.wx + this.dragBuilding.offsetX;
            this.dragBuilding.y = this.pointer.wy + this.dragBuilding.offsetY;
            this.pointer.dragging = false;
          }
        } else this.pointer.dragging = dx + dy > 16;
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
        const picked = this.pickEntity(this.pointer.wx, this.pointer.wy);
        if (picked && picked.entity === 'building' && picked.faction === 0) {
          this.dragBuilding = {
            building: picked,
            active: false,
            offsetX: picked.x - this.pointer.wx,
            offsetY: picked.y - this.pointer.wy,
            x: picked.x,
            y: picked.y,
            originalX: picked.x,
            originalY: picked.y
          };
        }
      }
    });
    canvas.addEventListener('mouseup', (e) => {
      this.updatePointer(e);
      if (e.button === 0 && this.pointer.down) {
        if (this.dragBuilding) {
          if (this.dragBuilding.active) this.finishBuildingDrag();
          else this.clickSelect(e.shiftKey);
          this.dragBuilding = null;
        } else if (this.pointer.dragging) this.dragSelect(e.shiftKey);
        else this.clickSelect(e.shiftKey);
      }
      this.pointer.down = false; this.pointer.dragging = false;
      this.dragBuilding = null;
    });
    canvas.addEventListener('mouseenter', () => this.pointer.inside = true);
    canvas.addEventListener('mouseleave', () => {
      this.pointer.inside = false;
      this.pointer.down = false;
      this.pointer.dragging = false;
      this.dragBuilding = null;
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

    // Touch events for mobile
    let touchStartTime = 0;
    let touchStartX = 0;
    let touchStartY = 0;
    let lastTouchX = 0;
    let lastTouchY = 0;
    let pinchStartDist = 0;
    let pinchStartZoom = 1;
    let activeTouchId = null;

    canvas.addEventListener('touchstart', (e) => {
      e.preventDefault();
      this.sfx.resume();
      touchStartTime = Date.now();
      
      if (e.touches.length === 1) {
        const touch = e.touches[0];
        activeTouchId = touch.identifier;
        this.updatePointerFromTouch(touch);
        touchStartX = this.pointer.x;
        touchStartY = this.pointer.y;
        lastTouchX = touch.clientX;
        lastTouchY = touch.clientY;
        this.pointer.down = true;
        this.pointer.dragging = false;
        this.pointer.startX = this.pointer.x;
        this.pointer.startY = this.pointer.y;
        this.pointer.startWx = this.pointer.wx;
        this.pointer.startWy = this.pointer.wy;
        
        if (this.placing) {
          this.tryPlace(this.placing, this.pointer.wx, this.pointer.wy);
          this.pointer.down = false;
          return;
        }
        
        const picked = this.pickEntity(this.pointer.wx, this.pointer.wy);
        if (picked && picked.entity === 'building' && picked.faction === 0) {
          this.dragBuilding = {
            building: picked,
            active: false,
            offsetX: picked.x - this.pointer.wx,
            offsetY: picked.y - this.pointer.wy,
            x: picked.x,
            y: picked.y,
            originalX: picked.x,
            originalY: picked.y
          };
        }
      } else if (e.touches.length === 2) {
        // Pinch zoom
        const t1 = e.touches[0];
        const t2 = e.touches[1];
        pinchStartDist = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
        pinchStartZoom = this.camera.targetZoom;
        activeTouchId = null;
        // Clear long press timer on pinch
        if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
      }
    }, { passive: false });

    canvas.addEventListener('touchmove', (e) => {
      e.preventDefault();
      
      if (e.touches.length === 2) {
        // Pinch zoom
        const t1 = e.touches[0];
        const t2 = e.touches[1];
        const dist = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
        if (pinchStartDist > 0) {
          const scale = dist / pinchStartDist;
          this.camera.targetZoom = clamp(pinchStartZoom * scale, 0.72, 1.32);
          this.camera.zoom = this.camera.targetZoom;
        }
        return;
      }
      
      if (e.touches.length === 1 && activeTouchId !== null) {
        const touch = e.touches[0];
        if (touch.identifier !== activeTouchId) return;
        
        this.updatePointerFromTouch(touch);
        
        if (this.pointer.down) {
          const dx = Math.abs(this.pointer.x - this.pointer.startX);
          const dy = Math.abs(this.pointer.y - this.pointer.startY);
          
          if (this.dragBuilding) {
            if (dx + dy > 8) {
              this.dragBuilding.active = true;
              this.dragBuilding.x = this.pointer.wx + this.dragBuilding.offsetX;
              this.dragBuilding.y = this.pointer.wy + this.dragBuilding.offsetY;
              this.pointer.dragging = false;
            }
          } else {
            this.pointer.dragging = dx + dy > 16;
          }
        }
        
        lastTouchX = touch.clientX;
        lastTouchY = touch.clientY;
      }
    }, { passive: false });

    canvas.addEventListener('touchend', (e) => {
      e.preventDefault();
      
      // Find if our active touch ended
      let ourTouchEnded = false;
      for (let i = 0; i < e.changedTouches.length; i++) {
        if (e.changedTouches[i].identifier === activeTouchId) {
          ourTouchEnded = true;
          break;
        }
      }
      
      if (ourTouchEnded || e.touches.length === 0) {
        if (this.pointer.down && activeTouchId !== null) {
          const touchDuration = Date.now() - touchStartTime;
          const wasTap = touchDuration < 250 && !this.pointer.dragging;
          
          if (this.dragBuilding) {
            if (this.dragBuilding.active) this.finishBuildingDrag();
            else this.clickSelect(false);
            this.dragBuilding = null;
          } else if (wasTap) {
            this.clickSelect(false);
          } else if (this.pointer.dragging) {
            this.dragSelect(false);
          }
        }
        
        this.pointer.down = false;
        this.pointer.dragging = false;
        activeTouchId = null;
        this.dragBuilding = null;
      }
    }, { passive: false });

    canvas.addEventListener('touchcancel', (e) => {
      e.preventDefault();
      this.pointer.down = false;
      this.pointer.dragging = false;
      activeTouchId = null;
      this.dragBuilding = null;
    });

    // Long press for context menu (right-click equivalent)
    let longPressTimer = null;
    const LONG_PRESS_DURATION = 500;

    canvas.addEventListener('touchstart', (e) => {
      if (e.touches.length === 1) {
        longPressTimer = setTimeout(() => {
          if (this.pointer.down && !this.pointer.dragging) {
            this.updatePointerFromTouch(e.touches[0]);
            this.contextOrder(this.pointer.wx, this.pointer.wy);
            navigator.vibrate && navigator.vibrate(50);
          }
          longPressTimer = null;
        }, LONG_PRESS_DURATION);
      }
    }, { passive: false });

    canvas.addEventListener('touchend', (e) => {
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
    });

    canvas.addEventListener('touchmove', (e) => {
      if (longPressTimer && this.pointer.dragging) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
    });

    mini.addEventListener('click', (e) => {
      const r = mini.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width * WORLD_W;
      const y = (e.clientY - r.top) / r.height * WORLD_H;
      this.centerCamera(x, y);
    });
    
    // Touch support for minimap
    mini.addEventListener('touchstart', (e) => {
      e.preventDefault();
      const touch = e.touches[0];
      const r = mini.getBoundingClientRect();
      const x = (touch.clientX - r.left) / r.width * WORLD_W;
      const y = (touch.clientY - r.top) / r.height * WORLD_H;
      this.centerCamera(x, y);
    }, { passive: false });
    
    HUD.miniToggle.addEventListener('click', () => this.toggleMini());
    HUD.miniToggle.addEventListener('touchstart', (e) => { e.preventDefault(); this.toggleMini(); });
    HUD.helpClose.addEventListener('click', () => this.toggleHelp(false));
    HUD.helpClose.addEventListener('touchstart', (e) => { e.preventDefault(); this.toggleHelp(false); });
    window.addEventListener('resize', () => { this.resizeMini(); this.handleResize(); });
  
};

Game.prototype.updatePointer = function(e) {
    const rect = canvas.getBoundingClientRect();
    this.pointer.x = clamp((e.clientX - rect.left) / rect.width * VIEW_W, 0, VIEW_W);
    this.pointer.y = clamp((e.clientY - rect.top) / rect.height * VIEW_H, 0, VIEW_H);
    const w = screenToWorld(this, this.pointer.x, this.pointer.y);
    this.pointer.wx = w.x; this.pointer.wy = w.y;
  
};

Game.prototype.updatePointerFromTouch = function(touch) {
    const rect = canvas.getBoundingClientRect();
    this.pointer.x = clamp((touch.clientX - rect.left) / rect.width * VIEW_W, 0, VIEW_W);
    this.pointer.y = clamp((touch.clientY - rect.top) / rect.height * VIEW_H, 0, VIEW_H);
    const w = screenToWorld(this, this.pointer.x, this.pointer.y);
    this.pointer.wx = w.x; this.pointer.wy = w.y;
  
};


Game.prototype.finishBuildingDrag = function() {
    const drag = this.dragBuilding;
    if (!drag || !drag.building || drag.building.dead) return;
    const b = drag.building;
    const x = clamp(this.pointer.wx + drag.offsetX, 48, WORLD_W - 48);
    const y = clamp(this.pointer.wy + drag.offsetY, 48, WORLD_H - 48);
    const issue = this.relocateBuilding(b, x, y);
    if (issue) {
      this.toast(issue, 1.6);
      this.sfx.deny();
      return;
    }
    this.select([b]);
    this.effects.push({ kind: 'move', x: b.x, y: b.y, time: .55, max: .55 });
    this.toast(`${BUILDINGS[b.type].label} moved.`, 1.1);
    this.sfx.build(this.audioGainAt(b.x, b.y));
};

Game.prototype.cancelModes = function() {
    if (this.placing) { this.placing = null; this.toast('Build cancelled.', 1.1); }
    this.dragBuilding = null;
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

Game.prototype.handleResize = function() {
  // Handle canvas resize for responsive display
  const rect = canvas.getBoundingClientRect();
  if (rect.width > 0 && rect.height > 0) {
    // Canvas CSS handles the display size, we just need to ensure pointer tracking works
    this.uiDirty = true;
  }
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
    const isSprite = icon && (icon.startsWith('res') || ['iconWorker', 'iconWarrior', 'iconArcher', 'iconLancer', 'iconMonk'].includes(icon));
    b.innerHTML = `<img src="${IMAGE_PATHS[icon] || IMAGE_PATHS.iconMove}" class="${isSprite ? 'sprite-icon' : ''}" alt=""><span class="txt"><b>${title}</b><span>${sub || ''}</span></span>`;
    b.addEventListener('click', () => { if (b.classList.contains('disabled')) { this.sfx.deny(); return; } this.sfx.click(); onClick && onClick(); });
    return b;
};

Game.prototype.makeAction = function(hotkey, title, sub, icon, onClick, disabled = false) {
    const b = document.createElement('button');
    b.className = 'command' + (disabled ? ' disabled' : '');
    b.type = 'button';
    if (hotkey) b.dataset.hotkey = hotkey.toLowerCase();
    const isSprite = icon && (icon.startsWith('res') || ['iconWorker', 'iconWarrior', 'iconArcher', 'iconLancer', 'iconMonk'].includes(icon));
    b.innerHTML = `<img src="${IMAGE_PATHS[icon] || IMAGE_PATHS.iconMove}" class="${isSprite ? 'sprite-icon' : ''}" alt=""><span class="txt"><b>${title}</b><span>${sub || ''}</span></span>`;
    b.addEventListener('click', () => { if (b.classList.contains('disabled')) { this.sfx.deny(); return; } this.sfx.click(); onClick && onClick(); });
    return b;
};

Game.prototype.startPlacing = function(type) {
    this.placing = type;
    HUD.buildMenu.classList.add('hidden');
    this.toast(`Placing ${BUILDINGS[type].label}: left click clear grass, right click/Esc cancels.`, 2.2);
    this.uiDirty = true;
  
};

Game.prototype.tryPlace = function(type, x, y) {
    const f = this.factions[0];
    const def = BUILDINGS[type];
    if (!canAfford(f, def.cost)) { this.toast('Not enough resources.', 1.4); this.sfx.deny(); return false; }
    const issue = this.placementIssue(type, x, y);
    if (issue) { this.toast(issue, 1.6); this.sfx.deny(); return false; }
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
    if (e.entity === 'resource' || e.entity === 'decor' || (!e.entity && e.kind)) { this.select([e]); return; }
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
    for (let i = this.decor.length - 1; i >= 0; i--) {
      const d = this.decor[i];
      if (d.dead || d.sky || d.water) continue;
      const ds = DECOR_SPECS[d.kind] || {};
      const dw = (ds.fw || 64) * (d.scale || 1) * .55;
      const dh = (ds.fh || 64) * (d.scale || 1) * .55;
      const dcy = d.y - ((ds.baseline || ds.fh || 64) * (d.scale || 1) - dh) * .35;
      if (dist2(x, y, d.x, dcy) <= Math.max(dw, dh) * Math.max(dw, dh)) return d;
    }
    for (let i = this.resources.length - 1; i >= 0; i--) {
      const r = this.resources[i];
      if (r.dead) continue;
      if (r.amount <= 0 && !r.depleted) continue;
      const spec = getResourceVisualSpec(r);
      const visualCenterY = r.y - ((spec.baseline || spec.fh || 0) * (spec.scale || 1)) * .48;
      const visualRadius = Math.max(r.r + 10, Math.min(58, (spec.fh || r.r * 2) * (spec.scale || 1) * .38));
      if (dist2(x, y, r.x, r.y) <= (r.r + 9) * (r.r + 9)
        || dist2(x, y, r.x, visualCenterY) <= visualRadius * visualRadius) return r;
    }
    for (let i = this.decor.length - 1; i >= 0; i--) {
      const d = this.decor[i];
      if (d.dead || d.sky || d.water) continue;
      const ds = DECOR_SPECS[d.kind] || {};
      const dw = (ds.fw || 64) * (d.scale || 1) * .55;
      const dh = (ds.fh || 64) * (d.scale || 1) * .55;
      const dcy = d.y - ((ds.baseline || ds.fh || 64) * (d.scale || 1) - dh) * .35;
      if (dist2(x, y, d.x, dcy) <= Math.max(dw, dh) * Math.max(dw, dh)) return d;
    }
    return null;

};

Game.prototype.contextOrder = function(x, y) {
    if (this.placing) { this.placing = null; this.uiDirty = true; return; }
    const target = this.pickEntity(x, y);
    const ownBuildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1);
    const ownUnits = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && !e.garrisoned);
    if (ownBuildings.length && (!target || (target.entity !== 'resource' && target.entity !== 'decor' && !(target.kind && !target.entity)))) {
      const rally = this.nearestLandPoint(x, y, 320) || { x, y };
      let setRally = false;
      for (const b of ownBuildings) {
        if (BUILDINGS[b.type].trains && BUILDINGS[b.type].trains.length) {
          b.rally = { x: rally.x, y: rally.y };
          setRally = true;
        }
      }
      if (setRally) {
        this.effects.push({ kind: 'flag', x: rally.x, y: rally.y, time: 1.2, max: 1.2 });
        this.toast('Rally flag set.', 1.1);
        this.sfx.click();
      }
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
    if (target && (target.entity === 'decor' || (!target.entity && target.kind))) {
      this.orderMoveFormation(ownUnits, x, y, false);
      this.sfx.click();
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




// Pass 2: control groups, formation controls, worker-built placement, no garrison orders.
Game.prototype.bindEvents = function() {
  window.addEventListener('keydown', (e) => {
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    keys.add(e.key.toLowerCase());
    if (e.key === ' ') { e.preventDefault(); this.paused = !this.paused; this.toast(this.paused ? 'Paused' : 'Resumed', 1.1); return; }
    if (e.key.toLowerCase() === 'h') { this.toggleHelp(); return; }
    if (e.key.toLowerCase() === 'm') { this.toggleMini(); return; }
    if (e.key.toLowerCase() === 'b') { this.toggleBuildMenu(); return; }
    if (e.key === 'Escape') { this.cancelModes(); return; }
    if (e.ctrlKey && e.key.toLowerCase() === 'a') { e.preventDefault(); this.selectUnits(this.units.filter(u => u.faction === 0 && u.type !== 'worker' && !u.dead)); return; }
    if (e.ctrlKey && e.key.toLowerCase() === 's') { e.preventDefault(); this.saveToWorldRecord && this.saveToWorldRecord('manual'); return; }
    if (e.ctrlKey && /^[1-9]$/.test(e.key)) { e.preventDefault(); this.assignControlGroup(e.key); return; }
    if (/^[1-9]$/.test(e.key)) { e.preventDefault(); this.recallControlGroup(e.key) || this.activateHotkey(e.key); return; }
    if (e.key === '0') { this.selectUnits(this.units.filter(u => u.faction === 0 && !u.dead)); return; }
    const modeKey = e.key.toLowerCase();
    if (modeKey === 'z') this.setFormationMode('line');
    if (modeKey === 'x') this.setFormationMode('box');
    if (modeKey === 'c') this.setFormationMode('wedge');
    if (modeKey === 'v') this.setFormationMode('split');
  });
  window.addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));
  window.addEventListener('blur', () => keys.clear());

  canvas.addEventListener('mousemove', (e) => {
    this.updatePointer(e);
    if (this.pointer.down) {
      const dx = Math.abs(this.pointer.x - this.pointer.startX);
      const dy = Math.abs(this.pointer.y - this.pointer.startY);
      if (this.dragBuilding) {
        if (dx + dy > 8) {
          this.dragBuilding.active = true;
          this.dragBuilding.x = this.pointer.wx + this.dragBuilding.offsetX;
          this.dragBuilding.y = this.pointer.wy + this.dragBuilding.offsetY;
          this.pointer.dragging = false;
        }
      } else this.pointer.dragging = dx + dy > 16;
    }
  });
  canvas.addEventListener('mousedown', (e) => {
    this.sfx.resume();
    this.updatePointer(e);
    if (e.button === 0) {
      if (this.placing) { this.tryPlace(this.placing, this.pointer.wx, this.pointer.wy); return; }
      this.pointer.down = true; this.pointer.dragging = false;
      this.pointer.startX = this.pointer.x; this.pointer.startY = this.pointer.y;
      this.pointer.startWx = this.pointer.wx; this.pointer.startWy = this.pointer.wy;
      const picked = this.pickEntity(this.pointer.wx, this.pointer.wy);
      if (picked && picked.entity === 'building' && picked.faction === 0) {
        this.dragBuilding = { building: picked, active: false, offsetX: picked.x - this.pointer.wx, offsetY: picked.y - this.pointer.wy, x: picked.x, y: picked.y, originalX: picked.x, originalY: picked.y };
      }
    }
  });
  canvas.addEventListener('mouseup', (e) => {
    this.updatePointer(e);
    if (e.button === 0 && this.pointer.down) {
      if (this.dragBuilding) {
        if (this.dragBuilding.active) this.finishBuildingDrag();
        else this.clickSelect(e.shiftKey);
        this.dragBuilding = null;
      } else if (this.pointer.dragging) this.dragSelect(e.shiftKey);
      else this.clickSelect(e.shiftKey);
    }
    this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null;
  });
  canvas.addEventListener('mouseenter', () => this.pointer.inside = true);
  canvas.addEventListener('mouseleave', () => { this.pointer.inside = false; this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null; });
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
    this.centerCamera((e.clientX - r.left) / r.width * WORLD_W, (e.clientY - r.top) / r.height * WORLD_H);
  });
  HUD.miniToggle.addEventListener('click', () => this.toggleMini());
  HUD.helpClose.addEventListener('click', () => this.toggleHelp(false));
  window.addEventListener('resize', () => this.resizeMini());
};

Game.prototype.assignControlGroup = function(key) {
  const units = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && !e.dead);
  if (!units.length) { this.toast(`Control group ${key}: select player units first.`, 1.2); this.sfx.deny(); return false; }
  this.controlGroups[key] = units.map(u => u.id);
  this.toast(`Control group ${key} saved (${units.length} units).`, 1.2);
  this.uiDirty = true;
  return true;
};

Game.prototype.recallControlGroup = function(key) {
  const ids = this.controlGroups && this.controlGroups[key];
  if (!ids || !ids.length) return false;
  const set = new Set(ids);
  const units = this.units.filter(u => set.has(u.id) && u.faction === 0 && !u.dead);
  if (!units.length) { delete this.controlGroups[key]; this.toast(`Control group ${key} is empty.`, 1.1); return true; }
  const now = performance.now();
  const doubleTap = this.lastControlGroupTap && this.lastControlGroupTap[key] && now - this.lastControlGroupTap[key] < 550;
  this.lastControlGroupTap[key] = now;
  this.select(units);
  if (doubleTap) this.centerOnSelection(true);
  return true;
};

Game.prototype.setFormationMode = function(mode) {
  if (!FORMATION_MODES[mode]) return;
  this.formationMode = mode;
  this.toast(`Formation: ${FORMATION_MODES[mode].label}.`, 1.1);
  this.uiDirty = true;
};

Game.prototype.tryPlace = function(type, x, y) {
  const f = this.factions[0];
  const def = BUILDINGS[type];
  if (!canAfford(f, def.cost)) { this.toast('Not enough resources.', 1.4); this.sfx.deny(); return false; }
  const issue = this.placementIssue(type, x, y);
  if (issue) { this.toast(issue, 1.6); this.sfx.deny(); return false; }
  pay(f, def.cost);
  const b = this.addBuilding(0, type, x, y, false);
  this.effects.push({ kind: 'dust', x, y, time: .8, max: .8 });
  this.placing = null;
  const builders = this.assignBuildersTo ? this.assignBuildersTo(b, 0, type === 'castle' ? 4 : 2, true) : 0;
  this.select([b]);
  this.toast(builders ? `${def.label} foundation placed. ${builders} worker(s) building.` : `${def.label} foundation placed. Select workers and right click it to build.`, 2.0);
  this.sfx.build(this.audioGainAt(x, y));
  return true;
};

Game.prototype.contextOrder = function(x, y) {
  if (this.placing) { this.placing = null; this.uiDirty = true; return; }
  const target = this.pickEntity(x, y);
  const ownBuildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1);
  const ownUnits = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && !e.dead);
    if (ownBuildings.length && (!target || (target.entity !== 'resource' && target.entity !== 'decor' && !(target.kind && !target.entity)))) {
      const rally = this.nearestLandPoint(x, y, 320) || { x, y };
      let setRally = false;
      for (const b of ownBuildings) {
        if (BUILDINGS[b.type].trains && BUILDINGS[b.type].trains.length) {
          b.rally = { x: rally.x, y: rally.y };
          setRally = true;
        }
      }
      if (setRally) {
        this.effects.push({ kind: 'flag', x: rally.x, y: rally.y, time: 1.2, max: 1.2 });
        this.toast('Rally flag set.', 1.1); this.sfx.click();
      }
    }
  if (!ownUnits.length) return;
  if (target && target.entity === 'building' && target.faction === 0 && (target.build < 1 || target.hp < target.maxHp)) {
    const workers = ownUnits.filter(u => u.type === 'worker');
    if (workers.length) {
      for (const u of workers) { this.clearUnitPath && this.clearUnitPath(u); u.order = 'repair'; u.target = target; u.goal = null; u.carry = null; u.gather = 0; u.hold = false; }
      this.sfx.click(); this.toast(`${workers.length} worker(s) assigned to ${target.build < 1 ? 'build' : 'repair'}.`, 1.4); return;
    }
  }
  if (target && target.entity === 'resource') {
    const workers = ownUnits.filter(u => u.type === 'worker');
    for (const u of workers) this.orderHarvest(u, target);
    if (workers.length) this.toast(`${workers.length} worker(s) harvesting ${target.type === 'tree' ? 'wood' : target.type}.`, 1.4);
    return;
  }
  if (target && (target.entity === 'decor' || (!target.entity && target.kind))) {
    this.orderMoveFormation(ownUnits, x, y, false);
    this.sfx.click();
    return;
  }
  if (target && target.faction !== undefined && target.faction !== 0) {
    for (const u of ownUnits) this.orderAttack(u, target, false);
    this.sfx.attack(this.audioGainAt(x, y)); return;
  }
  this.orderMoveFormation(ownUnits, x, y, false);
  this.sfx.click();
};

Game.prototype.garrisonArchers = function() {
  this.toast('Garrison removed: every tower has one permanent archer.', 1.4);
  this.sfx.deny();
};

Game.prototype.ungarrisonSelected = function() {
  this.toast('Garrison removed: tower archers are built in.', 1.4);
};


// Pass 3: E opens an RTS worker-role command panel and keeps drag/build controls intact.
Game.prototype.bindEvents = function() {
  window.addEventListener('keydown', (e) => {
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    keys.add(e.key.toLowerCase());
    const k = e.key.toLowerCase();

    if (e.key === ' ') { e.preventDefault(); this.paused = !this.paused; this.toast(this.paused ? 'Paused' : 'Resumed', 1.1); return; }
    if (k === 'e') { e.preventDefault(); this.toggleWorkerRoles && this.toggleWorkerRoles(); return; }
    if (k === 'h') { this.toggleHelp(); return; }
    if (k === 'm') { this.toggleMini(); return; }
    if (k === 'b') { this.toggleBuildMenu(); return; }
    if (e.key === 'Escape') {
      if (this.workerRolesOpen && this.toggleWorkerRoles) { this.toggleWorkerRoles(false); return; }
      this.cancelModes();
      return;
    }
    if (e.ctrlKey && k === 'a') { e.preventDefault(); this.selectUnits(this.units.filter(u => u.faction === 0 && u.type !== 'worker' && !u.dead)); return; }
    if (e.ctrlKey && k === 's') { e.preventDefault(); this.saveToWorldRecord && this.saveToWorldRecord('manual'); return; }
    if (e.ctrlKey && /^[1-9]$/.test(e.key)) { e.preventDefault(); this.assignControlGroup(e.key); return; }
    if (/^[1-9]$/.test(e.key)) { e.preventDefault(); this.recallControlGroup(e.key) || this.activateHotkey(e.key); return; }
    if (e.key === '0') { this.selectUnits(this.units.filter(u => u.faction === 0 && !u.dead)); return; }

    if (k === 'z') this.setFormationMode('line');
    if (k === 'x') this.setFormationMode('box');
    if (k === 'c') this.setFormationMode('wedge');
    if (k === 'v') this.setFormationMode('split');
  });
  window.addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));
  window.addEventListener('blur', () => keys.clear());

  canvas.addEventListener('mousemove', (e) => {
    this.updatePointer(e);
    if (this.pointer.down) {
      const dx = Math.abs(this.pointer.x - this.pointer.startX);
      const dy = Math.abs(this.pointer.y - this.pointer.startY);
      if (this.dragBuilding) {
        if (dx + dy > 8) {
          this.dragBuilding.active = true;
          this.dragBuilding.x = this.pointer.wx + this.dragBuilding.offsetX;
          this.dragBuilding.y = this.pointer.wy + this.dragBuilding.offsetY;
          this.pointer.dragging = false;
        }
      } else this.pointer.dragging = dx + dy > 16;
    }
  });
  canvas.addEventListener('mousedown', (e) => {
    this.sfx.resume();
    this.updatePointer(e);
    if (e.button === 0) {
      if (this.placing) { this.tryPlace(this.placing, this.pointer.wx, this.pointer.wy); return; }
      this.pointer.down = true; this.pointer.dragging = false;
      this.pointer.startX = this.pointer.x; this.pointer.startY = this.pointer.y;
      this.pointer.startWx = this.pointer.wx; this.pointer.startWy = this.pointer.wy;
      const picked = this.pickEntity(this.pointer.wx, this.pointer.wy);
      if (picked && picked.entity === 'building' && picked.faction === 0) {
        this.dragBuilding = { building: picked, active: false, offsetX: picked.x - this.pointer.wx, offsetY: picked.y - this.pointer.wy, x: picked.x, y: picked.y, originalX: picked.x, originalY: picked.y };
      }
    }
  });
  canvas.addEventListener('mouseup', (e) => {
    this.updatePointer(e);
    if (e.button === 0 && this.pointer.down) {
      if (this.dragBuilding) {
        if (this.dragBuilding.active) this.finishBuildingDrag();
        else this.clickSelect(e.shiftKey);
        this.dragBuilding = null;
      } else if (this.pointer.dragging) this.dragSelect(e.shiftKey);
      else this.clickSelect(e.shiftKey);
    }
    this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null;
  });
  canvas.addEventListener('mouseenter', () => this.pointer.inside = true);
  canvas.addEventListener('mouseleave', () => { this.pointer.inside = false; this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null; });
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
    this.centerCamera((e.clientX - r.left) / r.width * WORLD_W, (e.clientY - r.top) / r.height * WORLD_H);
  });
  HUD.miniToggle.addEventListener('click', () => this.toggleMini());
  HUD.helpClose.addEventListener('click', () => this.toggleHelp(false));
  if (HUD.workerRoleClose) HUD.workerRoleClose.addEventListener('click', () => this.toggleWorkerRoles && this.toggleWorkerRoles(false));
  if (HUD.workerRoles) {
    HUD.workerRoles.addEventListener('click', (e) => {
      const scope = e.target.closest('[data-worker-scope]');
      if (scope) {
        this.workerRoleScope = scope.dataset.workerScope;
        this.renderWorkerRolePanel && this.renderWorkerRolePanel();
        this.sfx.click();
        return;
      }
      const role = e.target.closest('[data-worker-role]');
      if (role) {
        this.assignWorkersRole && this.assignWorkersRole(role.dataset.workerRole, this.workerRoleScope || 'selected');
        this.renderWorkerRolePanel && this.renderWorkerRolePanel();
        this.sfx.click();
      }
    });
  }
  window.addEventListener('resize', () => this.resizeMini());
};

const tinySwordsPass3CancelModes = Game.prototype.cancelModes;
Game.prototype.cancelModes = function() {
  if (this.workerRolesOpen && this.toggleWorkerRoles) this.toggleWorkerRoles(false);
  tinySwordsPass3CancelModes.call(this);
};


// Pass 4: pause menu wiring and quieter in-game HUD behavior.
Game.prototype.setPaused = function(paused) {
  this.paused = !!paused;
  this.uiDirty = true;
  if (this.paused) {
    this.saveToWorldRecord && this.saveToWorldRecord('autosave');
    if (HUD.pauseSettingsBody) HUD.pauseSettingsBody.classList.add('hidden');
    if (HUD.pauseVolume) {
      const gs = TinySwordsStorage.globalSettings ? TinySwordsStorage.globalSettings() : {};
      HUD.pauseVolume.value = Number(gs.volume ?? 0.8);
    }
    if (HUD.pauseAutosave) HUD.pauseAutosave.checked = this.worldSettings?.autosave !== false;
  }
};

Game.prototype.togglePause = function(force) {
  const next = force === undefined ? !this.paused : !!force;
  this.setPaused(next);
};

Game.prototype.applyPauseSettings = function() {
  const vol = Number(HUD.pauseVolume?.value ?? 0.8);
  if (Number.isFinite(vol)) {
    if (this.sfx && this.sfx.master) this.sfx.master.gain.value = 0.10 * clamp(vol, 0, 1);
    TinySwordsStorage.saveGlobalSettings && TinySwordsStorage.saveGlobalSettings({ volume: clamp(vol, 0, 1) });
  }
  if (this.worldSettings && HUD.pauseAutosave) this.worldSettings.autosave = HUD.pauseAutosave.checked;
};

Game.prototype.bindEvents = function() {
  window.addEventListener('keydown', (e) => {
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    const k = e.key.toLowerCase();

    if (e.key === ' ' || k === 'p') { e.preventDefault(); this.togglePause(); return; }
    if (this.paused) {
      if (e.key === 'Escape') { this.togglePause(false); return; }
      return;
    }

    keys.add(k);
    if (k === 'e') { e.preventDefault(); this.toggleWorkerRoles && this.toggleWorkerRoles(); return; }
    if (k === 'h') { this.toggleHelp(); return; }
    if (k === 'm') { this.toggleMini(); return; }
    if (k === 'b') { this.toggleBuildMenu(); return; }
    if (e.key === 'Escape') {
      if (this.workerRolesOpen && this.toggleWorkerRoles) { this.toggleWorkerRoles(false); return; }
      this.cancelModes();
      return;
    }
    if (e.ctrlKey && k === 'a') { e.preventDefault(); this.selectUnits(this.units.filter(u => u.faction === 0 && u.type !== 'worker' && !u.dead)); return; }
    if (e.ctrlKey && k === 's') { e.preventDefault(); this.saveToWorldRecord && this.saveToWorldRecord('manual'); return; }
    if (e.ctrlKey && /^[1-9]$/.test(e.key)) { e.preventDefault(); this.assignControlGroup(e.key); return; }
    if (/^[1-9]$/.test(e.key)) { e.preventDefault(); this.recallControlGroup(e.key) || this.activateHotkey(e.key); return; }
    if (e.key === '0') { this.selectUnits(this.units.filter(u => u.faction === 0 && !u.dead)); return; }

    if (k === 'z') this.setFormationMode('line');
    if (k === 'x') this.setFormationMode('box');
    if (k === 'c') this.setFormationMode('wedge');
    if (k === 'v') this.setFormationMode('split');
  });
  window.addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));
  window.addEventListener('blur', () => keys.clear());

  canvas.addEventListener('mousemove', (e) => {
    this.updatePointer(e);
    if (this.paused) return;
    if (this.pointer.down) {
      const dx = Math.abs(this.pointer.x - this.pointer.startX);
      const dy = Math.abs(this.pointer.y - this.pointer.startY);
      if (this.dragBuilding) {
        if (dx + dy > 8) {
          this.dragBuilding.active = true;
          this.dragBuilding.x = this.pointer.wx + this.dragBuilding.offsetX;
          this.dragBuilding.y = this.pointer.wy + this.dragBuilding.offsetY;
          this.pointer.dragging = false;
        }
      } else this.pointer.dragging = dx + dy > 16;
    }
  });
  canvas.addEventListener('mousedown', (e) => {
    this.sfx.resume();
    this.updatePointer(e);
    if (this.paused) return;
    if (e.button === 0) {
      if (this.placing) { this.tryPlace(this.placing, this.pointer.wx, this.pointer.wy); return; }
      this.pointer.down = true; this.pointer.dragging = false;
      this.pointer.startX = this.pointer.x; this.pointer.startY = this.pointer.y;
      this.pointer.startWx = this.pointer.wx; this.pointer.startWy = this.pointer.wy;
      const picked = this.pickEntity(this.pointer.wx, this.pointer.wy);
      if (picked && picked.entity === 'building' && picked.faction === 0) {
        this.dragBuilding = { building: picked, active: false, offsetX: picked.x - this.pointer.wx, offsetY: picked.y - this.pointer.wy, x: picked.x, y: picked.y, originalX: picked.x, originalY: picked.y };
      }
    }
  });
  canvas.addEventListener('mouseup', (e) => {
    this.updatePointer(e);
    if (this.paused) { this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null; return; }
    if (e.button === 0 && this.pointer.down) {
      if (this.dragBuilding) {
        if (this.dragBuilding.active) this.finishBuildingDrag();
        else this.clickSelect(e.shiftKey);
        this.dragBuilding = null;
      } else if (this.pointer.dragging) this.dragSelect(e.shiftKey);
      else this.clickSelect(e.shiftKey);
    }
    this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null;
  });
  canvas.addEventListener('mouseenter', () => this.pointer.inside = true);
  canvas.addEventListener('mouseleave', () => { this.pointer.inside = false; this.pointer.down = false; this.pointer.dragging = false; this.dragBuilding = null; });
  canvas.addEventListener('contextmenu', (e) => { e.preventDefault(); this.updatePointer(e); if (!this.paused) this.contextOrder(this.pointer.wx, this.pointer.wy); });
  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    this.updatePointer(e);
    const before = screenToWorld(this, this.pointer.x, this.pointer.y);
    this.camera.targetZoom = clamp(this.camera.targetZoom * (e.deltaY < 0 ? 1.09 : 0.92), 0.72, 1.32);
    this.camera.zoom = this.camera.targetZoom;
    this.camera.x = clamp(before.x - this.pointer.x / this.camera.zoom, 0, WORLD_W - VIEW_W / this.camera.zoom);
    this.camera.y = clamp(before.y - this.pointer.y / this.camera.zoom, 0, WORLD_H - VIEW_H / this.camera.zoom);
  }, { passive: false });

  mini.addEventListener('click', (e) => {
    if (this.paused) return;
    const r = mini.getBoundingClientRect();
    this.centerCamera((e.clientX - r.left) / r.width * WORLD_W, (e.clientY - r.top) / r.height * WORLD_H);
  });
  HUD.miniToggle && HUD.miniToggle.addEventListener('click', () => this.toggleMini());
  HUD.helpClose && HUD.helpClose.addEventListener('click', () => this.toggleHelp(false));
  HUD.pauseResume && HUD.pauseResume.addEventListener('click', () => { this.sfx.click(); this.togglePause(false); });
  HUD.pauseSettings && HUD.pauseSettings.addEventListener('click', () => {
    this.sfx.click();
    if (HUD.pauseSettingsBody) HUD.pauseSettingsBody.classList.toggle('hidden');
  });
  HUD.pauseExit && HUD.pauseExit.addEventListener('click', () => {
    this.applyPauseSettings && this.applyPauseSettings();
    this.saveToWorldRecord && this.saveToWorldRecord('manual');
    window.tinySwordsApp && window.tinySwordsApp.returnToMenu();
  });
  HUD.pauseVolume && HUD.pauseVolume.addEventListener('input', () => this.applyPauseSettings && this.applyPauseSettings());
  HUD.pauseAutosave && HUD.pauseAutosave.addEventListener('change', () => this.applyPauseSettings && this.applyPauseSettings());

  if (HUD.workerRoleClose) HUD.workerRoleClose.addEventListener('click', () => this.toggleWorkerRoles && this.toggleWorkerRoles(false));
  if (HUD.workerRoles) {
    HUD.workerRoles.addEventListener('click', (e) => {
      const scope = e.target.closest('[data-worker-scope]');
      if (scope) {
        this.workerRoleScope = scope.dataset.workerScope;
        this.renderWorkerRolePanel && this.renderWorkerRolePanel();
        this.sfx.click();
        return;
      }
      const role = e.target.closest('[data-worker-role]');
      if (role) {
        this.assignWorkersRole && this.assignWorkersRole(role.dataset.workerRole, this.workerRoleScope || 'selected');
        this.renderWorkerRolePanel && this.renderWorkerRolePanel();
        this.sfx.click();
      }
    });
  }
  window.addEventListener('resize', () => this.resizeMini());
};
