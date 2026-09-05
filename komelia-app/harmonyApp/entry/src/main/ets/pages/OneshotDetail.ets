import { router } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { BookReadControl } from '../components/BookReadControl';
import { ErrorState, LoadingState, SectionTitle } from '../components/CommonUi';
import { KomeliaColors, KomeliaDetailCover, KomeliaDetailMetadata, KomeliaLayout,
  WindowClass } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { namespaceOf } from '../data/local/DatabaseModels';
import { catalogRepository, offlineRepository } from '../data/repository/DefaultRepositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { hasStoredKomfConfiguration } from '../domain/service/KomfSession';
import { ReaderCapabilities } from '../domain/service/ReaderCapabilities';
import { readerEngine } from '../domain/service/ReaderEngine';
import { authorRoleResource, bookMediaTypeLabel, bookReadingSummary, BookAuthorGroup, groupBookAuthors,
  isLocalDownloadOutdated } from '../model/BookPresentation';
import { KomgaAuthor, KomgaBook, KomgaSeries, KomgaWebLink } from '../model/KomgaModels';
import { libraryMetadataNavigationRequest, LibraryMetadataFilterKind, libraryScopeNavigationRequest } from
  '../model/LibraryPresentation';
import { formatDateTime } from '../model/UserPresentation';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

interface OneshotParams {
  seriesId?: string;
  bookId?: string;
}

@Entry
@Component
struct OneshotDetail {
  @State series: KomgaSeries | undefined = undefined;
  @State book: KomgaBook | undefined = undefined;
  @State isLoading: boolean = true;
  @State errorMessage: string = '';
  @State downloadLabel: string = '';
  @State downloaded: boolean = false;
  @State downloadInProgress: boolean = false;
  @State downloadPaused: boolean = false;
  @State localDownloadOutdated: boolean = false;
  @State actionsSheetVisible: boolean = false;
  @State actionBusy: boolean = false;
  @State komfConfigured: boolean = false;
  @State windowClass: WindowClass = WindowClass.COMPACT;
  private seriesId: string = '';
  private bookId: string = '';
  private downloadJobId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as OneshotParams | undefined;
    this.seriesId = requiredRouteId(params?.seriesId);
    this.bookId = requiredRouteId(params?.bookId);
    this.downloadLabel = localizedString($r('app.string.download'));
    if (this.seriesId.length > 0 || this.bookId.length > 0) this.load();
    else {
      this.isLoading = false;
      this.errorMessage = localizedString($r('app.string.book_detail_load_failed'));
    }
    this.restoreKomfAvailability();
  }

  private async restoreKomfAvailability(): Promise<void> {
    this.komfConfigured = await hasStoredKomfConfiguration();
  }

  private coverWidth(): number {
    return KomeliaDetailCover.width(this.windowClass);
  }

  private coverHeight(): number {
    return KomeliaDetailCover.height(this.windowClass);
  }

  private async load(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    this.downloaded = false;
    this.localDownloadOutdated = false;
    this.downloadLabel = localizedString($r('app.string.download'));
    try {
      if (this.bookId.length > 0) {
        this.book = await catalogRepository.bookDetail(this.bookId);
        this.seriesId = this.book.seriesId;
        this.series = await catalogRepository.seriesDetail(this.seriesId);
      } else {
        this.series = await catalogRepository.seriesDetail(this.seriesId);
        const books = await catalogRepository.seriesBooks(this.seriesId);
        if (books.length === 0) throw new Error(localizedString($r('app.string.book_detail_load_failed')));
        this.book = books[0];
        this.bookId = this.book.id;
      }
      const key = AppState.getInstance().namespace;
      if (key !== undefined) {
        const offline = await offlineRepository.getBook(namespaceOf(key), this.bookId);
        if (offline !== undefined && offline.downloadedPages >= offline.pageCount) {
          this.downloaded = true;
          this.localDownloadOutdated = isLocalDownloadOutdated(this.book.fileLastModified, offline.updatedAt);
          this.downloadLabel = localizedString($r('app.string.downloaded'));
        }
      }
    } catch (error) {
      this.errorMessage = (error as Error).message || localizedString($r('app.string.book_detail_load_failed'));
    } finally {
      this.isLoading = false;
    }
  }

  private openReader(trackProgress: boolean = true): void {
    const mediaType = (this.book?.media?.mediaType || '').toLowerCase();
    const isEpub = mediaType.includes('epub');
    if (isEpub && !ReaderCapabilities.nativeEpubAvailable()) {
      this.getUIContext().showAlertDialog({
        title: $r('app.string.emulator_epub_unavailable'),
        message: $r('app.string.emulator_epub_message'),
        buttons: [{ value: $r('app.string.got_it'), action: () => {} }]
      });
      return;
    }
    router.pushUrl({
      url: isEpub ? 'pages/EpubReader' : 'pages/ImageReader',
      params: {
        bookId: this.bookId,
        initialPage: Math.max(0, (this.book?.readProgress?.page || 1) - 1),
        trackProgress: trackProgress,
        bookTitle: this.book?.metadata?.title || this.book?.name || '',
        seriesTitle: this.series?.metadata?.title || this.series?.name || ''
      }
    });
  }

  private async download(): Promise<void> {
    const key = AppState.getInstance().namespace;
    if (key === undefined || (this.downloaded && !this.localDownloadOutdated)) return;
    if (this.localDownloadOutdated) {
      await offlineDownloadService.delete(namespaceOf(key), this.bookId);
      this.downloaded = false;
      this.localDownloadOutdated = false;
    }
    if (this.downloadInProgress && this.downloadJobId.length > 0) {
      this.downloadPaused = true;
      this.downloadLabel = localizedString($r('app.string.resume'));
      await offlineDownloadService.pause(this.downloadJobId);
      this.downloadInProgress = false;
      return;
    }
    this.downloadPaused = false;
    this.downloadInProgress = true;
    this.downloadLabel = localizedString($r('app.string.pause'));
    try {
      if (this.downloadJobId.length > 0) {
        await offlineDownloadService.resume(namespaceOf(key), this.bookId, this.downloadJobId);
      } else {
        const handle = await offlineDownloadService.start(namespaceOf(key), this.bookId);
        this.downloadJobId = handle.jobId;
        await handle.completion;
      }
      this.downloaded = true;
      this.localDownloadOutdated = false;
      this.downloadLabel = localizedString($r('app.string.downloaded'));
    } catch (error) {
      this.downloadLabel = this.downloadPaused ? localizedString($r('app.string.resume')) :
        localizedString($r('app.string.download_failed_retry'), (error as Error).message);
    } finally {
      if (!this.downloadPaused) this.downloadInProgress = false;
    }
  }

  private async refreshBook(): Promise<void> {
    this.book = await catalogRepository.bookDetail(this.bookId);
  }

  private async performBookAction(action: string): Promise<void> {
    if (this.actionBusy) return;
    this.actionsSheetVisible = false;
    this.actionBusy = true;
    try {
      if (action === 'markRead') {
        const key = AppState.getInstance().namespace;
        if (key === undefined) throw new Error(localizedString($r('app.string.current_account_unavailable')));
        await readerEngine.updateProgress(namespaceOf(key), this.bookId,
          Math.max(1, this.book?.media?.pagesCount || 1), true);
        if (AppState.getInstance().isOnline) await this.refreshBook();
        else this.applyLocalReadState(true);
        this.showToast($r('app.string.book_marked_read'));
      } else if (action === 'markUnread') {
        const key = AppState.getInstance().namespace;
        if (key === undefined) throw new Error(localizedString($r('app.string.current_account_unavailable')));
        await readerEngine.clearProgress(namespaceOf(key), this.bookId);
        if (AppState.getInstance().isOnline) await this.refreshBook();
        else this.applyLocalReadState(false);
        this.showToast($r('app.string.book_marked_unread'));
      } else {
        await komgaClient.runBookAction(this.bookId, action);
        this.showToast(action === 'analyze' ? $r('app.string.book_analysis_started') :
          $r('app.string.book_metadata_refresh_started'));
      }
    } catch (error) {
      this.showToast(localizedString($r('app.string.book_action_failed'), (error as Error).message));
    } finally {
      this.actionBusy = false;
    }
  }

  private applyLocalReadState(completed: boolean): void {
    const book = this.book;
    if (book === undefined) return;
    book.readProgress = completed ? {
      page: Math.max(1, book.media.pagesCount), completed: true, readDate: new Date().toISOString()
    } : undefined;
    this.book = book;
  }

  private showToast(message: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({ message: message });
  }

  private confirmDeleteDownload(): void {
    this.actionsSheetVisible = false;
    this.getUIContext().showAlertDialog({
      title: $r('app.string.book_delete_download_title'),
      message: localizedString($r('app.string.book_delete_download_message'),
        this.book?.metadata?.title || this.book?.name || ''),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          this.deleteDownload().catch((error: Error) =>
            this.showToast(localizedString($r('app.string.book_action_failed'), error.message)));
        } }
      ]
    });
  }

  private openKomfIdentify(): void {
    if (this.series === undefined) return;
    this.actionsSheetVisible = false;
    router.pushUrl({ url: 'pages/KomfIdentify', params: {
      seriesId: this.seriesId,
      libraryId: this.series.libraryId,
      seriesName: this.series.metadata.title || this.series.name
    } });
  }

  private openKomfReset(): void {
    if (this.series === undefined) return;
    this.actionsSheetVisible = false;
    router.pushUrl({ url: 'pages/KomfResetMetadata', params: {
      scope: 'series',
      seriesId: this.seriesId,
      libraryId: this.series.libraryId,
      displayName: this.series.metadata.title || this.series.name
    } });
  }

  private openOneshotEdit(): void {
    if (this.series === undefined || this.book === undefined) return;
    this.actionsSheetVisible = false;
    router.pushUrl({ url: 'pages/OneshotEdit', params: {
      seriesId: this.seriesId, bookId: this.bookId
    } });
  }

  private async deleteDownload(): Promise<void> {
    const key = AppState.getInstance().namespace;
    if (key === undefined) return;
    await offlineDownloadService.delete(namespaceOf(key), this.bookId);
    this.downloaded = false;
    this.localDownloadOutdated = false;
    this.downloadLabel = localizedString($r('app.string.download'));
    this.showToast($r('app.string.download_removed'));
  }

  private openExternalLink(link: KomgaWebLink): void {
    if (link.url.trim().length === 0) return;
    const context = getContext(this) as common.UIAbilityContext;
    context.openLink(link.url).catch(() => this.showToast($r('app.string.link_open_failed')));
  }

  private readingProgressText(): string {
    const book = this.book;
    if (book === undefined) return '';
    const summary = bookReadingSummary(book);
    if (summary === undefined) return '';
    return localizedString($r('app.string.book_read_progress_value'), summary.percentage, summary.pagesLeft);
  }

  private readingDirectionText(): string {
    switch (this.series?.metadata?.readingDirection || '') {
      case 'LEFT_TO_RIGHT': return localizedString($r('app.string.left_to_right'));
      case 'RIGHT_TO_LEFT': return localizedString($r('app.string.right_to_left'));
      case 'VERTICAL': return localizedString($r('app.string.vertical'));
      case 'WEBTOON': return localizedString($r('app.string.webtoon'));
      default: return '';
    }
  }

  private distinct(values: string[]): string[] {
    return values.map((value: string): string => value.trim())
      .filter((value: string): boolean => value.length > 0)
      .filter((value: string, index: number, all: string[]): boolean => all.indexOf(value) === index);
  }

  private summaryText(): string {
    return this.book?.metadata?.summary || this.series?.metadata?.summary || '';
  }

  private libraryName(): string {
    const libraryId = this.book?.libraryId || this.series?.libraryId || '';
    return AppState.getInstance().libraries.find((library) => library.id === libraryId)?.name || '';
  }

  private openLibraryScope(): void {
    const libraryId = this.book?.libraryId || this.series?.libraryId || '';
    if (libraryId.length === 0) return;
    AppState.getInstance().openLibrary(libraryScopeNavigationRequest(libraryId));
    router.back({ url: 'pages/Index' });
  }

  private openMetadataFilter(kind: LibraryMetadataFilterKind, value: string, role: string = ''): void {
    const libraryId = this.book?.libraryId || this.series?.libraryId || '';
    if (libraryId.length === 0 || value.trim().length === 0) return;
    AppState.getInstance().openLibrary(
      libraryMetadataNavigationRequest(libraryId, kind, value, role));
    router.back({ url: 'pages/Index' });
  }

  @Builder
  private infoRow(label: ResourceStr, value: string) {
    if (value.length > 0) {
      Row({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
        Text(label).width(KomeliaDetailMetadata.labelWidth(this.windowClass))
          .fontSize(KomeliaDetailMetadata.rowFontSize).lineHeight(KomeliaDetailMetadata.rowLineHeight)
          .fontColor($r('app.color.on_surface_variant'))
        Text(value).layoutWeight(1).fontSize(KomeliaDetailMetadata.rowFontSize)
          .lineHeight(KomeliaDetailMetadata.rowLineHeight).fontColor($r('app.color.on_surface'))
      }.width('100%').alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  private chipGroup(label: ResourceStr, values: string[], idPrefix: string,
    onOpen: (value: string) => void) {
    if (this.distinct(values).length > 0) {
      Row({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
        Text(label).width(KomeliaDetailMetadata.labelWidth(this.windowClass))
          .fontSize(KomeliaDetailMetadata.rowFontSize).lineHeight(KomeliaDetailMetadata.rowLineHeight)
          .fontColor($r('app.color.on_surface_variant'))
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(this.distinct(values), (value: string, index: number) => {
            Text(value).fontSize(KomeliaDetailMetadata.chipFontSize)
              .lineHeight(KomeliaDetailMetadata.chipLineHeight)
              .fontColor($r('app.color.on_surface_variant'))
              .padding({ left: KomeliaDetailMetadata.chipHorizontalPadding,
                right: KomeliaDetailMetadata.chipHorizontalPadding,
                top: KomeliaDetailMetadata.chipVerticalPadding,
                bottom: KomeliaDetailMetadata.chipVerticalPadding })
              .backgroundColor(Color.Transparent).borderRadius(KomeliaDetailMetadata.chipRadius)
              .border({ width: 1, color: KomeliaColors.surfaceContainer() })
              .margin({ right: KomeliaDetailMetadata.chipSpacing, bottom: KomeliaDetailMetadata.chipSpacing })
              .id(`oneshot_filter_${idPrefix}_${index}`).focusable(true)
              .accessibilityText(value).onClick(() => onOpen(value))
          }, (value: string) => value)
        }.layoutWeight(1)
      }.width('100%').alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  private authorChipGroups() {
    ForEach(groupBookAuthors(this.book?.metadata?.authors || []), (group: BookAuthorGroup) => {
      this.chipGroup(authorRoleResource(group.role),
        group.authors.map((author: KomgaAuthor): string => author.name), `author_${group.role}`,
        (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.AUTHOR, value, group.role))
    }, (group: BookAuthorGroup) => group.role)
  }

  @Builder
  private linkChipGroup() {
    if ((this.book?.metadata?.links || []).length > 0) {
      Row({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
        Text($r('app.string.web_links')).width(KomeliaDetailMetadata.labelWidth(this.windowClass))
          .fontSize(KomeliaDetailMetadata.rowFontSize).lineHeight(KomeliaDetailMetadata.rowLineHeight)
          .fontColor($r('app.color.on_surface_variant'))
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(this.book?.metadata?.links || [], (link: KomgaWebLink, index: number) => {
            Text(link.label.trim().length > 0 ? link.label : link.url)
              .fontSize(KomeliaDetailMetadata.chipFontSize).lineHeight(KomeliaDetailMetadata.chipLineHeight)
              .fontColor($r('app.color.primary'))
              .padding({ left: KomeliaDetailMetadata.chipHorizontalPadding,
                right: KomeliaDetailMetadata.chipHorizontalPadding,
                top: KomeliaDetailMetadata.chipVerticalPadding,
                bottom: KomeliaDetailMetadata.chipVerticalPadding })
              .backgroundColor(Color.Transparent).borderRadius(KomeliaDetailMetadata.chipRadius)
              .border({ width: 1, color: $r('app.color.primary') })
              .margin({ right: KomeliaDetailMetadata.chipSpacing, bottom: KomeliaDetailMetadata.chipSpacing })
              .id(`oneshot_link_${index}`).focusable(true)
              .accessibilityText(link.url).onClick(() => this.openExternalLink(link))
          }, (link: KomgaWebLink, index: number) => `${link.url}-${index}`)
        }.layoutWeight(1)
      }.width('100%').alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  private statusChip(label: ResourceStr) {
    Text(label).fontSize(13).fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.on_error_container'))
      .padding({ left: 10, right: 10, top: 6, bottom: 6 })
      .backgroundColor($r('app.color.error_container')).borderRadius(10)
      .margin({ right: 8, bottom: 8 })
  }

  @Builder
  private readingDetails() {
    if ((this.book?.metadata?.releaseDate || '').length > 0) {
      this.infoRow($r('app.string.release_date'), this.book?.metadata?.releaseDate || '')
    }
    if (this.readingProgressText().length > 0) {
      this.infoRow($r('app.string.book_read_progress'), this.readingProgressText())
    }
    if ((this.book?.readProgress?.readDate || '').length > 0) {
      this.infoRow($r('app.string.book_last_read'), formatDateTime(this.book?.readProgress?.readDate || ''))
    }
  }

  @Builder
  private oneshotActionsSheet() {
    Scroll() {
      Column({ space: 8 }) {
        Text(this.book?.metadata?.title || this.book?.name || '')
          .fontSize(18).fontWeight(FontWeight.Bold).maxLines(2).width('100%')
        if (AppState.getInstance().isOnline && AppState.getInstance().canManageServer()) {
          Button($r('app.string.analyze')).height(48).width('100%')
            .id('oneshot_action_analyze').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performBookAction('analyze'))
          Button($r('app.string.refresh_metadata')).height(48).width('100%')
            .id('oneshot_action_refresh').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performBookAction('metadata/refresh'))
          Button($r('app.string.add_to_read_list')).height(48).width('100%')
            .id('oneshot_action_read_list').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface')).onClick(() => {
              this.actionsSheetVisible = false;
              router.pushUrl({ url: 'pages/AddToGroup', params: { kind: 'readList', targetId: this.bookId } });
            })
          Button($r('app.string.add_to_collection')).height(48).width('100%')
            .id('oneshot_action_collection').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface')).onClick(() => {
              this.actionsSheetVisible = false;
              router.pushUrl({ url: 'pages/AddToGroup', params: { kind: 'collection', targetId: this.seriesId } });
            })
          Button($r('app.string.edit_oneshot')).height(48).width('100%')
            .id('oneshot_action_edit_book').backgroundColor(KomeliaColors.surfaceContainer())
            .fontColor($r('app.color.on_surface')).onClick(() => this.openOneshotEdit())
          if (this.komfConfigured) {
            Button($r('app.string.komf_identify')).height(48).width('100%')
              .id('oneshot_action_komf_identify').backgroundColor(KomeliaColors.surfaceContainer())
              .fontColor($r('app.color.on_surface')).onClick(() => this.openKomfIdentify())
            Button($r('app.string.komf_reset')).height(48).width('100%')
              .id('oneshot_action_komf_reset').backgroundColor(KomeliaColors.surfaceContainer())
              .fontColor($r('app.color.on_surface')).onClick(() => this.openKomfReset())
          }
        }
        if (this.book?.readProgress?.completed !== true) {
          Button($r('app.string.mark_as_read')).height(48).width('100%')
            .id('oneshot_action_mark_read').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performBookAction('markRead'))
        }
        if (this.book?.readProgress !== undefined &&
          this.book?.readProgress !== null) {
          Button($r('app.string.mark_as_unread')).height(48).width('100%')
            .id('oneshot_action_mark_unread').enabled(!this.actionBusy)
            .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
            .onClick(() => this.performBookAction('markUnread'))
        }
        if (this.downloaded) {
          Button($r('app.string.book_delete_downloaded')).height(48).width('100%')
            .id('oneshot_action_delete_download').enabled(!this.actionBusy)
            .backgroundColor($r('app.color.error_container')).fontColor($r('app.color.on_error_container'))
            .onClick(() => this.confirmDeleteDownload())
        }
      }
      .width('100%').padding({ left: 16, right: 16, top: 16, bottom: 40 })
      .alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').scrollBar(BarState.Auto).id('oneshot_actions_scroll')
  }

  @Builder
  private pageContent() {
    Column() {
      Row() {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24)
            .fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.book?.metadata?.title || this.book?.name || localizedString($r('app.string.book_detail')))
          .fontSize(20).fontWeight(FontWeight.Bold).maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
        if (this.book !== undefined) {
          Button({ type: ButtonType.Circle }) {
            Image($r('app.media.detail_more_vertical')).width(24).height(24)
              .fillColor($r('app.color.on_surface'))
          }.width(48).height(48).backgroundColor(Color.Transparent)
            .id('oneshot_detail_more').accessibilityText($r('app.string.book_actions'))
            .onClick(() => this.actionsSheetVisible = true)
          if (AppState.getInstance().canManageServer() && AppState.getInstance().isOnline) {
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.detail_edit')).width(24).height(24)
                .fillColor($r('app.color.on_surface'))
            }.width(48).height(48).backgroundColor(Color.Transparent)
              .id('oneshot_detail_edit').accessibilityText($r('app.string.edit_oneshot'))
              .onClick(() => this.openOneshotEdit())
          }
        }
      }.height(48).width('100%').padding({
        left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
        right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding
      })

      if (this.isLoading) {
        LoadingState()
      } else if (this.errorMessage.length > 0 || this.book === undefined || this.series === undefined) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Row() {
            Column({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
            Row({ space: KomeliaLayout.spec(this.windowClass).gridSpacing }) {
              Stack() {
                AuthenticatedImage({ endpoint: `/api/v1/books/${this.bookId}/thumbnail` })
              }.id('oneshot_detail_cover')
                .width(this.coverWidth()).height(this.coverHeight()).borderRadius(16).clip(true)
                .border({ width: 1, color: $r('app.color.outline') })

              Column({ space: KomeliaLayout.spec(this.windowClass).controlSpacing }) {
                Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
                  if (this.libraryName().length > 0) {
                    Button(this.libraryName()).height(40).fontSize(14).margin({ right: 8, bottom: 8 })
                      .fontColor($r('app.color.primary')).backgroundColor($r('app.color.primary_container'))
                      .id('oneshot_library_scope').accessibilityText(this.libraryName())
                      .onClick(() => this.openLibraryScope())
                  }
                  if (this.series?.metadata?.ageRating !== null &&
                    this.series?.metadata?.ageRating !== undefined) {
                    Button(`${this.series?.metadata?.ageRating}+`).height(40).fontSize(14)
                      .margin({ right: 8, bottom: 8 }).backgroundColor(KomeliaColors.surfaceContainer())
                      .fontColor($r('app.color.on_surface_variant')).id('oneshot_age_rating')
                      .onClick(() => this.openMetadataFilter(LibraryMetadataFilterKind.AGE_RATING,
                        `${this.series?.metadata?.ageRating}`))
                  }
                  if ((this.series?.metadata?.language || '').length > 0) {
                    Button(this.series?.metadata?.language || '').height(40).fontSize(14)
                      .margin({ right: 8, bottom: 8 }).backgroundColor(KomeliaColors.surfaceContainer())
                      .fontColor($r('app.color.on_surface_variant')).id('oneshot_language')
                      .onClick(() => this.openMetadataFilter(LibraryMetadataFilterKind.LANGUAGE,
                        this.series?.metadata?.language || ''))
                  }
                  if (this.readingDirectionText().length > 0) {
                    Button(this.readingDirectionText()).height(40).fontSize(14)
                      .margin({ right: 8, bottom: 8 }).backgroundColor(KomeliaColors.surfaceContainer())
                      .fontColor($r('app.color.on_surface_variant')).id('oneshot_reading_direction')
                  }
                }.width('100%')
                Text(localizedString($r('app.string.volume_page_summary'),
                  this.book?.metadata?.number || this.book?.number || '-', this.book?.media?.pagesCount || 0))
                  .fontSize(16).lineHeight(24).fontColor($r('app.color.on_surface')).width('100%')
                Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
                  if (this.series?.deleted === true || this.book?.deleted === true) {
                    this.statusChip($r('app.string.book_unavailable'))
                  }
                  if (this.book?.remoteFileUnavailable === true) {
                    this.statusChip($r('app.string.book_remote_unavailable'))
                  }
                  if (this.localDownloadOutdated) {
                    this.statusChip($r('app.string.book_local_download_outdated'))
                  }
                }.width('100%')
                this.readingDetails()
                Row({ space: 8 }) {
                  BookReadControl(this.series?.deleted !== true && this.book?.deleted !== true &&
                    this.book?.remoteFileUnavailable !== true, 'oneshot_detail',
                    () => this.openReader(true), () => this.openReader(false))
                  Button() {
                    Row({ space: 3 }) {
                      Image($r('app.media.detail_download')).width(24).height(24)
                        .fillColor($r('app.color.primary'))
                      Text(this.downloadLabel).fontSize(14).fontColor($r('app.color.primary'))
                    }
                  }.height(40).borderRadius(20)
                    .id('oneshot_detail_download')
                    .backgroundColor($r('app.color.primary_container')).fontColor($r('app.color.primary'))
                    .enabled((!this.downloaded || this.localDownloadOutdated) && AppState.getInstance().isOnline)
                    .onClick(() => this.download())
                    .responseRegion({ x: 0, y: -4, width: '100%', height: 48 })
                }
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            }.width('100%').alignItems(VerticalAlign.Top)

            if (this.summaryText().length > 0) {
              Column({ space: 8 }) {
                SectionTitle($r('app.string.content_summary'))
                Text(this.summaryText()).fontSize(KomeliaDetailMetadata.rowFontSize)
                  .lineHeight(KomeliaDetailMetadata.rowLineHeight)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.width('100%')
            }

            this.chipGroup($r('app.string.publisher'), [this.series?.metadata?.publisher || ''], 'publisher',
              (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.PUBLISHER, value))
            this.chipGroup($r('app.string.genres'), this.series?.metadata?.genres || [], 'genre',
              (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.GENRE, value))
            this.chipGroup($r('app.string.tags'), this.book?.metadata?.tags || [], 'tag',
              (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.TAG, value))
            this.linkChipGroup()
            this.authorChipGroups()

            Column({ space: 10 }) {
              this.infoRow($r('app.string.size'), this.book?.size || '')
              this.infoRow($r('app.string.format'), bookMediaTypeLabel(this.book?.media?.mediaType || ''))
              if ((this.book?.metadata?.isbn || '').length > 0) {
                this.infoRow($r('app.string.book_isbn'), this.book?.metadata?.isbn || '')
              }
              this.infoRow($r('app.string.file_name'), this.book?.url || this.book?.name || '')
            }.id('oneshot_file_information')
              .width('100%')
            }
            .id('oneshot_detail_content')
            .width('100%').constraintSize({ maxWidth: KomeliaLayout.spec(this.windowClass).contentMaxWidth })
            .padding({
              left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              top: KomeliaLayout.spec(this.windowClass).pageVerticalPadding,
              bottom: KomeliaLayout.spec(this.windowClass).gridBottomPadding
            })
          }
          .width('100%').justifyContent(FlexAlign.Center).alignItems(VerticalAlign.Top)
        }.id('oneshot_detail_scroll').layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }
    .width('100%').height('100%').align(Alignment.Top).justifyContent(FlexAlign.Start)
    .id('oneshot_detail_page')
    .backgroundColor(KomeliaColors.surface())
    .bindSheet($$this.actionsSheetVisible, this.oneshotActionsSheet(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      showClose: false
    })
    .onAreaChange((_, area) => this.windowClass = KomeliaLayout.windowClass(Number(area.width)))
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }.width('100%').height('100%')
  }
}
