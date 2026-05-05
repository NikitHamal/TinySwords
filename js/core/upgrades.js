// Building upgrades and derived combat/economy stats.
'use strict';

const BUILDING_UPGRADE_SPECS = Object.freeze({
  tower: {
    maxLevel: 2,
    costs: [null, { wood: 120, gold: 135, food: 0 }],
    hpMult: [1.00, 2.05],
    rangeBonus: [0, 60],
    damageBonus: [0, 4],
    archerCount: [1, 2]
  },
  castle: {
    maxLevel: 4,
    costs: [null, { wood: 180, gold: 140, food: 0 }, { wood: 260, gold: 240, food: 0 }, { wood: 360, gold: 340, food: 0 }],
    hpMult: [1.00, 1.22, 1.48, 1.78],
    popBonus: [0, 4, 8, 12],
    trainMult: [1.00, 0.90, 0.80, 0.70],
    rangeBonus: [300, 335, 370, 405],
    damageBonus: [0, 2, 5, 8],
    archerCount: [1, 2, 3, 3]
  },
  house: {
    maxLevel: 2,
    costs: [null, { wood: 95, gold: 55, food: 0 }],
    hpMult: [1.00, 1.70],
    popBonus: [0, 6],
    workerHpBonus: [0, 14],
    workerDamageBonus: [0, 2],
    workerSpeedBonus: [0, 8]
  },
  barracks: {
    maxLevel: 2,
    costs: [null, { wood: 150, gold: 120, food: 0 }],
    hpMult: [1.00, 1.40],
    trainMult: [1.00, 0.78]
  },
  archery: {
    maxLevel: 2,
    costs: [null, { wood: 145, gold: 135, food: 0 }],
    hpMult: [1.00, 1.35],
    trainMult: [1.00, 0.78],
    archerHpBonus: [0, 16],
    archerDamageBonus: [0, 4],
    archerRangeBonus: [0, 38]
  },
  monastery: {
    maxLevel: 2,
    costs: [null, { wood: 135, gold: 145, food: 0 }],
    hpMult: [1.00, 1.35],
    trainMult: [1.00, 0.82]
  }
});

function levelValue(list, level, fallback) {
  if (!Array.isArray(list) || !list.length) return fallback;
  const idx = clamp((level || 1) - 1, 0, list.length - 1);
  return list[idx] === undefined ? fallback : list[idx];
}

function buildingUpgradeSpec(type) { return BUILDING_UPGRADE_SPECS[type] || null; }
function buildingUpgradeMaxLevel(type) { return (buildingUpgradeSpec(type) && buildingUpgradeSpec(type).maxLevel) || 1; }
function buildingLevel(building) { return clamp(Number(building && building.level) || 1, 1, buildingUpgradeMaxLevel(building && building.type)); }
function buildingUpgradeCost(building) {
  const spec = buildingUpgradeSpec(building && building.type);
  if (!spec) return null;
  const current = buildingLevel(building);
  if (current >= spec.maxLevel) return null;
  return spec.costs[current] || null;
}
function buildingUpgradeLabel(building) {
  const next = buildingLevel(building) + 1;
  return `${BUILDINGS[building.type].label} Lv.${next}`;
}
function buildingMaxHpFor(type, level = 1) {
  const def = BUILDINGS[type];
  if (!def) return 1;
  const spec = buildingUpgradeSpec(type);
  const mult = levelValue(spec && spec.hpMult, level, 1);
  return Math.max(1, Math.round(def.hp * mult));
}
function normalizeBuildingStats(building, preserveRatio = true) {
  if (!building || !BUILDINGS[building.type]) return building;
  const oldMax = Math.max(1, Number(building.maxHp) || BUILDINGS[building.type].hp);
  const oldHp = clamp(Number(building.hp) || oldMax, 0, oldMax);
  building.level = buildingLevel(building);
  const nextMax = buildingMaxHpFor(building.type, building.level);
  const wasStructurallyFull = building.build >= 1 && oldHp >= oldMax - 1;
  building.maxHp = nextMax;
  if (preserveRatio) {
    const ratio = oldMax > 0 ? clamp(oldHp / oldMax, 0, 1) : 1;
    building.hp = clamp(Math.max(building.build < 1 ? oldHp : 1, Math.round(nextMax * ratio)), 0, nextMax);
  } else if (wasStructurallyFull && nextMax > oldMax) {
    // Old saves and level migrations often have hp equal to the old max. Treat
    // that as healthy rather than damaged, otherwise every upgraded building
    // renders a fake floating HP bar forever.
    building.hp = nextMax;
  } else {
    building.hp = clamp(oldHp, 0, nextMax);
  }
  return building;
}
function buildingPopulationCapacity(building) {
  if (!building || building.dead || building.build < 1) return 0;
  const def = BUILDINGS[building.type];
  const spec = buildingUpgradeSpec(building.type);
  return (def && def.pop || 0) + levelValue(spec && spec.popBonus, buildingLevel(building), 0);
}
function buildingTrainTime(building, unitType) {
  const unit = UNITS[unitType];
  if (!unit) return 1;
  const spec = buildingUpgradeSpec(building && building.type);
  const mult = levelValue(spec && spec.trainMult, buildingLevel(building), 1);
  return Math.max(1, unit.time * mult);
}
function defensiveArcherCount(building) {
  if (!building || building.dead || building.build < 1) return 0;
  const spec = buildingUpgradeSpec(building.type);
  return levelValue(spec && spec.archerCount, buildingLevel(building), BUILDINGS[building.type] && BUILDINGS[building.type].builtInArcher ? 1 : 0) || 0;
}
function defensiveBuildingRange(building) {
  if (!defensiveArcherCount(building)) return 0;
  const spec = buildingUpgradeSpec(building.type);
  if (building.type === 'tower') return (BUILDINGS.tower.range || 0) + levelValue(spec && spec.rangeBonus, buildingLevel(building), 0);
  return levelValue(spec && spec.rangeBonus, buildingLevel(building), 0);
}
function defensiveBuildingDamage(building) {
  const spec = buildingUpgradeSpec(building && building.type);
  return (UNITS.archer.damage || 0) + levelValue(spec && spec.damageBonus, buildingLevel(building), 0);
}
function defensiveBuildingCooldown(building) {
  // Each defensive archer fires one projectile per volley. Do not shorten the
  // cooldown by archer count, otherwise high-level castles multiply damage twice
  // and become unreadable. Damage scales through projectile count + upgrades.
  const level = buildingLevel(building);
  const base = building && building.type === 'tower' ? UNITS.archer.cd * 0.92 : UNITS.archer.cd * 1.08;
  const levelHaste = building && building.type === 'castle' ? Math.max(0, level - 1) * 0.04 : 0;
  return Math.max(0.72, base - levelHaste);
}

function factionUnitUpgradeBonuses(game, factionId) {
  const bonus = { workerHp: 0, workerDamage: 0, workerSpeed: 0, archerHp: 0, archerDamage: 0, archerRange: 0 };
  if (!game) return bonus;
  for (const b of game.buildings || []) {
    if (!b || b.dead || b.faction !== factionId || b.build < 1) continue;
    const spec = buildingUpgradeSpec(b.type);
    if (!spec) continue;
    const level = buildingLevel(b);
    bonus.workerHp = Math.max(bonus.workerHp, levelValue(spec.workerHpBonus, level, 0));
    bonus.workerDamage = Math.max(bonus.workerDamage, levelValue(spec.workerDamageBonus, level, 0));
    bonus.workerSpeed = Math.max(bonus.workerSpeed, levelValue(spec.workerSpeedBonus, level, 0));
    bonus.archerHp = Math.max(bonus.archerHp, levelValue(spec.archerHpBonus, level, 0));
    bonus.archerDamage = Math.max(bonus.archerDamage, levelValue(spec.archerDamageBonus, level, 0));
    bonus.archerRange = Math.max(bonus.archerRange, levelValue(spec.archerRangeBonus, level, 0));
  }
  return bonus;
}
function unitDerivedStats(game, unit) {
  const def = UNITS[unit && unit.type] || UNITS.worker;
  const bonus = factionUnitUpgradeBonuses(game, unit && unit.faction);
  const stats = { maxHp: def.hp, speed: def.speed, range: def.range, damage: def.damage, cd: def.cd };
  if (unit && unit.type === 'worker') {
    stats.maxHp += bonus.workerHp;
    stats.damage += bonus.workerDamage;
    stats.speed += bonus.workerSpeed;
  } else if (unit && unit.type === 'archer') {
    stats.maxHp += bonus.archerHp;
    stats.damage += bonus.archerDamage;
    stats.range += bonus.archerRange;
  }
  return stats;
}
function normalizeUnitStats(game, unit, preserveRatio = true) {
  if (!unit || !UNITS[unit.type]) return unit;
  const oldMax = Number(unit.maxHp) || UNITS[unit.type].hp;
  const oldHp = Number(unit.hp) || oldMax;
  const stats = unitDerivedStats(game, unit);
  unit.maxHp = stats.maxHp;
  unit.speed = stats.speed;
  unit.range = stats.range;
  unit.damage = stats.damage;
  if (preserveRatio) {
    const ratio = oldMax > 0 ? clamp(oldHp / oldMax, 0, 1) : 1;
    unit.hp = clamp(Math.max(1, Math.round(stats.maxHp * ratio)), 0, stats.maxHp);
  } else {
    unit.hp = clamp(oldHp, 0, stats.maxHp);
  }
  return unit;
}
function unitCombatRange(game, unit) { return unitDerivedStats(game, unit).range; }
function unitCombatDamage(game, unit) { return unitDerivedStats(game, unit).damage; }
function unitAttackCooldown(game, unit) { return unitDerivedStats(game, unit).cd; }

function upgradeBuildingForPlayer(game, building) {
  if (!game || !building || building.entity !== 'building' || building.faction !== 0 || building.build < 1) return false;
  const cost = buildingUpgradeCost(building);
  if (!cost) { game.toast('Already fully upgraded.', 1.2); game.sfx.deny(); return false; }
  if (!pay(game.factions[0], cost)) { game.toast(`Upgrade needs ${fmtCost(cost)}.`, 1.5); game.sfx.deny(); return false; }
  building.level = buildingLevel(building) + 1;
  normalizeBuildingStats(building, false);
  // Upgrades are a full structural refit; fill HP to the new max so the world
  // renderer does not leave unwanted floating HP bars after a clean upgrade.
  building.hp = building.maxHp;
  for (const unit of game.units) if (unit.faction === building.faction && !unit.dead) normalizeUnitStats(game, unit, true);
  game.effects.push({ kind: 'upgrade', x: building.x, y: building.y - building.h * .45, time: .9, max: .9 });
  game.toast(`${BUILDINGS[building.type].label} upgraded to Lv.${building.level}.`, 1.6);
  game.sfx.build(game.audioGainAt ? game.audioGainAt(building.x, building.y) : 1);
  game.uiDirty = true;
  return true;
}
