import { router } from '@kit.ArkUI';
import { SeriesCard } from '../components/CoverCards';
import { EmptyState, LoadingState, PageTitle } from '../components/CommonUi';
import { KomgaLibrary, KomgaSeries } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

@Component
export struct LibraryPage {
  @ObjectLink state: AppState;
  @State series: KomgaSeries[] = [];
  @State isLoading: boolean = false;
  @State selectedLibrary: number = 0;
  @State columns: number = 3;
  @State query: string = '';

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    this.isLoading = true;
    try {
      const page = await komgaClient.searchSeries(this.query, 0, 100);
      if (this.selectedLibrary === 0) {
        this.series = page.content;
      } else {
        const libraryId = this.state.libraries[this.selectedLibrary - 1].id;
        this.series = page.content.filter((item: KomgaSeries) => item.libraryId === libraryId);
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
      PageTitle('书库', `${this.series.length} 个系列`)

      Row({ space: 8 }) {
        Select([{ value: '全部书库' }].concat(
          this.state.libraries.map((item: KomgaLibrary) => ({ value: item.name }))))
          .selected(this.selectedLibrary)
          .value(this.selectedLibrary === 0 ? '全部书库' : this.state.libraries[this.selectedLibrary - 1].name)
          .height(44)
          .backgroundColor($r('app.color.surface_high'))
          .onSelect((index: number) => {
            this.selectedLibrary = index;
            this.load();
          })

        TextInput({ placeholder: '搜索系列', text: this.query })
          .layoutWeight(1)
          .height(44)
          .backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.query = value)
          .onSubmit(() => this.load())
      }
      .width('100%')

      if (this.isLoading) {
        LoadingState()
      } else if (this.series.length === 0) {
        EmptyState('当前筛选下没有系列')
      } else {
        Grid() {
          ForEach(this.series, (item: KomgaSeries) => {
            GridItem() {
              SeriesCard({
                series: item,
                onOpen: (id: string) => router.pushUrl({ url: 'pages/SeriesDetail', params: { seriesId: id } })
              })
            }
          }, (item: KomgaSeries) => item.id)
        }
        .columnsTemplate(this.gridTemplate())
        .columnsGap(12)
        .rowsGap(12)
        .layoutWeight(1)
        .width('100%')
        .edgeEffect(EdgeEffect.Spring)
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
