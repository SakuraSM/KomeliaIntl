import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { LocalContentPage } from './LocalContentPage';

interface LocalSeriesParams { seriesId: string; }
@Entry
@Component
struct LocalSeriesDetail {
  @State seriesId: string = '';
  aboutToAppear(): void { this.seriesId = requiredRouteId((router.getParams() as LocalSeriesParams)?.seriesId); }
  @Builder private content() { LocalContentPage({ seriesId: this.seriesId }) }
  build() {
    Stack({ alignContent: Alignment.TopStart }) { AppNavigationFrame({ content: () => { this.content(); } }) }
      .width('100%').height('100%')
  }
}
