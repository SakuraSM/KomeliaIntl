export interface HomeGroup {
  id: string;
  label: string;
  enabled: boolean;
}

export interface HomeGroupOverflowLayout {
  visibleGroupIndices: number[];
  overflowGroupIndices: number[];
}

export function homeGroupChipWidth(textWidth: number): number {
  return Math.max(0, textWidth) + 32;
}

export function homeMoreChipWidth(textWidth: number): number {
  return Math.max(0, textWidth) + 56;
}

export function homeOverviewItemLimit(columnCount: number, overview: boolean): number {
  if (!overview) return Number.MAX_SAFE_INTEGER;
  return Math.max(1, Math.floor(columnCount)) * 2;
}

export function filterHomeGroupsForNavigation(groups: HomeGroup[], itemCounts: number[]): HomeGroup[] {
  const result: HomeGroup[] = [];
  for (let index = 0; index < groups.length; index++) {
    const itemCount = index < itemCounts.length ? itemCounts[index] : 0;
    if (groups[index].enabled && Math.max(0, itemCount) > 0) {
      result.push(groups[index]);
    }
  }
  return result;
}

function occupiedHomeGroupWidth(allChipWidth: number, moreChipWidth: number, groupWidths: number[],
  indices: number[], spacing: number, includeMore: boolean): number {
  let width = Math.max(0, allChipWidth);
  for (let index = 0; index < indices.length; index++) {
    width += Math.max(0, groupWidths[indices[index]]);
  }
  if (includeMore) width += Math.max(0, moreChipWidth);
  const itemCount = 1 + indices.length + (includeMore ? 1 : 0);
  return width + Math.max(0, spacing) * Math.max(0, itemCount - 1);
}

export function calculateHomeGroupOverflowLayout(availableWidth: number, allChipWidth: number,
  moreChipWidth: number, groupWidths: number[], activeGroupIndex: number, spacing: number): HomeGroupOverflowLayout {
  if (groupWidths.length === 0) return { visibleGroupIndices: [], overflowGroupIndices: [] };
  const allIndices: number[] = [];
  for (let index = 0; index < groupWidths.length; index++) allIndices.push(index);
  if (occupiedHomeGroupWidth(allChipWidth, moreChipWidth, groupWidths, allIndices, spacing, false) <=
    Math.max(0, availableWidth)) {
    return { visibleGroupIndices: allIndices, overflowGroupIndices: [] };
  }

  const effectiveWidths = groupWidths.map((width: number) => Math.max(0, width));
  const activeValid = activeGroupIndex >= 0 && activeGroupIndex < groupWidths.length;
  if (activeValid) {
    const promotedCapacity = Math.max(0,
      availableWidth - Math.max(0, allChipWidth) - Math.max(0, moreChipWidth) - Math.max(0, spacing) * 2);
    effectiveWidths[activeGroupIndex] = Math.min(effectiveWidths[activeGroupIndex], promotedCapacity);
  }

  const visible: number[] = [];
  for (let index = 0; index < groupWidths.length; index++) {
    const candidate = visible.concat(index);
    if (occupiedHomeGroupWidth(allChipWidth, moreChipWidth, effectiveWidths, candidate, spacing, true) <=
      Math.max(0, availableWidth)) {
      visible.push(index);
    } else {
      break;
    }
  }
  if (activeValid && !visible.includes(activeGroupIndex)) {
    let candidate = visible.concat(activeGroupIndex);
    while (visible.length > 0 && occupiedHomeGroupWidth(allChipWidth, moreChipWidth, effectiveWidths,
      candidate, spacing, true) > Math.max(0, availableWidth)) {
      visible.pop();
      candidate = visible.concat(activeGroupIndex);
    }
    if (occupiedHomeGroupWidth(allChipWidth, moreChipWidth, effectiveWidths, candidate, spacing, true) <=
      Math.max(0, availableWidth)) visible.push(activeGroupIndex);
  }

  const overflow: number[] = [];
  for (let index = 0; index < groupWidths.length; index++) {
    if (!visible.includes(index)) overflow.push(index);
  }
  return { visibleGroupIndices: visible, overflowGroupIndices: overflow };
}

export const DEFAULT_HOME_GROUPS: HomeGroup[] = [
  { id: 'keepReading', label: 'keepReading', enabled: true },
  { id: 'onDeck', label: 'onDeck', enabled: true },
  { id: 'recentlyReleased', label: 'recentlyReleased', enabled: true },
  { id: 'latest', label: 'latest', enabled: true },
  { id: 'series', label: 'series', enabled: true },
  { id: 'updatedSeries', label: 'updatedSeries', enabled: true },
  { id: 'recentlyRead', label: 'recentlyRead', enabled: true },
  { id: 'local', label: 'local', enabled: true },
  { id: 'downloads', label: 'downloads', enabled: true }
];

const LEGACY_HOME_GROUP_IDS: string[] = ['onDeck', 'latest', 'series'];

export function defaultHomeGroups(): HomeGroup[] {
  const result: HomeGroup[] = [];
  for (let index = 0; index < DEFAULT_HOME_GROUPS.length; index++) {
    const item = DEFAULT_HOME_GROUPS[index];
    result.push({ id: item.id, label: item.label, enabled: item.enabled });
  }
  return result;
}

export function parseHomeGroups(raw: string): HomeGroup[] {
  if (raw.length === 0) return defaultHomeGroups();
  try {
    const parsed = JSON.parse(raw) as HomeGroup[];
    const known = new Map<string, HomeGroup>();
    for (let index = 0; index < DEFAULT_HOME_GROUPS.length; index++) {
      known.set(DEFAULT_HOME_GROUPS[index].id, DEFAULT_HOME_GROUPS[index]);
    }
    const parsedIds = parsed.map((item: HomeGroup) => item.id);
    const legacyConfiguration = parsed.length > 0 &&
      parsedIds.every((id: string) => LEGACY_HOME_GROUP_IDS.includes(id));
    if (legacyConfiguration) {
      const migrated: HomeGroup[] = [];
      for (let index = 0; index < DEFAULT_HOME_GROUPS.length; index++) {
        const fallback = DEFAULT_HOME_GROUPS[index];
        const previous = parsed.find((item: HomeGroup) => item.id === fallback.id);
        migrated.push({
          id: fallback.id,
          label: fallback.label,
          enabled: previous === undefined ? fallback.enabled : previous.enabled !== false
        });
      }
      return migrated;
    }
    const result: HomeGroup[] = [];
    for (let index = 0; index < parsed.length; index++) {
      const fallback = known.get(parsed[index].id);
      if (fallback !== undefined && !result.some((item: HomeGroup) => item.id === fallback.id)) {
        result.push({ id: fallback.id, label: fallback.label, enabled: parsed[index].enabled !== false });
      }
    }
    for (let index = 0; index < DEFAULT_HOME_GROUPS.length; index++) {
      const fallback = DEFAULT_HOME_GROUPS[index];
      if (!result.some((item: HomeGroup) => item.id === fallback.id)) {
        result.push({ id: fallback.id, label: fallback.label, enabled: fallback.enabled });
      }
    }
    return result;
  } catch (_) {
    return defaultHomeGroups();
  }
}

export function homeGroupLabel(id: string): ResourceStr {
  if (id === 'keepReading') return $r('app.string.home_group_keep_reading');
  if (id === 'onDeck') return $r('app.string.home_group_on_deck');
  if (id === 'recentlyReleased') return $r('app.string.home_group_recently_released');
  if (id === 'latest') return $r('app.string.home_group_latest');
  if (id === 'series') return $r('app.string.home_group_recently_added_series');
  if (id === 'updatedSeries') return $r('app.string.home_group_recently_updated_series');
  if (id === 'recentlyRead') return $r('app.string.home_group_recently_read');
  if (id === 'local') return $r('app.string.local_source_files');
  if (id === 'downloads') return $r('app.string.local_source_downloaded');
  return id;
}
