import { router } from '@kit.ArkUI';
import { KomeliaColors, KomeliaMotion } from '../core/design/KomeliaDesign';
import { primaryNavigationDecision } from '../core/navigation/PrimaryNavigationBehavior';
import { AppDestination, AppState } from '../state/AppState';

@Component
export struct AppPrimaryNavigation {
  @ObjectLink state: AppState;
  @StorageLink('appDestination') selectedDestination: number = AppDestination.HOME;
  @Prop isWide: boolean = false;
  @Prop nested: boolean = false;

  private select(destination: AppDestination): void {
    const decision = primaryNavigationDecision(this.selectedDestination, destination, this.nested);
    if (decision.updateDestination) {
      this.selectedDestination = destination;
      this.state.setDestination(destination);
    }
    if (decision.returnToRoot) router.back({ url: 'pages/Index' });
  }

  @Builder
  private navItem(destination: AppDestination, icon: Resource, label: Resource, componentId: string) {
    Column({ space: 2 }) {
      Stack({ alignContent: Alignment.Center }) {
        Image(icon).width(24).height(24)
          .fillColor(this.selectedDestination === destination ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
      }
      .width(this.isWide ? 56 : 64).height(32).borderRadius(18)
      .backgroundColor(this.selectedDestination === destination ? $r('app.color.primary_container') : Color.Transparent)
      Text(label).fontSize(12)
        .fontColor(this.selectedDestination === destination ? $r('app.color.primary') :
          $r('app.color.on_surface_variant'))
    }
    .height(this.isWide ? 58 : 60).width(this.isWide ? 80 : this.state.isLocalMode ? '25%' : '20%').id(componentId)
    .justifyContent(FlexAlign.Center)
    .animation({
      duration: KomeliaMotion.duration(KomeliaMotion.selection, this.state.reducedMotion), curve: Curve.EaseOut
    })
    .onClick(() => this.select(destination)).accessibilityText(label)
  }

  build() {
    if (this.isWide) {
      Column({ space: 4 }) {
        if (!this.state.isLocalMode) this.navItem(AppDestination.LIBRARY, $r('app.media.nav_library'), $r('app.string.library'), 'nav_library')
        this.navItem(AppDestination.HOME, $r('app.media.nav_home'), $r('app.string.home'), 'nav_home')
        this.navItem(AppDestination.LOCAL, $r('app.media.settings_download'), $r('app.string.local_content'), 'nav_local')
        this.navItem(AppDestination.SEARCH, $r('app.media.nav_search'), $r('app.string.search'), 'nav_search')
        this.navItem(AppDestination.SETTINGS, $r('app.media.nav_settings'), $r('app.string.settings'), 'nav_settings')
      }
      .id('primary_navigation_rail')
      .width(80).height('100%').padding({ top: 4, bottom: 4 }).backgroundColor(KomeliaColors.surface())
    } else {
      Row() {
        if (!this.state.isLocalMode) this.navItem(AppDestination.LIBRARY, $r('app.media.nav_library'), $r('app.string.library'), 'nav_library')
        this.navItem(AppDestination.HOME, $r('app.media.nav_home'), $r('app.string.home'), 'nav_home')
        this.navItem(AppDestination.LOCAL, $r('app.media.settings_download'), $r('app.string.local_content'), 'nav_local')
        this.navItem(AppDestination.SEARCH, $r('app.media.nav_search'), $r('app.string.search'), 'nav_search')
        this.navItem(AppDestination.SETTINGS, $r('app.media.nav_settings'), $r('app.string.settings'), 'nav_settings')
      }
      .id('primary_navigation_bar')
      .width('100%').height(72).padding({ left: 8, right: 8, top: 6, bottom: 6 })
      .backgroundColor(KomeliaColors.surface())
    }
  }
}
