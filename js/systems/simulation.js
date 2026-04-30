// RTS simulation systems: movement, combat, economy, AI and cleanup.
Game.prototype.updateCamera = function(dt) {
    const margin = 18;
    const sp = (keys.has('shift') ? 760 : 520) * dt / this.camera.zoom;
    let dx = 0, dy = 0;
    if (keys.has('a') || keys.has('arrowleft')) dx -= sp;
    if (keys.has('d') || keys.has('arrowright')) dx += sp;
    if (keys.has('w') || keys.has('arrowup')) dy -= sp;
    if (keys.has('s') || keys.has('arrowdown')) dy += sp;
    if (this.pointer.inside) {
      if (this.pointer.x < margin) dx -= sp * .75;
      if (this.pointer.x > VIEW_W - margin) dx += sp * .75;
      if (this.pointer.y < margin) dy -= sp * .75;
      if (this.pointer.y > VIEW_H - margin) dy += sp * .75;
    }
    this.camera.x = clamp(this.camera.x + dx, 0, WORLD_W - VIEW_W / this.camera.zoom);
    this.camera.y = clamp(this.camera.y + dy, 0, WORLD_H - VIEW_H / this.camera.zoom);
    this.camera.zoom += (this.camera.targetZoom - this.camera.zoom) * Math.min(1, dt * 8);
  
};

Game.prototype.updateBuildings = function(dt) {
    for (const b of this.buildings) {
      if (b.dead) continue;
      b.flash = Math.max(0, b.flash - dt * 3);
      if (b.build < 1) {
        b.build = Math.min(1, b.build + dt / b.buildTime);
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * .9);
        if (b.build >= 1 && b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} completed.`, 1.4); this.sfx.build(); }
        continue;
      }
      if (b.queue.length) {
        const q = b.queue[0]; q.time -= dt;
        if (q.time <= 0) {
          b.queue.shift();
          const u = this.addUnit(b.faction, q.type, b.x + (Math.random() - .5) * 60, b.y + b.h * .48 + 26);
          if (b.rally) this.orderMoveFormation([u], b.rally.x, b.rally.y, false);
          if (b.faction === 0) this.uiDirty = true;
        }
      }
      if (b.type === 'tower' && b.garrison.length) this.towerAttack(b, dt);
      if (b.type === 'monastery') this.passiveHeal(b, dt);
    }
  
};

Game.prototype.towerAttack = function(b, dt) {
    b.cd -= dt;
    if (b.cd > 0) return;
    const target = this.nearestEnemy({ x: b.x, y: b.y, faction: b.faction }, BUILDINGS.tower.range, true);
    if (!target) return;
    b.cd = Math.max(.42, 1.15 - b.garrison.length * .22);
    this.spawnProjectile(b.faction, b.x, b.y - 70, target, 15 + b.garrison.length * 4);
  
};

Game.prototype.passiveHeal = function(b, dt) {
    b.cd -= dt;
    if (b.cd > 0) return;
    let healed = false;
    for (const u of this.units) {
      if (u.faction === b.faction && !u.dead && !u.garrisoned && u.hp < u.maxHp && dist2(u.x, u.y, b.x, b.y) < 240 * 240) {
        u.hp = Math.min(u.maxHp, u.hp + 10); healed = true;
      }
    }
    if (healed) { b.cd = 1.2; this.effects.push({ kind: 'heal', x: b.x, y: b.y - 10, time: .9, max: .9 }); }
  
};

Game.prototype.updateResources = function(dt) {
    for (const r of this.resources) {
      r.flash = Math.max(0, (r.flash || 0) - dt * 3.5);
      if (r.dead || r.type !== 'food' || r.amount <= 0) continue;
      r.wander -= dt;
      if (r.wander <= 0) {
        r.wander = 1.2 + Math.random() * 3.4;
        const a = Math.random() * Math.PI * 2;
        const sp = 10 + Math.random() * 22;
        r.vx = Math.cos(a) * sp;
        r.vy = Math.sin(a) * sp;
      }
      const nx = r.x + r.vx * dt;
      const ny = r.y + r.vy * dt;
      if (!this.isWater(nx, ny) && !this.occupiedByBase(nx, ny, 90)) { r.x = nx; r.y = ny; }
      else { r.vx *= -0.6; r.vy *= -0.6; r.wander = .4; }
      r.vx *= .985; r.vy *= .985;
    }
  
};

Game.prototype.updateUnits = function(dt) {
    for (const u of this.units) {
      if (u.dead || u.garrisoned) continue;
      u.flash = Math.max(0, u.flash - dt * 4);
      u.cd = Math.max(0, u.cd - dt);
      u.lastWaterBounce = Math.max(0, (u.lastWaterBounce || 0) - dt);
      u.anim += dt * (u.order === 'move' || u.order === 'attackMove' || u.order === 'garrison' ? 8 : u.order === 'attack' ? 7 : 4);
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
      if (this.moveToward(u, b.x, b.y + b.h * 0.4, dt, 36)) {
        u.face = b.x >= u.x ? 1 : -1;
        // The worker is now hammering! Handled in world-renderer
        if (Math.random() < dt * 2) this.effects.push({ kind: 'dust', x: u.x + (Math.random() - .5) * 10, y: u.y, time: .3, max: .3 });
        
        // Actually repair/build it over time
        if (b.build < 1) {
          b.build = Math.min(1, b.build + dt / b.buildTime * 1.5);
          b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * 1.35);
          if (b.build >= 1 && b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(); u.order = 'idle'; }
        } else if (b.hp < b.maxHp) {
          b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.05);
          if (b.hp >= b.maxHp) { u.order = 'idle'; this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.4); }
        }
      }
      return;
    }
    if (u.order === 'harvest') {
      const res = u.target;
      if (!res || res.dead || res.amount <= 0) { u.carry = null; u.order = 'idle'; u.target = null; return; }
      if (u.carry) {
        const drop = this.nearestDropoff(u.faction, u.x, u.y);
        if (!drop) { u.order = 'idle'; return; }
        if (this.moveToward(u, drop.x, drop.y + 36, dt, 24)) {
          addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
          u.carry = null; u.gather = 0;
        }
        return;
      }
      if (this.moveToward(u, res.x, res.y, dt, res.r + 8)) {
        u.gather += dt;
        if (u.gather >= (res.type === 'tree' ? 1.4 : res.type === 'gold' ? 1.65 : 1.1)) {
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
      }
      return;
    }
    this.updateFighter(u, dt);
    if (u.order === 'idle' && this.factions[u.faction].ai) this.autoGather(u);
  
};

Game.prototype.updateMonk = function(u, dt) {
    const ally = this.lowestHurtAlly(u, UNITS.monk.range);
    if (ally && u.cd <= 0) {
      u.face = ally.x >= u.x ? 1 : -1;
      ally.hp = Math.min(ally.maxHp, ally.hp + 18);
      u.cd = UNITS.monk.cd;
      this.effects.push({ kind: 'heal', x: ally.x, y: ally.y - 28, time: .6, max: .6 });
      u.order = 'heal';
      return;
    }
    if (u.order === 'heal') u.order = 'idle';
    this.updateFighter(u, dt);
  
};

Game.prototype.updateFighter = function(u, dt) {
    if ((u.order === 'idle' || u.order === 'attackMove') && !u.hold) {
      const enemy = this.nearestEnemy(u, u.attackMove ? 420 : 310, true);
      if (enemy) this.orderAttack(u, enemy, u.order === 'attackMove');
    }
    if (u.order === 'attack' && isAlive(u.target)) {
      const def = UNITS[u.type];
      const d = dist(u, u.target);
      if (d > def.range * .94) this.moveToward(u, u.target.x, u.target.y, dt, def.range * .82);
      else this.attackTarget(u, u.target);
      return;
    }
    if (u.order === 'attack' && (!u.target || !isAlive(u.target))) {
      u.target = null; u.order = u.attackMove ? 'attackMove' : 'idle';
    }
    if ((u.order === 'move' || u.order === 'attackMove') && u.goal) {
      if (this.moveToward(u, u.goal.x, u.goal.y, dt, 8)) { if (u.order === 'move') u.order = 'idle'; u.goal = null; }
    }
  
};

Game.prototype.moveToward = function(u, x, y, dt, stop = 6) {
    const dx = x - u.x, dy = y - u.y;
    const d = Math.hypot(dx, dy);
    if (d <= stop) return true;
    const sp = u.speed * dt * (u.carry ? .77 : 1);
    const step = Math.min(sp, Math.max(0, d - stop));
    let nx = u.x + dx / d * step;
    let ny = u.y + dy / d * step;

    // Water-aware steering: units slide along shorelines instead of crossing ocean.
    if (this.isWater(nx, ny)) {
      const angle = Math.atan2(dy, dx);
      let found = false;
      for (const turn of [Math.PI / 5, -Math.PI / 5, Math.PI / 2, -Math.PI / 2, Math.PI * .82, -Math.PI * .82]) {
        const a = angle + turn;
        const tx = u.x + Math.cos(a) * step;
        const ty = u.y + Math.sin(a) * step;
        if (!this.isWater(tx, ty)) { nx = tx; ny = ty; found = true; break; }
      }
      if (!found) {
        u.stuck = (u.stuck || 0) + dt;
        if (u.lastWaterBounce <= 0) {
          this.effects.push({ kind: 'splash', x: u.x, y: u.y + 10, time: .42, max: .42 });
          u.lastWaterBounce = .65;
        }
        if (u.stuck > .65) this.nudgeUnitToLand(u);
        return false;
      }
    } else u.stuck = 0;

    u.x = nx;
    u.y = ny;
    u.face = dx >= 0 ? 1 : -1;
    return false;
};

Game.prototype.separate = function(u, dt) {
    let sx = 0, sy = 0;
    for (const v of this.units) {
      if (v === u || v.dead || v.garrisoned) continue;
      const min = u.r + v.r + 3;
      const dx = u.x - v.x, dy = u.y - v.y;
      const d2 = dx * dx + dy * dy;
      if (d2 > 0 && d2 < min * min) { const d = Math.sqrt(d2); sx += dx / d * (min - d); sy += dy / d * (min - d); }
    }
    u.x += sx * dt * 2.2; u.y += sy * dt * 2.2;
  
};

Game.prototype.attackTarget = function(u, target) {
    const def = UNITS[u.type];
    if (u.cd > 0) return;
    u.face = target.x >= u.x ? 1 : -1;
    u.cd = def.cd;
    if (u.type === 'archer') this.spawnProjectile(u.faction, u.x, u.y - 34, target, def.damage);
    else this.damage(target, def.damage, u.faction);
  
};

Game.prototype.spawnProjectile = function(fid, x, y, target, damage) {
    this.projectiles.push({ id: gid++, x, y, faction: fid, target, damage, speed: 510, life: 2.2, dead: false });
  
};

Game.prototype.updateProjectiles = function(dt) {
    for (const p of this.projectiles) {
      if (p.dead) continue;
      p.life -= dt;
      if (p.life <= 0 || !isAlive(p.target)) { p.dead = true; continue; }
      const tx = p.target.x, ty = p.target.y - (p.target.entity === 'building' ? 38 : 28);
      const dx = tx - p.x, dy = ty - p.y, d = Math.hypot(dx, dy);
      if (d < 18) {
        this.damage(p.target, p.damage, p.faction);
        this.effects.push({ kind: 'hit', x: tx, y: ty, time: .25, max: .25 });
        p.dead = true;
      }
      else {
        const step = p.speed * dt;
        p.x += dx / d * step;
        p.y += dy / d * step;
        if (this.isWater(p.x, p.y) && rngHash(Math.floor(p.x), Math.floor(p.y), Math.floor(this.time * 20)) < .06) this.effects.push({ kind: 'splash', x: p.x, y: p.y, time: .25, max: .25 });
      }
    }
};

Game.prototype.damage = function(target, amount, sourceFaction) {
    if (!isAlive(target)) return;
    target.hp -= amount;
    target.flash = 1;
    if (target.entity === 'building') this.factions[target.faction].underAttack = 5;
    if (target.faction === 0 && sourceFaction !== 0 && Math.random() < .08) this.sfx.alert();
    if (target.hp <= 0) {
      target.dead = true;
      this.effects.push({ kind: 'boom', x: target.x, y: target.y - 30, time: 1.0, max: 1.0 });
      if (target.entity === 'building' && target.type === 'tower') {
        for (const id of target.garrison) {
          const u = this.units.find(unit => unit.id === id);
          if (u && !u.dead) { u.garrisoned = null; u.x = target.x + (Math.random() - .5) * 50; u.y = target.y + 40; u.order = 'idle'; }
        }
      }
      this.checkDefeat(target.faction);
    }
  
};

Game.prototype.checkDefeat = function(fid) {
    const f = this.factions[fid];
    if (!f || !f.alive) return;
    const hasCastle = this.buildings.some(b => b.faction === fid && b.type === 'castle' && !b.dead);
    const hasUnits = this.units.some(u => u.faction === fid && !u.dead);
    if (!hasCastle && !hasUnits) {
      f.alive = false;
      this.toast(`${f.name} has fallen.`, 3);
      if (fid === 0) this.toast('Your realm has fallen. Press refresh for another war.', 8);
    }
  
};

Game.prototype.nearestEnemy = function(u, range, includeBuildings) {
    let best = null, bd = range * range;
    for (const v of this.units) {
      if (v.dead || v.garrisoned || v.faction === u.faction || !this.factions[v.faction].alive) continue;
      const d = dist2(u.x, u.y, v.x, v.y);
      if (d < bd) { bd = d; best = v; }
    }
    if (includeBuildings) {
      for (const b of this.buildings) {
        if (b.dead || b.faction === u.faction || !this.factions[b.faction].alive || b.build < 1) continue;
        const d = dist2(u.x, u.y, b.x, b.y);
        if (d < bd) { bd = d; best = b; }
      }
    }
    return best;
  
};

Game.prototype.lowestHurtAlly = function(u, range) {
    let best = null, pct = 1;
    for (const v of this.units) {
      if (v.dead || v.garrisoned || v.faction !== u.faction || v.hp >= v.maxHp) continue;
      if (dist2(u.x, u.y, v.x, v.y) > range * range) continue;
      const p = v.hp / v.maxHp;
      if (p < pct) { pct = p; best = v; }
    }
    return best;
  
};

Game.prototype.nearestDropoff = function(fid, x, y) {
    let best = null, bd = Infinity;
    for (const b of this.buildings) {
      if (b.faction !== fid || b.dead || b.build < 1) continue;
      if (b.type !== 'castle' && b.type !== 'house') continue;
      const d = dist2(x, y, b.x, b.y);
      if (d < bd) { bd = d; best = b; }
    }
    return best;
  
};

Game.prototype.autoGather = function(u) {
    const f = this.factions[u.faction];
    const need = f.res.wood < 180 ? 'tree' : f.res.gold < 160 ? 'gold' : f.res.food < 4 ? 'food' : choose(['tree', 'gold', 'food']);
    const r = this.nearestResource(u.x, u.y, need, 900) || this.nearestResource(u.x, u.y, null, 1400);
    if (r) this.orderHarvest(u, r);
  
};

Game.prototype.nearestResource = function(x, y, type, range) {
    let best = null, bd = range * range;
    for (const r of this.resources) {
      if (r.dead || r.amount <= 0) continue;
      if (type && r.type !== type) continue;
      const d = dist2(x, y, r.x, r.y);
      if (d < bd) { bd = d; best = r; }
    }
    return best;
  
};

Game.prototype.setAutoWorkerOrders = function(fid) {
    for (const u of this.units) if (u.faction === fid && u.type === 'worker' && u.order === 'idle') this.autoGather(u);
  
};

Game.prototype.updateAI = function(dt) {
    for (const f of this.factions) {
      if (!f.ai || !f.alive) continue;
      f.underAttack = Math.max(0, f.underAttack - dt);
      f.aiState.timer -= dt;
      if (f.aiState.timer <= 0) {
        f.aiState.timer = .9 + Math.random() * .8;
        this.aiThink(f);
      }
    }
  
};

Game.prototype.aiThink = function(f) {
    this.setAutoWorkerOrders(f.id);
    this.aiBuild(f);
    this.aiTrain(f);
    this.aiTactics(f);
  
};

Game.prototype.aiBuild = function(f) {
    const ownB = this.buildings.filter(b => b.faction === f.id && !b.dead);
    const count = (t) => ownB.filter(b => b.type === t).length;
    const pop = this.population(f.id);
    const candidates = [];
    if (pop.cap - pop.used < 5) candidates.push('house');
    if (count('barracks') < 1) candidates.push('barracks');
    if (count('archery') < 1 && count('barracks') >= 1) candidates.push('archery');
    if (count('tower') < 2 + f.aiState.expansion) candidates.push('tower');
    if (count('monastery') < 1 && pop.used > 10) candidates.push('monastery');
    if (pop.used > 20 && count('barracks') < 2) candidates.push('barracks');
    if (!candidates.length && Math.random() < .25) candidates.push(choose(['house','tower','archery']));
    for (const t of candidates) {
      const def = BUILDINGS[t];
      if (!canAfford(f, def.cost)) continue;
      const pos = this.findBuildSpot(f.base.x, f.base.y, t, f.aiState.expansion);
      if (pos && pay(f, def.cost)) { this.addBuilding(f.id, t, pos.x, pos.y, false); return; }
    }
  
};

Game.prototype.findBuildSpot = function(cx, cy, type, ring = 0) {
    const base = 170 + ring * 60;
    for (let i = 0; i < 20; i++) {
      const a = Math.random() * Math.PI * 2;
      const r = base + Math.random() * 560;
      const x = clamp(cx + Math.cos(a) * r, 120, WORLD_W - 120);
      const y = clamp(cy + Math.sin(a) * r, 120, WORLD_H - 120);
      if (this.canPlace(type, x, y)) return { x, y };
    }
    return null;
  
};

Game.prototype.aiTrain = function(f) {
    const pop = this.population(f.id);
    for (const b of this.buildings) {
      if (b.faction !== f.id || b.dead || b.build < 1 || b.queue.length >= 2) continue;
      const trains = BUILDINGS[b.type].trains;
      if (!trains.length) continue;
      let desired = null;
      const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead).length;
      const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead).length;
      if (b.type === 'castle' && workers < 9 + Math.floor(army / 8)) desired = 'worker';
      else if (b.type === 'barracks') desired = Math.random() < .32 ? 'lancer' : 'warrior';
      else if (b.type === 'archery') desired = 'archer';
      else if (b.type === 'monastery' && army > 8 && Math.random() < .65) desired = 'monk';
      if (!desired) continue;
      const def = UNITS[desired];
      if (pop.used + def.pop > pop.cap) continue;
      if (pay(f, def.cost)) b.queue.push({ type: desired, time: def.time });
    }
  
};

Game.prototype.aiTactics = function(f) {
    const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead && !u.garrisoned);
    const idleArmy = army.filter(u => u.order === 'idle' || u.order === 'move' || u.order === 'attackMove');
    const threat = this.nearestThreatToBase(f.id, f.base.x, f.base.y, 780);
    if (threat && idleArmy.length) {
      for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 14))) this.orderAttack(u, threat, false);
      return;
    }
    f.aiState.attackTimer -= 1;
    if (f.aiState.attackTimer <= 0 && idleArmy.length >= 6) {
      f.aiState.attackTimer = 8 + Math.random() * 9;
      const target = this.pickStrategicTarget(f.id);
      if (target) {
        const squad = idleArmy.slice(0, Math.min(idleArmy.length, 8 + Math.floor(Math.random() * 8)));
        for (const u of squad) this.orderAttack(u, target, true);
      }
    }
    const towers = this.buildings.filter(b => b.faction === f.id && b.type === 'tower' && b.build >= 1 && b.garrison.length < BUILDINGS.tower.garrisonCap);
    for (const tw of towers) {
      const ar = idleArmy.find(u => u.type === 'archer' && dist2(u.x, u.y, tw.x, tw.y) < 600 * 600);
      if (ar) this.garrisonArchers([ar], tw, true);
    }
  
};

Game.prototype.nearestThreatToBase = function(fid, x, y, range) {
    let best = null, bd = range * range;
    for (const u of this.units) {
      if (u.dead || u.faction === fid || u.garrisoned) continue;
      const d = dist2(x, y, u.x, u.y);
      if (d < bd) { bd = d; best = u; }
    }
    return best;
  
};

Game.prototype.pickStrategicTarget = function(fid) {
    let best = null, score = Infinity;
    const own = this.factions[fid];
    for (const b of this.buildings) {
      if (b.dead || b.faction === fid || !this.factions[b.faction].alive || b.build < 1) continue;
      const baseD = Math.sqrt(dist2(own.base.x, own.base.y, b.x, b.y));
      const factionWeakness = this.population(b.faction).used;
      const s = baseD + factionWeakness * 18 + Math.random() * 380 - (b.type === 'castle' ? 160 : 0);
      if (s < score) { score = s; best = b; }
    }
    return best;
  
};

Game.prototype.population = function(fid) {
    let used = 0, cap = 0;
    for (const u of this.units) if (u.faction === fid && !u.dead) used += UNITS[u.type].pop;
    for (const b of this.buildings) if (b.faction === fid && !b.dead && b.build >= 1) cap += BUILDINGS[b.type].pop || 0;
    return { used, cap: Math.max(4, cap) };
  
};

Game.prototype.queueTrain = function(type) {
    const buildings = this.selected.filter(e => e.entity === 'building' && e.faction === 0 && e.build >= 1 && BUILDINGS[e.type].trains.includes(type));
    if (!buildings.length) return;
    const f = this.factions[0], def = UNITS[type], pop = this.population(0);
    if (pop.used + def.pop > pop.cap) { this.toast('Population cap reached. Build houses.', 1.6); this.sfx.deny(); return; }
    if (!pay(f, def.cost)) { this.toast('Not enough resources.', 1.3); this.sfx.deny(); return; }
    buildings.sort((a, b) => a.queue.length - b.queue.length)[0].queue.push({ type, time: def.time });
    this.uiDirty = true;
  
};

Game.prototype.repairSelected = function() {
    const f = this.factions[0];
    const damaged = this.selected.find(e => e.entity === 'building' && e.faction === 0 && e.hp < e.maxHp && e.build >= 1);
    if (!damaged) return;
    const cost = { wood: 28, gold: 18, food: 0 };
    if (!pay(f, cost)) { this.toast('Need wood and gold to repair.', 1.2); this.sfx.deny(); return; }
    damaged.hp = Math.min(damaged.maxHp, damaged.hp + damaged.maxHp * .28);
    this.effects.push({ kind: 'dust', x: damaged.x, y: damaged.y, time: .5, max: .5 });
    this.sfx.build();
  
};

Game.prototype.stopSelected = function() {
    for (const e of this.selected) {
      if (e.entity === 'unit' && e.faction === 0) { e.order = 'idle'; e.target = null; e.goal = null; e.attackMove = false; e.hold = false; }
    }
  
};

Game.prototype.holdSelected = function() {
    for (const e of this.selected) if (e.entity === 'unit' && e.faction === 0) { e.hold = true; e.order = 'idle'; e.goal = null; e.target = null; }
    this.toast('Selected units holding position.', 1.2);
  
};

Game.prototype.ungarrisonSelected = function() {
    const towers = this.selected.filter(e => e.entity === 'building' && e.type === 'tower' && e.faction === 0);
    for (const t of towers) {
      for (const id of t.garrison.splice(0)) {
        const u = this.units.find(x => x.id === id);
        if (u && !u.dead) { u.garrisoned = null; u.x = t.x + (Math.random() - .5) * 50; u.y = t.y + 58; u.order = 'idle'; }
      }
    }
    this.uiDirty = true;
  
};

Game.prototype.cleanup = function() {
    this.projectiles = this.projectiles.filter(p => !p.dead);
    this.effects = this.effects.filter(e => e.time > 0);
    this.selected = this.selected.filter(isAlive);
    for (const b of this.buildings) b.garrison = b.garrison.filter(id => this.units.some(u => u.id === id && !u.dead && u.garrisoned === b.id));
  
};

Game.prototype.updateEffects = function(dt) { for (const e of this.effects) e.time -= dt; 
};

Game.prototype.isWater = function(x, y) {
    if (x < 0 || y < 0 || x >= WORLD_W || y >= WORLD_H) return true;
    if (!this.landMap) return false;
    const tx = Math.floor(x / TILE), ty = Math.floor(y / TILE);
    if (tx < 0 || ty < 0 || tx >= this.landCols || ty >= this.landRows) return true;
    return this.landMap[ty * this.landCols + tx] !== 1;
  
};

Game.prototype.finishGarrison = function(u, tower) {
  if (!u || !tower || tower.dead || tower.garrison.length >= BUILDINGS.tower.garrisonCap) return false;
  if (tower.garrison.includes(u.id)) return true;
  u.garrisoned = tower.id;
  u.order = 'garrison';
  u.target = tower;
  u.goal = null;
  u.selected = false;
  tower.garrison.push(u.id);
  return true;
};

Game.prototype.updateGarrisonUnit = function(u, dt) {
  const tower = u.target;
  if (!tower || tower.dead || tower.type !== 'tower' || tower.faction !== u.faction) {
    u.order = 'idle';
    u.target = null;
    u.goal = null;
    return;
  }
  if (tower.garrison.length >= BUILDINGS.tower.garrisonCap) {
    u.order = 'idle';
    u.target = null;
    return;
  }
  if (this.moveToward(u, tower.x, tower.y + 42, dt, 20)) {
    this.finishGarrison(u, tower);
    if (u.faction === 0) this.uiDirty = true;
  }
};

Game.prototype.nudgeUnitToLand = function(u) {
  for (let r = 20; r <= 160; r += 20) {
    for (let i = 0; i < 12; i++) {
      const a = (Math.PI * 2 * i / 12) + rngHash(Math.floor(u.x), Math.floor(u.y), r) * .35;
      const x = clamp(u.x + Math.cos(a) * r, 20, WORLD_W - 20);
      const y = clamp(u.y + Math.sin(a) * r, 20, WORLD_H - 20);
      if (!this.isWater(x, y)) { u.x = x; u.y = y; return true; }
    }
  }
  return false;
};
