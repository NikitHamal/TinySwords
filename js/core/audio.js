// Lightweight WebAudio sound effects.
class SoundBank {
  constructor() { this.ctx = null; this.muted = localStorage.getItem('tiny-swords-rts-muted') === '1'; }
  resume() { if (this.muted) return; if (!this.ctx) this.ctx = new (window.AudioContext || window.webkitAudioContext)(); if (this.ctx.state === 'suspended') this.ctx.resume(); }
  tone(freq, time = .08, type = 'square', gain = .025, slide = 1) {
    if (this.muted) return;
    this.resume();
    if (!this.ctx) return;
    const now = this.ctx.currentTime;
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, now); if (slide !== 1) o.frequency.exponentialRampToValueAtTime(Math.max(20, freq * slide), now + time);
    g.gain.setValueAtTime(gain, now); g.gain.exponentialRampToValueAtTime(0.0001, now + time);
    o.connect(g).connect(this.ctx.destination); o.start(now); o.stop(now + time + .04);
  }
  click() { this.tone(560, .04, 'triangle', .018, 1.3); }
  deny() { this.tone(120, .12, 'square', .03, .7); }
  build() { this.tone(260, .08, 'triangle', .028, 1.4); setTimeout(() => this.tone(410, .1, 'triangle', .02, 1.2), 70); }
  attack() { this.tone(180, .07, 'sawtooth', .018, 1.6); }
  alert() { this.tone(170, .15, 'sawtooth', .03, 1.7); setTimeout(() => this.tone(220, .15, 'sawtooth', .02, 1.2), 120); }
}

