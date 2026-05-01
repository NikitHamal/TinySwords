// Canonical RTS gameplay simulation: movement, combat, economy, construction, AI, and frame update.
Game.prototype.audioGainAt = function(x, y, radius = 1260) {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return 1;
  const cx = this.camera.x + VIEW_W / this.camera.zoom / 2;
  const cy = this.camera.y + VIEW_H / this.camera.zoom / 2;
  const d = Math.hypot(x - cx, y - cy);
  const near = 180 / this.camera.zoom;
  if (d <= near) return 1;
  const falloff = clamp(1 - (d - near) / Math.max(1, radius - near), 0, 1);
  return Math.pow(falloff, 1.65);
};

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
  this.rebuildBuildingSpatialIndex && this.rebuildBuildingSpatialIndex();
  for (const b of this.buildings) {
    if (b.dead) continue;
    b.flash = Math.max(0, b.flash - dt * 3);
    if (b.build < 1) {
      b.build = Math.min(1, b.build + dt / b.buildTime);
      b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt / b.buildTime * .9);
      if (b.build >= 1) {
        if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} completed.`, 1.4); this.sfx.build(this.audioGainAt(b.x, b.y)); }
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
    if (healed) { b.cd = 1.2; this.effects.push({ kind: 'heal', x: b.x, y: b.y - 10, time: .9, max: .9 }); this.sfx.heal(this.audioGainAt(b.x, b.y)); }
  
};

Game.prototype.setAnimalDirection = function(r, vx, vy) {
  if (!r || !r.animal) return;
  if (Math.abs(vx) < 1 && Math.abs(vy) < 1) return;
  if (Math.abs(vx) > Math.abs(vy) * 1.12) {
    r.animalDir = vx >= 0 ? ANIMAL_DIRECTION_ROWS.right : ANIMAL_DIRECTION_ROWS.left;
    r.face = vx >= 0 ? 1 : -1;
  } else {
    r.animalDir = vy >= 0 ? ANIMAL_DIRECTION_ROWS.down : ANIMAL_DIRECTION_ROWS.up;
    if (Math.abs(vx) > 0.6) r.face = vx >= 0 ? 1 : -1;
  }
};

Game.prototype.updateResources = function(dt) {
  this.rebuildResourceSpatialIndex();
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
      for (const res of this.nearbyResources(nx, ny, 96)) {
        if (res === r || res.dead || res.amount <= 0) continue;
        if (dist2(nx, ny, res.x, res.y) < (getResourceFootprint(r) + getResourceFootprint(res)) ** 2 * .7) { blocked = true; break; }
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


Game.prototype.rebuildResourceSpatialIndex = function() {
  const bucketSize = 128;
  this.resourceBucketSize = bucketSize;
  this.resourceBuckets = new Map();
  for (const r of this.resources) {
    if (r.dead || r.amount <= 0) continue;
    const key = `${Math.floor(r.x / bucketSize)},${Math.floor(r.y / bucketSize)}`;
    let list = this.resourceBuckets.get(key);
    if (!list) this.resourceBuckets.set(key, list = []);
    list.push(r);
  }
};

Game.prototype.nearbyResources = function(x, y, range = 160) {
  if (!this.resourceBuckets) return this.resources;
  const bucketSize = this.resourceBucketSize || 128;
  const bx = Math.floor(x / bucketSize), by = Math.floor(y / bucketSize);
  const reach = Math.max(1, Math.ceil(range / bucketSize));
  const out = [];
  for (let oy = -reach; oy <= reach; oy++) {
    for (let ox = -reach; ox <= reach; ox++) {
      const list = this.resourceBuckets.get(`${bx + ox},${by + oy}`);
      if (list) out.push(...list);
    }
  }
  return out;
};

Game.prototype.rebuildBuildingSpatialIndex = function() {
  const bucketSize = 192;
  this.buildingBucketSize = bucketSize;
  this.buildingBuckets = new Map();
  for (const b of this.buildings) {
    if (b.dead) continue;
    const key = `${Math.floor(b.x / bucketSize)},${Math.floor(b.y / bucketSize)}`;
    let list = this.buildingBuckets.get(key);
    if (!list) this.buildingBuckets.set(key, list = []);
    list.push(b);
  }
};

Game.prototype.nearbyBuildings = function(x, y, range = 260) {
  if (!this.buildingBuckets) return this.buildings;
  const bucketSize = this.buildingBucketSize || 192;
  const bx = Math.floor(x / bucketSize), by = Math.floor(y / bucketSize);
  const reach = Math.max(1, Math.ceil(range / bucketSize));
  const out = [];
  for (let oy = -reach; oy <= reach; oy++) {
    for (let ox = -reach; ox <= reach; ox++) {
      const list = this.buildingBuckets.get(`${bx + ox},${by + oy}`);
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
          if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(this.audioGainAt(b.x, b.y)); }
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

    const interact = getResourceInteractionPoint(res);
    this.moveToward(u, interact.x, interact.y, dt, res.r + 6);
    if (dist2(u.x, u.y, interact.x, interact.y) <= (res.r + 26) * (res.r + 26)) {
      u.gather += dt;
      u.face = interact.x >= u.x ? 1 : -1;
      const gatherTime = res.type === 'tree' ? 1.35 : res.type === 'gold' ? 1.6 : .82;
      if (u.gather >= gatherTime) {
        const amount = res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14;
        res.amount -= amount;
        u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
        u.gather = 0;
        if (res.amount <= 0) {
          if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; this.markNavDirty && this.markNavDirty(); }
          else { res.dead = true; this.markNavDirty && this.markNavDirty(); }
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
      this.sfx.heal(this.audioGainAt(ally.x, ally.y));
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
      if (this.moveToward(u, u.goal.x, u.goal.y, dt, 12)) {
        u.order = 'idle';
        u.goal = null;
      } else {
        const d2 = dist2(u.x, u.y, u.goal.x, u.goal.y);
        if (d2 < 160 * 160) {
          let crowdBlocked = false;
          for (const v of this.units) {
            if (v !== u && !v.dead && v.faction === u.faction && v.order === 'idle') {
              if (dist2(u.x, u.y, v.x, v.y) < (u.r + v.r + 14) * (u.r + v.r + 14)) {
                crowdBlocked = true;
                break;
              }
            }
          }
          if (crowdBlocked || u.stuck > 0.5) {
            u.order = 'idle';
            u.goal = null;
          }
        } else if (u.stuck > 1.8 || u.trafficJam > 1.5) {
          u.order = 'idle';
          u.goal = null;
        }
      }
    }
  
};

Game.prototype.moveToward = function(u, x, y, dt, stop = 6) {
  const dx = x - u.x, dy = y - u.y;
  const d = Math.hypot(dx, dy);
  if (d <= stop) { this.clearUnitPath && this.clearUnitPath(u); return true; }

  let tx = x, ty = y;
  if (this.isSegmentWalkable && this.isSegmentWalkable(u, u.x, u.y, x, y, d > 200 ? 10 : 6)) this.clearUnitPath && this.clearUnitPath(u);
  const path = this.prepareUnitPath ? this.prepareUnitPath(u, x, y, d) : null;
  if (path && path.length) {
    const wp = this.nextPathWaypoint(u, x, y);
    tx = wp.x; ty = wp.y;
  }

  const pdx = tx - u.x, pdy = ty - u.y;
  const pd = Math.hypot(pdx, pdy) || d;
  const sp = u.speed * dt * (u.carry ? .77 : 1);
  const step = Math.min(sp, Math.max(0, pd - Math.min(stop, 10)));
  let nx = u.x + pdx / pd * step;
  let ny = u.y + pdy / pd * step;

  if (this.isBlocked(nx, ny, u)) {
    const angle = Math.atan2(pdy, pdx);
    let found = false;
    const bias = (u.pathProbe || 0) % 2 ? -1 : 1;
    const turns = path ? [Math.PI / 5 * bias, -Math.PI / 5 * bias, Math.PI / 3 * bias, -Math.PI / 3 * bias, Math.PI / 2, -Math.PI / 2]
                       : [Math.PI / 6 * bias, -Math.PI / 6 * bias, Math.PI / 3 * bias, -Math.PI / 3 * bias, Math.PI / 2, -Math.PI / 2, Math.PI * .82, -Math.PI * .82];
    for (const turn of turns) {
      const a = angle + turn;
      const tx2 = u.x + Math.cos(a) * step;
      const ty2 = u.y + Math.sin(a) * step;
      if (!this.isBlocked(tx2, ty2, u)) { nx = tx2; ny = ty2; found = true; break; }
    }
    if (!found) {
      u.stuck = (u.stuck || 0) + dt;
      if (u.stuck > .55) {
        u.pathProbe = (u.pathProbe || 0) + 1;
        this.clearUnitPath && this.clearUnitPath(u);
        const nudgeAngle = (u.pathProbe * 2.39996) % (Math.PI * 2);
        const nudgeR = u.r + 18 + Math.random() * 24;
        const gx = u.x + Math.cos(nudgeAngle) * nudgeR;
        const gy = u.y + Math.sin(nudgeAngle) * nudgeR;
        if (!this.isBlocked(gx, gy, u)) { u.x = gx; u.y = gy; }
        else this.nudgeUnitToLand(u);
        u.stuck = .15;
      }
      return false;
    }
  } else u.stuck = 0;

  u.x = nx;
  u.y = ny;
  u.face = pdx >= 0 ? 1 : -1;
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
    else { this.sfx.attack(this.audioGainAt(u.x, u.y)); this.damage(target, def.damage, u.faction); }
  
};

Game.prototype.spawnProjectile = function(fid, x, y, target, damage) {
    this.projectiles.push({ id: gid++, x, y, faction: fid, target, damage, speed: 510, life: 2.2, dead: false });
    this.sfx.arrow(this.audioGainAt(x, y));
  
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
    if (amount > 0) this.sfx.hit(this.audioGainAt(target.x, target.y));
    if (target.entity === 'building') this.factions[target.faction].underAttack = 5;
    if (target.faction === 0 && sourceFaction !== 0 && Math.random() < .08) this.sfx.alert(this.audioGainAt(target.x, target.y, 1600));
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
  const candidates = this.nearbyResources ? this.nearbyResources(x, y, range) : this.resources;
  for (const r of candidates) {
    if (r.dead || r.amount <= 0) continue;
    if (type && r.type !== type) continue;
    const d = dist2(x, y, r.x, r.y);
    if (d < bd) { bd = d; best = r; }
  }
  if (!best && candidates !== this.resources) {
    for (const r of this.resources) {
      if (r.dead || r.amount <= 0) continue;
      if (type && r.type !== type) continue;
      const d = dist2(x, y, r.x, r.y);
      if (d < bd) { bd = d; best = r; }
    }
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

Game.prototype.aiFrontlinePosition = function(f) {
  const target = this.pickStrategicTargetV2(f.id);
  if (!target) return f.base;
  return this.nearestLandPoint(f.base.x + (target.x - f.base.x) * 0.42, f.base.y + (target.y - f.base.y) * 0.42, 260) || f.base;
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
  const diff = DIFFICULTY_PRESETS[this.worldSettings?.difficulty || 'normal'] || DIFFICULTY_PRESETS.normal;
  const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead && !u.garrisoned);
  const idleArmy = army.filter(u => u.order === 'idle' || u.order === 'move' || u.order === 'attackMove');
  const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead && !u.garrisoned);
  const threat = this.nearestThreatToBase(f.id, f.base.x, f.base.y, f.underAttack > 0 ? 1350 : 940);

  if (threat && idleArmy.length) {
    const defenders = idleArmy
      .sort((a, b) => dist2(a.x, a.y, threat.x, threat.y) - dist2(b.x, b.y, threat.x, threat.y))
      .slice(0, Math.min(idleArmy.length, f.underAttack > 0 ? 24 : 14));
    for (const u of defenders) this.orderAttack(u, threat, false);
    if (workers.length && f.underAttack > 1) {
      const repairTarget = this.buildings.find(b => b.faction === f.id && !b.dead && b.hp < b.maxHp && dist2(b.x, b.y, threat.x, threat.y) < 720 * 720);
      if (repairTarget) {
        for (const w of workers.slice(0, 4)) { w.order = 'repair'; w.target = repairTarget; w.goal = null; this.clearUnitPath && this.clearUnitPath(w); }
      }
    }
    return;
  }

  const towers = this.buildings.filter(b => b.faction === f.id && b.type === 'tower' && b.build >= 1 && b.garrison.length < BUILDINGS.tower.garrisonCap);
  for (const tw of towers) {
    const ar = idleArmy.find(u => u.type === 'archer' && dist2(u.x, u.y, tw.x, tw.y) < 860 * 860);
    if (ar) this.garrisonArchers([ar], tw, true);
  }

  const armyPower = army.reduce((sum, u) => sum + (u.type === 'lancer' ? 2.1 : u.type === 'archer' ? 1.25 : u.type === 'monk' ? .85 : 1), 0);
  f.aiState.attackTimer -= diff.aggression;
  const stageAngle = f.aiState.rallyAngle;
  const frontline = this.aiFrontlinePosition(f);
  const stage = this.nearestLandPoint(
    frontline.x + Math.cos(stageAngle) * (180 + f.aiState.expansion * 40),
    frontline.y + Math.sin(stageAngle) * (180 + f.aiState.expansion * 40),
    280
  ) || frontline;

  if (idleArmy.length >= Math.max(3, diff.aiSquadMin - 2)) {
    for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 12))) {
      if (dist2(u.x, u.y, f.base.x, f.base.y) < 660 * 660) this.orderMoveFormation([u], stage.x, stage.y, true);
    }
  }

  const wounded = army.filter(u => u.hp / u.maxHp < .35 && u.order !== 'move');
  const archers = idleArmy.filter(u => u.type === 'archer');
  const melee = idleArmy.filter(u => u.type === 'warrior' || u.type === 'lancer');
  for (const u of wounded.slice(0, 5)) this.orderMoveFormation([u], f.base.x + (Math.random() - .5) * 260, f.base.y + (Math.random() - .5) * 260, false);

  if (archers.length && melee.length) {
    for (const ar of archers.slice(0, Math.min(archers.length, 6))) {
      const escort = melee[Math.floor(Math.random() * melee.length)];
      if (escort && dist2(ar.x, ar.y, escort.x, escort.y) > 180 * 180) this.orderMoveFormation([ar], escort.x - 18 * ar.face, escort.y + 12, true);
    }
  }

  const shouldAttack = f.aiState.attackTimer <= 0 && armyPower >= diff.aiSquadMin;
  if (!shouldAttack) return;

  f.aiState.attackTimer = diff.aiAttackDelay + 6 + Math.random() * 10;
  f.aiState.rallyAngle += .55 + Math.random() * .75;
  const target = this.pickStrategicTargetV2(f.id);
  if (!target) return;
  f.aiState.lastTargetId = target.id;
  const squadLimit = Math.min(idleArmy.length, Math.max(diff.aiSquadMin, 8 + Math.floor(armyPower / 2)));
  const squad = idleArmy
    .sort((a, b) => {
      const roleA = a.type === 'monk' ? 2 : a.type === 'archer' ? 0 : 1;
      const roleB = b.type === 'monk' ? 2 : b.type === 'archer' ? 0 : 1;
      return roleA - roleB;
    })
    .slice(0, squadLimit);
  for (const u of squad) this.orderAttack(u, target, true);
  for (const monk of squad.filter(u => u.type === 'monk').slice(0, 2)) this.orderMoveFormation([monk], stage.x - 30, stage.y + 24, false);

  if (Math.random() < .35 * diff.aggression) {
    const exposed = this.units.find(u => u.faction !== f.id && !u.dead && u.type === 'worker' && this.factions[u.faction]?.alive);
    if (exposed) {
      for (const raider of idleArmy.filter(u => u.type !== 'monk').slice(0, 3)) this.orderAttack(raider, exposed, true);
    }
  }
};

Game.prototype.pickStrategicTargetV2 = function(fid) {
  let best = null, score = Infinity;
  const own = this.factions[fid];
  for (const b of this.buildings) {
    if (b.dead || b.faction === fid || !this.factions[b.faction].alive || b.build < 1) continue;
    const baseD = Math.sqrt(dist2(own.base.x, own.base.y, b.x, b.y));
    const enemyArmyNear = this.units.filter(u => u.faction === b.faction && !u.dead && dist2(u.x, u.y, b.x, b.y) < 620 * 620).length;
    const friendlyPressure = this.units.filter(u => u.faction === fid && !u.dead && dist2(u.x, u.y, b.x, b.y) < 720 * 720).length;
    const value = b.type === 'castle' ? -520 : b.type === 'tower' ? 220 : b.type === 'house' ? -80 : -180;
    const hpPenalty = (b.hp / b.maxHp) * 90;
    const s = baseD + enemyArmyNear * 58 - friendlyPressure * 24 + value + hpPenalty + Math.random() * 240;
    if (s < score) { score = s; best = b; }
  }
  return best || this.pickStrategicTarget(fid);
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
    this.sfx.build(this.audioGainAt(damaged.x, damaged.y));
  
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

Game.prototype.placementIssue = function(type, x, y, ignoreBuilding = null) {
  const def = BUILDINGS[type];
  if (!def) return 'Unknown building.';
  const footprint = getBuildingFootprintRect(type, x, y, 8);
  if (footprint.x < 32 || footprint.y < 32 || footprint.x + footprint.w > WORLD_W - 32 || footprint.y + footprint.h > WORLD_H - 32) return 'Too close to the world edge.';

  const terrainPads = [
    [0, 0],
    [-footprint.w * .45, -footprint.h * .42],
    [ footprint.w * .45, -footprint.h * .42],
    [-footprint.w * .45,  footprint.h * .42],
    [ footprint.w * .45,  footprint.h * .42]
  ];
  const probeY = footprint.y + footprint.h / 2;
  if (!terrainPads.every(([ox, oy]) => this.isSafeLand(x + ox, probeY + oy, 12))) return 'Need a clear patch of land.';

  const nearbyB = this.nearbyBuildings ? this.nearbyBuildings(x, y, Math.max(def.w, def.h) + 180) : this.buildings;
  for (const b of nearbyB) {
    if (b.dead || b === ignoreBuilding) continue;
    const other = getBuildingFootprintRect(b, undefined, undefined, 16);
    if (rectsOverlap(footprint, other)) return `Blocked by ${BUILDINGS[b.type]?.label || 'building'}.`;
  }

  const nearbyR = this.nearbyResources ? this.nearbyResources(x, y, Math.max(def.w, def.h) + 170) : this.resources;
  for (const r of nearbyR) {
    if (r.dead || r.amount <= 0) continue;
    const fp = getResourceFootprint(r);
    const resourceRect = { x: r.x - fp - 7, y: r.y - fp - 7, w: fp * 2 + 14, h: fp * 2 + 14 };
    if (rectsOverlap(footprint, resourceRect)) {
      return r.animal ? `Blocked by ${getAnimalLabel(r)}.` : `Blocked by ${r.type === 'tree' ? 'wood' : r.type}.`;
    }
  }

  return null;
};

Game.prototype.canPlace = function(type, x, y, ignoreBuilding = null) {
  return !this.placementIssue(type, x, y, ignoreBuilding);
};

Game.prototype.relocateBuilding = function(b, x, y) {
  if (!b || b.dead || b.faction !== 0) return false;
  const issue = this.placementIssue(b.type, x, y, b);
  if (issue) return issue;
  b.x = x;
  b.y = y;
  this.buildingBuckets = null;
  if (b.rally) b.rally = { x, y: y + 190 };
  this.clearOverlapsAroundStructures();
  this.markNavDirty && this.markNavDirty();
  this.uiDirty = true;
  return false;
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
    this.clearUnitPath && this.clearUnitPath(u);
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
  
  const buildingCandidates = this.nearbyBuildings ? this.nearbyBuildings(x, y, 280) : this.buildings;
  for (const b of buildingCandidates) {
    if (b.dead || b.build < 0.1) continue;
    const brect = getBuildingFootprintRect(b, undefined, undefined, 6);
    if (rectsOverlap(rect, brect)) return true;
  }
  
  const resourceCandidates = this.nearbyResources ? this.nearbyResources(x, y, 180) : this.resources;
  for (const res of resourceCandidates) {
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
  if (!this.running) return;
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
    this.autosaveIfDue && this.autosaveIfDue(dt);
    this.cleanup();
  }
  this.uiTimer -= dt;
  if (this.uiDirty || this.uiTimer <= 0) {
    this.renderUI();
    this.uiTimer = .25;
    this.uiDirty = false;
  }
};


// Pass 2: worker-built construction, built-in tower archers, stronger path movement, formations, attack pings, smarter AI.
Game.prototype.normalizeTowerStats = function(b) {
  if (!b || b.type !== 'tower') return;
  const hp = UNITS.archer.hp;
  if (b.maxHp !== hp) {
    const pct = b.maxHp ? clamp(b.hp / b.maxHp, 0, 1) : 1;
    b.maxHp = hp;
    b.hp = Math.max(1, Math.ceil(hp * pct));
  }
  b.garrison = [];
  b.builtInArcher = true;
};

Game.prototype.hasActiveBuilder = function(b) {
  return this.units.some(u => !u.dead && u.faction === b.faction && u.type === 'worker' && u.order === 'repair' && u.target === b);
};

Game.prototype.assignBuildersTo = function(b, fid, maxBuilders = 2, preferSelected = false) {
  if (!b || b.dead) return 0;
  const selected = preferSelected ? this.selected.filter(u => u.entity === 'unit' && u.faction === fid && u.type === 'worker' && !u.dead) : [];
  const existing = this.units.filter(u => u.faction === fid && u.type === 'worker' && !u.dead && u.order === 'repair' && u.target === b);
  const chosen = [...existing];
  const addCandidate = (u) => {
    if (!u || chosen.includes(u) || u.dead || u.faction !== fid || u.type !== 'worker') return;
    chosen.push(u);
  };
  selected.forEach(addCandidate);
  const pool = this.units
    .filter(u => u.faction === fid && u.type === 'worker' && !u.dead && !chosen.includes(u))
    .sort((a, c) => {
      const idleA = a.order === 'idle' ? -800000 : 0;
      const idleC = c.order === 'idle' ? -800000 : 0;
      return dist2(a.x, a.y, b.x, b.y) + idleA - (dist2(c.x, c.y, b.x, b.y) + idleC);
    });
  for (const u of pool) {
    if (chosen.length >= maxBuilders) break;
    addCandidate(u);
  }
  let count = 0;
  for (const u of chosen.slice(0, maxBuilders)) {
    this.clearUnitPath && this.clearUnitPath(u);
    u.order = 'repair'; u.target = b; u.goal = null; u.carry = null; u.gather = 0; u.hold = false;
    count++;
  }
  return count;
};

Game.prototype.buildingApproachPoint = function(b, u) {
  const rect = getBuildingFootprintRect(b, undefined, undefined, 10);
  const cx = clamp(u.x, rect.x, rect.x + rect.w);
  const cy = clamp(u.y, rect.y, rect.y + rect.h);
  let dx = u.x - b.x, dy = u.y - b.y;
  const d = Math.hypot(dx, dy) || 1;
  dx /= d; dy /= d;
  const px = clamp(cx + dx * 28, 24, WORLD_W - 24);
  const py = clamp(cy + dy * 28, 24, WORLD_H - 24);
  return this.nearestLandPoint(px, py, 120) || { x: px, y: py };
};

Game.prototype.updateBuildings = function(dt) {
  this.rebuildBuildingSpatialIndex && this.rebuildBuildingSpatialIndex();
  for (const b of this.buildings) {
    if (b.dead) continue;
    if (b.type === 'tower') this.normalizeTowerStats(b);
    b.flash = Math.max(0, b.flash - dt * 3);
    b.cd = Math.max(0, (b.cd || 0) - dt);
    if (b.build < 1) continue;
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
    if (b.type === 'tower') this.towerAttack(b, dt);
    if (b.type === 'monastery') this.passiveHeal(b, dt);
  }
};

Game.prototype.updateWorker = function(u, dt) {
  if (u.order === 'repair') {
    const b = u.target;
    if (!b || b.dead || (b.build >= 1 && b.hp >= b.maxHp)) { u.order = 'idle'; u.target = null; return; }
    const p = this.buildingApproachPoint(b, u);
    if (this.moveToward(u, p.x, p.y, dt, 12)) {
      u.face = b.x >= u.x ? 1 : -1;
      u.gather += dt;
      if (Math.random() < dt * 3.5) this.effects.push({ kind: 'dust', x: u.x + (Math.random() - .5) * 12, y: u.y - 4, time: .34, max: .34 });
      if (b.build < 1) {
        b.build = Math.min(1, b.build + dt / b.buildTime * 1.35);
        b.hp = Math.max(1, Math.min(b.maxHp, b.maxHp * (.18 + b.build * .82)));
        if (b.build >= 1) {
          b.hp = b.maxHp;
          b.completedAt = this.time;
          this.clearOverlapsAroundStructures && this.clearOverlapsAroundStructures();
          this.markNavDirty && this.markNavDirty();
          if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(this.audioGainAt(b.x, b.y)); }
          u.order = 'idle'; u.target = null; u.gather = 0;
        }
      } else if (b.hp < b.maxHp) {
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.06);
        if (b.hp >= b.maxHp) { u.order = 'idle'; u.target = null; u.gather = 0; if (b.faction === 0) this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.2); }
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
      const p = this.buildingApproachPoint ? this.buildingApproachPoint(drop, u) : drop;
      if (this.moveToward(u, p.x, p.y, dt, 14)) {
        addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
        u.carry = null; u.gather = 0;
        if (res && !res.dead && res.amount > 0) u.order = 'harvest';
        else { u.order = 'idle'; u.target = null; this.autoGather(u); }
      }
      return;
    }
    if (res.type === 'food' && res.animal) {
      const strikeRange = res.r + 6;
      this.moveToward(u, res.x, res.y, dt, strikeRange);
      if (dist2(u.x, u.y, res.x, res.y) <= (strikeRange + 22) * (strikeRange + 22)) {
        u.face = res.x >= u.x ? 1 : -1; u.gather += dt;
        if (u.gather >= .55) { u.gather = 0; u.huntSwing = .42; this.strikeAnimal(u, res); }
      } else u.gather = 0;
      return;
    }
    const interact = getResourceInteractionPoint(res);
    this.moveToward(u, interact.x, interact.y, dt, res.r + 6);
    if (dist2(u.x, u.y, interact.x, interact.y) <= (res.r + 26) * (res.r + 26)) {
      u.gather += dt; u.face = interact.x >= u.x ? 1 : -1;
      const gatherTime = res.type === 'tree' ? 1.35 : res.type === 'gold' ? 1.6 : .82;
      if (u.gather >= gatherTime) {
        const amount = res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14;
        res.amount -= amount;
        u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
        u.gather = 0;
        if (res.amount <= 0) {
          if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; this.markNavDirty && this.markNavDirty(); }
          else { res.dead = true; this.markNavDirty && this.markNavDirty(); }
          this.resourceBuckets = null;
          this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
        }
      }
    } else u.gather = 0;
    return;
  }
  this.updateFighter(u, dt);
  if (u.order === 'idle') this.autoGather(u);
};

Game.prototype.towerAttack = function(b, dt) {
  if (!b || b.dead || b.build < 1) return;
  if (b.cd > 0) return;
  const target = this.nearestEnemy({ x: b.x, y: b.y, faction: b.faction }, BUILDINGS.tower.range, true);
  if (!target) return;
  b.cd = UNITS.archer.cd * .92;
  this.spawnProjectile(b.faction, b.x, b.y - b.h * .72, target, UNITS.archer.damage);
};

Game.prototype.updateUnits = function(dt) {
  this.rebuildUnitSpatialIndex();
  for (const u of this.units) {
    if (u.dead) continue;
    if (u.garrisoned) { u.garrisoned = null; if (u.order === 'garrison') u.order = 'idle'; }
    u.flash = Math.max(0, u.flash - dt * 4);
    u.cd = Math.max(0, u.cd - dt);
    u.huntSwing = Math.max(0, (u.huntSwing || 0) - dt);
    u.lastWaterBounce = Math.max(0, (u.lastWaterBounce || 0) - dt);
    const activeMove = u.order === 'move' || u.order === 'attackMove' || (u.order === 'harvest' && !u.gather && !u.huntSwing);
    u.anim += dt * (activeMove ? 8 : u.order === 'attack' || u.order === 'heal' || u.huntSwing > 0 ? 8 : 4);
    const oldX = u.x, oldY = u.y;
    if (u.type === 'monk') this.updateMonk(u, dt);
    else if (u.type === 'worker') this.updateWorker(u, dt);
    else this.updateFighter(u, dt);
    this.separate(u, dt);
    if (this.isWater(u.x, u.y)) this.nudgeUnitToLand(u);
    u.x = clamp(u.x, 20, WORLD_W - 20); u.y = clamp(u.y, 20, WORLD_H - 20);
    if (activeMove) {
      const d2 = dist2(oldX, oldY, u.x, u.y);
      const minStep = u.speed * dt * 0.15;
      if (d2 < minStep * minStep) u.trafficJam = (u.trafficJam || 0) + dt;
      else u.trafficJam = 0;
    } else u.trafficJam = 0;
  }
};

Game.prototype.moveToward = function(u, x, y, dt, stop = 6) {
  const dx = x - u.x, dy = y - u.y;
  const d = Math.hypot(dx, dy);
  if (d <= stop) { this.clearUnitPath && this.clearUnitPath(u); u.stuck = 0; return true; }
  let tx = x, ty = y;
  const directSamples = Math.ceil(Math.min(18, Math.max(5, d / 38)));
  if (d < 180 && this.isSegmentWalkable && this.isSegmentWalkable(u, u.x, u.y, x, y, directSamples)) this.clearUnitPath && this.clearUnitPath(u);
  const path = this.prepareUnitPath ? this.prepareUnitPath(u, x, y, d) : null;
  if (path && path.length) { const wp = this.nextPathWaypoint(u, x, y); tx = wp.x; ty = wp.y; }
  const pdx = tx - u.x, pdy = ty - u.y;
  const pd = Math.hypot(pdx, pdy) || d;
  const sp = u.speed * dt * (u.carry ? .77 : 1);
  const step = Math.min(sp, Math.max(0, pd - Math.min(stop, 8)));
  if (step <= .001) return false;
  let nx = u.x + pdx / pd * step;
  let ny = u.y + pdy / pd * step;
  if (this.isBlocked(nx, ny, u)) {
    const angle = Math.atan2(pdy, pdx);
    let found = false;
    const bias = (u.pathProbe || 0) % 2 ? -1 : 1;
    const turns = [Math.PI / 8 * bias, -Math.PI / 8 * bias, Math.PI / 4 * bias, -Math.PI / 4 * bias, Math.PI / 2, -Math.PI / 2, Math.PI * .75, -Math.PI * .75];
    for (const turn of turns) {
      const a = angle + turn;
      const tx2 = u.x + Math.cos(a) * step;
      const ty2 = u.y + Math.sin(a) * step;
      if (!this.isBlocked(tx2, ty2, u)) { nx = tx2; ny = ty2; found = true; break; }
    }
    if (!found) {
      u.stuck = (u.stuck || 0) + dt;
      if (u.stuck > .36) {
        u.pathProbe = (u.pathProbe || 0) + 1;
        this.clearUnitPath && this.clearUnitPath(u);
        u.pathRetry = 0;
        const p = this.nearestLandPoint(u.x + (Math.random() - .5) * 72, u.y + (Math.random() - .5) * 72, 160);
        if (p && !this.isBlocked(p.x, p.y, u)) { u.x = p.x; u.y = p.y; }
        u.stuck = .08;
      }
      return false;
    }
  } else u.stuck = 0;
  u.x = nx; u.y = ny; u.face = pdx >= 0 ? 1 : -1;
  return false;
};

Game.prototype.separate = function(u, dt) {
  let sx = 0, sy = 0;
  for (const v of this.nearbyUnits(u.x, u.y)) {
    if (v === u || v.dead) continue;
    let min = u.r + v.r + 2;
    const sameGoal = u.goal && v.goal && dist2(u.goal.x, u.goal.y, v.goal.x, v.goal.y) < 18 * 18;
    if (sameGoal || (u.type === 'worker' && v.type === 'worker' && u.target && u.target === v.target)) min *= .55;
    const dx = u.x - v.x, dy = u.y - v.y;
    const d2 = dx * dx + dy * dy;
    if (d2 > 0 && d2 < min * min) { const d = Math.sqrt(d2); sx += dx / d * (min - d); sy += dy / d * (min - d); }
  }
  if (sx || sy) {
    const nx = u.x + sx * dt * 2.0;
    const ny = u.y + sy * dt * 2.0;
    if (!this.isBlocked(nx, ny, u)) { u.x = nx; u.y = ny; }
  }
};

Game.prototype.formationOrderedUnits = function(units, mode) {
  const rank = (u) => mode === 'split' ? (u.type === 'warrior' || u.type === 'lancer' ? 0 : u.type === 'archer' ? 1 : u.type === 'monk' ? 2 : 3) : 0;
  return units.slice().sort((a, b) => rank(a) - rank(b) || a.id - b.id);
};

Game.prototype.formationLocalOffset = function(index, count, unit, mode, spacing) {
  if (count <= 1) return { x: 0, y: 0 };
  if (mode === 'line') return { x: (index - (count - 1) / 2) * spacing, y: 0 };
  if (mode === 'wedge') {
    if (index === 0) return { x: 0, y: -spacing * .7 };
    const row = Math.ceil((Math.sqrt(8 * index + 1) - 1) / 2);
    const prev = row * (row - 1) / 2;
    const pos = index - prev;
    return { x: (pos - (row - 1) / 2) * spacing, y: row * spacing * .72 };
  }
  if (mode === 'split') {
    const roleY = (unit.type === 'warrior' || unit.type === 'lancer') ? -spacing * .65 : unit.type === 'archer' ? spacing * .35 : unit.type === 'monk' ? spacing * 1.2 : spacing * 1.65;
    return { x: (index - (count - 1) / 2) * spacing * .75, y: roleY };
  }
  const cols = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / cols);
  return { x: ((index % cols) - (cols - 1) / 2) * spacing, y: (Math.floor(index / cols) - (rows - 1) / 2) * spacing };
};

Game.prototype.orderMoveFormation = function(units, x, y, attackMove) {
  const movable = units.filter(u => u && u.entity === 'unit' && !u.dead);
  if (!movable.length) return;
  const land = this.nearestLandPoint(x, y, 360) || { x, y };
  const cx = movable.reduce((s, u) => s + u.x, 0) / movable.length;
  const cy = movable.reduce((s, u) => s + u.y, 0) / movable.length;
  const angle = Math.atan2(land.y - cy, land.x - cx);
  const fx = Math.cos(angle), fy = Math.sin(angle);
  const rx = -fy, ry = fx;
  const mode = FORMATION_MODES[this.formationMode] ? this.formationMode : 'box';
  const ordered = this.formationOrderedUnits(movable, mode);
  const spacing = FORMATION_MODES[mode].spacing;
  ordered.forEach((u, i) => {
    const o = this.formationLocalOffset(i, ordered.length, u, mode, spacing + (u.r || 12) * .35);
    const gx = clamp(land.x + rx * o.x + fx * o.y, 30, WORLD_W - 30);
    const gy = clamp(land.y + ry * o.x + fy * o.y, 30, WORLD_H - 30);
    const p = this.nearestLandPoint(gx, gy, 180) || land;
    this.clearUnitPath && this.clearUnitPath(u);
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
  const buildingCandidates = this.nearbyBuildings ? this.nearbyBuildings(x, y, 280) : this.buildings;
  for (const b of buildingCandidates) {
    if (b.dead || b.build < 0.1) continue;
    const brect = getBuildingFootprintRect(b, undefined, undefined, 5);
    if (rectsOverlap(rect, brect)) return true;
  }
  const resourceCandidates = this.nearbyResources ? this.nearbyResources(x, y, 180) : this.resources;
  for (const res of resourceCandidates) {
    if (res.dead || res.amount <= 0 || res.animal) continue;
    const rr = Math.max(8, getResourceBlockingRadius(res) * .58);
    const resRect = { x: res.x - rr, y: res.y - rr, w: rr * 2, h: rr * 2 };
    if (rectsOverlap(rect, resRect) && (!u || u.target !== res)) return true;
  }
  return false;
};

Game.prototype.shouldWarnPlayerAttack = function(target, sourceFaction) {
  if (!target || target.faction !== 0 || sourceFaction === 0) return false;
  if (target.entity === 'building') return true;
  return this.buildings.some(b => b.faction === 0 && !b.dead && b.build >= 1 && dist2(target.x, target.y, b.x, b.y) < 560 * 560);
};

Game.prototype.damage = function(target, amount, sourceFaction) {
  if (!isAlive(target)) return;
  if (target.type === 'tower') this.normalizeTowerStats(target);
  target.hp -= amount;
  target.flash = 1;
  if (amount > 0) this.sfx.hit(this.audioGainAt(target.x, target.y));
  if (target.entity === 'building') this.factions[target.faction].underAttack = 5;
  if (this.shouldWarnPlayerAttack(target, sourceFaction)) {
    if (!this.lastPlayerAttackAlert || this.time - this.lastPlayerAttackAlert > 5.5) {
      this.lastPlayerAttackAlert = this.time;
      this.sfx.alert(this.audioGainAt(target.x, target.y, 1500));
      this.toast('Your realm is under attack.', 2.0);
    }
    this.attackPings = this.attackPings || [];
    this.attackPings.push({ x: target.x, y: target.y, start: this.time, until: this.time + 6.0 });
  }
  if (target.hp <= 0) {
    target.dead = true;
    this.effects.push({ kind: 'boom', x: target.x, y: target.y - 30, time: 1.0, max: 1.0 });
    if (target.entity === 'building') { this.buildingBuckets = null; this.markNavDirty && this.markNavDirty(); }
    this.checkDefeat(target.faction);
  }
};

Game.prototype.cleanup = function() {
  this.projectiles = this.projectiles.filter(p => !p.dead);
  this.effects = this.effects.filter(e => e.time > 0);
  this.selected = this.selected.filter(isAlive);
  this.attackPings = (this.attackPings || []).filter(p => p.until > this.time);
};

Game.prototype.update = function(dt) {
  this.time += dt;
  if (this.toastTimer > 0) { this.toastTimer -= dt; if (this.toastTimer <= 0) HUD.message.classList.add('hidden'); }
  this.updateCamera(dt);
  if (!this.paused) {
    this.updateBuildings(dt);
    this.updateResources(dt);
    this.updateUnits(dt);
    this.updateProjectiles(dt);
    this.updateEffects(dt);
    this.updateAI(dt);
    this.autosaveIfDue && this.autosaveIfDue(dt);
    this.cleanup();
  }
  this.uiTimer -= dt;
  if (this.uiDirty || this.uiTimer <= 0) { this.renderUI(); this.uiTimer = .25; this.uiDirty = false; }
};

Game.prototype.repairSelected = function() {
  const target = this.selected.find(e => e.entity === 'building' && e.faction === 0 && (e.hp < e.maxHp || e.build < 1));
  if (!target) return;
  const cost = target.build < 1 ? { wood: 0, gold: 0, food: 0 } : { wood: 24, gold: 14, food: 0 };
  if ((cost.wood || cost.gold) && !pay(this.factions[0], cost)) { this.toast('Need wood and gold to repair.', 1.2); this.sfx.deny(); return; }
  const n = this.assignBuildersTo(target, 0, 3, false);
  if (n) { this.toast(`${n} worker(s) assigned to ${target.build < 1 ? 'build' : 'repair'}.`, 1.3); this.sfx.click(); }
  else { this.toast('No worker available.', 1.2); this.sfx.deny(); }
};

Game.prototype.finishGarrison = function() { return false; };
Game.prototype.updateGarrisonUnit = function(u) { if (u) { u.order = 'idle'; u.garrisoned = null; u.target = null; } };

Game.prototype.aiBuild = function(f) {
  const ownB = this.buildings.filter(b => b.faction === f.id && !b.dead);
  const count = (t) => ownB.filter(b => b.type === t).length;
  const pop = this.population(f.id);
  const candidates = [];
  if (pop.cap - pop.used < 5) candidates.push('house');
  if (count('barracks') < 1) candidates.push('barracks');
  if (count('archery') < 1 && count('barracks') >= 1) candidates.push('archery');
  if (count('tower') < 2 + Math.floor(f.aiState.expansion * 1.4)) candidates.push('tower');
  if (pop.used > 22 && count('castle') < 2 && f.res.wood > BUILDINGS.castle.cost.wood && f.res.gold > BUILDINGS.castle.cost.gold) candidates.push('castle');
  if (count('monastery') < 1 && pop.used > 12) candidates.push('monastery');
  if (pop.used > 24 && count('barracks') < 2) candidates.push('barracks');
  if (pop.used > 28 && count('archery') < 2) candidates.push('archery');
  if (!candidates.length && Math.random() < .22) candidates.push(choose(['house','tower','archery','barracks']));
  for (const t of candidates) {
    const def = BUILDINGS[t];
    if (!canAfford(f, def.cost)) continue;
    const anchor = this.aiBuildAnchor(f, t);
    const pos = this.findBuildSpot(anchor.x, anchor.y, t, f.aiState.expansion);
    if (pos && pay(f, def.cost)) {
      const b = this.addBuilding(f.id, t, pos.x, pos.y, false);
      b.aiIntent = t;
      this.assignBuildersTo(b, f.id, t === 'castle' ? 5 : t === 'tower' ? 2 : 3, false);
      f.aiState.expansion = Math.min(5, f.aiState.expansion + (t === 'castle' ? .65 : .12));
      return;
    }
  }
};

Game.prototype.aiBuildAnchor = function(f, type) {
  if (type === 'tower') return this.aiFrontlinePosition(f);
  if (type === 'castle') return this.aiSecondBaseAnchor(f) || this.aiFrontlinePosition(f);
  const castles = this.buildings.filter(b => b.faction === f.id && b.type === 'castle' && !b.dead && b.build >= 1);
  if (castles.length > 1 && Math.random() < .38) return castles[Math.floor(Math.random() * castles.length)];
  return f.base;
};

Game.prototype.aiSecondBaseAnchor = function(f) {
  const enemyBases = this.factions.filter(x => x.id !== f.id && x.alive).map(x => x.base);
  let best = null, score = -Infinity;
  for (const r of this.resources) {
    if (r.dead || r.amount <= 0 || r.animal) continue;
    const ownDist = Math.sqrt(dist2(r.x, r.y, f.base.x, f.base.y));
    if (ownDist < 900 || ownDist > 3600) continue;
    const enemyDist = Math.min(...enemyBases.map(b => Math.sqrt(dist2(r.x, r.y, b.x, b.y))));
    if (enemyDist < 850) continue;
    const value = (r.type === 'gold' ? 480 : 260) + Math.min(700, r.amount) - ownDist * .08 + enemyDist * .03 + Math.random() * 120;
    if (value > score) { score = value; best = r; }
  }
  return best ? { x: best.x + (Math.random() - .5) * 220, y: best.y + (Math.random() - .5) * 220 } : null;
};

Game.prototype.aiTactics = function(f) {
  const diff = DIFFICULTY_PRESETS[this.worldSettings?.difficulty || 'normal'] || DIFFICULTY_PRESETS.normal;
  const army = this.units.filter(u => u.faction === f.id && u.type !== 'worker' && !u.dead);
  const idleArmy = army.filter(u => u.order === 'idle' || u.order === 'move' || u.order === 'attackMove');
  const workers = this.units.filter(u => u.faction === f.id && u.type === 'worker' && !u.dead);
  const threat = this.nearestThreatToBase(f.id, f.base.x, f.base.y, f.underAttack > 0 ? 1350 : 940);
  if (threat && idleArmy.length) {
    const defenders = idleArmy.sort((a, b) => dist2(a.x, a.y, threat.x, threat.y) - dist2(b.x, b.y, threat.x, threat.y)).slice(0, Math.min(idleArmy.length, f.underAttack > 0 ? 24 : 14));
    for (const u of defenders) this.orderAttack(u, threat, false);
    const repairTarget = this.buildings.find(b => b.faction === f.id && !b.dead && b.hp < b.maxHp && dist2(b.x, b.y, threat.x, threat.y) < 720 * 720);
    if (repairTarget) this.assignBuildersTo(repairTarget, f.id, 4, false);
    return;
  }
  for (const b of this.buildings) if (b.faction === f.id && !b.dead && b.build < 1 && !this.hasActiveBuilder(b)) this.assignBuildersTo(b, f.id, b.type === 'castle' ? 5 : 3, false);
  const armyPower = army.reduce((sum, u) => sum + (u.type === 'lancer' ? 2.1 : u.type === 'archer' ? 1.25 : u.type === 'monk' ? .85 : 1), 0);
  f.aiState.attackTimer -= diff.aggression;
  const frontline = this.aiFrontlinePosition(f);
  const stage = this.nearestLandPoint(frontline.x + Math.cos(f.aiState.rallyAngle) * (180 + f.aiState.expansion * 40), frontline.y + Math.sin(f.aiState.rallyAngle) * (180 + f.aiState.expansion * 40), 280) || frontline;
  if (idleArmy.length >= Math.max(3, diff.aiSquadMin - 2)) for (const u of idleArmy.slice(0, Math.min(idleArmy.length, 12))) if (dist2(u.x, u.y, f.base.x, f.base.y) < 760 * 760) this.orderMoveFormation([u], stage.x, stage.y, true);
  const wounded = army.filter(u => u.hp / u.maxHp < .35 && u.order !== 'move');
  for (const u of wounded.slice(0, 5)) this.orderMoveFormation([u], f.base.x + (Math.random() - .5) * 260, f.base.y + (Math.random() - .5) * 260, false);
  this.aiHarassEconomy(f, idleArmy, diff);
  if (f.aiState.attackTimer > 0 || armyPower < diff.aiSquadMin) return;
  f.aiState.attackTimer = diff.aiAttackDelay + 6 + Math.random() * 10;
  f.aiState.rallyAngle += .55 + Math.random() * .75;
  const target = this.pickStrategicTargetV2(f.id);
  if (!target) return;
  f.aiState.lastTargetId = target.id;
  const squadLimit = Math.min(idleArmy.length, Math.max(diff.aiSquadMin, 8 + Math.floor(armyPower / 2)));
  const squad = this.formationOrderedUnits(idleArmy, 'split').slice(0, squadLimit);
  for (const u of squad) this.orderAttack(u, target, true);
};

Game.prototype.aiHarassEconomy = function(f, idleArmy, diff) {
  if (!idleArmy.length || Math.random() > .28 * diff.aggression) return;
  const raiders = idleArmy.filter(u => u.type === 'lancer' || u.type === 'warrior' || u.type === 'archer').slice(0, 3);
  if (!raiders.length) return;
  let best = null, score = Infinity;
  for (const u of this.units) {
    if (u.dead || u.type !== 'worker' || u.faction === f.id || !this.factions[u.faction]?.alive) continue;
    const nearDefense = this.buildings.some(b => b.faction === u.faction && !b.dead && (b.type === 'tower' || b.type === 'castle') && dist2(b.x, b.y, u.x, u.y) < (b.type === 'tower' ? 430 : 520) ** 2);
    const d = dist2(f.base.x, f.base.y, u.x, u.y) + (nearDefense ? 900000 : 0) + Math.random() * 120000;
    if (d < score) { score = d; best = u; }
  }
  if (best) for (const r of raiders) this.orderAttack(r, best, true);
};


// Pass 2: no-garrison spatial index override for old saved worlds.
Game.prototype.rebuildUnitSpatialIndex = function() {
  const bucketSize = 72;
  this.unitBuckets = new Map();
  this.unitBucketSize = bucketSize;
  for (const u of this.units) {
    if (u.dead) continue;
    if (u.garrisoned) u.garrisoned = null;
    const key = `${Math.floor(u.x / bucketSize)},${Math.floor(u.y / bucketSize)}`;
    let list = this.unitBuckets.get(key);
    if (!list) this.unitBuckets.set(key, list = []);
    list.push(u);
  }
};


// Pass 2: monk heal animation override.
Game.prototype.updateMonk = function(u, dt) {
  u.healAnim = Math.max(0, (u.healAnim || 0) - dt);
  const ally = this.lowestHurtAlly(u, UNITS.monk.range);
  if (ally && u.cd <= 0) {
    u.face = ally.x >= u.x ? 1 : -1;
    ally.hp = Math.min(ally.maxHp, ally.hp + 18);
    u.cd = UNITS.monk.cd;
    u.healAnim = .62;
    this.effects.push({ kind: 'heal', x: ally.x, y: ally.y - 28, time: .62, max: .62 });
    this.sfx.heal(this.audioGainAt(ally.x, ally.y));
    u.order = 'heal';
    return;
  }
  if (u.order === 'heal' && u.healAnim <= 0) u.order = 'idle';
  if (u.healAnim > 0) return;
  this.updateFighter(u, dt);
};


// Pass 3: cargo-safe workers, robust building approach points, and economy role assignment.
Game.prototype.isBlocked = function(x, y, u, ignoreBuilding = null) {
  if (this.isWater(x, y)) return true;
  const r = u ? u.r || 8 : 8;
  const rect = { x: x - r, y: y - r, w: r * 2, h: r * 2 };
  const buildingCandidates = this.nearbyBuildings ? this.nearbyBuildings(x, y, 300) : this.buildings;
  for (const b of buildingCandidates) {
    if (b === ignoreBuilding || b.dead || b.build < 0.1) continue;
    const brect = getBuildingFootprintRect(b, undefined, undefined, 5);
    if (rectsOverlap(rect, brect)) return true;
  }
  const resourceCandidates = this.nearbyResources ? this.nearbyResources(x, y, 190) : this.resources;
  for (const res of resourceCandidates) {
    if (res.dead || res.amount <= 0 || res.animal) continue;
    const rr = Math.max(8, getResourceBlockingRadius(res) * .54);
    const resRect = { x: res.x - rr, y: res.y - rr, w: rr * 2, h: rr * 2 };
    if (rectsOverlap(rect, resRect) && (!u || u.target !== res)) return true;
  }
  return false;
};

Game.prototype.buildingApproachPoint = function(b, u) {
  if (!b) return { x: u ? u.x : 0, y: u ? u.y : 0 };
  if (!u) return { x: b.x, y: b.y + b.h * .52 };
  const rect = getBuildingFootprintRect(b, undefined, undefined, 7);
  const pad = (u.r || 10) + 20;
  const candidates = [];
  const add = (x, y, sideBias = 0) => {
    x = clamp(x, 24, WORLD_W - 24);
    y = clamp(y, 24, WORLD_H - 24);
    if (this.isWater(x, y)) {
      const land = this.nearestLandPoint(x, y, 120);
      if (land) { x = land.x; y = land.y; }
    }
    candidates.push({ x, y, sideBias });
  };
  for (let t = .15; t <= .85; t += .175) {
    const px = rect.x + rect.w * t;
    const py = rect.y + rect.h * t;
    add(px, rect.y - pad, 0);
    add(px, rect.y + rect.h + pad, -1200);
    add(rect.x - pad, py, 0);
    add(rect.x + rect.w + pad, py, 0);
  }
  add(rect.x - pad, rect.y - pad, 600);
  add(rect.x + rect.w + pad, rect.y - pad, 600);
  add(rect.x - pad, rect.y + rect.h + pad, 0);
  add(rect.x + rect.w + pad, rect.y + rect.h + pad, 0);

  let best = null, bestScore = Infinity;
  for (const c of candidates) {
    const blocked = this.isBlocked(c.x, c.y, u, b);
    const pathBlocked = this.isSegmentWalkable && !this.isSegmentWalkable(u, u.x, u.y, c.x, c.y, 7);
    let score = dist2(u.x, u.y, c.x, c.y) + c.sideBias;
    if (blocked) score += 1500000;
    if (pathBlocked) score += 70000;
    if (score < bestScore) { bestScore = score; best = c; }
  }
  if (best && !this.isBlocked(best.x, best.y, u, b)) return best;
  if (best) return best;
  return this.nearestLandPoint(b.x, b.y + b.h * .55 + pad, 180) || { x: b.x, y: b.y + b.h * .55 + pad };
};

Game.prototype.workerNearBuilding = function(u, b, extra = 34) {
  if (!u || !b) return false;
  const rect = getBuildingFootprintRect(b, undefined, undefined, 8);
  const cx = clamp(u.x, rect.x, rect.x + rect.w);
  const cy = clamp(u.y, rect.y, rect.y + rect.h);
  return dist2(u.x, u.y, cx, cy) <= Math.pow((u.r || 10) + extra, 2);
};

Game.prototype.depositWorkerCargo = function(u, drop) {
  if (!u || !u.carry || !drop) return false;
  addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
  this.effects.push({ kind: 'dust', x: u.x, y: u.y - 4, time: .38, max: .38 });
  u.carry = null;
  u.gather = 0;
  u.returning = false;
  u.depositStuck = 0;
  this.clearUnitPath && this.clearUnitPath(u);
  this.uiDirty = true;
  return true;
};

Game.prototype.findResourceForWorkerRole = function(u, role, range = 2400) {
  const type = role === 'wood' ? 'tree' : role === 'gold' ? 'gold' : role === 'food' ? 'food' : null;
  if (!type) return null;
  return this.nearestResource(u.x, u.y, type, range) || this.nearestResource(u.x, u.y, null, range);
};

Game.prototype.workerRoleMatchesResource = function(role, res) {
  if (!role || role === 'auto') return true;
  if (!res) return false;
  if (role === 'wood') return res.type === 'tree';
  if (role === 'gold') return res.type === 'gold';
  if (role === 'food') return res.type === 'food';
  return false;
};

Game.prototype.resumeWorkerRole = function(u) {
  if (!u || u.dead || u.type !== 'worker') return false;
  const role = u.workerRole;
  if (!role || role === 'auto') { this.autoGather(u); return true; }
  if (role === 'idle') { u.order = 'idle'; u.target = null; u.goal = null; return true; }
  if (role === 'build') {
    const target = this.bestBuildTargetForWorker(u);
    if (target) {
      this.clearUnitPath && this.clearUnitPath(u);
      u.order = 'repair'; u.target = target; u.goal = null; u.gather = 0; u.hold = false;
      return true;
    }
    u.order = 'idle'; u.target = null; return false;
  }
  const res = this.findResourceForWorkerRole(u, role);
  if (res) { this.orderHarvest(u, res, role); return true; }
  u.order = 'idle'; u.target = null; return false;
};

Game.prototype.orderHarvest = function(u, res, preferredRole = null) {
  if (!u || u.type !== 'worker' || !res || res.dead || res.amount <= 0) return;
  this.clearUnitPath && this.clearUnitPath(u);
  u.order = 'harvest';
  u.target = res;
  u.goal = null;
  u.gather = 0;
  u.hold = false;
  u.returning = false;
  if (preferredRole) u.workerRole = preferredRole;
  else if (res.type === 'tree') u.workerRole = 'wood';
  else if (res.type === 'gold') u.workerRole = 'gold';
  else if (res.type === 'food') u.workerRole = 'food';
};

Game.prototype.bestBuildTargetForWorker = function(u) {
  let best = null, score = Infinity;
  for (const b of this.buildings) {
    if (b.faction !== u.faction || b.dead) continue;
    if (b.build >= 1 && b.hp >= b.maxHp) continue;
    const existing = this.units.filter(v => !v.dead && v.faction === u.faction && v.type === 'worker' && v.order === 'repair' && v.target === b).length;
    const cap = b.type === 'castle' ? 5 : b.type === 'tower' ? 2 : 3;
    if (existing >= cap && u.target !== b) continue;
    const s = dist2(u.x, u.y, b.x, b.y) + existing * 90000 + (b.build < 1 ? -45000 : 0);
    if (s < score) { score = s; best = b; }
  }
  return best;
};

Game.prototype.assignWorkersRole = function(role, scope = 'selected') {
  const allWorkers = this.units.filter(u => u.faction === 0 && u.type === 'worker' && !u.dead);
  const selectedWorkers = this.selected.filter(e => e.entity === 'unit' && e.faction === 0 && e.type === 'worker' && !e.dead);
  const workers = scope === 'all' ? allWorkers : selectedWorkers;
  if (!workers.length) {
    this.toast(scope === 'all' ? 'No workers available.' : 'Select workers first or switch scope to All workers.', 1.6);
    this.sfx.deny();
    return 0;
  }

  let assigned = 0;
  if (role === 'idle') {
    for (const u of workers) {
      this.clearUnitPath && this.clearUnitPath(u);
      u.workerRole = 'idle';
      if (u.carry) {
        // Do not delete carried resources; walk them home, then stop.
        u.order = 'harvest';
        u.goal = null;
      } else {
        u.order = 'idle';
        u.target = null;
        u.goal = null;
      }
      u.gather = 0;
      assigned++;
    }
  } else if (role === 'auto') {
    for (const u of workers) {
      u.workerRole = 'auto';
      if (!u.carry) this.resumeWorkerRole(u);
      assigned++;
    }
  } else if (role === 'build') {
    for (const u of workers) {
      u.workerRole = 'build';
      if (!u.carry && this.resumeWorkerRole(u)) assigned++;
    }
  } else {
    for (const u of workers) {
      u.workerRole = role;
      if (!u.carry && this.resumeWorkerRole(u)) assigned++;
      else if (u.carry) assigned++;
    }
  }

  const label = role === 'wood' ? 'woodcutting' : role === 'gold' ? 'mining' : role === 'food' ? 'food gathering' : role === 'build' ? 'build/repair' : role === 'auto' ? 'auto economy' : 'idle';
  this.toast(`${assigned} worker(s) set to ${label}.`, 1.4);
  this.uiDirty = true;
  return assigned;
};

Game.prototype.updateWorker = function(u, dt) {
  if (u.carry && u.order !== 'harvest') {
    u.order = 'harvest';
    u.goal = null;
    this.clearUnitPath && this.clearUnitPath(u);
  }
  if (u.order === 'repair') {
    const b = u.target;
    if (!b || b.dead || (b.build >= 1 && b.hp >= b.maxHp)) {
      u.order = 'idle'; u.target = null; u.gather = 0;
      this.resumeWorkerRole(u);
      return;
    }
    const p = this.buildingApproachPoint(b, u);
    if (this.workerNearBuilding(u, b, 30) || this.moveToward(u, p.x, p.y, dt, 13)) {
      u.face = b.x >= u.x ? 1 : -1;
      u.gather += dt;
      if (Math.random() < dt * 3.5) this.effects.push({ kind: 'dust', x: u.x + (Math.random() - .5) * 12, y: u.y - 4, time: .34, max: .34 });
      if (b.build < 1) {
        b.build = Math.min(1, b.build + dt / b.buildTime * 1.35);
        b.hp = Math.max(1, Math.min(b.maxHp, b.maxHp * (.18 + b.build * .82)));
        if (b.build >= 1) {
          b.hp = b.maxHp;
          b.completedAt = this.time;
          this.clearOverlapsAroundStructures && this.clearOverlapsAroundStructures();
          this.markNavDirty && this.markNavDirty();
          if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(this.audioGainAt(b.x, b.y)); }
          u.order = 'idle'; u.target = null; u.gather = 0;
          this.resumeWorkerRole(u);
        }
      } else if (b.hp < b.maxHp) {
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.08);
        if (b.hp >= b.maxHp) {
          u.order = 'idle'; u.target = null; u.gather = 0;
          if (b.faction === 0) this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.2);
          this.resumeWorkerRole(u);
        }
      }
    }
    return;
  }

  if (u.order === 'harvest') {
    const res = u.target;

    // Cargo must be delivered even if the source node has become depleted or dead.
    if (u.carry) {
      const drop = this.nearestDropoff(u.faction, u.x, u.y);
      if (!drop) { u.order = 'idle'; u.target = null; return; }
      if (this.workerNearBuilding(u, drop, 38)) {
        this.depositWorkerCargo(u, drop);
        if (res && !res.dead && res.amount > 0 && this.workerRoleMatchesResource(u.workerRole, res)) this.orderHarvest(u, res, u.workerRole);
        else if (u.workerRole) this.resumeWorkerRole(u); else this.autoGather(u);
        return;
      }
      const p = this.buildingApproachPoint(drop, u);
      const beforeX = u.x, beforeY = u.y;
      this.moveToward(u, p.x, p.y, dt, 14);
      const moved = dist2(beforeX, beforeY, u.x, u.y);
      if (moved < 0.5) u.depositStuck = (u.depositStuck || 0) + dt;
      else u.depositStuck = 0;
      if (u.depositStuck > 1.0 && this.workerNearBuilding(u, drop, 86)) {
        this.depositWorkerCargo(u, drop);
        if (res && !res.dead && res.amount > 0 && this.workerRoleMatchesResource(u.workerRole, res)) this.orderHarvest(u, res, u.workerRole);
        else if (u.workerRole) this.resumeWorkerRole(u); else this.autoGather(u);
      }
      return;
    }

    if (!res || res.dead || res.amount <= 0) {
      u.order = 'idle'; u.target = null; u.gather = 0;
      if (u.workerRole) this.resumeWorkerRole(u); else this.autoGather(u);
      return;
    }

    if (res.type === 'food' && res.animal) {
      const strikeRange = res.r + 6;
      this.moveToward(u, res.x, res.y, dt, strikeRange);
      if (dist2(u.x, u.y, res.x, res.y) <= (strikeRange + 22) * (strikeRange + 22)) {
        u.face = res.x >= u.x ? 1 : -1; u.gather += dt;
        if (u.gather >= .55) { u.gather = 0; u.huntSwing = .42; this.strikeAnimal(u, res); }
      } else u.gather = 0;
      return;
    }

    const interact = getResourceInteractionPoint(res);
    this.moveToward(u, interact.x, interact.y, dt, res.r + 6);
    if (dist2(u.x, u.y, interact.x, interact.y) <= (res.r + 26) * (res.r + 26)) {
      u.gather += dt; u.face = interact.x >= u.x ? 1 : -1;
      const gatherTime = res.type === 'tree' ? 1.35 : res.type === 'gold' ? 1.6 : .82;
      if (u.gather >= gatherTime) {
        const amount = Math.min(res.amount, res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14);
        res.amount -= amount;
        u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
        u.gather = 0;
        u.depositStuck = 0;
        this.clearUnitPath && this.clearUnitPath(u);
        if (res.amount <= 0) {
          if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; this.markNavDirty && this.markNavDirty(); }
          else { res.dead = true; this.markNavDirty && this.markNavDirty(); }
          this.resourceBuckets = null;
          this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
        }
      }
    } else u.gather = 0;
    return;
  }

  this.updateFighter(u, dt);
  if (u.order === 'idle') {
    if (u.workerRole) this.resumeWorkerRole(u);
    else this.autoGather(u);
  }
};


// Pass 3: keep explicit Auto Balance workers in auto mode instead of converting them to the current resource.
Game.prototype.autoGather = function(u) {
  if (!u || u.dead || u.type !== 'worker') return;
  const f = this.factions[u.faction];
  const need = f.res.wood < 180 ? 'tree' : f.res.gold < 160 ? 'gold' : f.res.food < 4 ? 'food' : choose(['tree', 'gold', 'food']);
  const r = this.nearestResource(u.x, u.y, need, 1200) || this.nearestResource(u.x, u.y, null, 2200);
  if (r) this.orderHarvest(u, r, u.workerRole === 'auto' ? 'auto' : null);
};


// Pass 4: worker delivery, close construction approach, and cargo-safe job switching.
Game.prototype.entityById = function(id) {
  if (id === undefined || id === null) return null;
  for (const b of this.buildings) if (b.id === id && !b.dead) return b;
  for (const u of this.units) if (u.id === id && !u.dead) return u;
  for (const r of this.resources) if (r.id === id && !r.dead) return r;
  return null;
};

Game.prototype.rectDistanceToPoint = function(rect, x, y) {
  const dx = Math.max(rect.x - x, 0, x - (rect.x + rect.w));
  const dy = Math.max(rect.y - y, 0, y - (rect.y + rect.h));
  return Math.hypot(dx, dy);
};

Game.prototype.isBlockedIgnoringBuilding = function(x, y, u, ignoreBuilding) {
  const old = u ? u.interactionBuilding : null;
  if (u) u.interactionBuilding = ignoreBuilding || null;
  const blocked = this.isBlocked(x, y, u);
  if (u) u.interactionBuilding = old;
  return blocked;
};

Game.prototype.buildingInteractionCandidates = function(b, u, distance = 20) {
  const rect = getBuildingFootprintRect(b, undefined, undefined, 0);
  const r = (u && u.r) || 10;
  const gap = distance + r;
  const xs = [b.x, rect.x + rect.w * .24, rect.x + rect.w * .50, rect.x + rect.w * .76];
  const ys = [b.y, rect.y + rect.h * .30, rect.y + rect.h * .55, rect.y + rect.h * .82];
  const candidates = [];
  for (const px of xs) candidates.push({ x: px, y: rect.y + rect.h + gap, side: 'front' });
  for (const px of xs) candidates.push({ x: px, y: rect.y - gap, side: 'back' });
  for (const py of ys) candidates.push({ x: rect.x - gap, y: py, side: 'left' });
  for (const py of ys) candidates.push({ x: rect.x + rect.w + gap, y: py, side: 'right' });
  candidates.push({ x: b.x, y: rect.y + rect.h + gap + 6, side: 'door' });
  return candidates
    .map(p => this.nearestLandPoint(clamp(p.x, 24, WORLD_W - 24), clamp(p.y, 24, WORLD_H - 24), 80) || p)
    .filter(p => this.isSafeLand(p.x, p.y, 10) && !this.isBlockedIgnoringBuilding(p.x, p.y, u, b));
};

Game.prototype.buildingApproachPoint = function(b, u) {
  const candidates = this.buildingInteractionCandidates(b, u, 8);
  if (!candidates.length) {
    const rect = getBuildingFootprintRect(b, undefined, undefined, 0);
    const dx = u.x < rect.x ? -1 : u.x > rect.x + rect.w ? 1 : 0;
    const dy = u.y < rect.y ? -1 : u.y > rect.y + rect.h ? 1 : 0;
    return this.nearestLandPoint(
      clamp(u.x + (dx || Math.sign(u.x - b.x) || 1) * 26, 24, WORLD_W - 24),
      clamp(u.y + (dy || Math.sign(u.y - b.y) || 1) * 26, 24, WORLD_H - 24),
      90
    ) || { x: u.x, y: u.y };
  }
  const rect = getBuildingFootprintRect(b, undefined, undefined, 0);
  const preferredSide = u.y >= rect.y + rect.h * .35 ? 'front' : '';
  let best = null, bestScore = Infinity;
  for (const p of candidates) {
    const sideBonus = p.side === preferredSide || p.side === 'door' ? -1800 : 0;
    const s = dist2(u.x, u.y, p.x, p.y) + sideBonus;
    if (s < bestScore) { best = p; bestScore = s; }
  }
  return best || candidates[0];
};

Game.prototype.dropoffPoint = function(drop, u) {
  const candidates = this.buildingInteractionCandidates(drop, u, 7);
  if (!candidates.length) return this.buildingApproachPoint(drop, u);
  const front = candidates.filter(p => p.side === 'front' || p.side === 'door');
  const pool = front.length ? front : candidates;
  return pool.reduce((best, p) => !best || dist2(u.x, u.y, p.x, p.y) < dist2(u.x, u.y, best.x, best.y) ? p : best, null);
};

Game.prototype.depositWorkerCargo = function(u, drop) {
  if (!u || !u.carry || !drop || drop.dead || drop.build < 1) return false;
  addRes(this.factions[u.faction], u.carry.type, u.carry.amount);
  this.effects.push({ kind: 'dust', x: u.x, y: u.y - 4, time: .38, max: .38 });
  u.carry = null;
  u.returning = false;
  u.depositStuck = 0;
  u.gather = 0;
  this.clearUnitPath && this.clearUnitPath(u);
  this.uiDirty = true;
  return true;
};

Game.prototype.applyAfterDepositOrder = function(u) {
  const after = u.afterDeposit;
  u.afterDeposit = null;
  if (after && after.kind === 'repair') {
    const b = this.entityById(after.targetId);
    if (b && b.entity === 'building' && b.faction === u.faction && !b.dead && (b.build < 1 || b.hp < b.maxHp)) {
      u.order = 'repair'; u.target = b; u.goal = null; u.gather = 0; u.hold = false;
      return true;
    }
  }
  if (after && after.kind === 'harvest') {
    const r = this.entityById(after.targetId);
    if (r && r.entity === 'resource' && !r.dead && r.amount > 0) {
      this.orderHarvest(u, r, after.role || null);
      return true;
    }
  }
  const source = u.target && u.target.entity === 'resource' ? u.target : this.entityById(u.returnSourceId);
  if (source && !source.dead && source.amount > 0 && this.workerRoleMatchesResource((u.workerRole || 'auto'), source)) {
    u.order = 'harvest';
    u.target = source;
    u.goal = null;
    return true;
  }
  return this.resumeWorkerRole(u);
};

Game.prototype.updateWorkerDelivery = function(u, dt) {
  const drop = this.nearestDropoff(u.faction, u.x, u.y);
  if (!drop) { u.order = 'idle'; u.goal = null; return; }
  const p = this.dropoffPoint(drop, u);
  const rect = getBuildingFootprintRect(drop, undefined, undefined, 2);
  const closeEnough = this.rectDistanceToPoint(rect, u.x, u.y) <= Math.max(26, (u.r || 10) + 14) || dist2(u.x, u.y, p.x, p.y) <= 24 * 24;
  if (closeEnough) {
    if (this.depositWorkerCargo(u, drop)) this.applyAfterDepositOrder(u);
    return;
  }
  const old = u.interactionBuilding;
  u.interactionBuilding = drop;
  u.order = 'harvest';
  const arrived = this.moveToward(u, p.x, p.y, dt, 13);
  u.interactionBuilding = old;
  const nowClose = this.rectDistanceToPoint(rect, u.x, u.y) <= Math.max(30, (u.r || 10) + 18);
  if (arrived || nowClose || ((u.stuck || 0) > .42 && this.rectDistanceToPoint(rect, u.x, u.y) < 58)) {
    if (this.depositWorkerCargo(u, drop)) this.applyAfterDepositOrder(u);
  }
};

Game.prototype.orderHarvest = function(u, res, preferredRole = null) {
  if (!u || u.type !== 'worker' || !res || res.dead || res.amount <= 0) return;
  if (preferredRole) u.workerRole = preferredRole;
  else if (res.type === 'tree') u.workerRole = 'wood';
  else if (res.type === 'gold') u.workerRole = 'gold';
  else if (res.type === 'food') u.workerRole = 'food';
  if (u.carry) {
    u.afterDeposit = { kind: 'harvest', targetId: res.id, role: u.workerRole || preferredRole || 'auto' };
    u.order = 'harvest';
    u.goal = null;
    u.gather = 0;
    u.hold = false;
    return;
  }
  this.clearUnitPath && this.clearUnitPath(u);
  u.order = 'harvest';
  u.target = res;
  u.returnSourceId = res.id;
  u.goal = null;
  u.gather = 0;
  u.hold = false;
  u.returning = false;
};

Game.prototype.assignBuildersTo = function(b, fid, maxBuilders = 2, preferSelected = false) {
  if (!b || b.dead) return 0;
  const selected = preferSelected ? this.selected.filter(u => u.entity === 'unit' && u.faction === fid && u.type === 'worker' && !u.dead) : [];
  const existing = this.units.filter(u => u.faction === fid && u.type === 'worker' && !u.dead && u.order === 'repair' && u.target === b);
  const chosen = [...existing];
  const addCandidate = (u) => {
    if (!u || chosen.includes(u) || u.dead || u.faction !== fid || u.type !== 'worker') return;
    chosen.push(u);
  };
  selected.forEach(addCandidate);
  const pool = this.units
    .filter(u => u.faction === fid && u.type === 'worker' && !u.dead && !chosen.includes(u))
    .sort((a, c) => {
      const idleA = a.order === 'idle' ? -800000 : 0;
      const idleC = c.order === 'idle' ? -800000 : 0;
      return dist2(a.x, a.y, b.x, b.y) + idleA - (dist2(c.x, c.y, b.x, b.y) + idleC);
    });
  for (const u of pool) {
    if (chosen.length >= maxBuilders) break;
    addCandidate(u);
  }
  let count = 0;
  for (const u of chosen.slice(0, maxBuilders)) {
    this.clearUnitPath && this.clearUnitPath(u);
    u.afterDeposit = null;
    if (u.carry) {
      u.afterDeposit = { kind: 'repair', targetId: b.id };
      u.order = 'harvest';
      u.goal = null;
      u.gather = 0;
    } else {
      u.order = 'repair';
      u.target = b;
      u.goal = null;
      u.gather = 0;
    }
    u.hold = false;
    u.workerRole = u.workerRole === 'idle' ? 'build' : (u.workerRole || 'build');
    count++;
  }
  return count;
};

Game.prototype.resumeWorkerRole = function(u) {
  if (!u || u.dead || u.type !== 'worker') return false;
  if (u.carry) { this.updateWorkerDelivery(u, 0); return true; }
  const role = u.workerRole;
  if (!role || role === 'auto') { this.autoGather(u); return true; }
  if (role === 'idle') { u.order = 'idle'; u.target = null; u.goal = null; return true; }
  if (role === 'build') {
    const target = this.bestBuildTargetForWorker(u);
    if (target) {
      this.clearUnitPath && this.clearUnitPath(u);
      u.order = 'repair'; u.target = target; u.goal = null; u.gather = 0; u.hold = false;
      return true;
    }
    u.order = 'idle'; u.target = null; return false;
  }
  const res = this.findResourceForWorkerRole(u, role);
  if (res) { this.orderHarvest(u, res, role); return true; }
  u.order = 'idle'; u.target = null; return false;
};

Game.prototype.updateWorker = function(u, dt) {
  if (u.carry) { this.updateWorkerDelivery(u, dt); return; }

  if (u.order === 'repair') {
    const b = u.target;
    if (!b || b.dead || (b.build >= 1 && b.hp >= b.maxHp)) { u.order = 'idle'; u.target = null; u.gather = 0; this.resumeWorkerRole(u); return; }
    const p = this.buildingApproachPoint(b, u);
    const rect = getBuildingFootprintRect(b, undefined, undefined, 0);
    const alreadyClose = this.rectDistanceToPoint(rect, u.x, u.y) <= Math.max(20, (u.r || 10) + 8);
    let working = alreadyClose;
    if (!working) {
      const old = u.interactionBuilding;
      u.interactionBuilding = b;
      working = this.moveToward(u, p.x, p.y, dt, 10);
      u.interactionBuilding = old;
      working = working || this.rectDistanceToPoint(rect, u.x, u.y) <= Math.max(22, (u.r || 10) + 10);
    }
    if (working) {
      this.clearUnitPath && this.clearUnitPath(u);
      u.face = b.x >= u.x ? 1 : -1;
      u.gather += dt;
      if (Math.random() < dt * 4.2) this.effects.push({ kind: 'dust', x: u.x + (Math.random() - .5) * 10, y: u.y - 3, time: .34, max: .34 });
      if (b.build < 1) {
        b.build = Math.min(1, b.build + dt / b.buildTime * 1.5);
        b.hp = Math.max(1, Math.min(b.maxHp, b.maxHp * (.18 + b.build * .82)));
        if (b.build >= 1) {
          b.hp = b.maxHp;
          b.completedAt = this.time;
          this.clearOverlapsAroundStructures && this.clearOverlapsAroundStructures();
          this.markNavDirty && this.markNavDirty();
          if (b.faction === 0) { this.toast(`${BUILDINGS[b.type].label} constructed.`, 1.4); this.sfx.build(this.audioGainAt(b.x, b.y)); }
          u.order = 'idle'; u.target = null; u.gather = 0; this.resumeWorkerRole(u);
        }
      } else if (b.hp < b.maxHp) {
        b.hp = Math.min(b.maxHp, b.hp + b.maxHp * dt * 0.065);
        if (b.hp >= b.maxHp) { u.order = 'idle'; u.target = null; u.gather = 0; if (b.faction === 0) this.toast(`${BUILDINGS[b.type].label} fully repaired.`, 1.2); this.resumeWorkerRole(u); }
      }
    }
    return;
  }

  if (u.order === 'harvest') {
    const res = u.target;
    if (!res || res.dead || res.amount <= 0) { u.order = 'idle'; u.target = null; u.gather = 0; this.resumeWorkerRole(u); return; }

    if (res.type === 'food' && res.animal) {
      const strikeRange = res.r + 6;
      this.moveToward(u, res.x, res.y, dt, strikeRange);
      if (dist2(u.x, u.y, res.x, res.y) <= (strikeRange + 22) * (strikeRange + 22)) {
        u.face = res.x >= u.x ? 1 : -1;
        u.gather += dt;
        if (u.gather >= .55) { u.gather = 0; u.huntSwing = .42; this.strikeAnimal(u, res); if (u.carry) u.returnSourceId = res.id; }
      } else u.gather = 0;
      return;
    }

    const interact = getResourceInteractionPoint(res);
    this.moveToward(u, interact.x, interact.y, dt, res.r + 6);
    if (dist2(u.x, u.y, interact.x, interact.y) <= (res.r + 26) * (res.r + 26)) {
      u.gather += dt;
      u.face = interact.x >= u.x ? 1 : -1;
      const gatherTime = res.type === 'tree' ? 1.35 : res.type === 'gold' ? 1.6 : .82;
      if (u.gather >= gatherTime) {
        const amount = res.type === 'gold' ? 12 : res.type === 'food' ? 10 : 14;
        res.amount -= amount;
        u.carry = { type: res.type === 'tree' ? 'wood' : res.type, amount };
        u.returnSourceId = res.id;
        u.gather = 0;
        if (res.amount <= 0) {
          if (res.type === 'tree') { res.depleted = true; res.dead = false; res.amount = 0; res.sprite = choose(['stump1','stump2']); res.r = 12; this.markNavDirty && this.markNavDirty(); }
          else { res.dead = true; this.markNavDirty && this.markNavDirty(); }
          this.resourceBuckets = null;
          this.effects.push({ kind: 'dust', x: res.x, y: res.y, time: .65, max: .65 });
        }
      }
    } else {
      u.gather = 0;
    }
    return;
  }

  this.updateFighter(u, dt);
  if (u.order === 'idle') this.resumeWorkerRole(u);
};

Game.prototype.isBlocked = function(x, y, u) {
  if (this.isWater(x, y)) return true;
  const r = u ? u.r || 8 : 8;
  const rect = { x: x - r, y: y - r, w: r * 2, h: r * 2 };
  const ignoreBuilding = u && u.interactionBuilding;
  const buildingCandidates = this.nearbyBuildings ? this.nearbyBuildings(x, y, 260) : this.buildings;
  for (const b of buildingCandidates) {
    if (b.dead || b.build < 0.1 || b === ignoreBuilding) continue;
    const brect = getBuildingFootprintRect(b, undefined, undefined, 4);
    if (rectsOverlap(rect, brect)) return true;
  }
  const resourceCandidates = this.nearbyResources ? this.nearbyResources(x, y, 170) : this.resources;
  for (const res of resourceCandidates) {
    if (res.dead || res.amount <= 0 || res.animal) continue;
    const rr = Math.max(7, getResourceBlockingRadius(res) * .52);
    const resRect = { x: res.x - rr, y: res.y - rr, w: rr * 2, h: rr * 2 };
    if (rectsOverlap(rect, resRect) && (!u || u.target !== res)) return true;
  }
  return false;
};


// Pass 4: placement should honor only grass-contact footprints, not roof silhouettes.
Game.prototype.placementIssue = function(type, x, y, ignoreBuilding = null) {
  const def = BUILDINGS[type];
  if (!def) return 'Unknown building.';
  const footprint = getBuildingFootprintRect(type, x, y, 5);
  if (footprint.x < 28 || footprint.y < 28 || footprint.x + footprint.w > WORLD_W - 28 || footprint.y + footprint.h > WORLD_H - 28) return 'Too close to the world edge.';

  const probes = [
    [footprint.x + footprint.w * .18, footprint.y + footprint.h * .20],
    [footprint.x + footprint.w * .82, footprint.y + footprint.h * .20],
    [footprint.x + footprint.w * .18, footprint.y + footprint.h * .82],
    [footprint.x + footprint.w * .82, footprint.y + footprint.h * .82],
    [footprint.x + footprint.w * .50, footprint.y + footprint.h * .50]
  ];
  if (!probes.every(([px, py]) => this.isSafeLand(px, py, 8))) return 'Need a clear patch of land.';

  const nearbyB = this.nearbyBuildings ? this.nearbyBuildings(x, y, Math.max(def.w, def.h) + 130) : this.buildings;
  for (const b of nearbyB) {
    if (b.dead || b === ignoreBuilding) continue;
    const other = getBuildingFootprintRect(b, undefined, undefined, 6);
    if (rectsOverlap(footprint, other)) return `Blocked by ${BUILDINGS[b.type]?.label || 'building'}.`;
  }

  const nearbyR = this.nearbyResources ? this.nearbyResources(x, y, Math.max(def.w, def.h) + 120) : this.resources;
  for (const r of nearbyR) {
    if (r.dead || r.amount <= 0) continue;
    const fp = Math.max(8, getResourceFootprint(r) * (r.animal ? .72 : .62));
    const resourceRect = { x: r.x - fp - 4, y: r.y - fp - 4, w: fp * 2 + 8, h: fp * 2 + 8 };
    if (rectsOverlap(footprint, resourceRect)) return r.animal ? `Blocked by ${getAnimalLabel(r)}.` : `Blocked by ${r.type === 'tree' ? 'wood' : r.type}.`;
  }
  return null;
};

Game.prototype.canPlace = function(type, x, y, ignoreBuilding = null) {
  return !this.placementIssue(type, x, y, ignoreBuilding);
};
