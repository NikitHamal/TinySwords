// Boot after all gameplay systems are registered.
loadImages(IMAGE_PATHS).then(() => {
  document.body.classList.add('ready');
  HUD.root.classList.add('hidden');
  canvas.width = VIEW_W;
  canvas.height = VIEW_H;
  ctx.imageSmoothingEnabled = false;
  window.tinySwordsApp = new TinySwordsApp();
}).catch((err) => {
  console.error(err);
  HUD.loading.textContent = 'Failed to load Tiny Swords assets. Check the asset folder paths.';
});
