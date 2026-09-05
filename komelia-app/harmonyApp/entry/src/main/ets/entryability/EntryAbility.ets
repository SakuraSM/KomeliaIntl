import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want, common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { i18n } from '@kit.LocalizationKit';
import { AppContainer } from '../core/di/AppContainer';
import { AppLanguage, AppThemeMode, KomeliaGrid } from '../core/design/KomeliaDesign';
import { preferredLanguageFor, shouldApplyPreferredLanguageOnStartup } from '../core/i18n/AppLocale';
import { preferredAppOrientation } from '../core/window/AppWindowPolicy';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppDestination } from '../state/AppState';
import { localLibraryService } from '../domain/service/LocalLibraryService';

const DOMAIN = 0x0000;
const TAG = 'KomeliaEntryAbility';

export default class EntryAbility extends UIAbility {
  private localScanTimer: number = -1;
  private foreground: boolean = false;
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    AppContainer.initialize(this.context as common.UIAbilityContext).catch((error: Error) => {
      hilog.error(DOMAIN, TAG, `Failed to initialize native data layer: ${error.message}`);
    });
    AppStorage.setOrCreate('colorMode', this.context.config.colorMode);
    AppStorage.setOrCreate('appDestination', AppDestination.HOME);
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
      const cardWidth = KomeliaGrid.normalizedCardWidth(
        await AppPreferences.getNumber('gridCardWidth', KomeliaGrid.defaultCardWidth));
      AppStorage.setOrCreate('oledMode', theme === AppThemeMode.DARKER);
      AppStorage.setOrCreate('gridCardWidth', cardWidth);
      const desiredColorMode = theme === AppThemeMode.LIGHT
        ? ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT : ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
      if (this.context.config.colorMode !== desiredColorMode) {
        this.context.getApplicationContext().setColorMode(desiredColorMode);
      }
      const desiredLanguage = preferredLanguageFor(language);
      const currentLanguage = i18n.System.getAppPreferredLanguage();
      if (shouldApplyPreferredLanguageOnStartup(currentLanguage, desiredLanguage)) {
        hilog.info(DOMAIN, TAG, `Applying preferred language ${desiredLanguage}`);
        i18n.System.setAppPreferredLanguage(desiredLanguage);
      }
      const mainWindow = await windowStage.getMainWindow();
      try {
        await mainWindow.setPreferredOrientation(preferredAppOrientation());
      } catch (error) {
        hilog.warn(DOMAIN, TAG, `Unable to restore system window orientation policy: ${(error as Error).message}`);
      }
      mainWindow.setWindowLayoutFullScreen(false);
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

  onForeground(): void {
    this.foreground = true;
    AppContainer.ready().then(() => {
      if (!this.foreground) return;
      localLibraryService.scanAll(true).catch(() => {});
      if (this.localScanTimer >= 0) clearInterval(this.localScanTimer);
      this.localScanTimer = setInterval(() => { localLibraryService.scanAll(true).catch(() => {}); }, 60000);
    }).catch(() => {});
  }

  onBackground(): void {
    this.foreground = false;
    if (this.localScanTimer >= 0) clearInterval(this.localScanTimer);
    this.localScanTimer = -1;
  }

  onDestroy(): void {
    this.onBackground();
  }
}
