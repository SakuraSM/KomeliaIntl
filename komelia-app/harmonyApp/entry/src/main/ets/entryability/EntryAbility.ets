import { AbilityConstant, Configuration, UIAbility, Want, common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { AppContainer } from '../core/di/AppContainer';

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
    windowStage.getMainWindow().then((mainWindow: window.Window) => {
      mainWindow.setWindowLayoutFullScreen(true);
      mainWindow.setWindowSystemBarProperties({
        statusBarColor: '#00000000',
        navigationBarColor: '#00000000',
        statusBarContentColor: '#202027',
        navigationBarContentColor: '#202027'
      });
    }).catch(() => {
      hilog.warn(DOMAIN, TAG, 'Unable to configure transparent system bars');
    });

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
