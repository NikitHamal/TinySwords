// HUD rendering and command buttons.
Game.prototype.renderUI = function() {
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
  
};

Game.prototype.renderSelectionPanel = function() {
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
  
};

Game.prototype.renderActions = function() {
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
  
};

Game.prototype.makeAction = function(hotkey, title, sub, icon, fn, disabled = false) {
    const b = this.makeButton({ icon, title: `<span class="keytag">${hotkey}</span> ${title}`, sub, onClick: fn, disabled });
    b.dataset.hotkey = String(hotkey).toLowerCase();
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
