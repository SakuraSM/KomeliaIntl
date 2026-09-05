import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { KomgaSettings, KomgaSettingsUpdateRequest } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

const THUMBNAIL_VALUES: string[] = ['DEFAULT', 'MEDIUM', 'LARGE', 'XLARGE'];

@Entry
@Component
struct ServerSettings {
  @State loading: boolean = true;
  @State saving: boolean = false;
  @State errorMessage: string = '';
  @State message: string = '';
  @State savedSuccessfully: boolean = false;
  @State changed: boolean = false;
  @State deleteEmptyCollections: boolean = false;
  @State deleteEmptyReadLists: boolean = false;
  @State rememberMeDurationDays: string = '365';
  @State renewRememberMeKey: boolean = false;
  @State thumbnailIndex: number = 0;
  @State taskPoolSize: string = '1';
  @State serverPort: string = '';
  @State configuredServerPort: string = '';
  @State serverContextPath: string = '';
  private current?: KomgaSettings;

  private text(resource: Resource): string {
    return localizedString(resource);
  }

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    this.message = '';
    try {
      const settings = await komgaClient.getSettings();
      this.apply(settings);
    } catch (error) {
      this.errorMessage = (error as Error).message || this.text($r('app.string.server_settings_load_failed'));
    } finally {
      this.loading = false;
    }
  }

  private apply(settings: KomgaSettings): void {
    this.current = settings;
    this.deleteEmptyCollections = settings.deleteEmptyCollections;
    this.deleteEmptyReadLists = settings.deleteEmptyReadLists;
    this.rememberMeDurationDays = `${settings.rememberMeDurationDays}`;
    this.renewRememberMeKey = false;
    this.thumbnailIndex = Math.max(0, THUMBNAIL_VALUES.indexOf(settings.thumbnailSize));
    this.taskPoolSize = `${settings.taskPoolSize}`;
    this.serverPort = settings.serverPort.databaseSource === null ||
      settings.serverPort.databaseSource === undefined ? '' : `${settings.serverPort.databaseSource}`;
    this.configuredServerPort = settings.serverPort.configurationSource === null ||
      settings.serverPort.configurationSource === undefined ? '' : `${settings.serverPort.configurationSource}`;
    this.serverContextPath = settings.serverContextPath.databaseSource || '';
    this.changed = false;
  }

  private markChanged(): void {
    this.changed = true;
    this.savedSuccessfully = false;
    this.message = '';
  }

  private validationMessage(): string {
    const duration = Number(this.rememberMeDurationDays);
    const poolSize = Number(this.taskPoolSize);
    const port = this.serverPort.length === 0 ? 0 : Number(this.serverPort);
    if (!Number.isInteger(duration) || duration < 1) {
      return this.text($r('app.string.remember_me_days_validation'));
    }
    if (!Number.isInteger(poolSize) || poolSize < 1) {
      return this.text($r('app.string.task_threads_validation'));
    }
    if (this.serverPort.length > 0 && (!Number.isInteger(port) || port < 1 || port > 65535)) {
      return this.text($r('app.string.server_port_validation'));
    }
    if (this.serverContextPath.length > 0 && !this.serverContextPath.startsWith('/')) {
      return this.text($r('app.string.context_path_validation'));
    }
    return '';
  }

  private async save(): Promise<void> {
    const current = this.current;
    if (current === undefined) return;
    const validation = this.validationMessage();
    if (validation.length > 0) {
      this.message = validation;
      return;
    }
    const request: KomgaSettingsUpdateRequest = {};
    const duration = Number(this.rememberMeDurationDays);
    const poolSize = Number(this.taskPoolSize);
    const thumbnail = THUMBNAIL_VALUES[this.thumbnailIndex];
    if (this.deleteEmptyCollections !== current.deleteEmptyCollections) {
      request.deleteEmptyCollections = this.deleteEmptyCollections;
    }
    if (this.deleteEmptyReadLists !== current.deleteEmptyReadLists) {
      request.deleteEmptyReadLists = this.deleteEmptyReadLists;
    }
    if (duration !== current.rememberMeDurationDays) request.rememberMeDurationDays = duration;
    if (this.renewRememberMeKey) request.renewRememberMeKey = true;
    if (thumbnail !== current.thumbnailSize) request.thumbnailSize = thumbnail;
    if (poolSize !== current.taskPoolSize) request.taskPoolSize = poolSize;
    const currentPort = current.serverPort.databaseSource;
    if (this.serverPort.length === 0 && currentPort !== null && currentPort !== undefined) {
      request.serverPort = null;
    } else if (this.serverPort.length > 0 && Number(this.serverPort) !== currentPort) {
      request.serverPort = Number(this.serverPort);
    }
    const currentPath = current.serverContextPath.databaseSource || '';
    if (this.serverContextPath !== currentPath) request.serverContextPath = this.serverContextPath;

    this.saving = true;
    this.message = '';
    try {
      await komgaClient.updateSettings(request);
      this.savedSuccessfully = true;
      this.message = this.text($r('app.string.server_settings_updated'));
      await this.load();
    } catch (error) {
      this.savedSuccessfully = false;
      this.message = (error as Error).message || this.text($r('app.string.server_settings_update_failed'));
    } finally {
      this.saving = false;
    }
  }

  @Builder
  private sectionTitle(title: Resource, description: Resource) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private switchRow(title: Resource, description: Resource, enabled: boolean,
    onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(title).fontSize(15).width('100%')
        Text(description).fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: enabled }).onChange(onChange)
    }.width('100%').constraintSize({ minHeight: 52 })
  }

  @Builder
  private thumbnailOption(title: Resource, index: number) {
    Button(title)
      .height(40)
      .margin({ right: 8, bottom: 8 })
      .fontColor(this.thumbnailIndex === index ? $r('app.color.primary') : $r('app.color.on_surface'))
      .backgroundColor(this.thumbnailIndex === index ? $r('app.color.primary_container') :
        KomeliaColors.surfaceContainer())
      .onClick(() => {
        this.thumbnailIndex = index;
        this.markChanged();
      })
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.server_general_settings')).fontSize(22).fontWeight(FontWeight.Bold)
          .layoutWeight(1)
      }.height(64).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Column({ space: 16 }) {
            Column({ space: 14 }) {
              this.sectionTitle($r('app.string.media_and_cleanup'),
                $r('app.string.media_and_cleanup_description'))
              Column({ space: 4 }) {
                Text($r('app.string.thumbnail_size')).fontSize(13)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
                Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
                  this.thumbnailOption($r('app.string.thumbnail_default'), 0)
                  this.thumbnailOption($r('app.string.thumbnail_medium'), 1)
                  this.thumbnailOption($r('app.string.thumbnail_large'), 2)
                  this.thumbnailOption($r('app.string.thumbnail_xlarge'), 3)
                }.width('100%')
              }.width('100%')
              this.switchRow($r('app.string.delete_empty_collections'),
                $r('app.string.delete_empty_collections_description'),
                this.deleteEmptyCollections, (value: boolean) => {
                  this.deleteEmptyCollections = value; this.markChanged();
                })
              Divider().color($r('app.color.outline'))
              this.switchRow($r('app.string.delete_empty_read_lists'),
                $r('app.string.delete_empty_read_lists_description'),
                this.deleteEmptyReadLists, (value: boolean) => {
                  this.deleteEmptyReadLists = value; this.markChanged();
                })
            }
            .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

            Column({ space: 14 }) {
              this.sectionTitle($r('app.string.tasks_and_sessions'),
                $r('app.string.tasks_and_sessions_description'))
              TextInput({ text: this.taskPoolSize, placeholder: '1' })
                .type(InputType.Number).height(52).width('100%')
                .onChange((value: string) => { this.taskPoolSize = value; this.markChanged(); })
              Text($r('app.string.task_threads')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              TextInput({ text: this.rememberMeDurationDays, placeholder: '365' })
                .type(InputType.Number).height(52).width('100%')
                .onChange((value: string) => { this.rememberMeDurationDays = value; this.markChanged(); })
              Text($r('app.string.remember_me_days_restart')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              this.switchRow($r('app.string.regenerate_remember_me_key'),
                $r('app.string.regenerate_remember_me_key_description'),
                this.renewRememberMeKey, (value: boolean) => {
                  this.renewRememberMeKey = value; this.markChanged();
                })
            }
            .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

            Column({ space: 14 }) {
              this.sectionTitle($r('app.string.network_listener'),
                $r('app.string.network_listener_description'))
              TextInput({ text: this.serverPort,
                placeholder: this.configuredServerPort.length > 0 ? this.configuredServerPort : '25600' })
                .type(InputType.Number).height(52).width('100%')
                .onChange((value: string) => { this.serverPort = value; this.markChanged(); })
              Text($r('app.string.server_port')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              TextInput({ text: this.serverContextPath, placeholder: '/komga' }).height(52).width('100%')
                .onChange((value: string) => { this.serverContextPath = value; this.markChanged(); })
              Text($r('app.string.context_path_hint')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
            }
            .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

            if (this.message.length > 0) {
              Text(this.message).fontSize(13)
                .fontColor(this.savedSuccessfully ? $r('app.color.primary') : $r('app.color.error'))
                .width('100%')
            }
            Row({ space: 10 }) {
              Button($r('app.string.discard')).height(48).layoutWeight(1)
                .enabled(this.changed && !this.saving)
                .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
                .onClick(() => { if (this.current !== undefined) this.apply(this.current); })
              Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
                .height(48).layoutWeight(1)
                .enabled(this.changed && !this.saving).onClick(() => this.save())
            }.width('100%').padding({ bottom: 32 })
          }.width('100%').constraintSize({ maxWidth: 840 })
        }.layoutWeight(1).width('100%').align(Alignment.Top)
      }
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
