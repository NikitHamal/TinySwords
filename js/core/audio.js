class SoundBank {
  constructor() {
    this.ctx = null;
    this.master = null;
    this.enabled = true;
    this.sampleUrls = {
      arrow: 'assets/sounds/tinyswords/arrow.mp3',
      hit: 'assets/sounds/tinyswords/arrow_hit.mp3',
      battle: 'assets/sounds/tinyswords/battle.mp3',
      heal: 'assets/sounds/tinyswords/heal.mp3',
      run: 'assets/sounds/tinyswords/run.mp3',
      sword: 'assets/sounds/tinyswords/sword.mp3'
    };
    this.sampleCache = {};
    this.lastSampleAt = {};
  }

  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    this.ctx = new AC();
    this.master = this.ctx.createGain();
    this.master.gain.value = 0.08;
    this.master.connect(this.ctx.destination);
  }

  resume() {
    this.init();
    if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume().catch(() => {});
  }

  tone(freq = 440, duration = 0.08, type = 'square', vol = 0.10, sweep = 0) {
    this.init();
    if (!this.ctx || !this.master) return;
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t);
    if (sweep) osc.frequency.linearRampToValueAtTime(freq + sweep, t + duration);
    gain.gain.setValueAtTime(vol, t);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    osc.connect(gain);
    gain.connect(this.master);
    osc.start(t);
    osc.stop(t + duration + 0.02);
  }

  playSample(name, volume = 0.32, rate = 1, cooldown = 0) {
    const now = performance.now();
    if (cooldown && this.lastSampleAt[name] && now - this.lastSampleAt[name] < cooldown) return false;
    const url = this.sampleUrls[name];
    if (!url) return false;
    this.lastSampleAt[name] = now;
    let base = this.sampleCache[name];
    if (!base) {
      base = new Audio(url);
      base.preload = 'auto';
      this.sampleCache[name] = base;
    }
    const audio = base.cloneNode();
    audio.volume = this.enabled ? volume : 0;
    audio.playbackRate = rate;
    audio.play().catch(() => {});
    return true;
  }

  click() {
    this.tone(620, 0.045, 'square', 0.07, 90);
    this.tone(890, 0.03, 'square', 0.04, -70);
  }

  build() {
    this.tone(330, 0.07, 'triangle', 0.08, 60);
    this.tone(500, 0.10, 'sine', 0.05, 140);
  }

  deny() {
    this.tone(220, 0.08, 'sawtooth', 0.08, -80);
  }

  attack() {
    if (this.playSample('sword', 0.22, 0.96 + Math.random() * 0.08, 45)) return;
    this.tone(170, 0.06, 'square', 0.08, 50);
    this.tone(110, 0.09, 'triangle', 0.05, -25);
  }

  arrow() {
    if (this.playSample('arrow', 0.18, 0.98 + Math.random() * 0.06, 40)) return;
    this.tone(760, 0.05, 'triangle', 0.05, -200);
  }

  hit() {
    if (this.playSample('hit', 0.18, 0.98 + Math.random() * 0.06, 35)) return;
    this.tone(240, 0.05, 'square', 0.06, -70);
  }

  heal() {
    if (this.playSample('heal', 0.18, 1, 60)) return;
    this.tone(520, 0.08, 'sine', 0.05, 160);
    this.tone(740, 0.12, 'sine', 0.03, 120);
  }

  alert() {
    if (this.playSample('battle', 0.18, 1, 220)) return;
    this.tone(190, 0.11, 'sawtooth', 0.09, 70);
    this.tone(120, 0.14, 'square', 0.06, -40);
  }
}
