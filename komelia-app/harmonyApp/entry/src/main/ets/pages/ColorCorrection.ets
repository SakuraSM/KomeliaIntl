import { KomeliaColors } from '../core/design/KomeliaDesign';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { bookColorCorrectionRepository } from '../data/repository/DefaultRepositories';
import { imageColorProcessor } from '../domain/service/ImageColorProcessor';
import { readerEngine } from '../domain/service/ReaderEngine';
import { ReaderRequestToken } from '../domain/service/ReaderRequestToken';
import {
  BookColorCorrectionSettings,
  ColorChannel,
  ColorCorrectionType,
  ColorLevelsConfig,
  CurvePoint,
  CurvePointType,
  colorCorrectionIsActive,
  defaultBookColorCorrection,
  defaultCurvePoints,
  defaultLevelsConfig,
  normalizeBookColorCorrection
} from '../model/ColorCorrectionModels';

interface ColorCorrectionParams {
  namespace: string;
  bookId: string;
  page: number;
}

@Component
struct ColorCorrectionPreview {
  @Prop namespace: string;
  @Prop bookId: string;
  @Prop page: number;
  @Prop settingsJson: string;
  @Prop revision: number;
  @State source: PixelMap | Resource = $r('app.media.app_icon');
  @State loading: boolean = true;
  @State failed: boolean = false;
  private pixelMap?: PixelMap;
  private token: ReaderRequestToken = new ReaderRequestToken();

  aboutToAppear(): void {
    this.load();
  }

  aboutToDisappear(): void {
    this.token.cancel();
    this.pixelMap?.release();
    this.pixelMap = undefined;
  }

  private async load(): Promise<void> {
    this.token.cancel();
    const token = new ReaderRequestToken();
    this.token = token;
    this.loading = true;
    this.failed = false;
    try {
      const pixelMap = await readerEngine.pageSource(this.namespace, this.bookId, this.page, token, true);
      const settings = normalizeBookColorCorrection(
        JSON.parse(this.settingsJson) as BookColorCorrectionSettings);
      if (colorCorrectionIsActive(settings)) await imageColorProcessor.apply(pixelMap, settings, token);
      if (token.isCancelled()) {
        pixelMap.release();
        return;
      }
      this.pixelMap?.release();
      this.pixelMap = pixelMap;
      this.source = pixelMap;
    } catch (_) {
      if (!token.isCancelled()) this.failed = true;
    } finally {
      if (!token.isCancelled()) this.loading = false;
    }
  }

  build() {
    Stack() {
      Image(this.source).width('100%').height('100%').objectFit(ImageFit.Contain)
        .opacity(this.loading || this.failed ? 0 : 1).interpolation(ImageInterpolation.High)
      if (this.loading) {
        LoadingProgress().width(34).height(34).color($r('app.color.primary'))
      } else if (this.failed) {
        Button($r('app.string.retry')).height(44).onClick(() => this.load())
      }
    }
    .width('100%').height(360).backgroundColor('#FF17171C').borderRadius(16).clip(true)
  }
}

@Entry
@Component
struct ColorCorrection {
  @State settingsJson: string = JSON.stringify(defaultBookColorCorrection());
  @State selectedChannel: ColorChannel = ColorChannel.VALUE;
  @State selectedPoint: number = 0;
  @State previewRevision: number = 0;
  @State loading: boolean = true;
  @State saving: boolean = false;
  @State errorMessage: string = '';
  private namespace: string = '';
  private bookId: string = '';
  private page: number = 0;

  aboutToAppear(): void {
    const params = router.getParams() as ColorCorrectionParams | undefined;
    this.namespace = requiredRouteId(params?.namespace);
    this.bookId = requiredRouteId(params?.bookId);
    this.page = Math.max(0, params?.page || 0);
    if (this.bookId.length > 0) this.restore();
  }

  private async restore(): Promise<void> {
    try {
      const saved = await bookColorCorrectionRepository.get(this.namespace, this.bookId);
      this.settingsJson = JSON.stringify(saved || defaultBookColorCorrection());
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
      this.previewRevision += 1;
    }
  }

  private settings(): BookColorCorrectionSettings {
    try {
      return normalizeBookColorCorrection(JSON.parse(this.settingsJson) as BookColorCorrectionSettings);
    } catch (_) {
      return defaultBookColorCorrection();
    }
  }

  private replace(value: BookColorCorrectionSettings, preview: boolean = true): void {
    this.settingsJson = JSON.stringify(normalizeBookColorCorrection(value));
    if (preview) this.previewRevision += 1;
  }

  private setType(type: ColorCorrectionType): void {
    const settings = this.settings();
    settings.type = type;
    this.replace(settings);
  }

  private curves(): CurvePoint[] {
    const settings = this.settings();
    if (this.selectedChannel === ColorChannel.RED) return settings.curves.red;
    if (this.selectedChannel === ColorChannel.GREEN) return settings.curves.green;
    if (this.selectedChannel === ColorChannel.BLUE) return settings.curves.blue;
    return settings.curves.color;
  }

  private setCurves(points: CurvePoint[], preview: boolean = true): void {
    const settings = this.settings();
    if (this.selectedChannel === ColorChannel.RED) settings.curves.red = points;
    else if (this.selectedChannel === ColorChannel.GREEN) settings.curves.green = points;
    else if (this.selectedChannel === ColorChannel.BLUE) settings.curves.blue = points;
    else settings.curves.color = points;
    this.replace(settings, preview);
  }

  private levels(): ColorLevelsConfig {
    const settings = this.settings();
    if (this.selectedChannel === ColorChannel.RED) return settings.levels.red;
    if (this.selectedChannel === ColorChannel.GREEN) return settings.levels.green;
    if (this.selectedChannel === ColorChannel.BLUE) return settings.levels.blue;
    return settings.levels.color;
  }

  private setLevels(levels: ColorLevelsConfig, preview: boolean = true): void {
    const settings = this.settings();
    if (this.selectedChannel === ColorChannel.RED) settings.levels.red = levels;
    else if (this.selectedChannel === ColorChannel.GREEN) settings.levels.green = levels;
    else if (this.selectedChannel === ColorChannel.BLUE) settings.levels.blue = levels;
    else settings.levels.color = levels;
    this.replace(settings, preview);
  }

  private selectChannel(channel: ColorChannel): void {
    this.selectedChannel = channel;
    this.selectedPoint = 0;
  }

  private addCurvePoint(): void {
    const points = this.curves().slice();
    points.push({ x: 0.5, y: 0.5, type: CurvePointType.SMOOTH });
    points.sort((first: CurvePoint, second: CurvePoint) => first.x - second.x);
    this.selectedPoint = points.findIndex((point: CurvePoint) => point.x === 0.5 && point.y === 0.5);
    this.setCurves(points);
  }

  private deleteCurvePoint(): void {
    const points = this.curves().slice();
    if (this.selectedPoint <= 0 || this.selectedPoint >= points.length - 1) return;
    points.splice(this.selectedPoint, 1);
    this.selectedPoint = Math.max(0, Math.min(this.selectedPoint - 1, points.length - 1));
    this.setCurves(points);
  }

  private updateCurvePoint(x: number, y: number, type: CurvePointType, preview: boolean): void {
    const points = this.curves().slice();
    if (this.selectedPoint < 0 || this.selectedPoint >= points.length) return;
    const minimum = this.selectedPoint === 0 ? 0 : points[this.selectedPoint - 1].x + 0.004;
    const maximum = this.selectedPoint === points.length - 1 ? 1 : points[this.selectedPoint + 1].x - 0.004;
    points[this.selectedPoint] = {
      x: Math.max(minimum, Math.min(maximum, x)),
      y: Math.max(0, Math.min(1, y)),
      type: type
    };
    this.setCurves(points, preview);
  }

  private resetCurrentChannel(): void {
    const settings = this.settings();
    if (settings.type === ColorCorrectionType.COLOR_CURVES) this.setCurves(defaultCurvePoints());
    else this.setLevels(defaultLevelsConfig());
    this.selectedPoint = 0;
  }

  private resetAll(): void {
    const type = this.settings().type;
    const settings = defaultBookColorCorrection();
    settings.type = type;
    this.selectedPoint = 0;
    this.replace(settings);
  }

  private async saveAndExit(): Promise<void> {
    if (this.saving) return;
    this.saving = true;
    try {
      const settings = this.settings();
      if (colorCorrectionIsActive(settings)) {
        await bookColorCorrectionRepository.save(this.namespace, this.bookId, settings);
      } else {
        await bookColorCorrectionRepository.delete(this.namespace, this.bookId);
      }
      router.back();
    } catch (error) {
      this.errorMessage = (error as Error).message;
      this.saving = false;
    }
  }

  @Builder
  private option(title: ResourceStr, selected: boolean, onClick: () => void) {
    Button(title).height(44).margin({ right: 8, bottom: 8 }).borderRadius(14)
      .fontColor(selected ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(selected ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .onClick(onClick)
  }

  @Builder
  private channels() {
    Column({ space: 8 }) {
      Text($r('app.string.color_correction_channel')).fontSize(14).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Flex({ wrap: FlexWrap.Wrap }) {
        this.option($r('app.string.color_channel_value'), this.selectedChannel === ColorChannel.VALUE,
          () => this.selectChannel(ColorChannel.VALUE))
        this.option($r('app.string.color_channel_red'), this.selectedChannel === ColorChannel.RED,
          () => this.selectChannel(ColorChannel.RED))
        this.option($r('app.string.color_channel_green'), this.selectedChannel === ColorChannel.GREEN,
          () => this.selectChannel(ColorChannel.GREEN))
        this.option($r('app.string.color_channel_blue'), this.selectedChannel === ColorChannel.BLUE,
          () => this.selectChannel(ColorChannel.BLUE))
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private sliderRow(title: ResourceStr, value: number, minimum: number, maximum: number, step: number,
    display: string, onChange: (value: number, preview: boolean) => void) {
    Column({ space: 4 }) {
      Row() {
        Text(title).fontSize(14).fontColor($r('app.color.on_surface')).layoutWeight(1)
        Text(display).fontSize(13).fontColor($r('app.color.on_surface_variant'))
      }.width('100%')
      Slider({ value: value, min: minimum, max: maximum, step: step })
        .selectedColor($r('app.color.primary'))
        .onChange((next: number, mode: SliderChangeMode) => onChange(next, mode === SliderChangeMode.End))
    }.width('100%')
  }

  @Builder
  private curvesEditor() {
    Column({ space: 14 }) {
      this.channels()
      Text($r('app.string.color_curve_point')).fontSize(14).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Flex({ wrap: FlexWrap.Wrap }) {
        ForEach(this.curves(), (_point: CurvePoint, index: number) => {
          this.option(`P${index + 1}`, this.selectedPoint === index, () => this.selectedPoint = index)
        }, (_point: CurvePoint, index: number) => `${index}`)
        this.option($r('app.string.color_curve_add_point'), false, () => this.addCurvePoint())
      }.width('100%')
      if (this.curves().length > 0) {
        this.sliderRow($r('app.string.color_curve_input'), this.curves()[this.selectedPoint].x * 255,
          0, 255, 1, `${Math.round(this.curves()[this.selectedPoint].x * 255)}`,
          (value: number, preview: boolean) => {
            const point = this.curves()[this.selectedPoint];
            this.updateCurvePoint(value / 255, point.y, point.type, preview);
          })
        this.sliderRow($r('app.string.color_curve_output'), this.curves()[this.selectedPoint].y * 255,
          0, 255, 1, `${Math.round(this.curves()[this.selectedPoint].y * 255)}`,
          (value: number, preview: boolean) => {
            const point = this.curves()[this.selectedPoint];
            this.updateCurvePoint(point.x, value / 255, point.type, preview);
          })
        Flex({ wrap: FlexWrap.Wrap }) {
          this.option($r('app.string.color_curve_smooth'),
            this.curves()[this.selectedPoint].type === CurvePointType.SMOOTH, () => {
              const point = this.curves()[this.selectedPoint];
              this.updateCurvePoint(point.x, point.y, CurvePointType.SMOOTH, true);
            })
          this.option($r('app.string.color_curve_corner'),
            this.curves()[this.selectedPoint].type === CurvePointType.CORNER, () => {
              const point = this.curves()[this.selectedPoint];
              this.updateCurvePoint(point.x, point.y, CurvePointType.CORNER, true);
            })
          if (this.selectedPoint > 0 && this.selectedPoint < this.curves().length - 1) {
            this.option($r('app.string.color_curve_delete_point'), false, () => this.deleteCurvePoint())
          }
        }.width('100%')
      }
      Row({ space: 8 }) {
        Button($r('app.string.color_reset_channel')).height(44).layoutWeight(1)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.resetCurrentChannel())
        Button($r('app.string.color_reset_all')).height(44).layoutWeight(1)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.resetAll())
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private levelsEditor() {
    Column({ space: 14 }) {
      this.channels()
      this.sliderRow($r('app.string.color_levels_black'), this.levels().lowInput * 255,
        0, 255, 1, `${Math.round(this.levels().lowInput * 255)}`,
        (value: number, preview: boolean) => {
          const levels = this.levels(); levels.lowInput = value / 255; this.setLevels(levels, preview);
        })
      this.sliderRow($r('app.string.color_levels_gamma'), this.levels().gamma,
        0.1, 10, 0.05, this.levels().gamma.toFixed(2),
        (value: number, preview: boolean) => {
          const levels = this.levels(); levels.gamma = value; this.setLevels(levels, preview);
        })
      this.sliderRow($r('app.string.color_levels_white'), this.levels().highInput * 255,
        0, 255, 1, `${Math.round(this.levels().highInput * 255)}`,
        (value: number, preview: boolean) => {
          const levels = this.levels(); levels.highInput = value / 255; this.setLevels(levels, preview);
        })
      Text($r('app.string.color_levels_output')).fontSize(14).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      this.sliderRow($r('app.string.color_levels_black'), this.levels().lowOutput * 255,
        0, 255, 1, `${Math.round(this.levels().lowOutput * 255)}`,
        (value: number, preview: boolean) => {
          const levels = this.levels(); levels.lowOutput = value / 255; this.setLevels(levels, preview);
        })
      this.sliderRow($r('app.string.color_levels_white'), this.levels().highOutput * 255,
        0, 255, 1, `${Math.round(this.levels().highOutput * 255)}`,
        (value: number, preview: boolean) => {
          const levels = this.levels(); levels.highOutput = value / 255; this.setLevels(levels, preview);
        })
      Row({ space: 8 }) {
        Button($r('app.string.color_reset_channel')).height(44).layoutWeight(1)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.resetCurrentChannel())
        Button($r('app.string.color_reset_all')).height(44).layoutWeight(1)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.resetAll())
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => this.saveAndExit())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.color_correction')).fontSize(22).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface')).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.done')).height(44).borderRadius(14)
          .enabled(!this.saving).backgroundColor($r('app.color.primary_container'))
          .fontColor($r('app.color.primary')).onClick(() => this.saveAndExit())
      }.height(64).width('100%')

      if (this.loading) {
        LoadingProgress().width(38).height(38).color($r('app.color.primary')).layoutWeight(1)
      } else {
        Scroll() {
          Column({ space: 16 }) {
            Text($r('app.string.color_correction_preview')).fontSize(14).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            ForEach([this.previewRevision], (_revision: number) => {
              ColorCorrectionPreview({ namespace: this.namespace, bookId: this.bookId, page: this.page,
                settingsJson: this.settingsJson, revision: this.previewRevision })
            }, (revision: number) => `${revision}`)

            Row({ space: 8 }) {
              Button($r('app.string.color_correction_curves')).height(44).layoutWeight(1).borderRadius(14)
                .fontColor(this.settings().type === ColorCorrectionType.COLOR_CURVES ?
                  $r('app.color.primary') : $r('app.color.on_surface_variant'))
                .backgroundColor(this.settings().type === ColorCorrectionType.COLOR_CURVES ?
                  $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
                .onClick(() => this.setType(ColorCorrectionType.COLOR_CURVES))
              Button($r('app.string.color_correction_levels')).height(44).layoutWeight(1).borderRadius(14)
                .fontColor(this.settings().type === ColorCorrectionType.COLOR_LEVELS ?
                  $r('app.color.primary') : $r('app.color.on_surface_variant'))
                .backgroundColor(this.settings().type === ColorCorrectionType.COLOR_LEVELS ?
                  $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
                .onClick(() => this.setType(ColorCorrectionType.COLOR_LEVELS))
            }.width('100%')

            Column() {
              if (this.settings().type === ColorCorrectionType.COLOR_CURVES) this.curvesEditor()
              else this.levelsEditor()
            }
            .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

            if (this.errorMessage.length > 0) {
              Text(this.errorMessage).fontSize(13).fontColor($r('app.color.error')).width('100%')
            }
          }.padding({ bottom: 32 }).width('100%').constraintSize({ maxWidth: 920 })
        }.layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }
    .padding({ left: 12, right: 12 }).width('100%').height('100%').align(Alignment.Top)
    .justifyContent(FlexAlign.Start)
    .backgroundColor(KomeliaColors.surface())
  }
}
