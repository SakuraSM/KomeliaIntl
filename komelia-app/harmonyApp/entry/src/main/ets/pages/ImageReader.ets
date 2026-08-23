import { router } from '@kit.ArkUI';
import { namespaceOf, OfflinePageRecord } from '../data/local/DatabaseModels';
import { offlineRepository } from '../data/repository/DefaultRepositories';
import { readerEngine } from '../domain/service/ReaderEngine';
import { KomgaBookPage } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppState } from '../state/AppState';

interface ReaderParams {
  bookId: string;
  initialPage: number;
}

@Component
struct RemotePage {
  @Prop namespace: string;
  @Prop bookId: string;
  @Prop page: number;
  @Prop colorCorrection: boolean = false;
  @State source: PixelMap | Resource = $r('app.media.app_icon');
  @State loading: boolean = true;

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      this.source = await readerEngine.pageSource(this.namespace, this.bookId, this.page);
    } finally {
      this.loading = false;
    }
  }

  build() {
    Stack() {
      Image(this.source)
        .width('100%').height('100%').objectFit(ImageFit.Contain)
        .colorFilter(this.colorCorrection ? [
          1.04, 0, 0, 0, -4,
          0, 1.04, 0, 0, -4,
          0, 0, 1.04, 0, -4,
          0, 0, 0, 1, 0
        ] : [
          1, 0, 0, 0, 0,
          0, 1, 0, 0, 0,
          0, 0, 1, 0, 0,
          0, 0, 0, 1, 0
        ])
        .interpolation(ImageInterpolation.High).draggable(false)
      if (this.loading) {
        LoadingProgress().width(36).height(36).color('#FFFFFF')
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black)
  }
}

@Entry
@Component
struct ImageReader {
  @State pages: KomgaBookPage[] = [];
  @State currentPage: number = 0;
  @State chromeVisible: boolean = true;
  @State isLoading: boolean = true;
  @State modeIndex: number = 0;
  @State directionIndex: number = 0;
  @State colorCorrection: boolean = false;
  @State reducedMotion: boolean = false;
  private bookId: string = '';
  private namespace: string = '';
  private swiperController: SwiperController = new SwiperController();
  private hideTimer: number = -1;

  aboutToAppear(): void {
    const params = router.getParams() as ReaderParams;
    this.bookId = params.bookId;
    const key = AppState.getInstance().namespace;
    this.namespace = key === undefined ? '' : namespaceOf(key);
    this.currentPage = Math.max(0, params.initialPage || 0);
    this.restoreAndLoad();
    this.scheduleHide();
  }

  private async restoreAndLoad(): Promise<void> {
    this.modeIndex = Number(await AppPreferences.getString('image.mode', '0'));
    this.directionIndex = Number(await AppPreferences.getString('image.direction', '0'));
    this.colorCorrection = await AppPreferences.getBoolean('image.colorCorrection');
    this.reducedMotion = await AppPreferences.getBoolean('reducedMotion');
    await this.load();
  }

  aboutToDisappear(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
  }

  private async load(): Promise<void> {
    try {
      try {
        this.pages = await komgaClient.getBookPages(this.bookId);
      } catch (_) {
        const localPages = await offlineRepository.listPages(this.namespace, this.bookId);
        this.pages = localPages.map((page: OfflinePageRecord) => ({
          number: page.pageNumber,
          fileName: page.localPath,
          mediaType: page.mediaType,
          sizeBytes: page.sizeBytes
        }));
      }
    } finally {
      this.isLoading = false;
    }
  }

  private scheduleHide(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    this.hideTimer = setTimeout(() => this.chromeVisible = false, 2600);
  }

  private toggleChrome(): void {
    this.chromeVisible = !this.chromeVisible;
    if (this.chromeVisible) this.scheduleHide();
  }

  private orderedPages(): KomgaBookPage[] {
    return this.directionIndex === 1 ? this.pages.slice().reverse() : this.pages;
  }

  private pageAt(index: number): number {
    const ordered = this.orderedPages();
    return ordered.length === 0 ? 0 : ordered[Math.max(0, Math.min(index, ordered.length - 1))].number;
  }

  private updatePage(index: number): void {
    this.currentPage = index;
    const page = this.pageAt(index);
    readerEngine.updateProgress(this.namespace, this.bookId, page, page === this.pages.length - 1);
  }

  @Builder
  private paginatedReader() {
    Swiper(this.swiperController) {
      ForEach(this.orderedPages(), (page: KomgaBookPage) => {
        RemotePage({ namespace: this.namespace, bookId: this.bookId, page: page.number,
          colorCorrection: this.colorCorrection })
      }, (page: KomgaBookPage) => `${page.number}`)
    }
    .index(this.currentPage).indicator(false).loop(false).width('100%').height('100%')
    .vertical(this.directionIndex === 2).displayCount(this.modeIndex === 2 ? 2 : 1)
    .duration(this.reducedMotion ? 0 : 180)
    .onChange((index: number) => this.updatePage(index))
    .onClick(() => this.toggleChrome())
  }

  @Builder
  private continuousReader() {
    List({ space: 0, initialIndex: this.currentPage }) {
      ForEach(this.orderedPages(), (page: KomgaBookPage) => {
        ListItem() {
          RemotePage({ namespace: this.namespace, bookId: this.bookId, page: page.number,
            colorCorrection: this.colorCorrection })
        }.width('100%').height('100%')
      }, (page: KomgaBookPage) => `${page.number}`)
    }
    .width('100%').height('100%').edgeEffect(EdgeEffect.None)
    .onScrollIndex((start: number, _end: number, center: number) => this.updatePage(center < 0 ? start : center))
    .onClick(() => this.toggleChrome())
  }

  build() {
    Stack() {
      if (this.isLoading) {
        LoadingProgress().width(42).height(42).color(Color.White)
      } else {
        if (this.modeIndex === 1) {
          this.continuousReader()
        } else {
          this.paginatedReader()
        }
      }

      if (this.chromeVisible) {
        Column() {
          Row() {
            Button({ type: ButtonType.Circle }) {
              Text('‹').fontSize(34).fontColor(Color.White)
            }
            .width(44).height(44).backgroundColor('#66121217').onClick(() => router.back())
            Blank()
            Text(`${this.pageAt(this.currentPage) + 1} / ${this.pages.length}`)
              .fontSize(14).fontColor(Color.White).backgroundColor('#66121217')
              .padding({ left: 12, right: 12, top: 8, bottom: 8 }).borderRadius(16)
          }.width('100%').padding({ left: 12, right: 12, top: 12 })
          Blank()
          Row({ space: 12 }) {
            Text('‹').fontSize(32).fontColor(Color.White)
              .onClick(() => this.swiperController.showPrevious())
            Slider({ value: this.currentPage, min: 0, max: Math.max(0, this.pages.length - 1), step: 1 })
              .layoutWeight(1).selectedColor($r('app.color.primary'))
              .onChange((value: number, mode: SliderChangeMode) => {
                if (mode === SliderChangeMode.End && this.modeIndex !== 1) {
                  this.swiperController.changeIndex(Math.round(value));
                }
              })
            Text('›').fontSize(32).fontColor(Color.White)
              .onClick(() => this.swiperController.showNext())
          }
          .height(64).width('calc(100% - 24vp)').padding({ left: 16, right: 16 })
          .margin({ bottom: 14 }).backgroundColor('#E6181824').borderRadius(22)
          if (this.modeIndex === 3) {
            Text('面板检测模型未安装，当前使用分页回退')
              .fontSize(12).fontColor(Color.White).padding({ left: 12, right: 12, top: 6, bottom: 6 })
              .margin({ bottom: 10 }).backgroundColor('#B0181824').borderRadius(14)
          }
        }
        .width('100%').height('100%').hitTestBehavior(HitTestMode.Transparent)
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
