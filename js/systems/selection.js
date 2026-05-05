// View-aware selection helpers.
'use strict';

function visibleWorldRect(game, pad = 0) {
  return {
    left: game.camera.x - pad,
    top: game.camera.y - pad,
    right: game.camera.x + VIEW_W / game.camera.zoom + pad,
    bottom: game.camera.y + VIEW_H / game.camera.zoom + pad
  };
}
function entityInVisibleFrame(game, entity, pad = 24) {
  const r = visibleWorldRect(game, pad);
  return entity.x >= r.left && entity.x <= r.right && entity.y >= r.top && entity.y <= r.bottom;
}
Game.prototype.selectSameVisibleUnitType = function(source, add = false) {
  if (!source || source.entity !== 'unit' || source.faction !== 0 || source.dead || source.garrisoned) return false;
  const matches = this.units.filter(u => u.faction === 0 && u.type === source.type && !u.dead && !u.garrisoned && entityInVisibleFrame(this, u));
  if (!matches.length) return false;
  if (add) this.select([...new Set([...this.selected.filter(e => e.faction === 0), ...matches])]);
  else this.select(matches);
  this.toast(`${matches.length} ${UNITS[source.type].label}${matches.length === 1 ? '' : 's'} selected in view.`, 1.1);
  return true;
};
Game.prototype.registerSelectionTap = function(entity, add = false) {
  const now = performance.now();
  const previous = this._lastSelectionTap;
  this._lastSelectionTap = entity ? { id: entity.id, type: entity.type, entity: entity.entity, faction: entity.faction, time: now } : null;
  if (!entity || entity.entity !== 'unit' || entity.faction !== 0) return false;
  if (previous && previous.entity === 'unit' && previous.type === entity.type && previous.faction === entity.faction && now - previous.time <= 330) {
    return this.selectSameVisibleUnitType(entity, add);
  }
  return false;
};
