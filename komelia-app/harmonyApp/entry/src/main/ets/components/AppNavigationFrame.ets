import { KomeliaColors, KomeliaLayout, WindowClass } from '../core/design/KomeliaDesign';
import { AppState } from '../state/AppState';
import { AppPrimaryNavigation } from './AppPrimaryNavigation';

@Component
export struct AppNavigationFrame {
  @State state: AppState = AppState.getInstance();
  @State isWide: boolean = false;
  @BuilderParam content: () => void = this.emptyContent;

  @Builder
  private emptyContent() {}

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (this.isWide) {
        Row() {
          AppPrimaryNavigation({ state: this.state, isWide: true, nested: true })
          Column() { this.content() }.layoutWeight(1).height('100%')
        }.width('100%').height('100%').alignItems(VerticalAlign.Top)
      } else {
        Column() {
          Column() { this.content() }.layoutWeight(1).width('100%')
          AppPrimaryNavigation({ state: this.state, isWide: false, nested: true })
        }.width('100%').height('100%').alignItems(HorizontalAlign.Start)
      }
    }
    .width('100%').height('100%').backgroundColor(KomeliaColors.surface())
    .onAreaChange((_, area) => this.isWide = KomeliaLayout.windowClass(Number(area.width)) !== WindowClass.COMPACT)
  }
}
