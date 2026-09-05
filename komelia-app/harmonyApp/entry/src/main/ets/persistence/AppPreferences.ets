import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';

const STORE_NAME = 'komelia_harmony_settings';

export class AppPreferences {
  private static context?: common.UIAbilityContext;

  static initialize(context: common.UIAbilityContext): void {
    AppPreferences.context = context;
  }

  static async getString(key: string, fallback: string = ''): Promise<string> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    return await store.get(key, fallback) as string;
  }

  static async getBoolean(key: string, fallback: boolean = false): Promise<boolean> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    return await store.get(key, fallback) as boolean;
  }

  static async getNumber(key: string, fallback: number = 0): Promise<number> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    return await store.get(key, fallback) as number;
  }

  static async putString(key: string, value: string): Promise<void> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    await store.put(key, value);
    await store.flush();
  }

  static async putBoolean(key: string, value: boolean): Promise<void> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    await store.put(key, value);
    await store.flush();
  }

  static async putNumber(key: string, value: number): Promise<void> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    await store.put(key, value);
    await store.flush();
  }

  static async clear(): Promise<void> {
    const store = await preferences.getPreferences(AppPreferences.requireContext(), STORE_NAME);
    await store.clear();
    await store.flush();
  }

  private static requireContext(): common.UIAbilityContext {
    if (AppPreferences.context === undefined) {
      throw new Error('APP_PREFERENCES_NOT_INITIALIZED');
    }
    return AppPreferences.context;
  }
}
