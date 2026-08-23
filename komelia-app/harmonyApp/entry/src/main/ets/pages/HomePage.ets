import { router } from '@kit.ArkUI';
import { BookCard, SeriesCard } from '../components/CoverCards';
import { EmptyState, PageTitle, SectionTitle } from '../components/CommonUi';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { AppState } from '../state/AppState';

@Component
export struct HomePage {
  @ObjectLink state: AppState;
  @State columns: number = 3;

  private gridTemplate(): string {
    if (this.columns >= 7) return '1fr 1fr 1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }

  @Builder
  private bookGrid() {
    Grid() {
      ForEach(this.state.homeFeed.onDeck, (book: KomgaBook) => {
        GridItem() {
          BookCard({
            book: book,
            supporting: true,
            onOpen: (id: string) => router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } })
          })
        }
      }, (book: KomgaBook) => book.id)
    }
    .columnsTemplate(this.gridTemplate())
    .columnsGap(12)
    .rowsGap(12)
    .width('100%')
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        PageTitle('首页', '继续上次的阅读，或发现刚加入的内容')

        SectionTitle('继续阅读', this.state.homeFeed.onDeck.length)
        if (this.state.homeFeed.onDeck.length === 0) {
          EmptyState('还没有正在阅读的书籍')
        } else {
          this.bookGrid()
        }

        SectionTitle('最近添加的书籍', this.state.homeFeed.latest.length)
        Grid() {
          ForEach(this.state.homeFeed.latest, (book: KomgaBook) => {
            GridItem() {
              BookCard({
                book: book,
                supporting: true,
                onOpen: (id: string) => router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } })
              })
            }
          }, (book: KomgaBook) => book.id)
        }
        .columnsTemplate(this.gridTemplate())
        .columnsGap(12)
        .rowsGap(12)
        .width('100%')

        SectionTitle('系列', this.state.homeFeed.series.length)
        Grid() {
          ForEach(this.state.homeFeed.series, (series: KomgaSeries) => {
            GridItem() {
              SeriesCard({
                series: series,
                onOpen: (id: string) => router.pushUrl({ url: 'pages/SeriesDetail', params: { seriesId: id } })
              })
            }
          }, (series: KomgaSeries) => series.id)
        }
        .columnsTemplate(this.gridTemplate())
        .columnsGap(12)
        .rowsGap(12)
        .width('100%')
      }
      .padding({ left: 12, right: 12, top: 16, bottom: 28 })
      .width('100%')
      .onAreaChange((_, area) => {
        const width = Number(area.width);
        this.columns = width >= 1000 ? 7 : width >= 720 ? 5 : width >= 520 ? 4 : 3;
      })
    }
    .width('100%')
    .height('100%')
    .edgeEffect(EdgeEffect.Spring)
  }
}
