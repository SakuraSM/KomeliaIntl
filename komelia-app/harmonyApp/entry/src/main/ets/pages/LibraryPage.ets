import { router } from '@kit.ArkUI';
import { SeriesCard } from '../components/CoverCards';
import { EmptyState, LoadingState, PageTitle } from '../components/CommonUi';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { KomgaCollection, KomgaLibrary, KomgaReadList, KomgaSeries } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

enum LibrarySection { SERIES = 0, COLLECTIONS = 1, READ_LISTS = 2 }

@Component
export struct LibraryPage {
  @ObjectLink state: AppState;
  @State series: KomgaSeries[] = [];
  @State collections: KomgaCollection[] = [];
  @State readLists: KomgaReadList[] = [];
  @State isLoading: boolean = false;
  @State selectedLibrary: number = 0;
  @State selectedSection: LibrarySection = LibrarySection.SERIES;
  @State columns: number = 3;
  @State query: string = '';
  @State selecting: boolean = false;
  @State selectedSeriesIds: string[] = [];
  @State message: string = '';

  aboutToAppear(): void { this.load(); }

  private async load(): Promise<void> {
    this.isLoading = true;
    try {
      if (this.selectedSection === LibrarySection.COLLECTIONS) {
        this.collections = (await komgaClient.getCollections(this.query)).content;
      } else if (this.selectedSection === LibrarySection.READ_LISTS) {
        this.readLists = (await komgaClient.getReadLists(this.query)).content;
      } else {
        const page = await catalogRepository.series(this.query, 0, 100);
        if (this.selectedLibrary === 0) this.series = page.content;
        else {
          const libraryId = this.state.libraries[this.selectedLibrary - 1].id;
          this.series = page.content.filter((item: KomgaSeries) => item.libraryId === libraryId);
        }
      }
    } finally { this.isLoading = false; }
  }

  private resultCount(): number {
    if (this.selectedSection === LibrarySection.COLLECTIONS) return this.collections.length;
    if (this.selectedSection === LibrarySection.READ_LISTS) return this.readLists.length;
    return this.series.length;
  }

  private gridTemplate(): string {
    if (this.columns >= 7) return '1fr 1fr 1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }

  private toggleSeries(id: string): void {
    this.selectedSeriesIds = this.selectedSeriesIds.includes(id)
      ? this.selectedSeriesIds.filter((value: string) => value !== id)
      : this.selectedSeriesIds.concat([id]);
  }

  private async batchSeriesAction(action: string): Promise<void> {
    if (this.selectedSeriesIds.length === 0) return;
    this.message = `正在处理 ${this.selectedSeriesIds.length} 个系列…`;
    try {
      for (let index = 0; index < this.selectedSeriesIds.length; index++) {
        const id = this.selectedSeriesIds[index];
        if (action === 'read') await komgaClient.setSeriesReadStatus(id, true);
        else if (action === 'unread') await komgaClient.setSeriesReadStatus(id, false);
        else await komgaClient.runSeriesAction(id, action);
      }
      this.message = '批量操作已完成';
      this.selectedSeriesIds = [];
      this.selecting = false;
      await this.load();
    } catch (error) { this.message = (error as Error).message; }
  }

  @Builder
  private sectionButton(section: LibrarySection, label: string) {
    Button(label).height(44).borderRadius(14)
      .fontColor(this.selectedSection === section ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(this.selectedSection === section ? $r('app.color.primary_container') : Color.Transparent)
      .onClick(() => { this.selectedSection = section; this.load(); })
  }

  @Builder
  private namedItem(title: string, supporting: string, onOpen: () => void) {
    Row({ space: 12 }) {
      Column({ space: 4 }) {
        Text(title).fontSize(16).fontWeight(FontWeight.Medium).maxLines(2).width('100%')
        Text(supporting).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Text('›').fontSize(28).fontColor($r('app.color.on_surface_variant'))
    }.width('100%').minHeight(68).padding({ left: 16, right: 16, top: 10, bottom: 10 })
    .backgroundColor($r('app.color.surface_high')).borderRadius(16)
    .border({ width: 1, color: $r('app.color.outline') }).onClick(onOpen)
  }

  build() {
    Column({ space: 12 }) {
      PageTitle('书库', `${this.resultCount()} 项`)
      Row({ space: 8 }) {
        this.sectionButton(LibrarySection.SERIES, '系列')
        this.sectionButton(LibrarySection.COLLECTIONS, '收藏')
        this.sectionButton(LibrarySection.READ_LISTS, '阅读列表')
        Blank()
        if (this.selectedSection === LibrarySection.SERIES) {
          Button(this.selecting ? '取消' : '批量').height(40).backgroundColor(Color.Transparent)
            .fontColor($r('app.color.primary')).onClick(() => {
              this.selecting = !this.selecting;
              if (!this.selecting) this.selectedSeriesIds = [];
            })
        }
      }.width('100%')
      if (this.selecting) {
        Flex({ wrap: FlexWrap.Wrap, alignItems: ItemAlign.Center }) {
          Text(`已选 ${this.selectedSeriesIds.length} 项`).fontSize(14).margin({ right: 8, bottom: 8 })
          Button('标为已读').height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('read'))
          Button('标为未读').height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('unread'))
          Button('分析').height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('analyze'))
          Button('刷新元数据').height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('metadata/refresh'))
        }.width('100%').padding(10).backgroundColor($r('app.color.surface_high')).borderRadius(16)
      }
      Row({ space: 8 }) {
        if (this.selectedSection === LibrarySection.SERIES) {
          Select([{ value: '全部书库' }].concat(
            this.state.libraries.map((item: KomgaLibrary) => ({ value: item.name }))))
            .selected(this.selectedLibrary)
            .value(this.selectedLibrary === 0 ? '全部书库' : this.state.libraries[this.selectedLibrary - 1].name)
            .height(44).backgroundColor($r('app.color.surface_high'))
            .onSelect((index: number) => { this.selectedLibrary = index; this.load(); })
        }
        TextInput({ placeholder: '搜索当前分类', text: this.query }).layoutWeight(1).height(44)
          .backgroundColor($r('app.color.surface_high')).border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14).onChange((value: string) => this.query = value).onSubmit(() => this.load())
      }.width('100%')
      if (this.isLoading) { LoadingState() }
      else if (this.resultCount() === 0) { EmptyState('当前筛选下没有内容') }
      else if (this.selectedSection === LibrarySection.SERIES) {
        Grid() {
          ForEach(this.series, (item: KomgaSeries) => {
            GridItem() {
              Stack({ alignContent: Alignment.TopEnd }) {
                SeriesCard({ series: item, onOpen: (id: string) => {
                  if (this.selecting) this.toggleSeries(id);
                  else router.pushUrl({ url: 'pages/SeriesDetail', params: { seriesId: id } });
                } })
                if (this.selecting) {
                  Text(this.selectedSeriesIds.includes(item.id) ? '✓' : '○').fontSize(18)
                    .fontColor(this.selectedSeriesIds.includes(item.id) ? Color.White : $r('app.color.primary'))
                    .width(32).height(32).textAlign(TextAlign.Center)
                    .backgroundColor(this.selectedSeriesIds.includes(item.id) ? $r('app.color.primary') : '#EFFFFFFF')
                    .borderRadius(16).margin(8).onClick(() => this.toggleSeries(item.id))
                }
              }.width('100%')
            }
          }, (item: KomgaSeries) => item.id)
        }.columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12)
        .layoutWeight(1).width('100%').edgeEffect(EdgeEffect.Spring)
      } else if (this.selectedSection === LibrarySection.COLLECTIONS) {
        List({ space: 8 }) {
          ForEach(this.collections, (item: KomgaCollection) => {
            ListItem() { this.namedItem(item.name, `${item.seriesIds.length} 个系列`, () =>
              router.pushUrl({ url: 'pages/CollectionDetail', params: { collectionId: item.id, title: item.name } })) }
          }, (item: KomgaCollection) => item.id)
        }.layoutWeight(1).width('100%')
      } else {
        List({ space: 8 }) {
          ForEach(this.readLists, (item: KomgaReadList) => {
            ListItem() { this.namedItem(item.name, `${item.bookIds.length} 本书籍`, () =>
              router.pushUrl({ url: 'pages/ReadListDetail', params: { readListId: item.id, title: item.name } })) }
          }, (item: KomgaReadList) => item.id)
        }.layoutWeight(1).width('100%')
      }
      if (this.message.length > 0) {
        Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      }
    }.padding({ left: 12, right: 12, top: 16, bottom: 12 }).width('100%').height('100%')
    .onAreaChange((_, area) => {
      const width = Number(area.width);
      this.columns = width >= 1000 ? 7 : width >= 720 ? 5 : width >= 520 ? 4 : 3;
    })
  }
}
