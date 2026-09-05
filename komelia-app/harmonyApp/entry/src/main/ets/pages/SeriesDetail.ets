import { KomeliaColors, KomeliaDetailCover, KomeliaDetailMetadata, KomeliaGrid, KomeliaLayout,
  WindowClass } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { BookCard } from '../components/CoverCards';
import { ErrorState, LoadingState, SectionTitle } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { bookDetailPage } from '../core/navigation/CatalogNavigation';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { namespaceOf } from '../data/local/DatabaseModels';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { hasStoredKomfConfiguration } from '../domain/service/KomfSession';
import { readerEngine } from '../domain/service/ReaderEngine';
import { KomgaAuthor, KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { libraryMetadataNavigationRequest, LibraryMetadataFilterKind, libraryScopeNavigationRequest } from
  '../model/LibraryPresentation';
import { seriesActionAvailability, SeriesActionAvailability, seriesPublicationStatusResource } from
  '../model/SeriesPresentation';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

interface SeriesParams {
  seriesId: string;
}

@Entry
@Component
struct SeriesDetail {
  @State series: KomgaSeries | undefined = undefined;
  @State books: KomgaBook[] = [];
  @State isLoading: boolean = true;
  @State areaWidth: number = 360;
  @StorageLink('gridCardWidth') cardWidth: number = KomeliaGrid.defaultCardWidth;
  @State selecting: boolean = false;
  @State selectedBookIds: string[] = [];
  @State message: string = '';
  @State errorMessage: string = '';
  @State windowClass: WindowClass = WindowClass.COMPACT;
  @State summaryExpanded: boolean = false;
  @State actionsSheetVisible: boolean = false;
  @State actionBusy: boolean = false;
  @State komfConfigured: boolean = false;
  private seriesId: string = '';

  private coverWidth(): number {
    return KomeliaDetailCover.width(this.windowClass);
  }

  private coverHeight(): number {
    return KomeliaDetailCover.height(this.windowClass);
  }

  aboutToAppear(): void {
    const params = router.getParams() as SeriesParams | undefined;
    this.seriesId = requiredRouteId(params?.seriesId);
    if (this.seriesId.length > 0) this.load();
    else {
      this.isLoading = false;
      this.errorMessage = localizedString($r('app.string.series_detail_load_failed'));
    }
    this.restoreKomfAvailability();
  }

  private async restoreKomfAvailability(): Promise<void> {
    this.komfConfigured = await hasStoredKomfConfiguration();
  }

  private async load(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    try {
      const values = await Promise.all([
        catalogRepository.seriesDetail(this.seriesId), catalogRepository.seriesBooks(this.seriesId)
      ]);
      this.series = values[0];
      this.books = values[1];
      if (this.series.oneshot) {
        router.replaceUrl({ url: 'pages/OneshotDetail', params: { seriesId: this.seriesId } });
        return;
      }
    } catch (error) {
      this.errorMessage = (error as Error).message || localizedString($r('app.string.series_detail_load_failed'));
    } finally {
      this.isLoading = false;
    }
  }

  private gridTemplate(): string {
    return KomeliaGrid.template(KomeliaGrid.columnCount(this.areaWidth, this.windowClass, this.cardWidth));
  }

  private summary(): string {
    const seriesSummary = this.series?.metadata?.summary || '';
    if (seriesSummary.length > 0) return seriesSummary;
    const bookSummary = this.series?.booksMetadata?.summary || '';
    if (bookSummary.length === 0) return '';
    return localizedString($r('app.string.series_summary_from_book'),
      this.series?.booksMetadata?.summaryNumber || '', bookSummary);
  }

  private releaseYear(): string {
    const value = (this.series?.booksMetadata?.releaseDate || '').trim();
    return value.length >= 4 ? value.substring(0, 4) : value;
  }

  private libraryName(): string {
    const libraryId = this.series?.libraryId || '';
    return AppState.getInstance().libraries.find((library) => library.id === libraryId)?.name || '';
  }

  private distinctValues(values: string[]): string[] {
    return values.map((value: string): string => value.trim())
      .filter((value: string): boolean => value.length > 0)
      .filter((value: string, index: number, all: string[]): boolean => all.indexOf(value) === index);
  }

  private authorNames(role: string): string[] {
    return this.distinctValues((this.series?.booksMetadata?.authors || [])
      .filter((author: KomgaAuthor): boolean => author.role.toLowerCase() === role)
      .map((author: KomgaAuthor): string => author.name));
  }

  private combinedTags(): string[] {
    return this.distinctValues((this.series?.metadata?.tags || [])
      .concat(this.series?.booksMetadata?.tags || []));
  }

  private actionAvailability(): SeriesActionAvailability {
    const series = this.series;
    return seriesActionAvailability(AppState.getInstance().isOnline, AppState.getInstance().canManageServer(),
      series?.booksCount || 0, series?.booksReadCount || 0, series?.booksUnreadCount || 0,
      this.komfConfigured);
  }

  private showToast(message: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({ message: message });
  }

  private async performSeriesAction(action: string): Promise<void> {
    if (this.actionBusy) return;
    this.actionsSheetVisible = false;
    this.actionBusy = true;
    try {
      if (action === 'markRead' || action === 'markUnread') {
        const completed = action === 'markRead';
        if (AppState.getInstance().isOnline) {
          await komgaClient.setSeriesReadStatus(this.seriesId, completed);
          await this.load();
        } else {
          await this.applyOfflineSeriesReadState(completed);
        }
        this.showToast(completed ? $r('app.string.series_marked_read') :
          $r('app.string.series_marked_unread'));
      } else {
        await komgaClient.runSeriesAction(this.seriesId, action);
        this.showToast(action === 'analyze' ? $r('app.string.series_analysis_started') :
          $r('app.string.series_metadata_refresh_started'));
      }
    } catch (error) {
      this.showToast(localizedString($r('app.string.series_action_failed'), (error as Error).message));
    } finally {
      this.actionBusy = false;
    }
  }

  private async applyOfflineSeriesReadState(completed: boolean): Promise<void> {
    const key = AppState.getInstance().namespace;
    if (key === undefined) throw new Error(localizedString($r('app.string.current_account_unavailable')));
    const namespace = namespaceOf(key);
    for (let index = 0; index < this.books.length; index++) {
      const book = this.books[index];
      if (completed) {
        await readerEngine.updateProgress(namespace, book.id, Math.max(1, book.media.pagesCount), true);
        book.readProgress = {
          page: Math.max(1, book.media.pagesCount), completed: true, readDate: new Date().toISOString()
        };
      } else {
        await readerEngine.clearProgress(namespace, book.id);
        book.readProgress = undefined;
      }
    }
    const series = this.series;
    if (series !== undefined) {
      series.booksReadCount = completed ? series.booksCount : 0;
      series.booksUnreadCount = completed ? 0 : series.booksCount;
      series.booksInProgressCount = 0;
      this.series = series;
    }
    this.books = this.books.slice();
  }

  private openKomfIdentify(): void {
    const series = this.series;
    if (series === undefined) return;
    this.actionsSheetVisible = false;
    router.pushUrl({ url: 'pages/KomfIdentify', params: {
      libraryId: series.libraryId,
      seriesId: series.id,
      seriesName: series.metadata.title || series.name
    } });
  }

  private openKomfReset(): void {
    const series = this.series;
    if (series === undefined) return;
    this.actionsSheetVisible = false;
    router.pushUrl({ url: 'pages/KomfResetMetadata', params: {
      target: 'series',
      libraryId: series.libraryId,
      seriesId: series.id,
      displayName: series.metadata.title || series.name
    } });
  }

  private openLibraryScope(): void {
    const series = this.series;
    if (series === undefined) return;
    AppState.getInstance().openLibrary(libraryScopeNavigationRequest(series.libraryId));
    router.back({ url: 'pages/Index' });
  }

  private openMetadataFilter(kind: LibraryMetadataFilterKind, value: string, role: string = ''): void {
    const series = this.series;
    if (series === undefined || value.trim().length === 0) return;
    AppState.getInstance().openLibrary(
      libraryMetadataNavigationRequest(series.libraryId, kind, value, role));
    router.back({ url: 'pages/Index' });
  }

  @Builder
  private metadataChipGroup(label: ResourceStr, values: string[], idPrefix: string,
    onOpen: (value: string) => void) {
    if (values.length > 0) {
      Row({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
        Text(label).fontSize(KomeliaDetailMetadata.rowFontSize)
          .lineHeight(KomeliaDetailMetadata.rowLineHeight).fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_surface_variant'))
          .width(KomeliaDetailMetadata.labelWidth(this.windowClass))
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(values, (value: string, index: number) => {
            Text(value).fontSize(KomeliaDetailMetadata.chipFontSize)
              .lineHeight(KomeliaDetailMetadata.chipLineHeight)
              .fontColor($r('app.color.on_surface_variant'))
              .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
              .padding({ left: KomeliaDetailMetadata.chipHorizontalPadding,
                right: KomeliaDetailMetadata.chipHorizontalPadding,
                top: KomeliaDetailMetadata.chipVerticalPadding,
                bottom: KomeliaDetailMetadata.chipVerticalPadding })
              .border({ width: 1, color: KomeliaColors.surfaceContainer() })
              .borderRadius(KomeliaDetailMetadata.chipRadius)
              .margin({ right: KomeliaDetailMetadata.chipSpacing, bottom: KomeliaDetailMetadata.chipSpacing })
              .id(`series_filter_${idPrefix}_${index}`).focusable(true)
              .accessibilityText(value).onClick(() => onOpen(value))
          }, (value: string) => value)
        }.layoutWeight(1)
      }.width('100%').alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  private heroDescription() {
    Column({ space: 5 }) {
      if (this.releaseYear().length > 0) {
        Text(localizedString($r('app.string.series_release_year'), this.releaseYear()))
          .fontSize(12).lineHeight(16).fontColor($r('app.color.on_surface_variant')).width('100%')
          .id('series_detail_release')
      }
      Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
        if (this.libraryName().length > 0) {
          Button() {
            Row({ space: 4 }) {
              Image($r('app.media.nav_library')).width(24).height(24)
                .fillColor($r('app.color.primary'))
              Text(this.libraryName()).fontSize(14).fontColor($r('app.color.primary'))
                .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                .decoration({ type: TextDecorationType.Underline })
            }
          }
          .height(48).padding({ left: 24, right: 24 })
          .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(20)
          .margin({ right: 10 })
          .id('series_library_scope').accessibilityText(this.libraryName())
          .onClick(() => this.openLibraryScope())
        }
        if ((this.series?.metadata?.status || '').length > 0) {
          Text(seriesPublicationStatusResource(this.series?.metadata?.status || ''))
            .fontSize(13).fontColor($r('app.color.on_surface_variant'))
            .height(48).padding({ left: 16, right: 16, top: 14 })
            .backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(20)
            .margin({ right: 10 })
            .id('series_filter_status').focusable(true)
            .onClick(() => this.openMetadataFilter(LibraryMetadataFilterKind.PUBLICATION_STATUS,
              this.series?.metadata?.status || ''))
        }
        if (this.series?.metadata?.ageRating !== undefined && this.series?.metadata?.ageRating !== null) {
          Text(`${this.series?.metadata?.ageRating}+`).fontSize(13)
            .fontColor($r('app.color.on_surface_variant'))
            .height(48).padding({ left: 16, right: 16, top: 14 })
            .backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(20)
            .margin({ right: 10 })
            .id('series_filter_age').focusable(true)
            .accessibilityText(`${this.series?.metadata?.ageRating}+`)
            .onClick(() => this.openMetadataFilter(LibraryMetadataFilterKind.AGE_RATING,
              `${this.series?.metadata?.ageRating}`))
        }
        if ((this.series?.metadata?.language || '').length > 0) {
          Text(this.series?.metadata?.language || '').fontSize(13)
            .fontColor($r('app.color.on_surface_variant'))
            .height(48).padding({ left: 16, right: 16, top: 14 })
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(20)
            .margin({ right: 10 })
            .id('series_filter_language').focusable(true)
            .accessibilityText(this.series?.metadata?.language || '')
            .onClick(() => this.openMetadataFilter(LibraryMetadataFilterKind.LANGUAGE,
              this.series?.metadata?.language || ''))
        }
      }.width('100%')
    }.layoutWeight(1).alignItems(HorizontalAlign.Start)
  }

  @Builder
  private expandableSummary() {
    if (this.summary().length > 0) {
      Column() {
        Text(this.summary()).fontSize(14).lineHeight(20)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
          .maxLines(this.summaryExpanded ? 999 : 9)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .id('series_detail_summary')
        if (this.summary().length > 260) {
          Button(this.summaryExpanded ? $r('app.string.collapse') : $r('app.string.expand'))
            .height(48).width('100%').backgroundColor(Color.Transparent)
            .fontColor($r('app.color.on_surface'))
            .onClick(() => this.summaryExpanded = !this.summaryExpanded)
        }
      }.width('100%')
    }
  }

  @Builder
  private seriesActionsSheet() {
    Scroll() {
      Column({ space: 8 }) {
        Text(this.series?.metadata?.title || this.series?.name || '')
          .fontSize(18).fontWeight(FontWeight.Bold).maxLines(2).width('100%')
        if (this.actionAvailability().analyze) {
          Button($r('app.string.analyze')).height(48).width('100%')
            .id('series_action_analyze').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performSeriesAction('analyze'))
        }
        if (this.actionAvailability().refreshMetadata) {
          Button($r('app.string.refresh_metadata')).height(48).width('100%')
            .id('series_action_refresh').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performSeriesAction('metadata/refresh'))
        }
        if (this.actionAvailability().addToCollection) {
          Button($r('app.string.add_to_collection')).height(48).width('100%')
            .id('series_action_collection').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface'))
            .onClick(() => {
              this.actionsSheetVisible = false;
              router.pushUrl({ url: 'pages/AddToGroup', params: { kind: 'collection', targetId: this.seriesId } });
            })
        }
        if (this.actionAvailability().markAsRead) {
          Button($r('app.string.mark_as_read')).height(48).width('100%')
            .id('series_action_mark_read').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performSeriesAction('markRead'))
        }
        if (this.actionAvailability().markAsUnread) {
          Button($r('app.string.mark_as_unread')).height(48).width('100%')
            .id('series_action_mark_unread').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performSeriesAction('markUnread'))
        }
        if (this.actionAvailability().edit) {
          Button($r('app.string.edit_series')).height(48).width('100%')
            .id('series_action_edit')
            .backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface'))
            .onClick(() => {
              this.actionsSheetVisible = false;
              router.pushUrl({ url: 'pages/SeriesEdit', params: { seriesId: this.seriesId } });
            })
        }
        if (this.actionAvailability().komf) {
          Button($r('app.string.komf_identify')).height(48).width('100%')
            .id('series_action_komf_identify').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface')).onClick(() => this.openKomfIdentify())
          Button($r('app.string.komf_reset')).height(48).width('100%')
            .id('series_action_komf_reset').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface')).onClick(() => this.openKomfReset())
        }
      }
      .width('100%').padding({ left: 16, right: 16, top: 16, bottom: 40 })
      .alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').scrollBar(BarState.Auto).id('series_actions_scroll')
  }

  private toggleBook(id: string): void {
    this.selectedBookIds = this.selectedBookIds.includes(id)
      ? this.selectedBookIds.filter((value: string) => value !== id)
      : this.selectedBookIds.concat([id]);
  }

  private async batchBookAction(action: string): Promise<void> {
    if (this.selectedBookIds.length === 0) return;
    this.message = localizedString($r('app.string.processing_books'), this.selectedBookIds.length);
    try {
      const key = AppState.getInstance().namespace;
      const namespace = key === undefined ? undefined : namespaceOf(key);
      for (let index = 0; index < this.selectedBookIds.length; index++) {
        const id = this.selectedBookIds[index];
        if (action === 'read' || action === 'deleteProgress') {
          if (namespace === undefined) {
            throw new Error(localizedString($r('app.string.current_account_unavailable')));
          }
          const book = this.books.find((value: KomgaBook) => value.id === id);
          if (action === 'read') {
            await readerEngine.updateProgress(namespace, id, Math.max(1, book?.media.pagesCount || 1), true);
          } else {
            await readerEngine.clearProgress(namespace, id);
          }
        } else {
          await komgaClient.runBookAction(id, action);
        }
      }
      this.message = localizedString($r('app.string.batch_completed'));
      this.selectedBookIds = [];
      this.selecting = false;
      await this.load();
    } catch (error) { this.message = (error as Error).message; }
  }

  private openBatchReadList(): void {
    if (this.selectedBookIds.length === 0) return;
    router.pushUrl({
      url: 'pages/AddToGroup',
      params: { kind: 'readList', targetIds: this.selectedBookIds.slice() }
    });
  }

  private async downloadSelectedBooks(): Promise<void> {
    const state = AppState.getInstance();
    const key = state.namespace;
    if (key === undefined || !state.isOnline || this.selectedBookIds.length === 0) return;
    const selected = this.selectedBookIds.slice();
    this.selectedBookIds = [];
    this.selecting = false;
    this.message = localizedString($r('app.string.downloads_queued'), selected.length);
    try {
      for (let index = 0; index < selected.length; index += 1) {
        await offlineDownloadService.download(namespaceOf(key), selected[index]);
      }
      this.message = localizedString($r('app.string.batch_download_completed'), selected.length);
    } catch (error) {
      this.message = localizedString($r('app.string.batch_download_failed'), (error as Error).message);
    }
  }

  private async downloadSeries(): Promise<void> {
    const state = AppState.getInstance();
    const key = state.namespace;
    if (key === undefined || !state.isOnline || this.books.length === 0) return;
    const bookIds = this.books.map((book: KomgaBook): string => book.id);
    this.message = localizedString($r('app.string.downloads_queued'), bookIds.length);
    try {
      for (let index = 0; index < bookIds.length; index += 1) {
        await offlineDownloadService.download(namespaceOf(key), bookIds[index]);
      }
      this.message = localizedString($r('app.string.batch_download_completed'), bookIds.length);
    } catch (error) {
      this.message = localizedString($r('app.string.batch_download_failed'), (error as Error).message);
    }
  }

  @Builder
  private pageContent() {
    Column() {
      Row() {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24)
            .fillColor($r('app.color.on_surface'))
        }
        .width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
        .id('series_detail_back')
        .accessibilityText($r('app.string.back'))
        Text(this.series?.metadata?.title || this.series?.name || localizedString($r('app.string.series_detail')))
          .fontSize(20).fontWeight(FontWeight.Bold).maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
          .id('series_detail_title')
        if (this.series !== undefined) {
          Button({ type: ButtonType.Circle }) {
            Image($r('app.media.detail_more_vertical')).width(24).height(24)
              .fillColor($r('app.color.on_surface'))
          }
          .width(48).height(48).backgroundColor(Color.Transparent)
          .id('series_detail_more')
          .accessibilityText($r('app.string.series_actions'))
          .onClick(() => this.actionsSheetVisible = true)
          if (AppState.getInstance().isOnline) {
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.detail_download')).width(24).height(24)
                .fillColor($r('app.color.on_surface'))
            }
            .width(48).height(48).backgroundColor(Color.Transparent)
            .id('series_detail_download')
            .accessibilityText($r('app.string.download_series'))
            .onClick(() => this.downloadSeries())
          }
        }
      }
      .height(56).width('100%').padding({
        left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
        right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding
      })

      if (this.isLoading) {
        LoadingState()
      } else if (this.errorMessage.length > 0 || this.series === undefined) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Row() {
            Column({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
            Column({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
              Row({ space: KomeliaLayout.spec(this.windowClass).gridSpacing }) {
                Stack() {
                  AuthenticatedImage({ endpoint: `/api/v1/series/${this.seriesId}/thumbnail` })
                }
                .id('series_detail_cover')
                .width(this.coverWidth()).height(this.coverHeight()).borderRadius(16).clip(true)
                .border({ width: 1, color: $r('app.color.outline') })

                this.heroDescription()
              }
              .width('100%').alignItems(VerticalAlign.Top)

              this.expandableSummary()
            }.width('100%')

            Column({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
              this.metadataChipGroup($r('app.string.series_publisher'),
                (this.series?.metadata?.publisher || '').length > 0 ? [this.series?.metadata?.publisher || ''] : [],
                'publisher', (value: string) =>
                this.openMetadataFilter(LibraryMetadataFilterKind.PUBLISHER, value))
              this.metadataChipGroup($r('app.string.genres'), this.series?.metadata?.genres || [], 'genre',
                (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.GENRE, value))
              this.metadataChipGroup($r('app.string.tags'), this.combinedTags(), 'tag',
                (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.TAG, value))
              this.metadataChipGroup($r('app.string.writer'), this.authorNames('writer'), 'writer',
                (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.AUTHOR, value, 'writer'))
              this.metadataChipGroup($r('app.string.penciller'), this.authorNames('penciller'), 'penciller',
                (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.AUTHOR, value, 'penciller'))
            }.width('100%')

            Row() {
              SectionTitle($r('app.string.books'), this.books.length)
              Button(this.selecting ? $r('app.string.cancel') : $r('app.string.batch'))
                .height(40).backgroundColor(Color.Transparent)
                .fontColor($r('app.color.primary')).onClick(() => {
                  this.selecting = !this.selecting;
                  if (!this.selecting) this.selectedBookIds = [];
                })
            }.width('100%')
            if (this.selecting) {
              Flex({ wrap: FlexWrap.Wrap }) {
                Text(localizedString($r('app.string.selected_books_count'), this.selectedBookIds.length))
                  .fontSize(14).margin({ right: 8, bottom: 8 })
                Button($r('app.string.mark_as_read')).height(40).margin({ right: 8, bottom: 8 })
                  .onClick(() => this.batchBookAction('read'))
                Button($r('app.string.clear_progress')).height(40).margin({ right: 8, bottom: 8 })
                  .onClick(() => this.batchBookAction('deleteProgress'))
                if (AppState.getInstance().canManageServer()) {
                  Button($r('app.string.bulk_edit_metadata')).height(40).margin({ right: 8, bottom: 8 })
                    .enabled(this.selectedBookIds.length > 0)
                    .onClick(() => router.pushUrl({
                      url: 'pages/BookBulkEdit', params: { bookIds: this.selectedBookIds.slice() }
                    }))
                  Button($r('app.string.add_to_read_list')).height(40).margin({ right: 8, bottom: 8 })
                    .enabled(this.selectedBookIds.length > 0)
                    .onClick(() => this.openBatchReadList())
                }
                Button($r('app.string.download')).height(40).margin({ right: 8, bottom: 8 })
                  .enabled(this.selectedBookIds.length > 0 && AppState.getInstance().isOnline)
                  .onClick(() => this.downloadSelectedBooks())
                if (AppState.getInstance().canManageServer()) {
                  Button($r('app.string.analyze')).height(40).margin({ right: 8, bottom: 8 })
                    .onClick(() => this.batchBookAction('analyze'))
                  Button($r('app.string.refresh_metadata')).height(40).margin({ right: 8, bottom: 8 })
                    .onClick(() => this.batchBookAction('metadata/refresh'))
                }
              }.width('100%').padding(10).backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            }
            Grid() {
              ForEach(this.books, (book: KomgaBook, index: number) => {
                GridItem() {
                  Stack({ alignContent: Alignment.TopEnd }) {
                    BookCard({ book: book, compact: this.windowClass === WindowClass.COMPACT,
                      onOpen: (id: string) => {
                      if (this.selecting) this.toggleBook(id);
                      else router.pushUrl({ url: bookDetailPage(book.oneshot), params: { bookId: id } });
                    } })
                    if (this.selecting) {
                      Text(this.selectedBookIds.includes(book.id) ? '✓' : '○').fontSize(18)
                        .fontColor(this.selectedBookIds.includes(book.id) ? Color.White : $r('app.color.primary'))
                        .width(32).height(32).textAlign(TextAlign.Center)
                        .backgroundColor(this.selectedBookIds.includes(book.id) ? $r('app.color.primary') : '#EFFFFFFF')
                        .borderRadius(16).margin(8).onClick(() => this.toggleBook(book.id))
                    }
                  }.width('100%')
                }.id(`series_book_card_${index}`)
              }, (book: KomgaBook) => book.id)
            }
            .columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12).width('100%')
            if (this.message.length > 0) {
              Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
            }
            }
            .id('series_detail_content')
            .width('100%').constraintSize({ maxWidth: KomeliaLayout.spec(this.windowClass).contentMaxWidth })
            .padding({
              left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              top: 0,
              bottom: KomeliaLayout.spec(this.windowClass).gridBottomPadding
            })
            .onAreaChange((_, area) => {
              const width = Number(area.width);
              this.areaWidth = width;
            })
          }
          .width('100%').justifyContent(FlexAlign.Center).alignItems(VerticalAlign.Top)
        }
        .id('series_detail_scroll').layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }
    .width('100%').height('100%').align(Alignment.Top).justifyContent(FlexAlign.Start)
    .id('series_detail_page')
    .backgroundColor(KomeliaColors.surface())
    .bindSheet($$this.actionsSheetVisible, this.seriesActionsSheet(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      showClose: false
    })
    .onAreaChange((_, area) => this.windowClass = KomeliaLayout.windowClass(Number(area.width)))
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
