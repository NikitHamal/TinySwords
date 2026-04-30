// Canvas renderer: terrain, animated water, entities, FX, minimap.
Game.prototype.draw = function() {
  ctx.clearRect(0, 0, VIEW_W, VIEW_H);
  ctx.fillStyle = '#143340';
  ctx.fillRect(0, 0, VIEW_W, VIEW_H);
  ctx.save();
  ctx.scale(this.camera.zoom, this.camera.zoom);
  ctx.translate(-this.camera.x, -this.camera.y);
  this.drawTerrain();
  this.drawWorldEntities();
  this.drawPlacementGhost();
  ctx.restore();
  this.drawScreenOverlays();
  this.drawMinimap();
};

Game.prototype.drawTerrain = function() {
  const sx = Math.floor(this.camera.x / TILE) - 2;
  const sy = Math.floor(this.camera.y / TILE) - 2;
  const ex = Math.ceil((this.camera.x + VIEW_W / this.camera.zoom) / TILE) + 2;
  const ey = Math.ceil((this.camera.y + VIEW_H / this.camera.zoom) / TILE) + 2;
  const cols = this.landCols || Math.ceil(WORLD_W / TILE);
  const rows = this.landRows || Math.ceil(WORLD_H / TILE);

  ctx.fillStyle = '#48aaa8';
  ctx.fillRect(this.camera.x - 180, this.camera.y - 180, VIEW_W / this.camera.zoom + 360, VIEW_H / this.camera.zoom + 360);

  for (let ty = sy; ty <= ey; ty++) for (let tx = sx; tx <= ex; tx++) {
    const x = tx * TILE, y = ty * TILE;
    if (tx < 0 || ty < 0 || tx >= cols || ty >= rows || !this.landMap || this.landMap[ty * cols + tx] !== 1) this.drawWaterTile(tx, ty, x, y);
  }
  for (let ty = sy; ty <= ey; ty++) for (let tx = sx; tx <= ex; tx++) {
    if (tx < 0 || ty < 0 || tx >= cols || ty >= rows || !this.landMap || this.landMap[ty * cols + tx] !== 1) continue;
    this.drawGrassGround(tx, ty, tx * TILE, ty * TILE);
  }
  this.drawShoreLines(sx, sy, ex, ey);
};

Game.prototype.landAtTile = function(tx, ty) {
  return this.landMap && tx >= 0 && ty >= 0 && tx < this.landCols && ty < this.landRows && this.landMap[ty * this.landCols + tx] === 1;
};

Game.prototype.edgeSource = function(tx, ty) {
  const n = !this.landAtTile(tx, ty - 1), s = !this.landAtTile(tx, ty + 1), w = !this.landAtTile(tx - 1, ty), e = !this.landAtTile(tx + 1, ty);
  if (n && w) return { sx: 0, sy: 0, edge: true, n, s, w, e };
  if (n && e) return { sx: 128, sy: 0, edge: true, n, s, w, e };
  if (s && w) return { sx: 0, sy: 128, edge: true, n, s, w, e };
  if (s && e) return { sx: 128, sy: 128, edge: true, n, s, w, e };
  if (n) return { sx: 64, sy: 0, edge: true, n, s, w, e };
  if (s) return { sx: 64, sy: 128, edge: true, n, s, w, e };
  if (w) return { sx: 0, sy: 64, edge: true, n, s, w, e };
  if (e) return { sx: 128, sy: 64, edge: true, n, s, w, e };
  return { sx: 64, sy: 64, edge: false, n, s, w, e };
};

Game.prototype.drawGrassGround = function(tx, ty, x, y) {
  const variant = this.groundVariant ? this.groundVariant[ty * this.landCols + tx] : 0;
  const palette = [assets.tileGrass, assets.tileAlt, assets.tileMoss, assets.tileDeep, assets.tileWarm].filter(Boolean);
  const img = palette.length ? palette[variant % palette.length] : null;
  const edge = this.edgeSource(tx, ty);

  if (edge.edge && assets.waterFoam) {
    const foamFrame = Math.floor(this.time * 5.4 + rngHash(tx, ty, 619) * 16) % 16;
    ctx.globalAlpha = .68;
    ctx.drawImage(assets.waterFoam, foamFrame * 192 + edge.sx, edge.sy, 64, 64, x, y, TILE + 1, TILE + 1);
    ctx.globalAlpha = 1;
  }

  if (img) {
    ctx.drawImage(img, edge.sx, edge.sy, 64, 64, x, y, TILE + 1, TILE + 1);
  } else {
    ctx.fillStyle = '#87bd62';
    ctx.fillRect(x, y, TILE + 1, TILE + 1);
  }

  if (!edge.edge) {
    const n = rngHash(tx, ty, 1200);
    if (n < .10) { ctx.fillStyle = 'rgba(244, 239, 141, .10)'; ctx.fillRect(x + 8 + (variant % 11), y + 14, 30, 3); }
    if (n > .91) { ctx.fillStyle = 'rgba(41, 104, 68, .13)'; ctx.fillRect(x + 18, y + 47 - (variant % 9), 34, 4); }
  }
};

Game.prototype.drawWaterTile = function(tx, ty, x, y) {
  const img = assets.water;
  if (img) ctx.drawImage(img, 0, 0, 64, 64, x, y, TILE + 1, TILE + 1);
  else { ctx.fillStyle = '#47aaa6'; ctx.fillRect(x, y, TILE + 1, TILE + 1); }

  const phase = this.time * 1.35 + tx * .73 + ty * .41;
  const shimmer = (Math.sin(phase) + 1) * .5;
  const n = rngHash(tx, ty, 731);
  if (n < .38) {
    ctx.globalAlpha = .055 + shimmer * .065;
    ctx.fillStyle = '#d8fff6';
    ctx.fillRect(x + 8 + Math.floor((n * 31 + this.time * 7) % 28), y + 12 + Math.floor(n * 37) % 34, 18 + (tx + ty) % 18, 2);
    ctx.globalAlpha = 1;
  }
  if (n > .90) {
    ctx.globalAlpha = .08;
    ctx.fillStyle = '#286f82';
    ctx.fillRect(x + 4, y + 47, 42, 3);
    ctx.globalAlpha = 1;
  }
};

Game.prototype.drawShoreLines = function(sx, sy, ex, ey) {
  if (!this.landMap) return;
  for (let ty = sy; ty <= ey; ty++) for (let tx = sx; tx <= ex; tx++) {
    if (this.landAtTile(tx, ty)) continue;
    const north = this.landAtTile(tx, ty - 1), south = this.landAtTile(tx, ty + 1), west = this.landAtTile(tx - 1, ty), east = this.landAtTile(tx + 1, ty);
    if (!(north || south || west || east)) continue;
    const x = tx * TILE, y = ty * TILE;
    const a = .18 + Math.sin(this.time * 3 + tx * .4 + ty * .7) * .05;
    ctx.globalAlpha = a;
    ctx.fillStyle = '#e7fff1';
    if (north) ctx.fillRect(x + 9, y + 2, 46, 2);
    if (south) ctx.fillRect(x + 9, y + TILE - 4, 46, 2);
    if (west) ctx.fillRect(x + 2, y + 9, 2, 46);
    if (east) ctx.fillRect(x + TILE - 4, y + 9, 2, 46);
    ctx.globalAlpha = 1;
  }
};

Game.prototype.drawWorldEntities = function() {
  const drawables = [];
  const inView = (x, y, pad = 180) => x > this.camera.x - pad && y > this.camera.y - pad && x < this.camera.x + VIEW_W / this.camera.zoom + pad && y < this.camera.y + VIEW_H / this.camera.zoom + pad;
  for (const d of this.decor) if (inView(d.x, d.y, d.sky ? 360 : 100)) drawables.push({ y: d.sky ? d.y - 900 : d.y + (d.front ? 6 : -18), kind: 'decor', item: d });
  for (const r of this.resources) if (!r.dead && inView(r.x, r.y, 130)) drawables.push({ y: r.y + (r.type === 'tree' ? -10 : 0), kind: 'resource', item: r });
  for (const b of this.buildings) if (!b.dead && inView(b.x, b.y, 280)) drawables.push({ y: b.y + b.h * .34, kind: 'building', item: b });
  for (const u of this.units) if (!u.dead && !u.garrisoned && inView(u.x, u.y, 140)) drawables.push({ y: u.y, kind: 'unit', item: u });
  drawables.sort((a, b) => a.y - b.y);
  for (const d of drawables) {
    if (d.kind === 'decor') this.drawDecor(d.item);
    else if (d.kind === 'resource') this.drawResource(d.item);
    else if (d.kind === 'building') this.drawBuilding(d.item);
    else this.drawUnit(d.item);
  }
  for (const p of this.projectiles) this.drawProjectile(p);
  for (const e of this.effects) this.drawEffect(e);
};

Game.prototype.drawShadow = function(x, y, w, h) {
  if (assets.shadow) {
    ctx.globalAlpha = 0.45;
    ctx.drawImage(assets.shadow, x - w * 1.5, y - h * 1.5, w * 3, h * 3);
    ctx.globalAlpha = 1;
  } else {
    ctx.fillStyle = 'rgba(0,0,0,.22)';
    ctx.beginPath(); ctx.ellipse(x, y, w, h, 0, 0, Math.PI * 2); ctx.fill();
  }
};

Game.prototype.drawDecor = function(d) {
  const img = assets[d.kind];
  if (!img) return;
  let fw = img.width, fh = img.height, fps = 0, frame = 0, sx = 0;
  if (d.kind.startsWith('bush')) { fw = 128; fh = 128; fps = 1.05; }
  else if (d.kind.startsWith('waterRock')) { fw = 64; fh = 64; fps = 3.5; }
  else if (d.kind === 'rubberDuck') { fw = 32; fh = 32; fps = 2.2; }
  else if (d.kind.startsWith('cloud')) { fw = img.width; fh = img.height; fps = 0; }
  const frames = Math.max(1, Math.floor(img.width / fw));
  if (fps) frame = Math.floor(this.time * fps + (d.id % frames)) % frames;
  sx = frame * fw;
  const bob = d.water ? Math.sin(this.time * 1.35 + (d.drift || 0)) * 2.2 : 0;
  const drift = d.sky ? Math.sin(this.time * (d.speed || 1) * .22 + d.drift) * 18 : 0;
  const cloudScale = d.sky ? CLOUD_BOOST : 1;
  const w = fw * d.scale * cloudScale, h = fh * d.scale * cloudScale;
  if (!d.water && !d.sky) this.drawShadow(d.x, d.y + 4, Math.min(22, w * .15), 5);
  ctx.globalAlpha = d.sky ? .82 : 1;
  ctx.drawImage(img, sx, 0, fw, fh, d.x - w / 2 + drift, d.y - h + 8 + bob, w, h);
  ctx.globalAlpha = 1;
};

Game.prototype.drawResource = function(r) {
  const moving = r.type === 'food' && r.animal && Math.hypot(r.vx || 0, r.vy || 0) > 7;
  let sprite = assets[r.sprite];
  if (r.type === 'tree' && r.depleted) {
    sprite = assets.stump1 || sprite;
  }
  if (r.type === 'food') {
    if (!r.animal) sprite = assets.meat || sprite;
    else if (moving) sprite = assets.sheepMove || sprite;
    else if (rngHash(r.id, 3, 66) > .82) sprite = assets.sheepGrass || sprite;
  }
  const bob = r.type === 'food' && r.animal ? Math.sin(this.time * 2 + r.bob) * 1.8 : 0;
  this.drawShadow(r.x, r.y + 4, r.type === 'tree' ? 19 : r.r * .8, 7);
  if (sprite) {
    let frameW = sprite.width, frameH = sprite.height, fps = 0, scale = .5;
    if (r.type === 'tree') { frameW = 192; frameH = 256; fps = r.depleted ? 0 : 4.0; scale = 0.65 * SPRITE_BOOST; }
    else if (r.type === 'food' && r.animal) { frameW = 128; frameH = 128; fps = moving ? 6 : 2.5; scale = .50 * SPRITE_BOOST; }
    else if (r.type === 'food') { frameW = 64; frameH = 64; fps = 0; scale = .78 * SPRITE_BOOST; }
    else if (r.type === 'gold') { frameW = 128; frameH = 128; scale = .56 * SPRITE_BOOST; }
    const frames = Math.max(1, Math.floor(sprite.width / frameW));
    const fr = fps ? Math.floor(this.time * fps + r.bob) % frames : 0;
    const w = frameW * scale, h = frameH * scale;
    ctx.drawImage(sprite, fr * frameW, 0, frameW, frameH, r.x - w / 2, r.y - h + 14 + bob, w, h);
  } else { ctx.fillStyle = r.type === 'gold' ? '#e6ca59' : '#6fa75a'; ctx.fillRect(r.x - r.r, r.y - r.r, r.r * 2, r.r * 2); }
  if (this.selected.includes(r)) this.drawSelectionCircle(r.x, r.y, r.r + 8, '#f5d37d');
};

Game.prototype.drawBuilding = function(b) {
  const def = BUILDINGS[b.type];
  const img = assets[`b_${faction(b.faction).key}_${b.sprite || b.type}`] || assets[`b_${faction(b.faction).key}_${b.type}`];
  this.drawShadow(b.x, b.y + b.h * .30, b.w * .52, b.h * .20);
  if (img) {
    const w = img.width * def.scale * SPRITE_BOOST, h = img.height * def.scale * SPRITE_BOOST;
    ctx.globalAlpha = b.build < 1 ? .58 + .36 * b.build : 1;
    ctx.drawImage(img, b.x - w / 2, b.y - h + b.h * .46, w, h);
    ctx.globalAlpha = 1;
  } else { ctx.fillStyle = faction(b.faction).color; ctx.fillRect(b.x - b.w / 2, b.y - b.h / 2, b.w, b.h); }
  if (b.flash > 0) { ctx.fillStyle = `rgba(255,255,255,${b.flash * .25})`; ctx.fillRect(b.x - b.w / 2, b.y - b.h, b.w, b.h); }
  if (b.build < 1) this.drawProgress(b.x, b.y - b.h * .65, b.build, '#e8c965');
  this.drawHpBar(b.x, b.y - b.h * .88, b.hp / b.maxHp, b.faction);
  if (b.selected || this.selected.includes(b)) this.drawSelectionRect(b.x, b.y, b.w, b.h, faction(b.faction).color);
  if (b.rally && b.faction === 0 && this.selected.includes(b)) this.drawRallyFlag(b.rally.x, b.rally.y, faction(b.faction).color);
  if (b.type === 'tower' && b.garrison.length) {
    ctx.fillStyle = '#fff4b8'; ctx.font = '14px monospace'; ctx.fillText(`x${b.garrison.length}`, b.x + 20, b.y - b.h * .75);
  }
};

Game.prototype.drawUnit = function(u) {
  const f = faction(u.faction);
  const def = UNITS[u.type];
  let anim = 'idle';
  if (u.order === 'move' || u.order === 'attackMove' || u.order === 'garrison' || u.carry) anim = 'run';
  if (u.order === 'attack' && u.target && dist(u, u.target) <= def.range + 8) anim = 'attack';
  if (u.type === 'worker' && ((u.order === 'harvest' && !u.carry && u.gather > 0) || u.huntSwing > 0)) anim = u.target && u.target.type === 'gold' ? 'mine' : 'chop';
  let key = `u_${f.key}_${u.type}_${anim}`;
  if (u.type === 'worker') {
    if (u.carry) key = `u_${f.key}_worker_carry${u.carry.type[0].toUpperCase()}${u.carry.type.slice(1)}`;
    else if (anim === 'mine') key = `u_${f.key}_worker_mine`;
    else if (anim === 'chop') key = `u_${f.key}_worker_chop`;
    else key = `u_${f.key}_worker_${anim === 'run' ? 'run' : 'idle'}`;
  }
  const img = assets[key] || assets[`u_${f.key}_${u.type}_idle`];
  this.drawShadow(u.x, u.y + 6, u.r * 1.15, 8);
  if (u.selected) this.drawSelectionCircle(u.x, u.y, u.r + 8, '#f5d37d');
  if (img) {
    const fw = def.fw, fh = def.fh;
    const frames = Math.max(1, Math.floor(img.width / fw));
    const frame = Math.floor(u.anim) % frames;
    const w = fw * def.scale * SPRITE_BOOST, h = fh * def.scale * SPRITE_BOOST;
    ctx.save();
    ctx.translate(u.x, u.y + 10);
    ctx.scale(u.face, 1);
    ctx.globalAlpha = u.flash > 0 ? .75 : 1;
    ctx.drawImage(img, frame * fw, 0, fw, fh, -w / 2, -h + 16, w, h);
    ctx.globalAlpha = 1;
    ctx.restore();
  } else { ctx.fillStyle = f.color; ctx.beginPath(); ctx.arc(u.x, u.y, u.r, 0, Math.PI * 2); ctx.fill(); }
  if (u.hp < u.maxHp || u.faction !== 0) this.drawHpBar(u.x, u.y - 58, u.hp / u.maxHp, u.faction, 34);
};

Game.prototype.drawProjectile = function(p) {
  const f = faction(p.faction);
  const img = assets[`${f.key}Arrow`] || assets.blueArrow;
  const target = p.target || p;
  const a = Math.atan2((target.y - 20) - p.y, target.x - p.x);
  ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(a);
  if (img) ctx.drawImage(img, -10, -5, 28, 10);
  else { ctx.strokeStyle = '#f4e7a8'; ctx.beginPath(); ctx.moveTo(-8,0); ctx.lineTo(10,0); ctx.stroke(); }
  ctx.restore();
};

Game.prototype.drawEffect = function(e) {
  const t = clamp(e.time / e.max, 0, 1);
  if (e.kind === 'move' || e.kind === 'attack' || e.kind === 'flag') {
    ctx.strokeStyle = e.kind === 'attack' ? `rgba(255,95,80,${t})` : `rgba(246,218,116,${t})`;
    ctx.lineWidth = 3;
    ctx.beginPath(); ctx.arc(e.x, e.y, 12 + (1 - t) * 24, 0, Math.PI * 2); ctx.stroke();
    if (e.kind === 'flag') this.drawRallyFlag(e.x, e.y, '#f5d37d');
    return;
  }
  if (e.kind === 'hit') {
    ctx.globalAlpha = t;
    ctx.fillStyle = '#fff3bd';
    ctx.beginPath(); ctx.arc(e.x, e.y, 4 + (1 - t) * 10, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = 1;
    return;
  }
  const img = e.kind === 'boom' ? assets.explosion : e.kind === 'heal' ? assets.healFx : e.kind === 'splash' ? assets.waterSplash : assets.dust;
  if (img) {
    const fw = 192, fh = 192;
    const frames = Math.max(1, Math.floor(img.width / fw));
    const frame = Math.min(frames - 1, Math.floor((1 - t) * frames));
    const s = e.kind === 'boom' ? .8 : e.kind === 'splash' ? .38 : .42;
    ctx.globalAlpha = t;
    ctx.drawImage(img, frame * fw, 0, fw, fh, e.x - fw * s / 2, e.y - fh * s / 2, fw * s, fh * s);
    ctx.globalAlpha = 1;
  }
};

Game.prototype.drawSelectionCircle = function(x, y, r, color) {
  ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.setLineDash([8, 5]); ctx.beginPath(); ctx.ellipse(x, y + 4, r, r * .48, 0, 0, Math.PI * 2); ctx.stroke(); ctx.setLineDash([]);
};

Game.prototype.drawSelectionRect = function(x, y, w, h, color) {
  ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.setLineDash([7, 5]); ctx.strokeRect(x - w / 2 - 6, y - h / 2 - 10, w + 12, h + 16); ctx.setLineDash([]);
};

Game.prototype.drawHpBar = function(x, y, pct, fid, width = 58) {
  pct = clamp(pct, 0, 1);
  ctx.fillStyle = 'rgba(31,15,20,.76)'; ctx.fillRect(x - width / 2, y, width, 6);
  ctx.fillStyle = faction(fid).color; ctx.fillRect(x - width / 2 + 1, y + 1, (width - 2) * pct, 4);
  ctx.strokeStyle = 'rgba(0,0,0,.55)'; ctx.strokeRect(x - width / 2, y, width, 6);
};

Game.prototype.drawProgress = function(x, y, pct, color) {
  ctx.fillStyle = 'rgba(0,0,0,.65)'; ctx.fillRect(x - 32, y, 64, 6);
  ctx.fillStyle = color; ctx.fillRect(x - 31, y + 1, 62 * clamp(pct, 0, 1), 4);
};

Game.prototype.drawRallyFlag = function(x, y, color) {
  ctx.strokeStyle = '#0b111c'; ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - 38); ctx.stroke();
  ctx.strokeStyle = color; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - 38); ctx.stroke();
  ctx.fillStyle = color; ctx.beginPath(); ctx.moveTo(x + 2, y - 38); ctx.lineTo(x + 34, y - 30); ctx.lineTo(x + 2, y - 22); ctx.closePath(); ctx.fill();
};

Game.prototype.drawPlacementGhost = function() {
  if (!this.placing) return;
  const type = this.placing, def = BUILDINGS[type], ok = this.canPlace(type, this.pointer.wx, this.pointer.wy) && canAfford(this.factions[0], def.cost);
  ctx.globalAlpha = .62;
  ctx.fillStyle = ok ? 'rgba(94,211,105,.28)' : 'rgba(225,60,60,.32)';
  ctx.fillRect(this.pointer.wx - def.w / 2, this.pointer.wy - def.h / 2, def.w, def.h);
  const img = assets[`b_blue_${type}`];
  if (img) {
    const w = img.width * def.scale * SPRITE_BOOST, h = img.height * def.scale * SPRITE_BOOST;
    ctx.drawImage(img, this.pointer.wx - w / 2, this.pointer.wy - h + def.h * .46, w, h);
  }
  ctx.globalAlpha = 1;
};

Game.prototype.drawScreenOverlays = function() {
  if (this.pointer.down && this.pointer.dragging) {
    const x = Math.min(this.pointer.startX, this.pointer.x), y = Math.min(this.pointer.startY, this.pointer.y);
    const w = Math.abs(this.pointer.x - this.pointer.startX), h = Math.abs(this.pointer.y - this.pointer.startY);
    ctx.fillStyle = 'rgba(104, 183, 217, .14)'; ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = 'rgba(244, 218, 128, .85)'; ctx.lineWidth = 2; ctx.strokeRect(x, y, w, h);
  }
  if (this.paused) {
    ctx.fillStyle = 'rgba(0,0,0,.28)'; ctx.fillRect(0, 0, VIEW_W, VIEW_H);
    ctx.fillStyle = '#fff2b8'; ctx.font = 'bold 42px monospace'; ctx.textAlign = 'center'; ctx.fillText('PAUSED', VIEW_W / 2, VIEW_H / 2); ctx.textAlign = 'left';
  }
};

Game.prototype.drawMinimap = function() {
  this.resizeMini();
  const w = mini.width, h = mini.height;
  if (!w || !h) return;
  mctx.fillStyle = '#1f6773'; mctx.fillRect(0, 0, w, h);
  if (this.landMap) {
    const cols = this.landCols, rows = this.landRows;
    const cw = Math.ceil(w / cols), ch = Math.ceil(h / rows);
    for (let ty = 0; ty < rows; ty++) for (let tx = 0; tx < cols; tx++) {
      if (this.landMap[ty * cols + tx] !== 1) continue;
      mctx.fillStyle = '#6fa75a';
      mctx.fillRect(Math.floor(tx / cols * w), Math.floor(ty / rows * h), cw, ch);
    }
  }
  for (const r of this.resources) if (!r.dead) { mctx.fillStyle = r.type === 'gold' ? '#e8ca4d' : r.type === 'tree' ? '#366f3f' : '#e8a765'; mctx.fillRect(r.x / WORLD_W * w, r.y / WORLD_H * h, 1.8, 1.8); }
  for (const b of this.buildings) if (!b.dead) { mctx.fillStyle = faction(b.faction).color; mctx.fillRect(b.x / WORLD_W * w - 2, b.y / WORLD_H * h - 2, b.type === 'castle' ? 6 : 4, b.type === 'castle' ? 6 : 4); }
  for (const u of this.units) if (!u.dead && !u.garrisoned) { mctx.fillStyle = faction(u.faction).color; mctx.fillRect(u.x / WORLD_W * w, u.y / WORLD_H * h, 2, 2); }
  mctx.strokeStyle = '#fff3bd'; mctx.lineWidth = 1.5;
  mctx.strokeRect(this.camera.x / WORLD_W * w, this.camera.y / WORLD_H * h, (VIEW_W / this.camera.zoom) / WORLD_W * w, (VIEW_H / this.camera.zoom) / WORLD_H * h);
};
