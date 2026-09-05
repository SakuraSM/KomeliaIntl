import { KomeliaColors, KomeliaGrid, KomeliaSettingsField } from '../core/design/KomeliaDesign';
import { ConfigurationConstant } from '@kit.AbilityKit';
import { i18n } from '@kit.LocalizationKit';
import { router } from '@kit.ArkUI';
import { AppContainer } from '../core/di/AppContainer';
import { AppLanguage, AppThemeMode } from '../core/design/KomeliaDesign';
import { preferredLanguageFor, preferredLanguageMatches } from '../core/i18n/AppLocale';
import { localizedString } from '../core/i18n/LocalizedResources';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppNavigationFrame } from '../components/AppNavigationFrame';

@Entry
@Component
struct AppearanceSettings {
  @State themeIndex: number = 0;
  @State languageIndex: number = 0;
  @State cardWidth: number = KomeliaGrid.defaultCardWidth;

  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    const theme = await AppPreferences.getString('appTheme', AppThemeMode.LIGHT);
    const language = await AppPreferences.getString('appLanguage', AppLanguage.SYSTEM);
    this.cardWidth = KomeliaGrid.normalizedCardWidth(
      await AppPreferences.getNumber('gridCardWidth', KomeliaGrid.defaultCardWidth));
    this.themeIndex = theme === AppThemeMode.DARK ? 1 : theme === AppThemeMode.DARKER ? 2 : 0;
    this.languageIndex = language === AppLanguage.EN ? 1 : language === AppLanguage.ZH_CN ? 2 : 0;
  }
  private async setCardWidth(value: number): Promise<void> {
    this.cardWidth = KomeliaGrid.normalizedCardWidth(value);
    AppStorage.setOrCreate('gridCardWidth', this.cardWidth);
    await AppPreferences.putNumber('gridCardWidth', this.cardWidth);
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
    const desired = preferredLanguageFor(value);
    if (!preferredLanguageMatches(i18n.System.getAppPreferredLanguage(), desired)) {
      i18n.System.setAppPreferredLanguage(desired);
    }
  }

  private themeOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.light_theme')) },
      { value: localizedString($r('app.string.dark_theme')) },
      { value: 'OLED' }
    ];
  }
  private languageOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.follow_system')) },
      { value: localizedString($r('app.string.english_language')) },
      { value: localizedString($r('app.string.simplified_chinese')) }
    ];
  }
  private themeValue(): ResourceStr { return this.themeOptions()[this.themeIndex].value; }
  private languageValue(): ResourceStr { return this.languageOptions()[this.languageIndex].value; }

  @Builder
  private labeledSelect(label: Resource, options: SelectOption[], selected: number,
    value: ResourceStr, componentId: string, onSelect: (index: number) => void) {
    Column({ space: 2 }) {
      Text(label).fontSize(13).fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Select(options).selected(selected).value(value)
        .height(KomeliaSettingsField.selectHeight).width('100%').backgroundColor(Color.Transparent)
        .fontColor($r('app.color.on_surface')).selectedOptionFontColor($r('app.color.on_surface'))
        .onSelect((index: number) => onSelect(index))
    }
    .width(KomeliaSettingsField.width).height(KomeliaSettingsField.height)
    .padding({ left: 12, right: 8, top: 8, bottom: 5 }).id(componentId)
    .backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(8)
  }
  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.appearance')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 10 }) {
          this.labeledSelect($r('app.string.application_theme'), this.themeOptions(), this.themeIndex,
            this.themeValue(), 'appearance_theme_field', (index: number) => this.setTheme(index))
          Divider().color($r('app.color.outline'))
          this.labeledSelect($r('app.string.language'), this.languageOptions(), this.languageIndex,
            this.languageValue(), 'appearance_language_field', (index: number) => this.setLanguage(index))
          Divider().color($r('app.color.outline'))
          Text($r('app.string.grid_cover_minimum_size')).fontSize(16)
            .fontColor($r('app.color.on_surface')).padding({ left: 10, top: 10, bottom: 6 }).width('100%')
          Slider({ value: this.cardWidth, min: KomeliaGrid.minimumCardWidth,
            max: KomeliaGrid.maximumCardWidth, step: 10 })
            .width('100%').onChange((value: number) => this.setCardWidth(value))
          Column({ space: 8 }) {
            Text(`${this.cardWidth}.0`).fontSize(16).fontColor($r('app.color.on_surface'))
            Column() {}
              .width(this.cardWidth).height(Math.round(this.cardWidth / 0.703))
              .backgroundColor($r('app.color.primary_container')).borderRadius(16)
          }
          .width('100%').constraintSize({ minHeight: 400, maxHeight: 520 })
          .alignItems(HorizontalAlign.Center).justifyContent(FlexAlign.Start)
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%')
          .alignItems(HorizontalAlign.Start)
      }.layoutWeight(1).width('100%')
    }.width('100%').height('100%').id('appearance_settings_page')
      .align(Alignment.Top).alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.Start)
      .backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
