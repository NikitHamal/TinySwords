// HUD rendering and command buttons.
Game.prototype.setHudHtml = function(el, html) {
  if (el && el.innerHTML !== html) el.innerHTML = html;
};

Game.prototype.renderUI = function() {
  const f = this.factions[0];
  const pop = this.population(0);
  const resHtml = Object.keys(RESOURCES).map(k => {
    const r = RESOURCES[k];
    return `<div class="res-pill"><img src="${IMAGE_PATHS[r.icon]}" alt="${r.label}"><span><b>${Math.floor(f.res[k])}</b><small>${r.label}</small></span></div>`;
  }).join('') + `<div class="res-pill pop-pill"><img src="${IMAGE_PATHS.iconHouse}" alt="Population"><span><b>${pop.used}/${pop.cap}</b><small>Pop</small></span></div>`;
  this.setHudHtml(HUD.resources, resHtml);

  const enemiesAlive = this.factions.filter(x => x.id !== 0 && x.ai && x.alive).length;
  const worldName = this.worldRecord?.name || 'World';
  const stateHtml = `<span class="status-dot ${this.paused ? 'paused' : 'live'}"></span><span class="state-main">${this.paused ? 'Paused' : 'Live'}</span><span class="state-world">${worldName}</span><span class="state-rivals">${enemiesAlive} rivals</span><button class="mini-action" id="hudSaveBtn" title="Save world (Ctrl+S)">Save</button><button class="mini-action" id="hudExitBtn" title="Save and return to menu">Menu</button>`;
  this.setHudHtml(HUD.state, stateHtml);
  const saveBtn = document.getElementById('hudSaveBtn');
  const exitBtn = document.getElementById('hudExitBtn');
  if (saveBtn) saveBtn.onclick = () => this.saveToWorldRecord && this.saveToWorldRecord('manual');
  if (exitBtn) exitBtn.onclick = () => window.tinySwordsApp && window.tinySwordsApp.returnToMenu();

  this.renderSelectionPanel();
  this.renderActions();
};

Game.prototype.renderSelectionPanel = function() {
  const s = this.selected.filter(isAlive);
  if (!s.length) {
    this.setHudHtml(HUD.selectionHeader, '<span class="panel-kicker">Selection</span><b>No selection</b>');
    this.setHudHtml(HUD.selectionBody, '<div class="selection-hint">Drag units, click buildings, or press B to place structures.</div>');
    return;
  }

  const first = s[0];
  const iconFor = (e) => {
    if (e.entity === 'resource') return e.type === 'tree' ? IMAGE_PATHS.resWood : e.type === 'gold' ? IMAGE_PATHS.resGold : IMAGE_PATHS.resFood;
    const def = e.entity === 'unit' ? UNITS[e.type] : BUILDINGS[e.type];
    return IMAGE_PATHS[def.icon] || IMAGE_PATHS.iconMove;
  };

  if (s.length > 1) {
    const groups = {};
    for (const e of s) groups[e.type] = (groups[e.type] || 0) + 1;
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Group</small><b>${s.length} selected</b></span>`);
    this.setHudHtml(HUD.selectionBody, Object.entries(groups).map(([t, n]) => `<div class="selection-row"><span>${UNITS[t]?.label || BUILDINGS[t]?.label || t}</span><b>${n}</b></div>`).join(''));
    return;
  }

  if (first.entity === 'resource') {
    const title = first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : first.animal ? getAnimalLabel(first) : 'Meat';
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Resource</small><b>${title}</b></span>`);
    const hp = first.animal ? `<div class="selection-row"><span>Animal HP</span><b>${Math.max(0, Math.ceil(first.animalHp))}</b></div>` : '';
    this.setHudHtml(HUD.selectionBody, `${hp}<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`);
    return;
  }

  const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
  const owner = faction(first.faction);
  const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
  const range = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Tower range</span><b>${BUILDINGS.tower.range}</b></div>` : '';
  const extra = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Archers inside</span><b>${first.garrison.length}/${BUILDINGS.tower.garrisonCap}</b></div>` : '';
  const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div>` : '';
  const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
  const dragHint = first.entity === 'building' && first.faction === 0 ? `<div class="selection-row hint-row"><span>Move building</span><b>Drag it</b></div>` : '';
  this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>${owner.name}</small><b>${def.label}</b></span>`);
  this.setHudHtml(HUD.selectionBody, `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${range}${extra}${queue}${dragHint}`);
};

Game.prototype.renderActions = function() {
  HUD.actionBar.innerHTML = '';
  const own = this.selected.filter(e => e.faction === 0 && isAlive(e));
  if (!own.length) {
    HUD.actionDock && HUD.actionDock.classList.add('hidden');
    return;
  }
  const units = own.filter(e => e.entity === 'unit');
  const buildings = own.filter(e => e.entity === 'building');
  HUD.actionTitle.textContent = units.length ? 'Unit Commands' : 'Building Commands';

  if (units.length) {
    HUD.actionBar.appendChild(this.makeAction('', 'Build', 'Workers construct', 'iconBuild', () => this.toggleBuildMenu(), !units.some(u => u.type === 'worker')));
    HUD.actionBar.appendChild(this.makeAction('', 'Attack Move', 'Move toward pointer', 'iconAttack', () => { this.orderMoveFormation(units, this.pointer.wx, this.pointer.wy, true); }));
    HUD.actionBar.appendChild(this.makeAction('', 'Stop', 'Cancel orders', 'iconStop', () => this.stopSelected()));
    HUD.actionBar.appendChild(this.makeAction('', 'Hold', 'Defensive stance', 'iconRally', () => this.holdSelected()));
    const archers = units.filter(u => u.type === 'archer');
    const tower = this.nearestOwnTower(this.pointer.wx, this.pointer.wy, 260);
    HUD.actionBar.appendChild(this.makeAction('', 'Garrison', 'Nearest tower', 'iconGarrison', () => this.garrisonArchers(archers, tower), !archers.length || !tower));
  }

  if (buildings.length) {
    const trainSet = new Set();
    for (const b of buildings) if (b.build >= 1) BUILDINGS[b.type].trains.forEach(t => trainSet.add(t));
    let n = 1;
    for (const t of trainSet) {
      const d = UNITS[t];
      const pop = this.population(0);
      const disabled = !canAfford(this.factions[0], d.cost) || pop.used + d.pop > pop.cap;
      HUD.actionBar.appendChild(this.makeAction(String(n), `Train ${d.label}`, fmtCost(d.cost), d.icon, () => this.queueTrain(t), disabled));
      n++;
    }
    HUD.actionBar.appendChild(this.makeAction('', 'Rally Flag', 'Right click map', 'iconRally', () => this.toast('Right click the map to set rally flags.', 1.4)));
    HUD.actionBar.appendChild(this.makeAction('', 'Repair', '28W 18G', 'iconRepair', () => this.repairSelected()));
    if (buildings.some(b => b.type === 'tower' && b.garrison.length)) HUD.actionBar.appendChild(this.makeAction('', 'Ungarrison', 'Release archers', 'iconGarrison', () => this.ungarrisonSelected()));
  }
};

Game.prototype.makeAction = function(hotkey, title, sub, icon, fn, disabled = false) {
  const label = hotkey ? `<span class="keytag">${hotkey}</span> ${title}` : title;
  const b = this.makeButton({ icon, title: label, sub, onClick: fn, disabled });
  if (hotkey) b.dataset.hotkey = String(hotkey).toLowerCase();
  return b;
};

Game.prototype.nearestOwnTower = function(x, y, range) {
  let best = null, bd = range * range;
  for (const b of this.buildings) {
    if (b.faction !== 0 || b.type !== 'tower' || b.dead || b.garrison.length >= BUILDINGS.tower.garrisonCap) continue;
    const d = dist2(x, y, b.x, b.y);
    if (d < bd) { bd = d; best = b; }
  }
  return best;
};


// Pass 2: economy panel, formation commands, and no garrison UI.
Game.prototype.renderUI = function() {
  const f = this.factions[0];
  const pop = this.population(0);
  const resHtml = Object.keys(RESOURCES).map(k => {
    const r = RESOURCES[k];
    return `<div class="res-pill"><img src="${IMAGE_PATHS[r.icon]}" alt="${r.label}"><span><b>${Math.floor(f.res[k])}</b><small>${r.label}</small></span></div>`;
  }).join('') + `<div class="res-pill pop-pill"><img src="${IMAGE_PATHS.iconHouse}" alt="Population"><span><b>${pop.used}/${pop.cap}</b><small>Pop</small></span></div>`;
  this.setHudHtml(HUD.resources, resHtml);
  const enemiesAlive = this.factions.filter(x => x.id !== 0 && x.ai && x.alive).length;
  const worldName = this.worldRecord?.name || 'World';
  const stateHtml = `<span class="status-dot ${this.paused ? 'paused' : 'live'}"></span><span class="state-main">${this.paused ? 'Paused' : 'Live'}</span><span class="state-world">${worldName}</span><span class="state-rivals">${enemiesAlive} rivals</span><button class="mini-action" id="hudSaveBtn" title="Save world (Ctrl+S)">Save</button><button class="mini-action" id="hudExitBtn" title="Save and return to menu">Menu</button>`;
  this.setHudHtml(HUD.state, stateHtml);
  const saveBtn = document.getElementById('hudSaveBtn');
  const exitBtn = document.getElementById('hudExitBtn');
  if (saveBtn) saveBtn.onclick = () => this.saveToWorldRecord && this.saveToWorldRecord('manual');
  if (exitBtn) exitBtn.onclick = () => window.tinySwordsApp && window.tinySwordsApp.returnToMenu();
  this.renderEconomyPanel();
  this.renderSelectionPanel();
  this.renderActions();
};

Game.prototype.workerDistribution = function(fid) {
  const out = { wood: 0, gold: 0, food: 0, building: 0, idle: 0 };
  for (const u of this.units) {
    if (u.faction !== fid || u.type !== 'worker' || u.dead) continue;
    if (u.order === 'repair') out.building++;
    else if (u.order === 'harvest' && u.target) {
      if (u.target.type === 'tree' || (u.carry && u.carry.type === 'wood')) out.wood++;
      else if (u.target.type === 'gold' || (u.carry && u.carry.type === 'gold')) out.gold++;
      else out.food++;
    } else out.idle++;
  }
  return out;
};

Game.prototype.renderEconomyPanel = function() {
  if (!HUD.economy) return;
  const d = this.workerDistribution(0);
  const html = [
    ['wood', 'Wood', d.wood], ['gold', 'Gold', d.gold], ['food', 'Food', d.food], ['building', 'Build', d.building], ['idle', 'Idle', d.idle]
  ].map(([cls, label, n]) => `<div class="econ-chip ${cls}"><b>${n}</b><span>${label}</span></div>`).join('');
  this.setHudHtml(HUD.economy, html);
};

Game.prototype.renderSelectionPanel = function() {
  const s = this.selected.filter(isAlive);
  if (!s.length) { this.setHudHtml(HUD.selectionHeader, '<span class="panel-kicker">Selection</span><b>No selection</b>'); this.setHudHtml(HUD.selectionBody, '<div class="selection-hint">Drag units, click buildings, or press B to place structures.</div>'); return; }
  const first = s[0];
  const iconFor = (e) => {
    if (e.entity === 'resource') return e.type === 'tree' ? IMAGE_PATHS.resWood : e.type === 'gold' ? IMAGE_PATHS.resGold : IMAGE_PATHS.resFood;
    const def = e.entity === 'unit' ? UNITS[e.type] : BUILDINGS[e.type];
    return IMAGE_PATHS[def.icon] || IMAGE_PATHS.iconMove;
  };
  if (s.length > 1) {
    const groups = {};
    for (const e of s) groups[e.type] = (groups[e.type] || 0) + 1;
    const formation = s.some(e => e.entity === 'unit') ? `<span class="formation-tag">${FORMATION_MODES[this.formationMode || 'box'].label}</span>` : '';
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Group</small><b>${s.length} selected ${formation}</b></span>`);
    this.setHudHtml(HUD.selectionBody, Object.entries(groups).map(([t, n]) => `<div class="selection-row"><span>${UNITS[t]?.label || BUILDINGS[t]?.label || t}</span><b>${n}</b></div>`).join(''));
    return;
  }
  if (first.entity === 'resource') {
    const title = first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : first.animal ? getAnimalLabel(first) : 'Meat';
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Resource</small><b>${title}</b></span>`);
    const hp = first.animal ? `<div class="selection-row"><span>Animal HP</span><b>${Math.max(0, Math.ceil(first.animalHp))}</b></div>` : '';
    this.setHudHtml(HUD.selectionBody, `${hp}<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`);
    return;
  }
  const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
  const owner = faction(first.faction);
  const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
  const unitRange = first.entity === 'unit' ? `<div class="selection-row"><span>Range</span><b>${Math.round(UNITS[first.type].range)}</b></div>` : '';
  const tower = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Built-in archer</span><b>1 / 1</b></div><div class="selection-row"><span>Tower range</span><b>${BUILDINGS.tower.range}</b></div>` : '';
  const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div><div class="selection-row"><span>Builder</span><b>${this.hasActiveBuilder && this.hasActiveBuilder(first) ? 'Working' : 'Needs worker'}</b></div>` : '';
  const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
  const dragHint = first.entity === 'building' && first.faction === 0 ? `<div class="selection-row hint-row"><span>Move building</span><b>Drag it</b></div>` : '';
  this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>${owner.name}</small><b>${def.label}</b></span>`);
  this.setHudHtml(HUD.selectionBody, `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${unitRange}${tower}${queue}${dragHint}`);
};

Game.prototype.renderActions = function() {
  HUD.actionBar.innerHTML = '';
  const own = this.selected.filter(e => e.faction === 0 && isAlive(e));
  if (!own.length) { HUD.actionDock && HUD.actionDock.classList.add('hidden'); return; }
  const units = own.filter(e => e.entity === 'unit');
  const buildings = own.filter(e => e.entity === 'building');
  HUD.actionTitle.textContent = units.length ? `Unit Commands · ${FORMATION_MODES[this.formationMode || 'box'].label}` : 'Building Commands';
  if (units.length) {
    HUD.actionBar.appendChild(this.makeAction('', 'Build', 'Workers construct', 'iconBuild', () => this.toggleBuildMenu(), !units.some(u => u.type === 'worker')));
    HUD.actionBar.appendChild(this.makeAction('', 'Attack Move', 'Move toward pointer', 'iconAttack', () => { this.orderMoveFormation(units, this.pointer.wx, this.pointer.wy, true); }));
    HUD.actionBar.appendChild(this.makeAction('', 'Stop', 'Cancel orders', 'iconStop', () => this.stopSelected()));
    HUD.actionBar.appendChild(this.makeAction('', 'Hold', 'Defensive stance', 'iconRally', () => this.holdSelected()));
    HUD.actionBar.appendChild(this.makeAction('Z', 'Line', 'Wide front', 'iconRally', () => this.setFormationMode('line')));
    HUD.actionBar.appendChild(this.makeAction('X', 'Box', 'Compact', 'iconRally', () => this.setFormationMode('box')));
    HUD.actionBar.appendChild(this.makeAction('C', 'Wedge', 'Charge', 'iconRally', () => this.setFormationMode('wedge')));
    HUD.actionBar.appendChild(this.makeAction('V', 'Split', 'Archers back', 'iconRally', () => this.setFormationMode('split')));
  }
  if (buildings.length) {
    const trainSet = new Set();
    for (const b of buildings) if (b.build >= 1) BUILDINGS[b.type].trains.forEach(t => trainSet.add(t));
    let n = 1;
    for (const t of trainSet) {
      const d = UNITS[t]; const pop = this.population(0); const disabled = !canAfford(this.factions[0], d.cost) || pop.used + d.pop > pop.cap;
      HUD.actionBar.appendChild(this.makeAction(String(n), `Train ${d.label}`, fmtCost(d.cost), d.icon, () => this.queueTrain(t), disabled)); n++;
    }
    HUD.actionBar.appendChild(this.makeAction('', 'Rally Flag', 'Right click map', 'iconRally', () => this.toast('Right click the map to set rally flags.', 1.4)));
    HUD.actionBar.appendChild(this.makeAction('', 'Assign Workers', 'Build / repair', 'iconRepair', () => this.repairSelected()));
  }
};

Game.prototype.nearestOwnTower = function() { return null; };


// Pass 3: worker role dialog.
Game.prototype.toggleWorkerRoles = function(force) {
  if (!HUD.workerRoles) return;
  const open = force === undefined ? HUD.workerRoles.classList.contains('hidden') : !!force;
  this.workerRolesOpen = open;
  if (!this.workerRoleScope) this.workerRoleScope = 'selected';
  HUD.workerRoles.classList.toggle('hidden', !open);
  if (open) this.renderWorkerRolePanel();
};

Game.prototype.workerRoleCounts = function(fid) {
  const counts = { wood: 0, gold: 0, food: 0, build: 0, idle: 0, auto: 0 };
  for (const u of this.units) {
    if (u.faction !== fid || u.type !== 'worker' || u.dead) continue;
    const role = u.workerRole || (u.order === 'repair' ? 'build' : u.order === 'idle' ? 'idle' : 'auto');
    if (role === 'wood') counts.wood++;
    else if (role === 'gold') counts.gold++;
    else if (role === 'food') counts.food++;
    else if (role === 'build') counts.build++;
    else if (role === 'idle') counts.idle++;
    else counts.auto++;
  }
  return counts;
};

Game.prototype.renderWorkerRolePanel = function() {
  if (!HUD.workerRoles || !HUD.workerRoleBody) return;
  const selectedWorkers = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && e.type === 'worker' && !e.dead).length;
  const allWorkers = this.units.filter(u => u.faction === 0 && u.type === 'worker' && !u.dead).length;
  const scope = this.workerRoleScope || 'selected';
  const counts = this.workerRoleCounts(0);
  if (HUD.workerRoleTitle) HUD.workerRoleTitle.textContent = `Worker Roles · ${scope === 'all' ? allWorkers + ' all' : selectedWorkers + ' selected'}`;
  const scopeHtml = `
    <div class="worker-scope-row">
      <button class="worker-scope-btn ${scope === 'selected' ? 'active' : ''}" data-worker-scope="selected"><b>Selected</b><span>${selectedWorkers} worker(s)</span></button>
      <button class="worker-scope-btn ${scope === 'all' ? 'active' : ''}" data-worker-scope="all"><b>All workers</b><span>${allWorkers} available</span></button>
    </div>`;
  const countHtml = `
    <div class="worker-count-row">
      <div class="worker-count"><b>${counts.wood}</b><span>Wood</span></div>
      <div class="worker-count"><b>${counts.gold}</b><span>Gold</span></div>
      <div class="worker-count"><b>${counts.food}</b><span>Food</span></div>
      <div class="worker-count"><b>${counts.build}</b><span>Build</span></div>
      <div class="worker-count"><b>${counts.idle}</b><span>Idle</span></div>
    </div>`;
  const actionHtml = `
    <div class="worker-action-grid">
      <button class="worker-role-btn" data-worker-role="wood"><b>Wood</b><span>Chop nearest trees</span></button>
      <button class="worker-role-btn" data-worker-role="gold"><b>Gold</b><span>Mine nearest veins</span></button>
      <button class="worker-role-btn" data-worker-role="food"><b>Food</b><span>Hunt / gather food</span></button>
      <button class="worker-role-btn" data-worker-role="build"><b>Build / Repair</b><span>Finish foundations first</span></button>
      <button class="worker-role-btn" data-worker-role="auto"><b>Auto Balance</b><span>Fill urgent needs</span></button>
      <button class="worker-role-btn" data-worker-role="idle"><b>Idle</b><span>Stop current job</span></button>
    </div>`;
  this.setHudHtml(HUD.workerRoleBody, scopeHtml + countHtml + actionHtml);
};

const tinySwordsPass3RenderUI = Game.prototype.renderUI;
Game.prototype.renderUI = function() {
  tinySwordsPass3RenderUI.call(this);
  if (this.workerRolesOpen) this.renderWorkerRolePanel();
};

const tinySwordsPass3RenderActions = Game.prototype.renderActions;
Game.prototype.renderActions = function() {
  tinySwordsPass3RenderActions.call(this);
  const ownWorkers = this.selected.some(e => e.entity === 'unit' && e.faction === 0 && e.type === 'worker' && !e.dead);
  if (ownWorkers) {
    HUD.actionBar.appendChild(this.makeAction('E', 'Worker Roles', 'Assign economy jobs', 'iconWorker', () => this.toggleWorkerRoles(true)));
  }
};


// Pass 4: minimal in-game HUD, hidden empty panels, compact worker roles, and pause overlay.
Game.prototype.renderUI = function() {
  const f = this.factions[0];
  const pop = this.population(0);
  const resHtml = Object.keys(RESOURCES).map(k => {
    const r = RESOURCES[k];
    return `<div class="res-pill"><img src="${IMAGE_PATHS[r.icon]}" alt="${r.label}"><span><b>${Math.floor(f.res[k])}</b><small>${r.label}</small></span></div>`;
  }).join('') + `<div class="res-pill pop-pill"><img src="${IMAGE_PATHS.iconHouse}" alt="Population"><span><b>${pop.used}/${pop.cap}</b><small>Pop</small></span></div>`;
  this.setHudHtml(HUD.resources, resHtml);
  if (HUD.pauseOverlay) HUD.pauseOverlay.classList.toggle('hidden', !this.paused);
  this.renderSelectionPanel();
  this.renderActions();
  if (this.workerRolesOpen) this.renderWorkerRolePanel();
};

Game.prototype.renderSelectionPanel = function() {
  const panel = document.getElementById('selectionPanel');
  const s = this.selected.filter(isAlive);
  if (!s.length) {
    if (panel) panel.classList.add('hidden');
    this.setHudHtml(HUD.selectionHeader, '');
    this.setHudHtml(HUD.selectionBody, '');
    return;
  }
  if (panel) panel.classList.remove('hidden');

  const first = s[0];
  const iconFor = (e) => {
    if (e.entity === 'resource') return e.type === 'tree' ? IMAGE_PATHS.resWood : e.type === 'gold' ? IMAGE_PATHS.resGold : IMAGE_PATHS.resFood;
    const def = e.entity === 'unit' ? UNITS[e.type] : BUILDINGS[e.type];
    return IMAGE_PATHS[def.icon] || IMAGE_PATHS.iconMove;
  };

  if (s.length > 1) {
    const groups = {};
    for (const e of s) groups[e.type] = (groups[e.type] || 0) + 1;
    const formation = s.some(e => e.entity === 'unit') ? `<span class="formation-tag">${FORMATION_MODES[this.formationMode || 'box'].label}</span>` : '';
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Group</small><b>${s.length} selected ${formation}</b></span>`);
    this.setHudHtml(HUD.selectionBody, Object.entries(groups).map(([t, n]) => `<div class="selection-row"><span>${UNITS[t]?.label || BUILDINGS[t]?.label || t}</span><b>${n}</b></div>`).join(''));
    return;
  }

  if (first.entity === 'resource') {
    const title = first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : first.animal ? getAnimalLabel(first) : 'Meat';
    this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>Resource</small><b>${title}</b></span>`);
    const hp = first.animal ? `<div class="selection-row"><span>Animal HP</span><b>${Math.max(0, Math.ceil(first.animalHp))}</b></div>` : '';
    this.setHudHtml(HUD.selectionBody, `${hp}<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`);
    return;
  }

  const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
  const owner = faction(first.faction);
  const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
  const unitRange = first.entity === 'unit' ? `<div class="selection-row"><span>Range</span><b>${Math.round(UNITS[first.type].range)}</b></div>` : '';
  const tower = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Built-in archer</span><b>1 / 1</b></div><div class="selection-row"><span>Tower range</span><b>${BUILDINGS.tower.range}</b></div>` : '';
  const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div><div class="selection-row"><span>Builder</span><b>${this.hasActiveBuilder && this.hasActiveBuilder(first) ? 'Working' : 'Needs worker'}</b></div>` : '';
  const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
  const dragHint = first.entity === 'building' && first.faction === 0 ? `<div class="selection-row hint-row"><span>Move building</span><b>Drag</b></div>` : '';
  const role = first.entity === 'unit' && first.type === 'worker' ? `<div class="selection-row"><span>Role</span><b>${first.workerRole || 'auto'}</b></div>` : '';
  this.setHudHtml(HUD.selectionHeader, `<img src="${iconFor(first)}" alt=""><span><small>${owner.name}</small><b>${def.label}</b></span>`);
  this.setHudHtml(HUD.selectionBody, `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${role}${unitRange}${tower}${queue}${dragHint}`);
};

Game.prototype.renderActions = function() {
  const dock = document.getElementById('actionDock');
  if (!HUD.actionBar || !HUD.actionTitle) return;
  HUD.actionBar.innerHTML = '';
  const own = this.selected.filter(e => e.faction === 0 && isAlive(e));
  if (!own.length) {
    if (dock) dock.classList.add('hidden');
    HUD.actionTitle.textContent = '';
    return;
  }
  if (dock) dock.classList.remove('hidden');

  const units = own.filter(e => e.entity === 'unit');
  const buildings = own.filter(e => e.entity === 'building');
  HUD.actionTitle.textContent = units.length ? `Unit Commands · ${FORMATION_MODES[this.formationMode || 'box'].label}` : 'Building Commands';

  if (units.length) {
    const workers = units.filter(u => u.type === 'worker');
    HUD.actionBar.appendChild(this.makeAction('', 'Build', 'Open build menu', 'iconBuild', () => this.toggleBuildMenu(), !workers.length));
    if (workers.length) HUD.actionBar.appendChild(this.makeAction('E', 'Worker Roles', 'Economy jobs', 'iconWorker', () => this.toggleWorkerRoles(true)));
    HUD.actionBar.appendChild(this.makeAction('', 'Attack Move', 'Fight along path', 'iconAttack', () => { this.orderMoveFormation(units, this.pointer.wx, this.pointer.wy, true); }));
    HUD.actionBar.appendChild(this.makeAction('', 'Stop', 'Cancel orders', 'iconStop', () => this.stopSelected()));
    HUD.actionBar.appendChild(this.makeAction('', 'Hold', 'Defensive stance', 'iconRally', () => this.holdSelected()));
    HUD.actionBar.appendChild(this.makeAction('Z', 'Line', 'Wide front', 'iconRally', () => this.setFormationMode('line')));
    HUD.actionBar.appendChild(this.makeAction('X', 'Box', 'Compact', 'iconRally', () => this.setFormationMode('box')));
    HUD.actionBar.appendChild(this.makeAction('C', 'Wedge', 'Charge', 'iconRally', () => this.setFormationMode('wedge')));
    HUD.actionBar.appendChild(this.makeAction('V', 'Split', 'Archers back', 'iconRally', () => this.setFormationMode('split')));
  }

  if (buildings.length) {
    const trainSet = new Set();
    for (const b of buildings) if (b.build >= 1) BUILDINGS[b.type].trains.forEach(t => trainSet.add(t));
    let n = 1;
    for (const t of trainSet) {
      const d = UNITS[t];
      const pop = this.population(0);
      const disabled = !canAfford(this.factions[0], d.cost) || pop.used + d.pop > pop.cap;
      HUD.actionBar.appendChild(this.makeAction(String(n), `Train ${d.label}`, fmtCost(d.cost), d.icon, () => this.queueTrain(t), disabled));
      n++;
    }
    HUD.actionBar.appendChild(this.makeAction('', 'Rally Flag', 'Right click map', 'iconRally', () => this.toast('Right click the map to set rally flags.', 1.4)));
    HUD.actionBar.appendChild(this.makeAction('', 'Assign Workers', 'Build / repair', 'iconRepair', () => this.repairSelected()));
  }
};

Game.prototype.renderWorkerRolePanel = function() {
  if (!HUD.workerRoles || !HUD.workerRoleBody) return;
  const selectedWorkers = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && e.type === 'worker' && !e.dead).length;
  const allWorkers = this.units.filter(u => u.faction === 0 && u.type === 'worker' && !u.dead).length;
  const scope = this.workerRoleScope || 'selected';
  const counts = this.workerRoleCounts(0);
  if (HUD.workerRoleTitle) HUD.workerRoleTitle.textContent = `Workers · ${scope === 'all' ? allWorkers + ' all' : selectedWorkers + ' selected'}`;
  const scopeHtml = `
    <div class="worker-scope-row">
      <button class="worker-scope-btn ${scope === 'selected' ? 'active' : ''}" data-worker-scope="selected"><b>Selected</b><span>${selectedWorkers}</span></button>
      <button class="worker-scope-btn ${scope === 'all' ? 'active' : ''}" data-worker-scope="all"><b>All</b><span>${allWorkers}</span></button>
    </div>`;
  const countHtml = `
    <div class="worker-count-row">
      <div class="worker-count"><b>${counts.wood}</b><span>Wood</span></div>
      <div class="worker-count"><b>${counts.gold}</b><span>Gold</span></div>
      <div class="worker-count"><b>${counts.food}</b><span>Food</span></div>
      <div class="worker-count"><b>${counts.build}</b><span>Build</span></div>
      <div class="worker-count"><b>${counts.idle}</b><span>Idle</span></div>
    </div>`;
  const actionHtml = `
    <div class="worker-action-grid">
      <button class="worker-role-btn" data-worker-role="wood"><b>Wood</b><span>Chop</span></button>
      <button class="worker-role-btn" data-worker-role="gold"><b>Gold</b><span>Mine</span></button>
      <button class="worker-role-btn" data-worker-role="food"><b>Food</b><span>Hunt</span></button>
      <button class="worker-role-btn" data-worker-role="build"><b>Build</b><span>Repair</span></button>
      <button class="worker-role-btn" data-worker-role="auto"><b>Auto</b><span>Balance</span></button>
      <button class="worker-role-btn" data-worker-role="idle"><b>Idle</b><span>Stop</span></button>
    </div>`;
  this.setHudHtml(HUD.workerRoleBody, scopeHtml + countHtml + actionHtml);
};

Game.prototype.renderEconomyPanel = function() {};
