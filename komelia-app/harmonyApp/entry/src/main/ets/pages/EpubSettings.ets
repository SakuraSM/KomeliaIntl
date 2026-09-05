import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { AppPreferences } from '../persistence/AppPreferences';
import { normalizedEpubTopSpacing } from '../domain/service/EpubReaderLayout';

@Entry
@Component
struct EpubSettings {
  @State fontSize: number = 18;
  @State lineHeight: number = 1.8;
  @State pageMargin: number = 16;
  @State paginated: boolean = true;
  @State nightMode: boolean = false;
  @State keepScreenOn: boolean = false;
  @State immersiveMode: boolean = false;
  @State topSpacing: number = 8;
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.fontSize = Number(await AppPreferences.getString('epub.fontSize', '18'));
    this.lineHeight = Number(await AppPreferences.getString('epub.lineHeight', '1.8'));
    this.pageMargin = Number(await AppPreferences.getString('epub.margin', '16'));
    this.paginated = await AppPreferences.getBoolean('epub.paginated', true);
    this.nightMode = await AppPreferences.getBoolean('epub.nightMode');
    this.keepScreenOn = await AppPreferences.getBoolean('epub.keepScreenOn');
    this.immersiveMode = await AppPreferences.getBoolean('epub.immersiveMode');
    this.topSpacing = normalizedEpubTopSpacing(await AppPreferences.getNumber('epub.topSpacing', 8));
  }
  private async save(): Promise<void> {
    await Promise.all([
      AppPreferences.putString('epub.fontSize', `${this.fontSize}`),
      AppPreferences.putString('epub.lineHeight', `${this.lineHeight}`),
      AppPreferences.putString('epub.margin', `${this.pageMargin}`),
      AppPreferences.putBoolean('epub.paginated', this.paginated),
      AppPreferences.putBoolean('epub.nightMode', this.nightMode),
      AppPreferences.putBoolean('epub.keepScreenOn', this.keepScreenOn),
      AppPreferences.putBoolean('epub.immersiveMode', this.immersiveMode),
      AppPreferences.putNumber('epub.topSpacing', this.topSpacing)
    ]);
  }
  @Builder
  private pageContent() {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 8 }) {
          Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
            .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
          Text($r('app.string.epub_reader')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        }.height(64).width('100%')
        Text(localizedString($r('app.string.epub_font_size'), this.fontSize)).width('100%')
        Slider({ value: this.fontSize, min: 14, max: 30, step: 1 })
          .onChange((value: number) => { this.fontSize = value; this.save(); })
        Text(localizedString($r('app.string.epub_line_height'), this.lineHeight.toFixed(1))).width('100%')
        Slider({ value: this.lineHeight, min: 1.2, max: 2.4, step: 0.1 })
          .onChange((value: number) => { this.lineHeight = value; this.save(); })
        Text(localizedString($r('app.string.epub_page_margin'), this.pageMargin)).width('100%')
        Slider({ value: this.pageMargin, min: 0, max: 48, step: 4 })
          .onChange((value: number) => { this.pageMargin = value; this.save(); })
        Row() {
          Text($r('app.string.epub_immersive')).fontSize(16).layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.immersiveMode })
            .onChange((value: boolean) => { this.immersiveMode = value; this.save(); })
        }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        Text($r('app.string.epub_safe_area_description')).fontSize(12).width('100%')
        Select([0, 8, 16, 24, 32, 48].map((value: number): SelectOption =>
          ({ value: localizedString($r('app.string.epub_top_spacing'), value) })))
          .selected([0, 8, 16, 24, 32, 48].indexOf(this.topSpacing)).width('100%')
          .onSelect((index: number) => { this.topSpacing = [0, 8, 16, 24, 32, 48][index]; this.save(); })
        Row() {
          Text($r('app.string.horizontal_page_turning')).fontSize(16).layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.paginated }).onChange((value: boolean) => { this.paginated = value; this.save(); })
        }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        Row() {
          Text($r('app.string.dark_reading_background')).fontSize(16).layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.nightMode })
            .onChange((value: boolean) => { this.nightMode = value; this.save(); })
        }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        Row() {
          Text($r('app.string.keep_screen_on_while_reading')).fontSize(16).layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.keepScreenOn })
            .onChange((value: boolean) => { this.keepScreenOn = value; this.save(); })
        }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%')
        .alignItems(HorizontalAlign.Start).justifyContent(FlexAlign.Start)
    }.width('100%').height('100%').align(Alignment.Top)
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
