import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { komfDefaultProviderPatch, komfDefaultProviders, komfProviderGlobalPatch,
  komfProviderGlobalSettings, KomfProviderSummary } from '../model/KomfModels';

@Entry
@Component
struct KomfProviders {
  @State loading: boolean = true;
  @State saving: boolean = false;
  @State providers: KomfProviderSummary[] = [];
  @State nameMatchingMode: string = 'CLOSEST_MATCH';
  @State malClientId: string = '';
  @State comicVineClientId: string = '';
  @State showCredentials: boolean = false;
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
      const values = await Promise.all([komfRepository.config(), komfRepository.providers()]);
      this.providers = komfDefaultProviders(values[0], values[1]);
      const global = komfProviderGlobalSettings(values[0]);
      this.nameMatchingMode = global.nameMatchingMode;
      this.malClientId = global.malClientId;
      this.comicVineClientId = global.comicVineClientId;
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private providerName(id: string): string {
    if (id === 'MANGA_UPDATES') return 'MangaUpdates';
    if (id === 'ANILIST') return 'AniList';
    if (id === 'YEN_PRESS') return 'Yen Press';
    if (id === 'BOOK_WALKER') return 'BookWalker';
    if (id === 'MANGADEX') return 'MangaDex';
    if (id === 'COMIC_VINE') return 'Comic Vine';
    if (id === 'MANGA_BAKA') return 'MangaBaka';
    if (id === 'WEBTOONS') return 'Webtoons';
    if (id === 'HENTAG') return 'Hentag';
    return id.substring(0, 1) + id.substring(1).toLocaleLowerCase().replace(/_/g, ' ');
  }

  private enabledProviders(): KomfProviderSummary[] {
    return this.providers.filter((provider: KomfProviderSummary) => provider.enabled)
      .sort((left: KomfProviderSummary, right: KomfProviderSummary) => left.priority - right.priority);
  }

  private disabledProviders(): KomfProviderSummary[] {
    return this.providers.filter((provider: KomfProviderSummary) => !provider.enabled)
      .sort((left: KomfProviderSummary, right: KomfProviderSummary) => left.id.localeCompare(right.id));
  }

  private replaceProvider(next: KomfProviderSummary): void {
    const values = this.providers.slice();
    const index = values.findIndex((provider: KomfProviderSummary) => provider.id === next.id);
    if (index >= 0) values[index] = next;
    this.providers = values;
  }

  private async setEnabled(provider: KomfProviderSummary, enabled: boolean): Promise<void> {
    this.saving = true;
    this.message = '';
    try {
      const priority = enabled ? this.enabledProviders().length + 1 : 999;
      await komfRepository.updateConfig(komfDefaultProviderPatch(provider.id, enabled, priority));
      this.replaceProvider({ id: provider.id, enabled: enabled, priority: priority });
      this.messageIsError = false;
      this.message = localizedString(enabled ? $r('app.string.provider_enabled') :
        $r('app.string.provider_disabled'), this.providerName(provider.id));
    } catch (error) {
      this.messageIsError = true;
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
  }

  private async moveProvider(index: number, delta: number): Promise<void> {
    const enabled = this.enabledProviders();
    const target = index + delta;
    if (target < 0 || target >= enabled.length) return;
    const moved = enabled[index];
    enabled[index] = enabled[target];
    enabled[target] = moved;
    this.saving = true;
    this.message = '';
    try {
      const requests: Promise<void>[] = [];
      for (let current = 0; current < enabled.length; current += 1) {
        const provider = enabled[current];
        provider.priority = current + 1;
        requests.push(komfRepository.updateConfig(
          komfDefaultProviderPatch(provider.id, true, provider.priority)));
      }
      await Promise.all(requests);
      this.providers = enabled.concat(this.disabledProviders());
      this.messageIsError = false;
      this.message = localizedString($r('app.string.provider_order_saved'));
    } catch (error) {
      this.messageIsError = true;
      this.message = (error as Error).message;
      await this.load();
    } finally {
      this.saving = false;
    }
  }

  private async saveGlobal(): Promise<void> {
    this.saving = true;
    this.message = '';
    try {
      await komfRepository.updateConfig(komfProviderGlobalPatch(this.nameMatchingMode,
        this.malClientId.trim(), this.comicVineClientId.trim()));
      this.messageIsError = false;
      this.message = localizedString($r('app.string.provider_settings_saved'));
    } catch (error) {
      this.messageIsError = true;
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
  }

  @Builder
  private sectionTitle(title: ResourceStr, description: ResourceStr) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private providerCard(provider: KomfProviderSummary, index: number, enabled: boolean) {
    Row({ space: 10 }) {
      Text(this.providerName(provider.id).substring(0, 1)).fontSize(16).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.primary')).width(40).height(40).textAlign(TextAlign.Center)
        .padding({ top: 9 }).backgroundColor($r('app.color.primary_container')).borderRadius(20)
      Column({ space: 2 }) {
        Text(this.providerName(provider.id)).fontSize(15).fontWeight(FontWeight.Medium).width('100%')
        Text(enabled ? localizedString($r('app.string.provider_priority'), index + 1) :
          localizedString($r('app.string.provider_not_enabled'))).fontSize(12)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      if (enabled) {
        Button('↑').width(40).height(40).enabled(!this.saving && index > 0)
          .backgroundColor(Color.Transparent).onClick(() => this.moveProvider(index, -1))
          .accessibilityText($r('app.string.move_up'))
        Button('↓').width(40).height(40).enabled(!this.saving && index + 1 < this.enabledProviders().length)
          .backgroundColor(Color.Transparent).onClick(() => this.moveProvider(index, 1))
          .accessibilityText($r('app.string.move_down'))
      }
      Toggle({ type: ToggleType.Switch, isOn: enabled }).enabled(!this.saving)
        .onChange((value: boolean) => this.setEnabled(provider, value))
    }.constraintSize({ minHeight: 64 }).padding({ left: 12, right: 8, top: 8, bottom: 8 }).width('100%')
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(14)
      .border({ width: 1, color: $r('app.color.outline') })
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.providers')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text($r('app.string.komf_providers_description')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button($r('app.string.refresh')).height(40).enabled(!this.saving).onClick(() => this.load())
      }.height(72).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else if (this.providers.length === 0) {
        EmptyState($r('app.string.no_providers'))
      } else {
        Scroll() {
          Column({ space: 16 }) {
            Column({ space: 10 }) {
              this.sectionTitle($r('app.string.provider_common_settings'),
                $r('app.string.provider_common_settings_description'))
              Text($r('app.string.name_matching_mode')).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
              Row({ space: 8 }) {
                Button($r('app.string.closest_match')).height(40).layoutWeight(1)
                  .fontColor(this.nameMatchingMode === 'CLOSEST_MATCH' ? $r('app.color.primary') :
                    $r('app.color.on_surface'))
                  .backgroundColor(this.nameMatchingMode === 'CLOSEST_MATCH' ? $r('app.color.primary_container') :
                    KomeliaColors.surfaceContainer()).onClick(() => this.nameMatchingMode = 'CLOSEST_MATCH')
                Button($r('app.string.exact_match')).height(40).layoutWeight(1)
                  .fontColor(this.nameMatchingMode === 'EXACT' ? $r('app.color.primary') :
                    $r('app.color.on_surface'))
                  .backgroundColor(this.nameMatchingMode === 'EXACT' ? $r('app.color.primary_container') :
                    KomeliaColors.surfaceContainer()).onClick(() => this.nameMatchingMode = 'EXACT')
              }.width('100%')
              Button(this.showCredentials ? $r('app.string.hide_provider_credentials') :
                $r('app.string.edit_provider_credentials')).height(40)
                .backgroundColor(KomeliaColors.surfaceContainer()).fontColor($r('app.color.on_surface'))
                .onClick(() => this.showCredentials = !this.showCredentials)
              if (this.showCredentials) {
                TextInput({ text: this.malClientId, placeholder: $r('app.string.mal_client_id') })
                  .type(InputType.Password).height(48).width('100%')
                  .onChange((value: string) => this.malClientId = value)
                TextInput({ text: this.comicVineClientId, placeholder: $r('app.string.comic_vine_client_id') })
                  .type(InputType.Password).height(48).width('100%')
                  .onChange((value: string) => this.comicVineClientId = value)
              }
              Button(this.saving ? $r('app.string.saving') : $r('app.string.save')).height(44)
                .enabled(!this.saving).onClick(() => this.saveGlobal())
            }.alignItems(HorizontalAlign.Start).padding(16).width('100%')
              .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
              .border({ width: 1, color: $r('app.color.outline') })

            this.sectionTitle($r('app.string.enabled_providers'), $r('app.string.enabled_providers_description'))
            ForEach(this.enabledProviders(), (provider: KomfProviderSummary, index: number) => {
              this.providerCard(provider, index, true)
            }, (provider: KomfProviderSummary) => `enabled:${provider.id}`)

            this.sectionTitle($r('app.string.available_providers'),
              $r('app.string.available_providers_description'))
            ForEach(this.disabledProviders(), (provider: KomfProviderSummary, index: number) => {
              this.providerCard(provider, index, false)
            }, (provider: KomfProviderSummary) => `disabled:${provider.id}`)

            if (this.message.length > 0) {
              Text(this.message).fontSize(13)
                .fontColor(this.messageIsError ? $r('app.color.error') : $r('app.color.on_surface_variant'))
                .width('100%')
            }
          }.alignItems(HorizontalAlign.Start).width('100%').padding({ top: 8, bottom: 32 })
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
