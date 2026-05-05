// Production pathfinding: 32px A* grid with dynamic blockers and path smoothing.
'use strict';

const PATH_CELL = 32;

Game.prototype.markNavDirty = function() {
  this.navVersion = (this.navVersion || 0) + 1;
  this.pathGrid = null;
};

Game.prototype.pathCellKey = function(cx, cy) { return cy * this.pathCols + cx; };

Game.prototype.worldToPathCell = function(x, y) {
  return {
    x: clamp(Math.floor(x / PATH_CELL), 0, this.pathCols ? this.pathCols - 1 : Math.ceil(WORLD_W / PATH_CELL) - 1),
    y: clamp(Math.floor(y / PATH_CELL), 0, this.pathRows ? this.pathRows - 1 : Math.ceil(WORLD_H / PATH_CELL) - 1)
  };
};

Game.prototype.pathCellToWorld = function(cx, cy) {
  return { x: cx * PATH_CELL + PATH_CELL / 2, y: cy * PATH_CELL + PATH_CELL / 2 };
};

Game.prototype.buildPathGrid = function() {
  this.pathCols = Math.ceil(WORLD_W / PATH_CELL);
  this.pathRows = Math.ceil(WORLD_H / PATH_CELL);
  const total = this.pathCols * this.pathRows;
  const blocked = new Uint8Array(total);

  for (let cy = 0; cy < this.pathRows; cy++) {
    const row = cy * this.pathCols;
    for (let cx = 0; cx < this.pathCols; cx++) {
      const p = this.pathCellToWorld(cx, cy);
      blocked[row + cx] = this.isWater(p.x, p.y) ? 1 : 0;
    }
  }

  const blockRect = (x1, y1, x2, y2) => {
    const min = this.worldToPathCell(clamp(x1, 0, WORLD_W - 1), clamp(y1, 0, WORLD_H - 1));
    const max = this.worldToPathCell(clamp(x2, 0, WORLD_W - 1), clamp(y2, 0, WORLD_H - 1));
    for (let cy = min.y; cy <= max.y; cy++) {
      const row = cy * this.pathCols;
      for (let cx = min.x; cx <= max.x; cx++) blocked[row + cx] = 1;
    }
  };

  for (const b of this.buildings) {
    if (b.dead || b.build < .1) continue;
    const rect = getBuildingFootprintRect(b, undefined, undefined, 10);
    blockRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h);
  }

  for (const r of this.resources) {
    if (r.dead || r.amount <= 0 || r.animal) continue;
    const pad = Math.max(12, getResourceBlockingRadius(r) * .76);
    blockRect(r.x - pad, r.y - pad, r.x + pad, r.y + pad);
  }

  this.pathGrid = blocked;
  this.pathGridVersion = this.navVersion || 1;
};

Game.prototype.ensurePathGrid = function() {
  if (!this.pathGrid || this.pathGridVersion !== (this.navVersion || 1)) this.buildPathGrid();
};

Game.prototype.isPathCellWalkable = function(cx, cy) {
  this.ensurePathGrid();
  return cx >= 0 && cy >= 0 && cx < this.pathCols && cy < this.pathRows && this.pathGrid[cy * this.pathCols + cx] === 0;
};

Game.prototype.findNearestWalkableCell = function(cell, maxR = 18) {
  if (this.isPathCellWalkable(cell.x, cell.y)) return cell;
  for (let r = 1; r <= maxR; r++) {
    for (let y = -r; y <= r; y++) {
      for (let x = -r; x <= r; x++) {
        if (Math.abs(x) !== r && Math.abs(y) !== r) continue;
        const cx = cell.x + x, cy = cell.y + y;
        if (this.isPathCellWalkable(cx, cy)) return { x: cx, y: cy };
      }
    }
  }
  return null;
};

Game.prototype.findPath = function(startX, startY, goalX, goalY, maxNodes = 16000) {
  this.ensurePathGrid();
  const start = this.findNearestWalkableCell(this.worldToPathCell(startX, startY), 10);
  const goal = this.findNearestWalkableCell(this.worldToPathCell(goalX, goalY), 24);
  if (!start || !goal) return null;
  if (start.x === goal.x && start.y === goal.y) return [this.pathCellToWorld(goal.x, goal.y)];

  const cols = this.pathCols, rows = this.pathRows, total = cols * rows;
  const startKey = start.y * cols + start.x;
  const goalKey = goal.y * cols + goal.x;

  if (!this._pathCame || this._pathCame.length !== total) {
    this._pathCame = new Int32Array(total);
    this._pathGScore = new Float32Array(total);
    this._pathClosed = new Uint8Array(total);
  }
  const came = this._pathCame;
  const gScore = this._pathGScore;
  const closed = this._pathClosed;
  came.fill(-1);
  gScore.fill(Infinity);
  closed.fill(0);

  const heap = [];
  const heuristic = (key) => {
    const ax = key % cols, ay = Math.floor(key / cols);
    const dx = Math.abs(ax - goal.x), dy = Math.abs(ay - goal.y);
    return (Math.max(dx, dy) * 10 + Math.min(dx, dy) * 4);
  };
  const push = (key, f) => {
    heap.push([key, f]);
    let i = heap.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (heap[p][1] <= f) break;
      heap[i] = heap[p]; i = p;
    }
    heap[i] = [key, f];
  };
  const pop = () => {
    const top = heap[0];
    const last = heap.pop();
    if (heap.length && last) {
      let i = 0;
      while (true) {
        const l = i * 2 + 1, r = l + 1;
        if (l >= heap.length) break;
        const c = (r < heap.length && heap[r][1] < heap[l][1]) ? r : l;
        if (heap[c][1] >= last[1]) break;
        heap[i] = heap[c]; i = c;
      }
      heap[i] = last;
    }
    return top;
  };

  gScore[startKey] = 0;
  push(startKey, heuristic(startKey));
  const dirs = [[1,0,10],[-1,0,10],[0,1,10],[0,-1,10],[1,1,14],[1,-1,14],[-1,1,14],[-1,-1,14]];
  let visited = 0, found = false;

  while (heap.length && visited++ < maxNodes) {
    const [key] = pop();
    if (closed[key]) continue;
    if (key === goalKey) { found = true; break; }
    closed[key] = 1;
    const cx = key % cols, cy = Math.floor(key / cols);
    for (const [ox, oy, cost] of dirs) {
      const nx = cx + ox, ny = cy + oy;
      if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
      const nk = ny * cols + nx;
      if (closed[nk] || this.pathGrid[nk]) continue;
      if (ox && oy && (this.pathGrid[cy * cols + nx] || this.pathGrid[ny * cols + cx])) continue;
      const ng = gScore[key] + cost;
      if (ng < gScore[nk]) { came[nk] = key; gScore[nk] = ng; push(nk, ng + heuristic(nk)); }
    }
  }
  if (!found) return null;

  const cells = [];
  let k = goalKey;
  while (k !== -1 && k !== startKey) { cells.push(k); k = came[k]; }
  cells.reverse();

  const raw = cells.map(ck => this.pathCellToWorld(ck % cols, Math.floor(ck / cols)));
  if (!raw.length) return [this.pathCellToWorld(goal.x, goal.y)];

  const smooth = [];
  let anchor = { x: startX, y: startY };
  for (let i = 0; i < raw.length; i++) {
    const last = i === raw.length - 1;
    if (!last && this.isSegmentWalkable(null, anchor.x, anchor.y, raw[i + 1].x, raw[i + 1].y, Math.ceil(Math.hypot(raw[i + 1].x - anchor.x, raw[i + 1].y - anchor.y) / 28))) continue;
    smooth.push(raw[i]);
    anchor = raw[i];
  }
  const finalPoint = this.nearestLandPoint(goalX, goalY, 180) || this.pathCellToWorld(goal.x, goal.y);
  if (!smooth.length || dist2(smooth[smooth.length - 1].x, smooth[smooth.length - 1].y, finalPoint.x, finalPoint.y) > 30 * 30) smooth.push(finalPoint);
  return smooth.slice(0, 128);
};

Game.prototype.clearUnitPath = function(u) {
  if (!u) return;
  u.path = null; u.pathGoal = null; u.pathIndex = 0; u.pathRetry = 0; u.directPathUntil = 0;
};

Game.prototype.isSegmentWalkable = function(u, ax, ay, bx, by, samples = 9) {
  if (!this.pathGrid || this.pathGridVersion !== (this.navVersion || 1)) this.buildPathGrid();
  const steps = Math.max(2, samples);
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const px = ax + (bx - ax) * t;
    const py = ay + (by - ay) * t;
    if (this.isWater(px, py)) return false;
    const cell = this.worldToPathCell(px, py);
    if (cell.x >= 0 && cell.x < this.pathCols && cell.y >= 0 && cell.y < this.pathRows) {
      if (this.pathGrid[cell.y * this.pathCols + cell.x] === 1) return false;
    } else {
      return false;
    }
  }
  return true;
};

Game.prototype.prepareUnitPath = function(u, x, y, d) {
  const nav = this.navVersion || 1;
  const goalChanged = !u.pathGoal || dist2(u.pathGoal.x, u.pathGoal.y, x, y) > 42 * 42;
  const stale = u.pathVersion !== nav;
  u.pathRetry = Math.max(0, (u.pathRetry || 0) - .016);

  if (!u.path || goalChanged || stale) {
    if (!goalChanged && !stale && u.directPathUntil && u.directPathUntil > (this.time || 0)) return null;
    const samples = d < 180 ? 6 : Math.ceil(Math.min(36, Math.max(9, d / 96)));
    if (this.isSegmentWalkable(u, u.x, u.y, x, y, samples) && !this.isBlocked(x, y, u)) {
      this.clearUnitPath(u);
      u.pathGoal = { x, y };
      u.pathVersion = nav;
      u.directPathUntil = (this.time || 0) + .20;
      return null;
    }
  }

  if (!u.path || goalChanged || stale) {
    if (u.pathRetry > 0 && !goalChanged && !stale) return u.path;
    u.path = this.findPath(u.x, u.y, x, y);
    u.pathGoal = { x, y };
    u.pathIndex = 0;
    u.pathVersion = nav;
    u.directPathUntil = 0;
    u.pathRetry = u.path ? .18 : .72;
  }
  return u.path;
};

Game.prototype.nextPathWaypoint = function(u, x, y) {
  if (!u.path || !u.path.length) return { x, y };
  while (u.pathIndex < u.path.length - 1 && dist2(u.x, u.y, u.path[u.pathIndex].x, u.path[u.pathIndex].y) < 24 * 24) u.pathIndex++;
  while (u.pathIndex < u.path.length - 1 && this.isSegmentWalkable(u, u.x, u.y, u.path[u.pathIndex + 1].x, u.path[u.pathIndex + 1].y, 6)) u.pathIndex++;
  return u.path[u.pathIndex] || { x, y };
};
