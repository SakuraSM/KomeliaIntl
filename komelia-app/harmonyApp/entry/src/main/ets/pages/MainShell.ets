import { AppDestination, AppState } from '../state/AppState';
import { HomePage } from './HomePage';
import { LibraryPage } from './LibraryPage';
import { SearchPage } from './SearchPage';
import { SettingsPage } from './SettingsPage';

@Component
export struct MainShell {
  @ObjectLink state: AppState;
  @State isWide: boolean = false;

  @Builder
  private destinationContent() {
    if (this.state.destination === AppDestination.LIBRARY) {
      LibraryPage({ state: this.state })
    } else if (this.state.destination === AppDestination.SEARCH) {
      SearchPage()
    } else if (this.state.destination === AppDestination.SETTINGS) {
      SettingsPage({ state: this.state })
    } else {
      HomePage({ state: this.state })
    }
  }

  @Builder
  private navItem(destination: AppDestination, icon: string, label: Resource) {
    Column({ space: 3 }) {
      Text(icon)
        .fontSize(24)
        .fontColor(this.state.destination === destination ? $r('app.color.primary') :
          $r('app.color.on_surface_variant'))
      Text(label)
        .fontSize(12)
        .fontColor(this.state.destination === destination ? $r('app.color.primary') :
          $r('app.color.on_surface_variant'))
    }
    .height(this.isWide ? 64 : 58)
    .width(this.isWide ? 72 : '25%')
    .justifyContent(FlexAlign.Center)
    .borderRadius(20)
    .backgroundColor(this.state.destination === destination ? $r('app.color.primary_container') :
      Color.Transparent)
    .animation({ duration: this.state.reducedMotion ? 0 : 160, curve: Curve.EaseOut })
    .onClick(() => this.state.destination = destination)
  }

  @Builder
  private navigation() {
    if (this.isWide) {
      Column({ space: 8 }) {
        Image($r('app.media.app_icon')).width(42).height(42).borderRadius(12).margin({ bottom: 18 })
        this.navItem(AppDestination.LIBRARY, '▤', $r('app.string.library'))
        this.navItem(AppDestination.HOME, '⌂', $r('app.string.home'))
        this.navItem(AppDestination.SEARCH, '⌕', $r('app.string.search'))
        this.navItem(AppDestination.SETTINGS, '⚙', $r('app.string.settings'))
      }
      .width(88)
      .height('100%')
      .padding({ top: 24, bottom: 16 })
      .backgroundColor($r('app.color.surface'))
    } else {
      Row() {
        this.navItem(AppDestination.LIBRARY, '▤', $r('app.string.library'))
        this.navItem(AppDestination.HOME, '⌂', $r('app.string.home'))
        this.navItem(AppDestination.SEARCH, '⌕', $r('app.string.search'))
        this.navItem(AppDestination.SETTINGS, '⚙', $r('app.string.settings'))
      }
      .width('100%')
      .height(72)
      .padding({ left: 8, right: 8, top: 6, bottom: 8 })
      .backgroundColor($r('app.color.surface'))
    }
  }

  build() {
    if (this.isWide) {
      Row() {
        this.navigation()
        Column() {
          this.destinationContent()
        }
        .layoutWeight(1)
        .height('100%')
      }
      .width('100%')
      .height('100%')
      .backgroundColor($r('app.color.surface'))
      .onAreaChange((_, area) => this.isWide = Number(area.width) >= 600)
    } else {
      Column() {
        Column() {
          this.destinationContent()
        }
        .layoutWeight(1)
        .width('100%')
        this.navigation()
      }
      .width('100%')
      .height('100%')
      .backgroundColor($r('app.color.surface'))
      .onAreaChange((_, area) => this.isWide = Number(area.width) >= 600)
    }
  }
}
