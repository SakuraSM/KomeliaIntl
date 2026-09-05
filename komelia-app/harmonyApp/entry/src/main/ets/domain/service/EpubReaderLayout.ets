export interface EpubViewportMetrics {
  widthPixels: number;
  heightPixels: number;
  scaledDensity: number;
}

export function normalizedEpubTopSpacing(value: number): number {
  return [0, 8, 16, 24, 32, 48].includes(value) ? value : 8;
}

function positiveOr(value: number, fallback: number): number {
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

export function calculateEpubViewport(viewportWidthVp: number, viewportHeightVp: number,
  horizontalMarginVp: number, densityPixels: number, scaledDensity: number,
  fallbackWidthPixels: number, fallbackHeightPixels: number, topSpacingVp: number = 0): EpubViewportMetrics {
  const density = positiveOr(densityPixels, 1);
  const textScale = positiveOr(scaledDensity, 1);
  const width = positiveOr(viewportWidthVp, positiveOr(fallbackWidthPixels, 1) / density);
  const height = positiveOr(viewportHeightVp, positiveOr(fallbackHeightPixels, 1) / density);
  const margin = Math.max(0, Number.isFinite(horizontalMarginVp) ? horizontalMarginVp : 0);
  return {
    widthPixels: Math.max(1, Math.round((width - margin * 2) * density)),
    heightPixels: Math.max(1, Math.round((height - Math.max(0, topSpacingVp)) * density)),
    scaledDensity: textScale
  };
}
