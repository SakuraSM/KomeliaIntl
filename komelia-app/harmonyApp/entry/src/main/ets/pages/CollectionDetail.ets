import { router } from '@kit.ArkUI';
import { SeriesCard } from '../components/CoverCards';
import { EmptyState, LoadingState } from '../components/CommonUi';
import { KomgaSeries } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface CollectionParams { collectionId: string; title: string; }

@Entry
@Component
struct CollectionDetail {
  @State series: KomgaSeries[] = [];
  @State loading: boolean = true;
  @State columns: number = 3;
  private collectionId: string = '';
  private title: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as CollectionParams;
    this.collectionId = params.collectionId; this.title = params.title; this.load();
  }
  private async load(): Promise<void> {
    try { this.series = (await komgaClient.getCollectionSeries(this.collectionId)).content; }
    finally { this.loading = false; }
  }
  private template(): string {
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }
  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).backgroundColor(Color.Transparent).fontSize(30).onClick(() => router.back())
        Text(this.title).fontSize(20).fontWeight(FontWeight.Bold).maxLines(1).layoutWeight(1)
      }.height(56).width('100%').padding({ left: 8, right: 12 })
      if (this.loading) { LoadingState() }
      else if (this.series.length === 0) { EmptyState('收藏中没有系列') }
      else {
        Grid() {
          ForEach(this.series, (item: KomgaSeries) => {
            GridItem() { SeriesCard({ series: item, onOpen: (id: string) =>
              router.pushUrl({ url: 'pages/SeriesDetail', params: { seriesId: id } }) }) }
          }, (item: KomgaSeries) => item.id)
        }.columnsTemplate(this.template()).columnsGap(12).rowsGap(12).layoutWeight(1).width('100%')
      }
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
    .onAreaChange((_, area) => this.columns = Number(area.width) >= 900 ? 5 : Number(area.width) >= 600 ? 4 : 3)
  }
}
