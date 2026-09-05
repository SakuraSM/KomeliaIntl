import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { AppPreferences } from '../persistence/AppPreferences';
import { CredentialStore } from '../persistence/CredentialStore';
import { AppState } from '../state/AppState';

@Entry
@Component
struct ServerManagement {
  @State origin: string = '';
  @State allowHttp: boolean = false;
  @State lanOrigin: string = '';
  @State lanAutoSwitchEnabled: boolean = false;
  @State allowLanHttp: boolean = false;
  @State checkingLan: boolean = false;
  @State connectionMessage: string = '';
  @State message: string = '';
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    const values = await Promise.all([
      AppPreferences.getString('serverUrl'),
      AppPreferences.getBoolean('allowInsecureHttp'),
      AppPreferences.getString('lanServerUrl'),
      AppPreferences.getBoolean('lanAutoSwitchEnabled'),
      AppPreferences.getBoolean('allowLanHttp')
    ]);
    this.origin = values[0] as string;
    this.allowHttp = values[1] as boolean;
    this.lanOrigin = values[2] as string;
    this.lanAutoSwitchEnabled = values[3] as boolean;
    this.allowLanHttp = values[4] as boolean;
    this.updateConnectionMessage();
  }

  private normalized(value: string): string {
    return value.trim().replace(/\/+$/, '');
  }

  private validUrl(value: string): boolean {
    return value.startsWith('https://') || value.startsWith('http://');
  }

  private async save(): Promise<void> {
    const normalized = this.normalized(this.origin);
    if (!this.validUrl(normalized)) {
      this.message = localizedString($r('app.string.server_url_scheme_validation')); return;
    }
    if (normalized.startsWith('http://') && !this.allowHttp) {
      this.message = localizedString($r('app.string.http_confirmation_required')); return;
    }
    const activeAlias = await AppPreferences.getString('activeCredentialAlias');
    await Promise.all([
      AppPreferences.putString('serverUrl', normalized),
      AppPreferences.putBoolean('allowInsecureHttp', this.allowHttp),
      AppPreferences.putString('activeCredentialAlias', ''),
      activeAlias.length > 0 ? CredentialStore.remove(activeAlias) : Promise.resolve()
    ]);
    this.message = localizedString($r('app.string.server_saved_relogin'));
  }

  private updateConnectionMessage(): void {
    const state = AppState.getInstance();
    if (!state.isOnline) {
      this.connectionMessage = localizedString($r('app.string.lan_unreachable'));
    } else if (state.usingLan) {
      this.connectionMessage = localizedString($r('app.string.using_lan_server'), state.effectiveServerUrl);
    } else {
      this.connectionMessage = localizedString($r('app.string.using_primary_server'));
    }
  }

  private async saveLanAndCheck(): Promise<void> {
    const normalized = this.normalized(this.lanOrigin);
    if (this.lanAutoSwitchEnabled && normalized.length === 0) {
      this.connectionMessage = localizedString($r('app.string.lan_address_required')); return;
    }
    if (normalized.length > 0 && !this.validUrl(normalized)) {
      this.connectionMessage = localizedString($r('app.string.server_url_scheme_validation')); return;
    }
    if (normalized.startsWith('http://') && !this.allowLanHttp) {
      this.connectionMessage = localizedString($r('app.string.http_confirmation_required')); return;
    }
    this.checkingLan = true;
    this.connectionMessage = localizedString($r('app.string.checking_lan'));
    try {
      await Promise.all([
        AppPreferences.putString('lanServerUrl', normalized),
        AppPreferences.putBoolean('lanAutoSwitchEnabled', this.lanAutoSwitchEnabled),
        AppPreferences.putBoolean('allowLanHttp', this.allowLanHttp)
      ]);
      await AppState.getInstance().reconnectConfiguredNetwork();
      this.updateConnectionMessage();
    } catch (error) {
      this.connectionMessage = (error as Error).message || localizedString($r('app.string.lan_unreachable'));
    } finally {
      this.checkingLan = false;
    }
  }

  @Builder
  private sectionTitle(title: ResourceStr) {
    Text(title).fontSize(14).fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface_variant')).padding({ left: 4 }).width('100%')
  }

  @Builder
  private pageContent() {
    Scroll() {
      Column({ space: 16 }) {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.network_and_server')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
        this.sectionTitle($r('app.string.primary_server'))
        Column({ space: 14 }) {
          TextInput({ text: this.origin, placeholder: 'https://komga.example.com' }).height(52).width('100%')
            .onChange((value: string) => this.origin = value)
          Row() {
            Column({ space: 3 }) {
              Text($r('app.string.allow_local_http')).fontSize(16).width('100%')
              Text($r('app.string.local_http_description')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
            }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            Toggle({ type: ToggleType.Switch, isOn: this.allowHttp })
              .onChange((value: boolean) => this.allowHttp = value)
          }.width('100%')
          Button($r('app.string.save_and_clear_credentials')).height(48).width('100%').onClick(() => this.save())
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }
        .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

        this.sectionTitle($r('app.string.lan_connection'))
        Column({ space: 14 }) {
          Row() {
            Column({ space: 3 }) {
              Text($r('app.string.lan_auto_switch')).fontSize(16).width('100%')
              Text($r('app.string.lan_auto_switch_description')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
            }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            Toggle({ type: ToggleType.Switch, isOn: this.lanAutoSwitchEnabled })
              .onChange((value: boolean) => this.lanAutoSwitchEnabled = value)
          }.width('100%')
          TextInput({ text: this.lanOrigin, placeholder: 'http://192.168.1.10:25600' })
            .height(52).width('100%').onChange((value: string) => this.lanOrigin = value)
          Row() {
            Column({ space: 3 }) {
              Text($r('app.string.allow_lan_http')).fontSize(16).width('100%')
              Text($r('app.string.komf_http_description')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
            }.layoutWeight(1).alignItems(HorizontalAlign.Start)
            Toggle({ type: ToggleType.Switch, isOn: this.allowLanHttp })
              .onChange((value: boolean) => this.allowLanHttp = value)
          }.width('100%')
          Button(this.checkingLan ? $r('app.string.checking_lan') : $r('app.string.check_lan_connection'))
            .height(48).width('100%').enabled(!this.checkingLan).onClick(() => this.saveLanAndCheck())
          Text(this.connectionMessage).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }
        .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
        .alignItems(HorizontalAlign.Start).justifyContent(FlexAlign.Start)
    }.width('100%').height('100%').align(Alignment.Top)
      .backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
