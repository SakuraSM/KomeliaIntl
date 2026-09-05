import { KomeliaColors } from '../core/design/KomeliaDesign';
import { router } from '@kit.ArkUI';
import { SeriesCard } from '../components/CoverCards';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { KomeliaGrid, KomeliaLayout, KomeliaLayoutSpec, WindowClass } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { seriesDetailPage } from '../core/navigation/CatalogNavigation';
import { namespaceOf } from '../data/local/DatabaseModels';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { readerEngine } from '../domain/service/ReaderEngine';
import { KomgaAuthor, KomgaCollection, KomgaLibrary, KomgaReadList, KomgaSeries } from '../model/KomgaModels';
import { copyLibrarySeriesFilter, emptyLibrarySeriesFilter, LIBRARY_PAGE_SIZES,
  LibraryNavigationRequest, LibrarySeriesFilter, librarySectionVisibility, librarySeriesFilterCount,
  safeLibraryPageSizeIndex, safeSeriesSortIndex, sameAuthorFilter, SERIES_SORT_VALUES,
  toggleAuthorFilterValue, toggleFilterValue } from '../model/LibraryPresentation';
import { seriesPublicationStatusResource, SERIES_PUBLICATION_STATUS_VALUES } from '../model/SeriesPresentation';
import { komgaClient, SeriesFilterReferential, SeriesSearchOptions } from '../network/KomgaClient';
import { AppState } from '../state/AppState';
import { ArrayDataSource } from '../core/model/ArrayDataSource';

enum LibrarySection { SERIES = 0, COLLECTIONS = 1, READ_LISTS = 2 }

@Component
export struct LibraryPage {
  @ObjectLink state: AppState;
  @State series: KomgaSeries[] = [];
  @State collections: KomgaCollection[] = [];
  @State readLists: KomgaReadList[] = [];
  @State seriesPage: number = 0;
  @State seriesTotalPages: number = 0;
  @State seriesTotalCount: number = 0;
  @State collectionsCount: number = 0;
  @State readListsCount: number = 0;
  @State pageSize: number = 20;
  @State seriesSort: string = SERIES_SORT_VALUES[0];
  @State isLoading: boolean = false;
  @State selectedLibrary: number = 0;
  @State selectedSection: LibrarySection = LibrarySection.SERIES;
  @State areaWidth: number = 360;
  @StorageLink('gridCardWidth') cardWidth: number = KomeliaGrid.defaultCardWidth;
  @State query: string = '';
  @State selecting: boolean = false;
  @State selectedSeriesIds: string[] = [];
  @State message: string = '';
  @State supportingPane: boolean = false;
  @State compact: boolean = true;
  @State scopeSheetVisible: boolean = false;
  @State filterSheetVisible: boolean = false;
  @State draftQuery: string = '';
  @State draftSeriesSort: string = SERIES_SORT_VALUES[0];
  @State seriesFilter: LibrarySeriesFilter = emptyLibrarySeriesFilter();
  @State draftSeriesFilter: LibrarySeriesFilter = emptyLibrarySeriesFilter();
  @State filterReferential: SeriesFilterReferential = {
    genres: [], tags: [], releaseYears: [], ageRatings: [], publishers: [], languages: []
  };
  @State authorQuery: string = '';
  @State authorOptions: KomgaAuthor[] = [];
  @State filterOptionsLoading: boolean = false;
  @State errorMessage: string = '';
  @State windowClass: WindowClass = WindowClass.COMPACT;
  private seriesDataSource: ArrayDataSource<KomgaSeries> = new ArrayDataSource<KomgaSeries>();
  private collectionDataSource: ArrayDataSource<KomgaCollection> = new ArrayDataSource<KomgaCollection>();
  private readListDataSource: ArrayDataSource<KomgaReadList> = new ArrayDataSource<KomgaReadList>();
  private sectionCountsLoaded: boolean = false;
  private sectionCountsLoading: boolean = false;

  private layout(): KomeliaLayoutSpec {
    return KomeliaLayout.spec(this.windowClass);
  }

  private selectedLibraryId(): string | undefined {
    return this.selectedLibrary === 0 ? undefined : this.state.libraries[this.selectedLibrary - 1].id;
  }

  private seriesSearchOptions(): SeriesSearchOptions {
    return {
      libraryId: this.selectedLibraryId(),
      sort: this.seriesSort,
      readStatus: this.seriesFilter.readStatus,
      publicationStatus: this.seriesFilter.publicationStatus,
      includeGenres: this.seriesFilter.includeGenres,
      includeTags: this.seriesFilter.includeTags,
      excludeGenres: this.seriesFilter.excludeGenres,
      excludeTags: this.seriesFilter.excludeTags,
      inclusionMode: this.seriesFilter.inclusionMode,
      exclusionMode: this.seriesFilter.exclusionMode,
      authors: this.seriesFilter.authors,
      releaseYears: this.seriesFilter.releaseYears,
      ageRatings: this.seriesFilter.ageRatings,
      publishers: this.seriesFilter.publishers,
      languages: this.seriesFilter.languages,
      complete: this.seriesFilter.complete,
      oneshot: this.seriesFilter.oneshot
    };
  }

  aboutToAppear(): void {
    this.applyPendingNavigation(this.state.consumeLibraryNavigation());
    this.load();
  }

  private applyPendingNavigation(request?: LibraryNavigationRequest): void {
    if (request === undefined) return;
    const requestedLibrary = request.libraryId;
    const libraryIndex = requestedLibrary === undefined ? -1 :
      this.state.libraries.findIndex((library: KomgaLibrary): boolean => library.id === requestedLibrary);
    if (requestedLibrary !== undefined &&
      (libraryIndex < 0 || this.state.libraries[libraryIndex].unavailable)) {
      this.selectedLibrary = 0;
      this.message = localizedString($r('app.string.library_scope_fallback'));
    } else {
      this.selectedLibrary = libraryIndex + 1;
    }
    this.selectedSection = LibrarySection.SERIES;
    this.query = '';
    this.seriesSort = SERIES_SORT_VALUES[0];
    this.seriesFilter = copyLibrarySeriesFilter(request.filter);
    this.draftQuery = '';
    this.draftSeriesSort = this.seriesSort;
    this.draftSeriesFilter = copyLibrarySeriesFilter(request.filter);
    this.seriesPage = 0;
    this.scopeSheetVisible = false;
    this.filterSheetVisible = false;
  }

  private async load(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    this.refreshSectionCounts();
    try {
      if (this.selectedLibrary > this.state.libraries.length ||
        (this.selectedLibrary > 0 && this.state.libraries[this.selectedLibrary - 1].unavailable)) {
        this.selectedLibrary = 0;
        this.message = localizedString($r('app.string.library_scope_fallback'));
      }
      if (this.selectedSection === LibrarySection.COLLECTIONS) {
        const result = await komgaClient.getCollections(this.query, 0, 100);
        this.collections = result.content;
        this.collectionDataSource.replace(this.collections);
        this.collectionsCount = result.totalElements;
      } else if (this.selectedSection === LibrarySection.READ_LISTS) {
        const result = await komgaClient.getReadLists(this.query, 0, 100);
        this.readLists = result.content;
        this.readListDataSource.replace(this.readLists);
        this.readListsCount = result.totalElements;
      } else {
        const result = await catalogRepository.series(
          this.query, this.seriesPage, this.pageSize, this.seriesSearchOptions());
        this.series = result.content;
        this.seriesDataSource.replace(this.series);
        this.seriesPage = result.number;
        this.seriesTotalPages = result.totalPages;
        this.seriesTotalCount = result.totalElements;
      }
    } catch (error) {
      this.errorMessage = (error as Error).message || localizedString($r('app.string.library_load_failed'));
    } finally { this.isLoading = false; }
  }

  private async refreshSectionCounts(): Promise<void> {
    if (this.sectionCountsLoaded || this.sectionCountsLoading) return;
    this.sectionCountsLoading = true;
    try {
      const results = await Promise.all([
        komgaClient.getCollections('', 0, 1),
        komgaClient.getReadLists('', 0, 1)
      ]);
      this.collectionsCount = results[0].totalElements;
      this.readListsCount = results[1].totalElements;
      this.sectionCountsLoaded = true;
    } catch (_) {
      // The main series result remains usable when optional section summaries fail.
    } finally {
      this.sectionCountsLoading = false;
    }
  }

  private resultCount(): number {
    if (this.selectedSection === LibrarySection.COLLECTIONS) return this.collections.length;
    if (this.selectedSection === LibrarySection.READ_LISTS) return this.readLists.length;
    return this.series.length;
  }

  private showSeriesSection(): boolean {
    return librarySectionVisibility(this.collectionsCount, this.readListsCount).showSeries;
  }

  private showCollectionsSection(): boolean {
    return librarySectionVisibility(this.collectionsCount, this.readListsCount).showCollections;
  }

  private showReadListsSection(): boolean {
    return librarySectionVisibility(this.collectionsCount, this.readListsCount).showReadLists;
  }

  private gridTemplate(): string {
    return KomeliaGrid.template(this.gridColumnCount());
  }

  private gridColumnCount(): number {
    const contentWidth = this.supportingPane ? Math.max(0, this.areaWidth - 232) : this.areaWidth;
    return KomeliaGrid.columnCount(contentWidth, this.windowClass, this.cardWidth);
  }

  private toggleSeries(id: string): void {
    this.selectedSeriesIds = this.selectedSeriesIds.includes(id)
      ? this.selectedSeriesIds.filter((value: string) => value !== id)
      : this.selectedSeriesIds.concat([id]);
  }

  private async batchSeriesAction(action: string): Promise<void> {
    if (this.selectedSeriesIds.length === 0) return;
    this.message = localizedString($r('app.string.processing_series'), this.selectedSeriesIds.length);
    try {
      const key = this.state.namespace;
      const namespace = key === undefined ? undefined : namespaceOf(key);
      for (let index = 0; index < this.selectedSeriesIds.length; index++) {
        const id = this.selectedSeriesIds[index];
        if ((action === 'read' || action === 'unread') && !this.state.isOnline) {
          if (namespace === undefined) {
            throw new Error(localizedString($r('app.string.current_account_unavailable')));
          }
          const books = await catalogRepository.seriesBooks(id);
          for (let bookIndex = 0; bookIndex < books.length; bookIndex++) {
            const book = books[bookIndex];
            if (action === 'read') {
              await readerEngine.updateProgress(namespace, book.id, Math.max(1, book.media.pagesCount), true);
            } else {
              await readerEngine.clearProgress(namespace, book.id);
            }
          }
        } else if (action === 'read') {
          await komgaClient.setSeriesReadStatus(id, true);
        } else if (action === 'unread') {
          await komgaClient.setSeriesReadStatus(id, false);
        } else {
          await komgaClient.runSeriesAction(id, action);
        }
      }
      this.message = localizedString($r('app.string.batch_completed'));
      const completed = action === 'read';
      if (!this.state.isOnline && (action === 'read' || action === 'unread')) {
        for (let index = 0; index < this.series.length; index++) {
          const item = this.series[index];
          if (!this.selectedSeriesIds.includes(item.id)) continue;
          item.booksReadCount = completed ? item.booksCount : 0;
          item.booksUnreadCount = completed ? 0 : item.booksCount;
          item.booksInProgressCount = 0;
        }
        this.series = this.series.slice();
      }
      this.selectedSeriesIds = [];
      this.selecting = false;
      if (this.state.isOnline || (action !== 'read' && action !== 'unread')) await this.load();
    } catch (error) { this.message = (error as Error).message; }
  }

  private openBatchCollection(): void {
    if (this.selectedSeriesIds.length === 0) return;
    router.pushUrl({
      url: 'pages/AddToGroup',
      params: { kind: 'collection', targetIds: this.selectedSeriesIds.slice() }
    });
  }

  private async downloadSelectedSeries(): Promise<void> {
    const key = this.state.namespace;
    if (key === undefined || !this.state.isOnline || this.selectedSeriesIds.length === 0) return;
    const selected = this.selectedSeriesIds.slice();
    this.message = localizedString($r('app.string.preparing_batch_download'));
    try {
      const bookIds: string[] = [];
      for (let seriesIndex = 0; seriesIndex < selected.length; seriesIndex += 1) {
        const books = await catalogRepository.seriesBooks(selected[seriesIndex]);
        for (let bookIndex = 0; bookIndex < books.length; bookIndex += 1) {
          if (!bookIds.includes(books[bookIndex].id)) bookIds.push(books[bookIndex].id);
        }
      }
      this.selectedSeriesIds = [];
      this.selecting = false;
      this.message = localizedString($r('app.string.downloads_queued'), bookIds.length);
      for (let index = 0; index < bookIds.length; index += 1) {
        await offlineDownloadService.download(namespaceOf(key), bookIds[index]);
      }
      this.message = localizedString($r('app.string.batch_download_completed'), bookIds.length);
    } catch (error) {
      this.message = localizedString($r('app.string.batch_download_failed'), (error as Error).message);
    }
  }

  @Builder
  private sectionButton(section: LibrarySection, label: ResourceStr) {
    Button(label).height(48).borderRadius(14)
      .fontColor(this.selectedSection === section ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(this.selectedSection === section ? $r('app.color.primary_container') : Color.Transparent)
      .onClick(() => { this.selectedSection = section; this.seriesPage = 0; this.load(); })
  }

  @Builder
  private libraryScopeButton() {
    Button() {
      Row({ space: 8 }) {
        Text(this.selectedLibrary === 0 ? localizedString($r('app.string.all_libraries')) :
          this.state.libraries[this.selectedLibrary - 1].name)
          .fontSize(14).fontWeight(FontWeight.Medium).fontColor($r('app.color.on_surface_variant'))
          .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
        Image($r('app.media.library_expand_more')).width(24).height(24)
          .fillColor($r('app.color.on_surface_variant'))
      }
    }
    .height(48)
    .constraintSize({ maxWidth: this.compact ? 180 : 280 })
    .padding({ left: 24, right: 14 })
    .backgroundColor(Color.Transparent)
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(24)
    .translate({ x: -this.layout().pageHorizontalPadding })
    .onClick(() => {
      this.filterSheetVisible = false;
      this.scopeSheetVisible = true;
    })
    .bindSheet($$this.scopeSheetVisible, this.libraryScopeSheet(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      showClose: false
    })
  }

  private pageSizeOptions(): SelectOption[] {
    return LIBRARY_PAGE_SIZES.map((value: number): SelectOption => ({ value: `${value}` }));
  }

  private seriesSortOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.sort_title_ascending')) },
      { value: localizedString($r('app.string.sort_title_descending')) },
      { value: localizedString($r('app.string.sort_updated_descending')) },
      { value: localizedString($r('app.string.sort_updated_ascending')) },
      { value: localizedString($r('app.string.sort_added_descending')) },
      { value: localizedString($r('app.string.sort_added_ascending')) }
    ];
  }

  private openFilters(): void {
    this.scopeSheetVisible = false;
    this.draftQuery = this.query;
    this.draftSeriesSort = this.seriesSort;
    this.draftSeriesFilter = copyLibrarySeriesFilter(this.seriesFilter);
    this.authorQuery = '';
    this.authorOptions = [];
    this.filterSheetVisible = true;
    this.loadFilterReferential();
  }

  private async loadFilterReferential(): Promise<void> {
    this.filterOptionsLoading = true;
    try {
      this.filterReferential = await komgaClient.getSeriesFilterReferential(this.selectedLibraryId());
    } catch (error) {
      this.message = localizedString($r('app.string.filter_options_load_failed'), (error as Error).message);
    } finally {
      this.filterOptionsLoading = false;
    }
  }

  private async searchAuthorOptions(): Promise<void> {
    const query = this.authorQuery.trim();
    if (query.length === 0) {
      this.authorOptions = [];
      return;
    }
    try {
      const authors = await komgaClient.searchAuthors(query, this.selectedLibraryId());
      this.authorOptions = authors.filter((author: KomgaAuthor, index: number, values: KomgaAuthor[]): boolean =>
        values.findIndex((candidate: KomgaAuthor): boolean => sameAuthorFilter(candidate, author)) === index);
    } catch (error) {
      this.message = localizedString($r('app.string.filter_options_load_failed'), (error as Error).message);
    }
  }

  private updateDraftFilter(update: (value: LibrarySeriesFilter) => void): void {
    const next = copyLibrarySeriesFilter(this.draftSeriesFilter);
    update(next);
    this.draftSeriesFilter = next;
  }

  private async applyFilters(): Promise<void> {
    this.query = this.draftQuery.trim();
    this.seriesSort = this.draftSeriesSort;
    this.seriesFilter = copyLibrarySeriesFilter(this.draftSeriesFilter);
    this.seriesPage = 0;
    this.filterSheetVisible = false;
    await this.load();
  }

  private async resetFilters(): Promise<void> {
    this.draftQuery = '';
    this.draftSeriesSort = SERIES_SORT_VALUES[0];
    this.query = '';
    this.seriesSort = SERIES_SORT_VALUES[0];
    this.seriesFilter = emptyLibrarySeriesFilter();
    this.draftSeriesFilter = emptyLibrarySeriesFilter();
    this.seriesPage = 0;
    this.filterSheetVisible = false;
    await this.load();
  }

  private readStatusLabel(value: string): ResourceStr {
    if (value === 'READ') return $r('app.string.filter_read');
    if (value === 'IN_PROGRESS') return $r('app.string.filter_in_progress');
    return $r('app.string.unread');
  }

  private booleanFilterOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.any')) },
      { value: localizedString($r('app.string.yes')) },
      { value: localizedString($r('app.string.no')) }
    ];
  }

  private booleanFilterIndex(value: string): number {
    return value === 'YES' ? 1 : value === 'NO' ? 2 : 0;
  }

  private booleanFilterValue(index: number): string {
    return index === 1 ? 'YES' : index === 2 ? 'NO' : 'ANY';
  }

  private matchModeOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.filter_match_all')) },
      { value: localizedString($r('app.string.filter_match_any')) }
    ];
  }

  private filterValueLabel(value: string): ResourceStr {
    return value === 'None' ? $r('app.string.filter_none') : value;
  }

  @Builder
  private filterChip(id: string, label: ResourceStr, selected: boolean, excluded: boolean, onToggle: () => void) {
    Toggle({ type: ToggleType.Button, isOn: selected }) {
      Text(label)
        .fontSize(13)
        .fontColor(excluded ? $r('app.color.error') :
          selected ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
        .textAlign(TextAlign.Center)
    }
      .id(id)
      .height(40).padding({ left: 12, right: 12 })
      .backgroundColor(selected ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .border({ width: 1, color: excluded ? $r('app.color.error') : $r('app.color.outline') })
      .borderRadius(12).margin({ right: 8, bottom: 8 })
      .focusable(true)
      .onChange((isOn: boolean) => {
        if (isOn !== selected) onToggle();
      })
  }

  @Builder
  private stringFilterGroup(title: ResourceStr, options: string[], selected: string[], excluded: boolean,
    onToggle: (value: string) => void) {
    Column({ space: 8 }) {
      Text(title).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      if (options.length === 0) {
        Text(this.filterOptionsLoading ? $r('app.string.loading') : $r('app.string.no_options'))
          .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      } else {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(options, (value: string) => {
            this.filterChip(`library_filter_${excluded ? 'exclude' : 'include'}_${value}`,
              this.filterValueLabel(value), selected.includes(value), excluded, () => onToggle(value))
          }, (value: string) => value)
        }.width('100%')
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private readStatusFilterGroup() {
    Column({ space: 8 }) {
      Text($r('app.string.filter_read_status')).fontSize(13)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Flex({ wrap: FlexWrap.Wrap }) {
        ForEach(['UNREAD', 'IN_PROGRESS', 'READ'], (value: string) => {
          this.filterChip(`library_filter_read_${value}`, this.readStatusLabel(value),
            this.draftSeriesFilter.readStatus.includes(value), false,
            () => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
              filter.readStatus = toggleFilterValue(filter.readStatus, value)))
        })
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private publicationStatusFilterGroup() {
    Column({ space: 8 }) {
      Text($r('app.string.filter_publication_status')).fontSize(13)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Flex({ wrap: FlexWrap.Wrap }) {
        ForEach(SERIES_PUBLICATION_STATUS_VALUES, (value: string) => {
          this.filterChip(`library_filter_publication_${value}`, seriesPublicationStatusResource(value),
            this.draftSeriesFilter.publicationStatus.includes(value), false,
            () => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
              filter.publicationStatus = toggleFilterValue(filter.publicationStatus, value)))
        })
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private labeledFilterSelect(label: ResourceStr, options: SelectOption[], selected: number,
    onSelect: (index: number) => void) {
    Column({ space: 8 }) {
      Text(label).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      Select(options).selected(selected).value(options[selected].value).height(48).width('100%')
        .backgroundColor(KomeliaColors.surfaceContainer())
        .onSelect((index: number) => onSelect(index))
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private booleanFilterSelect(label: ResourceStr, value: string, componentId: string,
    onSelect: (value: string) => void) {
    Column({ space: 8 }) {
      Text(label).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      Row({ space: 8 }) {
        Button() {
          Text($r('app.string.any')).fontColor(value === 'ANY' ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
        }.id(`${componentId}_ANY`).height(48).layoutWeight(1).borderRadius(14)
          .backgroundColor(value === 'ANY' ? $r('app.color.primary_container') :
            KomeliaColors.surfaceContainer())
          .accessibilityText($r('app.string.any')).onClick(() => onSelect('ANY'))
        Button() {
          Text($r('app.string.yes')).fontColor(value === 'YES' ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
        }.id(`${componentId}_YES`).height(48).layoutWeight(1).borderRadius(14)
          .backgroundColor(value === 'YES' ? $r('app.color.primary_container') :
            KomeliaColors.surfaceContainer())
          .accessibilityText($r('app.string.yes')).onClick(() => onSelect('YES'))
        Button() {
          Text($r('app.string.no')).fontColor(value === 'NO' ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
        }.id(`${componentId}_NO`).height(48).layoutWeight(1).borderRadius(14)
          .backgroundColor(value === 'NO' ? $r('app.color.primary_container') :
            KomeliaColors.surfaceContainer())
          .accessibilityText($r('app.string.no')).onClick(() => onSelect('NO'))
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private authorFilterGroup() {
    Column({ space: 8 }) {
      Text($r('app.string.filter_authors')).fontSize(13)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Row({ space: 8 }) {
        TextInput({ text: this.authorQuery, placeholder: $r('app.string.filter_author_search') })
          .height(48).layoutWeight(1).backgroundColor(KomeliaColors.surfaceContainer())
          .onChange((value: string) => this.authorQuery = value)
          .onSubmit(() => this.searchAuthorOptions())
        Button($r('app.string.search')).height(48).onClick(() => this.searchAuthorOptions())
      }.width('100%')
      if (this.draftSeriesFilter.authors.length > 0) {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(this.draftSeriesFilter.authors, (value: KomgaAuthor, index: number) => {
            this.filterChip(`library_filter_author_selected_${index}`, value.name, true, false,
              () => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
              filter.authors = toggleAuthorFilterValue(filter.authors, value)))
          }, (value: KomgaAuthor) => `selected-${value.name}-${value.role}`)
        }.width('100%')
      }
      if (this.authorOptions.length > 0) {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(this.authorOptions, (value: KomgaAuthor, index: number) => {
            this.filterChip(`library_filter_author_option_${index}`, value.name,
              this.draftSeriesFilter.authors.some((candidate: KomgaAuthor): boolean =>
                sameAuthorFilter(candidate, value)), false,
              () => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
                filter.authors = toggleAuthorFilterValue(filter.authors, value)))
          }, (value: KomgaAuthor) => `option-${value.name}-${value.role}`)
        }.width('100%')
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  private selectLibrary(index: number): void {
    this.selectedLibrary = index;
    this.seriesPage = 0;
    this.scopeSheetVisible = false;
    this.load();
  }

  @Builder
  private namedItem(title: string, supporting: ResourceStr, onOpen: () => void) {
    Row({ space: 12 }) {
      Column({ space: 4 }) {
        Text(title).fontSize(16).fontWeight(FontWeight.Medium).maxLines(2).width('100%')
        Text(supporting).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Text('›').fontSize(28).fontColor($r('app.color.on_surface_variant'))
    }.width('100%').constraintSize({ minHeight: 68 }).padding({ left: 16, right: 16, top: 10, bottom: 10 })
    .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
    .border({ width: 1, color: $r('app.color.outline') }).onClick(onOpen)
  }

  @Builder
  private libraryScopeItem(index: number, label: ResourceStr, unavailable: boolean = false) {
    Row({ space: 10 }) {
      Text(index === 0 ? '▦' : '▤').fontSize(20)
        .fontColor(this.selectedLibrary === index ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      Column({ space: 2 }) {
        Text(label).fontSize(14).fontWeight(this.selectedLibrary === index ? FontWeight.Bold : FontWeight.Regular)
          .maxLines(2).width('100%')
        if (unavailable) {
          Text($r('app.string.currently_unavailable')).fontSize(11).fontColor($r('app.color.error')).width('100%')
        }
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
    }.id(`library_scope_item_${index}`)
      .height(56).width('100%').padding({ left: 12, right: 12 })
      .backgroundColor(this.selectedLibrary === index ? $r('app.color.primary_container') : Color.Transparent)
      .borderRadius(14).onClick(() => this.selectLibrary(index))
  }

  @Builder
  private libraryScopePane() {
    Column({ space: 8 }) {
      this.libraryScopeItem(0, $r('app.string.all_libraries'))
      List({ space: 4 }) {
        ForEach(this.state.libraries, (library: KomgaLibrary, index: number) => {
          ListItem() { this.libraryScopeItem(index + 1, library.name, library.unavailable) }
        }, (library: KomgaLibrary) => library.id)
      }.layoutWeight(1).width('100%')
      if (this.state.canManageServer()) {
        Button($r('app.string.manage_libraries')).height(44).width('100%')
          .backgroundColor(KomeliaColors.surfaceHigh())
          .fontColor($r('app.color.primary')).onClick(() => router.pushUrl({ url: 'pages/Management' }))
      }
    }.id('library_scope_supporting_pane')
      .width(232).height('100%').padding({ left: 24, right: 24, top: 24, bottom: 24 })
      .backgroundColor(KomeliaColors.surfaceContainer())
  }

  @Builder
  private libraryScopeSheet() {
    Column({ space: 8 }) {
      Row() {
        Text($r('app.string.choose_library')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.done')).height(40).onClick(() => this.scopeSheetVisible = false)
      }.height(56).width('100%')
      this.libraryScopeItem(0, $r('app.string.all_libraries'))
      List({ space: 4 }) {
        ForEach(this.state.libraries, (library: KomgaLibrary, index: number) => {
          ListItem() {
            Row() {
              this.libraryScopeItem(index + 1, library.name, library.unavailable)
            }.onClick(() => this.scopeSheetVisible = false)
          }
        }, (library: KomgaLibrary) => library.id)
      }.layoutWeight(1).width('100%')
    }.id('library_scope_sheet')
      .padding({ left: 16, right: 16, bottom: 24 }).width('100%').height('100%')
      .backgroundColor(KomeliaColors.surfaceHigh())
  }

  @Builder
  private seriesFilterSheet() {
    Column({ space: this.layout().controlSpacing }) {
      Row() {
        Text($r('app.string.filters')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.reset_filters')).height(40).backgroundColor(Color.Transparent)
          .id('library_filter_reset')
          .fontColor($r('app.color.primary')).onClick(() => this.resetFilters())
      }.height(56).width('100%')
      Scroll() {
        Column({ space: this.layout().sectionSpacing }) {
          TextInput({ placeholder: $r('app.string.search_current_category'), text: this.draftQuery })
            .id('library_filter_query')
            .height(48).width('100%').fontSize(16)
            .backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') })
            .borderRadius(14)
            .onChange((value: string) => this.draftQuery = value)
          this.labeledFilterSelect($r('app.string.sort_order'), this.seriesSortOptions(),
            safeSeriesSortIndex(this.draftSeriesSort),
            (index: number) => this.draftSeriesSort = SERIES_SORT_VALUES[index])
          this.readStatusFilterGroup()
          this.publicationStatusFilterGroup()
          this.labeledFilterSelect($r('app.string.filter_inclusion_mode'), this.matchModeOptions(),
            this.draftSeriesFilter.inclusionMode === 'ANY' ? 1 : 0,
            (index: number) => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
              filter.inclusionMode = index === 1 ? 'ANY' : 'ALL'))
          this.stringFilterGroup($r('app.string.filter_include_genres'), this.filterReferential.genres,
            this.draftSeriesFilter.includeGenres, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => {
                filter.includeGenres = toggleFilterValue(filter.includeGenres, value);
                if (filter.includeGenres.includes(value)) {
                  filter.excludeGenres = filter.excludeGenres.filter((item: string) => item !== value);
                }
              }))
          this.stringFilterGroup($r('app.string.filter_include_tags'), this.filterReferential.tags,
            this.draftSeriesFilter.includeTags, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => {
                filter.includeTags = toggleFilterValue(filter.includeTags, value);
                if (filter.includeTags.includes(value)) {
                  filter.excludeTags = filter.excludeTags.filter((item: string) => item !== value);
                }
              }))
          this.labeledFilterSelect($r('app.string.filter_exclusion_mode'), this.matchModeOptions(),
            this.draftSeriesFilter.exclusionMode === 'ALL' ? 0 : 1,
            (index: number) => this.updateDraftFilter((filter: LibrarySeriesFilter) =>
              filter.exclusionMode = index === 0 ? 'ALL' : 'ANY'))
          this.stringFilterGroup($r('app.string.filter_exclude_genres'), this.filterReferential.genres,
            this.draftSeriesFilter.excludeGenres, true, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => {
                filter.excludeGenres = toggleFilterValue(filter.excludeGenres, value);
                if (filter.excludeGenres.includes(value)) {
                  filter.includeGenres = filter.includeGenres.filter((item: string) => item !== value);
                }
              }))
          this.stringFilterGroup($r('app.string.filter_exclude_tags'), this.filterReferential.tags,
            this.draftSeriesFilter.excludeTags, true, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => {
                filter.excludeTags = toggleFilterValue(filter.excludeTags, value);
                if (filter.excludeTags.includes(value)) {
                  filter.includeTags = filter.includeTags.filter((item: string) => item !== value);
                }
              }))
          this.authorFilterGroup()
          this.stringFilterGroup($r('app.string.filter_publisher'), this.filterReferential.publishers,
            this.draftSeriesFilter.publishers, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) =>
                filter.publishers = toggleFilterValue(filter.publishers, value)))
          this.stringFilterGroup($r('app.string.filter_language'), this.filterReferential.languages,
            this.draftSeriesFilter.languages, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) =>
                filter.languages = toggleFilterValue(filter.languages, value)))
          this.stringFilterGroup($r('app.string.filter_release_year'), this.filterReferential.releaseYears,
            this.draftSeriesFilter.releaseYears, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) =>
                filter.releaseYears = toggleFilterValue(filter.releaseYears, value)))
          this.stringFilterGroup($r('app.string.filter_age_rating'), this.filterReferential.ageRatings,
            this.draftSeriesFilter.ageRatings, false, (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) =>
                filter.ageRatings = toggleFilterValue(filter.ageRatings, value)))
          this.booleanFilterSelect($r('app.string.filter_complete'), this.draftSeriesFilter.complete,
            'library_filter_complete', (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => filter.complete = value))
          this.booleanFilterSelect($r('app.string.filter_oneshot'), this.draftSeriesFilter.oneshot,
            'library_filter_oneshot', (value: string) =>
              this.updateDraftFilter((filter: LibrarySeriesFilter) => filter.oneshot = value))
        }.width('100%').alignItems(HorizontalAlign.Start)
      }.id('library_filter_scroll').layoutWeight(1).width('100%').edgeEffect(EdgeEffect.Spring)
      Button($r('app.string.apply_filters')).height(48).width('100%')
        .id('library_filter_apply')
        .backgroundColor($r('app.color.primary_container'))
        .fontColor($r('app.color.on_surface'))
        .onClick(() => this.applyFilters())
    }
    .id('library_filter_sheet')
    .padding({ left: 16, right: 16, bottom: 24 })
    .width('100%').height('100%')
    .alignItems(HorizontalAlign.Start)
    .backgroundColor(KomeliaColors.surfaceHigh())
  }

  @Builder
  private libraryToolbar() {
    Row({ space: this.layout().controlSpacing }) {
      if (!this.supportingPane) this.libraryScopeButton()
      if (this.showSeriesSection()) {
        this.sectionButton(LibrarySection.SERIES, $r('app.string.series'))
      }
      if (this.showCollectionsSection()) {
        this.sectionButton(LibrarySection.COLLECTIONS, $r('app.string.collections'))
      }
      if (this.showReadListsSection()) {
        this.sectionButton(LibrarySection.READ_LISTS, $r('app.string.read_lists'))
      }
      Blank()
      if (this.selectedSection !== LibrarySection.SERIES && this.state.canManageServer()) {
        Button($r('app.string.create')).height(40).backgroundColor($r('app.color.primary_container'))
          .fontColor($r('app.color.primary')).onClick(() => router.pushUrl({
            url: this.selectedSection === LibrarySection.COLLECTIONS ?
              'pages/CollectionEdit' : 'pages/ReadListEdit'
          }))
      }
    }.height(48).width('100%')
  }

  @Builder
  private libraryHeader() {
    Column() {
      this.libraryToolbar()
      if (this.selectedSection === LibrarySection.SERIES && !this.selecting) {
        this.seriesToolbar()
      }
    }.id('library_header').width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private seriesToolbar() {
    Row({ space: this.layout().controlSpacing }) {
      Button(localizedString($r('app.string.series_count'), this.seriesTotalCount))
        .id('library_series_count')
        .height(48).padding({ left: 15, right: 15 }).fontSize(14)
        .fontColor($r('app.color.on_surface_variant'))
        .backgroundColor(Color.Transparent)
        .border({ width: 1, color: $r('app.color.outline') })
        .borderRadius(14)
      Blank()
      Button() {
        Image($r('app.media.library_filter')).width(24).height(24)
          .fillColor(this.query.length > 0 || this.seriesSort !== SERIES_SORT_VALUES[0] ||
            librarySeriesFilterCount(this.seriesFilter) > 0 ?
            $r('app.color.primary') : $r('app.color.on_surface_variant'))
      }
      .width(48).height(48).backgroundColor(Color.Transparent).borderRadius(24)
      .id('library_filter_button')
      .accessibilityText($r('app.string.filters'))
      .onClick(() => this.openFilters())
      .bindSheet($$this.filterSheetVisible, this.seriesFilterSheet(), {
        height: SheetSize.MEDIUM,
        dragBar: true,
        showClose: false
      })
      Select(this.pageSizeOptions())
        .id('library_page_size')
        .selected(safeLibraryPageSizeIndex(this.pageSize))
        .value(`${this.pageSize}`)
        .height(48).width(70)
        .backgroundColor(Color.Transparent)
        .onSelect((index: number) => {
          this.pageSize = LIBRARY_PAGE_SIZES[index];
          this.seriesPage = 0;
          this.load();
        })
    }.height(48).width('100%').alignItems(VerticalAlign.Center)
  }

  @Builder
  private seriesPagination() {
    if (this.seriesTotalPages > 1) {
      Row({ space: this.layout().controlSpacing }) {
        Button($r('app.string.previous_page')).height(40).enabled(this.seriesPage > 0)
          .onClick(() => { this.seriesPage--; this.load(); })
        Text(localizedString($r('app.string.page_of'), this.seriesPage + 1, this.seriesTotalPages))
          .fontSize(13).fontColor($r('app.color.on_surface_variant'))
        Button($r('app.string.next_page')).height(40)
          .enabled(this.seriesPage + 1 < this.seriesTotalPages)
          .onClick(() => { this.seriesPage++; this.load(); })
      }.width('100%').justifyContent(FlexAlign.Center).alignItems(VerticalAlign.Center)
    }
  }

  build() {
    Row() {
      if (this.supportingPane) {
        this.libraryScopePane()
      }
      Column({ space: this.layout().itemSpacing }) {
      this.libraryHeader()
      if (this.selecting) {
        Flex({ wrap: FlexWrap.Wrap, alignItems: ItemAlign.Center }) {
          Text(localizedString($r('app.string.selected_count'), this.selectedSeriesIds.length))
            .fontSize(14).margin({ right: 8, bottom: 8 })
          Button($r('app.string.mark_as_read')).height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('read'))
          Button($r('app.string.mark_as_unread')).height(40).margin({ right: 8, bottom: 8 })
            .onClick(() => this.batchSeriesAction('unread'))
          if (this.state.canManageServer()) {
            Button($r('app.string.bulk_edit_metadata')).height(40).margin({ right: 8, bottom: 8 })
              .enabled(this.selectedSeriesIds.length > 0)
              .onClick(() => router.pushUrl({
                url: 'pages/SeriesBulkEdit', params: { seriesIds: this.selectedSeriesIds.slice() }
              }))
            Button($r('app.string.add_to_collection')).height(40).margin({ right: 8, bottom: 8 })
              .enabled(this.selectedSeriesIds.length > 0)
              .onClick(() => this.openBatchCollection())
          }
          Button($r('app.string.download')).height(40).margin({ right: 8, bottom: 8 })
            .enabled(this.selectedSeriesIds.length > 0 && this.state.isOnline)
            .onClick(() => this.downloadSelectedSeries())
          if (this.state.canManageServer()) {
            Button($r('app.string.analyze')).height(40).margin({ right: 8, bottom: 8 })
              .onClick(() => this.batchSeriesAction('analyze'))
            Button($r('app.string.refresh_metadata')).height(40).margin({ right: 8, bottom: 8 })
              .onClick(() => this.batchSeriesAction('metadata/refresh'))
          }
        }.width('100%').padding(10).backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      }
      if (this.isLoading) { LoadingState() }
      else if (this.errorMessage.length > 0) { ErrorState(this.errorMessage, () => this.load()) }
      else if (this.resultCount() === 0) {
        Column() { EmptyState($r('app.string.no_filter_results')) }
          .id('library_empty_state').width('100%')
      }
      else if (this.selectedSection === LibrarySection.SERIES) {
        Grid() {
          LazyForEach(this.seriesDataSource, (item: KomgaSeries, index: number) => {
            GridItem() {
              Stack({ alignContent: Alignment.TopEnd }) {
                SeriesCard({ series: item, compact: this.compact, onOpen: (id: string) => {
                  if (this.selecting) this.toggleSeries(id);
                  else router.pushUrl({ url: seriesDetailPage(item.oneshot), params: { seriesId: id } });
                } })
                if (this.selecting) {
                  Text(this.selectedSeriesIds.includes(item.id) ? '✓' : '○').fontSize(18)
                    .fontColor(this.selectedSeriesIds.includes(item.id) ? Color.White : $r('app.color.primary'))
                    .width(32).height(32).textAlign(TextAlign.Center)
                    .backgroundColor(this.selectedSeriesIds.includes(item.id) ? $r('app.color.primary') : '#EFFFFFFF')
                    .borderRadius(16).margin(8).onClick(() => this.toggleSeries(item.id))
                }
              }.width('100%')
                .id(`library_series_${item.oneshot ? 'oneshot' : 'standard'}_card_${index}`)
                .gesture(LongPressGesture({ repeat: false, duration: 500 }).onAction(() => {
                  if (!this.selecting) this.selecting = true;
                  if (!this.selectedSeriesIds.includes(item.id)) this.toggleSeries(item.id);
                }))
            }.id(`library_series_card_${index}`)
          }, (item: KomgaSeries) => item.id)
        }.columnsTemplate(this.gridTemplate()).columnsGap(this.layout().gridSpacing)
        .rowsGap(this.layout().gridSpacing)
        .cachedCount(Math.max(2, this.gridColumnCount() * 2))
        .id('library_series_grid').layoutWeight(1).width('100%').edgeEffect(EdgeEffect.Spring)
        this.seriesPagination()
      } else if (this.selectedSection === LibrarySection.COLLECTIONS) {
        List({ space: 8 }) {
          LazyForEach(this.collectionDataSource, (item: KomgaCollection) => {
            ListItem() { this.namedItem(item.name,
              localizedString($r('app.string.series_count'), item.seriesIds.length), () =>
              router.pushUrl({ url: 'pages/CollectionDetail', params: { collectionId: item.id, title: item.name } })) }
          }, (item: KomgaCollection) => item.id)
        }.layoutWeight(1).width('100%')
      } else {
        List({ space: 8 }) {
          LazyForEach(this.readListDataSource, (item: KomgaReadList) => {
            ListItem() { this.namedItem(item.name,
              localizedString($r('app.string.books_count'), item.bookIds.length), () =>
              router.pushUrl({ url: 'pages/ReadListDetail', params: { readListId: item.id, title: item.name } })) }
          }, (item: KomgaReadList) => item.id)
        }.layoutWeight(1).width('100%')
      }
      if (this.message.length > 0) {
        Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      }
      }.padding({
        left: this.layout().pageHorizontalPadding,
        right: this.layout().pageHorizontalPadding,
        top: 0,
        bottom: this.layout().gridBottomPadding
      }).layoutWeight(1).height('100%')
        .id('library_content_column')
    }.width('100%').height('100%').id('library_page')
    .align(Alignment.Top).justifyContent(FlexAlign.Start)
    .onAreaChange((_, area) => {
      const width = Number(area.width);
      this.areaWidth = width;
      this.windowClass = KomeliaLayout.windowClass(width);
      this.supportingPane = width >= 1200;
      this.compact = width < 600;
    })
  }
}
