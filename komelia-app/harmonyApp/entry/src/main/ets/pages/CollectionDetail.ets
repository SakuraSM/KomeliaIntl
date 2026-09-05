import { KomeliaColors, KomeliaGrid, KomeliaLayout, WindowClass } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { SeriesCard } from '../components/CoverCards';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { seriesDetailPage } from '../core/navigation/CatalogNavigation';
import { optionalRouteText, requiredRouteId } from '../core/navigation/RouteParams';
import { KomgaCollection, KomgaSeries } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

interface CollectionParams { collectionId: string; title: string; }

@Entry
@Component
struct CollectionDetail {
  @State series: KomgaSeries[] = [];
  @State loading: boolean = true;
  @State areaWidth: number = 360;
  @State windowClass: WindowClass = WindowClass.COMPACT;
  @StorageLink('gridCardWidth') cardWidth: number = KomeliaGrid.defaultCardWidth;
  @State errorMessage: string = '';
  @State collection: KomgaCollection | undefined = undefined;
  @State title: string = '';
  private collectionId: string = '';
  private hasShown: boolean = false;

  aboutToAppear(): void {
    const params = router.getParams() as CollectionParams | undefined;
    this.collectionId = requiredRouteId(params?.collectionId);
    this.title = optionalRouteText(params?.title);
    if (this.collectionId.length > 0) this.load();
    else this.loading = false;
  }
  onPageShow(): void {
    if (!this.hasShown) { this.hasShown = true; return; }
    if (this.collectionId.length > 0) this.load();
  }
  private async load(): Promise<void> {
    this.loading = true; this.errorMessage = '';
    try {
      const values = await Promise.all([
        komgaClient.getCollection(this.collectionId), komgaClient.getCollectionSeries(this.collectionId)
      ]);
      this.collection = values[0];
      this.title = values[0].name;
      this.series = values[1].content;
    }
    catch (error) { this.errorMessage = (error as Error).message || localizedString($r('app.string.collection_load_failed')); }
    finally { this.loading = false; }
  }
  private confirmDelete(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.delete_collection'),
      message: $r('app.string.delete_collection_confirmation'),
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          komgaClient.deleteCollection(this.collectionId).then(() => router.back())
            .catch((error: Error) => this.errorMessage = error.message);
        } }
      ]
    });
  }
  private template(): string {
    return KomeliaGrid.template(KomeliaGrid.columnCount(this.areaWidth, this.windowClass, this.cardWidth));
  }
  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.title).fontSize(20).fontWeight(FontWeight.Bold).maxLines(1).layoutWeight(1)
        if (AppState.getInstance().canManageServer()) {
          Button($r('app.string.edit')).height(40).backgroundColor(Color.Transparent)
            .fontColor($r('app.color.primary')).onClick(() => router.pushUrl({
              url: 'pages/CollectionEdit', params: { collectionId: this.collectionId }
            }))
          Button($r('app.string.delete')).height(40).backgroundColor(Color.Transparent)
            .fontColor($r('app.color.error')).onClick(() => this.confirmDelete())
        }
      }.height(56).width('100%').padding({ left: 8, right: 12 })
      if (this.loading) { LoadingState() }
      else if (this.errorMessage.length > 0) { ErrorState(this.errorMessage, () => this.load()) }
      else if (this.series.length === 0) { EmptyState($r('app.string.no_collection_series')) }
      else {
        Grid() {
          ForEach(this.series, (item: KomgaSeries) => {
            GridItem() { SeriesCard({ series: item, compact: this.windowClass === WindowClass.COMPACT,
              onOpen: (id: string) => router.pushUrl({
                url: seriesDetailPage(item.oneshot), params: { seriesId: id }
              }) }) }
          }, (item: KomgaSeries) => item.id)
        }.columnsTemplate(this.template()).columnsGap(12).rowsGap(12).layoutWeight(1).width('100%')
      }
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%').align(Alignment.Top)
    .justifyContent(FlexAlign.Start)
    .backgroundColor(KomeliaColors.surface())
    .onAreaChange((_, area) => {
      this.areaWidth = Number(area.width);
      this.windowClass = KomeliaLayout.windowClass(this.areaWidth);
    })
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
