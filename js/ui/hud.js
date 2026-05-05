// HUD rendering and command buttons.
'use strict';

let lastResourceHtml = '';
let lastPopUsed = -1;
let lastPopCap = -1;

Game.prototype.setHudHtml = function(el, html) {
  if (el && el.innerHTML !== html) el.innerHTML = html;
};

Game.prototype.renderUI = function() {
  const player = this.factions[0];
  const pop = this.population(0);
  const resourceHtml = Object.keys(RESOURCES).map(key => {
    const resource = RESOURCES[key];
    return `<div class="res-pill"><img src="${IMAGE_PATHS[resource.icon]}" class="sprite-icon" alt="${resource.label}"><span><b>${Math.floor(player.res[key])}</b><small>${resource.label}</small></span></div>`;
  }).join('') + `<div class="res-pill pop-pill"><img src="${IMAGE_PATHS.iconHouse}" alt="Population"><span><b>${pop.used}/${pop.cap}</b><small>Pop</small></span></div>`;

  if (resourceHtml !== lastResourceHtml || pop.used !== lastPopUsed || pop.cap !== lastPopCap) {
    lastResourceHtml = resourceHtml;
    lastPopUsed = pop.used;
    lastPopCap = pop.cap;
    this.setHudHtml(HUD.resources, resourceHtml);
  }

  const enemiesAlive = this.factions.filter(f => f.id !== 0 && f.ai && f.alive).length;
  const worldName = this.worldRecord?.name || 'World';
  const stateHtml = `<span class="status-dot ${this.paused ? 'paused' : 'live'}"></span><span class="state-main">${this.paused ? 'Paused' : 'Live'}</span><span class="state-world">${worldName}</span><span class="state-rivals">${enemiesAlive} rivals</span><button class="mini-action" id="hudSaveBtn" title="Save world (Ctrl+S)">Save</button><button class="mini-action" id="hudExitBtn" title="Save and return to menu">Menu</button>`;
  this.setHudHtml(HUD.state, stateHtml);

  const saveButton = document.getElementById('hudSaveBtn');
  const exitButton = document.getElementById('hudExitBtn');
  if (saveButton) saveButton.onclick = () => this.saveToWorldRecord && this.saveToWorldRecord('manual');
  if (exitButton) exitButton.onclick = () => window.tinySwordsApp && window.tinySwordsApp.returnToMenu();
  if (HUD.pauseOverlay) HUD.pauseOverlay.classList.toggle('hidden', !this.paused);

  this.renderSelectionPanel();
  this.renderActions();
  if (this.workerRolesOpen) this.renderWorkerRolePanel();
};

function selectionIconPath(entity) {
  if (entity.entity === 'resource') return entity.type === 'tree' ? IMAGE_PATHS.resWood : entity.type === 'gold' ? IMAGE_PATHS.resGold : IMAGE_PATHS.resFood;
  if (entity.entity === 'decor' || (!entity.entity && entity.kind)) return IMAGE_PATHS.iconMove;
  const def = entity.entity === 'unit' ? UNITS[entity.type] : BUILDINGS[entity.type];
  return IMAGE_PATHS[def.icon] || IMAGE_PATHS.iconMove;
}

function spriteIconClass(entity) {
  return entity && (entity.entity === 'resource' || entity.entity === 'unit') ? 'sprite-icon' : '';
}

Game.prototype.renderSelectionPanel = function() {
  const panel = document.getElementById('selectionPanel');
  const selection = this.selected.filter(isAlive);

  if (!selection.length) {
    if (panel) panel.classList.add('hidden');
    this.setHudHtml(HUD.selectionHeader, '');
    this.setHudHtml(HUD.selectionBody, '');
    return;
  }
  if (panel) panel.classList.remove('hidden');

  const first = selection[0];
  if (selection.length > 1) {
    const groups = {};
    for (const entity of selection) groups[entity.type] = (groups[entity.type] || 0) + 1;
    const rows = Object.entries(groups).map(([type, count]) => `<div class="selection-row"><span>${UNITS[type]?.label || BUILDINGS[type]?.label || type}</span><b>${count}</b></div>`).join('');
    this.setHudHtml(HUD.selectionHeader, `<div class="selection-icon-wrap"><img src="${selectionIconPath(first)}" class="${spriteIconClass(first)}" alt=""></div><span><small>Group</small><b>${selection.length} selected</b></span>`);
    this.setHudHtml(HUD.selectionBody, rows);
    return;
  }

  if (first.entity === 'resource') {
    const title = first.depleted ? 'Tree Stump' : first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : first.animal ? getAnimalLabel(first) : 'Meat';
    const hp = first.animal ? `<div class="selection-row"><span>Animal HP</span><b>${Math.max(0, Math.ceil(first.animalHp))}</b></div>` : '';
    const remaining = first.depleted ? '<div class="selection-row"><span>Status</span><b>Depleted</b></div>' : `<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`;
    this.setHudHtml(HUD.selectionHeader, `<div class="selection-icon-wrap"><img src="${selectionIconPath(first)}" class="sprite-icon" alt=""></div><span><small>Resource</small><b>${title}</b></span>`);
    this.setHudHtml(HUD.selectionBody, `${hp}${remaining}`);
    return;
  }

  if (first.entity === 'decor' || (!first.entity && first.kind)) {
    const labels = { bush1: 'Bush', bush2: 'Bush', bush3: 'Bush', bush4: 'Bush', rock1: 'Rock', rock2: 'Rock', rock3: 'Rock', rock4: 'Rock' };
    const label = labels[first.kind] || first.kind;
    this.setHudHtml(HUD.selectionHeader, `<div class="selection-icon-wrap"><img src="${IMAGE_PATHS.iconMove}" alt=""></div><span><small>Scenery</small><b>${label}</b></span>`);
    this.setHudHtml(HUD.selectionBody, '<div class="selection-row"><span>Decoration</span><b>Impassable</b></div>');
    return;
  }

  const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
  const owner = faction(first.faction);
  const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
  const unitRange = first.entity === 'unit' ? `<div class="selection-row"><span>Range</span><b>${Math.round(unitCombatRange(this, first))}</b></div>` : '';
  const role = first.entity === 'unit' && first.type === 'worker' ? `<div class="selection-row"><span>Role</span><b>${first.workerRole || 'auto'}</b></div>` : '';
  const maxLevel = first.entity === 'building' ? buildingUpgradeMaxLevel(first.type) : 1;
  const level = first.entity === 'building' && maxLevel > 1 ? `<div class="selection-row"><span>Level</span><b>${buildingLevel(first)} / ${maxLevel}</b></div>` : '';
  const archerCount = first.entity === 'building' ? defensiveArcherCount(first) : 0;
  const defense = first.entity === 'building' && archerCount > 0 ? `<div class="selection-row"><span>Defensive archers</span><b>${archerCount}</b></div><div class="selection-row"><span>Range</span><b>${Math.round(defensiveBuildingRange(first))}</b></div>` : '';
  const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div><div class="selection-row"><span>Builder</span><b>${this.hasActiveBuilder && this.hasActiveBuilder(first) ? 'Working' : 'Needs worker'}</b></div>` : '';
  const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
  const dragHint = first.entity === 'building' && first.faction === 0 ? '<div class="selection-row hint-row"><span>Move building</span><b>Drag</b></div>' : '';

  this.setHudHtml(HUD.selectionHeader, `<div class="selection-icon-wrap"><img src="${selectionIconPath(first)}" class="${spriteIconClass(first)}" alt=""></div><span><small>${owner.name}</small><b>${def.label}</b></span>`);
  this.setHudHtml(HUD.selectionBody, `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${role}${level}${unitRange}${defense}${queue}${dragHint}`);
};

Game.prototype.renderActions = function() {
  const dock = document.getElementById('actionDock');
  if (!HUD.actionBar || !HUD.actionTitle) return;
  HUD.actionBar.innerHTML = '';

  const own = this.selected.filter(entity => entity.faction === 0 && isAlive(entity));
  const resources = this.selected.filter(entity => entity.entity === 'resource' && isAlive(entity));
  if (!own.length && !resources.length) {
    if (dock) dock.classList.add('hidden');
    HUD.actionTitle.textContent = '';
    return;
  }
  if (dock) dock.classList.remove('hidden');

  const units = own.filter(entity => entity.entity === 'unit');
  const buildings = own.filter(entity => entity.entity === 'building');
  HUD.actionTitle.textContent = units.length ? `Unit Commands · ${FORMATION_MODES[this.formationMode || 'box'].label}` : 'Building Commands';

  if (units.length) {
    const workers = units.filter(unit => unit.type === 'worker');
    HUD.actionBar.appendChild(this.makeAction('', 'Build', 'Open build menu', 'iconBuild', () => this.toggleBuildMenu(), !workers.length));
    if (workers.length) HUD.actionBar.appendChild(this.makeAction('E', 'Worker Roles', 'Economy jobs', 'iconWorker', () => this.toggleWorkerRoles(true)));
    HUD.actionBar.appendChild(this.makeAction('', 'Attack Move', 'Fight along path', 'iconAttack', () => this.orderMoveFormation(units, this.pointer.wx, this.pointer.wy, true)));
    HUD.actionBar.appendChild(this.makeAction('', 'Stop', 'Cancel orders', 'iconStop', () => this.stopSelected()));
    HUD.actionBar.appendChild(this.makeAction('', 'Hold', 'Defensive stance', 'iconRally', () => this.holdSelected()));
    HUD.actionBar.appendChild(this.makeAction('Z', 'Line', 'Wide front', 'iconRally', () => this.setFormationMode('line')));
    HUD.actionBar.appendChild(this.makeAction('X', 'Box', 'Compact', 'iconRally', () => this.setFormationMode('box')));
    HUD.actionBar.appendChild(this.makeAction('C', 'Wedge', 'Charge', 'iconRally', () => this.setFormationMode('wedge')));
    HUD.actionBar.appendChild(this.makeAction('V', 'Split', 'Archers back', 'iconRally', () => this.setFormationMode('split')));
  }

  if (buildings.length) {
    const trainSet = new Set();
    for (const building of buildings) if (building.build >= 1) BUILDINGS[building.type].trains.forEach(unitType => trainSet.add(unitType));
    let hotkey = 1;
    for (const unitType of trainSet) {
      const unitDef = UNITS[unitType];
      const pop = this.population(0);
      const disabled = !canAfford(this.factions[0], unitDef.cost) || pop.used + unitDef.pop > pop.cap;
      HUD.actionBar.appendChild(this.makeAction(String(hotkey), `Train ${unitDef.label}`, fmtCost(unitDef.cost), unitDef.icon, () => this.queueTrain(unitType), disabled));
      hotkey++;
    }
    if (trainSet.size > 0) HUD.actionBar.appendChild(this.makeAction('', 'Rally Flag', 'Right click map', 'iconRally', () => this.toast('Right click the map to set rally flags.', 1.4)));

    const upgradable = buildings.find(building => building.build >= 1 && buildingUpgradeCost(building));
    if (upgradable) {
      const cost = buildingUpgradeCost(upgradable);
      const disabled = !canAfford(this.factions[0], cost);
      HUD.actionBar.appendChild(this.makeAction('U', `Upgrade Lv.${buildingLevel(upgradable) + 1}`, fmtCost(cost), 'iconUpgrade', () => upgradeBuildingForPlayer(this, upgradable), disabled));
    }
    HUD.actionBar.appendChild(this.makeAction('', 'Assign Workers', 'Build / repair', 'iconRepair', () => this.repairSelected()));
  }

  if (!own.length && resources.length) {
    if (dock) dock.classList.remove('hidden');
    HUD.actionTitle.textContent = 'Resource Commands';
    const resource = resources[0];
    if (resource.type === 'tree') HUD.actionBar.appendChild(this.makeAction('', 'Chop', 'Send worker', 'resWood', () => this.assignWorkerToResource(resource)));
    if (resource.type === 'gold') HUD.actionBar.appendChild(this.makeAction('', 'Mine', 'Send worker', 'resGold', () => this.assignWorkerToResource(resource)));
    if (resource.type === 'food') HUD.actionBar.appendChild(this.makeAction('', resource.animal ? 'Hunt' : 'Gather', 'Send worker', 'resFood', () => this.assignWorkerToResource(resource)));
  }
};

Game.prototype.renderWorkerRolePanel = function() {
  if (!HUD.workerRoles || !HUD.workerRoleBody) return;
  const selectedWorkers = this.selected.filter(entity => entity.entity === 'unit' && entity.faction === 0 && entity.type === 'worker' && !entity.dead).length;
  const allWorkers = this.units.filter(unit => unit.faction === 0 && unit.type === 'worker' && !unit.dead).length;
  const scope = this.workerRoleScope || 'selected';
  const counts = this.workerRoleCounts(0);
  if (HUD.workerRoleTitle) HUD.workerRoleTitle.textContent = `Workers · ${scope === 'all' ? allWorkers + ' all' : selectedWorkers + ' selected'}`;

  const scopeHtml = `<div class="worker-scope-row"><button class="worker-scope-btn ${scope === 'selected' ? 'active' : ''}" data-worker-scope="selected"><b>Selected</b><span>${selectedWorkers}</span></button><button class="worker-scope-btn ${scope === 'all' ? 'active' : ''}" data-worker-scope="all"><b>All</b><span>${allWorkers}</span></button></div>`;
  const countHtml = `<div class="worker-count-row"><div class="worker-count"><b>${counts.wood}</b><span>Wood</span></div><div class="worker-count"><b>${counts.gold}</b><span>Gold</span></div><div class="worker-count"><b>${counts.food}</b><span>Food</span></div><div class="worker-count"><b>${counts.build}</b><span>Build</span></div><div class="worker-count"><b>${counts.idle}</b><span>Idle</span></div></div>`;
  const actionHtml = `<div class="worker-action-grid"><button class="worker-role-btn" data-worker-role="wood"><b>Wood</b><span>Chop</span></button><button class="worker-role-btn" data-worker-role="gold"><b>Gold</b><span>Mine</span></button><button class="worker-role-btn" data-worker-role="food"><b>Food</b><span>Hunt</span></button><button class="worker-role-btn" data-worker-role="build"><b>Build</b><span>Repair</span></button><button class="worker-role-btn" data-worker-role="auto"><b>Auto</b><span>Balance</span></button><button class="worker-role-btn" data-worker-role="idle"><b>Idle</b><span>Stop</span></button></div>`;
  this.setHudHtml(HUD.workerRoleBody, scopeHtml + countHtml + actionHtml);
};

Game.prototype.renderEconomyPanel = function() {};
