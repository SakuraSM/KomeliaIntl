import { router } from '@kit.ArkUI';
import { AppPreferences } from '../persistence/AppPreferences';

@Entry
@Component
struct ImageReaderSettings {
  @State modeIndex: number = 0;
  @State directionIndex: number = 0;
  @State colorCorrection: boolean = false;
  @State aiEnabled: boolean = false;
  @State reducedMotion: boolean = false;
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.modeIndex = Number(await AppPreferences.getString('image.mode', '0'));
    this.directionIndex = Number(await AppPreferences.getString('image.direction', '0'));
    this.colorCorrection = await AppPreferences.getBoolean('image.colorCorrection');
    this.aiEnabled = await AppPreferences.getBoolean('image.aiEnabled');
    this.reducedMotion = await AppPreferences.getBoolean('reducedMotion');
  }
  private async save(): Promise<void> {
    await Promise.all([
      AppPreferences.putString('image.mode', `${this.modeIndex}`),
      AppPreferences.putString('image.direction', `${this.directionIndex}`),
      AppPreferences.putBoolean('image.colorCorrection', this.colorCorrection),
      AppPreferences.putBoolean('image.aiEnabled', this.aiEnabled),
      AppPreferences.putBoolean('reducedMotion', this.reducedMotion)
    ]);
  }
  @Builder
  private toggleRow(title: string, value: boolean, onChange: (value: boolean) => void) {
    Row() {
      Text(title).fontSize(16).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: value }).onChange(onChange)
    }
      .padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
  }
  build() {
    Scroll() {
      Column({ space: 14 }) {
        Row({ space: 8 }) {
          Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
          Text('图像阅读器').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        }.height(64).width('100%')
        Text('阅读模式').fontSize(15).fontWeight(FontWeight.Bold).width('100%')
        Select([{ value: '分页' }, { value: '连续' }, { value: '双页' }, { value: '面板' }])
          .selected(this.modeIndex).value(['分页', '连续', '双页', '面板'][this.modeIndex]).height(52).width('100%')
          .onSelect((index: number) => { this.modeIndex = index; this.save(); })
        Text('阅读方向').fontSize(15).fontWeight(FontWeight.Bold).width('100%')
        Select([{ value: '从左到右' }, { value: '从右到左' }, { value: '从上到下' }])
          .selected(this.directionIndex).value(['从左到右', '从右到左', '从上到下'][this.directionIndex]).height(52).width('100%')
          .onSelect((index: number) => { this.directionIndex = index; this.save(); })
        this.toggleRow('颜色校正', this.colorCorrection, (value: boolean) => { this.colorCorrection = value; this.save(); })
        this.toggleRow('MindSpore 图像增强', this.aiEnabled, (value: boolean) => { this.aiEnabled = value; this.save(); })
        this.toggleRow('减少动态效果', this.reducedMotion, (value: boolean) => { this.reducedMotion = value; this.save(); })
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%')
    }.width('100%').height('100%')
  }
}
