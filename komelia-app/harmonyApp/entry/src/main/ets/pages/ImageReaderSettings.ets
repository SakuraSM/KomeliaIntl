import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { mindSporePanelDetector } from '../domain/service/MindSporePanelDetector';
import { mindSporeSuperResolution } from '../domain/service/MindSporeSuperResolution';
import {
  ContinuousReadingDirection,
  ImageReaderPreferences,
  ImageReaderSettingsValue,
  ImageReaderType,
  LayoutScaleType,
  PageDisplayLayout,
  PagedReadingDirection,
  ReaderFlashColor
} from '../model/ImageReaderModels';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppState } from '../state/AppState';

@Entry
@Component
struct ImageReaderSettings {
  @State readerType: ImageReaderType = ImageReaderType.PAGED;
  @State pagedDirection: PagedReadingDirection = PagedReadingDirection.LEFT_TO_RIGHT;
  @State continuousDirection: ContinuousReadingDirection = ContinuousReadingDirection.TOP_TO_BOTTOM;
  @State pageLayout: PageDisplayLayout = PageDisplayLayout.SINGLE_PAGE;
  @State scaleType: LayoutScaleType = LayoutScaleType.SCREEN;
  @State layoutOffset: boolean = false;
  @State sidePadding: number = 0;
  @State pageSpacing: number = 0;
  @State stretchSmallImages: boolean = true;
  @State cropBorders: boolean = false;
  @State colorCorrection: boolean = false;
  @State aiEnabled: boolean = false;
  @State flashEnabled: boolean = false;
  @State flashDuration: number = 100;
  @State flashEveryNPages: number = 1;
  @State flashColor: ReaderFlashColor = ReaderFlashColor.BLACK;
  @State reducedMotion: boolean = false;
  @State panelModelAvailable: boolean = false;
  @State aiModelAvailable: boolean = false;

  aboutToAppear(): void {
    this.restore();
  }

  private async restore(): Promise<void> {
    const value = await ImageReaderPreferences.load();
    const modelAvailability = await Promise.all([
      mindSporePanelDetector.isAvailable(),
      mindSporeSuperResolution.isAvailable()
    ]);
    this.panelModelAvailable = modelAvailability[0];
    this.aiModelAvailable = modelAvailability[1];
    this.apply(value);
    this.reducedMotion = await AppPreferences.getBoolean('reducedMotion');
  }

  private apply(value: ImageReaderSettingsValue): void {
    this.readerType = value.readerType;
    this.pagedDirection = value.pagedDirection;
    this.continuousDirection = value.continuousDirection;
    this.pageLayout = value.pageLayout;
    this.scaleType = value.scaleType;
    this.layoutOffset = value.layoutOffset;
    this.sidePadding = value.sidePadding;
    this.pageSpacing = value.pageSpacing;
    this.stretchSmallImages = value.stretchSmallImages;
    this.cropBorders = value.cropBorders;
    this.colorCorrection = value.colorCorrection;
    this.aiEnabled = value.aiEnabled && this.aiModelAvailable;
    this.flashEnabled = value.flashEnabled;
    this.flashDuration = value.flashDuration;
    this.flashEveryNPages = value.flashEveryNPages;
    this.flashColor = value.flashColor;
  }

  private currentValue(): ImageReaderSettingsValue {
    return {
      readerType: this.readerType,
      pagedDirection: this.pagedDirection,
      continuousDirection: this.continuousDirection,
      pageLayout: this.pageLayout,
      scaleType: this.scaleType,
      layoutOffset: this.layoutOffset,
      sidePadding: this.sidePadding,
      pageSpacing: this.pageSpacing,
      stretchSmallImages: this.stretchSmallImages,
      cropBorders: this.cropBorders,
      colorCorrection: this.colorCorrection,
      aiEnabled: this.aiEnabled,
      flashEnabled: this.flashEnabled,
      flashDuration: this.flashDuration,
      flashEveryNPages: this.flashEveryNPages,
      flashColor: this.flashColor
    };
  }

  private async save(): Promise<void> {
    await Promise.all([
      ImageReaderPreferences.save(this.currentValue()),
      AppPreferences.putBoolean('reducedMotion', this.reducedMotion)
    ]);
    AppState.getInstance().reducedMotion = this.reducedMotion;
  }

  @Builder
  private toggleRow(title: ResourceStr, value: () => boolean, onChange: (value: boolean) => void,
    enabled: boolean = true) {
    Row() {
      Text(title).fontSize(16).fontColor($r('app.color.on_surface')).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: value() }).enabled(enabled).onChange(onChange)
    }
    .constraintSize({ minHeight: 52 }).padding({ left: 14, right: 14 }).width('100%')
    .opacity(enabled ? 1 : 0.45)
  }

  @Builder
  private optionButton(title: ResourceStr, selected: () => boolean, onSelect: () => void,
    enabled: boolean = true) {
    Button(title)
      .height(44)
      .margin({ right: 8, bottom: 8 })
      .borderRadius(14)
      .fontColor(selected() ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(selected() ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .enabled(enabled)
      .opacity(enabled ? 1 : 0.45)
      .onClick(onSelect)
  }

  @Builder
  private sectionLabel(title: ResourceStr) {
    Text(title).fontSize(15).fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface_variant')).width('100%')
  }

  @Builder
  private pageContent() {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 8 }) {
          Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
            .backgroundColor(Color.Transparent).onClick(() => router.back())
            .accessibilityText($r('app.string.back'))
          Text($r('app.string.image_reader')).fontSize(22).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface')).layoutWeight(1)
        }.height(64).width('100%')

        Column({ space: 10 }) {
          this.sectionLabel($r('app.string.reader_type'))
          Flex({ wrap: FlexWrap.Wrap }) {
            this.optionButton($r('app.string.paginated'), () => this.readerType === ImageReaderType.PAGED,
              () => { this.readerType = ImageReaderType.PAGED; this.save(); })
            this.optionButton($r('app.string.continuous'), () => this.readerType === ImageReaderType.CONTINUOUS,
              () => { this.readerType = ImageReaderType.CONTINUOUS; this.save(); })
            if (this.panelModelAvailable) {
              this.optionButton($r('app.string.panel_mode'), () => this.readerType === ImageReaderType.PANELS,
                () => { this.readerType = ImageReaderType.PANELS; this.save(); })
            } else {
              this.optionButton($r('app.string.panel_mode'), () => false, () => {}, false)
            }
          }.width('100%')
          if (!this.panelModelAvailable) {
            Text($r('app.string.panel_model_required')).fontSize(12)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
          }

          if (this.readerType !== ImageReaderType.CONTINUOUS) {
            if (this.readerType === ImageReaderType.PAGED) {
              this.sectionLabel($r('app.string.page_scale'))
              Flex({ wrap: FlexWrap.Wrap }) {
                this.optionButton($r('app.string.fit_screen'), () => this.scaleType === LayoutScaleType.SCREEN,
                  () => { this.scaleType = LayoutScaleType.SCREEN; this.save(); })
                this.optionButton($r('app.string.fit_width'), () => this.scaleType === LayoutScaleType.FIT_WIDTH,
                  () => { this.scaleType = LayoutScaleType.FIT_WIDTH; this.save(); })
                this.optionButton($r('app.string.fit_height'), () => this.scaleType === LayoutScaleType.FIT_HEIGHT,
                  () => { this.scaleType = LayoutScaleType.FIT_HEIGHT; this.save(); })
                this.optionButton($r('app.string.original_size'), () => this.scaleType === LayoutScaleType.ORIGINAL,
                  () => { this.scaleType = LayoutScaleType.ORIGINAL; this.save(); })
              }.width('100%')
            }

            this.sectionLabel($r('app.string.reading_direction'))
            Flex({ wrap: FlexWrap.Wrap }) {
              this.optionButton($r('app.string.right_to_left'),
                () => this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT,
                () => { this.pagedDirection = PagedReadingDirection.RIGHT_TO_LEFT; this.save(); })
              this.optionButton($r('app.string.left_to_right'),
                () => this.pagedDirection === PagedReadingDirection.LEFT_TO_RIGHT,
                () => { this.pagedDirection = PagedReadingDirection.LEFT_TO_RIGHT; this.save(); })
            }.width('100%')

            if (this.readerType === ImageReaderType.PAGED) {
              this.sectionLabel($r('app.string.page_layout'))
              Flex({ wrap: FlexWrap.Wrap }) {
                this.optionButton($r('app.string.single_page'), () => this.pageLayout === PageDisplayLayout.SINGLE_PAGE,
                  () => { this.pageLayout = PageDisplayLayout.SINGLE_PAGE; this.save(); })
                this.optionButton($r('app.string.double_page'), () => this.pageLayout === PageDisplayLayout.DOUBLE_PAGES,
                  () => { this.pageLayout = PageDisplayLayout.DOUBLE_PAGES; this.save(); })
                this.optionButton($r('app.string.double_pages_no_cover'),
                  () => this.pageLayout === PageDisplayLayout.DOUBLE_PAGES_NO_COVER,
                  () => { this.pageLayout = PageDisplayLayout.DOUBLE_PAGES_NO_COVER; this.save(); })
              }.width('100%')
              if (this.pageLayout !== PageDisplayLayout.SINGLE_PAGE) {
                this.toggleRow($r('app.string.offset_pages'), () => this.layoutOffset,
                  (value: boolean) => { this.layoutOffset = value; this.save(); })
              }
            }
          } else if (this.readerType === ImageReaderType.CONTINUOUS) {
            this.sectionLabel($r('app.string.reading_direction'))
            Flex({ wrap: FlexWrap.Wrap }) {
              this.optionButton($r('app.string.top_to_bottom'),
                () => this.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM,
                () => { this.continuousDirection = ContinuousReadingDirection.TOP_TO_BOTTOM; this.save(); })
              this.optionButton($r('app.string.left_to_right'),
                () => this.continuousDirection === ContinuousReadingDirection.LEFT_TO_RIGHT,
                () => { this.continuousDirection = ContinuousReadingDirection.LEFT_TO_RIGHT; this.save(); })
              this.optionButton($r('app.string.right_to_left'),
                () => this.continuousDirection === ContinuousReadingDirection.RIGHT_TO_LEFT,
                () => { this.continuousDirection = ContinuousReadingDirection.RIGHT_TO_LEFT; this.save(); })
            }.width('100%')
            Column({ space: 4 }) {
              Text(`${localizedString($r('app.string.continuous_side_padding'))} ` +
                `${Math.round(this.sidePadding * 200)}%`).fontSize(14).width('100%')
              Slider({ value: this.sidePadding, min: 0, max: 0.4, step: 0.025 })
                .selectedColor($r('app.color.primary')).onChange((value: number) => {
                  this.sidePadding = value;
                  this.save();
                })
              Text(`${localizedString($r('app.string.continuous_page_spacing'))} ` +
                `${Math.round(this.pageSpacing)}vp`).fontSize(14).width('100%')
              Slider({ value: this.pageSpacing, min: 0, max: 250, step: 10 })
                .selectedColor($r('app.color.primary')).onChange((value: number) => {
                  this.pageSpacing = value;
                  this.save();
                })
            }.width('100%')
          }
        }
        .padding(16).width('100%').alignItems(HorizontalAlign.Start)
        .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })

        Column() {
          this.sectionLabel($r('app.string.image_settings'))
          this.toggleRow($r('app.string.stretch_small_images'), () => this.stretchSmallImages,
            (value: boolean) => { this.stretchSmallImages = value; this.save(); })
          this.toggleRow($r('app.string.crop_borders'), () => this.cropBorders,
            (value: boolean) => { this.cropBorders = value; this.save(); })
          this.toggleRow($r('app.string.color_correction'), () => this.colorCorrection,
            (value: boolean) => { this.colorCorrection = value; this.save(); })
          this.toggleRow($r('app.string.mindspore_enhancement'), () => this.aiEnabled,
            (value: boolean) => { this.aiEnabled = value; this.save(); }, this.aiModelAvailable)
          if (!this.aiModelAvailable) {
            Text($r('app.string.mindspore_model_unavailable')).fontSize(12)
              .fontColor($r('app.color.on_surface_variant'))
              .padding({ left: 14, right: 14, bottom: 8 }).width('100%')
          }
          this.toggleRow($r('app.string.flash_on_page_change'), () => this.flashEnabled,
            (value: boolean) => { this.flashEnabled = value; this.save(); })
          if (this.flashEnabled) {
            Column({ space: 4 }) {
              Text(localizedString($r('app.string.flash_duration'), Math.round(this.flashDuration)))
                .fontSize(14).width('100%')
              Slider({ value: this.flashDuration, min: 100, max: 1500, step: 100 })
                .selectedColor($r('app.color.primary')).onChange((value: number) => {
                  this.flashDuration = value;
                  this.save();
                })
              Text(localizedString($r('app.string.flash_every_n_pages'), Math.round(this.flashEveryNPages)))
                .fontSize(14).width('100%')
              Slider({ value: this.flashEveryNPages, min: 1, max: 10, step: 1 })
                .selectedColor($r('app.color.primary')).onChange((value: number) => {
                  this.flashEveryNPages = value;
                  this.save();
                })
              this.sectionLabel($r('app.string.flash_color'))
              Flex({ wrap: FlexWrap.Wrap }) {
                this.optionButton($r('app.string.flash_black'), () => this.flashColor === ReaderFlashColor.BLACK,
                  () => { this.flashColor = ReaderFlashColor.BLACK; this.save(); })
                this.optionButton($r('app.string.flash_white'), () => this.flashColor === ReaderFlashColor.WHITE,
                  () => { this.flashColor = ReaderFlashColor.WHITE; this.save(); })
                this.optionButton($r('app.string.flash_alternate'),
                  () => this.flashColor === ReaderFlashColor.WHITE_AND_BLACK,
                  () => { this.flashColor = ReaderFlashColor.WHITE_AND_BLACK; this.save(); })
              }.width('100%')
            }.padding({ left: 14, right: 14, bottom: 12 }).width('100%')
          }
          this.toggleRow($r('app.string.reduce_motion'), () => this.reducedMotion,
            (value: boolean) => { this.reducedMotion = value; this.save(); })
        }
        .padding({ top: 16, bottom: 4 }).width('100%').alignItems(HorizontalAlign.Start)
        .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })
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
