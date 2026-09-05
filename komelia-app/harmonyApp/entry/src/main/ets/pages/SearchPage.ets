import { router } from '@kit.ArkUI';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { KomeliaColors, KomeliaLayout, KomeliaLayoutSpec, WindowClass } from
  '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { bookDetailPage, seriesDetailPage } from '../core/navigation/CatalogNavigation';
import { availableContentRepository } from '../data/repository/AvailableContentRepository';
import { AppState } from '../state/AppState';
import { namespaceOf } from '../data/local/DatabaseModels';
import { SearchCoverage } from '../model/UnifiedContentModels';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { searchMetadataTagLimit, searchResultCardMinimumHeight, searchResultCoverHeight,
  searchResultCoverWidth, searchTypeBarHorizontalInset, summarizeSearchMetadata } from
  '../model/SearchPresentation';

const SEARCH_PAGE_SIZE: number = 10;
const SEARCH_DEBOUNCE_MILLIS: number = 500;

@Component
export struct SearchPage {
  @State query: string = '';
  @State selectedType: number = 0;
  @State isLoading: boolean = false;
  @State series: KomgaSeries[] = [];
  @State books: KomgaBook[] = [];
  @State seriesPage: number = 0;
  @State seriesTotalPages: number = 0;
  @State bookPage: number = 0;
  @State bookTotalPages: number = 0;
  @State errorMessage: string = '';
  @State bookCoverage: SearchCoverage = SearchCoverage.COMPLETE;
  @State seriesCoverage: SearchCoverage = SearchCoverage.COMPLETE;
  @StorageProp('localContentRevision') @Watch('onLocalContentChanged') localRevision: number = 0;
  @State windowClass: WindowClass = WindowClass.COMPACT;
  private preferredType: number = 0;
  private searchGeneration: number = 0;
  private debounceTimer: number = -1;

  aboutToAppear(): void {
    this.search();
  }

  aboutToDisappear(): void {
    if (this.debounceTimer >= 0) clearTimeout(this.debounceTimer);
    this.searchGeneration++;
  }

  private layout(): KomeliaLayoutSpec {
    return KomeliaLayout.spec(this.windowClass);
  }

  private scheduleSearch(value: string): void {
    this.query = value;
    this.searchGeneration++;
    if (this.debounceTimer >= 0) clearTimeout(this.debounceTimer);
    const delay = value.trim().length === 0 ? 0 : SEARCH_DEBOUNCE_MILLIS;
    this.debounceTimer = setTimeout(() => this.search(), delay);
  }

  private async search(): Promise<void> {
    if (this.debounceTimer >= 0) clearTimeout(this.debounceTimer);
    const generation = ++this.searchGeneration;
    const value = this.query.trim();
    this.seriesPage = 0;
    this.bookPage = 0;
    this.selectedType = this.preferredType;
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const state = AppState.getInstance();
      const namespace = state.namespace === undefined ? '' : namespaceOf(state.namespace);
      const results = await Promise.all([
        availableContentRepository.searchSeries(namespace, state.isOnline, value, 0, SEARCH_PAGE_SIZE),
        availableContentRepository.searchBooks(namespace, state.isOnline, value, 0, SEARCH_PAGE_SIZE)
      ]);
      if (generation !== this.searchGeneration) return;
      this.series = results[0].page.content;
      this.seriesTotalPages = results[0].page.totalPages;
      this.seriesCoverage = results[0].coverage;
      this.books = results[1].page.content;
      this.bookTotalPages = results[1].page.totalPages;
      this.bookCoverage = results[1].coverage;
      if (this.series.length === 0 && this.books.length > 0 && this.selectedType === 0) {
        this.selectedType = 1;
      }
    } catch (error) {
      if (generation !== this.searchGeneration) return;
      this.errorMessage = (error as Error).message || localizedString($r('app.string.search_failed'));
    } finally {
      if (generation === this.searchGeneration) this.isLoading = false;
    }
  }

  private async loadSeriesPage(page: number): Promise<void> {
    if (page < 0 || page >= this.seriesTotalPages) return;
    const generation = ++this.searchGeneration;
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const state = AppState.getInstance();
      const namespace = state.namespace === undefined ? '' : namespaceOf(state.namespace);
      const result = await availableContentRepository.searchSeries(namespace, state.isOnline, this.query.trim(), page, SEARCH_PAGE_SIZE);
      if (generation !== this.searchGeneration) return;
      this.series = result.page.content;
      this.seriesPage = result.page.number;
      this.seriesTotalPages = result.page.totalPages;
      this.seriesCoverage = result.coverage;
    } catch (error) {
      if (generation !== this.searchGeneration) return;
      this.errorMessage = (error as Error).message || localizedString($r('app.string.search_failed'));
    } finally {
      if (generation === this.searchGeneration) this.isLoading = false;
    }
  }

  private async loadBookPage(page: number): Promise<void> {
    if (page < 0 || page >= this.bookTotalPages) return;
    const generation = ++this.searchGeneration;
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const state = AppState.getInstance();
      const namespace = state.namespace === undefined ? '' : namespaceOf(state.namespace);
      const result = await availableContentRepository.searchBooks(namespace, state.isOnline, this.query.trim(), page, SEARCH_PAGE_SIZE);
      if (generation !== this.searchGeneration) return;
      this.books = result.page.content;
      this.bookPage = result.page.number;
      this.bookTotalPages = result.page.totalPages;
      this.bookCoverage = result.coverage;
    } catch (error) {
      if (generation !== this.searchGeneration) return;
      this.errorMessage = (error as Error).message || localizedString($r('app.string.search_failed'));
    } finally {
      if (generation === this.searchGeneration) this.isLoading = false;
    }
  }

  private onLocalContentChanged(): void { this.search(); }

  private coverage(): SearchCoverage { return this.selectedType === 0 ? this.seriesCoverage : this.bookCoverage; }

  private hasSeries(): boolean {
    return this.series.length > 0;
  }

  private hasBooks(): boolean {
    return this.books.length > 0;
  }

  private selectType(index: number): void {
    this.selectedType = index;
    this.preferredType = index;
  }

  @Builder
  private searchField() {
    Row() {
      TextInput({ placeholder: $r('app.string.search_hint'), text: this.query })
        .height(48)
        .layoutWeight(1)
        .fontSize(16)
        .backgroundColor(Color.Transparent)
        .border({ width: 0 })
        .enterKeyType(EnterKeyType.Search)
        .onChange((value: string) => this.scheduleSearch(value))
        .onSubmit(() => this.search())
      Image($r('app.media.nav_search'))
        .width(24)
        .height(24)
        .fillColor($r('app.color.on_surface_variant'))
        .margin({ right: 16 })
        .accessibilityText($r('app.string.search'))
        .onClick(() => this.search())
    }
    .height(48)
    .width('100%')
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(22)
    .margin({ top: 4 })
  }

  @Builder
  private searchTypeSegment(text: Resource, index: number) {
    Button(text)
      .height(48)
      .layoutWeight(1)
      .fontSize(14)
      .fontWeight(FontWeight.Medium)
      .fontColor(this.selectedType === index ? $r('app.color.on_surface') :
        $r('app.color.on_surface_variant'))
      .backgroundColor(this.selectedType === index ? $r('app.color.primary_container') :
        KomeliaColors.surfaceContainer())
      .borderRadius(12)
      .onClick(() => this.selectType(index))
  }

  @Builder
  private searchTypeBar() {
    if (this.hasSeries() || this.hasBooks()) {
      Row({ space: this.layout().controlSpacing }) {
        if (this.hasSeries()) this.searchTypeSegment($r('app.string.series'), 0)
        if (this.hasBooks()) this.searchTypeSegment($r('app.string.books'), 1)
      }
      .height(54)
      .width(`calc(100% - ${searchTypeBarHorizontalInset(this.windowClass)}vp)`)
      .constraintSize({ maxWidth: this.layout().contentMaxWidth })
      .padding(3)
      .backgroundColor(KomeliaColors.surfaceContainer())
      .border({ width: 1, color: $r('app.color.outline') })
      .borderRadius(16)
      .margin({ top: 8 })
    }
  }

  @Builder
  private unreadBadge(value: number) {
    if (value > 0) {
      Text(`${value}`)
        .fontSize(12)
        .fontColor($r('app.color.on_surface'))
        .backgroundColor($r('app.color.primary_container'))
        .padding({ left: 7, right: 7, top: 3, bottom: 3 })
        .borderRadius(12)
        .margin(6)
    }
  }

  @Builder
  private resultCover(endpoint: string, unreadCount: number, maxCardHeight: number) {
    Stack({ alignContent: Alignment.TopEnd }) {
      AuthenticatedImage({ endpoint: endpoint })
      this.unreadBadge(unreadCount)
    }
    .width(searchResultCoverWidth(this.windowClass))
    .height(searchResultCoverHeight(this.windowClass, maxCardHeight))
    .backgroundColor(KomeliaColors.surfaceContainer())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .clip(true)
  }

  @Builder
  private metadataChip(text: string) {
    Text(text)
      .fontSize(12)
      .fontColor($r('app.color.on_surface_variant'))
      .maxLines(1)
      .textOverflow({ overflow: TextOverflow.Ellipsis })
      .constraintSize({ maxWidth: 104 })
      .padding({ left: 9, right: 9, top: 5, bottom: 5 })
      .backgroundColor(KomeliaColors.surfaceContainer())
      .borderRadius(8)
      .margin({ right: 6, bottom: 6 })
  }

  @Builder
  private metadataTags(values: string[]) {
    Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
      ForEach(summarizeSearchMetadata(values, searchMetadataTagLimit(this.windowClass)).visible,
        (value: string) => this.metadataChip(value), (value: string) => value)
      if (summarizeSearchMetadata(values, searchMetadataTagLimit(this.windowClass)).hiddenCount > 0) {
        this.metadataChip(`+${summarizeSearchMetadata(values,
          searchMetadataTagLimit(this.windowClass)).hiddenCount}`)
      }
    }.width('100%').margin({ top: 8 })
  }

  @Builder
  private seriesResult(item: KomgaSeries) {
    Row({ space: this.layout().itemSpacing }) {
      this.resultCover(`/api/v1/series/${item.id}/thumbnail`, item.booksUnreadCount, 200)
      Column({ space: 4 }) {
        Text(item.metadata.title || item.name)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        this.metadataTags(item.metadata.genres)
        if (item.metadata.summary.length > 0) {
          Text(item.metadata.summary)
            .fontSize(13)
            .lineHeight(18)
            .fontColor($r('app.color.on_surface_variant'))
            .maxLines(4)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .constraintSize({ maxWidth: this.layout().contentMaxWidth,
      minHeight: searchResultCardMinimumHeight(this.windowClass, 200) })
    .padding(Math.max(0, this.layout().cardContentPadding - 1))
    .alignItems(VerticalAlign.Top)
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .onClick(() => router.pushUrl({ url: seriesDetailPage(item.oneshot, item.id), params: { seriesId: item.id } }))
  }

  @Builder
  private bookResult(item: KomgaBook) {
    Row({ space: this.layout().itemSpacing }) {
      this.resultCover(`/api/v1/books/${item.id}/thumbnail`, 0, 220)
      Column({ space: 4 }) {
        Text(item.metadata.title || item.name)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Text(localizedString($r('app.string.search_result_pages'), item.media.pagesCount))
          .fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .width('100%')
        this.metadataTags(item.metadata.tags)
        if (item.metadata.summary.length > 0) {
          Text(item.metadata.summary)
            .fontSize(13)
            .lineHeight(18)
            .fontColor($r('app.color.on_surface_variant'))
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .constraintSize({ maxWidth: this.layout().contentMaxWidth,
      minHeight: searchResultCardMinimumHeight(this.windowClass, 220) })
    .padding(Math.max(0, this.layout().cardContentPadding - 1))
    .alignItems(VerticalAlign.Top)
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .onClick(() => router.pushUrl({ url: bookDetailPage(item.oneshot, item.id), params: { bookId: item.id } }))
  }

  @Builder
  private pagination(currentPage: number, totalPages: number, onPageChange: (page: number) => void) {
    if (totalPages > 1) {
      Row({ space: this.layout().controlSpacing }) {
        Button($r('app.string.previous_page')).height(44).enabled(currentPage > 0)
          .onClick(() => onPageChange(currentPage - 1))
        Text(localizedString($r('app.string.page_of'), currentPage + 1, totalPages))
          .fontSize(14).fontColor($r('app.color.on_surface_variant'))
        Button($r('app.string.next_page')).height(44).enabled(currentPage + 1 < totalPages)
          .onClick(() => onPageChange(currentPage + 1))
      }.width('100%').justifyContent(FlexAlign.Center).alignItems(VerticalAlign.Center)
    }
  }

  @Builder
  private resultsList() {
    List({ space: this.layout().itemSpacing }) {
      if (this.selectedType === 0) {
        ForEach(this.series, (item: KomgaSeries) => {
          ListItem() {
            Row() { this.seriesResult(item) }
              .width('100%').justifyContent(FlexAlign.Center)
          }
        }, (item: KomgaSeries) => item.id)
        ListItem() {
          this.pagination(this.seriesPage, this.seriesTotalPages, (page: number) => this.loadSeriesPage(page))
        }
      } else {
        ForEach(this.books, (item: KomgaBook) => {
          ListItem() {
            Row() { this.bookResult(item) }
              .width('100%').justifyContent(FlexAlign.Center)
          }
        }, (item: KomgaBook) => item.id)
        ListItem() {
          this.pagination(this.bookPage, this.bookTotalPages, (page: number) => this.loadBookPage(page))
        }
      }
    }
    .layoutWeight(1)
    .width('100%')
    .padding({ left: this.layout().pageHorizontalPadding, right: this.layout().pageHorizontalPadding,
      bottom: this.layout().gridBottomPadding })
    .margin({ top: 8 })
  }

  build() {
    Column() {
      this.searchField()
      this.searchTypeBar()
      if (!this.isLoading && this.coverage() !== SearchCoverage.COMPLETE && !AppState.getInstance().isLocalMode) {
        Text(this.coverage() === SearchCoverage.OFFLINE_ONLY ? $r('app.string.local_search_offline') :
          $r('app.string.local_search_remote')).fontSize(13).fontColor($r('app.color.on_surface_variant'))
          .width('100%').padding(12).id('search_coverage')
      }
      if (this.isLoading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.search())
      } else if ((this.selectedType === 0 ? this.series.length : this.books.length) === 0) {
        EmptyState(this.query.length === 0 ? $r('app.string.start_search') : $r('app.string.no_match_results'))
      } else {
        this.resultsList()
      }
    }
    .width('100%')
    .height('100%')
    .id('search_page')
    .align(Alignment.Top)
    .justifyContent(FlexAlign.Start)
    .onAreaChange((_, area) => this.windowClass = KomeliaLayout.windowClass(Number(area.width)))
  }
}
