import { KomeliaColors } from '../core/design/KomeliaDesign';
/*
 * Reader Kit integration follows Huawei's Apache-2.0 ReaderKit sample.
 * Komelia-specific download, progress and chrome are implemented here.
 */
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { display, router } from '@kit.ArkUI';
import { ReadPageComponent, bookParser, readerCore } from '@kit.ReaderKit';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { namespaceOf, OfflineMediaKind } from '../data/local/DatabaseModels';
import { offlineRepository, progressOutbox } from '../data/repository/DefaultRepositories';
import { epubLocatorMapper, shouldUseLocalEpubPosition } from '../domain/service/EpubLocatorMapper';
import { readerEngine } from '../domain/service/ReaderEngine';
import { calculateEpubViewport, normalizedEpubTopSpacing } from '../domain/service/EpubReaderLayout';
import { epubReadAloudService } from '../domain/service/EpubReadAloudService';
import { LatestAsyncTask } from '../domain/service/LatestAsyncTask';
import { SerialTaskGate } from '../domain/service/ReaderRequestToken';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';
import { isLocalBook, LOCAL_NAMESPACE } from '../model/LocalContentModels';
import { localLibraryService } from '../domain/service/LocalLibraryService';
import { localLibraryRepository } from '../data/repository/LocalLibraryRepository';
import { localSourcePath } from '../domain/service/LocalFilePlatform';
import { ReadiumLocator } from '../model/KomgaModels';
import { AppState } from '../state/AppState';

interface EpubReaderParams {
  bookId: string;
  initialPage: number;
  trackProgress?: boolean;
}

interface EpubPositionUpdate {
  resourceIndex: number;
  startDomPos: string;
  href: string;
  chapter: string;
  readDate: string;
}

@Entry
@Component
struct EpubReader {
  @State isLoading: boolean = true;
  @State chromeVisible: boolean = true;
  @State settingsVisible: boolean = false;
  @State catalogVisible: boolean = false;
  @State currentChapter: string = '';
  @State catalog: bookParser.CatalogItem[] = [];
  @State fontSize: number = 18;
  @State lineHeight: number = 1.8;
  @State horizontalMargin: number = 16;
  @State paginated: boolean = true;
  @State nightMode: boolean = false;
  @State keepScreenOn: boolean = false;
  @State immersiveMode: boolean = false;
  @State topSpacing: number = 8;
  private pageActive: boolean = false;
  private windowGate: SerialTaskGate = new SerialTaskGate();
  @State readAloudAvailable: boolean = false;
  @State readAloudBusy: boolean = false;
  private bookId: string = '';
  private localPath: string = '';
  private namespace: string = '';
  private trackProgress: boolean = true;
  private currentData: readerCore.PageDataInfo | null = null;
  private handler: bookParser.BookParserHandler | null = null;
  private controller: readerCore.ReaderComponentController = new readerCore.ReaderComponentController();
  private hideTimer: number = -1;
  private viewportWidthVp: number = 0;
  private viewportHeightVp: number = 0;
  private controllerReady: boolean = false;
  private positionWriter: LatestAsyncTask<EpubPositionUpdate> = new LatestAsyncTask<EpubPositionUpdate>(
    (position: EpubPositionUpdate) => this.persistPosition(position));
  private readerSetting: readerCore.ReaderSetting = {
    fontName: '',
    fontPath: '',
    fontSize: 18,
    fontColor: '#202027',
    fontWeight: 400,
    lineHeight: 1.8,
    nightMode: false,
    themeColor: '#FAF9FE',
    themeBgImg: '',
    flipMode: '0',
    scaledDensity: 1,
    viewPortWidth: 0,
    viewPortHeight: 0
  };

  aboutToAppear(): void {
    this.pageActive = true;
    const params = router.getParams() as EpubReaderParams | undefined;
    this.bookId = requiredRouteId(params?.bookId);
    this.trackProgress = params?.trackProgress !== false;
    const key = AppState.getInstance().namespace;
    this.namespace = isLocalBook(this.bookId) ? LOCAL_NAMESPACE : key === undefined ? '' : namespaceOf(key);
    this.readAloudAvailable = epubReadAloudService.isAvailable();
    this.registerCallbacks();
    if (this.bookId.length > 0) {
      this.prepareAndStart();
      this.scheduleHide();
    }
  }

  aboutToDisappear(): void {
    this.pageActive = false;
    this.applyWindowMode();
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    this.controller.off('pageShow');
    this.controller.off('resourceRequest');
    this.controllerReady = false;
    this.controller.releaseBook();
    epubReadAloudService.setArticleSelected(undefined);
    this.setKeepScreenOn(false);
  }

  private async setKeepScreenOn(enabled: boolean): Promise<void> {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const mainWindow = await context.windowStage.getMainWindow();
      await mainWindow.setWindowKeepScreenOn(enabled);
    } catch (_) {
    }
  }

  private async applyWindowMode(): Promise<void> {
    await this.windowGate.enter();
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const mainWindow = await context.windowStage.getMainWindow();
      const immersive = this.pageActive && this.immersiveMode;
      await mainWindow.setWindowLayoutFullScreen(immersive);
      await mainWindow.setWindowSystemBarEnable(immersive ? [] : ['status', 'navigation']);
    } catch (_) { }
    finally { this.windowGate.leave(); }
  }

  private syncViewportMetrics(): boolean {
    const screen = display.getDefaultDisplaySync();
    const metrics = calculateEpubViewport(this.viewportWidthVp, this.viewportHeightVp,
      this.horizontalMargin, screen.densityPixels, screen.scaledDensity, screen.width, screen.height, this.topSpacing);
    const changed = this.readerSetting.viewPortWidth !== metrics.widthPixels ||
      this.readerSetting.viewPortHeight !== metrics.heightPixels ||
      this.readerSetting.scaledDensity !== metrics.scaledDensity;
    this.readerSetting.viewPortWidth = metrics.widthPixels;
    this.readerSetting.viewPortHeight = metrics.heightPixels;
    this.readerSetting.scaledDensity = metrics.scaledDensity;
    return changed;
  }

  private async prepareAndStart(): Promise<void> {
    try {
      const readerPreferences = await Promise.all([
        AppPreferences.getString('epub.fontSize', '18'),
        AppPreferences.getString('epub.lineHeight', '1.8'),
        AppPreferences.getString('epub.margin', '16'),
        AppPreferences.getBoolean('epub.paginated', true),
        AppPreferences.getBoolean('epub.nightMode'),
        AppPreferences.getBoolean('epub.keepScreenOn'),
        AppPreferences.getBoolean('epub.immersiveMode'),
        AppPreferences.getNumber('epub.topSpacing', 8)
      ]);
      this.fontSize = Number(readerPreferences[0]);
      this.lineHeight = Number(readerPreferences[1]);
      this.horizontalMargin = Number(readerPreferences[2]);
      this.paginated = readerPreferences[3];
      this.nightMode = readerPreferences[4];
      this.keepScreenOn = readerPreferences[5];
      this.immersiveMode = readerPreferences[6];
      this.topSpacing = normalizedEpubTopSpacing(readerPreferences[7]);
      if (!this.pageActive) return;
      await this.applyWindowMode();
      this.setKeepScreenOn(this.keepScreenOn);
      this.readerSetting.fontSize = this.fontSize;
      this.readerSetting.lineHeight = this.lineHeight;
      this.readerSetting.flipMode = this.paginated ? '1' : '0';
      this.readerSetting.nightMode = this.nightMode;
      this.readerSetting.fontColor = this.nightMode ? '#F2EEF8' : '#202027';
      this.readerSetting.themeColor = this.nightMode ? '#121217' : '#FAF9FE';
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const directory = `${context.filesDir}/reader`;
      try {
        fs.mkdirSync(directory, true);
      } catch (_) {
      }
      const offline = await offlineRepository.getBook(this.namespace, this.bookId);
      this.localPath = offline !== undefined && offline.mediaKind === OfflineMediaKind.EPUB_FILE &&
        fs.accessSync(offline.localPath) ? offline.localPath : `${directory}/${this.bookId}.epub`;
      if (isLocalBook(this.bookId)) {
        this.localPath = localSourcePath((await localLibraryService.readableBook(this.bookId)).sourceUri);
      }
      if (!fs.accessSync(this.localPath)) {
        if (isLocalBook(this.bookId)) throw new Error('LOCAL_BOOK_SOURCE_UNAVAILABLE');
        const temporary = `${this.localPath}.partial`;
        await komgaClient.downloadBookFile(this.bookId, temporary);
        if (!fs.accessSync(temporary) || fs.statSync(temporary).size <= 0) {
          if (fs.accessSync(temporary)) fs.unlinkSync(temporary);
          throw new Error(localizedString($r('app.string.download_file_empty')));
        }
        fs.renameSync(temporary, this.localPath);
      }

      this.syncViewportMetrics();
      const hasLocalPosition = await AppPreferences.getBoolean(`epub.${this.bookId}.saved`);
      let savedResource = Number(await AppPreferences.getString(`epub.${this.bookId}.resource`, '0'));
      let savedDomPos = await AppPreferences.getString(`epub.${this.bookId}.domPos`, '');
      const localReadDate = await AppPreferences.getString(`epub.${this.bookId}.readDate`, '');
      const pendingProgress = await progressOutbox.pending(this.namespace, this.bookId);
      if (isLocalBook(this.bookId)) {
        const progress = await localLibraryRepository.progress(this.bookId);
        const localPosition = progress !== undefined && progress.locatorJson.length > 0 ?
          epubLocatorMapper.fromLocator(JSON.parse(progress.locatorJson) as ReadiumLocator) : undefined;
        savedResource = localPosition?.resourceIndex || 0;
        savedDomPos = localPosition?.domPos || '';
      }
      if (!isLocalBook(this.bookId) && AppState.getInstance().isOnline && pendingProgress === undefined) {
        try {
          const remote = await Promise.all([
            komgaClient.getReadiumProgression(this.bookId),
            komgaClient.getBook(this.bookId)
          ]);
          const serverPosition = epubLocatorMapper.fromLocator(remote[0]);
          const useLocal = shouldUseLocalEpubPosition({
            hasLocalPosition: hasLocalPosition,
            localReadDate: localReadDate,
            hasPendingUpload: false,
            hasServerPosition: serverPosition !== undefined,
            serverReadDate: remote[1].readProgress?.readDate || ''
          });
          if (!useLocal && serverPosition !== undefined) {
            savedResource = serverPosition.resourceIndex;
            savedDomPos = serverPosition.domPos;
          }
        } catch (_) {
        }
      }

      const values = await Promise.all([
        bookParser.getDefaultHandler(this.localPath),
        this.controller.init(context)
      ]);
      this.handler = values[0];
      this.controllerReady = true;
      this.controller.registerBookParser(this.handler);
      this.controller.setPageConfig(this.readerSetting);
      this.catalog = this.handler.getCatalogList() || [];
      this.currentChapter = this.chapterTitleForResource(savedResource);
      await this.controller.startPlay(savedResource, savedDomPos);
      if (epubReadAloudService.isActiveFor(this.bookId)) {
        epubReadAloudService.setArticleSelected((resourceIndex: number): void => {
          this.openSpeechArticle(resourceIndex);
        });
        const spokenResourceIndex = epubReadAloudService.activeResourceIndexFor(this.bookId);
        if (spokenResourceIndex !== undefined && spokenResourceIndex !== savedResource) {
          await this.openSpeechArticle(spokenResourceIndex);
        }
      }
    } catch (error) {
      this.currentChapter = localizedString($r('app.string.open_epub_failed'), (error as Error).message);
    } finally {
      this.isLoading = false;
    }
  }

  private registerCallbacks(): void {
    this.controller.on('resourceRequest', (filePath: string): ArrayBuffer => this.loadFile(filePath));
    this.controller.on('pageShow', (data: readerCore.PageDataInfo): void => {
      this.currentData = data;
      if (data.state === readerCore.PageState.PAGE_ON_SHOW) {
        this.isLoading = false;
        this.currentChapter = this.chapterTitleForResource(data.resourceIndex, data.pageHeaderContent);
        this.queuePosition(data);
      }
    });
  }

  private chapterTitleForResource(resourceIndex: number, pageHeader: string = ''): string {
    if (this.handler === null) return pageHeader;
    const spine = (this.handler.getSpineList() || []).find(value => value.index === resourceIndex);
    if (spine !== undefined) {
      const item = this.catalog.find((candidate: bookParser.CatalogItem) =>
        candidate.resourceFile === spine.href || candidate.idRef === spine.idRef);
      if (item !== undefined && item.catalogName.length > 0) return item.catalogName;
    }
    if (pageHeader.length > 0) return pageHeader;
    try {
      return this.handler.getBookInfo().bookTitle || '';
    } catch (_) {
      return '';
    }
  }

  private queuePosition(data: readerCore.PageDataInfo): void {
    if (!this.trackProgress) return;
    let href = '';
    if (this.handler !== null) {
      const spine = this.handler.getSpineList() || [];
      href = spine.find(value => value.index === data.resourceIndex)?.href || '';
    }
    this.positionWriter.submit({
      resourceIndex: data.resourceIndex,
      startDomPos: data.startDomPos || '',
      href: href,
      chapter: this.currentChapter,
      readDate: new Date().toISOString()
    });
  }

  private async persistPosition(position: EpubPositionUpdate): Promise<void> {
    await Promise.all([
      AppPreferences.putString(`epub.${this.bookId}.resource`, `${position.resourceIndex}`),
      AppPreferences.putString(`epub.${this.bookId}.domPos`, position.startDomPos),
      AppPreferences.putString(`epub.${this.bookId}.readDate`, position.readDate),
      AppPreferences.putBoolean(`epub.${this.bookId}.saved`, true)
    ]);
    await readerEngine.updateEpubProgress(this.namespace, this.bookId, position.resourceIndex,
      epubLocatorMapper.toLocator(position.resourceIndex, position.startDomPos, position.href, position.chapter));
  }

  private loadFile(filePath: string): ArrayBuffer {
    if (filePath.length === 0) return new ArrayBuffer(0);
    try {
      const stats = fs.statSync(filePath);
      const file = fs.openSync(filePath, fs.OpenMode.READ_ONLY);
      const buffer = new ArrayBuffer(stats.size);
      fs.readSync(file.fd, buffer);
      fs.closeSync(file);
      return buffer;
    } catch (_) {
      return new ArrayBuffer(0);
    }
  }

  private applySettings(): void {
    this.readerSetting.fontSize = this.fontSize;
    this.readerSetting.lineHeight = this.lineHeight;
    this.readerSetting.nightMode = this.nightMode;
    this.readerSetting.flipMode = this.paginated ? '1' : '0';
    this.readerSetting.fontColor = this.nightMode ? '#F2EEF8' : '#202027';
    this.readerSetting.themeColor = this.nightMode ? '#121217' : '#FAF9FE';
    this.syncViewportMetrics();
    try {
      this.controller.setPageConfig(this.readerSetting);
    } catch (_) {
    }
    AppPreferences.putString('epub.fontSize', `${this.fontSize}`);
    AppPreferences.putString('epub.lineHeight', `${this.lineHeight}`);
    AppPreferences.putString('epub.margin', `${this.horizontalMargin}`);
    AppPreferences.putBoolean('epub.paginated', this.paginated);
    AppPreferences.putBoolean('epub.nightMode', this.nightMode);
    AppPreferences.putBoolean('epub.keepScreenOn', this.keepScreenOn);
    AppPreferences.putBoolean('epub.immersiveMode', this.immersiveMode);
    AppPreferences.putNumber('epub.topSpacing', this.topSpacing);
    this.applyWindowMode();
    this.setKeepScreenOn(this.keepScreenOn);
  }

  private async openCatalogItem(item: bookParser.CatalogItem): Promise<void> {
    if (this.handler === null) return;
    const spine = (this.handler.getSpineList() || []).find(value => value.href === (item.resourceFile || ''));
    const domPos = this.handler.getDomPosByCatalogHref(item.href || '');
    await this.controller.startPlay(spine?.index || 0, domPos);
    this.catalogVisible = false;
  }

  private async openSpeechArticle(resourceIndex: number): Promise<void> {
    if (!this.controllerReady) return;
    try {
      await this.controller.startPlay(resourceIndex, '');
    } catch (_) {
    }
  }

  private async startReadAloud(): Promise<void> {
    if (this.readAloudBusy) return;
    if (!this.readAloudAvailable || this.handler === null) {
      this.getUIContext().getPromptAction().showToast({ message: $r('app.string.read_aloud_unavailable') });
      return;
    }
    this.readAloudBusy = true;
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      await epubReadAloudService.start(context, {
        bookId: this.bookId,
        currentResourceIndex: this.currentData?.resourceIndex || 0,
        handler: this.handler,
        catalog: this.catalog
      }, (resourceIndex: number): void => {
        this.openSpeechArticle(resourceIndex);
      });
      this.settingsVisible = false;
    } catch (error) {
      const message = error instanceof Error ? error.message : `${error}`;
      this.getUIContext().getPromptAction().showToast({
        message: localizedString($r('app.string.read_aloud_failed'), message)
      });
    } finally {
      this.readAloudBusy = false;
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

  @Builder
  private settingsSheet() {
    Scroll() {
    Column({ space: 18 }) {
      Row() {
        Text($r('app.string.reading_settings')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.done')).height(40).onClick(() => this.settingsVisible = false)
      }.width('100%')
      Text(localizedString($r('app.string.epub_font_size'), this.fontSize)).fontSize(15).width('100%')
      Slider({ value: this.fontSize, min: 14, max: 30, step: 1 })
        .selectedColor($r('app.color.primary'))
        .onChange((value: number) => { this.fontSize = value; this.applySettings(); })
      Text(localizedString($r('app.string.epub_line_height'), this.lineHeight.toFixed(1))).fontSize(15).width('100%')
      Slider({ value: this.lineHeight, min: 1.2, max: 2.4, step: 0.1 })
        .selectedColor($r('app.color.primary'))
        .onChange((value: number) => { this.lineHeight = value; this.applySettings(); })
      Text(localizedString($r('app.string.epub_page_margin'), this.horizontalMargin)).fontSize(15).width('100%')
      Slider({ value: this.horizontalMargin, min: 0, max: 48, step: 4 })
        .selectedColor($r('app.color.primary'))
        .onChange((value: number) => { this.horizontalMargin = value; this.applySettings(); })
      Row() {
        Text($r('app.string.epub_immersive')).fontSize(16).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.immersiveMode })
          .onChange((value: boolean) => { this.immersiveMode = value; this.applySettings(); })
      }.width('100%')
      Text($r('app.string.epub_safe_area_description')).fontSize(12).width('100%')
      Select([0, 8, 16, 24, 32, 48].map((value: number): SelectOption =>
        ({ value: localizedString($r('app.string.epub_top_spacing'), value) })))
        .selected([0, 8, 16, 24, 32, 48].indexOf(this.topSpacing)).width('100%')
        .onSelect((index: number) => { this.topSpacing = [0, 8, 16, 24, 32, 48][index]; this.applySettings(); })
      Row() {
        Text($r('app.string.horizontal_page_turning')).fontSize(16).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.paginated })
          .onChange((value: boolean) => { this.paginated = value; this.applySettings(); })
      }.width('100%')
      Row() {
        Text($r('app.string.dark_reading_background')).fontSize(16).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.nightMode })
          .onChange((value: boolean) => { this.nightMode = value; this.applySettings(); })
      }.width('100%')
      Row() {
        Text($r('app.string.keep_screen_on_while_reading')).fontSize(16).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.keepScreenOn })
          .onChange((value: boolean) => { this.keepScreenOn = value; this.applySettings(); })
      }.width('100%')
      Row({ space: 12 }) {
        Column({ space: 4 }) {
          Text($r('app.string.read_aloud')).fontSize(16).width('100%')
          Text(this.readAloudAvailable ? $r('app.string.read_aloud_description') :
            $r('app.string.read_aloud_unavailable'))
            .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button(this.readAloudBusy ? $r('app.string.preparing_read_aloud') : $r('app.string.start_read_aloud'))
          .height(44).enabled(this.readAloudAvailable && !this.readAloudBusy)
          .onClick(() => this.startReadAloud())
      }.width('100%')
    }
    .width('100%').padding(20).backgroundColor(KomeliaColors.surfaceHigh())
    .borderRadius({ topLeft: 24, topRight: 24 })
    }.width('100%').constraintSize({ maxHeight: '85%' })
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      ReadPageComponent({
        controller: this.controller,
        readerCallback: (error: BusinessError, controller: readerCore.ReaderComponentController) => {
          if (!error) this.controller = controller;
        }
      })
      .width('100%').height('100%')
      .padding({ left: this.horizontalMargin, right: this.horizontalMargin, top: this.topSpacing })
      .onClick(() => this.toggleChrome())

      if (this.isLoading) {
        Column({ space: 12 }) {
          LoadingProgress().width(38).height(38).color($r('app.color.primary'))
          Text($r('app.string.preparing_epub')).fontSize(14).fontColor($r('app.color.on_surface_variant'))
        }
        .width('100%').height('100%').justifyContent(FlexAlign.Center)
        .backgroundColor(this.nightMode ? '#121217' : '#FAF9FE')
      }

      if (this.chromeVisible && !this.settingsVisible && !this.catalogVisible) {
        Column() {
          Row() {
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.navigation_back')).width(24).height(24)
                .fillColor($r('app.color.on_surface'))
            }.width(48).height(48).backgroundColor(KomeliaColors.surfaceHigh()).onClick(() => router.back())
              .accessibilityText($r('app.string.back'))
            Text(this.currentChapter).fontSize(15).maxLines(1).layoutWeight(1)
          }
          .width('100%').height(60).padding({ left: 12, right: 12 })
          .backgroundColor(KomeliaColors.surfaceHigh())
          Blank()
          Row() {
            Button($r('app.string.catalog')).height(44).backgroundColor(Color.Transparent)
              .onClick(() => this.catalogVisible = true)
            Blank()
            Button($r('app.string.reading_settings')).height(44).backgroundColor(Color.Transparent)
              .onClick(() => this.settingsVisible = true)
          }
          .width('calc(100% - 24vp)').height(60).padding({ left: 10, right: 10 }).margin({ bottom: 14 })
          .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(20)
          .shadow({ radius: 18, color: '#33000000', offsetY: 6 })
        }.width('100%').height('100%')
      }

      if (this.catalogVisible) {
        Column() {
          Row() {
            Text($r('app.string.catalog')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
            Button($r('app.string.done')).height(40).onClick(() => this.catalogVisible = false)
          }.height(56).width('100%').padding({ left: 16, right: 16 })
          List() {
            ForEach(this.catalog, (item: bookParser.CatalogItem) => {
              ListItem() {
                Text(item.catalogName || localizedString($r('app.string.unnamed_chapter')))
                  .fontSize(15).maxLines(2).width('100%').padding({ left: 16, right: 16, top: 12, bottom: 12 })
              }.onClick(() => this.openCatalogItem(item))
            })
          }.layoutWeight(1).width('100%')
        }
        .width('100%').height('70%').backgroundColor(KomeliaColors.surfaceHigh())
        .borderRadius({ topLeft: 24, topRight: 24 })
      }

      if (this.settingsVisible) {
        this.settingsSheet()
      }
    }
    .width('100%').height('100%').backgroundColor(this.nightMode ? '#121217' : '#FAF9FE')
    .onAreaChange((_, area) => {
      this.viewportWidthVp = Number(area.width);
      this.viewportHeightVp = Number(area.height);
      if (this.syncViewportMetrics() && this.controllerReady) {
        try {
          this.controller.setPageConfig(this.readerSetting);
        } catch (_) {
        }
      }
    })
  }
}
