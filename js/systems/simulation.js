// Canonical RTS gameplay simulation: movement, combat, economy, construction, AI, and frame update.
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
      if (b.build >= 1) {
        if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} completed.`, 1.4); this.sfx.build(); }
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

Game.prototype.setAnimalDirection = function(r, vx, vy) {
  if (!r || !r.animal) return;
  if (Math.abs(vx) < 1 && Math.abs(vy) < 1) return;
  if (Math.abs(vx) > Math.abs(vy) * 1.12) {
    r.animalDir = vx >= 0 ? ANIMAL_DIRECTION_ROWS.right : ANIMAL_DIRECTION_ROWS.left;
    r.face = vx >= 0 ? 1 : -1;
  } else {
    r.animalDir = vy >= 0 ? ANIMAL_DIRECTION_ROWS.down : ANIMAL_DIRECTION_ROWS.up;
  }
};

Game.prototype.updateResources = function(dt) {
  for (const r of this.resources) {
    r.flash = Math.max(0, (r.flash || 0) - dt * 3.5);
    r.hurtTimer = Math.max(0, (r.hurtTimer || 0) - dt);
    if (r.dead || r.type !== 'food' || r.amount <= 0 || !r.animal) continue;
    const spec = getHuntAnimal(r.animalKind);
    r.panic = Math.max(0, (r.panic || 0) - dt);
    r.wander -= dt;
    if (r.wander <= 0) {
      const calm = spec ? spec.walkSpeed : [9, 18];
      const flee = spec ? spec.runSpeed : [34, 62];
      r.wander = r.panic > 0 ? .30 + Math.random() * .62 : 1.2 + Math.random() * 3.4;
      const a = Math.random() * Math.PI * 2;
      const speedRange = r.panic > 0 ? flee : calm;
      const sp = speedRange[0] + Math.random() * (speedRange[1] - speedRange[0]);
      r.vx = Math.cos(a) * sp;
      r.vy = Math.sin(a) * sp;
      this.setAnimalDirection(r, r.vx, r.vy);
    }
    const nx = r.x + r.vx * dt;
    const ny = r.y + r.vy * dt;
    if (!this.isWater(nx, ny) && !this.occupiedByBase(nx, ny, 90)) {
      let blocked = false;
      for (const res of this.resources) {
        if (res === r || res.dead || res.amount <= 0) continue;
        if (dist2(nx, ny, res.x, res.y) < (r.r + res.r) * (r.r + res.r) * .7) { blocked = true; break; }
      }
      if (!blocked) {
        r.x = nx; r.y = ny;
        this.setAnimalDirection(r, r.vx, r.vy);
      } else { r.vx *= -0.65; r.vy *= -0.65; r.wander = .22; this.setAnimalDirection(r, r.vx, r.vy); }
    }
    else { r.vx *= -0.65; r.vy *= -0.65; r.wander = .22; this.setAnimalDirection(r, r.vx, r.vy); }
    r.vx *= r.panic > 0 ? .992 : .982;
    r.vy *= r.panic > 0 ? .992 : .982;
  }
};



Game.prototype.rebuildUnitSpatialIndex = function() {
  const bucketSize = 72;
  this.unitBuckets = new Map();
  this.unitBucketSize = bucketSize;
  for (const u of this.units) {
    if (u.dead || u.garrisoned) continue;
    const key = `${Math.floor(u.x / bucketSize)},${Math.floor(u.y / bucketSize)}`;
    let list = this.unitBuckets.get(key);
    if (!list) this.unitBuckets.set(key, list = []);
    list.push(u);
  }
};

Game.prototype.nearbyUnits = function(x, y) {
  if (!this.unitBuckets) return this.units;
  const bucketSize = this.unitBucketSize || 72;
  const bx = Math.floor(x / bucketSize), by = Math.floor(y / bucketSize);
  const out = [];
  for (let oy = -1; oy <= 1; oy++) {
    for (let ox = -1; ox <= 1; ox++) {
      const list = this.unitBuckets.get(`${bx + ox},${by + oy}`);
      if (list) out.push(...list);
    }
  }
  return out;
};

Game.prototype.updateUnits = function(dt) {
  this.rebuildUnitSpatialIndex();
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
          u.order = 'idle'; u.target = null;
        }
      } else if (b.hp < b.maxHp) {
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.05);
        if (b.hp >= b.maxHp) { u.order = 'idle'; u.target = null; this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.4); }
      }
    }
    return;
  }

  if (u.order === 'harvest') {
    const res = u.target;
    if (!res || res.dead || res.amount <= 0) { u.carry = null; u.order = 'idle'; u.target = null; u.gather = 0; return; }
    if (u.carry) {
      const drop = this.nearestDropoff(u.faction, u.x, u.y);
      if (!drop) { u.order = 'idle'; u.target = null; return; }
      if (this.moveToward(u, drop.x, drop.y, dt, Math.hypot(drop.w/2, drop.h/2) + u.r + 4)) {
        addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
        u.carry = null; u.gather = 0;
        if (res && !res.dead && res.amount > 0) { u.order = 'harvest'; }
        else { u.order = 'idle'; u.target = null; this.autoGather(u); }
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
  if (u.order === 'idle') this.autoGather(u);
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
      if (u.stuck > .4) {
        u.pathProbe = (u.pathProbe || 0) + 1;
        const nudgeAngle = (u.pathProbe * 2.39996) % (Math.PI * 2);
        const nudgeR = u.r + 20 + Math.random() * 30;
        const gx = u.x + Math.cos(nudgeAngle) * nudgeR;
        const gy = u.y + Math.sin(nudgeAngle) * nudgeR;
        if (!this.isWater(gx, gy)) { u.x = gx; u.y = gy; }
        else this.nudgeUnitToLand(u);
      }
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
  for (const v of this.nearbyUnits(u.x, u.y)) {
    if (v === u || v.dead || v.garrisoned) continue;
    let min = u.r + v.r + 3;
    if (u.type === 'worker' && v.type === 'worker' && u.order === 'harvest' && v.order === 'harvest' && u.target && u.target === v.target) {
      min = u.r + 2;
    }
    const dx = u.x - v.x, dy = u.y - v.y;
    const d2 = dx * dx + dy * dy;
    if (d2 > 0 && d2 < min * min) {
      const d = Math.sqrt(d2);
      sx += dx / d * (min - d);
      sy += dy / d * (min - d);
    }
  }
  if (sx || sy) {
    const nx = u.x + sx * dt * 2.2;
    const ny = u.y + sy * dt * 2.2;
    if (!this.isBlocked(nx, ny, u)) { u.x = nx; u.y = ny; }
  }
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
  const r = this.nearestResource(u.x, u.y, need, 1200) || this.nearestResource(u.x, u.y, null, 2000);
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
      f.aiState.timer = .65 + Math.random() * .65;
      this.aiThink(f);
    }
  }
};


Game.prototype.aiThink = function(f) {
  this.aiEconomyEmergency(f);
  this.aiBuild(f);
  this.aiTrain(f);
  this.aiTactics(f);
  this.reassignIdleWorkers(f.id);
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
    const enemyArmyNear = this.units.filter(u => u.faction === b.faction && !u.dead && dist2(u.x, u.y, b.x, b.y) < 520 * 520).length;
    const s = baseD + enemyArmyNear * 44 + Math.random() * 320 - (b.type === 'castle' ? 260 : b.type === 'tower' ? -80 : 0);
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
    if (d.sky || d.water || PASSABLE_DECOR.has(d.kind)) continue;
    const spec = DECOR_SPECS[d.kind] || {};
    const baseBlock = LIGHT_DECOR.has(d.kind) ? 8 : Math.max(10, Math.min(20, ((spec.shadow && spec.shadow[0]) || (d.kind.startsWith('bush') ? 18 : 14)) * .72));
    const dr = baseBlock * (d.scale || 1);
    const dRect = { x: d.x - dr, y: d.y - dr, w: dr * 2, h: dr * 2 };
    if (rectsOverlap(rect, dRect)) return true;
  }
  
  return false;
};

Game.prototype.strikeAnimal = function(u, res) {
  if (!res || res.dead || !res.animal) return;
  const spec = getHuntAnimal(res.animalKind);
  const damage = spec && res.animalKind === 'boar' ? 10 : 11;
  res.animalHp -= damage;
  res.panic = spec && res.animalKind === 'hare' ? 3.0 : 2.4;
  res.flash = 1;
  res.hurtTimer = .26;
  const dx = res.x - u.x, dy = res.y - u.y;
  const d = Math.hypot(dx, dy) || 1;
  const shove = spec && res.animalKind === 'deer' ? 78 : spec && res.animalKind === 'hare' ? 92 : 58;
  res.vx += dx / d * shove;
  res.vy += dy / d * shove;
  this.setAnimalDirection(res, res.vx, res.vy);
  this.effects.push({ kind: 'hit', x: res.x, y: res.y - 18, time: .18, max: .18 });

  if (spec && spec.retaliation && dist2(u.x, u.y, res.x, res.y) <= 46 * 46 && Math.random() < .42) {
    u.hp -= spec.retaliation;
    u.flash = 1;
    this.effects.push({ kind: 'hit', x: u.x, y: u.y - 22, time: .18, max: .18 });
    if (u.hp <= 0) { u.dead = true; u.selected = false; this.checkDefeat(u.faction); }
  }

  if (res.animalHp <= 0) {
    const foodAmount = Math.min(res.amount, spec ? spec.yield : 14);
    res.dead = true;
    res.amount = 0;
    u.carry = { type: 'food', amount: foodAmount };
    u.gather = 0;
    this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
  }
};

Game.prototype.reassignIdleWorkers = function(fid) {
  for (const u of this.units) {
    if (u.faction === fid && u.type === 'worker' && u.order === 'idle' && !u.dead && !u.garrisoned) {
      this.autoGather(u);
    }
  }
};

Game.prototype.aiEconomyEmergency = function(f) {
  const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead).length;
  if (workers < 3) { f.res.wood += 12; f.res.gold += 12; }
  const pop = this.population(f.id);
  if (pop.cap - pop.used <= 1) f.aiState.expansion = Math.min(4, f.aiState.expansion + .03);
};

Game.prototype.aiBuildAnchor = function(f, type) {
  if (type === 'tower') {
    const a = f.aiState.rallyAngle;
    return { x: f.base.x + Math.cos(a) * 420, y: f.base.y + Math.sin(a) * 420 };
  }
  return f.base;
};

Game.prototype.run = function(ts) {
  const dt = Math.min(MAX_DT, (ts - this.lastFrame) / 1000 || 0);
  this.lastFrame = ts;
  this.update(dt * (this.fast ? 1.7 : 1));
  this.draw();
  requestAnimationFrame(t => this.run(t));
};

Game.prototype.update = function(dt) {
  this.time += dt;
  if (this.toastTimer > 0) {
    this.toastTimer -= dt;
    if (this.toastTimer <= 0) HUD.message.classList.add('hidden');
  }
  this.updateCamera(dt);
  if (!this.paused) {
    this.updateBuildings(dt);
    this.updateResources(dt);
    this.updateUnits(dt);
    this.updateProjectiles(dt);
    this.updateEffects(dt);
    this.updateAI(dt);
    this.cleanup();
  }
  this.uiTimer -= dt;
  if (this.uiDirty || this.uiTimer <= 0) {
    this.renderUI();
    this.uiTimer = .25;
    this.uiDirty = false;
  }
};
