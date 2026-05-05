// Tutorial / Onboarding system for first-time Tiny Swords players.
'use strict';

const TUTORIAL_KEY = 'tinyswords.tutorial.completed.v1';

const TUTORIAL_STEPS = [
  {
    id: 'welcome',
    icon: '⚔️',
    title: 'Welcome, Commander!',
    body: 'Tiny Swords is a real-time strategy game. You\'ll build a base, train an army, gather resources, and conquer rival realms.',
    highlight: null
  },
  {
    id: 'camera',
    icon: '🗺️',
    title: 'Look Around',
    body: 'Use WASD or arrow keys to pan the camera. Scroll to zoom in and out. On mobile, drag the map to move around.',
    highlight: null
  },
  {
    id: 'select',
    icon: '👆',
    title: 'Select Units',
    body: 'Left-click a unit to select it. Drag a box to select multiple units. Your workers gather resources and construct buildings.',
    highlight: null
  },
  {
    id: 'resources',
    icon: '🪵',
    title: 'Gather Resources',
    body: 'Select workers, then right-click trees for wood, gold veins for gold, or animals for food. Resources fuel everything you build and train.',
    highlight: 'topbar'
  },
  {
    id: 'build',
    icon: '🏗️',
    title: 'Build Structures',
    body: 'Press B to open the build menu (or tap the Build button). Place houses for population, barracks to train warriors, and towers for defense.',
    highlight: null
  },
  {
    id: 'train',
    icon: '🗡️',
    title: 'Train Your Army',
    body: 'Select your Castle or Barracks, then use the action panel to train warriors, archers, lancers, and monks. Each unit costs resources and population.',
    highlight: null
  },
  {
    id: 'combat',
    icon: '⚔️',
    title: 'Attack & Defend',
    body: 'Select your army and right-click an enemy to attack. Use formations (Z/X/C/V keys) for tactical advantage. Set rally flags by right-clicking the map with a building selected.',
    highlight: null
  },
  {
    id: 'save',
    icon: '💾',
    title: 'Save Your Realm',
    body: 'Your world auto-saves every 45 seconds. Press Ctrl+S to save manually, or Space/P to pause. Press H any time for a quick reference guide.',
    highlight: null
  },
  {
    id: 'ready',
    icon: '🏰',
    title: 'You\'re Ready!',
    body: 'Scout the map, expand your economy, and crush your rivals. Good luck, Commander!',
    highlight: null
  }
];

class TutorialSystem {
  constructor() {
    this.currentStep = 0;
    this.active = false;
    this.overlay = null;
    this.highlightEl = null;
    this.completed = this._isCompleted();
  }

  _isCompleted() {
    try { return localStorage.getItem(TUTORIAL_KEY) === 'true'; }
    catch { return false; }
  }

  _markCompleted() {
    try { localStorage.setItem(TUTORIAL_KEY, 'true'); }
    catch { /* storage unavailable */ }
    this.completed = true;
  }

  shouldShow() {
    return !this.completed;
  }

  start() {
    if (this.active) return;
    this.active = true;
    this.currentStep = 0;
    this._render();
  }

  next() {
    if (this.currentStep < TUTORIAL_STEPS.length - 1) {
      this.currentStep++;
      this._render();
    } else {
      this.finish();
    }
  }

  prev() {
    if (this.currentStep > 0) {
      this.currentStep--;
      this._render();
    }
  }

  skip() {
    this.finish();
  }

  finish() {
    this.active = false;
    this._markCompleted();
    this._cleanup();
  }

  _cleanup() {
    if (this.overlay) { this.overlay.remove(); this.overlay = null; }
    if (this.highlightEl) { this.highlightEl.remove(); this.highlightEl = null; }
  }

  _render() {
    this._cleanup();
    const step = TUTORIAL_STEPS[this.currentStep];
    if (!step) return;

    // Highlight target element
    if (step.highlight) {
      const target = document.getElementById(step.highlight);
      if (target) {
        const rect = target.getBoundingClientRect();
        this.highlightEl = document.createElement('div');
        this.highlightEl.className = 'tutorial-highlight';
        this.highlightEl.style.cssText = `left:${rect.left - 4}px;top:${rect.top - 4}px;width:${rect.width + 8}px;height:${rect.height + 8}px`;
        document.getElementById('game-shell').appendChild(this.highlightEl);
      }
    }

    // Build overlay
    this.overlay = document.createElement('div');
    this.overlay.className = 'tutorial-overlay';
    this.overlay.innerHTML = `
      <div class="tutorial-card pixel-panel">
        <div class="tutorial-dots">
          ${TUTORIAL_STEPS.map((_, i) => `<span class="tutorial-dot ${i < this.currentStep ? 'done' : i === this.currentStep ? 'active' : ''}"></span>`).join('')}
        </div>
        <span class="tutorial-icon">${step.icon}</span>
        <h3>${step.title}</h3>
        <p>${step.body}</p>
        <div class="tutorial-actions">
          ${this.currentStep > 0 ? '<button class="tutorial-btn secondary" data-tut="prev">Back</button>' : '<button class="tutorial-btn secondary" data-tut="skip">Skip All</button>'}
          <button class="tutorial-btn" data-tut="next">${this.currentStep === TUTORIAL_STEPS.length - 1 ? 'Start Playing' : 'Next'}</button>
        </div>
      </div>`;

    this.overlay.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-tut]');
      if (!btn) return;
      e.stopPropagation();
      const action = btn.dataset.tut;
      if (action === 'next') this.next();
      else if (action === 'prev') this.prev();
      else if (action === 'skip') this.skip();
    });

    document.getElementById('game-shell').appendChild(this.overlay);
  }
}

// Global instance
const tutorial = new TutorialSystem();
