import { router } from '@kit.ArkUI';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { BookCard } from '../components/CoverCards';
import { LoadingState, SectionTitle } from '../components/CommonUi';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface SeriesParams {
  seriesId: string;
}

@Entry
@Component
struct SeriesDetail {
  @State series?: KomgaSeries;
  @State books: KomgaBook[] = [];
  @State isLoading: boolean = true;
  @State columns: number = 3;
  private seriesId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as SeriesParams;
    this.seriesId = params.seriesId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const values = await Promise.all([komgaClient.getSeries(this.seriesId), komgaClient.getSeriesBooks(this.seriesId)]);
      this.series = values[0];
      this.books = values[1];
    } finally {
      this.isLoading = false;
    }
  }

  private gridTemplate(): string {
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }

  build() {
    Column() {
      Row({ space: 12 }) {
        Button({ type: ButtonType.Circle }) {
          Text('‹').fontSize(34)
        }
        .width(44).height(44).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text(this.series?.metadata?.title || this.series?.name || '系列详情')
          .fontSize(20).fontWeight(FontWeight.Bold).maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
      }
      .height(56).width('100%').padding({ left: 8, right: 12 })

      if (this.isLoading || this.series === undefined) {
        LoadingState()
      } else {
        Scroll() {
          Column({ space: 20 }) {
            Row({ space: 20 }) {
              Stack() {
                AuthenticatedImage({ endpoint: `/api/v1/series/${this.seriesId}/thumbnail` })
              }
              .width(132).height(188).borderRadius(16).clip(true)
              .border({ width: 1, color: $r('app.color.outline') })

              Column({ space: 10 }) {
                Text(this.series?.metadata?.title || this.series?.name || '')
                  .fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface'))
                  .maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
                Text(`${this.series?.booksCount || 0} 本 · 未读 ${this.series?.booksUnreadCount || 0}`)
                  .fontSize(15).fontColor($r('app.color.on_surface_variant')).width('100%')
                if ((this.series?.metadata?.publisher || '').length > 0) {
                  Text(this.series?.metadata?.publisher || '')
                    .fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
                }
                if ((this.series?.metadata?.status || '').length > 0) {
                  Text(this.series?.metadata?.status || '')
                    .fontSize(13).fontColor($r('app.color.primary'))
                    .backgroundColor($r('app.color.primary_container'))
                    .padding({ left: 10, right: 10, top: 5, bottom: 5 }).borderRadius(12)
                }
              }
              .layoutWeight(1).alignItems(HorizontalAlign.Start)
            }
            .width('100%').alignItems(VerticalAlign.Top)

            if ((this.series?.metadata?.summary || '').length > 0) {
              Text(this.series?.metadata?.summary || '')
                .fontSize(15).lineHeight(23).fontColor($r('app.color.on_surface_variant')).width('100%')
            }

            SectionTitle('书籍', this.books.length)
            Grid() {
              ForEach(this.books, (book: KomgaBook) => {
                GridItem() {
                  BookCard({
                    book: book,
                    onOpen: (id: string) => router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } })
                  })
                }
              }, (book: KomgaBook) => book.id)
            }
            .columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12).width('100%')
          }
          .width('100%').padding({ left: 12, right: 12, bottom: 32 })
          .onAreaChange((_, area) => {
            const width = Number(area.width);
            this.columns = width >= 900 ? 5 : width >= 600 ? 4 : 3;
          })
        }
        .layoutWeight(1).width('100%')
      }
    }
    .width('100%').height('100%').backgroundColor($r('app.color.surface'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
