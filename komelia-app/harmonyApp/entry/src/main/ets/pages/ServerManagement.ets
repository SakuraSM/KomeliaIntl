import { router } from '@kit.ArkUI';
import { AppPreferences } from '../persistence/AppPreferences';
import { CredentialStore } from '../persistence/CredentialStore';

@Entry
@Component
struct ServerManagement {
  @State origin: string = '';
  @State allowHttp: boolean = false;
  @State message: string = '';
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.origin = await AppPreferences.getString('serverUrl');
    this.allowHttp = await AppPreferences.getBoolean('allowInsecureHttp');
  }
  private async save(): Promise<void> {
    if (!this.origin.startsWith('https://') && !this.origin.startsWith('http://')) {
      this.message = '地址必须以 https:// 或 http:// 开头'; return;
    }
    if (this.origin.startsWith('http://') && !this.allowHttp) {
      this.message = '使用 HTTP 前必须确认局域网风险'; return;
    }
    const activeAlias = await AppPreferences.getString('activeCredentialAlias');
    await Promise.all([
      AppPreferences.putString('serverUrl', this.origin.trim().replace(/\/+$/, '')),
      AppPreferences.putBoolean('allowInsecureHttp', this.allowHttp),
      AppPreferences.putString('activeCredentialAlias', ''),
      activeAlias.length > 0 ? CredentialStore.remove(activeAlias) : Promise.resolve()
    ]);
    this.message = '地址已保存；为保护凭据，请返回并重新登录';
  }
  build() {
    Column({ space: 16 }) {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('网络与服务器').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      TextInput({ text: this.origin, placeholder: 'https://komga.example.com' }).height(52).width('100%')
        .onChange((value: string) => this.origin = value)
      Row() {
        Column({ space: 3 }) {
          Text('允许局域网 HTTP').fontSize(16).width('100%')
          Text('连接未加密，仅用于可信局域网').fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Toggle({ type: ToggleType.Switch, isOn: this.allowHttp }).onChange((value: boolean) => this.allowHttp = value)
      }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
      Button('保存并清除旧凭据').height(52).width('100%').onClick(() => this.save())
      if (this.message.length > 0) Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').height('100%')
  }
}
