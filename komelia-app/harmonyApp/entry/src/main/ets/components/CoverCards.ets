import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { AuthenticatedImage } from './AuthenticatedImage';

@Component
export struct SeriesCard {
  @Prop series: KomgaSeries;
  onOpen: (id: string) => void = () => {};

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        AuthenticatedImage({ endpoint: `/api/v1/series/${this.series.id}/thumbnail` })
        if (this.series.booksUnreadCount > 0) {
          Text(`${this.series.booksUnreadCount}`)
            .fontSize(12)
            .fontColor($r('app.color.on_surface'))
            .backgroundColor($r('app.color.primary_container'))
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .borderRadius(12)
            .margin(8)
        }
      }
      .width('100%')
      .aspectRatio(0.703)
      .clip(true)

      Text(this.series.metadata?.title || this.series.name)
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.on_surface'))
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
        .height(52)
        .padding({ left: 10, right: 10 })
        .textAlign(TextAlign.Start)
    }
    .width('100%')
    .backgroundColor($r('app.color.surface_high'))
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .clip(true)
    .onClick(() => this.onOpen(this.series.id))
  }
}

@Component
export struct BookCard {
  @Prop book: KomgaBook;
  @Prop supporting: boolean = false;
  onOpen: (id: string) => void = () => {};

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        AuthenticatedImage({ endpoint: `/api/v1/books/${this.book.id}/thumbnail` })
        if (this.book.readProgress !== undefined && this.book.readProgress.completed) {
          Text('✓')
            .fontSize(16)
            .fontColor($r('app.color.on_surface'))
            .backgroundColor($r('app.color.primary_container'))
            .width(26).height(26).textAlign(TextAlign.Center).borderRadius(13)
            .margin(8)
        }
      }
      .width('100%')
      .aspectRatio(0.703)
      .clip(true)

      Column() {
        Text(this.book.metadata?.title || this.book.name)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_surface'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        if (this.supporting) {
          Text(this.book.seriesTitle)
            .fontSize(13)
            .fontColor($r('app.color.on_surface_variant'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
        }
      }
      .height(this.supporting ? 72 : 52)
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .padding({ left: 10, right: 10 })
    }
    .width('100%')
    .backgroundColor($r('app.color.surface_high'))
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .clip(true)
    .onClick(() => this.onOpen(this.book.id))
  }
}
