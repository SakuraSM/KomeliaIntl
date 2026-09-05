import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { defaultKomfNotificationContext, KomfAppriseRenderResult, KomfAppriseTemplates,
  KomfDiscordRenderResult, KomfDiscordTemplates, KomfEmbedField, KomfNotificationContext,
  komfNotificationSettings } from '../model/KomfModels';

@Entry
@Component
struct KomfNotifications {
  @State loading: boolean = true;
  @State actionBusy: boolean = false;
  @State selectedTab: number = 0;
  @State discordTemplates: string = '';
  @State appriseTemplates: string = '';
  @State contextText: string = JSON.stringify(defaultKomfNotificationContext(), undefined, 2);
  @State showContextEditor: boolean = false;
  @State discordPreview: KomfDiscordRenderResult | undefined = undefined;
  @State apprisePreview: KomfAppriseRenderResult | undefined = undefined;
  @State discordTargetCount: number = 0;
  @State appriseTargetCount: number = 0;
  @State discordWebhooks: string[] = [];
  @State appriseUrls: string[] = [];
  @State discordSeriesCover: boolean = false;
  @State appriseSeriesCover: boolean = false;
  @State showAddTarget: boolean = false;
  @State newTarget: string = '';
  @State errorMessage: string = '';
  @State message: string = '';
  @State messageIsError: boolean = false;

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      await configureStoredKomf();
      const values = await Promise.all([
        komfRepository.config(),
        komfRepository.discordTemplates(),
        komfRepository.appriseTemplates()
      ]);
      const targets = komfNotificationSettings(values[0]);
      this.discordTargetCount = targets.discordCount;
      this.appriseTargetCount = targets.appriseCount;
      this.discordWebhooks = targets.discordWebhooks;
      this.appriseUrls = targets.appriseUrls;
      this.discordSeriesCover = targets.discordSeriesCover;
      this.appriseSeriesCover = targets.appriseSeriesCover;
      this.discordTemplates = JSON.stringify(values[1], undefined, 2);
      this.appriseTemplates = JSON.stringify(values[2], undefined, 2);
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private notificationContext(): KomfNotificationContext {
    return JSON.parse(this.contextText) as KomfNotificationContext;
  }

  private parsedDiscordTemplates(): KomfDiscordTemplates {
    return JSON.parse(this.discordTemplates) as KomfDiscordTemplates;
  }

  private parsedAppriseTemplates(): KomfAppriseTemplates {
    return JSON.parse(this.appriseTemplates) as KomfAppriseTemplates;
  }

  private showMessage(message: string, isError: boolean = false): void {
    this.message = message;
    this.messageIsError = isError;
  }

  private async save(): Promise<void> {
    this.actionBusy = true;
    this.message = '';
    try {
      this.notificationContext();
      if (this.selectedTab === 0) {
        await komfRepository.updateDiscordTemplates(this.parsedDiscordTemplates());
      } else {
        await komfRepository.updateAppriseTemplates(this.parsedAppriseTemplates());
      }
      this.showMessage(localizedString($r('app.string.notification_templates_saved')));
    } catch (error) {
      this.showMessage(localizedString($r('app.string.invalid_notification_templates'),
        (error as Error).message), true);
    } finally {
      this.actionBusy = false;
    }
  }

  private async renderPreview(): Promise<void> {
    this.actionBusy = true;
    this.message = '';
    try {
      const context = this.notificationContext();
      if (this.selectedTab === 0) {
        this.discordPreview = await komfRepository.renderDiscord(context, this.parsedDiscordTemplates());
      } else {
        this.apprisePreview = await komfRepository.renderApprise(context, this.parsedAppriseTemplates());
      }
      this.showMessage(localizedString($r('app.string.notification_preview_updated')));
    } catch (error) {
      this.showMessage(localizedString($r('app.string.notification_preview_failed'),
        (error as Error).message), true);
    } finally {
      this.actionBusy = false;
    }
  }

  private confirmSend(): void {
    const targetCount = this.selectedTab === 0 ? this.discordTargetCount : this.appriseTargetCount;
    if (targetCount === 0) {
      this.showMessage(localizedString(this.selectedTab === 0 ?
        $r('app.string.discord_targets_missing') : $r('app.string.apprise_targets_missing')), true);
      return;
    }
    this.getUIContext().showAlertDialog({
      title: $r('app.string.send_test_notification'),
      message: localizedString($r('app.string.send_test_notification_confirmation'), targetCount),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.send'), style: DialogButtonStyle.HIGHLIGHT,
          action: () => this.sendTest().catch(() => {}) }
      ]
    });
  }

  private async sendTest(): Promise<void> {
    this.actionBusy = true;
    this.message = '';
    try {
      const context = this.notificationContext();
      if (this.selectedTab === 0) {
        await komfRepository.sendDiscord(context, this.parsedDiscordTemplates());
      } else {
        await komfRepository.sendApprise(context, this.parsedAppriseTemplates());
      }
      this.showMessage(localizedString($r('app.string.test_notification_sent')));
    } catch (error) {
      this.showMessage(localizedString($r('app.string.test_notification_failed'),
        (error as Error).message), true);
    } finally {
      this.actionBusy = false;
    }
  }

  private resetContext(): void {
    this.contextText = JSON.stringify(defaultKomfNotificationContext(), undefined, 2);
    this.discordPreview = undefined;
    this.apprisePreview = undefined;
    this.message = '';
  }

  private selectedTargets(): string[] {
    return this.selectedTab === 0 ? this.discordWebhooks : this.appriseUrls;
  }

  private maskedTarget(value: string, index: number): string {
    if (this.selectedTab === 0) {
      return localizedString($r('app.string.discord_webhook_number'), index + 1);
    }
    const separator = value.indexOf('://');
    const scheme = separator > 0 ? value.substring(0, separator) : 'url';
    return localizedString($r('app.string.apprise_target_number'), scheme, index + 1);
  }

  private validNewTarget(): boolean {
    const value = this.newTarget.trim();
    if (this.selectedTab === 0) return value.startsWith('https://discord.com/api/webhooks/');
    return value.indexOf('://') > 0;
  }

  private async addTarget(): Promise<void> {
    const value = this.newTarget.trim();
    if (!this.validNewTarget()) {
      this.showMessage(localizedString(this.selectedTab === 0 ?
        $r('app.string.invalid_discord_webhook') : $r('app.string.invalid_apprise_url')), true);
      return;
    }
    this.actionBusy = true;
    try {
      if (this.selectedTab === 0) {
        await komfRepository.updateDiscordWebhook(this.discordWebhooks.length, value);
        this.discordWebhooks = this.discordWebhooks.concat([value]);
        this.discordTargetCount = this.discordWebhooks.length;
      } else {
        await komfRepository.updateAppriseUrl(this.appriseUrls.length, value);
        this.appriseUrls = this.appriseUrls.concat([value]);
        this.appriseTargetCount = this.appriseUrls.length;
      }
      this.newTarget = '';
      this.showAddTarget = false;
      this.showMessage(localizedString($r('app.string.notification_target_added')));
    } catch (error) {
      this.showMessage((error as Error).message, true);
    } finally {
      this.actionBusy = false;
    }
  }

  private confirmRemoveTarget(index: number): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.remove_notification_target'),
      message: $r('app.string.remove_notification_target_confirmation'),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => this.removeTarget(index).catch(() => {}) }
      ]
    });
  }

  private async removeTarget(index: number): Promise<void> {
    this.actionBusy = true;
    try {
      if (this.selectedTab === 0) {
        await komfRepository.updateDiscordWebhook(index);
        const values = this.discordWebhooks.slice();
        values.splice(index, 1);
        this.discordWebhooks = values;
        this.discordTargetCount = values.length;
      } else {
        await komfRepository.updateAppriseUrl(index);
        const values = this.appriseUrls.slice();
        values.splice(index, 1);
        this.appriseUrls = values;
        this.appriseTargetCount = values.length;
      }
      this.showMessage(localizedString($r('app.string.notification_target_removed')));
    } catch (error) {
      this.showMessage((error as Error).message, true);
    } finally {
      this.actionBusy = false;
    }
  }

  private async updateSeriesCover(value: boolean): Promise<void> {
    this.actionBusy = true;
    try {
      if (this.selectedTab === 0) {
        await komfRepository.updateDiscordSeriesCover(value);
        this.discordSeriesCover = value;
      } else {
        await komfRepository.updateAppriseSeriesCover(value);
        this.appriseSeriesCover = value;
      }
      this.showMessage(localizedString($r('app.string.notification_settings_saved')));
    } catch (error) {
      this.showMessage((error as Error).message, true);
    } finally {
      this.actionBusy = false;
    }
  }

  @Builder
  private tab(title: string, index: number) {
    Button(title).height(44).layoutWeight(1)
      .fontColor(this.selectedTab === index ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(this.selectedTab === index ? $r('app.color.primary_container') : Color.Transparent)
      .onClick(() => {
        this.selectedTab = index;
        this.message = '';
        this.newTarget = '';
        this.showAddTarget = false;
      })
  }

  @Builder
  private sectionTitle(title: ResourceStr, description: ResourceStr) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private discordPreviewContent(preview: KomfDiscordRenderResult) {
    Column({ space: 8 }) {
      if ((preview.title ?? '').length > 0) {
        Text(preview.title ?? '').fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      }
      if ((preview.titleUrl ?? '').length > 0) {
        Text(preview.titleUrl ?? '').fontSize(12).fontColor($r('app.color.primary')).width('100%')
          .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      if ((preview.description ?? '').length > 0) {
        Text(preview.description ?? '').fontSize(14).width('100%')
      }
      ForEach(preview.fields ?? [], (field: KomfEmbedField, index: number) => {
        Column({ space: 2 }) {
          Text(field.name).fontSize(13).fontWeight(FontWeight.Medium).width('100%')
          Text(field.value).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }.alignItems(HorizontalAlign.Start).width('100%')
          .padding({ top: index === 0 ? 0 : 6 })
      }, (field: KomfEmbedField, index: number) => `${index}:${field.name}`)
      if ((preview.footer ?? '').length > 0) {
        Text(preview.footer ?? '').fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
      }
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private previewCard() {
    Column({ space: 10 }) {
      this.sectionTitle($r('app.string.notification_preview'), $r('app.string.notification_preview_description'))
      if (this.selectedTab === 0 && this.discordPreview !== undefined) {
        this.discordPreviewContent(this.discordPreview)
      } else if (this.selectedTab === 1 && this.apprisePreview !== undefined) {
        Text(this.apprisePreview.title ?? '').fontSize(17).fontWeight(FontWeight.Bold).width('100%')
        Text(this.apprisePreview.body ?? '').fontSize(14).width('100%')
      } else {
        Text($r('app.string.notification_preview_empty')).fontSize(13)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      }
    }.alignItems(HorizontalAlign.Start).width('100%').padding(16)
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      .border({ width: 1, color: $r('app.color.outline') })
  }

  @Builder
  private targetSettings() {
    Column({ space: 10 }) {
      this.sectionTitle(this.selectedTab === 0 ? $r('app.string.discord_webhooks') :
        $r('app.string.apprise_urls'), $r('app.string.notification_targets_description'))
      ForEach(this.selectedTargets(), (target: string, index: number) => {
        Row({ space: 10 }) {
          Text(this.maskedTarget(target, index)).fontSize(14).layoutWeight(1).maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Button($r('app.string.delete')).height(40).fontColor($r('app.color.error'))
            .backgroundColor(Color.Transparent).enabled(!this.actionBusy)
            .onClick(() => this.confirmRemoveTarget(index))
        }.constraintSize({ minHeight: 48 }).width('100%').padding({ left: 12, right: 8 })
          .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(12)
      }, (_target: string, index: number) => `${this.selectedTab}:${index}`)

      if (this.showAddTarget) {
        Column({ space: 8 }) {
          TextInput({ text: this.newTarget, placeholder: this.selectedTab === 0 ?
            $r('app.string.discord_webhook_placeholder') : $r('app.string.apprise_url_placeholder') })
            .height(48).width('100%').type(InputType.Normal)
            .onChange((value: string) => this.newTarget = value)
          Row({ space: 8 }) {
            Button($r('app.string.cancel')).height(40).backgroundColor(Color.Transparent)
              .onClick(() => { this.showAddTarget = false; this.newTarget = ''; })
            Button($r('app.string.add')).height(40).enabled(!this.actionBusy && this.newTarget.trim().length > 0)
              .onClick(() => this.addTarget())
          }.width('100%')
        }.alignItems(HorizontalAlign.Start).width('100%').padding(12)
          .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(12)
      } else {
        Button($r('app.string.add_notification_target')).height(44)
          .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
          .onClick(() => this.showAddTarget = true)
      }

      Row({ space: 12 }) {
        Column({ space: 2 }) {
          Text($r('app.string.upload_series_cover')).fontSize(15).width('100%')
          Text($r('app.string.upload_series_cover_description')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Toggle({ type: ToggleType.Switch, isOn: this.selectedTab === 0 ?
          this.discordSeriesCover : this.appriseSeriesCover }).enabled(!this.actionBusy)
          .onChange((value: boolean) => this.updateSeriesCover(value))
      }.constraintSize({ minHeight: 56 }).width('100%').padding({ left: 12, right: 8, top: 6, bottom: 6 })
        .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(12)
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.komf_notifications')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text($r('app.string.komf_notifications_description')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      }.height(72).width('100%')

      Row({ space: 8 }) {
        this.tab('Discord', 0)
        this.tab('Apprise', 1)
      }.padding(4).width('100%').backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(16)

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Column({ space: 16 }) {
            Column({ space: 8 }) {
              Text(this.selectedTab === 0 ? $r('app.string.discord_template_description') :
                $r('app.string.apprise_template_description')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              Text(localizedString($r('app.string.notification_targets_configured'),
                this.selectedTab === 0 ? this.discordTargetCount : this.appriseTargetCount)).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
            }.alignItems(HorizontalAlign.Start).width('100%')

            this.targetSettings()

            this.sectionTitle($r('app.string.notification_templates'),
              $r('app.string.notification_templates_json_description'))
            if (this.selectedTab === 0) {
              TextArea({ text: this.discordTemplates, placeholder: '{}' })
                .fontSize(12).fontFamily('monospace').height(280).width('100%').padding(12)
                .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
                .border({ width: 1, color: $r('app.color.outline') })
                .onChange((value: string) => this.discordTemplates = value)
            } else {
              TextArea({ text: this.appriseTemplates, placeholder: '{}' })
                .fontSize(12).fontFamily('monospace').height(220).width('100%').padding(12)
                .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
                .border({ width: 1, color: $r('app.color.outline') })
                .onChange((value: string) => this.appriseTemplates = value)
            }

            Row({ space: 8 }) {
              Button($r('app.string.preview')).height(44).enabled(!this.actionBusy)
                .onClick(() => this.renderPreview())
              Button($r('app.string.save')).height(44).enabled(!this.actionBusy).onClick(() => this.save())
              Button($r('app.string.send_test')).height(44).enabled(!this.actionBusy)
                .backgroundColor($r('app.color.primary_container')).fontColor($r('app.color.primary'))
                .onClick(() => this.confirmSend())
            }.width('100%')

            this.previewCard()

            Button(this.showContextEditor ? $r('app.string.hide_test_context') :
              $r('app.string.edit_test_context')).height(44).backgroundColor(KomeliaColors.surfaceContainer())
              .fontColor($r('app.color.on_surface')).onClick(() => this.showContextEditor = !this.showContextEditor)
            if (this.showContextEditor) {
              Column({ space: 10 }) {
                this.sectionTitle($r('app.string.notification_context'),
                  $r('app.string.notification_context_description'))
                TextArea({ text: this.contextText, placeholder: '{}' })
                  .fontSize(12).fontFamily('monospace').height(320).width('100%').padding(12)
                  .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
                  .border({ width: 1, color: $r('app.color.outline') })
                  .onChange((value: string) => this.contextText = value)
                Button($r('app.string.reset_test_context')).height(40)
                  .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
                  .onClick(() => this.resetContext())
              }.alignItems(HorizontalAlign.Start).width('100%')
            }

            if (this.message.length > 0) {
              Text(this.message).fontSize(13)
                .fontColor(this.messageIsError ? $r('app.color.error') : $r('app.color.on_surface_variant'))
                .width('100%')
            }
          }.alignItems(HorizontalAlign.Start).width('100%').padding({ top: 16, bottom: 32 })
            .constraintSize({ maxWidth: 840 })
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
