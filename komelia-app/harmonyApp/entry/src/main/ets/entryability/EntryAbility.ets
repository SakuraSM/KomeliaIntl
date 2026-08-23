import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want, common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { i18n } from '@kit.LocalizationKit';
import { AppContainer } from '../core/di/AppContainer';
import { AppLanguage, AppThemeMode } from '../core/design/KomeliaDesign';
import { AppPreferences } from '../persistence/AppPreferences';

const DOMAIN = 0x0000;
const TAG = 'KomeliaEntryAbility';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    AppContainer.initialize(this.context as common.UIAbilityContext).catch((error: Error) => {
      hilog.error(DOMAIN, TAG, `Failed to initialize native data layer: ${error.message}`);
    });
    AppStorage.setOrCreate('colorMode', this.context.config.colorMode);
    hilog.info(DOMAIN, TAG, 'Komelia HarmonyOS ability created');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    this.prepareWindow(windowStage);
  }

  private async prepareWindow(windowStage: window.WindowStage): Promise<void> {
    try {
      await AppContainer.ready();
      const theme = await AppPreferences.getString('appTheme', AppThemeMode.LIGHT);
      const language = await AppPreferences.getString('appLanguage', AppLanguage.SYSTEM);
      AppStorage.setOrCreate('oledMode', theme === AppThemeMode.DARKER);
      this.context.getApplicationContext().setColorMode(theme === AppThemeMode.LIGHT
        ? ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT : ConfigurationConstant.ColorMode.COLOR_MODE_DARK);
      i18n.System.setAppPreferredLanguage(language === AppLanguage.EN ? 'en' :
        language === AppLanguage.ZH_CN ? 'zh-Hans' : 'default');
      const mainWindow = await windowStage.getMainWindow();
      mainWindow.setWindowLayoutFullScreen(true);
      mainWindow.setWindowSystemBarProperties({
        statusBarColor: '#00000000', navigationBarColor: '#00000000',
        statusBarContentColor: theme === AppThemeMode.LIGHT ? '#202027' : '#F2EEF8',
        navigationBarContentColor: theme === AppThemeMode.LIGHT ? '#202027' : '#F2EEF8'
      });
    } catch (error) {
      hilog.warn(DOMAIN, TAG, `Unable to restore presentation settings: ${(error as Error).message}`);
    }
    windowStage.loadContent('pages/Index', (error) => {
      if (error.code !== 0) {
        hilog.error(DOMAIN, TAG, `Failed to load Index: ${error.code} ${error.message}`);
      }
    });
  }

  onConfigurationUpdate(configuration: Configuration): void {
    AppStorage.setOrCreate('colorMode', configuration.colorMode);
  }
}
