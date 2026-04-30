// Production pass overrides: hunting, land validation, smarter AI, safer pathing, compact HUD details.
Game.prototype.canPlace = function(type, x, y) {
  const def = BUILDINGS[type];
  if (x < 120 || y < 120 || x > WORLD_W - 120 || y > WORLD_H - 120) return false;
  const pads = [[0,0],[-def.w*.42,-def.h*.30],[def.w*.42,-def.h*.30],[-def.w*.42,def.h*.28],[def.w*.42,def.h*.28]];
  if (!pads.every(([ox, oy]) => this.isSafeLand(x + ox, y + oy, 20))) return false;
  const rect = { x: x - def.w / 2 - 14, y: y - def.h / 2 - 22, w: def.w + 28, h: def.h + 40 };
  for (const b of this.buildings) if (!b.dead && rectsOverlap(rect, { x: b.x - b.w / 2, y: b.y - b.h / 2, w: b.w, h: b.h })) return false;
  for (const r of this.resources) if (!r.dead && r.amount > 0 && rectsOverlap(rect, { x: r.x - r.r, y: r.y - r.r, w: r.r * 2, h: r.r * 2 })) return false;
  return true;
};

Game.prototype.orderMoveFormation = function(units, x, y, attackMove) {
  const land = this.nearestLandPoint(x, y, 320) || { x, y };
  const n = units.length;
  const cols = Math.ceil(Math.sqrt(n));
  const spacing = 44;
  units.forEach((u, i) => {
    const ox = ((i % cols) - (cols - 1) / 2) * spacing;
    const oy = (Math.floor(i / cols) - Math.floor(n / cols) / 2) * spacing;
    const p = this.nearestLandPoint(clamp(land.x + ox, 30, WORLD_W - 30), clamp(land.y + oy, 30, WORLD_H - 30), 150) || land;
    u.goal = { x: p.x, y: p.y };
    u.order = attackMove ? 'attackMove' : 'move';
    u.target = null; u.attackMove = attackMove; u.hold = false;
  });
  this.effects.push({ kind: attackMove ? 'attack' : 'move', x: land.x, y: land.y, time: .7, max: .7 });
};

Game.prototype.isBlocked = function(x, y, u) {
  if (this.isWater(x, y)) return true;
  const r = u ? u.r || 8 : 8;
  const rect = { x: x - r, y: y - r, w: r * 2, h: r * 2 };
  
  for (const b of this.buildings) {
    if (b.dead || b.build < 0.1) continue;
    const brect = { x: b.x - b.w / 2, y: b.y - b.h / 2, w: b.w, h: b.h };
    if (rectsOverlap(rect, brect)) return true;
  }
  
  for (const res of this.resources) {
    if (res.dead || res.amount <= 0 || res.animal) continue;
    const resRect = { x: res.x - res.r * 0.7, y: res.y - res.r * 0.7, w: res.r * 1.4, h: res.r * 1.4 };
    if (rectsOverlap(rect, resRect) && (!u || u.target !== res)) return true;
  }
  
  for (const d of this.decor) {
    if (d.sky || d.water) continue;
    const dr = d.kind.startsWith('bush') ? 16 : 12;
    const dRect = { x: d.x - dr, y: d.y - dr, w: dr * 2, h: dr * 2 };
    if (rectsOverlap(rect, dRect)) return true;
  }
  
  return false;
};

Game.prototype.moveToward = function(u, x, y, dt, stop = 6) {
  const dx = x - u.x, dy = y - u.y;
  const d = Math.hypot(dx, dy);
  if (d <= stop) return true;
  const sp = u.speed * dt * (u.carry ? .77 : 1);
  const step = Math.min(sp, Math.max(0, d - stop));
  let nx = u.x + dx / d * step;
  let ny = u.y + dy / d * step;

  if (this.isBlocked(nx, ny, u)) {
    const angle = Math.atan2(dy, dx);
    let found = false;
    const bias = (u.pathProbe || 0) % 2 ? -1 : 1;
    for (const turn of [Math.PI / 6 * bias, -Math.PI / 6 * bias, Math.PI / 3 * bias, -Math.PI / 3 * bias, Math.PI / 2, -Math.PI / 2, Math.PI * .82, -Math.PI * .82]) {
      const a = angle + turn;
      const tx = u.x + Math.cos(a) * step;
      const ty = u.y + Math.sin(a) * step;
      if (!this.isBlocked(tx, ty, u)) { nx = tx; ny = ty; found = true; break; }
    }
    if (!found) {
      u.stuck = (u.stuck || 0) + dt;
      if (u.lastWaterBounce <= 0) {
        this.effects.push({ kind: 'splash', x: u.x, y: u.y + 10, time: .42, max: .42 });
        u.lastWaterBounce = .65;
      }
      if (u.stuck > .55) { u.pathProbe = (u.pathProbe || 0) + 1; this.nudgeUnitToLand(u); }
      return false;
    }
  } else u.stuck = 0;

  u.x = nx;
  u.y = ny;
  u.face = dx >= 0 ? 1 : -1;
  return false;
};

Game.prototype.updateResources = function(dt) {
  for (const r of this.resources) {
    if (r.dead || r.type !== 'food' || r.amount <= 0) continue;
    if (!r.animal) continue;
    r.panic = Math.max(0, (r.panic || 0) - dt);
    r.wander -= dt;
    if (r.wander <= 0) {
      r.wander = r.panic > 0 ? .45 + Math.random() * .8 : 1.4 + Math.random() * 3.2;
      const a = Math.random() * Math.PI * 2;
      const sp = r.panic > 0 ? 34 + Math.random() * 28 : 9 + Math.random() * 18;
      r.vx = Math.cos(a) * sp;
      r.vy = Math.sin(a) * sp;
    }
    const nx = r.x + r.vx * dt;
    const ny = r.y + r.vy * dt;
    if (!this.isWater(nx, ny) && !this.occupiedByBase(nx, ny, 90)) { r.x = nx; r.y = ny; }
    else { r.vx *= -0.65; r.vy *= -0.65; r.wander = .25; }
    r.vx *= r.panic > 0 ? .992 : .985;
    r.vy *= r.panic > 0 ? .992 : .985;
  }
};

Game.prototype.updateUnits = function(dt) {
  for (const u of this.units) {
    if (u.dead || u.garrisoned) continue;
    u.flash = Math.max(0, u.flash - dt * 4);
    u.cd = Math.max(0, u.cd - dt);
    u.huntSwing = Math.max(0, (u.huntSwing || 0) - dt);
    u.lastWaterBounce = Math.max(0, (u.lastWaterBounce || 0) - dt);
    const activeMove = u.order === 'move' || u.order === 'attackMove' || u.order === 'garrison' || (u.order === 'harvest' && !u.gather && !u.huntSwing);
    u.anim += dt * (activeMove ? 8 : u.order === 'attack' || u.huntSwing > 0 ? 8 : 4);
    if (u.order === 'garrison') this.updateGarrisonUnit(u, dt);
    else if (u.type === 'monk') this.updateMonk(u, dt);
    else if (u.type === 'worker') this.updateWorker(u, dt);
    else this.updateFighter(u, dt);
    this.separate(u, dt);
    if (this.isWater(u.x, u.y)) this.nudgeUnitToLand(u);
    u.x = clamp(u.x, 20, WORLD_W - 20); u.y = clamp(u.y, 20, WORLD_H - 20);
  }
};

Game.prototype.updateWorker = function(u, dt) {
    if (u.order === 'repair') {
      const b = u.target;
      if (!b || b.dead || (b.build >= 1 && b.hp >= b.maxHp)) { u.order = 'idle'; u.target = null; return; }
      if (this.moveToward(u, b.x, b.y, dt, Math.hypot(b.w/2, b.h/2) + u.r + 4)) {
        u.face = b.x >= u.x ? 1 : -1;
        if (Math.random() < dt * 2) this.effects.push({ kind: 'dust', x: u.x + (Math.random() - .5) * 10, y: u.y, time: .3, max: .3 });
      
      if (b.build < 1) {
        b.build = Math.min(1, b.build + dt / b.buildTime * 1.5);
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * 1.35);
        if (b.build >= 1) { 
          if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(); }
          u.order = 'idle'; 
          if (b.type === 'tower') {
            const archer = this.addUnit(b.faction, 'archer', b.x, b.y);
            this.finishGarrison(archer, b);
          }
        }
      } else if (b.hp < b.maxHp) {
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.05);
        if (b.hp >= b.maxHp) { u.order = 'idle'; this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.4); }
      }
    }
    return;
  }

  if (u.order === 'harvest') {
    const res = u.target;
    if (!res || res.dead || res.amount <= 0) { u.carry = null; u.order = 'idle'; u.target = null; u.gather = 0; return; }
    if (u.carry) {
      const drop = this.nearestDropoff(u.faction, u.x, u.y);
      if (!drop) { u.order = 'idle'; return; }
      if (this.moveToward(u, drop.x, drop.y, dt, Math.hypot(drop.w/2, drop.h/2) + u.r + 4)) {
        addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
        u.carry = null; u.gather = 0;
        if (res && !res.dead && res.amount > 0) u.order = 'harvest';
      }
      return;
    }

    if (res.type === 'food' && res.animal) {
      const strikeRange = res.r + 6;
      this.moveToward(u, res.x, res.y, dt, strikeRange);
      if (dist2(u.x, u.y, res.x, res.y) <= (strikeRange + 22) * (strikeRange + 22)) {
        u.face = res.x >= u.x ? 1 : -1;
        u.gather += dt;
        if (u.gather >= .55) {
          u.gather = 0;
          u.huntSwing = .42;
          this.strikeAnimal(u, res);
        }
      } else u.gather = 0;
      return;
    }

    this.moveToward(u, res.x, res.y, dt, res.r + 6);
    if (dist2(u.x, u.y, res.x, res.y) <= (res.r + 26) * (res.r + 26)) {
      u.gather += dt;
      u.face = res.x >= u.x ? 1 : -1;
      const gatherTime = res.type === 'tree' ? 1.35 : res.type === 'gold' ? 1.6 : .82;
      if (u.gather >= gatherTime) {
        const amount = res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14;
        res.amount -= amount;
        u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
        u.gather = 0;
        if (res.amount <= 0) {
          if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; }
          else res.dead = true;
          this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
        }
      }
    } else {
      u.gather = 0;
    }
    return;
  }
  this.updateFighter(u, dt);
  if (u.order === 'idle' && this.factions[u.faction].ai) this.autoGather(u);
};

Game.prototype.strikeAnimal = function(u, res) {
  if (!res || res.dead || !res.animal) return;
  res.animalHp -= 11;
  res.panic = 2.4;
  res.flash = 1;
  const dx = res.x - u.x, dy = res.y - u.y;
  const d = Math.hypot(dx, dy) || 1;
  res.vx += dx / d * 58;
  res.vy += dy / d * 58;
  this.effects.push({ kind: 'hit', x: res.x, y: res.y - 18, time: .18, max: .18 });
  if (res.animalHp <= 0) {
    res.dead = true;
    res.amount = 0;
    u.carry = { type: 'food', amount: 14 };
    u.gather = 0;
    this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
  }
};

Game.prototype.convertAnimalToMeat = function(res) {
  // Deprecated: Workers now carry the meat immediately when the sheep dies
};

Game.prototype.attackTarget = function(u, target) {
  const def = UNITS[u.type];
  if (u.cd > 0) return;
  u.face = target.x >= u.x ? 1 : -1;
  u.cd = def.cd;
  if (u.type === 'archer') this.spawnProjectile(u.faction, u.x, u.y - 34, target, def.damage);
  else this.damage(target, def.damage, u.faction);
};

Game.prototype.updateBuildings = function(dt) {
  for (const b of this.buildings) {
    if (b.dead) continue;
    b.flash = Math.max(0, b.flash - dt * 3);
    if (b.build < 1) {
      b.build = Math.min(1, b.build + dt / b.buildTime);
      b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * .9);
      if (b.build >= 1) {
        if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} completed.`, 1.4); this.sfx.build(); }
        if (b.type === 'tower') {
          const u = this.addUnit(b.faction, 'archer', b.x, b.y);
          this.finishGarrison(u, b);
        }
      }
      continue;
    }
    if (b.queue.length) {
      const q = b.queue[0]; q.time -= dt;
      if (q.time <= 0) {
        b.queue.shift();
        const spawn = this.nearestLandPoint(b.x + (Math.random() - .5) * 70, b.y + b.h * .50 + 32, 180) || { x: b.x, y: b.y + b.h * .50 + 32 };
        const u = this.addUnit(b.faction, q.type, spawn.x, spawn.y);
        if (b.rally) this.orderMoveFormation([u], b.rally.x, b.rally.y, false);
        if (b.faction === 0) this.uiDirty = true;
      }
    }
    if (b.type === 'tower' && b.garrison.length) this.towerAttack(b, dt);
    if (b.type === 'monastery') this.passiveHeal(b, dt);
  }
};

Game.prototype.updateAI = function(dt) {
  for (const f of this.factions) {
    if (!f.ai || !f.alive) continue;
    f.underAttack = Math.max(0, f.underAttack - dt);
    f.aiState.timer -= dt;
    if (f.aiState.timer <= 0) {
      f.aiState.timer = .65 + Math.random() * .65;
      this.aiThink(f);
    }
  }
};

Game.prototype.aiThink = function(f) {
  this.aiEconomyEmergency(f);
  this.setAutoWorkerOrders(f.id);
  this.aiBuild(f);
  this.aiTrain(f);
  this.aiTactics(f);
};

Game.prototype.aiEconomyEmergency = function(f) {
  const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead).length;
  if (workers < 3) { f.res.wood += 12; f.res.gold += 12; }
  const pop = this.population(f.id);
  if (pop.cap - pop.used <= 1) f.aiState.expansion = Math.min(4, f.aiState.expansion + .03);
};

Game.prototype.aiBuild = function(f) {
  const ownB = this.buildings.filter(b => b.faction === f.id && !b.dead);
  const count = (t) => ownB.filter(b => b.type === t).length;
  const pop = this.population(f.id);
  const candidates = [];
  if (pop.cap - pop.used < 5) candidates.push('house');
  if (count('barracks') < 1) candidates.push('barracks');
  if (count('archery') < 1 && count('barracks') >= 1) candidates.push('archery');
  if (count('tower') < 2 + Math.floor(f.aiState.expansion)) candidates.push('tower');
  if (count('monastery') < 1 && pop.used > 12) candidates.push('monastery');
  if (pop.used > 24 && count('barracks') < 2) candidates.push('barracks');
  if (pop.used > 28 && count('archery') < 2) candidates.push('archery');
  if (!candidates.length && Math.random() < .18) candidates.push(choose(['house','tower','archery','barracks']));
  for (const t of candidates) {
    const def = BUILDINGS[t];
    if (!canAfford(f, def.cost)) continue;
    const anchor = this.aiBuildAnchor(f, t);
    const pos = this.findBuildSpot(anchor.x, anchor.y, t, f.aiState.expansion);
    if (pos && pay(f, def.cost)) { const b = this.addBuilding(f.id, t, pos.x, pos.y, false); b.aiIntent = t; return; }
  }
};

Game.prototype.aiBuildAnchor = function(f, type) {
  if (type === 'tower') {
    const a = f.aiState.rallyAngle;
    return { x: f.base.x + Math.cos(a) * 420, y: f.base.y + Math.sin(a) * 420 };
  }
  return f.base;
};

Game.prototype.findBuildSpot = function(cx, cy, type, ring = 0) {
  const base = 190 + ring * 72;
  for (let i = 0; i < 36; i++) {
    const a = Math.random() * Math.PI * 2;
    const r = base + Math.random() * 640;
    const x = clamp(cx + Math.cos(a) * r, 140, WORLD_W - 140);
    const y = clamp(cy + Math.sin(a) * r, 140, WORLD_H - 140);
    if (this.canPlace(type, x, y)) return { x, y };
  }
  return null;
};

Game.prototype.aiTrain = function(f) {
  const pop = this.population(f.id);
  const counts = { worker: 0, warrior: 0, archer: 0, lancer: 0, monk: 0 };
  for (const u of this.units) if (u.faction === f.id && !u.dead) counts[u.type] = (counts[u.type] || 0) + 1;
  for (const b of this.buildings) {
    if (b.faction !== f.id || b.dead || b.build < 1 || b.queue.length >= 2) continue;
    const trains = BUILDINGS[b.type].trains;
    if (!trains.length) continue;
    let desired = null;
    const army = counts.warrior + counts.archer + counts.lancer + counts.monk;
    if (b.type === 'castle' && counts.worker < Math.min(16, 8 + Math.floor(army / 5))) desired = 'worker';
    else if (b.type === 'barracks') desired = counts.lancer < counts.warrior * .35 && Math.random() < .42 ? 'lancer' : 'warrior';
    else if (b.type === 'archery') desired = 'archer';
    else if (b.type === 'monastery' && army > 7 && counts.monk < Math.ceil(army / 8)) desired = 'monk';
    if (!desired) continue;
    const def = UNITS[desired];
    if (pop.used + def.pop > pop.cap) continue;
    if (pay(f, def.cost)) b.queue.push({ type: desired, time: def.time });
  }
};

Game.prototype.aiTactics = function(f) {
  const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead && !u.garrisoned);
  const idleArmy = army.filter(u => u.order === 'idle' || u.order === 'move' || u.order === 'attackMove');
  const threat = this.nearestThreatToBase(f.id, f.base.x, f.base.y, 920);
  if (threat && idleArmy.length) {
    for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 18))) this.orderAttack(u, threat, false);
    return;
  }

  const towers = this.buildings.filter(b => b.faction === f.id && b.type === 'tower' && b.build >= 1 && b.garrison.length < BUILDINGS.tower.garrisonCap);
  for (const tw of towers) {
    const ar = idleArmy.find(u => u.type === 'archer' && dist2(u.x, u.y, tw.x, tw.y) < 720 * 720);
    if (ar) this.garrisonArchers([ar], tw, true);
  }

  f.aiState.attackTimer -= 1;
  if (idleArmy.length >= 4) {
    const stage = { x: f.base.x + Math.cos(f.aiState.rallyAngle) * 360, y: f.base.y + Math.sin(f.aiState.rallyAngle) * 360 };
    for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 10))) if (dist2(u.x, u.y, f.base.x, f.base.y) < 520 * 520) this.orderMoveFormation([u], stage.x, stage.y, true);
  }
  if (f.aiState.attackTimer <= 0 && idleArmy.length >= 7) {
    f.aiState.attackTimer = 9 + Math.random() * 11;
    f.aiState.rallyAngle += .8 + Math.random() * .6;
    const target = this.pickStrategicTarget(f.id);
    if (target) {
      const squad = idleArmy.slice(0, Math.min(idleArmy.length, 9 + Math.floor(Math.random() * 9)));
      for (const u of squad) this.orderAttack(u, target, true);
    }
  }
};

Game.prototype.pickStrategicTarget = function(fid) {
  let best = null, score = Infinity;
  const own = this.factions[fid];
  for (const b of this.buildings) {
    if (b.dead || b.faction === fid || !this.factions[b.faction].alive || b.build < 1) continue;
    const baseD = Math.sqrt(dist2(own.base.x, own.base.y, b.x, b.y));
    const enemyArmyNear = this.units.filter(u => u.faction === b.faction && !u.dead && dist2(u.x, u.y, b.x, b.y) < 520 * 520).length;
    const s = baseD + enemyArmyNear * 44 + Math.random() * 320 - (b.type === 'castle' ? 260 : b.type === 'tower' ? -80 : 0);
    if (s < score) { score = s; best = b; }
  }
  return best;
};

Game.prototype.renderUI = function() {
  const f = this.factions[0];
  const pop = this.population(0);
  HUD.resources.innerHTML = Object.keys(RESOURCES).map(k => {
    const r = RESOURCES[k];
    return `<div class="res-pill"><img src="${IMAGE_PATHS[r.icon]}" alt="${r.label}"><span>${Math.floor(f.res[k])}</span></div>`;
  }).join('') + `<div class="res-pill"><img src="${IMAGE_PATHS.iconHouse}" alt="Population"><span>${pop.used}/${pop.cap}</span></div>`;
  const enemiesAlive = this.factions.filter(x => x.id !== 0 && x.alive).length;
  HUD.state.innerHTML = `<span>${this.paused ? 'Paused' : 'Live'}</span><span>${enemiesAlive} rivals</span><span>H help</span>`;
  this.renderSelectionPanel();
  this.renderActions();
};

Game.prototype.renderSelectionPanel = function() {
  const s = this.selected.filter(isAlive);
  if (!s.length) {
    HUD.selectionHeader.innerHTML = '<span>No selection</span>';
    HUD.selectionBody.innerHTML = 'Drag units or click a building. Right-click gives contextual orders.';
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
    HUD.selectionHeader.innerHTML = `<img src="${iconFor(first)}" alt=""><span>${s.length} selected</span>`;
    HUD.selectionBody.innerHTML = Object.entries(groups).map(([t, n]) => `<div class="selection-row"><span>${n} x ${UNITS[t]?.label || BUILDINGS[t]?.label || t}</span></div>`).join('');
    return;
  }
  if (first.entity === 'resource') {
    HUD.selectionHeader.innerHTML = `<img src="${iconFor(first)}" alt=""><span>${first.type === 'tree' ? 'Wood Grove' : first.type === 'gold' ? 'Gold Vein' : first.animal ? 'Sheep' : 'Meat'}</span>`;
    const hp = first.animal ? `<div class="selection-row"><span>Animal HP</span><b>${Math.max(0, Math.ceil(first.animalHp))}</b></div>` : '';
    HUD.selectionBody.innerHTML = `${hp}<div class="selection-row"><span>Remaining</span><b>${Math.max(0, Math.floor(first.amount))}</b></div>`;
    return;
  }
  const def = first.entity === 'unit' ? UNITS[first.type] : BUILDINGS[first.type];
  const owner = faction(first.faction);
  const hpPct = clamp(first.hp / first.maxHp * 100, 0, 100);
  const extra = first.entity === 'building' && first.type === 'tower' ? `<div class="selection-row"><span>Archers inside</span><b>${first.garrison.length}/${BUILDINGS.tower.garrisonCap}</b></div>` : '';
  const build = first.entity === 'building' && first.build < 1 ? `<div class="selection-row"><span>Construction</span><b>${Math.floor(first.build * 100)}%</b></div>` : '';
  const queue = first.entity === 'building' && first.queue.length ? `<div class="selection-row"><span>Queue</span><b>${first.queue.map(q => UNITS[q.type].label).join(', ')}</b></div>` : '';
  HUD.selectionHeader.innerHTML = `<img src="${iconFor(first)}" alt=""><span>${def.label}</span><em>${owner.name}</em>`;
  HUD.selectionBody.innerHTML = `<div class="selection-row"><span>HP</span><div class="hpbar"><span style="width:${hpPct}%"></span></div><b>${Math.ceil(first.hp)}/${first.maxHp}</b></div>${build}${extra}${queue}`;
};
