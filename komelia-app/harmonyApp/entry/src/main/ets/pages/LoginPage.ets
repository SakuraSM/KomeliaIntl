import { AppState } from '../state/AppState';

@Component
export struct LoginPage {
  @ObjectLink state: AppState;
  @State server: string = 'https://';
  @State email: string = '';
  @State password: string = '';
  @State allowHttp: boolean = false;

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
          Text('HarmonyOS 原生阅读器')
            .fontSize(15)
            .fontColor($r('app.color.on_surface_variant'))
        }
        .margin({ bottom: 16 })

        TextInput({ placeholder: $r('app.string.server'), text: this.server })
          .type(InputType.Normal)
          .height(56)
          .backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.server = value)

        TextInput({ placeholder: $r('app.string.email'), text: this.email })
          .type(InputType.Email)
          .height(56)
          .backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.email = value)

        TextInput({ placeholder: $r('app.string.password'), text: this.password })
          .type(InputType.Password)
          .height(56)
          .backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(14)
          .onChange((value: string) => this.password = value)

        if (this.server.trim().startsWith('http://')) {
          Row({ space: 10 }) {
            Toggle({ type: ToggleType.Checkbox, isOn: this.allowHttp })
              .onChange((value: boolean) => this.allowHttp = value)
            Text('我了解局域网 HTTP 连接未加密的风险')
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

        Button(this.state.isLoading ? '正在登录…' : $r('app.string.login'))
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

        Text('凭据使用 HarmonyOS Asset Store 加密保存；默认拒绝无效 TLS 证书。')
          .fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .textAlign(TextAlign.Center)
          .width('100%')
      }
      .width('100%')
      .constraintSize({ maxWidth: 480 })
      .padding({ left: 24, right: 24, top: 64, bottom: 40 })
    }
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .backgroundColor($r('app.color.surface'))
  }
}
