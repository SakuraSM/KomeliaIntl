import { KomeliaColors } from '../core/design/KomeliaDesign';
import { router } from '@kit.ArkUI';
import { optionalRouteText, requiredRouteId } from '../core/navigation/RouteParams';
import { namespaceOf, OfflinePageRecord } from '../data/local/DatabaseModels';
import { bookColorCorrectionRepository, offlineRepository } from '../data/repository/DefaultRepositories';
import { readerEngine } from '../domain/service/ReaderEngine';
import { imageBorderCropper } from '../domain/service/ImageBorderCropper';
import { imageColorProcessor } from '../domain/service/ImageColorProcessor';
import { mindSporePanelDetector, PanelDetectionResult, PanelRect } from
  '../domain/service/MindSporePanelDetector';
import { mindSporeSuperResolution } from '../domain/service/MindSporeSuperResolution';
import { ReaderRequestToken } from '../domain/service/ReaderRequestToken';
import { panelDetectionCache, panelDetectionCacheKey, readerPageImageCache, readerPageImageCacheKey,
  readerPrefetchPageNumbers } from '../domain/service/ReaderPageCache';
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
import { KomgaBookPage } from '../model/KomgaModels';
import { colorCorrectionIsActive } from '../model/ColorCorrectionModels';
import {
  buildImageReaderSpreads,
  ImageReaderSpread,
  spreadIndexForPage
} from '../model/ImageReaderSpreads';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppState } from '../state/AppState';
import { isLocalBook, LOCAL_NAMESPACE } from '../model/LocalContentModels';
import { localLibraryService } from '../domain/service/LocalLibraryService';

interface ReaderParams {
  bookId: string;
  initialPage: number;
  bookTitle?: string;
  seriesTitle?: string;
  trackProgress?: boolean;
}

interface ReaderPixelMapRequest {
  namespace: string;
  bookId: string;
  page: number;
  cropBorders: boolean;
  colorCorrection: boolean;
  colorCorrectionRevision: number;
  aiEnabled: boolean;
}

async function createReaderPixelMap(request: ReaderPixelMapRequest): Promise<PixelMap> {
  const processingToken = new ReaderRequestToken();
  const correction = request.colorCorrection ?
    await bookColorCorrectionRepository.get(request.namespace, request.bookId) : undefined;
  const correctionActive = correction !== undefined && colorCorrectionIsActive(correction);
  let pixelMap = await readerEngine.pageSource(request.namespace, request.bookId, request.page, processingToken,
    request.cropBorders || correctionActive || request.aiEnabled);
  try {
    if (request.cropBorders) await imageBorderCropper.crop(pixelMap, processingToken);
    if (correctionActive && correction !== undefined) {
      await imageColorProcessor.apply(pixelMap, correction, processingToken);
    }
    if (request.aiEnabled) {
      const enhanced = await mindSporeSuperResolution.enhance(pixelMap, processingToken);
      pixelMap.release();
      pixelMap = enhanced;
    }
    return pixelMap;
  } catch (error) {
    pixelMap.release();
    throw error as Error;
  }
}

@Component
struct RemotePage {
  @Prop namespace: string;
  @Prop bookId: string;
  @Prop page: number;
  @Prop colorCorrection: boolean = false;
  @Prop colorCorrectionRevision: number = 0;
  @Prop cropBorders: boolean = false;
  @Prop aiEnabled: boolean = false;
  @Prop scaleType: LayoutScaleType = LayoutScaleType.SCREEN;
  @Prop stretchSmallImages: boolean = true;
  @Prop tapPosition: number = 0;
  @Prop tapPageCount: number = 1;
  @State source: PixelMap | undefined = undefined;
  @State loading: boolean = true;
  @State failed: boolean = false;
  @State zoomScale: number = 1;
  @State offsetX: number = 0;
  @State offsetY: number = 0;
  @State viewportWidth: number = 0;
  @State viewportHeight: number = 0;
  @State imageWidth: number = 1;
  @State imageHeight: number = 1;
  onZoomChange: (zoomed: boolean) => void = () => {};
  onPageTap: (positionX: number, positionY: number) => void = () => {};
  private requestToken: ReaderRequestToken = new ReaderRequestToken();
  private pixelMap?: PixelMap;
  private ownedCacheKey: string = '';
  private pinchStartScale: number = 1;
  private panStartX: number = 0;
  private panStartY: number = 0;

  aboutToAppear(): void {
    this.load();
  }

  aboutToDisappear(): void {
    this.requestToken.cancel();
    this.releaseOwnedImage();
    this.onZoomChange(false);
  }

  private cacheKey(): string {
    return readerPageImageCacheKey({
      sourceRevision: isLocalBook(this.bookId) ? localLibraryService.indexRevision : undefined,
      scope: komgaClient.getImageCacheScope(), namespace: this.namespace, bookId: this.bookId, page: this.page,
      cropBorders: this.cropBorders, colorCorrection: this.colorCorrection,
      colorCorrectionRevision: this.colorCorrectionRevision, aiEnabled: this.aiEnabled
    });
  }

  private adoptPixelMap(cacheKey: string, pixelMap: PixelMap): void {
    this.releaseOwnedImage();
    this.ownedCacheKey = cacheKey;
    this.pixelMap = pixelMap;
    const info = pixelMap.getImageInfoSync();
    this.imageWidth = Math.max(1, info.size.width);
    this.imageHeight = Math.max(1, info.size.height);
    this.source = pixelMap;
  }

  private releaseOwnedImage(): void {
    if (this.pixelMap !== undefined && this.ownedCacheKey.length > 0) {
      readerPageImageCache.release(this.ownedCacheKey, this.pixelMap);
    }
    this.pixelMap = undefined;
    this.ownedCacheKey = '';
  }

  private clamp(value: number, minimum: number, maximum: number): number {
    return Math.max(minimum, Math.min(value, maximum));
  }

  private constrainOffset(): void {
    const maximumX = Math.max(0, (this.displayWidth() * this.zoomScale - this.viewportWidth) / 2);
    const maximumY = Math.max(0, (this.displayHeight() * this.zoomScale - this.viewportHeight) / 2);
    this.offsetX = this.clamp(this.offsetX, -maximumX, maximumX);
    this.offsetY = this.clamp(this.offsetY, -maximumY, maximumY);
  }

  private resetZoom(): void {
    this.zoomScale = 1;
    this.offsetX = 0;
    this.offsetY = 0;
    this.onZoomChange(false);
  }

  private emitTap(localX: number, localY: number): void {
    if (this.zoomScale > 1.01 || this.viewportWidth <= 0) return;
    const localFraction = Math.max(0, Math.min(1, localX / this.viewportWidth));
    const verticalFraction = this.viewportHeight <= 0 ? 0.5 :
      Math.max(0, Math.min(1, localY / this.viewportHeight));
    this.onPageTap((this.tapPosition + localFraction) / Math.max(1, this.tapPageCount), verticalFraction);
  }

  private async load(): Promise<void> {
    this.requestToken.cancel();
    const token = new ReaderRequestToken();
    this.requestToken = token;
    this.loading = true;
    this.failed = false;
    const cacheKey = this.cacheKey();
    const cached = readerPageImageCache.acquireCached(cacheKey);
    if (cached !== undefined) {
      this.adoptPixelMap(cacheKey, cached);
      this.loading = false;
      return;
    }
    let loadedPixelMap: PixelMap | undefined;
    let adopted: boolean = false;
    try {
      loadedPixelMap = await readerPageImageCache.acquire(cacheKey, () => createReaderPixelMap({
        namespace: this.namespace, bookId: this.bookId, page: this.page, cropBorders: this.cropBorders,
        colorCorrection: this.colorCorrection, colorCorrectionRevision: this.colorCorrectionRevision,
        aiEnabled: this.aiEnabled
      }));
      if (token.isCancelled()) {
        readerPageImageCache.release(cacheKey, loadedPixelMap);
        loadedPixelMap = undefined;
        return;
      }
      this.adoptPixelMap(cacheKey, loadedPixelMap);
      adopted = true;
      loadedPixelMap = undefined;
    } catch (error) {
      if (!token.isCancelled()) this.failed = this.source === undefined;
    } finally {
      if (loadedPixelMap !== undefined && !adopted) readerPageImageCache.release(cacheKey, loadedPixelMap);
      if (!token.isCancelled()) this.loading = false;
    }
  }

  private baseScale(): number {
    if (this.viewportWidth <= 0 || this.viewportHeight <= 0) return 1;
    let value: number;
    if (this.scaleType === LayoutScaleType.FIT_WIDTH) {
      value = this.viewportWidth / this.imageWidth;
    } else if (this.scaleType === LayoutScaleType.FIT_HEIGHT) {
      value = this.viewportHeight / this.imageHeight;
    } else if (this.scaleType === LayoutScaleType.ORIGINAL) {
      value = 1;
    } else {
      value = Math.min(this.viewportWidth / this.imageWidth, this.viewportHeight / this.imageHeight);
    }
    return this.stretchSmallImages ? value : Math.min(1, value);
  }

  private displayWidth(): number {
    return Math.max(1, this.imageWidth * this.baseScale());
  }

  private displayHeight(): number {
    return Math.max(1, this.imageHeight * this.baseScale());
  }

  build() {
    Stack() {
      if (this.source !== undefined) {
        Image(this.source)
          .width(this.displayWidth()).height(this.displayHeight()).objectFit(ImageFit.Fill)
          .scale({ x: this.zoomScale, y: this.zoomScale })
          .translate({ x: this.offsetX, y: this.offsetY })
          .opacity(this.failed ? 0 : 1)
          .interpolation(ImageInterpolation.High).draggable(false)
      }
      if (this.loading) {
        LoadingProgress().width(36).height(36).color('#FFFFFF')
      } else if (this.failed) {
        Column({ space: 12 }) {
          Text($r('app.string.reader_page_load_failed')).fontSize(16).fontColor(Color.White)
          Button($r('app.string.retry')).height(44).onClick(() => this.load())
        }.justifyContent(FlexAlign.Center)
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black)
    .clip(true)
    .onAreaChange((_, area) => {
      this.viewportWidth = Number(area.width);
      this.viewportHeight = Number(area.height);
      this.constrainOffset();
    })
    .onClick((event: ClickEvent) => this.emitTap(event.x, event.y))
    .parallelGesture(
      GestureGroup(GestureMode.Parallel,
        PinchGesture({ fingers: 2, distance: 1 })
          .onActionStart(() => this.pinchStartScale = this.zoomScale)
          .onActionUpdate((event: GestureEvent) => {
            this.zoomScale = this.clamp(this.pinchStartScale * event.scale, 1, 5);
            this.constrainOffset();
            this.onZoomChange(this.zoomScale > 1.01);
          })
          .onActionEnd(() => {
            if (this.zoomScale <= 1.01) this.resetZoom();
          }),
        PanGesture({ fingers: 1, direction: PanDirection.All, distance: 3 })
          .onActionStart(() => {
            this.panStartX = this.offsetX;
            this.panStartY = this.offsetY;
          })
          .onActionUpdate((event: GestureEvent) => {
            if (this.zoomScale <= 1.01 && this.displayWidth() <= this.viewportWidth &&
              this.displayHeight() <= this.viewportHeight) return;
            this.offsetX = this.panStartX + event.offsetX;
            this.offsetY = this.panStartY + event.offsetY;
            this.constrainOffset();
          })
      )
    )
  }
}

@Component
struct PanelRemotePage {
  @Prop namespace: string;
  @Prop bookId: string;
  @Prop page: number;
  @Prop readingDirection: PagedReadingDirection = PagedReadingDirection.LEFT_TO_RIGHT;
  @Prop colorCorrection: boolean = false;
  @Prop colorCorrectionRevision: number = 0;
  @Prop cropBorders: boolean = false;
  @Prop aiEnabled: boolean = false;
  @Prop reducedMotion: boolean = false;
  @State source: PixelMap | undefined = undefined;
  @State loading: boolean = true;
  @State failed: boolean = false;
  @State panels: PanelRect[] = [];
  @State panelIndex: number = 0;
  @State zoomOutActive: boolean = false;
  @State viewportWidth: number = 0;
  @State viewportHeight: number = 0;
  @State imageWidth: number = 1;
  @State imageHeight: number = 1;
  onPreviousPage: () => void = () => {};
  onNextPage: () => void = () => {};
  onCenterTap: () => void = () => {};
  private requestToken: ReaderRequestToken = new ReaderRequestToken();
  private pixelMap?: PixelMap;
  private ownedCacheKey: string = '';

  aboutToAppear(): void {
    this.load();
  }

  aboutToDisappear(): void {
    this.requestToken.cancel();
    this.releaseOwnedImage();
  }

  private cacheKey(): string {
    return readerPageImageCacheKey({
      sourceRevision: isLocalBook(this.bookId) ? localLibraryService.indexRevision : undefined,
      scope: komgaClient.getImageCacheScope(), namespace: this.namespace, bookId: this.bookId, page: this.page,
      cropBorders: this.cropBorders, colorCorrection: this.colorCorrection,
      colorCorrectionRevision: this.colorCorrectionRevision, aiEnabled: this.aiEnabled
    });
  }

  private adoptPixelMap(cacheKey: string, pixelMap: PixelMap): void {
    this.releaseOwnedImage();
    this.ownedCacheKey = cacheKey;
    this.pixelMap = pixelMap;
    const info = pixelMap.getImageInfoSync();
    this.imageWidth = Math.max(1, info.size.width);
    this.imageHeight = Math.max(1, info.size.height);
    this.source = pixelMap;
  }

  private releaseOwnedImage(): void {
    if (this.pixelMap !== undefined && this.ownedCacheKey.length > 0) {
      readerPageImageCache.release(this.ownedCacheKey, this.pixelMap);
    }
    this.pixelMap = undefined;
    this.ownedCacheKey = '';
  }

  private async load(): Promise<void> {
    this.requestToken.cancel();
    const token = new ReaderRequestToken();
    this.requestToken = token;
    this.loading = true;
    this.failed = false;
    const cacheKey = this.cacheKey();
    let loadedPixelMap: PixelMap | undefined;
    let detectionCacheKey: string = '';
    let detection: PanelDetectionResult | undefined = undefined;
    let adopted: boolean = false;
    try {
      loadedPixelMap = readerPageImageCache.acquireCached(cacheKey);
      if (loadedPixelMap === undefined) {
        loadedPixelMap = await readerPageImageCache.acquire(cacheKey, () => createReaderPixelMap({
          namespace: this.namespace, bookId: this.bookId, page: this.page, cropBorders: this.cropBorders,
          colorCorrection: this.colorCorrection, colorCorrectionRevision: this.colorCorrectionRevision,
          aiEnabled: this.aiEnabled
        }));
      }
      if (token.isCancelled()) {
        readerPageImageCache.release(cacheKey, loadedPixelMap);
        loadedPixelMap = undefined;
        return;
      }
      detectionCacheKey = panelDetectionCacheKey(cacheKey, this.readingDirection);
      detection = panelDetectionCache.acquireCached(detectionCacheKey);
      if (detection === undefined) {
        detection = await panelDetectionCache.acquire(detectionCacheKey,
          () => mindSporePanelDetector.detect(loadedPixelMap as PixelMap, this.readingDirection));
      }
      token.throwIfCancelled();
      this.adoptPixelMap(cacheKey, loadedPixelMap);
      adopted = true;
      this.panels = detection.panels;
      this.panelIndex = 0;
      this.zoomOutActive = detection.panels.length === 0;
      loadedPixelMap = undefined;
    } catch (_) {
      if (!token.isCancelled()) this.failed = this.source === undefined;
    } finally {
      if (detection !== undefined && detectionCacheKey.length > 0) {
        panelDetectionCache.release(detectionCacheKey, detection);
      }
      if (loadedPixelMap !== undefined && !adopted) readerPageImageCache.release(cacheKey, loadedPixelMap);
      if (!token.isCancelled()) this.loading = false;
    }
  }

  private currentPanel(): PanelRect | undefined {
    if (this.zoomOutActive || this.panels.length === 0) return undefined;
    return this.panels[Math.max(0, Math.min(this.panelIndex, this.panels.length - 1))];
  }

  private baseScale(): number {
    if (this.viewportWidth <= 0 || this.viewportHeight <= 0) return 1;
    return Math.min(this.viewportWidth / this.imageWidth, this.viewportHeight / this.imageHeight);
  }

  private displayWidth(): number {
    return Math.max(1, this.imageWidth * this.baseScale());
  }

  private displayHeight(): number {
    return Math.max(1, this.imageHeight * this.baseScale());
  }

  private focusScale(): number {
    const panel = this.currentPanel();
    if (panel === undefined || this.viewportWidth <= 0 || this.viewportHeight <= 0) return 1;
    const base = this.baseScale();
    const horizontal = this.viewportWidth * 0.94 / Math.max(1, panel.width * base);
    const vertical = this.viewportHeight * 0.94 / Math.max(1, panel.height * base);
    return Math.max(1, Math.min(8, Math.min(horizontal, vertical)));
  }

  private focusOffsetX(): number {
    const panel = this.currentPanel();
    if (panel === undefined) return 0;
    const center = (panel.x + panel.width / 2) * this.baseScale();
    return (this.displayWidth() / 2 - center) * this.focusScale();
  }

  private focusOffsetY(): number {
    const panel = this.currentPanel();
    if (panel === undefined) return 0;
    const center = (panel.y + panel.height / 2) * this.baseScale();
    return (this.displayHeight() / 2 - center) * this.focusScale();
  }

  private nextPanel(): void {
    if (this.panels.length === 0) {
      this.onNextPage();
    } else if (this.panelIndex < this.panels.length - 1) {
      this.panelIndex += 1;
      this.zoomOutActive = false;
    } else if (!this.zoomOutActive) {
      this.zoomOutActive = true;
    } else {
      this.onNextPage();
    }
  }

  private previousPanel(): void {
    if (this.panels.length === 0) {
      this.onPreviousPage();
    } else if (this.zoomOutActive) {
      this.zoomOutActive = false;
      this.panelIndex = this.panels.length - 1;
    } else if (this.panelIndex > 0) {
      this.panelIndex -= 1;
    } else {
      this.onPreviousPage();
    }
  }

  private handleTap(x: number): void {
    const fraction = this.viewportWidth <= 0 ? 0.5 : x / this.viewportWidth;
    if (fraction >= 0.25 && fraction <= 0.75) {
      this.onCenterTap();
    } else if (fraction < 0.25) {
      if (this.readingDirection === PagedReadingDirection.RIGHT_TO_LEFT) this.nextPanel();
      else this.previousPanel();
    } else if (this.readingDirection === PagedReadingDirection.RIGHT_TO_LEFT) {
      this.previousPanel();
    } else {
      this.nextPanel();
    }
  }

  build() {
    Stack() {
      if (this.source !== undefined) {
        Image(this.source)
          .width(this.displayWidth()).height(this.displayHeight()).objectFit(ImageFit.Fill)
          .scale({ x: this.focusScale(), y: this.focusScale() })
          .translate({ x: this.focusOffsetX(), y: this.focusOffsetY() })
          .opacity(this.failed ? 0 : 1)
          .interpolation(ImageInterpolation.High).draggable(false)
          .animation({ duration: this.reducedMotion ? 0 : 180, curve: Curve.EaseOut })
      }
      if (this.loading) {
        Column({ space: 12 }) {
          LoadingProgress().width(36).height(36).color(Color.White)
          Text($r('app.string.panel_detection_loading')).fontSize(14).fontColor('#CCFFFFFF')
        }.justifyContent(FlexAlign.Center)
      } else if (this.failed) {
        Column({ space: 12 }) {
          Text($r('app.string.panel_detection_failed')).fontSize(16).fontColor(Color.White)
          Button($r('app.string.retry')).height(44).onClick(() => this.load())
        }.justifyContent(FlexAlign.Center)
      }
      if (!this.loading && !this.failed && this.panels.length > 0) {
        Text(this.zoomOutActive ? $r('app.string.panel_full_page') :
          `${this.panelIndex + 1} / ${this.panels.length}`)
          .fontSize(12).fontColor('#DFFFFFFF').backgroundColor('#66121217')
          .padding({ left: 10, right: 10, top: 6, bottom: 6 }).borderRadius(14)
          .position({ right: 12, bottom: 12 }).hitTestBehavior(HitTestMode.None)
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black).clip(true)
    .onAreaChange((_, area) => {
      this.viewportWidth = Number(area.width);
      this.viewportHeight = Number(area.height);
    })
    .onClick((event: ClickEvent) => this.handleTap(event.x))
  }
}

@Entry
@Component
struct ImageReader {
  @State pages: KomgaBookPage[] = [];
  @State currentPage: number = 0;
  @State chromeVisible: boolean = true;
  @State isLoading: boolean = true;
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
  @State colorCorrectionRevision: number = 0;
  @State aiEnabled: boolean = false;
  @State flashEnabled: boolean = false;
  @State flashDuration: number = 100;
  @State flashEveryNPages: number = 1;
  @State flashColor: ReaderFlashColor = ReaderFlashColor.BLACK;
  @State flashVisible: boolean = false;
  @State flashWhite: boolean = false;
  @State reducedMotion: boolean = false;
  @State pageZoomed: boolean = false;
  @State viewportWidth: number = 0;
  @State viewportHeight: number = 0;
  @State settingsVisible: boolean = false;
  @State settingsTab: number = 0;
  @State panelModelAvailable: boolean = false;
  @State aiModelAvailable: boolean = false;
  private bookId: string = '';
  private bookTitle: string = '';
  private seriesTitle: string = '';
  private namespace: string = '';
  private trackProgress: boolean = true;
  private swiperController: SwiperController = new SwiperController();
  private listScroller: Scroller = new Scroller();
  private hideTimer: number = -1;
  private flashTimer: number = -1;
  private prefetchTimer: number = -1;

  aboutToAppear(): void {
    const params = router.getParams() as ReaderParams | undefined;
    this.bookId = requiredRouteId(params?.bookId);
    this.bookTitle = optionalRouteText(params?.bookTitle);
    this.seriesTitle = optionalRouteText(params?.seriesTitle);
    this.trackProgress = params?.trackProgress !== false;
    const key = AppState.getInstance().namespace;
    this.namespace = isLocalBook(this.bookId) ? LOCAL_NAMESPACE : key === undefined ? '' : namespaceOf(key);
    this.currentPage = Math.max(0, params?.initialPage || 0);
    if (this.bookId.length > 0) {
      this.restoreAndLoad();
      this.scheduleHide();
    }
  }

  onPageShow(): void {
    if (this.namespace.length === 0 || this.bookId.length === 0) return;
    this.refreshColorCorrection().catch(() => {});
  }

  private async restoreAndLoad(): Promise<void> {
    const settingsRequest = ImageReaderPreferences.load();
    const modelAvailabilityRequest = Promise.all([
      mindSporePanelDetector.isAvailable(),
      mindSporeSuperResolution.isAvailable()
    ]);
    const reducedMotionRequest = AppPreferences.getBoolean('reducedMotion');
    const colorCorrectionRequest = bookColorCorrectionRepository.get(this.namespace, this.bookId);
    const pagesRequest = this.requestPages();
    try {
      const settings = await settingsRequest;
      const modelAvailability = await modelAvailabilityRequest;
      this.panelModelAvailable = modelAvailability[0];
      this.aiModelAvailable = modelAvailability[1];
      this.applySettings(settings);
      this.reducedMotion = await reducedMotionRequest;
      try {
        const colorCorrection = await colorCorrectionRequest;
        this.colorCorrection = colorCorrection !== undefined && colorCorrectionIsActive(colorCorrection);
        this.colorCorrectionRevision += 1;
      } catch (_) {
        this.colorCorrection = false;
      }
      this.pages = await pagesRequest;
    } finally {
      this.currentPage = Math.max(0, Math.min(Math.max(0, this.pages.length - 1), this.currentPage));
      this.schedulePrefetch(this.currentPage);
      this.isLoading = false;
    }
  }

  private async refreshColorCorrection(): Promise<void> {
    const value = await bookColorCorrectionRepository.get(this.namespace, this.bookId);
    this.colorCorrection = value !== undefined && colorCorrectionIsActive(value);
    this.colorCorrectionRevision += 1;
  }

  private applySettings(value: ImageReaderSettingsValue): void {
    this.readerType = value.readerType === ImageReaderType.PANELS && !this.panelModelAvailable ?
      ImageReaderType.PAGED : value.readerType;
    this.pagedDirection = value.pagedDirection;
    this.continuousDirection = value.continuousDirection;
    this.pageLayout = value.pageLayout;
    this.scaleType = value.scaleType;
    this.layoutOffset = value.layoutOffset;
    this.sidePadding = value.sidePadding;
    this.pageSpacing = value.pageSpacing;
    this.stretchSmallImages = value.stretchSmallImages;
    this.cropBorders = value.cropBorders;
    // Color correction is stored per book. The legacy global flag is ignored.
    this.aiEnabled = value.aiEnabled && this.aiModelAvailable;
    this.flashEnabled = value.flashEnabled;
    this.flashDuration = value.flashDuration;
    this.flashEveryNPages = value.flashEveryNPages;
    this.flashColor = value.flashColor;
  }

  private currentSettings(): ImageReaderSettingsValue {
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
      colorCorrection: false,
      aiEnabled: this.aiEnabled,
      flashEnabled: this.flashEnabled,
      flashDuration: this.flashDuration,
      flashEveryNPages: this.flashEveryNPages,
      flashColor: this.flashColor
    };
  }

  private saveSettings(): void {
    ImageReaderPreferences.save(this.currentSettings()).catch(() => {});
  }

  private openColorCorrection(): void {
    this.closeSettings();
    router.pushUrl({
      url: 'pages/ColorCorrection',
      params: { namespace: this.namespace, bookId: this.bookId, page: this.pageAt(this.currentPage) }
    });
  }

  aboutToDisappear(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    if (this.flashTimer >= 0) clearTimeout(this.flashTimer);
    if (this.prefetchTimer >= 0) clearTimeout(this.prefetchTimer);
  }

  private async requestPages(): Promise<KomgaBookPage[]> {
    if (isLocalBook(this.bookId)) return localLibraryService.pages(this.bookId);
    try {
      return await komgaClient.getBookPages(this.bookId);
    } catch (_) {
      const localPages = await offlineRepository.listPages(this.namespace, this.bookId);
      return localPages.map((page: OfflinePageRecord) => this.offlinePage(page));
    }
  }

  private schedulePrefetch(index: number): void {
    if (this.prefetchTimer >= 0) clearTimeout(this.prefetchTimer);
    if (!AppState.getInstance().isOnline || this.pages.length === 0) return;
    const pages = this.pages.map((page: KomgaBookPage): number => page.number);
    const pageNumbers = readerPrefetchPageNumbers(pages, index);
    this.prefetchTimer = setTimeout(() => {
      this.prefetchTimer = -1;
      readerEngine.prefetchPages(this.namespace, this.bookId, pageNumbers).catch(() => {});
    }, 300);
  }

  private offlinePage(page: OfflinePageRecord): KomgaBookPage {
    return {
      number: page.pageNumber,
      fileName: page.localPath,
      mediaType: page.mediaType,
      sizeBytes: page.sizeBytes
    };
  }

  private scheduleHide(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    this.hideTimer = setTimeout(() => this.chromeVisible = false, 4500);
  }

  private toggleChrome(): void {
    this.chromeVisible = !this.chromeVisible;
    if (this.chromeVisible) this.scheduleHide();
  }

  private orderedPages(): KomgaBookPage[] {
    // Reading direction changes the physical gesture and animation direction,
    // never the logical Komga page order.
    return this.pages;
  }

  private pageSpreads(): ImageReaderSpread[] {
    return buildImageReaderSpreads(this.orderedPages(), this.pageLayout, this.layoutOffset);
  }

  private currentSpreadIndex(): number {
    return spreadIndexForPage(this.pageSpreads(), this.currentPage);
  }

  private shouldLoadSpread(index: number): boolean {
    return Math.abs(index - this.currentSpreadIndex()) <= 1;
  }

  private pageAt(index: number): number {
    const ordered = this.orderedPages();
    return ordered.length === 0 ? 0 : ordered[Math.max(0, Math.min(index, ordered.length - 1))].number;
  }

  private shouldLoadPage(index: number): boolean {
    return Math.abs(index - this.currentPage) <= 1;
  }

  private updatePage(index: number): void {
    if (index === this.currentPage) return;
    this.pageZoomed = false;
    this.currentPage = index;
    this.schedulePrefetch(index);
    const page = this.pageAt(index);
    const completed = index === this.orderedPages().length - 1;
    if (this.trackProgress) readerEngine.updateProgress(this.namespace, this.bookId, page, completed);
    this.triggerFlash(index);
  }

  private updateSpread(index: number): void {
    const spreads = this.pageSpreads();
    if (index < 0 || index >= spreads.length) return;
    const spread = spreads[index];
    if (spread.firstIndex === this.currentPage) return;
    this.pageZoomed = false;
    this.currentPage = spread.firstIndex;
    this.schedulePrefetch(spread.firstIndex);
    const completed = spread.lastIndex === this.orderedPages().length - 1;
    if (this.trackProgress) {
      readerEngine.updateProgress(this.namespace, this.bookId, this.pageAt(spread.firstIndex), completed);
    }
    this.triggerFlash(spread.firstIndex);
  }

  private triggerFlash(index: number): void {
    if (!this.flashEnabled || (index + 1) % Math.max(1, Math.round(this.flashEveryNPages)) !== 0) return;
    if (this.flashTimer >= 0) clearTimeout(this.flashTimer);
    this.flashWhite = this.flashColor === ReaderFlashColor.WHITE ||
      (this.flashColor === ReaderFlashColor.WHITE_AND_BLACK && index % 2 === 0);
    this.flashVisible = true;
    this.flashTimer = setTimeout(() => this.flashVisible = false,
      this.reducedMotion ? 0 : Math.max(100, this.flashDuration));
  }

  private isContinuous(): boolean {
    return this.readerType === ImageReaderType.CONTINUOUS;
  }

  private isPanels(): boolean {
    return this.readerType === ImageReaderType.PANELS;
  }

  private goToIndex(index: number): void {
    if (this.pages.length === 0) return;
    const target = Math.max(0, Math.min(this.pages.length - 1, index));
    if (this.isContinuous()) {
      this.listScroller.scrollToIndex(target, !this.reducedMotion, ScrollAlign.START);
    } else if (this.isPanels()) {
      this.updatePage(target);
    } else {
      this.swiperController.changeIndex(spreadIndexForPage(this.pageSpreads(), target), !this.reducedMotion);
    }
  }

  private goPrevious(): void {
    if (this.isContinuous() || this.isPanels()) this.goToIndex(this.currentPage - 1);
    else this.swiperController.changeIndex(Math.max(0, this.currentSpreadIndex() - 1), !this.reducedMotion);
  }

  private goNext(): void {
    if (this.isContinuous() || this.isPanels()) this.goToIndex(this.currentPage + 1);
    else this.swiperController.changeIndex(
      Math.min(this.pageSpreads().length - 1, this.currentSpreadIndex() + 1), !this.reducedMotion);
  }

  private handlePageTap(positionFraction: number, verticalFraction: number): void {
    if (this.pageZoomed) return;
    if (this.chromeVisible && verticalFraction < 0.16) {
      if (positionFraction < 0.16) {
        router.back();
        return;
      }
      if (positionFraction >= 0.64 && positionFraction <= 0.81) {
        this.openSettings();
        return;
      }
    }
    if (positionFraction < 0.25) {
      if (this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT) this.goNext();
      else this.goPrevious();
    } else if (positionFraction > 0.75) {
      if (this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT) this.goPrevious();
      else this.goNext();
    } else {
      this.toggleChrome();
    }
  }

  private continuousItemWidth(page: KomgaBookPage): number | string {
    if (this.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM) return '100%';
    const availableHeight = Math.max(1, this.viewportHeight * (1 - this.sidePadding * 2));
    if ((page.width || 0) > 0 && (page.height || 0) > 0) {
      return Math.max(1, availableHeight * (page.width || 1) / (page.height || 1));
    }
    return Math.max(1, this.viewportWidth);
  }

  private continuousItemHeight(page: KomgaBookPage): number | string {
    if (this.continuousDirection !== ContinuousReadingDirection.TOP_TO_BOTTOM) return '100%';
    const availableWidth = Math.max(1, this.viewportWidth * (1 - this.sidePadding * 2));
    if ((page.width || 0) > 0 && (page.height || 0) > 0) {
      return Math.max(1, availableWidth * (page.height || 1) / (page.width || 1));
    }
    return Math.max(1, this.viewportHeight);
  }

  private openSettings(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    this.chromeVisible = true;
    this.settingsVisible = true;
  }

  private pageCounter(): string {
    if (this.isContinuous() || this.isPanels() || this.pageLayout === PageDisplayLayout.SINGLE_PAGE) {
      return `${this.currentPage + 1} / ${this.pages.length}`;
    }
    const spreads = this.pageSpreads();
    const spread = spreads[this.currentSpreadIndex()];
    if (spread === undefined || spread.firstIndex === spread.lastIndex) {
      return `${this.currentPage + 1} / ${this.pages.length}`;
    }
    return `${spread.firstIndex + 1}-${spread.lastIndex + 1} / ${this.pages.length}`;
  }

  private closeSettings(): void {
    this.settingsVisible = false;
    this.scheduleHide();
  }

  @Builder
  private sheetOptionButton(title: ResourceStr, selected: () => boolean, onSelect: () => void,
    enabled: boolean = true) {
    Button(title)
      .height(44)
      .margin({ right: 8, bottom: 8 })
      .padding({ left: 14, right: 14 })
      .borderRadius(14)
      .fontColor(selected() ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(selected() ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .enabled(enabled)
      .opacity(enabled ? 1 : 0.45)
      .onClick(() => {
        onSelect();
        this.saveSettings();
      })
      .gesture(TapGesture({ fingers: 1, count: 1 }).onAction(() => {
        onSelect();
        this.saveSettings();
      }))
  }

  @Builder
  private sheetToggleRow(title: ResourceStr, value: () => boolean, onChange: (value: boolean) => void,
    enabled: boolean = true) {
    Row({ space: 12 }) {
      Text(title).fontSize(16).fontColor($r('app.color.on_surface')).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: value() })
        .enabled(enabled)
        .onChange((next: boolean) => {
          onChange(next);
          this.saveSettings();
        })
    }
    .constraintSize({ minHeight: 52 }).padding({ left: 14, right: 14 }).width('100%')
    .opacity(enabled ? 1 : 0.45)
  }

  @Builder
  private sheetLabel(title: ResourceStr) {
    Text(title).fontSize(14).fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface_variant')).width('100%')
  }

  @Builder
  private readingSheetContent() {
    Column({ space: 10 }) {
      this.sheetLabel($r('app.string.reader_type'))
      Flex({ wrap: FlexWrap.Wrap }) {
        this.sheetOptionButton($r('app.string.paginated'), () => this.readerType === ImageReaderType.PAGED,
          () => this.readerType = ImageReaderType.PAGED)
        this.sheetOptionButton($r('app.string.continuous'), () => this.readerType === ImageReaderType.CONTINUOUS,
          () => this.readerType = ImageReaderType.CONTINUOUS)
        if (this.panelModelAvailable) {
          this.sheetOptionButton($r('app.string.panel_mode'), () => this.readerType === ImageReaderType.PANELS,
            () => this.readerType = ImageReaderType.PANELS)
        } else {
          this.sheetOptionButton($r('app.string.panel_mode'), () => false, () => {}, false)
        }
      }.width('100%')
      if (!this.panelModelAvailable) {
        Text($r('app.string.panel_model_required')).fontSize(12)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      }

      this.sheetLabel($r('app.string.reading_direction'))
      Flex({ wrap: FlexWrap.Wrap }) {
        if (this.readerType !== ImageReaderType.CONTINUOUS) {
          this.sheetOptionButton($r('app.string.left_to_right'),
            () => this.pagedDirection === PagedReadingDirection.LEFT_TO_RIGHT,
            () => this.pagedDirection = PagedReadingDirection.LEFT_TO_RIGHT)
          this.sheetOptionButton($r('app.string.right_to_left'),
            () => this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT,
            () => this.pagedDirection = PagedReadingDirection.RIGHT_TO_LEFT)
        } else {
          this.sheetOptionButton($r('app.string.top_to_bottom'),
            () => this.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM,
            () => this.continuousDirection = ContinuousReadingDirection.TOP_TO_BOTTOM)
          this.sheetOptionButton($r('app.string.left_to_right'),
            () => this.continuousDirection === ContinuousReadingDirection.LEFT_TO_RIGHT,
            () => this.continuousDirection = ContinuousReadingDirection.LEFT_TO_RIGHT)
          this.sheetOptionButton($r('app.string.right_to_left'),
            () => this.continuousDirection === ContinuousReadingDirection.RIGHT_TO_LEFT,
            () => this.continuousDirection = ContinuousReadingDirection.RIGHT_TO_LEFT)
        }
      }.width('100%')

      if (this.readerType === ImageReaderType.PAGED) {
        this.sheetLabel($r('app.string.page_scale'))
        Flex({ wrap: FlexWrap.Wrap }) {
          this.sheetOptionButton($r('app.string.fit_screen'), () => this.scaleType === LayoutScaleType.SCREEN,
            () => this.scaleType = LayoutScaleType.SCREEN)
          this.sheetOptionButton($r('app.string.fit_width'), () => this.scaleType === LayoutScaleType.FIT_WIDTH,
            () => this.scaleType = LayoutScaleType.FIT_WIDTH)
          this.sheetOptionButton($r('app.string.fit_height'), () => this.scaleType === LayoutScaleType.FIT_HEIGHT,
            () => this.scaleType = LayoutScaleType.FIT_HEIGHT)
          this.sheetOptionButton($r('app.string.original_size'), () => this.scaleType === LayoutScaleType.ORIGINAL,
            () => this.scaleType = LayoutScaleType.ORIGINAL)
        }.width('100%')
        this.sheetLabel($r('app.string.page_layout'))
        Flex({ wrap: FlexWrap.Wrap }) {
          this.sheetOptionButton($r('app.string.single_page'),
            () => this.pageLayout === PageDisplayLayout.SINGLE_PAGE,
            () => this.pageLayout = PageDisplayLayout.SINGLE_PAGE)
          this.sheetOptionButton($r('app.string.double_page'),
            () => this.pageLayout === PageDisplayLayout.DOUBLE_PAGES,
            () => this.pageLayout = PageDisplayLayout.DOUBLE_PAGES)
          this.sheetOptionButton($r('app.string.double_pages_no_cover'),
            () => this.pageLayout === PageDisplayLayout.DOUBLE_PAGES_NO_COVER,
            () => this.pageLayout = PageDisplayLayout.DOUBLE_PAGES_NO_COVER)
        }.width('100%')
        if (this.pageLayout !== PageDisplayLayout.SINGLE_PAGE) {
          this.sheetToggleRow($r('app.string.offset_pages'), () => this.layoutOffset,
            (value: boolean) => this.layoutOffset = value)
        }
      } else if (this.readerType === ImageReaderType.CONTINUOUS) {
        Row() {
          Text($r('app.string.continuous_side_padding')).fontSize(14)
            .fontColor($r('app.color.on_surface')).layoutWeight(1)
          Text(`${Math.round(this.sidePadding * 200)}%`).fontSize(14)
            .fontColor($r('app.color.on_surface_variant'))
        }.width('100%')
        Slider({ value: this.sidePadding, min: 0, max: 0.4, step: 0.025 })
          .selectedColor($r('app.color.primary')).onChange((value: number) => {
            this.sidePadding = value;
            this.saveSettings();
          })
        Row() {
          Text($r('app.string.continuous_page_spacing')).fontSize(14)
            .fontColor($r('app.color.on_surface')).layoutWeight(1)
          Text(`${Math.round(this.pageSpacing)}vp`).fontSize(14)
            .fontColor($r('app.color.on_surface_variant'))
        }.width('100%')
        Slider({ value: this.pageSpacing, min: 0, max: 250, step: 10 })
          .selectedColor($r('app.color.primary')).onChange((value: number) => {
            this.pageSpacing = value;
            this.saveSettings();
          })
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private imageSheetContent() {
    Column() {
      this.sheetToggleRow($r('app.string.stretch_small_images'), () => this.stretchSmallImages,
        (value: boolean) => this.stretchSmallImages = value)
      this.sheetToggleRow($r('app.string.crop_borders'), () => this.cropBorders,
        (value: boolean) => this.cropBorders = value)
      Row({ space: 10 }) {
        Text($r('app.string.color_correction')).fontSize(14)
          .fontColor($r('app.color.on_surface')).layoutWeight(1)
        if (this.colorCorrection) {
          Text($r('app.string.color_correction_active')).fontSize(12)
            .fontColor($r('app.color.primary'))
        }
        Text('›').fontSize(24).fontColor($r('app.color.on_surface_variant'))
      }
      .constraintSize({ minHeight: 52 }).padding({ left: 14, right: 14 }).width('100%')
      .onClick(() => this.openColorCorrection())
      .accessibilityText($r('app.string.color_correction'))
      this.sheetToggleRow($r('app.string.mindspore_enhancement'), () => this.aiEnabled,
        (value: boolean) => this.aiEnabled = value, this.aiModelAvailable)
      if (!this.aiModelAvailable) {
        Text($r('app.string.mindspore_model_unavailable')).fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .padding({ left: 14, right: 14, bottom: 8 }).width('100%')
      }
      this.sheetToggleRow($r('app.string.flash_on_page_change'), () => this.flashEnabled,
        (value: boolean) => this.flashEnabled = value)
      this.sheetToggleRow($r('app.string.reduce_motion'), () => this.reducedMotion,
        (value: boolean) => {
          this.reducedMotion = value;
          AppPreferences.putBoolean('reducedMotion', value).catch(() => {});
          AppState.getInstance().reducedMotion = value;
        })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private settingsSheet() {
    Column({ space: 12 }) {
      Row({ space: 8 }) {
        Text($r('app.string.settings')).fontSize(22).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface')).layoutWeight(1)
        Button($r('app.string.done')).height(44).borderRadius(16)
          .fontColor($r('app.color.primary')).backgroundColor($r('app.color.primary_container'))
          .onClick(() => this.closeSettings())
          .gesture(TapGesture({ fingers: 1, count: 1 }).onAction(() => this.closeSettings()))
      }.width('100%')
      Row({ space: 8 }) {
        Button($r('app.string.reading_settings')).layoutWeight(1).height(44).borderRadius(14)
          .fontColor(this.settingsTab === 0 ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
          .backgroundColor(this.settingsTab === 0 ? $r('app.color.primary_container') :
            KomeliaColors.surfaceContainer())
          .onClick(() => this.settingsTab = 0)
          .gesture(TapGesture({ fingers: 1, count: 1 }).onAction(() => this.settingsTab = 0))
        Button($r('app.string.image_settings')).layoutWeight(1).height(44).borderRadius(14)
          .fontColor(this.settingsTab === 1 ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
          .backgroundColor(this.settingsTab === 1 ? $r('app.color.primary_container') :
            KomeliaColors.surfaceContainer())
          .onClick(() => this.settingsTab = 1)
          .gesture(TapGesture({ fingers: 1, count: 1 }).onAction(() => this.settingsTab = 1))
      }.width('100%')
      Scroll() {
        if (this.settingsTab === 0) {
          this.readingSheetContent()
        } else {
          this.imageSheetContent()
        }
      }.layoutWeight(1).width('100%').edgeEffect(EdgeEffect.Spring)
    }
    .padding({ left: 16, right: 16, top: 8, bottom: 20 })
    .width('100%').height('100%').backgroundColor(KomeliaColors.surface())
  }

  @Builder
  private settingsOverlay() {
    Stack({ alignContent: Alignment.Bottom }) {
      Column()
        .width('100%').height('100%')
        .backgroundColor('#70000000')
        .zIndex(0)
        .onClick(() => this.closeSettings())
      Column() {
        this.settingsSheet()
      }
      .width('100%').height('82%')
      .borderRadius({ topLeft: 28, topRight: 28 })
      .clip(true)
      .zIndex(1)
      .onClick(() => {})
    }
    .width('100%').height('100%')
  }

  @Builder
  private panelsReader() {
    if (this.pages.length > 0) {
      ForEach([this.pages[this.currentPage]], (page: KomgaBookPage) => {
        PanelRemotePage({
          namespace: this.namespace,
          bookId: this.bookId,
          page: page.number,
          readingDirection: this.pagedDirection,
          colorCorrection: this.colorCorrection,
          colorCorrectionRevision: this.colorCorrectionRevision,
          cropBorders: this.cropBorders,
          aiEnabled: this.aiEnabled,
          reducedMotion: this.reducedMotion,
          onPreviousPage: () => {
            this.goPrevious();
            this.scheduleHide();
          },
          onNextPage: () => {
            this.goNext();
            this.scheduleHide();
          },
          onCenterTap: () => this.toggleChrome()
        })
      }, (page: KomgaBookPage) => `${page.number}:${this.pagedDirection}:` +
        `${this.cropBorders ? 1 : 0}:${this.aiEnabled ? 1 : 0}:` +
        `${this.colorCorrection ? this.colorCorrectionRevision : 0}`)
    }
  }

  @Builder
  private paginatedReader() {
    Swiper(this.swiperController) {
      ForEach(this.pageSpreads(), (spread: ImageReaderSpread, spreadIndex: number) => {
        if (this.shouldLoadSpread(spreadIndex)) {
          Row() {
            ForEach(spread.pages, (page: KomgaBookPage, pageIndex: number) => {
              RemotePage({ namespace: this.namespace, bookId: this.bookId, page: page.number,
                colorCorrection: this.colorCorrection,
                colorCorrectionRevision: this.colorCorrectionRevision,
                cropBorders: this.cropBorders,
                aiEnabled: this.aiEnabled,
                scaleType: this.scaleType,
                stretchSmallImages: this.stretchSmallImages,
                tapPosition: this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT
                  ? spread.pages.length - 1 - pageIndex : pageIndex,
                tapPageCount: spread.pages.length,
                onPageTap: (positionX: number, positionY: number) =>
                  this.handlePageTap(positionX, positionY),
                onZoomChange: (zoomed: boolean) => this.pageZoomed = zoomed })
                .layoutWeight(1)
            }, (page: KomgaBookPage) => `${page.number}:${this.cropBorders ? 1 : 0}:` +
              `${this.aiEnabled ? 1 : 0}:` +
              `${this.colorCorrection ? this.colorCorrectionRevision : 0}`)
          }
          .width('100%').height('100%')
          .direction(this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT ? Direction.Rtl : Direction.Ltr)
        } else {
          Blank().width('100%').height('100%').backgroundColor(Color.Black)
        }
      }, (spread: ImageReaderSpread) => spread.key)
    }
    .index(this.currentSpreadIndex()).indicator(false).loop(false).width('100%').height('100%')
    .vertical(false)
    .displayCount(1)
    .direction(this.pagedDirection === PagedReadingDirection.RIGHT_TO_LEFT ? Direction.Rtl : Direction.Ltr)
    .disableSwipe(this.pageZoomed)
    .duration(this.reducedMotion ? 0 : 180)
    .onChange((index: number) => this.updateSpread(index))
  }

  @Builder
  private continuousReader() {
    List({ space: this.pageSpacing, initialIndex: this.currentPage, scroller: this.listScroller }) {
      ForEach(this.orderedPages(), (page: KomgaBookPage, index: number) => {
        ListItem() {
          if (this.shouldLoadPage(index)) {
            RemotePage({ namespace: this.namespace, bookId: this.bookId, page: page.number,
              colorCorrection: this.colorCorrection,
              colorCorrectionRevision: this.colorCorrectionRevision,
              cropBorders: this.cropBorders,
              aiEnabled: this.aiEnabled,
              scaleType: LayoutScaleType.SCREEN,
              stretchSmallImages: this.stretchSmallImages,
              onPageTap: () => this.toggleChrome(),
              onZoomChange: (zoomed: boolean) => this.pageZoomed = zoomed })
          } else {
            Blank().width('100%').height('100%').backgroundColor(Color.Black)
          }
        }
        .width(this.continuousItemWidth(page)).height(this.continuousItemHeight(page))
      }, (page: KomgaBookPage) => `${page.number}:${this.cropBorders ? 1 : 0}:` +
        `${this.aiEnabled ? 1 : 0}:` +
        `${this.colorCorrection ? this.colorCorrectionRevision : 0}`)
    }
    .width('100%').height('100%')
    .padding(this.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM ? {
      left: this.viewportWidth * this.sidePadding,
      right: this.viewportWidth * this.sidePadding
    } : {
      top: this.viewportHeight * this.sidePadding,
      bottom: this.viewportHeight * this.sidePadding
    })
    .listDirection(this.continuousDirection === ContinuousReadingDirection.TOP_TO_BOTTOM ?
      Axis.Vertical : Axis.Horizontal)
    .direction(this.continuousDirection === ContinuousReadingDirection.RIGHT_TO_LEFT ?
      Direction.Rtl : Direction.Ltr)
    .edgeEffect(EdgeEffect.None).scrollBar(BarState.Off)
    .enableScrollInteraction(!this.pageZoomed)
    .onScrollIndex((start: number, _end: number, center: number) => this.updatePage(center < 0 ? start : center))
  }

  build() {
    Stack() {
      if (this.isLoading) {
        LoadingProgress().width(42).height(42).color(Color.White)
      } else {
        if (this.isContinuous()) {
          this.continuousReader()
        } else if (this.isPanels()) {
          this.panelsReader()
        } else {
          this.paginatedReader()
        }
      }

      if (this.chromeVisible) {
        Column() {
          Row() {
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.navigation_back')).width(24).height(24).fillColor(Color.White)
            }
            .width(48).height(48).backgroundColor('#66121217').onClick(() => router.back())
            .accessibilityText($r('app.string.back'))
            Column({ space: 2 }) {
              if (this.bookTitle.length > 0) {
                Text(this.bookTitle).fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)
                  .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
              }
              if (this.seriesTitle.length > 0 && this.seriesTitle !== this.bookTitle) {
                Text(this.seriesTitle).fontSize(12).fontColor('#C8FFFFFF')
                  .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
              }
            }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.nav_settings')).width(22).height(22).fillColor(Color.White)
                .hitTestBehavior(HitTestMode.None)
            }
            .width(44).height(44).backgroundColor('#66121217')
            .accessibilityText($r('app.string.settings'))
            .onClick(() => this.openSettings())
            .gesture(TapGesture({ fingers: 1, count: 1 }).onAction(() => this.openSettings()))
            Text(this.pageCounter())
              .fontSize(14).fontColor(Color.White).backgroundColor('#66121217')
              .padding({ left: 12, right: 12, top: 8, bottom: 8 }).borderRadius(16)
          }.width('100%').padding({ left: 12, right: 12, top: 12 })
          Blank()
            .hitTestBehavior(HitTestMode.Block)
            .onClick(() => this.toggleChrome())
          Row({ space: 12 }) {
            Text('‹').fontSize(32).fontColor(Color.White)
              .onClick(() => {
                this.goPrevious();
                this.scheduleHide();
              })
            Slider({ value: this.currentPage, min: 0, max: Math.max(0, this.pages.length - 1), step: 1 })
              .layoutWeight(1).selectedColor($r('app.color.primary'))
              .onChange((value: number, mode: SliderChangeMode) => {
                this.scheduleHide();
                if (mode === SliderChangeMode.End) {
                  const index = Math.round(value);
                  this.goToIndex(index);
                }
              })
            Text('›').fontSize(32).fontColor(Color.White)
              .onClick(() => {
                this.goNext();
                this.scheduleHide();
              })
          }
          .height(64).width('calc(100% - 24vp)').padding({ left: 16, right: 16 })
          .margin({ bottom: 14 }).backgroundColor('#E6181824').borderRadius(22)
        }
        .width('100%').height('100%')
        .hitTestBehavior(HitTestMode.Transparent)
      }
      if (this.flashVisible) {
        Blank().width('100%').height('100%')
          .backgroundColor(this.flashWhite ? Color.White : Color.Black)
          .hitTestBehavior(HitTestMode.None)
      }
      if (this.settingsVisible) {
        this.settingsOverlay()
      }
    }
    .width('100%').height('100%').backgroundColor(Color.Black)
    .onAreaChange((_, area) => {
      this.viewportWidth = Number(area.width);
      this.viewportHeight = Number(area.height);
    })
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
