import { router } from '@kit.ArkUI';
import { AppPreferences } from '../persistence/AppPreferences';

@Entry
@Component
struct EpubSettings {
  @State fontSize: number = 18;
  @State lineHeight: number = 1.8;
  @State margin: number = 16;
  @State paginated: boolean = true;
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.fontSize = Number(await AppPreferences.getString('epub.fontSize', '18'));
    this.lineHeight = Number(await AppPreferences.getString('epub.lineHeight', '1.8'));
    this.margin = Number(await AppPreferences.getString('epub.margin', '16'));
    this.paginated = await AppPreferences.getBoolean('epub.paginated', true);
  }
  private async save(): Promise<void> {
    await Promise.all([
      AppPreferences.putString('epub.fontSize', `${this.fontSize}`),
      AppPreferences.putString('epub.lineHeight', `${this.lineHeight}`),
      AppPreferences.putString('epub.margin', `${this.margin}`),
      AppPreferences.putBoolean('epub.paginated', this.paginated)
    ]);
  }
  build() {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 8 }) {
          Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
          Text('EPUB 阅读器').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        }.height(64).width('100%')
        Text(`字号 ${this.fontSize}`).width('100%')
        Slider({ value: this.fontSize, min: 14, max: 30, step: 1 })
          .onChange((value: number) => { this.fontSize = value; this.save(); })
        Text(`行高 ${this.lineHeight.toFixed(1)}`).width('100%')
        Slider({ value: this.lineHeight, min: 1.2, max: 2.4, step: 0.1 })
          .onChange((value: number) => { this.lineHeight = value; this.save(); })
        Text(`页边距 ${this.margin}`).width('100%')
        Slider({ value: this.margin, min: 0, max: 48, step: 4 })
          .onChange((value: number) => { this.margin = value; this.save(); })
        Row() {
          Text('分页阅读').fontSize(16).layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.paginated }).onChange((value: boolean) => { this.paginated = value; this.save(); })
        }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%')
    }.width('100%').height('100%')
  }
}
