import { AppLanguage } from '../design/KomeliaDesign';

export function preferredLanguageFor(value: string): string {
  if (value === AppLanguage.EN) return 'en';
  if (value === AppLanguage.ZH_CN) return 'zh-Hans';
  return 'default';
}

export function preferredLanguageMatches(current: string, desired: string): boolean {
  const normalizedCurrent = current.trim().replace('_', '-').toLowerCase();
  const normalizedDesired = desired.trim().replace('_', '-').toLowerCase();
  if (normalizedDesired === 'default') {
    return normalizedCurrent.length === 0 || normalizedCurrent === 'default';
  }
  if (normalizedDesired === 'en') {
    return normalizedCurrent === 'en' || normalizedCurrent.startsWith('en-');
  }
  if (normalizedDesired === 'zh-hans') {
    return normalizedCurrent === 'zh-hans' || normalizedCurrent.startsWith('zh-hans-') ||
      normalizedCurrent === 'zh-cn' || normalizedCurrent.startsWith('zh-cn-');
  }
  return normalizedCurrent === normalizedDesired;
}

export function shouldApplyPreferredLanguageOnStartup(current: string, desired: string): boolean {
  // In SYSTEM mode HarmonyOS may report the resolved locale (for example zh-Hans-CN)
  // instead of the "default" sentinel. The settings screen already applies an explicit
  // reset when the user changes back to SYSTEM, so startup must not create a restart loop.
  if (desired.trim().toLowerCase() === 'default') return false;
  return !preferredLanguageMatches(current, desired);
}
