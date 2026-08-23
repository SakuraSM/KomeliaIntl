import { router } from '@kit.ArkUI';
import { AuthenticatedImage } from '../components/AuthenticatedImage';
import { LoadingState, SectionTitle } from '../components/CommonUi';
import { KomgaAuthor, KomgaBook } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface BookParams {
  bookId: string;
}

@Entry
@Component
struct BookDetail {
  @State book?: KomgaBook;
  @State isLoading: boolean = true;
  private bookId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as BookParams;
    this.bookId = params.bookId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      this.book = await komgaClient.getBook(this.bookId);
    } finally {
      this.isLoading = false;
    }
  }

  private openReader(): void {
    const mediaType = (this.book?.media?.mediaType || '').toLowerCase();
    const target = mediaType.includes('epub') ? 'pages/EpubReader' : 'pages/ImageReader';
    router.pushUrl({
      url: target,
      params: { bookId: this.bookId, initialPage: this.book?.readProgress?.page || 0 }
    });
  }

  @Builder
  private infoRow(label: string, value: string) {
    Row() {
      Text(label).width(92).fontSize(14).fontColor($r('app.color.on_surface_variant'))
      Text(value).layoutWeight(1).fontSize(14).fontColor($r('app.color.on_surface'))
    }
    .width('100%').alignItems(VerticalAlign.Top)
  }

  build() {
    Column() {
      Row({ space: 12 }) {
        Button({ type: ButtonType.Circle }) {
          Text('‹').fontSize(34)
        }
        .width(44).height(44).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text(this.book?.metadata?.title || this.book?.name || '书籍详情')
          .fontSize(20).fontWeight(FontWeight.Bold).maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
      }
      .height(56).width('100%').padding({ left: 8, right: 12 })

      if (this.isLoading || this.book === undefined) {
        LoadingState()
      } else {
        Scroll() {
          Column({ space: 20 }) {
            Row({ space: 20 }) {
              Stack() {
                AuthenticatedImage({ endpoint: `/api/v1/books/${this.bookId}/thumbnail` })
              }
              .width(132).height(188).borderRadius(16).clip(true)
              .border({ width: 1, color: $r('app.color.outline') })

              Column({ space: 10 }) {
                Text(this.book?.metadata?.title || this.book?.name || '')
                  .fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface'))
                  .maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
                Text(this.book?.seriesTitle || '')
                  .fontSize(15).fontColor($r('app.color.primary')).maxLines(2).width('100%')
                Text(`第 ${this.book?.metadata?.number || this.book?.number || '-'} 本 · ${this.book?.media?.pagesCount || 0} 页`)
                  .fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
                Button('阅读')
                  .height(48).borderRadius(16).backgroundColor($r('app.color.primary'))
                  .onClick(() => this.openReader())
              }
              .layoutWeight(1).alignItems(HorizontalAlign.Start)
            }
            .width('100%').alignItems(VerticalAlign.Top)

            if ((this.book?.metadata?.summary || '').length > 0) {
              Column({ space: 8 }) {
                SectionTitle('内容简介')
                Text(this.book?.metadata?.summary || '')
                  .fontSize(15).lineHeight(23).fontColor($r('app.color.on_surface_variant')).width('100%')
              }.width('100%')
            }

            Column({ space: 12 }) {
              SectionTitle('文件信息')
              this.infoRow('编剧', (this.book?.metadata?.authors || [])
                .map((item: KomgaAuthor) => item.name).join('、') || '-')
              this.infoRow('大小', this.book?.size || '-')
              this.infoRow('格式', this.book?.media?.mediaType || '-')
              this.infoRow('文件', this.book?.name || '-')
            }
            .width('100%').padding(16).backgroundColor($r('app.color.surface_high'))
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
          }
          .width('100%').constraintSize({ maxWidth: 900 })
          .padding({ left: 12, right: 12, bottom: 32 })
        }
        .layoutWeight(1).width('100%')
      }
    }
    .width('100%').height('100%').backgroundColor($r('app.color.surface'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
