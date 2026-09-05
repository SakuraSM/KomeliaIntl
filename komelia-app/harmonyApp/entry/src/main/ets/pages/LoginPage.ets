import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppState } from '../state/AppState';
import { OfflineSessionProfile } from '../domain/repository/Repositories';
import { KomeliaLayout } from '../core/design/KomeliaDesign';

@Component
export struct LoginPage {
  @ObjectLink state: AppState;
  @State server: string = 'https://';
  @State email: string = '';
  @State password: string = '';
  @State allowHttp: boolean = false;
  @State offlineVisible: boolean = false;

  aboutToAppear(): void {
    this.state.loadOfflineProfiles().catch(() => {});
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 10 }) {
          Image($r('app.media.app_icon'))
            .width(72)
            .height(72)
            .borderRadius(20)
          Text('Komelia')
            .fontSize(34)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface'))
          Text($r('app.string.harmony_native_reader'))
            .fontSize(15)
            .fontColor($r('app.color.on_surface_variant'))
        }
        .margin({ bottom: 16 })

        TextInput({ placeholder: $r('app.string.server'), text: this.server })
          .type(InputType.Normal)
          .height(56)
          .backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.server = value)

        TextInput({ placeholder: $r('app.string.email'), text: this.email })
          .type(InputType.Email)
          .height(56)
          .backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.email = value)

        TextInput({ placeholder: $r('app.string.password'), text: this.password })
          .type(InputType.Password)
          .height(56)
          .backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.password = value)

        if (this.server.trim().startsWith('http://')) {
          Row({ space: 10 }) {
            Toggle({ type: ToggleType.Checkbox, isOn: this.allowHttp })
              .onChange((value: boolean) => this.allowHttp = value)
            Text($r('app.string.insecure_http_acknowledgement'))
              .fontSize(14)
              .fontColor($r('app.color.on_surface_variant'))
              .layoutWeight(1)
          }
          .width('100%')
        }

        if (this.state.errorMessage.length > 0) {
          Text(this.state.errorMessage)
            .fontSize(14)
            .fontColor($r('app.color.error'))
            .width('100%')
        }

        Button(this.state.isLoading ? $r('app.string.signing_in') : $r('app.string.login'))
          .width('100%')
          .height(52)
          .borderRadius(16)
          .enabled(!this.state.isLoading && this.email.trim().length > 0 && this.password.length > 0)
          .backgroundColor($r('app.color.primary'))
          .onClick(async () => {
            try {
              await this.state.signIn(this.server, this.email.trim(), this.password, this.allowHttp);
            } catch (_) {
            }
          })

        Button($r('app.string.local_mode_enter')).height(48).width('100%').id('login_local_mode')
          .onClick(() => this.state.useLocalMode().catch(() => {}))
        if (this.state.offlineProfiles.length > 0) {
          Button(this.offlineVisible ? $r('app.string.hide_offline_accounts') :
            $r('app.string.show_offline_accounts'))
            .width('100%').height(48).borderRadius(16)
            .backgroundColor($r('app.color.primary_container')).fontColor($r('app.color.primary'))
            .onClick(() => this.offlineVisible = !this.offlineVisible)
          if (this.offlineVisible) {
            Column({ space: 8 }) {
              Text($r('app.string.offline_login_description')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              ForEach(this.state.offlineProfiles, (profile: OfflineSessionProfile) => {
                Row({ space: 12 }) {
                  Column({ space: 3 }) {
                    Text(profile.user.email).fontSize(15).fontWeight(FontWeight.Medium)
                      .fontColor($r('app.color.on_surface')).maxLines(1).width('100%')
                    Text(profile.serverId).fontSize(12).fontColor($r('app.color.on_surface_variant'))
                      .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
                  }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                  Text($r('app.string.use_offline_account')).fontSize(13).fontColor($r('app.color.primary'))
                }
                .width('100%').height(64).padding({ left: 14, right: 14 })
                .backgroundColor(KomeliaColors.surfaceHigh())
                .border({ width: 1, color: $r('app.color.outline') }).borderRadius(14)
                .onClick(() => this.state.signInOffline(profile.namespace).catch(() => {}))
                .accessibilityText($r('app.string.use_offline_account'))
              }, (profile: OfflineSessionProfile) => profile.namespace)
            }.width('100%')
          }
        }

        Text($r('app.string.credential_security_hint'))
          .fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .textAlign(TextAlign.Center)
          .width('100%')
      }
      .width('100%')
      .constraintSize({ maxWidth: 480 })
      .padding({ left: 24, right: 24, top: KomeliaLayout.section, bottom: 40 })
      .justifyContent(FlexAlign.Start)
    }
    .width('100%')
    .height('100%')
    .id('login_page')
    .align(Alignment.Top)
    .backgroundColor(KomeliaColors.surface())
  }
}
