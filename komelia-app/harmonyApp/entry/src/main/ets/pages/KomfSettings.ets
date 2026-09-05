import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { KomfMediaServerLibrary } from '../model/KomfModels';
import { AppPreferences } from '../persistence/AppPreferences';

interface MediaServerSnapshot {
  connected: boolean;
  libraries: KomfMediaServerLibrary[];
  errorMessage: string;
}

@Entry
@Component
struct KomfSettings {
  @State origin: string = '';
  @State status: string = '';
  @State connected: boolean = false;
  @State connecting: boolean = false;
  @State komgaLibraries: KomfMediaServerLibrary[] = [];
  @State kavitaLibraries: KomfMediaServerLibrary[] = [];
  @State komgaConnected: boolean = false;
  @State kavitaConnected: boolean = false;
  @State allowHttp: boolean = false;

  aboutToAppear(): void {
    this.restore();
  }

  private async restore(): Promise<void> {
    this.status = localizedString($r('app.string.not_connected'));
    this.origin = await AppPreferences.getString('komfOrigin');
    this.allowHttp = await AppPreferences.getBoolean('komfAllowHttp');
    if (this.origin.length > 0) await this.connect(false);
  }

  private async connect(persist: boolean = true): Promise<void> {
    this.connecting = true;
    this.connected = false;
    this.status = localizedString($r('app.string.connecting'));
    try {
      komfRepository.configure(this.origin, this.allowHttp);
      if (!await komfRepository.ping()) {
        throw new Error(localizedString($r('app.string.komf_unresponsive')));
      }
      const servers = await Promise.all([this.loadMediaServer('komga'), this.loadMediaServer('kavita')]);
      this.komgaConnected = servers[0].connected;
      this.komgaLibraries = servers[0].libraries;
      this.kavitaConnected = servers[1].connected;
      this.kavitaLibraries = servers[1].libraries;
      if (persist) {
        await AppPreferences.putString('komfOrigin', this.origin.trim().replace(/\/+$/, ''));
        await AppPreferences.putBoolean('komfAllowHttp', this.allowHttp);
      }
      this.connected = true;
      this.status = this.komgaConnected || this.kavitaConnected ? localizedString($r('app.string.connected')) :
        (servers[0].errorMessage || servers[1].errorMessage ||
          localizedString($r('app.string.komf_media_server_disconnected')));
    } catch (error) {
      this.komgaLibraries = [];
      this.kavitaLibraries = [];
      this.komgaConnected = false;
      this.kavitaConnected = false;
      this.status = (error as Error).message;
    } finally {
      this.connecting = false;
    }
  }

  private async loadMediaServer(mediaServer: string): Promise<MediaServerSnapshot> {
    try {
      const status = await komfRepository.mediaServerStatus(mediaServer);
      if (!status.success) return { connected: false, libraries: [], errorMessage: status.errorMessage || '' };
      return { connected: true, libraries: await komfRepository.mediaServerLibraries(mediaServer),
        errorMessage: '' };
    } catch (error) {
      return { connected: false, libraries: [], errorMessage: (error as Error).message };
    }
  }

  @Builder
  private navigationRow(title: Resource, description: Resource, route: string, mediaServer: string = '') {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(title).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
        Text(description).fontSize(12).fontColor($r('app.color.on_surface_variant'))
          .maxLines(2).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Text('›').fontSize(28).fontColor($r('app.color.on_surface_variant'))
    }
    .height(64).width('100%').padding({ left: 14, right: 14 })
    .enabled(this.connected).opacity(this.connected ? 1 : 0.48)
    .onClick(() => router.pushUrl({ url: route, params: { mediaServer: mediaServer } }))
    .accessibilityText(title)
  }

  @Builder
  private separator() {
    Divider().color($r('app.color.outline')).margin({ left: 14, right: 14 })
  }

  @Builder
  private mediaServerCard(title: string, connected: boolean, libraries: KomfMediaServerLibrary[]) {
    Column({ space: 8 }) {
      Row({ space: 8 }) {
        Text(connected ? '●' : '○').fontSize(13)
          .fontColor(connected ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
        Text(title).fontSize(14).fontWeight(FontWeight.Bold).layoutWeight(1)
        Text(`${libraries.length}`).fontSize(13).fontColor($r('app.color.on_surface_variant'))
      }.width('100%')
      if (!connected) {
        Text($r('app.string.not_connected')).fontSize(13)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      } else if (libraries.length === 0) {
        Text($r('app.string.no_libraries_available')).fontSize(13)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      } else {
        ForEach(libraries, (library: KomfMediaServerLibrary) => {
          Text(library.name).fontSize(14).padding({ top: 5, bottom: 5 }).width('100%')
        }, (library: KomfMediaServerLibrary) => library.id)
      }
    }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
      .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.komf_settings')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 12 }) {
            Text($r('app.string.komf_connection')).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
            TextInput({ text: this.origin, placeholder: 'https://komf.example.com' })
              .height(52).width('100%').onChange((value: string) => {
                this.origin = value;
                this.connected = false;
              })
            Row() {
              Column({ space: 3 }) {
                Text($r('app.string.allow_local_http')).fontSize(15).width('100%')
                Text($r('app.string.komf_http_description')).fontSize(12)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Toggle({ type: ToggleType.Switch, isOn: this.allowHttp })
                .onChange((value: boolean) => {
                  this.allowHttp = value;
                  this.connected = false;
                })
            }.width('100%')
            Button(this.connecting ? $r('app.string.connecting') : $r('app.string.connect'))
              .height(48).width('100%').enabled(!this.connecting && this.origin.trim().length > 0)
              .onClick(() => this.connect())
            Row({ space: 8 }) {
              Text(this.connected ? '●' : '○').fontSize(13)
                .fontColor(this.connected ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
              Text(this.status).fontSize(13).fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
            }.width('100%')
          }
          .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

          if (this.connected) {
            this.mediaServerCard('Komga', this.komgaConnected, this.komgaLibraries)
            this.mediaServerCard('Kavita', this.kavitaConnected, this.kavitaLibraries)
          }

          Column() {
            this.navigationRow($r('app.string.komf_processing_komga'),
              $r('app.string.komf_processing_description'), 'pages/KomfConfiguration', 'komga')
            this.separator()
            this.navigationRow($r('app.string.komf_processing_kavita'),
              $r('app.string.komf_processing_description'), 'pages/KomfConfiguration', 'kavita')
            this.separator()
            this.navigationRow($r('app.string.providers'),
              $r('app.string.komf_providers_description'), 'pages/KomfProviders')
            this.separator()
            this.navigationRow($r('app.string.komf_notifications'),
              $r('app.string.komf_notifications_description'), 'pages/KomfNotifications')
            this.separator()
            this.navigationRow($r('app.string.komf_job_history'),
              $r('app.string.komf_jobs_description'), 'pages/KomfJobs')
          }
          .width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16).clip(true)
        }.width('100%').padding({ bottom: 32 }).constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%')
    }
    .padding({ left: 12, right: 12 }).width('100%').height('100%').align(Alignment.Top)
    .justifyContent(FlexAlign.Start)
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
