// Front-end shell: title screen, world management, settings, and generation flow.
'use strict';

class TinySwordsApp {
  constructor() {
    this.game = null;
    this.globalSettings = TinySwordsStorage.globalSettings();
    this.selectedWorldId = null;
    this.bindShell();
    this.refreshCreateWorldDefaults();
    this.renderWorldPreview();
    this.renderWorlds();
    this.showTitle();
  }

  $(id) { return document.getElementById(id); }

  bindShell() {
    this.$('btnSinglePlayer')?.addEventListener('click', () => this.showWorlds());
    this.$('btnContinue')?.addEventListener('click', () => {
      const latest = TinySwordsStorage.latestWorld();
      if (latest) this.startWorld(latest.id);
      else this.showWorlds();
    });
    this.$('btnSettings')?.addEventListener('click', () => this.showSettings());
    this.$('btnBackTitle')?.addEventListener('click', () => this.showTitle());
    this.$('btnBackFromSettings')?.addEventListener('click', () => this.showTitle());
    this.$('btnBackFromCreateWorld')?.addEventListener('click', () => this.showWorlds());
    this.$('btnOpenCreateWorld')?.addEventListener('click', () => this.showCreateWorld());
    this.$('btnCreateWorldFromEmpty')?.addEventListener('click', () => this.showCreateWorld());

    this.$('btnRandomSeed')?.addEventListener('click', () => {
      const input = this.$('worldSeed');
      if (input) {
        input.value = `tinyswords-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e5).toString(36)}`;
        this.renderWorldPreview();
      }
    });

    this.$('btnClearName')?.addEventListener('click', () => {
      const input = this.$('worldName');
      if (input) input.value = '';
      this.renderWorldPreview();
    });

    const createForm = this.$('createWorldForm');
    createForm?.addEventListener('submit', (e) => {
      e.preventDefault();
      this.createWorldFromForm();
    });
    createForm?.addEventListener('input', () => this.renderWorldPreview());
    createForm?.addEventListener('change', () => this.renderWorldPreview());

    const rivals = this.$('worldRivals');
    rivals?.addEventListener('input', () => {
      this.updateRivalsReadout();
      this.renderWorldPreview();
    });

    const settingsForm = this.$('globalSettingsForm');
    settingsForm?.addEventListener('change', () => {
      const data = new FormData(settingsForm);
      this.globalSettings = {
        autosave: data.get('autosave') === 'on',
        graphics: data.get('graphics') || 'balanced',
        edgePan: data.get('edgePan') === 'on',
        volume: Number(data.get('volume') || .8)
      };
      TinySwordsStorage.saveGlobalSettings(this.globalSettings);
      this.refreshCreateWorldDefaults();
      this.renderWorldPreview();
    });

    window.addEventListener('beforeunload', () => {
      if (this.game) this.game.saveToWorldRecord && this.game.saveToWorldRecord('autosave');
    });
  }

  setScreen(name) {
    for (const el of document.querySelectorAll('.screen')) el.classList.add('hidden');
    this.$(name)?.classList.remove('hidden');
    document.body.classList.toggle('in-game', name === 'hud');
  }

  showTitle() {
    this.setScreen('titleScreen');
    const latest = TinySwordsStorage.latestWorld();
    const btn = this.$('btnContinue');
    if (btn) {
      btn.disabled = !latest;
      const sub = btn.querySelector('span:last-child');
      if (sub) sub.textContent = latest ? `${latest.name} • ${this.formatAge(latest.updatedAt)}` : 'No saved worlds yet';
    }
  }

  showWorlds() {
    this.renderWorlds();
    this.setScreen('worldScreen');
  }

  showCreateWorld() {
    this.refreshCreateWorldDefaults();
    this.renderWorldPreview();
    this.setScreen('createWorldScreen');
  }

  showSettings() {
    this.setScreen('settingsScreen');
    const form = this.$('globalSettingsForm');
    if (form) {
      form.elements.graphics.value = this.globalSettings.graphics || 'balanced';
      form.elements.volume.value = this.globalSettings.volume ?? .8;
      form.elements.autosave.checked = this.globalSettings.autosave !== false;
      form.elements.edgePan.checked = this.globalSettings.edgePan !== false;
    }
  }

  refreshCreateWorldDefaults() {
    const form = this.$('createWorldForm');
    if (!form) return;
    if (!form.dataset.initialized) {
      form.elements.size.value = 'large';
      if (form.elements.mapStyle) form.elements.mapStyle.value = 'crossroads';
      form.elements.difficulty.value = 'normal';
      form.elements.resourceDensity.value = 'rich';
      form.elements.rivals.value = '4';
      form.elements.autosave.checked = this.globalSettings.autosave !== false;
      form.dataset.initialized = '1';
    }
    form.elements.graphics.value = this.globalSettings.graphics || 'balanced';
    if (!form.elements.seed.value) form.elements.seed.placeholder = 'Leave empty for random seed';
    this.updateRivalsReadout();
  }

  updateRivalsReadout() {
    const slider = this.$('worldRivals');
    const label = this.$('worldRivalsLabel');
    if (!slider || !label) return;
    const rivals = Number(slider.value || 0);
    label.textContent = rivals === 1 ? '1 rival' : `${rivals} rivals`;
  }

  renderWorldPreview() {
    const form = this.$('createWorldForm');
    const target = this.$('worldPreview');
    if (!form || !target) return;
    const data = new FormData(form);
    const settings = normalizedWorldSettings({
      size: data.get('size'),
      mapStyle: data.get('mapStyle'),
      difficulty: data.get('difficulty'),
      resourceDensity: data.get('resourceDensity'),
      rivals: data.get('rivals'),
      seed: data.get('seed'),
      autosave: data.get('autosave') === 'on',
      graphics: data.get('graphics')
    });
    const preset = WORLD_PRESETS[settings.size] || WORLD_PRESETS.large;
    const mapPreset = MAP_PRESETS[settings.mapStyle] || MAP_PRESETS.crossroads;
    const name = String(data.get('name') || '').trim() || 'Unnamed World';
    target.innerHTML = `
      <div class="world-preview-card">
        <h4>${this.escape(name)}</h4>
        <p>${mapPreset.label} · ${preset.label} · ${DIFFICULTY_PRESETS[settings.difficulty]?.label || settings.difficulty} · ${settings.rivals} rival(s)</p>
      </div>
      <div class="world-preview-card">
        <h4>Map Identity</h4>
        <p>${this.escape(mapPreset.desc)}</p>
      </div>
      <div class="world-preview-card">
        <h4>Expected Scale</h4>
        <p>${preset.width.toLocaleString()} × ${preset.height.toLocaleString()} realm with ${Math.round(preset.areaScale * 100)}% baseline area scale.</p>
      </div>
      <div class="world-preview-card">
        <h4>Included Systems</h4>
        <div class="preview-tags">
          <span class="menu-chip">Persistent slot save</span>
          <span class="menu-chip">Improved AI tactics</span>
          <span class="menu-chip">Hunting wildlife</span>
          <span class="menu-chip">Scaled generation</span>
          <span class="menu-chip">${settings.autosave ? 'Autosave on' : 'Manual save focus'}</span>
          <span class="menu-chip">${settings.graphics}</span>
        </div>
      </div>
      <div class="world-preview-card">
        <h4>Seed</h4>
        <p>${this.escape(settings.seed || 'Randomized at creation')}</p>
      </div>`;
  }

  renderWorlds() {
    const list = this.$('worldList');
    const countLabel = this.$('worldCountLabel');
    const details = this.$('worldDetails');
    const empty = this.$('worldEmptyState');
    if (!list || !countLabel || !details || !empty) return;
    const worlds = TinySwordsStorage.listWorlds();
    countLabel.textContent = worlds.length === 1 ? '1 world' : `${worlds.length} worlds`;

    if (!worlds.length) {
      list.innerHTML = '<div class="empty-worlds">No saved worlds yet. Use Create New World to begin.</div>';
      details.classList.add('hidden');
      empty.classList.remove('hidden');
      this.selectedWorldId = null;
      return;
    }

    if (!worlds.some(w => w.id === this.selectedWorldId)) this.selectedWorldId = worlds[0].id;
    list.innerHTML = '';
    for (const world of worlds) {
      const settings = normalizedWorldSettings(world.settings);
      const row = document.createElement('article');
      row.className = `world-card${world.id === this.selectedWorldId ? ' active' : ''}`;
      row.innerHTML = `
        <div>
          <h4>${this.escape(world.name || 'Unnamed World')}</h4>
          <p>${MAP_PRESETS[settings.mapStyle]?.label || settings.mapStyle} · ${WORLD_PRESETS[settings.size]?.label || settings.size} · ${DIFFICULTY_PRESETS[settings.difficulty]?.label || settings.difficulty} · ${settings.rivals} rival(s)</p>
          <small>Seed: ${this.escape(world.seed || settings.seed || 'random')}</small>
        </div>
        <div class="world-card-footer">
          <span class="world-status">${world.state || 'Created'}</span>
          <small>Updated ${this.formatAge(world.updatedAt)}</small>
        </div>`;
      row.addEventListener('click', () => {
        this.selectedWorldId = world.id;
        this.renderWorlds();
      });
      list.appendChild(row);
    }
    empty.classList.add('hidden');
    details.classList.remove('hidden');
    this.renderWorldDetails(worlds.find(w => w.id === this.selectedWorldId) || worlds[0]);
  }

  renderWorldDetails(worldMeta) {
    const details = this.$('worldDetails');
    if (!details || !worldMeta) return;
    const world = TinySwordsStorage.loadWorld(worldMeta.id) || worldMeta;
    const settings = normalizedWorldSettings(world.settings);
    const playTimeMins = Math.round((world.playTime || 0) / 60);
    details.innerHTML = `
      <div class="world-detail-copy">
        <h3>${this.escape(world.name || 'Unnamed World')}</h3>
        <p>Return to an existing realm, duplicate it as a variant, or remove the slot.</p>
      </div>
      <ul class="world-meta-list">
        <li><span>Map</span><b>${this.escape(MAP_PRESETS[settings.mapStyle]?.label || settings.mapStyle)}</b></li>
        <li><span>World Size</span><b>${this.escape(WORLD_PRESETS[settings.size]?.label || settings.size)}</b></li>
        <li><span>Difficulty</span><b>${this.escape(DIFFICULTY_PRESETS[settings.difficulty]?.label || settings.difficulty)}</b></li>
        <li><span>Resources</span><b>${this.escape(settings.resourceDensity)}</b></li>
        <li><span>Rivals</span><b>${settings.rivals}</b></li>
        <li><span>Graphics</span><b>${this.escape(settings.graphics)}</b></li>
        <li><span>Autosave</span><b>${settings.autosave ? 'Enabled' : 'Disabled'}</b></li>
        <li><span>Play Time</span><b>${playTimeMins ? `${playTimeMins} min` : 'Not played yet'}</b></li>
        <li><span>Last Updated</span><b>${this.escape(this.formatAge(world.updatedAt))}</b></li>
      </ul>
      <div class="world-detail-actions">
        <button id="worldPlayBtn" class="primary">Play World</button>
        <button id="worldDuplicateBtn" class="ghost">Duplicate</button>
        <button id="worldDeleteBtn" class="danger">Delete</button>
      </div>`;
    this.$('worldPlayBtn')?.addEventListener('click', () => this.startWorld(world.id));
    this.$('worldDuplicateBtn')?.addEventListener('click', () => {
      const copy = TinySwordsStorage.duplicateWorld(world.id);
      if (copy) this.selectedWorldId = copy.id;
      this.renderWorlds();
    });
    this.$('worldDeleteBtn')?.addEventListener('click', () => {
      if (confirm(`Delete "${world.name || 'Unnamed World'}"? This cannot be undone.`)) {
        TinySwordsStorage.deleteWorld(world.id);
        this.selectedWorldId = null;
        this.renderWorlds();
      }
    });
  }

  createWorldFromForm() {
    const form = this.$('createWorldForm');
    if (!form) return;
    const data = new FormData(form);
    const settings = normalizedWorldSettings({
      size: data.get('size'),
      mapStyle: data.get('mapStyle'),
      difficulty: data.get('difficulty'),
      resourceDensity: data.get('resourceDensity'),
      rivals: data.get('rivals'),
      seed: data.get('seed'),
      autosave: data.get('autosave') === 'on',
      graphics: data.get('graphics')
    });
    const record = TinySwordsStorage.createWorld(data.get('name'), settings);
    this.selectedWorldId = record.id;
    this.renderWorlds();
    this.startWorld(record.id);
  }

  startWorld(worldId) {
    const record = TinySwordsStorage.loadWorld(worldId);
    if (!record) { this.showWorlds(); return; }
    this.setScreen('generationScreen');
    const bar = this.$('generationBar');
    const text = this.$('generationText');
    const title = this.$('generationTitle');
    if (title) title.textContent = record.state ? 'Loading World' : 'Generating World';

    const steps = [
      ['Preparing seed and world slot', 10],
      ['Generating selected map layout and coastline masks', 28],
      ['Spawning resources, wildlife, and faction bases', 48],
      ['Validating footprints and clearing overlaps', 64],
      ['Building navigation data and AI caches', 81],
      ['Finalizing save state and entering realm', 100]
    ];

    let i = 0;
    const tick = () => {
      const [label, pct] = steps[i];
      if (bar) bar.style.width = `${pct}%`;
      if (text) text.textContent = `${label}... ${pct}%`;
      i += 1;
      if (i < steps.length) requestAnimationFrame(() => setTimeout(tick, 85));
      else requestAnimationFrame(() => {
        if (this.game) this.game.destroy();
        this.game = new Game(record);
        window.tinySwordsGame = this.game;
        canvas.width = VIEW_W;
        canvas.height = VIEW_H;
        ctx.imageSmoothingEnabled = false;
        this.game.draw();
        this.setScreen('hud');
        requestAnimationFrame((t) => this.game.run(t));
      });
    };
    tick();
  }

  returnToMenu() {
    if (this.game) {
      this.game.saveToWorldRecord && this.game.saveToWorldRecord('autosave');
      this.game.destroy();
      this.game = null;
      window.tinySwordsGame = null;
    }
    this.renderWorlds();
    this.showWorlds();
  }

  formatAge(ts) {
    if (!ts) return 'never';
    const secs = Math.max(1, Math.floor((Date.now() - ts) / 1000));
    if (secs < 60) return 'just now';
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  escape(text) {
    return String(text).replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  }
}
