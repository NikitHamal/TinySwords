// Time-of-day lighting and lightweight weather particles.
'use strict';

Game.prototype.initAtmosphere = function() {
  if (this.atmosphere) return this.atmosphere;
  this.atmosphere = {
    cycleTime: 0,
    cycleLength: 180,
    weather: 'clear',
    weatherTime: 0,
    nextWeather: 25 + Math.random() * 45,
    particles: []
  };
  return this.atmosphere;
};

Game.prototype.updateAtmosphere = function(dt) {
  const a = this.initAtmosphere();
  a.cycleTime = (a.cycleTime + dt) % a.cycleLength;
  if (a.weather === 'clear') {
    a.nextWeather -= dt;
    if (a.nextWeather <= 0) {
      a.weather = Math.random() < 0.72 ? 'rain' : 'snow';
      a.weatherTime = 16 + Math.random() * 34;
    }
  } else {
    a.weatherTime -= dt;
    const maxParticles = a.weather === 'rain' ? 120 : 85;
    const spawnCount = a.weather === 'rain' ? 5 : 3;
    for (let i = 0; i < spawnCount && a.particles.length < maxParticles; i++) {
      a.particles.push({
        x: Math.random() * VIEW_W,
        y: -16 - Math.random() * 80,
        vx: a.weather === 'rain' ? -85 + Math.random() * 28 : -20 + Math.random() * 40,
        vy: a.weather === 'rain' ? 560 + Math.random() * 170 : 48 + Math.random() * 78,
        size: a.weather === 'rain' ? 1 : 1.4 + Math.random() * 1.8,
        life: 2.0 + Math.random() * 0.8
      });
    }
    if (a.weatherTime <= 0) {
      a.weather = 'clear';
      a.nextWeather = 42 + Math.random() * 85;
      a.particles.length = 0;
    }
  }
  for (let i = a.particles.length - 1; i >= 0; i--) {
    const p = a.particles[i];
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.life -= dt;
    if (p.life <= 0 || p.y > VIEW_H + 30 || p.x < -80 || p.x > VIEW_W + 80) a.particles.splice(i, 1);
  }
};

Game.prototype.dayNightLighting = function() {
  const a = this.initAtmosphere();
  const t = a.cycleTime / a.cycleLength;
  const night = Math.max(0, Math.cos((t - 0.5) * Math.PI * 2));
  const dawn = Math.max(0, 1 - Math.abs(t - 0.25) / 0.10);
  const dusk = Math.max(0, 1 - Math.abs(t - 0.75) / 0.10);
  return { night: clamp(night, 0, 1), warm: clamp(Math.max(dawn, dusk), 0, 1) };
};

Game.prototype.drawAtmosphereOverlay = function() {
  const a = this.initAtmosphere();
  const light = this.dayNightLighting();
  ctx.save();
  if (light.night > 0.02) {
    ctx.fillStyle = `rgba(9,18,42,${0.08 + light.night * 0.34})`;
    ctx.fillRect(0, 0, VIEW_W, VIEW_H);
  }
  if (light.warm > 0.02) {
    ctx.fillStyle = `rgba(246,153,75,${light.warm * 0.12})`;
    ctx.fillRect(0, 0, VIEW_W, VIEW_H);
  }
  if (a.weather !== 'clear') {
    ctx.fillStyle = a.weather === 'rain' ? 'rgba(18,34,48,.13)' : 'rgba(230,242,255,.08)';
    ctx.fillRect(0, 0, VIEW_W, VIEW_H);
    if (a.weather === 'rain') {
      ctx.strokeStyle = 'rgba(185,220,255,.54)';
      ctx.lineWidth = 1;
      for (const p of a.particles) {
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
        ctx.lineTo(p.x + p.vx * 0.035, p.y + 18);
        ctx.stroke();
      }
    } else {
      ctx.fillStyle = 'rgba(245,250,255,.74)';
      for (const p of a.particles) ctx.fillRect(p.x, p.y, p.size, p.size);
    }
  }
  ctx.restore();
};
