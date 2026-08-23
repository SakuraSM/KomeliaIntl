import { common } from '@kit.AbilityKit';
import { HarmonyDatabase } from '../../data/local/HarmonyDatabase';
import { AppPreferences } from '../../persistence/AppPreferences';

export class AppContainer {
  private static initialization?: Promise<void>;
  private static context?: common.UIAbilityContext;

  static initialize(context: common.UIAbilityContext): Promise<void> {
    if (AppContainer.initialization === undefined) {
      AppContainer.context = context;
      AppPreferences.initialize(context);
      AppContainer.initialization = HarmonyDatabase.initialize(context);
    }
    return AppContainer.initialization;
  }

  static requireContext(): common.UIAbilityContext {
    if (AppContainer.context === undefined) throw new Error('AppContainer 尚未初始化');
    return AppContainer.context;
  }

  static async ready(): Promise<void> {
    if (AppContainer.initialization === undefined) {
      throw new Error('AppContainer 尚未初始化');
    }
    await AppContainer.initialization;
  }
}
