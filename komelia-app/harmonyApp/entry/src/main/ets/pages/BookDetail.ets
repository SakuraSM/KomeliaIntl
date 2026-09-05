import { KomeliaColors, KomeliaDetailCover, KomeliaDetailMetadata, KomeliaLayout,
  WindowClass } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import measure from '@ohos.measure';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { BookReadControl } from '../components/BookReadControl';
import { ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { namespaceOf } from '../data/local/DatabaseModels';
import { catalogRepository, offlineRepository } from '../data/repository/DefaultRepositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { ReaderCapabilities } from '../domain/service/ReaderCapabilities';
import { readerEngine } from '../domain/service/ReaderEngine';
import { authorRoleResource, bookMediaTypeLabel, bookReadingSummary, BookAuthorGroup, groupBookAuthors,
  isLocalDownloadOutdated } from '../model/BookPresentation';
import { KomgaAuthor, KomgaBook, KomgaWebLink } from '../model/KomgaModels';
import { libraryMetadataNavigationRequest, LibraryMetadataFilterKind } from '../model/LibraryPresentation';
import { formatDateTime } from '../model/UserPresentation';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

interface BookParams {
  bookId: string;
}

@Entry
@Component
struct BookDetail {
  @State book: KomgaBook | undefined = undefined;
  @State isLoading: boolean = true;
  @State downloadLabel: string = '';
  @State downloaded: boolean = false;
  @State downloadInProgress: boolean = false;
  @State downloadPaused: boolean = false;
  @State localDownloadOutdated: boolean = false;
  @State actionsSheetVisible: boolean = false;
  @State actionBusy: boolean = false;
  @State errorMessage: string = '';
  @State windowClass: WindowClass = WindowClass.COMPACT;
  @State pageWidth: number = 360;
  @State summaryExpanded: boolean = false;
  private bookId: string = '';

  private coverWidth(): number {
    return KomeliaDetailCover.width(this.windowClass);
  }

  private coverHeight(): number {
    return KomeliaDetailCover.height(this.windowClass);
  }
  private downloadJobId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as BookParams | undefined;
    this.bookId = requiredRouteId(params?.bookId);
    this.downloadLabel = localizedString($r('app.string.download'));
    if (this.bookId.length > 0) this.load();
    else {
      this.isLoading = false;
      this.errorMessage = localizedString($r('app.string.book_detail_load_failed'));
    }
  }

  private async load(): Promise<void> {
    this.isLoading = true;
    this.errorMessage = '';
    this.downloaded = false;
    this.localDownloadOutdated = false;
    this.downloadLabel = localizedString($r('app.string.download'));
    try {
      this.book = await catalogRepository.bookDetail(this.bookId);
      if (this.book.oneshot) {
        router.replaceUrl({ url: 'pages/OneshotDetail', params: { bookId: this.bookId } });
        return;
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
    const target = isEpub ? 'pages/EpubReader' : 'pages/ImageReader';
    router.pushUrl({
      url: target,
      params: {
        bookId: this.bookId,
        initialPage: Math.max(0, (this.book?.readProgress?.page || 1) - 1),
        trackProgress: trackProgress,
        bookTitle: this.book?.metadata?.title || this.book?.name || '',
        seriesTitle: this.book?.seriesTitle || ''
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
      if (this.downloadPaused) {
        this.downloadLabel = localizedString($r('app.string.resume'));
      } else {
        this.downloadLabel = localizedString($r('app.string.download_failed_retry'), (error as Error).message);
      }
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
    context.openLink(link.url).catch(() => {
      this.showToast($r('app.string.link_open_failed'));
    });
  }

  private readingProgressText(): string {
    const book = this.book;
    if (book === undefined) return '';
    const summary = bookReadingSummary(book);
    if (summary === undefined) return '';
    return localizedString($r('app.string.book_read_progress_value'), summary.percentage, summary.pagesLeft);
  }

  private summaryNeedsToggle(): boolean {
    const summary = this.book?.metadata?.summary || '';
    if (summary.length === 0) return false;
    const layout = KomeliaLayout.spec(this.windowClass);
    const contentWidth = Math.min(this.pageWidth, layout.contentMaxWidth);
    const textWidth = Math.max(1, contentWidth - layout.pageHorizontalPadding * 2);
    const measured = measure.measureTextSize({
      textContent: summary,
      constraintWidth: `${textWidth}vp`,
      fontSize: KomeliaDetailMetadata.rowFontSize,
      lineHeight: KomeliaDetailMetadata.rowLineHeight
    });
    return px2vp(Number(measured.height)) > KomeliaDetailMetadata.rowLineHeight * 9 + 0.5;
  }

  private distinct(values: string[]): string[] {
    return values.map((value: string): string => value.trim())
      .filter((value: string): boolean => value.length > 0)
      .filter((value: string, index: number, all: string[]): boolean => all.indexOf(value) === index);
  }

  private openMetadataFilter(kind: LibraryMetadataFilterKind, value: string, role: string = ''): void {
    const book = this.book;
    if (book === undefined || value.trim().length === 0) return;
    AppState.getInstance().openLibrary(
      libraryMetadataNavigationRequest(book.libraryId, kind, value, role));
    router.back({ url: 'pages/Index' });
  }

  @Builder
  private metadataChipGroup(label: ResourceStr, values: string[], idPrefix: string,
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
              .id(`book_filter_${idPrefix}_${index}`).focusable(true)
              .accessibilityText(value).onClick(() => onOpen(value))
          }, (value: string) => value)
        }.layoutWeight(1)
      }.width('100%').alignItems(VerticalAlign.Top)
    }
  }

  @Builder
  private authorChipGroups() {
    ForEach(groupBookAuthors(this.book?.metadata?.authors || []), (group: BookAuthorGroup) => {
      this.metadataChipGroup(authorRoleResource(group.role),
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
              .id(`book_link_${index}`).focusable(true)
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
  private expandableSummary() {
    if ((this.book?.metadata?.summary || '').length > 0) {
      Column() {
        Text(this.book?.metadata?.summary || '')
          .fontSize(KomeliaDetailMetadata.rowFontSize).lineHeight(KomeliaDetailMetadata.rowLineHeight)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
          .maxLines(this.summaryExpanded ? 999 : 9)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .id('book_detail_summary')
        if (this.summaryNeedsToggle() || this.summaryExpanded) {
          Button(this.summaryExpanded ? $r('app.string.collapse') : $r('app.string.expand'))
            .height(48).width('100%').backgroundColor(Color.Transparent)
            .fontColor($r('app.color.on_surface'))
            .onClick(() => this.summaryExpanded = !this.summaryExpanded)
        }
      }.width('100%')
    }
  }

  @Builder
  private bookActionsSheet() {
    Column({ space: 8 }) {
      Text(this.book?.metadata?.title || this.book?.name || '')
        .fontSize(18).fontWeight(FontWeight.Bold).maxLines(2).width('100%')
      if (AppState.getInstance().isOnline && AppState.getInstance().canManageServer()) {
        Button($r('app.string.analyze')).height(48).width('100%')
          .id('book_action_analyze').enabled(!this.actionBusy)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.performBookAction('analyze'))
        Button($r('app.string.refresh_metadata')).height(48).width('100%')
          .id('book_action_refresh').enabled(!this.actionBusy)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.performBookAction('metadata/refresh'))
        Button($r('app.string.add_to_read_list')).height(48).width('100%')
          .id('book_action_read_list').enabled(!this.actionBusy)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => {
            this.actionsSheetVisible = false;
            router.pushUrl({ url: 'pages/AddToGroup', params: { kind: 'readList', targetId: this.bookId } });
          })
      }
      if (this.book?.readProgress?.completed !== true) {
        Button($r('app.string.mark_as_read')).height(48).width('100%')
          .id('book_action_mark_read').enabled(!this.actionBusy)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.performBookAction('markRead'))
      }
      if (this.book?.readProgress !== undefined &&
        this.book?.readProgress !== null) {
        Button($r('app.string.mark_as_unread')).height(48).width('100%')
          .id('book_action_mark_unread').enabled(!this.actionBusy)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.performBookAction('markUnread'))
      }
      if (this.downloaded) {
        Button($r('app.string.book_delete_downloaded')).height(48).width('100%')
          .id('book_action_delete_download').enabled(!this.actionBusy)
          .backgroundColor($r('app.color.error_container')).fontColor($r('app.color.on_error_container'))
          .onClick(() => this.confirmDeleteDownload())
      }
    }.width('100%').padding(16).alignItems(HorizontalAlign.Start)
  }

  @Builder
  private infoRow(label: ResourceStr, value: string) {
    Row({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
      Text(label).width(KomeliaDetailMetadata.labelWidth(this.windowClass))
        .fontSize(KomeliaDetailMetadata.rowFontSize).lineHeight(KomeliaDetailMetadata.rowLineHeight)
        .fontColor($r('app.color.on_surface_variant'))
      Text(value).layoutWeight(1).fontSize(KomeliaDetailMetadata.rowFontSize)
        .lineHeight(KomeliaDetailMetadata.rowLineHeight).fontColor($r('app.color.on_surface'))
    }
    .width('100%').alignItems(VerticalAlign.Top)
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
        .accessibilityText($r('app.string.back'))
        Text(this.book?.metadata?.title || this.book?.name || localizedString($r('app.string.book_detail')))
          .fontSize(20).fontWeight(FontWeight.Bold).maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
        if (this.book !== undefined) {
          Button({ type: ButtonType.Circle }) {
            Image($r('app.media.detail_more_vertical')).width(24).height(24)
              .fillColor($r('app.color.on_surface'))
          }
          .width(48).height(48).backgroundColor(Color.Transparent)
          .id('book_detail_more').accessibilityText($r('app.string.book_actions'))
          .onClick(() => this.actionsSheetVisible = true)
          if (AppState.getInstance().canManageServer() && AppState.getInstance().isOnline) {
            Button({ type: ButtonType.Circle }) {
              Image($r('app.media.detail_edit')).width(24).height(24)
                .fillColor($r('app.color.on_surface'))
            }
            .width(48).height(48).backgroundColor(Color.Transparent)
            .id('book_detail_edit').accessibilityText($r('app.string.edit_book'))
            .onClick(() => router.pushUrl({ url: 'pages/BookEdit', params: { bookId: this.bookId } }))
          }
        }
      }
      .height(48).width('100%').padding({
        left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
        right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding
      })

      if (this.isLoading) {
        LoadingState()
      } else if (this.errorMessage.length > 0 || this.book === undefined) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Row() {
            Column({ space: KomeliaLayout.spec(this.windowClass).itemSpacing }) {
            Row({ space: KomeliaLayout.spec(this.windowClass).gridSpacing }) {
              Stack() {
                AuthenticatedImage({ endpoint: `/api/v1/books/${this.bookId}/thumbnail` })
              }
              .id('book_detail_cover')
              .width(this.coverWidth()).height(this.coverHeight()).borderRadius(16).clip(true)
              .border({ width: 1, color: $r('app.color.outline') })

              Column({ space: KomeliaLayout.spec(this.windowClass).controlSpacing }) {
                if ((this.book?.seriesTitle || '').length > 0) {
                  Button() {
                    Row({ space: 3 }) {
                      Image($r('app.media.nav_library')).width(24).height(24)
                        .fillColor($r('app.color.primary'))
                      Text(this.book?.seriesTitle || '').fontSize(14).fontColor($r('app.color.primary'))
                        .decoration({ type: TextDecorationType.Underline })
                        .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                    }
                  }
                    .height(40).padding({ left: 24, right: 24 })
                    .fontColor($r('app.color.primary')).backgroundColor($r('app.color.primary_container'))
                    .onClick(() => {
                      const seriesId = this.book?.seriesId || '';
                      if (seriesId.length > 0) router.pushUrl({
                        url: 'pages/SeriesDetail', params: { seriesId: seriesId }
                      });
                    })
                }
                Text(localizedString($r('app.string.volume_page_summary'),
                  this.book?.metadata?.number || this.book?.number || '-', this.book?.media?.pagesCount || 0))
                  .fontSize(16).lineHeight(24).fontColor($r('app.color.on_surface')).width('100%')
                Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
                  if (this.book?.deleted === true) {
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
                  BookReadControl(this.book?.deleted !== true && this.book?.remoteFileUnavailable !== true,
                    'book_detail', () => this.openReader(true), () => this.openReader(false))
                  Button() {
                    Row({ space: 3 }) {
                      Image($r('app.media.detail_download')).width(24).height(24)
                        .fillColor($r('app.color.primary'))
                      Text(this.downloadLabel).fontSize(14).fontColor($r('app.color.primary'))
                    }
                  }
                    .height(40).borderRadius(20).backgroundColor($r('app.color.primary_container'))
                    .id('book_detail_download')
                    .enabled((!this.downloaded || this.localDownloadOutdated) && AppState.getInstance().isOnline)
                    .onClick(() => this.download())
                    .responseRegion({ x: 0, y: -4, width: '100%', height: 48 })
                }
              }
              .layoutWeight(1).alignItems(HorizontalAlign.Start)
            }
            .width('100%').alignItems(VerticalAlign.Top)

            this.expandableSummary()

            this.metadataChipGroup($r('app.string.tags'), this.book?.metadata?.tags || [], 'tag',
              (value: string) => this.openMetadataFilter(LibraryMetadataFilterKind.TAG, value))
            this.linkChipGroup()
            this.authorChipGroups()

            Column({ space: 10 }) {
              this.infoRow($r('app.string.size'), this.book?.size || '-')
              this.infoRow($r('app.string.format'), bookMediaTypeLabel(this.book?.media?.mediaType || '-'))
              if ((this.book?.metadata?.isbn || '').length > 0) {
                this.infoRow($r('app.string.book_isbn'), this.book?.metadata?.isbn || '')
              }
              this.infoRow($r('app.string.file_name'), this.book?.url || this.book?.name || '-')
            }
            .id('book_file_information')
            .width('100%')
            }
            .id('book_detail_content')
            .width('100%').constraintSize({ maxWidth: KomeliaLayout.spec(this.windowClass).contentMaxWidth })
            .padding({
              left: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              right: KomeliaLayout.spec(this.windowClass).pageHorizontalPadding,
              top: KomeliaLayout.spec(this.windowClass).pageVerticalPadding,
              bottom: KomeliaLayout.spec(this.windowClass).gridBottomPadding
            })
          }
          .width('100%').justifyContent(FlexAlign.Center).alignItems(VerticalAlign.Top)
        }
        .id('book_detail_scroll').layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }
    .width('100%').height('100%').align(Alignment.Top).justifyContent(FlexAlign.Start)
    .id('book_detail_page')
    .backgroundColor(KomeliaColors.surface())
    .bindSheet($$this.actionsSheetVisible, this.bookActionsSheet(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      showClose: false
    })
    .onAreaChange((_, area) => {
      this.pageWidth = Number(area.width);
      this.windowClass = KomeliaLayout.windowClass(this.pageWidth);
    })
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
