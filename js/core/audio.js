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
    const stored = (typeof TinySwordsStorage !== 'undefined' && TinySwordsStorage.globalSettings) ? Number(TinySwordsStorage.globalSettings().volume ?? .8) : .8;
    this.master.gain.value = 0.10 * clamp(Number.isFinite(stored) ? stored : .8, 0, 1);
    this.master.connect(this.ctx.destination);
  }

  resume() {
    this.init();
    if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume().catch(() => {});
  }

  tone(freq = 440, duration = 0.08, type = 'square', vol = 0.10, sweep = 0) {
    this.init();
    if (!this.ctx || !this.master || !this.enabled || vol <= 0.001) return;
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
    if (!this.enabled || volume <= 0.012) return false;
    const now = performance.now();
    const cooldownKey = `${name}`;
    if (cooldown && this.lastSampleAt[cooldownKey] && now - this.lastSampleAt[cooldownKey] < cooldown) return false;
    const url = this.sampleUrls[name];
    if (!url) return false;
    this.lastSampleAt[cooldownKey] = now;
    let base = this.sampleCache[name];
    if (!base) {
      base = new Audio(url);
      base.preload = 'auto';
      this.sampleCache[name] = base;
    }
    const audio = base.cloneNode();
    audio.volume = clamp(volume, 0, 1);
    audio.playbackRate = rate;
    audio.play().catch(() => {});
    return true;
  }

  click(gain = 1) {
    gain = clamp(gain, 0, 1);
    this.tone(620, 0.045, 'square', 0.07 * gain, 90);
    this.tone(890, 0.03, 'square', 0.04 * gain, -70);
  }

  build(gain = 1) {
    gain = clamp(gain, 0, 1);
    this.tone(330, 0.07, 'triangle', 0.08 * gain, 60);
    this.tone(500, 0.10, 'sine', 0.05 * gain, 140);
  }

  deny(gain = 1) {
    gain = clamp(gain, 0, 1);
    this.tone(220, 0.08, 'sawtooth', 0.08 * gain, -80);
  }

  attack(gain = 1) {
    gain = clamp(gain, 0, 1);
    if (gain <= 0.012) return;
    if (this.playSample('sword', 0.22 * gain, 0.96 + Math.random() * 0.08, 45)) return;
    this.tone(170, 0.06, 'square', 0.08 * gain, 50);
    this.tone(110, 0.09, 'triangle', 0.05 * gain, -25);
  }

  arrow(gain = 1) {
    gain = clamp(gain, 0, 1);
    if (gain <= 0.012) return;
    if (this.playSample('arrow', 0.18 * gain, 0.98 + Math.random() * 0.06, 40)) return;
    this.tone(760, 0.05, 'triangle', 0.05 * gain, -200);
  }

  hit(gain = 1) {
    gain = clamp(gain, 0, 1);
    if (gain <= 0.012) return;
    if (this.playSample('hit', 0.18 * gain, 0.98 + Math.random() * 0.06, 35)) return;
    this.tone(240, 0.05, 'square', 0.06 * gain, -70);
  }

  heal(gain = 1) {
    gain = clamp(gain, 0, 1);
    if (gain <= 0.012) return;
    if (this.playSample('heal', 0.18 * gain, 1, 60)) return;
    this.tone(520, 0.08, 'sine', 0.05 * gain, 160);
    this.tone(740, 0.12, 'sine', 0.03 * gain, 120);
  }

  alert(gain = 1) {
    gain = clamp(gain, 0, 1);
    if (gain <= 0.012) return;
    if (this.playSample('battle', 0.18 * gain, 1, 220)) return;
    this.tone(190, 0.11, 'sawtooth', 0.09 * gain, 70);
    this.tone(120, 0.14, 'square', 0.06 * gain, -40);
  }
}
