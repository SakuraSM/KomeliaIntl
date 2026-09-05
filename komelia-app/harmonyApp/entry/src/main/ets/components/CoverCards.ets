import {
  CoverCaptionVariant, KomeliaColors, KomeliaCoverCaption
} from '../core/design/KomeliaDesign';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { isBookCompleted } from '../model/BookPresentation';
import { AuthenticatedImage } from './AuthenticatedImage';

@Component
export struct SeriesCard {
  @Prop series: KomgaSeries;
  @Prop compact: boolean = true;
  onOpen: (id: string) => void = () => {};

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        AuthenticatedImage({ endpoint: `/api/v1/series/${this.series.id}/thumbnail?v=${this.series.lastModified || ''}` })
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

      Column() {
        Text(this.series.metadata?.title || this.series.name)
          .fontSize(KomeliaCoverCaption.titleFontSize(this.compact))
          .lineHeight(KomeliaCoverCaption.titleLineHeight(this.compact))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_surface'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
      }
      .width('100%')
      .height(KomeliaCoverCaption.height(CoverCaptionVariant.TITLE_ONLY, this.compact))
      .padding({ left: 10, right: 10, top: 6, bottom: 6 })
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .backgroundColor(KomeliaColors.surfaceHigh())
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
  @Prop compact: boolean = true;
  onOpen: (id: string) => void = () => {};

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopEnd }) {
        AuthenticatedImage({ endpoint: `/api/v1/books/${this.book.id}/thumbnail?v=${this.book.fileLastModified || ''}` })
        if (isBookCompleted(this.book)) {
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
          .fontSize(KomeliaCoverCaption.titleFontSize(this.compact))
          .lineHeight(KomeliaCoverCaption.titleLineHeight(this.compact))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_surface'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        if (this.supporting) {
          Text(this.book.seriesTitle)
            .fontSize(12)
            .lineHeight(KomeliaCoverCaption.supportingLineHeight(this.compact))
            .fontColor($r('app.color.on_surface_variant'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
        }
      }
      .height(KomeliaCoverCaption.height(this.supporting ? CoverCaptionVariant.TITLE_WITH_SUPPORTING :
        CoverCaptionVariant.TITLE_ONLY, this.compact))
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .padding({ left: 10, right: 10, top: 6, bottom: 6 })
    }
    .width('100%')
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
    .clip(true)
    .onClick(() => this.onOpen(this.book.id))
  }
}
