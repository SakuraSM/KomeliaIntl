import { router } from '@kit.ArkUI';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { KomgaBook } from '../model/KomgaModels';
import { LOCAL_NAMESPACE } from '../model/LocalContentModels';
import { localLibraryService } from '../domain/service/LocalLibraryService';
import { readerEngine } from '../domain/service/ReaderEngine';
import { localizedString } from '../core/i18n/LocalizedResources';

interface LocalBookParams { bookId: string; }

@Entry
@Component
struct LocalBookDetail {
  @State book: KomgaBook | undefined = undefined;
  @State message: string = '';
  @State busy: boolean = false;
  private bookId: string = '';
  aboutToAppear(): void {
    this.bookId = requiredRouteId((router.getParams() as LocalBookParams)?.bookId);
    this.load();
  }
  onPageShow(): void { if (this.bookId.length > 0) this.load(); }
  private async load(): Promise<void> {
    try { this.book = await localLibraryService.book(this.bookId); }
    catch (_) { this.message = localizedString($r('app.string.local_library_missing')); }
  }

  private async markRead(completed: boolean): Promise<void> {
    if (this.busy || this.book === undefined) return;
    this.busy = true;
    try {
      if (completed) await readerEngine.updateProgress(LOCAL_NAMESPACE, this.bookId, this.book.media.pagesCount, true);
      else await readerEngine.clearProgress(LOCAL_NAMESPACE, this.bookId);
      await this.load();
    } catch (_) { this.message = localizedString($r('app.string.local_operation_failed')); }
    finally { this.busy = false; }
  }

  private read(): void {
    if (this.book === undefined) return;
    const book = this.book;
    router.pushUrl({ url: book.media.mediaType === 'application/epub+zip' ? 'pages/EpubReader' : 'pages/ImageReader',
      params: { bookId: book.id, bookTitle: book.metadata.title, seriesTitle: book.seriesTitle,
        initialPage: Math.max(0, (book.readProgress?.page || 1) - 1) } });
  }

  private exclude(): void {
    this.getUIContext().showAlertDialog({ title: $r('app.string.local_exclude_book'),
      message: $r('app.string.local_exclude_warning'), buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.local_exclude_book'), action: () => {
          localLibraryService.exclude(this.bookId).then(() => router.back(), () => {
            this.message = localizedString($r('app.string.local_operation_failed'));
          });
        } }
      ] });
  }

  @Builder
  private content() {
    Scroll() {
      Column({ space: 16 }) {
        Row() {
          Button() { Image($r('app.media.navigation_back')).width(24).height(24) }.width(48).height(48)
            .backgroundColor(Color.Transparent).accessibilityText($r('app.string.back')).onClick(() => router.back())
          Text($r('app.string.local_source_files')).fontSize(20).layoutWeight(1)
        }.width('100%').height(56)
        if (this.message.length > 0) Text(this.message).fontColor($r('app.color.error'))
        if (this.book !== undefined) {
          AuthenticatedImage({ endpoint: `/api/v1/books/${this.book.id}/thumbnail?v=${this.book.fileLastModified || ''}` })
            .width(180).height(256).borderRadius(16).clip(true)
          Text(this.book.metadata.title).fontSize(24).fontWeight(FontWeight.Bold).width('100%')
          Text(this.book.seriesTitle).fontSize(16).width('100%')
          Text(`${this.book.media.mediaType} · ${this.book.size}`).fontSize(13).width('100%')
          Button($r('app.string.local_read')).height(48).width('100%').id('local_book_read').onClick(() => this.read())
          Row({ space: 12 }) {
            Button($r('app.string.local_mark_read')).height(48).layoutWeight(1).enabled(!this.busy).onClick(() => this.markRead(true))
            Button($r('app.string.local_mark_unread')).height(48).layoutWeight(1).enabled(!this.busy).onClick(() => this.markRead(false))
          }.width('100%')
          Button($r('app.string.local_exclude_book')).height(48).width('100%').enabled(!this.busy).onClick(() => this.exclude())
        }
      }.width('100%').padding(16).alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').align(Alignment.Top).backgroundColor(KomeliaColors.surface()).id('local_book_detail')
  }
  build() {
    Stack({ alignContent: Alignment.TopStart }) { AppNavigationFrame({ content: () => { this.content(); } }) }
      .width('100%').height('100%')
  }
}
