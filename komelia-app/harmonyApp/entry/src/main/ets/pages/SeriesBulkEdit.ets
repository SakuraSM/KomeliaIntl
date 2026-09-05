import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf, hasStoredKomfConfiguration } from '../domain/service/KomfSession';
import { splitMetadataValues } from '../model/MetadataTextCodec';
import { SeriesMetadataUpdateRequest } from '../model/KomgaModels';
import { SERIES_PUBLICATION_STATUS_VALUES, seriesPublicationStatusKey } from '../model/SeriesPresentation';
import { komgaClient } from '../network/KomgaClient';

interface SeriesBulkEditParams { seriesIds: string[]; }

@Entry
@Component
struct SeriesBulkEdit {
  @State status: string = 'ONGOING';
  @State publisher: string = '';
  @State readingDirection: string = '';
  @State ageRating: string = '';
  @State language: string = '';
  @State genres: string = '';
  @State tags: string = '';
  @State sharingLabels: string = '';
  @State applyStatus: boolean = false;
  @State applyPublisher: boolean = false;
  @State applyReadingDirection: boolean = false;
  @State applyAgeRating: boolean = false;
  @State applyLanguage: boolean = false;
  @State applyGenres: boolean = false;
  @State applyTags: boolean = false;
  @State applySharingLabels: boolean = false;
  @State statusLock: boolean = false;
  @State publisherLock: boolean = false;
  @State readingDirectionLock: boolean = false;
  @State ageRatingLock: boolean = false;
  @State languageLock: boolean = false;
  @State genresLock: boolean = false;
  @State tagsLock: boolean = false;
  @State sharingLabelsLock: boolean = false;
  @State saving: boolean = false;
  @State komfRunning: boolean = false;
  @State message: string = '';
  @State komfConfigured: boolean = false;
  private seriesIds: string[] = [];

  aboutToAppear(): void {
    const params = router.getParams() as SeriesBulkEditParams | undefined;
    this.seriesIds = params?.seriesIds || [];
    this.restoreKomfAvailability();
  }

  private async restoreKomfAvailability(): Promise<void> {
    this.komfConfigured = await hasStoredKomfConfiguration();
  }

  private request(): SeriesMetadataUpdateRequest {
    const request: SeriesMetadataUpdateRequest = {};
    if (this.applyStatus) { request.status = this.status.trim(); request.statusLock = this.statusLock; }
    if (this.applyPublisher) { request.publisher = this.publisher.trim(); request.publisherLock = this.publisherLock; }
    if (this.applyReadingDirection) {
      request.readingDirection = this.readingDirection.trim();
      request.readingDirectionLock = this.readingDirectionLock;
    }
    if (this.applyAgeRating) {
      request.ageRating = this.ageRating.trim().length === 0 ? null : Number(this.ageRating);
      request.ageRatingLock = this.ageRatingLock;
    }
    if (this.applyLanguage) { request.language = this.language.trim(); request.languageLock = this.languageLock; }
    if (this.applyGenres) { request.genres = splitMetadataValues(this.genres); request.genresLock = this.genresLock; }
    if (this.applyTags) { request.tags = splitMetadataValues(this.tags); request.tagsLock = this.tagsLock; }
    if (this.applySharingLabels) {
      request.sharingLabels = splitMetadataValues(this.sharingLabels);
      request.sharingLabelsLock = this.sharingLabelsLock;
    }
    return request;
  }

  private async save(): Promise<void> {
    if (this.seriesIds.length === 0) return;
    this.saving = true;
    this.message = '';
    try {
      const request = this.request();
      for (let index = 0; index < this.seriesIds.length; index++) {
        await komgaClient.updateSeriesMetadata(this.seriesIds[index], request);
      }
      this.message = localizedString($r('app.string.bulk_metadata_saved'), this.seriesIds.length);
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
  }

  private confirmKomfIdentify(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.komf_bulk_auto_identify'),
      message: localizedString($r('app.string.komf_bulk_auto_identify_confirmation'), this.seriesIds.length),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.confirm'), style: DialogButtonStyle.HIGHLIGHT, action: () => {
          this.identifySelected().catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  private async identifySelected(): Promise<void> {
    if (this.seriesIds.length === 0 || this.komfRunning) return;
    this.komfRunning = true;
    this.message = '';
    let submitted = 0;
    try {
      await configureStoredKomf();
      for (let index = 0; index < this.seriesIds.length; index++) {
        const series = await komgaClient.getSeries(this.seriesIds[index]);
        await komfRepository.identifySeries(series.libraryId, series.id);
        submitted += 1;
      }
      this.message = localizedString($r('app.string.komf_jobs_submitted'), submitted);
    } catch (error) {
      this.message = localizedString($r('app.string.komf_bulk_partial_failure'), submitted,
        this.seriesIds.length, (error as Error).message);
    } finally {
      this.komfRunning = false;
    }
  }

  private publicationStatusOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    SERIES_PUBLICATION_STATUS_VALUES.forEach((value: string) => {
      const option: SelectOption = { value: this.publicationStatusLabel(value) };
      options.push(option);
    });
    return options;
  }

  private publicationStatusLabel(value: string): string {
    switch (seriesPublicationStatusKey(value)) {
      case 'ended':
        return localizedString($r('app.string.series_status_ended'));
      case 'ongoing':
        return localizedString($r('app.string.series_status_ongoing'));
      case 'abandoned':
        return localizedString($r('app.string.series_status_abandoned'));
      case 'hiatus':
        return localizedString($r('app.string.series_status_hiatus'));
      default:
        return value;
    }
  }

  @Builder
  private editableField(label: ResourceStr, enabled: boolean, value: string, locked: boolean,
    onEnabled: (value: boolean) => void, onValue: (value: string) => void,
    onLock: (value: boolean) => void) {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Text(label).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
        Text($r('app.string.apply_this_field')).fontSize(12).fontColor($r('app.color.on_surface_variant'))
        Toggle({ type: ToggleType.Switch, isOn: enabled }).onChange(onEnabled)
      }.width('100%').constraintSize({ minHeight: 44 })
      TextInput({ text: value }).height(48).width('100%').enabled(enabled).onChange(onValue)
      Row({ space: 10 }) {
        Text($r('app.string.metadata_lock')).fontSize(12)
          .fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: locked }).enabled(enabled).onChange(onLock)
      }.width('100%').constraintSize({ minHeight: 40 })
    }.padding(14).width('100%').alignItems(HorizontalAlign.Start)
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      .border({ width: 1, color: $r('app.color.outline') })
  }

  @Builder
  private editablePublicationStatusField() {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Text($r('app.string.publication_status')).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
        Text($r('app.string.apply_this_field')).fontSize(12).fontColor($r('app.color.on_surface_variant'))
        Toggle({ type: ToggleType.Switch, isOn: this.applyStatus })
          .onChange((value: boolean) => this.applyStatus = value)
      }.width('100%').constraintSize({ minHeight: 44 })
      Select(this.publicationStatusOptions())
        .selected(SERIES_PUBLICATION_STATUS_VALUES.indexOf(this.status))
        .value(this.publicationStatusOptions()[SERIES_PUBLICATION_STATUS_VALUES.indexOf(this.status)].value)
        .width('100%').height(48).enabled(this.applyStatus)
        .onSelect((index: number) => this.status = SERIES_PUBLICATION_STATUS_VALUES[index])
      Row({ space: 10 }) {
        Text($r('app.string.metadata_lock')).fontSize(12)
          .fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
        Toggle({ type: ToggleType.Switch, isOn: this.statusLock }).enabled(this.applyStatus)
          .onChange((value: boolean) => this.statusLock = value)
      }.width('100%').constraintSize({ minHeight: 40 })
    }.padding(14).width('100%').alignItems(HorizontalAlign.Start)
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      .border({ width: 1, color: $r('app.color.outline') })
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.bulk_edit_metadata')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text(localizedString($r('app.string.selected_count'), this.seriesIds.length)).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.saving && this.seriesIds.length > 0).onClick(() => this.save())
      }.height(68).width('100%')
      Scroll() {
        Column({ space: 12 }) {
          if (this.komfConfigured) {
            Column({ space: 8 }) {
              Text($r('app.string.komf_bulk_auto_identify')).fontSize(16)
                .fontWeight(FontWeight.Medium).width('100%')
              Text($r('app.string.komf_bulk_auto_identify_description')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).width('100%')
              Button(this.komfRunning ? $r('app.string.loading') :
                $r('app.string.komf_bulk_auto_identify')).height(48).width('100%')
                .enabled(!this.komfRunning && !this.saving && this.seriesIds.length > 0)
                .onClick(() => this.confirmKomfIdentify())
            }.padding(14).width('100%').alignItems(HorizontalAlign.Start)
              .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
              .border({ width: 1, color: $r('app.color.outline') })
          }
          this.editablePublicationStatusField()
          this.editableField($r('app.string.publisher'), this.applyPublisher, this.publisher, this.publisherLock,
            (value: boolean) => this.applyPublisher = value, (value: string) => this.publisher = value,
            (value: boolean) => this.publisherLock = value)
          this.editableField($r('app.string.reading_direction'), this.applyReadingDirection, this.readingDirection,
            this.readingDirectionLock, (value: boolean) => this.applyReadingDirection = value,
            (value: string) => this.readingDirection = value,
            (value: boolean) => this.readingDirectionLock = value)
          this.editableField($r('app.string.age_rating'), this.applyAgeRating, this.ageRating, this.ageRatingLock,
            (value: boolean) => this.applyAgeRating = value, (value: string) => this.ageRating = value,
            (value: boolean) => this.ageRatingLock = value)
          this.editableField($r('app.string.language'), this.applyLanguage, this.language, this.languageLock,
            (value: boolean) => this.applyLanguage = value, (value: string) => this.language = value,
            (value: boolean) => this.languageLock = value)
          this.editableField($r('app.string.genres_separator_hint'), this.applyGenres, this.genres, this.genresLock,
            (value: boolean) => this.applyGenres = value, (value: string) => this.genres = value,
            (value: boolean) => this.genresLock = value)
          this.editableField($r('app.string.tags_separator_hint'), this.applyTags, this.tags, this.tagsLock,
            (value: boolean) => this.applyTags = value, (value: string) => this.tags = value,
            (value: boolean) => this.tagsLock = value)
          this.editableField($r('app.string.sharing_labels'), this.applySharingLabels, this.sharingLabels,
            this.sharingLabelsLock, (value: boolean) => this.applySharingLabels = value,
            (value: string) => this.sharingLabels = value,
            (value: boolean) => this.sharingLabelsLock = value)
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%').align(Alignment.Top)
    }.width('100%').height('100%').align(Alignment.Top)
      .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
