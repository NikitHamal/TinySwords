// Mobile touch controls — floating action buttons for touch-only devices.
'use strict';

(function() {
  const isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  if (!isTouch) return;

  const shell = document.getElementById('game-shell');
  if (!shell) return;

  const wrap = document.createElement('div');
  wrap.className = 'touch-controls';
  wrap.id = 'touchControls';
  wrap.innerHTML = `
    <button class="touch-btn" data-touch="build" title="Build Menu">🏗️</button>
    <button class="touch-btn" data-touch="attack" title="Attack Move">⚔️</button>
    <button class="touch-btn" data-touch="stop" title="Stop">⏹️</button>
    <button class="touch-btn" data-touch="pause" title="Pause">⏸️</button>
  `;
  shell.appendChild(wrap);

  wrap.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-touch]');
    if (!btn) return;
    e.stopPropagation();
    const game = window.tinySwordsGame;
    if (!game) return;

    const action = btn.dataset.touch;
    if (action === 'build') game.toggleBuildMenu();
    else if (action === 'attack') {
      const units = game.selected.filter(u => u.entity === 'unit' && u.faction === 0);
      if (units.length) game.orderMoveFormation(units, game.pointer.wx, game.pointer.wy, true);
    }
    else if (action === 'stop') game.stopSelected && game.stopSelected();
    else if (action === 'pause') game.togglePause && game.togglePause();
  });
})();
