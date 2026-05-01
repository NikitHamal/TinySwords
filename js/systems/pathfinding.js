// Production pathfinding: coarse A* over the world tile graph with dynamic blocker versions.
'use strict';

Game.prototype.markNavDirty = function() {
  this.navVersion = (this.navVersion || 0) + 1;
  this.pathGrid = null;
};

Game.prototype.pathCellKey = function(cx, cy) { return cy * this.pathCols + cx; };

Game.prototype.worldToPathCell = function(x, y) {
  return {
    x: clamp(Math.floor(x / TILE), 0, this.landCols - 1),
    y: clamp(Math.floor(y / TILE), 0, this.landRows - 1)
  };
};

Game.prototype.pathCellToWorld = function(cx, cy) {
  return { x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 };
};

Game.prototype.buildPathGrid = function() {
  this.pathCols = this.landCols;
  this.pathRows = this.landRows;
  const total = this.pathCols * this.pathRows;
  const blocked = new Uint8Array(total);
  if (this.landMap) {
    for (let i = 0; i < total; i++) blocked[i] = this.landMap[i] === 1 ? 0 : 1;
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
    blockRect(b.x - b.w / 2 - 20, b.y - b.h / 2 - 20, b.x + b.w / 2 + 20, b.y + b.h / 2 + 20);
  }

  for (const r of this.resources) {
    if (r.dead || r.amount <= 0 || r.animal) continue;
    const pad = getResourceBlockingRadius(r);
    blockRect(r.x - pad, r.y - pad, r.x + pad, r.y + pad);
  }

  for (const d of this.decor) {
    if (d.sky || d.water || PASSABLE_DECOR.has(d.kind)) continue;
    const spec = DECOR_SPECS[d.kind] || {};
    const radius = Math.max(8, Math.min(18, ((spec.shadow && spec.shadow[0]) || 14) * (d.scale || 1) * .7));
    blockRect(d.x - radius, d.y - radius, d.x + radius, d.y + radius);
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

Game.prototype.findNearestWalkableCell = function(cell, maxR = 16) {
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

Game.prototype.findPath = function(startX, startY, goalX, goalY, maxNodes = 14000) {
  this.ensurePathGrid();
  const start = this.findNearestWalkableCell(this.worldToPathCell(startX, startY), 8);
  const goal = this.findNearestWalkableCell(this.worldToPathCell(goalX, goalY), 18);
  if (!start || !goal) return null;
  if (start.x === goal.x && start.y === goal.y) return [this.pathCellToWorld(goal.x, goal.y)];

  const cols = this.pathCols, rows = this.pathRows, total = cols * rows;
  const startKey = start.y * cols + start.x;
  const goalKey = goal.y * cols + goal.x;
  const came = new Int32Array(total);
  const gScore = new Float32Array(total);
  const closed = new Uint8Array(total);
  came.fill(-1);
  gScore.fill(Infinity);
  const heap = [];
  const heuristic = (aKey) => {
    const ax = aKey % cols, ay = Math.floor(aKey / cols);
    return (Math.abs(ax - goal.x) + Math.abs(ay - goal.y)) * 10;
  };
  const push = (key, f) => {
    heap.push([key, f]);
    let i = heap.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (heap[p][1] <= f) break;
      heap[i] = heap[p];
      i = p;
    }
    heap[i] = [key, f];
  };
  const pop = () => {
    const top = heap[0];
    const last = heap.pop();
    if (heap.length && last) {
      let i = 0;
      while (true) {
        let l = i * 2 + 1, r = l + 1;
        if (l >= heap.length) break;
        let c = (r < heap.length && heap[r][1] < heap[l][1]) ? r : l;
        if (heap[c][1] >= last[1]) break;
        heap[i] = heap[c];
        i = c;
      }
      heap[i] = last;
    }
    return top;
  };

  gScore[startKey] = 0;
  push(startKey, heuristic(startKey));
  const dirs = [
    [1,0,10], [-1,0,10], [0,1,10], [0,-1,10],
    [1,1,14], [1,-1,14], [-1,1,14], [-1,-1,14]
  ];
  let visited = 0;
  let found = false;

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
      if (ox && oy) {
        if (this.pathGrid[cy * cols + nx] || this.pathGrid[ny * cols + cx]) continue;
      }
      const ng = gScore[key] + cost;
      if (ng < gScore[nk]) {
        came[nk] = key;
        gScore[nk] = ng;
        push(nk, ng + heuristic(nk));
      }
    }
  }

  if (!found) return null;
  const cells = [];
  let k = goalKey;
  while (k !== -1 && k !== startKey) {
    cells.push(k);
    k = came[k];
  }
  cells.reverse();

  const points = [];
  let prevDx = 999, prevDy = 999;
  let lastCell = startKey;
  for (const ck of cells) {
    const dx = (ck % cols) - (lastCell % cols);
    const dy = Math.floor(ck / cols) - Math.floor(lastCell / cols);
    if (dx !== prevDx || dy !== prevDy || points.length === 0) {
      const p = this.pathCellToWorld(ck % cols, Math.floor(ck / cols));
      points.push(p);
      prevDx = dx; prevDy = dy;
    } else {
      const p = this.pathCellToWorld(ck % cols, Math.floor(ck / cols));
      points[points.length - 1] = p;
    }
    lastCell = ck;
  }
  const finalPoint = this.nearestLandPoint(goalX, goalY, 180) || this.pathCellToWorld(goal.x, goal.y);
  if (!points.length || dist2(points[points.length - 1].x, points[points.length - 1].y, finalPoint.x, finalPoint.y) > 36 * 36) {
    points.push(finalPoint);
  }
  return points.slice(0, 96);
};

Game.prototype.clearUnitPath = function(u) {
  if (!u) return;
  u.path = null;
  u.pathGoal = null;
  u.pathIndex = 0;
  u.pathRetry = 0;
};

Game.prototype.isSegmentWalkable = function(u, ax, ay, bx, by, samples = 9) {
  const steps = Math.max(2, samples);
  for (let i = 1; i <= steps; i++) {
    const t = i / steps;
    const px = ax + (bx - ax) * t;
    const py = ay + (by - ay) * t;
    if (this.isBlocked(px, py, u)) return false;
  }
  return true;
};

Game.prototype.prepareUnitPath = function(u, x, y, d) {
  if (d < 110 && this.isSegmentWalkable(u, u.x, u.y, x, y, 6) && !this.isBlocked(x, y, u)) return null;
  const goalChanged = !u.pathGoal || dist2(u.pathGoal.x, u.pathGoal.y, x, y) > 56 * 56;
  const stale = u.pathVersion !== (this.navVersion || 1);
  u.pathRetry = Math.max(0, (u.pathRetry || 0) - .016);
  if (!u.path || goalChanged || stale) {
    if (u.pathRetry > 0 && !goalChanged && !stale) return u.path;
    u.path = this.findPath(u.x, u.y, x, y);
    u.pathGoal = { x, y };
    u.pathIndex = 0;
    u.pathVersion = this.navVersion || 1;
    u.pathRetry = u.path ? .35 : 1.0;
  }
  return u.path;
};

Game.prototype.nextPathWaypoint = function(u, x, y) {
  if (!u.path || !u.path.length) return { x, y };
  while (u.pathIndex < u.path.length - 1 && dist2(u.x, u.y, u.path[u.pathIndex].x, u.path[u.pathIndex].y) < 28 * 28) {
    u.pathIndex++;
  }
  while (u.pathIndex < u.path.length - 1 && this.isSegmentWalkable(u, u.x, u.y, u.path[u.pathIndex + 1].x, u.path[u.pathIndex + 1].y, 6)) {
    u.pathIndex++;
  }
  return u.path[u.pathIndex] || { x, y };
};
