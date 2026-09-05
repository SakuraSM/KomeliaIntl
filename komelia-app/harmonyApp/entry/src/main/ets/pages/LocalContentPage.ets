import { router } from '@kit.ArkUI';
import { AppState } from '../state/AppState';
import { AvailableBook, AvailableBookSource, LocalBookSort, availableBookKey, availableBookPage,
  isLocalBook } from '../model/LocalContentModels';
import { availableContentRepository } from '../data/repository/AvailableContentRepository';
import { localLibraryService } from '../domain/service/LocalLibraryService';
import { localFilePlatform } from '../domain/service/LocalFilePlatform';
import { KomeliaColors, KomeliaGrid, KomeliaLayout, WindowClass } from '../core/design/KomeliaDesign';
import { bookDetailPage } from '../core/navigation/CatalogNavigation';
import { localizedString } from '../core/i18n/LocalizedResources';
import { AppPreferences } from '../persistence/AppPreferences';
import { BookCard } from '../components/CoverCards';
import { EmptyState, LoadingState } from '../components/CommonUi';
import { namespaceOf } from '../data/local/DatabaseModels';

@Component
export struct LocalContentPage {
  @State state: AppState = AppState.getInstance();
  @Prop @Watch('onContentChanged') seriesId: string = '';
  @State values: AvailableBook[] = [];
  @State query: string = '';
  @State source: AvailableBookSource = AvailableBookSource.ALL;
  @State sort: LocalBookSort = LocalBookSort.RECENTLY_ADDED;
  @State page: number = 0;
  @State loading: boolean = true;
  @State busy: boolean = false;
  @State message: string = '';
  @State areaWidth: number = 360;
  @StorageProp('localContentRevision') @Watch('onContentChanged') revision: number = 0;
  private generation: number = 0;

  aboutToAppear(): void { this.restore().catch(() => { this.loading = false; }); }
  aboutToDisappear(): void { this.generation++; }
  private onContentChanged(): void { this.reload().catch(() => {}); }

  private async restore(): Promise<void> {
    const source = await AppPreferences.getString('local.source', AvailableBookSource.ALL);
    this.source = source === AvailableBookSource.LOCAL ? AvailableBookSource.LOCAL :
      source === AvailableBookSource.DOWNLOADED ? AvailableBookSource.DOWNLOADED : AvailableBookSource.ALL;
    if (this.seriesId.length > 0) this.source = AvailableBookSource.LOCAL;
    const sort = await AppPreferences.getString('local.sort', LocalBookSort.RECENTLY_ADDED);
    this.sort = sort === LocalBookSort.TITLE ? LocalBookSort.TITLE : sort === LocalBookSort.FILE_MODIFIED ?
      LocalBookSort.FILE_MODIFIED : LocalBookSort.RECENTLY_ADDED;
    await this.reload();
  }

  private async reload(): Promise<void> {
    const generation = ++this.generation;
    this.loading = true;
    try {
      const key = this.state.namespace;
      const books = await availableContentRepository.books(key === undefined ? '' : namespaceOf(key));
      if (generation !== this.generation) return;
      this.values = this.seriesId.length === 0 ? books : books.filter(value => value.book.seriesId === this.seriesId);
      this.page = this.result().page;
    } catch (_) {
      if (generation === this.generation) this.message = localizedString($r('app.string.local_operation_failed'));
    } finally { if (generation === this.generation) this.loading = false; }
  }

  private result() { return availableBookPage(this.values, this.source, this.query, this.sort, this.page); }

  private async add(folder: boolean): Promise<void> {
    if (this.busy) return;
    this.busy = true; this.message = '';
    try {
      const result = await localLibraryService.addSelected(folder);
      if (result.failed > 0) this.message = localizedString($r('app.string.local_scan_partial'));
      this.page = 0;
      await this.reload();
    } catch (_) { this.message = localizedString($r('app.string.local_operation_failed')); }
    finally { this.busy = false; }
  }

  private async refresh(): Promise<void> {
    if (this.busy) return;
    this.busy = true; this.message = '';
    try {
      const result = await localLibraryService.scanAll();
      if (result.failed > 0) this.message = localizedString($r('app.string.local_scan_partial'));
      await this.reload();
    } catch (_) { this.message = localizedString($r('app.string.local_operation_failed')); }
    finally { this.busy = false; }
  }

  private open(value: AvailableBook): void {
    if (isLocalBook(value.book.id)) {
      router.pushUrl({ url: bookDetailPage(value.book.oneshot, value.book.id), params: { bookId: value.book.id } });
    } else {
      // A completed offline copy remains readable even if its catalog detail was never cached.
      router.pushUrl({ url: value.book.media.mediaType === 'application/epub+zip' ? 'pages/EpubReader' : 'pages/ImageReader',
        params: { bookId: value.book.id, bookTitle: value.book.metadata.title, seriesTitle: value.book.seriesTitle,
          initialPage: Math.max(0, (value.book.readProgress?.page || 1) - 1) } });
    }
  }

  build() {
    Column({ space: 12 }) {
      Row({ space: 8 }) {
        if (this.seriesId.length > 0) {
          Button() { Image($r('app.media.navigation_back')).width(24).height(24) }.width(48).height(48)
            .backgroundColor(Color.Transparent).accessibilityText($r('app.string.back')).onClick(() => router.back())
        }
        Text($r('app.string.local_content')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.local_manage')).height(48).id('local_manage_libraries')
          .onClick(() => router.pushUrl({ url: 'pages/LocalLibraries' }))
      }.width('100%').height(56)
      if (this.seriesId.length === 0) {
        Row({ space: 8 }) {
          Button($r('app.string.local_add_folder')).height(48).layoutWeight(1).id('local_add_folder')
            .enabled(!this.busy && localFilePlatform.supportsFolders()).onClick(() => this.add(true))
          Button($r('app.string.local_import_files')).height(48).layoutWeight(1).id('local_import_files')
            .enabled(!this.busy).onClick(() => this.add(false))
        }.width('100%')
        if (!localFilePlatform.supportsFolders()) {
          Text($r('app.string.local_folder_unavailable')).fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
        }
      }
      TextInput({ text: this.query, placeholder: $r('app.string.search_hint') }).height(48).id('local_search')
        .onChange(value => { this.query = value; this.page = 0; })
      Row({ space: 8 }) {
        Select([{ value: $r('app.string.local_source_all') }, { value: $r('app.string.local_source_files') },
          { value: $r('app.string.local_source_downloaded') }])
          .selected(this.source === AvailableBookSource.ALL ? 0 : this.source === AvailableBookSource.LOCAL ? 1 : 2)
          .layoutWeight(1).height(48).id('local_source_filter')
          .onSelect(index => {
            this.source = index === 1 ? AvailableBookSource.LOCAL : index === 2 ? AvailableBookSource.DOWNLOADED : AvailableBookSource.ALL;
            this.page = 0; AppPreferences.putString('local.source', this.source).catch(() => {});
          })
        Select([{ value: $r('app.string.local_sort_added') }, { value: $r('app.string.local_sort_modified') },
          { value: $r('app.string.local_sort_title') }])
          .selected(this.sort === LocalBookSort.RECENTLY_ADDED ? 0 : this.sort === LocalBookSort.FILE_MODIFIED ? 1 : 2)
          .layoutWeight(1).height(48).id('local_sort')
          .onSelect(index => {
            this.sort = index === 1 ? LocalBookSort.FILE_MODIFIED : index === 2 ? LocalBookSort.TITLE : LocalBookSort.RECENTLY_ADDED;
            this.page = 0; AppPreferences.putString('local.sort', this.sort).catch(() => {});
          })
      }.width('100%')
      Row() {
        Text(localizedString($r('app.string.item_count'), this.result().totalElements)).layoutWeight(1)
        Button($r('app.string.refresh')).height(48).enabled(!this.busy).id('local_refresh').onClick(() => this.refresh())
      }.width('100%')
      if (this.message.length > 0) Text(this.message).fontSize(13).fontColor($r('app.color.error')).width('100%')
      if (this.loading && this.values.length === 0) {
        LoadingState()
      } else if (this.result().content.length === 0) {
        EmptyState($r('app.string.local_empty'))
      } else {
        Grid() {
          ForEach(this.result().content, (value: AvailableBook) => {
            GridItem() { BookCard({ book: value.book, supporting: true, onOpen: () => this.open(value) }) }
          }, (value: AvailableBook) => availableBookKey(value))
        }.columnsTemplate(KomeliaGrid.template(KomeliaGrid.columnCount(this.areaWidth,
          KomeliaLayout.windowClass(this.areaWidth), KomeliaGrid.defaultCardWidth)))
          .columnsGap(12).rowsGap(12).layoutWeight(1).width('100%').id('local_book_grid')
      }
      if (this.result().totalPages > 1) {
        Row({ space: 8 }) {
          Button($r('app.string.previous_page')).height(48).enabled(this.page > 0).onClick(() => this.page--)
          Text(localizedString($r('app.string.page_of'), this.page + 1, this.result().totalPages)).layoutWeight(1).textAlign(TextAlign.Center)
          Button($r('app.string.next_page')).height(48).enabled(this.page + 1 < this.result().totalPages).onClick(() => this.page++)
        }.width('100%')
      }
    }.width('100%').height('100%').padding({ left: 12, right: 12, bottom: 12 })
      .align(Alignment.Top).justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface()).id('local_page')
      .onAreaChange((_, area) => this.areaWidth = Number(area.width))
  }
}
