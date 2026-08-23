export interface HomeGroup {
  id: string;
  label: string;
  enabled: boolean;
}

export const DEFAULT_HOME_GROUPS: HomeGroup[] = [
  { id: 'onDeck', label: '继续阅读', enabled: true },
  { id: 'latest', label: '最近添加的书籍', enabled: true },
  { id: 'series', label: '系列', enabled: true }
];

export function defaultHomeGroups(): HomeGroup[] {
  return DEFAULT_HOME_GROUPS.map((item: HomeGroup) => ({
    id: item.id,
    label: item.label,
    enabled: item.enabled
  }));
}

export function parseHomeGroups(raw: string): HomeGroup[] {
  if (raw.length === 0) return defaultHomeGroups();
  try {
    const parsed = JSON.parse(raw) as HomeGroup[];
    const known = new Map<string, HomeGroup>();
    for (let index = 0; index < DEFAULT_HOME_GROUPS.length; index++) {
      known.set(DEFAULT_HOME_GROUPS[index].id, DEFAULT_HOME_GROUPS[index]);
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
