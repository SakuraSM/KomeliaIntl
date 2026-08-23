import { router } from '@kit.ArkUI';
import { BookCard, SeriesCard } from '../components/CoverCards';
import { EmptyState, LoadingState, PageTitle } from '../components/CommonUi';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { KomgaBook, KomgaSeries } from '../model/KomgaModels';

@Component
export struct SearchPage {
  @State query: string = '';
  @State selectedType: number = 0;
  @State isLoading: boolean = false;
  @State series: KomgaSeries[] = [];
  @State books: KomgaBook[] = [];
  @State columns: number = 3;

  private async search(): Promise<void> {
    const value = this.query.trim();
    if (value.length === 0) {
      this.series = [];
      this.books = [];
      return;
    }
    this.isLoading = true;
    try {
      if (this.selectedType === 0) {
        this.series = (await catalogRepository.series(value, 0, 60)).content;
      } else {
        this.books = (await catalogRepository.books(value, 0, 60)).content;
      }
    } finally {
      this.isLoading = false;
    }
  }

  private gridTemplate(): string {
    if (this.columns >= 7) return '1fr 1fr 1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }

  build() {
    Column({ space: 12 }) {
      PageTitle('搜索', '搜索系列或单本书籍')
      TextInput({ placeholder: '输入标题、作者或关键词', text: this.query })
        .height(52)
        .backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') })
        .borderRadius(16)
        .enterKeyType(EnterKeyType.Search)
        .onChange((value: string) => this.query = value)
        .onSubmit(() => this.search())

      Tabs({ index: this.selectedType, barPosition: BarPosition.Start }) {
        TabContent() {}.tabBar('系列')
        TabContent() {}.tabBar('书籍')
      }
      .height(48)
      .barMode(BarMode.Fixed)
      .onChange((index: number) => {
        this.selectedType = index;
        this.search();
      })

      if (this.isLoading) {
        LoadingState()
      } else if ((this.selectedType === 0 ? this.series.length : this.books.length) === 0) {
        EmptyState(this.query.length === 0 ? '输入关键词开始搜索' : '没有匹配的结果')
      } else {
        Grid() {
          if (this.selectedType === 0) {
            ForEach(this.series, (item: KomgaSeries) => {
              GridItem() {
                SeriesCard({
                  series: item,
                  onOpen: (id: string) => router.pushUrl({ url: 'pages/SeriesDetail', params: { seriesId: id } })
                })
              }
            }, (item: KomgaSeries) => item.id)
          } else {
            ForEach(this.books, (item: KomgaBook) => {
              GridItem() {
                BookCard({
                  book: item,
                  supporting: true,
                  onOpen: (id: string) => router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } })
                })
              }
            }, (item: KomgaBook) => item.id)
          }
        }
        .columnsTemplate(this.gridTemplate())
        .columnsGap(12)
        .rowsGap(12)
        .layoutWeight(1)
        .width('100%')
      }
    }
    .padding({ left: 12, right: 12, top: 16, bottom: 12 })
    .width('100%')
    .height('100%')
    .onAreaChange((_, area) => {
      const width = Number(area.width);
      this.columns = width >= 1000 ? 7 : width >= 720 ? 5 : width >= 520 ? 4 : 3;
    })
  }
}
