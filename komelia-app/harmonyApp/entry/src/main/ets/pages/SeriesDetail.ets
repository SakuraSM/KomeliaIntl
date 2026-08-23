import { router } from '@kit.ArkUI';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { BookCard } from '../components/CoverCards';
import { LoadingState, SectionTitle } from '../components/CommonUi';
import { catalogRepository } from '../data/repository/DefaultRepositories';
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
  @State selecting: boolean = false;
  @State selectedBookIds: string[] = [];
  @State message: string = '';
  private seriesId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as SeriesParams;
    this.seriesId = params.seriesId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      const values = await Promise.all([
        catalogRepository.seriesDetail(this.seriesId), catalogRepository.seriesBooks(this.seriesId)
      ]);
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

  private toggleBook(id: string): void {
    this.selectedBookIds = this.selectedBookIds.includes(id)
      ? this.selectedBookIds.filter((value: string) => value !== id)
      : this.selectedBookIds.concat([id]);
  }

  private async batchBookAction(action: string): Promise<void> {
    if (this.selectedBookIds.length === 0) return;
    this.message = `正在处理 ${this.selectedBookIds.length} 本书籍…`;
    try {
      for (let index = 0; index < this.selectedBookIds.length; index++) {
        const id = this.selectedBookIds[index];
        if (action === 'deleteProgress') await komgaClient.deleteReadProgress(id);
        else await komgaClient.runBookAction(id, action);
      }
      this.message = '批量操作已完成';
      this.selectedBookIds = [];
      this.selecting = false;
      await this.load();
    } catch (error) { this.message = (error as Error).message; }
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
        Button('编辑').height(40).backgroundColor($r('app.color.primary_container'))
          .fontColor($r('app.color.primary')).onClick(() => router.pushUrl({
            url: 'pages/SeriesEdit', params: { seriesId: this.seriesId }
          }))
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

            Row() {
              SectionTitle('书籍', this.books.length)
              Button(this.selecting ? '取消' : '批量').height(40).backgroundColor(Color.Transparent)
                .fontColor($r('app.color.primary')).onClick(() => {
                  this.selecting = !this.selecting;
                  if (!this.selecting) this.selectedBookIds = [];
                })
            }.width('100%')
            if (this.selecting) {
              Flex({ wrap: FlexWrap.Wrap }) {
                Text(`已选 ${this.selectedBookIds.length} 本`).fontSize(14).margin({ right: 8, bottom: 8 })
                Button('清除进度').height(40).margin({ right: 8, bottom: 8 })
                  .onClick(() => this.batchBookAction('deleteProgress'))
                Button('分析').height(40).margin({ right: 8, bottom: 8 })
                  .onClick(() => this.batchBookAction('analyze'))
                Button('刷新元数据').height(40).margin({ right: 8, bottom: 8 })
                  .onClick(() => this.batchBookAction('metadata/refresh'))
              }.width('100%').padding(10).backgroundColor($r('app.color.surface_high')).borderRadius(16)
            }
            Grid() {
              ForEach(this.books, (book: KomgaBook) => {
                GridItem() {
                  Stack({ alignContent: Alignment.TopEnd }) {
                    BookCard({ book: book, onOpen: (id: string) => {
                      if (this.selecting) this.toggleBook(id);
                      else router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } });
                    } })
                    if (this.selecting) {
                      Text(this.selectedBookIds.includes(book.id) ? '✓' : '○').fontSize(18)
                        .fontColor(this.selectedBookIds.includes(book.id) ? Color.White : $r('app.color.primary'))
                        .width(32).height(32).textAlign(TextAlign.Center)
                        .backgroundColor(this.selectedBookIds.includes(book.id) ? $r('app.color.primary') : '#EFFFFFFF')
                        .borderRadius(16).margin(8).onClick(() => this.toggleBook(book.id))
                    }
                  }.width('100%')
                }
              }, (book: KomgaBook) => book.id)
            }
            .columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12).width('100%')
            if (this.message.length > 0) {
              Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
            }
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
