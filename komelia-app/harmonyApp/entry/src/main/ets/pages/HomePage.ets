import { router } from '@kit.ArkUI';
import { BookCard, SeriesCard } from '../components/CoverCards';
import { EmptyState, PageTitle, SectionTitle } from '../components/CommonUi';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';
import { defaultHomeGroups, HomeGroup, parseHomeGroups } from '../model/HomeGroupModels';
import { AppPreferences } from '../persistence/AppPreferences';
import { AppState } from '../state/AppState';

@Component
export struct HomePage {
  @ObjectLink state: AppState;
  @State columns: number = 3;
  @State groups: HomeGroup[] = defaultHomeGroups();

  aboutToAppear(): void {
    this.restoreGroups();
  }

  private async restoreGroups(): Promise<void> {
    this.groups = parseHomeGroups(await AppPreferences.getString('homeGroups'));
  }

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

  @Builder
  private latestGrid() {
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
    .columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12).width('100%')
  }

  @Builder
  private seriesGrid() {
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
    .columnsTemplate(this.gridTemplate()).columnsGap(12).rowsGap(12).width('100%')
  }

  @Builder
  private groupContent(group: HomeGroup) {
    if (group.id === 'onDeck') {
      SectionTitle(group.label, this.state.homeFeed.onDeck.length)
      if (this.state.homeFeed.onDeck.length === 0) {
        EmptyState('还没有正在阅读的书籍')
      } else {
        this.bookGrid()
      }
    } else if (group.id === 'latest') {
      SectionTitle(group.label, this.state.homeFeed.latest.length)
      if (this.state.homeFeed.latest.length === 0) {
        EmptyState('还没有最近添加的书籍')
      } else {
        this.latestGrid()
      }
    } else if (group.id === 'series') {
      SectionTitle(group.label, this.state.homeFeed.series.length)
      if (this.state.homeFeed.series.length === 0) {
        EmptyState('还没有系列')
      } else {
        this.seriesGrid()
      }
    }
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        PageTitle('首页', '继续上次的阅读，或发现刚加入的内容')

        ForEach(this.groups, (group: HomeGroup) => {
          if (group.enabled) {
            this.groupContent(group)
          }
        }, (group: HomeGroup) => group.id)
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
