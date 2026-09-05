import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { bookDetailPage } from '../core/navigation/CatalogNavigation';
import { KomgaBook } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

@Entry
@Component
struct MediaAnalysisPage {
  @State books: KomgaBook[] = [];
  @State loading: boolean = true;
  @State errorMessage: string = '';
  @State page: number = 0;
  @State totalPages: number = 0;

  aboutToAppear(): void {
    this.load(0);
  }

  private text(resource: Resource): string {
    return localizedString(resource);
  }

  private async load(page: number): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      const result = await komgaClient.getBooksByMediaStatus(['ERROR', 'UNSUPPORTED'], page, 20);
      this.books = result.content;
      this.page = result.number;
      this.totalPages = result.totalPages;
    } catch (error) {
      this.errorMessage = (error as Error).message || this.text($r('app.string.media_analysis_load_failed'));
    } finally {
      this.loading = false;
    }
  }

  private statusLabel(status: string): ResourceStr {
    if (status === 'ERROR') return $r('app.string.media_status_error');
    if (status === 'UNSUPPORTED') return $r('app.string.media_status_unsupported');
    return status;
  }

  private commentLabel(comment: string): ResourceStr {
    if (comment === 'ERR_1000') return $r('app.string.media_error_1000');
    if (comment === 'ERR_1001') return $r('app.string.media_error_1001');
    if (comment === 'ERR_1002') return $r('app.string.media_error_1002');
    if (comment === 'ERR_1003') return $r('app.string.media_error_1003');
    if (comment === 'ERR_1004') return $r('app.string.media_error_1004');
    if (comment === 'ERR_1005') return $r('app.string.media_error_1005');
    if (comment === 'ERR_1006') return $r('app.string.media_error_1006');
    if (comment === 'ERR_1007') return $r('app.string.media_error_1007');
    if (comment === 'ERR_1008') return $r('app.string.media_error_1008');
    return comment.length > 0 ? comment : $r('app.string.unknown_error');
  }

  @Builder
  private bookCard(book: KomgaBook) {
    Column({ space: 8 }) {
      Text(book.name)
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.primary'))
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
      Text(book.seriesTitle)
        .fontSize(13)
        .fontColor($r('app.color.on_surface_variant'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
      Text(book.size.length > 0 ? `${book.media.mediaType} · ${book.size}` : book.media.mediaType)
        .fontSize(13)
        .fontColor($r('app.color.on_surface_variant'))
        .width('100%')
      Row({ space: 6 }) {
        Text(this.statusLabel(book.media.status)).fontSize(13).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.error'))
        Text(this.commentLabel(book.media.comment || ''))
          .fontSize(13)
          .fontColor($r('app.color.error'))
          .layoutWeight(1)
          .maxLines(3)
      }.width('100%').alignItems(VerticalAlign.Top)
    }
    .padding(14)
    .width('100%')
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .onClick(() => router.pushUrl({ url: bookDetailPage(book.oneshot), params: { bookId: book.id } }))
    .accessibilityText(book.name)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.media_analysis')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text($r('app.string.media_analysis_description')).fontSize(13)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      }.height(72).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load(this.page))
      } else if (this.books.length === 0) {
        EmptyState($r('app.string.media_analysis_empty'))
      } else {
        List({ space: 10 }) {
          ForEach(this.books, (book: KomgaBook) => {
            ListItem() { this.bookCard(book) }
          }, (book: KomgaBook) => book.id)
          if (this.totalPages > 1) {
            ListItem() {
              Row({ space: 12 }) {
                Button($r('app.string.previous_page')).height(44).enabled(this.page > 0)
                  .onClick(() => this.load(this.page - 1))
                Text(`${this.page + 1} / ${this.totalPages}`).fontSize(14)
                  .fontColor($r('app.color.on_surface_variant'))
                Button($r('app.string.next_page')).height(44).enabled(this.page + 1 < this.totalPages)
                  .onClick(() => this.load(this.page + 1))
              }.width('100%').justifyContent(FlexAlign.Center).padding({ top: 6, bottom: 24 })
            }
          }
        }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })
      }
    }
    .padding({ left: 12, right: 12 })
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .justifyContent(FlexAlign.Start)
    .backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
