import { ConfigurationConstant } from '@kit.AbilityKit';
import { i18n } from '@kit.LocalizationKit';
import { router } from '@kit.ArkUI';
import { AppContainer } from '../core/di/AppContainer';
import { AppLanguage, AppThemeMode } from '../core/design/KomeliaDesign';
import { AppPreferences } from '../persistence/AppPreferences';

@Entry
@Component
struct AppearanceSettings {
  @State themeIndex: number = 0;
  @State languageIndex: number = 0;

  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    const theme = await AppPreferences.getString('appTheme', AppThemeMode.LIGHT);
    const language = await AppPreferences.getString('appLanguage', AppLanguage.SYSTEM);
    this.themeIndex = theme === AppThemeMode.DARK ? 1 : theme === AppThemeMode.DARKER ? 2 : 0;
    this.languageIndex = language === AppLanguage.EN ? 1 : language === AppLanguage.ZH_CN ? 2 : 0;
  }
  private async setTheme(index: number): Promise<void> {
    this.themeIndex = index;
    const value = index === 1 ? AppThemeMode.DARK : index === 2 ? AppThemeMode.DARKER : AppThemeMode.LIGHT;
    await AppPreferences.putString('appTheme', value);
    AppStorage.setOrCreate('oledMode', value === AppThemeMode.DARKER);
    AppContainer.requireContext().getApplicationContext().setColorMode(index === 0
      ? ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT : ConfigurationConstant.ColorMode.COLOR_MODE_DARK);
  }
  private async setLanguage(index: number): Promise<void> {
    this.languageIndex = index;
    const value = index === 1 ? AppLanguage.EN : index === 2 ? AppLanguage.ZH_CN : AppLanguage.SYSTEM;
    await AppPreferences.putString('appLanguage', value);
    i18n.System.setAppPreferredLanguage(index === 1 ? 'en' : index === 2 ? 'zh-Hans' : 'default');
  }
  build() {
    Column({ space: 20 }) {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('外观与语言').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      Text('主题').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface_variant')).width('100%')
      Row({ space: 8 }) {
        Button('浅色').height(48).backgroundColor(this.themeIndex === 0 ? $r('app.color.primary_container') : $r('app.color.surface_high')).onClick(() => this.setTheme(0))
        Button('深色').height(48).backgroundColor(this.themeIndex === 1 ? $r('app.color.primary_container') : $r('app.color.surface_high')).onClick(() => this.setTheme(1))
        Button('OLED').height(48).backgroundColor(this.themeIndex === 2 ? $r('app.color.primary_container') : $r('app.color.surface_high')).onClick(() => this.setTheme(2))
      }.width('100%')
      Text('应用语言').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface_variant')).width('100%')
      Select([{ value: '跟随系统' }, { value: 'English' }, { value: '简体中文' }])
        .selected(this.languageIndex).value(this.languageIndex === 1 ? 'English' : this.languageIndex === 2 ? '简体中文' : '跟随系统')
        .height(52).width('100%').onSelect((index: number) => this.setLanguage(index))
      Text('语言切换立即重建资源环境；OLED 保持原主题持久化值 DARKER。')
        .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').height('100%')
  }
}
