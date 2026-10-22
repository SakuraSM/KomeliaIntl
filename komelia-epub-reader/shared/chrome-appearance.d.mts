export interface ChromeAppearance {
  surfaceStyle?: 'CLASSIC' | 'LIQUID_GLASS';
  theme?: 'LIGHT' | 'DARK' | 'DARKER';
  reduceTransparency?: boolean;
  reducedMotion?: boolean;
  renderingSupported?: boolean;
}
export interface NormalizedChromeAppearance {
  surface: 'classic' | 'glass' | 'solid';
  theme: 'LIGHT' | 'DARK' | 'DARKER';
  reducedMotion: boolean;
}
export function normalizeChromeAppearance(value: unknown): NormalizedChromeAppearance;
export function refreshChromeAppearance(
  host?: {getChromeAppearance?: () => Promise<{result: ChromeAppearance}>},
  root?: {dataset: Record<string, string | undefined>},
): Promise<NormalizedChromeAppearance>;

export function prefersReducedChromeMotion(): boolean;
