/** Normalize an optional host bridge without changing publication or reader preferences. */
export function normalizeChromeAppearance(value) {
  const settings = value && typeof value === 'object' ? value : {};
  return {
    surface: settings.reduceTransparency === true || (settings.surfaceStyle === 'LIQUID_GLASS' && settings.renderingSupported === false) ? 'solid' : settings.surfaceStyle === 'LIQUID_GLASS' ? 'glass' : 'classic',
    theme: ['LIGHT', 'DARK', 'DARKER'].includes(settings.theme) ? settings.theme : 'DARK',
    reducedMotion: settings.reducedMotion === true,
  };
}

/** Refresh on control entry; there is no timer or sampling work while controls are hidden. */
export async function refreshChromeAppearance(host = window, root = document.documentElement) {
  let appearance;
  let timeout;
  try {
    if (typeof host.getChromeAppearance === 'function') {
      const result = await Promise.race([
        host.getChromeAppearance(),
        new Promise(resolve => { timeout = setTimeout(() => resolve(undefined), 750); }),
      ]);
      appearance = result?.result;
    }
  } catch {
    // Older hosts do not expose the optional appearance bridge.
  } finally {
    clearTimeout(timeout);
  }
  const settings = normalizeChromeAppearance(appearance);
  root.dataset.komeliaSurface = settings.surface;
  root.dataset.komeliaTheme = settings.theme;
  root.dataset.komeliaReducedMotion = String(settings.reducedMotion);
  return settings;
}

export function prefersReducedChromeMotion() {
  return document.documentElement.dataset.komeliaReducedMotion === 'true' || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
