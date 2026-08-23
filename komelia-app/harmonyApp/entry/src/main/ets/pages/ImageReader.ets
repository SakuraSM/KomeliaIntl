import { image } from '@kit.ImageKit';
import { router } from '@kit.ArkUI';
import { KomgaBookPage } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface ReaderParams {
  bookId: string;
  initialPage: number;
}

@Component
struct RemotePage {
  @Prop bookId: string;
  @Prop page: number;
  @State source: PixelMap | Resource = $r('app.media.app_icon');
  @State loading: boolean = true;

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const bytes = await komgaClient.getBookPage(this.bookId, this.page);
      const imageSource = image.createImageSource(bytes);
      this.source = await imageSource.createPixelMap({ editable: false });
      imageSource.release();
    } finally {
      this.loading = false;
    }
  }

  build() {
    Stack() {
      Image(this.source)
        .width('100%').height('100%').objectFit(ImageFit.Contain)
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
  private bookId: string = '';
  private swiperController: SwiperController = new SwiperController();
  private hideTimer: number = -1;

  aboutToAppear(): void {
    const params = router.getParams() as ReaderParams;
    this.bookId = params.bookId;
    this.currentPage = Math.max(0, params.initialPage || 0);
    this.load();
    this.scheduleHide();
  }

  aboutToDisappear(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
  }

  private async load(): Promise<void> {
    try {
      this.pages = await komgaClient.getBookPages(this.bookId);
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

  build() {
    Stack() {
      if (this.isLoading) {
        LoadingProgress().width(42).height(42).color(Color.White)
      } else {
        Swiper(this.swiperController) {
          ForEach(this.pages, (page: KomgaBookPage) => {
            RemotePage({ bookId: this.bookId, page: page.number })
          }, (page: KomgaBookPage) => `${page.number}`)
        }
        .index(this.currentPage).indicator(false).loop(false).width('100%').height('100%')
        .onChange((index: number) => {
          this.currentPage = index;
          komgaClient.updateReadProgress(this.bookId, index, index === this.pages.length - 1);
        })
        .onClick(() => this.toggleChrome())
      }

      if (this.chromeVisible) {
        Column() {
          Row() {
            Button({ type: ButtonType.Circle }) {
              Text('‹').fontSize(34).fontColor(Color.White)
            }
            .width(44).height(44).backgroundColor('#66121217').onClick(() => router.back())
            Blank()
            Text(`${this.currentPage + 1} / ${this.pages.length}`)
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
                if (mode === SliderChangeMode.End) this.swiperController.changeIndex(Math.round(value));
              })
            Text('›').fontSize(32).fontColor(Color.White)
              .onClick(() => this.swiperController.showNext())
          }
          .height(64).width('calc(100% - 24vp)').padding({ left: 16, right: 16 })
          .margin({ bottom: 14 }).backgroundColor('#E6181824').borderRadius(22)
        }
        .width('100%').height('100%').hitTestBehavior(HitTestMode.Transparent)
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
