import { KomeliaLayout, WindowClass } from '../core/design/KomeliaDesign';

export interface SearchMetadataSummary {
  visible: string[];
  hiddenCount: number;
}

export function searchMetadataTagLimit(windowClass: WindowClass): number {
  if (windowClass === WindowClass.COMPACT) return 3;
  if (windowClass === WindowClass.MEDIUM) return 5;
  return 8;
}

export function summarizeSearchMetadata(values: string[], limit: number): SearchMetadataSummary {
  const visible: string[] = [];
  const seen: Set<string> = new Set<string>();
  for (const raw of values) {
    const value = raw.trim();
    if (value.length === 0 || seen.has(value)) continue;
    seen.add(value);
    visible.push(value);
  }
  const safeLimit = Math.max(0, Math.floor(limit));
  return {
    visible: visible.slice(0, safeLimit),
    hiddenCount: Math.max(0, visible.length - safeLimit)
  };
}

export function searchResultCoverWidth(windowClass: WindowClass): number {
  if (windowClass === WindowClass.COMPACT || windowClass === WindowClass.MEDIUM) return 96;
  return 130;
}

export function searchResultCoverHeight(windowClass: WindowClass, maxCardHeight: number): number {
  const naturalHeight = Math.ceil(searchResultCoverWidth(windowClass) / 0.703);
  const availableHeight = Math.max(0,
    maxCardHeight - KomeliaLayout.spec(windowClass).cardContentPadding * 2);
  return Math.min(naturalHeight, availableHeight);
}

export function searchTypeBarHorizontalInset(windowClass: WindowClass): number {
  return KomeliaLayout.spec(windowClass).pageHorizontalPadding * 2;
}

export function searchResultMinimumHeight(windowClass: WindowClass): number {
  return Math.ceil(searchResultCoverWidth(windowClass) / 0.703 + 24);
}

export function searchResultCardMinimumHeight(windowClass: WindowClass, maxHeight: number): number {
  return Math.min(searchResultMinimumHeight(windowClass), maxHeight);
}
