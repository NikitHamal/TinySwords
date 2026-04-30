// Boot after every system has patched Game.prototype.
loadImages(IMAGE_PATHS).then(() => {
  document.body.classList.add('ready');
  HUD.root.classList.remove('hidden');
  canvas.width = VIEW_W; canvas.height = VIEW_H;
  ctx.imageSmoothingEnabled = false;
  const game = new Game();
  window.tinySwordsGame = game;
  requestAnimationFrame(t => game.run(t));
});
