/*
 * Reader Kit integration follows Huawei's Apache-2.0 ReaderKit sample.
 * Komelia-specific download, progress and chrome are implemented here.
 */
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { display, router } from '@kit.ArkUI';
import { ReadPageComponent, bookParser, readerCore } from '@kit.ReaderKit';
import { komgaClient } from '../network/KomgaClient';
import { AppPreferences } from '../persistence/AppPreferences';

interface EpubReaderParams {
  bookId: string;
  initialPage: number;
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
  @State nightMode: boolean = false;
  private bookId: string = '';
  private localPath: string = '';
  private currentData: readerCore.PageDataInfo | null = null;
  private handler: bookParser.BookParserHandler | null = null;
  private controller: readerCore.ReaderComponentController = new readerCore.ReaderComponentController();
  private hideTimer: number = -1;
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
    const params = router.getParams() as EpubReaderParams;
    this.bookId = params.bookId;
    this.registerCallbacks();
    this.prepareAndStart();
    this.scheduleHide();
  }

  aboutToDisappear(): void {
    if (this.hideTimer >= 0) clearTimeout(this.hideTimer);
    this.controller.off('pageShow');
    this.controller.off('resourceRequest');
    this.controller.releaseBook();
  }

  private async prepareAndStart(): Promise<void> {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const directory = `${context.filesDir}/reader`;
      try {
        fs.mkdirSync(directory, true);
      } catch (_) {
      }
      this.localPath = `${directory}/${this.bookId}.epub`;
      if (!fs.accessSync(this.localPath)) {
        const bytes = await komgaClient.getBookFile(this.bookId);
        const file = fs.openSync(this.localPath,
          fs.OpenMode.WRITE_ONLY | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
        fs.writeSync(file.fd, bytes);
        fs.closeSync(file);
      }

      const screen = display.getDefaultDisplaySync();
      this.readerSetting.scaledDensity = screen.scaledDensity > 0 ? screen.scaledDensity : 1;
      this.readerSetting.viewPortWidth = screen.width;
      this.readerSetting.viewPortHeight = screen.height;
      const savedResource = Number(await AppPreferences.getString(`epub.${this.bookId}.resource`, '0'));
      const savedDomPos = await AppPreferences.getString(`epub.${this.bookId}.domPos`, '');

      const values = await Promise.all([
        bookParser.getDefaultHandler(this.localPath),
        this.controller.init(context)
      ]);
      this.handler = values[0];
      this.controller.registerBookParser(this.handler);
      this.controller.setPageConfig(this.readerSetting);
      this.catalog = this.handler.getCatalogList() || [];
      await this.controller.startPlay(savedResource, savedDomPos);
    } catch (error) {
      this.currentChapter = `打开 EPUB 失败：${(error as Error).message}`;
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
        this.persistPosition(data);
      }
    });
  }

  private async persistPosition(data: readerCore.PageDataInfo): Promise<void> {
    await Promise.all([
      AppPreferences.putString(`epub.${this.bookId}.resource`, `${data.resourceIndex}`),
      AppPreferences.putString(`epub.${this.bookId}.domPos`, data.domPos || '')
    ]);
    await komgaClient.updateReadProgress(this.bookId, data.resourceIndex, false);
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
    this.readerSetting.fontColor = this.nightMode ? '#F2EEF8' : '#202027';
    this.readerSetting.themeColor = this.nightMode ? '#121217' : '#FAF9FE';
    try {
      this.controller.setPageConfig(this.readerSetting);
    } catch (_) {
    }
  }

  private async openCatalogItem(item: bookParser.CatalogItem): Promise<void> {
    if (this.handler === null) return;
    const spine = (this.handler.getSpineList() || []).find(value => value.href === (item.resourceFile || ''));
    const domPos = this.handler.getDomPosByCatalogHref(item.href || '');
    await this.controller.startPlay(spine?.index || 0, domPos);
    this.catalogVisible = false;
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
    Column({ space: 18 }) {
      Row() {
        Text('阅读设置').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button('完成').height(40).onClick(() => this.settingsVisible = false)
      }.width('100%')
      Text(`字号 ${this.fontSize}`).fontSize(15).width('100%')
      Slider({ value: this.fontSize, min: 14, max: 30, step: 1 })
        .selectedColor($r('app.color.primary'))
        .onChange((value: number) => { this.fontSize = value; this.applySettings(); })
      Text(`行高 ${this.lineHeight.toFixed(1)}`).fontSize(15).width('100%')
      Slider({ value: this.lineHeight, min: 1.2, max: 2.4, step: 0.1 })
        .selectedColor($r('app.color.primary'))
        .onChange((value: number) => { this.lineHeight = value; this.applySettings(); })
      Row() {
        Text('深色阅读背景').fontSize(16).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.nightMode })
          .onChange((value: boolean) => { this.nightMode = value; this.applySettings(); })
      }.width('100%')
    }
    .width('100%').padding(20).backgroundColor($r('app.color.surface_high'))
    .borderRadius({ topLeft: 24, topRight: 24 })
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      ReadPageComponent({
        controller: this.controller,
        readerCallback: (error: BusinessError, controller: readerCore.ReaderComponentController) => {
          if (!error) this.controller = controller;
        }
      })
      .width('100%').height('100%').onClick(() => this.toggleChrome())

      if (this.isLoading) {
        Column({ space: 12 }) {
          LoadingProgress().width(38).height(38).color($r('app.color.primary'))
          Text('正在准备 EPUB…').fontSize(14).fontColor($r('app.color.on_surface_variant'))
        }
        .width('100%').height('100%').justifyContent(FlexAlign.Center)
        .backgroundColor($r('app.color.surface'))
      }

      if (this.chromeVisible && !this.settingsVisible && !this.catalogVisible) {
        Column() {
          Row() {
            Button({ type: ButtonType.Circle }) {
              Text('‹').fontSize(34)
            }.width(44).height(44).backgroundColor($r('app.color.surface_high')).onClick(() => router.back())
            Text(this.currentChapter).fontSize(15).maxLines(1).layoutWeight(1)
          }
          .width('100%').height(60).padding({ left: 12, right: 12 })
          .backgroundColor($r('app.color.surface_high'))
          Blank()
          Row() {
            Button('目录').height(44).backgroundColor(Color.Transparent).onClick(() => this.catalogVisible = true)
            Blank()
            Button('阅读设置').height(44).backgroundColor(Color.Transparent).onClick(() => this.settingsVisible = true)
          }
          .width('calc(100% - 24vp)').height(60).padding({ left: 10, right: 10 }).margin({ bottom: 14 })
          .backgroundColor($r('app.color.surface_high')).borderRadius(20)
          .shadow({ radius: 18, color: '#33000000', offsetY: 6 })
        }.width('100%').height('100%')
      }

      if (this.catalogVisible) {
        Column() {
          Row() {
            Text('目录').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
            Button('完成').height(40).onClick(() => this.catalogVisible = false)
          }.height(56).width('100%').padding({ left: 16, right: 16 })
          List() {
            ForEach(this.catalog, (item: bookParser.CatalogItem) => {
              ListItem() {
                Text(item.label || '未命名章节')
                  .fontSize(15).maxLines(2).width('100%').padding({ left: 16, right: 16, top: 12, bottom: 12 })
              }.onClick(() => this.openCatalogItem(item))
            })
          }.layoutWeight(1).width('100%')
        }
        .width('100%').height('70%').backgroundColor($r('app.color.surface_high'))
        .borderRadius({ topLeft: 24, topRight: 24 })
      }

      if (this.settingsVisible) {
        this.settingsSheet()
      }
    }
    .width('100%').height('100%').backgroundColor($r('app.color.surface'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
