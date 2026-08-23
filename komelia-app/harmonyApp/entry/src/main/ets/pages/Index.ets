import { AppState } from '../state/AppState';
import { LoginPage } from './LoginPage';
import { MainShell } from './MainShell';

@Entry
@Component
struct Index {
  @State state: AppState = AppState.getInstance();

  aboutToAppear(): void {
    this.state.bootstrap();
  }

  build() {
    Stack() {
      if (this.state.isBootstrapping) {
        Column({ space: 14 }) {
          Image($r('app.media.app_icon')).width(72).height(72).borderRadius(20)
          LoadingProgress().width(32).height(32).color($r('app.color.primary'))
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.state.isAuthenticated) {
        MainShell({ state: this.state })
      } else {
        LoginPage({ state: this.state })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.surface'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
