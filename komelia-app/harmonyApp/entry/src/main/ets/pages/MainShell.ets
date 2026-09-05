import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppPrimaryNavigation } from '../components/AppPrimaryNavigation';
import { AppDestination, AppState } from '../state/AppState';
import { HomePage } from './HomePage';
import { LibraryPage } from './LibraryPage';
import { SearchPage } from './SearchPage';
import { SettingsPage } from './SettingsPage';
import { LocalContentPage } from './LocalContentPage';
import { KomeliaLayout, WindowClass } from '../core/design/KomeliaDesign';

@Component
export struct MainShell {
  @ObjectLink state: AppState;
  @StorageLink('appDestination') @Watch('onDestinationChanged') selectedDestination: number = AppDestination.HOME;
  @State isWide: boolean = false;
  @State libraryVisited: boolean = false;
  @State searchVisited: boolean = false;
  @State settingsVisited: boolean = false;
  @State localVisited: boolean = false;

  aboutToAppear(): void {
    this.markDestinationVisited();
  }

  private onDestinationChanged(): void {
    this.markDestinationVisited();
  }

  private markDestinationVisited(): void {
    if (this.selectedDestination === AppDestination.LIBRARY) this.libraryVisited = true;
    if (this.selectedDestination === AppDestination.SEARCH) this.searchVisited = true;
    if (this.selectedDestination === AppDestination.SETTINGS) this.settingsVisited = true;
    if (this.selectedDestination === AppDestination.LOCAL) this.localVisited = true;
  }

  @Builder
  private destinationContent() {
    Stack({ alignContent: Alignment.TopStart }) {
      HomePage({ state: this.state })
        .visibility(this.selectedDestination === AppDestination.HOME ? Visibility.Visible : Visibility.None)
      if (this.libraryVisited && !this.state.isLocalMode) {
        LibraryPage({ state: this.state })
          .visibility(this.selectedDestination === AppDestination.LIBRARY ? Visibility.Visible : Visibility.None)
      }
      if (this.searchVisited) {
        SearchPage()
          .visibility(this.selectedDestination === AppDestination.SEARCH ? Visibility.Visible : Visibility.None)
      }
      if (this.settingsVisited) {
        SettingsPage({ state: this.state })
          .visibility(this.selectedDestination === AppDestination.SETTINGS ? Visibility.Visible : Visibility.None)
      }
      if (this.localVisited) {
        LocalContentPage()
          .visibility(this.selectedDestination === AppDestination.LOCAL ? Visibility.Visible : Visibility.None)
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private contentWithConnectionState() {
    Column() {
      if (!this.state.isOnline) {
        Row({ space: 8 }) {
          Text($r('app.string.offline_mode')).fontSize(13).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.primary'))
          Text(this.state.isLocalMode ? $r('app.string.local_source_files') : $r('app.string.using_local_cache')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant'))
        }
        .height(36)
        .padding({ left: 12, right: 12 })
        .width('100%')
        .backgroundColor($r('app.color.primary_container'))
      }
      this.destinationContent()
    }
    .layoutWeight(1)
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Start)
    .justifyContent(FlexAlign.Start)
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (this.isWide) {
        Row() {
          AppPrimaryNavigation({ state: this.state, isWide: true })
          this.contentWithConnectionState()
        }
        .width('100%')
        .height('100%')
        .alignItems(VerticalAlign.Top)
      } else {
        Column() {
          this.contentWithConnectionState()
          AppPrimaryNavigation({ state: this.state, isWide: false })
        }
        .width('100%')
        .height('100%')
        .alignItems(HorizontalAlign.Start)
        .justifyContent(FlexAlign.Start)
      }
    }
    .width('100%')
    .height('100%')
    .align(Alignment.TopStart)
    .id('main_shell')
    .backgroundColor(KomeliaColors.surface())
    .onAreaChange((_, area) =>
      this.isWide = KomeliaLayout.windowClass(Number(area.width)) !== WindowClass.COMPACT)
  }
}
