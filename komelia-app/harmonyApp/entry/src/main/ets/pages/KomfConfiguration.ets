import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { komfProcessingSettings, komfProcessingUpdate, komfRemoveLibraryProcessingUpdate,
  KomfMediaServerId, KomfMediaServerLibrary, KomfProcessingSettingsSnapshot,
  normalizeKomfMediaServer } from '../model/KomfModels';

interface KomfConfigurationParams { mediaServer?: string; }

const LIBRARY_TYPES: string[] = ['MANGA', 'NOVEL', 'COMIC', 'WEBTOON'];
const READING_DIRECTIONS: string[] = ['', 'LEFT_TO_RIGHT', 'RIGHT_TO_LEFT', 'VERTICAL', 'WEBTOON'];

@Entry
@Component
struct KomfConfiguration {
  @State loading: boolean = true;
  @State saving: boolean = false;
  @State advancedMode: boolean = false;
  @State configText: string = '';
  @State libraryType: string = 'MANGA';
  @State aggregate: boolean = false;
  @State mergeTags: boolean = false;
  @State mergeGenres: boolean = false;
  @State bookCovers: boolean = false;
  @State seriesCovers: boolean = false;
  @State overrideExistingCovers: boolean = false;
  @State lockCovers: boolean = false;
  @State updateApi: boolean = true;
  @State updateComicInfo: boolean = false;
  @State seriesTitle: boolean = false;
  @State alternativeSeriesTitles: boolean = false;
  @State seriesTitleLanguage: string = '';
  @State alternativeTitleLanguages: string = '';
  @State fallbackToAltTitle: boolean = false;
  @State orderBooks: boolean = false;
  @State readingDirection: string = '';
  @State defaultLanguage: string = '';
  @State errorMessage: string = '';
  @State message: string = '';
  @State messageIsError: boolean = false;
  @State mediaServer: string = 'komga';
  @State libraries: KomfMediaServerLibrary[] = [];
  @State scopeIndex: number = 0;
  private loadedConfig: Object = JSON.parse('{}') as Object;

  aboutToAppear(): void {
    const params = router.getParams() as KomfConfigurationParams | undefined;
    this.mediaServer = normalizeKomfMediaServer(params?.mediaServer || 'komga');
    this.load();
  }

  private mediaServerId(): KomfMediaServerId {
    return normalizeKomfMediaServer(this.mediaServer);
  }

  private selectedLibraryId(): string {
    if (this.scopeIndex <= 0 || this.scopeIndex > this.libraries.length) return '';
    return this.libraries[this.scopeIndex - 1].id;
  }

  private scopeOptions(): SelectOption[] {
    const options: SelectOption[] = [{ value: localizedString($r('app.string.default_processing_scope')) }];
    return options.concat(this.libraries.map((library: KomfMediaServerLibrary): SelectOption =>
      ({ value: library.name })));
  }

  private applyScope(index: number): void {
    this.scopeIndex = Math.max(0, Math.min(index, this.libraries.length));
    this.applySettings(komfProcessingSettings(this.loadedConfig, this.mediaServerId(),
      this.selectedLibraryId()));
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      await configureStoredKomf();
      const config = await komfRepository.config();
      this.loadedConfig = config;
      this.configText = JSON.stringify(config, undefined, 2);
      try {
        this.libraries = await komfRepository.mediaServerLibraries(this.mediaServerId());
      } catch (_) {
        this.libraries = [];
      }
      this.applyScope(0);
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private applySettings(settings: KomfProcessingSettingsSnapshot): void {
    this.libraryType = settings.libraryType;
    this.aggregate = settings.aggregate;
    this.mergeTags = settings.mergeTags;
    this.mergeGenres = settings.mergeGenres;
    this.bookCovers = settings.bookCovers;
    this.seriesCovers = settings.seriesCovers;
    this.overrideExistingCovers = settings.overrideExistingCovers;
    this.lockCovers = settings.lockCovers;
    this.updateApi = settings.updateModes.includes('API');
    this.updateComicInfo = settings.updateModes.includes('COMIC_INFO');
    this.seriesTitle = settings.seriesTitle;
    this.alternativeSeriesTitles = settings.alternativeSeriesTitles;
    this.seriesTitleLanguage = settings.seriesTitleLanguage;
    this.alternativeTitleLanguages = settings.alternativeSeriesTitleLanguages.join(', ');
    this.fallbackToAltTitle = settings.fallbackToAltTitle;
    this.orderBooks = settings.orderBooks;
    this.readingDirection = settings.readingDirectionValue ?? '';
    this.defaultLanguage = settings.languageValue ?? '';
  }

  private currentSettings(): KomfProcessingSettingsSnapshot {
    const modes: string[] = [];
    if (this.updateApi) modes.push('API');
    if (this.updateComicInfo) modes.push('COMIC_INFO');
    const languages = this.alternativeTitleLanguages.split(',')
      .map((value: string) => value.trim()).filter((value: string) => value.length > 0);
    return {
      libraryType: this.libraryType,
      aggregate: this.aggregate,
      mergeTags: this.mergeTags,
      mergeGenres: this.mergeGenres,
      bookCovers: this.bookCovers,
      seriesCovers: this.seriesCovers,
      overrideExistingCovers: this.overrideExistingCovers,
      lockCovers: this.lockCovers,
      updateModes: modes,
      seriesTitle: this.seriesTitle,
      alternativeSeriesTitles: this.alternativeSeriesTitles,
      seriesTitleLanguage: this.seriesTitleLanguage.trim(),
      alternativeSeriesTitleLanguages: languages,
      fallbackToAltTitle: this.fallbackToAltTitle,
      orderBooks: this.orderBooks,
      readingDirectionValue: this.readingDirection.length === 0 ? undefined : this.readingDirection,
      languageValue: this.defaultLanguage.trim().length === 0 ? undefined : this.defaultLanguage.trim()
    };
  }

  private async save(): Promise<void> {
    this.saving = true;
    this.message = '';
    try {
      const request = this.advancedMode ? JSON.parse(this.configText) as Object :
        komfProcessingUpdate(this.currentSettings(), this.mediaServerId(), this.selectedLibraryId());
      await komfRepository.updateConfig(request);
      this.messageIsError = false;
      this.message = localizedString($r('app.string.configuration_saved'));
      if (!this.advancedMode) {
        const config = await komfRepository.config();
        this.loadedConfig = config;
        this.configText = JSON.stringify(config, undefined, 2);
        this.applyScope(this.scopeIndex);
      }
    } catch (error) {
      this.messageIsError = true;
      this.message = localizedString($r('app.string.invalid_configuration'), (error as Error).message);
    } finally {
      this.saving = false;
    }
  }

  private async removeLibraryOverride(): Promise<void> {
    const libraryId = this.selectedLibraryId();
    if (libraryId.length === 0) return;
    this.saving = true;
    try {
      await komfRepository.updateConfig(komfRemoveLibraryProcessingUpdate(this.mediaServerId(), libraryId));
      this.loadedConfig = await komfRepository.config();
      this.configText = JSON.stringify(this.loadedConfig, undefined, 2);
      this.applyScope(this.scopeIndex);
      this.messageIsError = false;
      this.message = localizedString($r('app.string.library_override_removed'));
    } catch (error) {
      this.messageIsError = true;
      this.message = localizedString($r('app.string.invalid_configuration'), (error as Error).message);
    } finally {
      this.saving = false;
    }
  }

  private typeLabel(value: string): ResourceStr {
    if (value === 'NOVEL') return $r('app.string.library_type_novel');
    if (value === 'COMIC') return $r('app.string.library_type_comic');
    if (value === 'WEBTOON') return $r('app.string.library_type_webtoon');
    return $r('app.string.library_type_manga');
  }

  private directionLabel(value: string): ResourceStr {
    if (value === 'LEFT_TO_RIGHT') return $r('app.string.left_to_right');
    if (value === 'RIGHT_TO_LEFT') return $r('app.string.right_to_left');
    if (value === 'VERTICAL') return $r('app.string.vertical');
    if (value === 'WEBTOON') return $r('app.string.webtoon');
    return $r('app.string.none');
  }

  @Builder
  private modeButton(title: ResourceStr, selected: boolean, onClick: () => void) {
    Button(title).height(44).layoutWeight(1)
      .fontColor(selected ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(selected ? $r('app.color.primary_container') : Color.Transparent)
      .onClick(onClick)
  }

  @Builder
  private sectionTitle(title: ResourceStr, description: ResourceStr) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private optionButton(title: ResourceStr, selected: boolean, onClick: () => void) {
    Button(title).height(40).margin({ right: 8, bottom: 8 })
      .fontColor(selected ? $r('app.color.primary') : $r('app.color.on_surface'))
      .backgroundColor(selected ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .onClick(onClick)
  }

  @Builder
  private switchRow(title: ResourceStr, description: ResourceStr, value: boolean,
    enabled: boolean, onChange: (value: boolean) => void) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(title).fontSize(15).width('100%')
        Text(description).fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Toggle({ type: ToggleType.Switch, isOn: value }).enabled(enabled).onChange(onChange)
    }.constraintSize({ minHeight: 56 }).width('100%').padding({ top: 5, bottom: 5 })
      .opacity(enabled ? 1 : 0.48)
  }

  @Builder
  private structuredContent() {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        this.sectionTitle($r('app.string.processing_general'), $r('app.string.processing_general_description'))
        Text($r('app.string.processing_library_type')).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(LIBRARY_TYPES, (value: string) => {
            this.optionButton(this.typeLabel(value), this.libraryType === value, () => this.libraryType = value)
          }, (value: string) => value)
        }.width('100%')
        Text($r('app.string.processing_update_modes')).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          this.optionButton($r('app.string.update_mode_api'), this.updateApi, () => this.updateApi = !this.updateApi)
          this.optionButton($r('app.string.update_mode_comic_info'), this.updateComicInfo,
            () => this.updateComicInfo = !this.updateComicInfo)
        }.width('100%')
        this.switchRow($r('app.string.processing_order_books'), $r('app.string.processing_order_books_description'),
          this.orderBooks, true, (value: boolean) => this.orderBooks = value)
      }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })

      Column({ space: 4 }) {
        this.sectionTitle($r('app.string.processing_aggregation'),
          $r('app.string.processing_aggregation_description'))
        this.switchRow($r('app.string.processing_aggregate'), $r('app.string.processing_aggregate_description'),
          this.aggregate, true, (value: boolean) => this.aggregate = value)
        this.switchRow($r('app.string.processing_merge_genres'),
          $r('app.string.processing_merge_genres_description'), this.mergeGenres, this.aggregate,
          (value: boolean) => this.mergeGenres = value)
        this.switchRow($r('app.string.processing_merge_tags'), $r('app.string.processing_merge_tags_description'),
          this.mergeTags, this.aggregate, (value: boolean) => this.mergeTags = value)
      }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })

      Column({ space: 4 }) {
        this.sectionTitle($r('app.string.processing_covers'), $r('app.string.processing_covers_description'))
        this.switchRow($r('app.string.processing_series_covers'),
          $r('app.string.processing_series_covers_description'), this.seriesCovers, true,
          (value: boolean) => this.seriesCovers = value)
        this.switchRow($r('app.string.processing_book_covers'),
          $r('app.string.processing_book_covers_description'), this.bookCovers, true,
          (value: boolean) => this.bookCovers = value)
        this.switchRow($r('app.string.processing_override_covers'),
          $r('app.string.processing_override_covers_description'), this.overrideExistingCovers, true,
          (value: boolean) => this.overrideExistingCovers = value)
        this.switchRow($r('app.string.processing_lock_covers'),
          $r('app.string.processing_lock_covers_description'), this.lockCovers, true,
          (value: boolean) => this.lockCovers = value)
      }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })

      Column({ space: 10 }) {
        this.sectionTitle($r('app.string.processing_titles'), $r('app.string.processing_titles_description'))
        this.switchRow($r('app.string.processing_series_title'),
          $r('app.string.processing_series_title_description'), this.seriesTitle, true,
          (value: boolean) => this.seriesTitle = value)
        this.switchRow($r('app.string.processing_alternative_titles'),
          $r('app.string.processing_alternative_titles_description'), this.alternativeSeriesTitles, true,
          (value: boolean) => this.alternativeSeriesTitles = value)
        this.switchRow($r('app.string.processing_fallback_title'),
          $r('app.string.processing_fallback_title_description'), this.fallbackToAltTitle,
          this.alternativeSeriesTitles, (value: boolean) => this.fallbackToAltTitle = value)
        TextInput({ text: this.seriesTitleLanguage, placeholder: $r('app.string.iso_language_placeholder') })
          .height(48).width('100%').onChange((value: string) => this.seriesTitleLanguage = value)
        TextInput({ text: this.alternativeTitleLanguages,
          placeholder: $r('app.string.alternative_languages_placeholder') })
          .height(48).width('100%').onChange((value: string) => this.alternativeTitleLanguages = value)
      }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })

      Column({ space: 10 }) {
        this.sectionTitle($r('app.string.processing_defaults'), $r('app.string.processing_defaults_description'))
        Text($r('app.string.processing_reading_direction')).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(READING_DIRECTIONS, (value: string) => {
            this.optionButton(this.directionLabel(value), this.readingDirection === value,
              () => this.readingDirection = value)
          }, (value: string) => value.length === 0 ? 'NONE' : value)
        }.width('100%')
        TextInput({ text: this.defaultLanguage, placeholder: $r('app.string.iso_language_placeholder') })
          .height(48).width('100%').onChange((value: string) => this.defaultLanguage = value)
      }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        .border({ width: 1, color: $r('app.color.outline') })
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text(this.mediaServer === 'kavita' ? $r('app.string.komf_processing_kavita') :
            $r('app.string.komf_processing_komga')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text($r('app.string.komf_processing_description')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save')).height(40)
          .enabled(!this.loading && !this.saving).onClick(() => this.save())
      }.height(72).width('100%')

      Row({ space: 8 }) {
        this.modeButton($r('app.string.structured_settings'), !this.advancedMode,
          () => this.advancedMode = false)
        this.modeButton($r('app.string.advanced_json'), this.advancedMode, () => this.advancedMode = true)
      }.padding(4).width('100%').backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(16)

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        Scroll() {
          Column({ space: 14 }) {
            if (this.advancedMode) {
              Text($r('app.string.advanced_configuration_warning')).fontSize(13)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              TextArea({ text: this.configText, placeholder: '{}' })
                .fontSize(12).fontFamily('monospace').height(720).width('100%').padding(12)
                .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
                .border({ width: 1, color: $r('app.color.outline') })
                .onChange((value: string) => this.configText = value)
            } else {
              Column({ space: 8 }) {
                Text($r('app.string.processing_scope')).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
                Select(this.scopeOptions()).selected(this.scopeIndex)
                  .value(this.scopeOptions()[this.scopeIndex].value).height(48).width('100%')
                  .onSelect((index: number) => this.applyScope(index))
                Text($r('app.string.processing_scope_description')).fontSize(13)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
                if (this.scopeIndex > 0) {
                  Button($r('app.string.library_override_remove')).height(40)
                    .fontColor($r('app.color.error')).backgroundColor(KomeliaColors.surfaceContainer())
                    .enabled(!this.saving).onClick(() => this.removeLibraryOverride())
                }
              }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
                .border({ width: 1, color: $r('app.color.outline') })
              this.structuredContent()
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
