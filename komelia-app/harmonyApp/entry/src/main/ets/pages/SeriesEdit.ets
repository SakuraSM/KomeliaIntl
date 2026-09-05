import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { localizedString } from '../core/i18n/LocalizedResources';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { KomgaSeries, SeriesMetadataUpdateRequest } from '../model/KomgaModels';
import { formatAlternativeTitles, formatWebLinks, parseAlternativeTitles, parseWebLinks,
  splitMetadataValues } from '../model/MetadataTextCodec';
import { seriesPublicationStatusKey, seriesPublicationStatusValues } from '../model/SeriesPresentation';
import { komgaClient } from '../network/KomgaClient';
import { hasStoredKomfConfiguration } from '../domain/service/KomfSession';

interface SeriesEditParams { seriesId: string; }

@Entry
@Component
struct SeriesEdit {
  @State series: KomgaSeries | undefined = undefined;
  @State title: string = '';
  @State titleSort: string = '';
  @State summary: string = '';
  @State publisher: string = '';
  @State language: string = '';
  @State status: string = '';
  @State genres: string = '';
  @State tags: string = '';
  @State readingDirection: string = 'LEFT_TO_RIGHT';
  @State ageRating: string = '';
  @State totalBookCount: string = '';
  @State sharingLabels: string = '';
  @State links: string = '';
  @State alternateTitles: string = '';
  @State titleLock: boolean = false;
  @State titleSortLock: boolean = false;
  @State summaryLock: boolean = false;
  @State publisherLock: boolean = false;
  @State languageLock: boolean = false;
  @State statusLock: boolean = false;
  @State readingDirectionLock: boolean = false;
  @State ageRatingLock: boolean = false;
  @State genresLock: boolean = false;
  @State tagsLock: boolean = false;
  @State totalBookCountLock: boolean = false;
  @State sharingLabelsLock: boolean = false;
  @State linksLock: boolean = false;
  @State alternateTitlesLock: boolean = false;
  @State message: string = '';
  @State saving: boolean = false;
  @State komfConfigured: boolean = false;
  private seriesId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as SeriesEditParams | undefined;
    this.seriesId = requiredRouteId(params?.seriesId);
    if (this.seriesId.length > 0) this.load();
    this.restoreKomfAvailability();
  }

  private async restoreKomfAvailability(): Promise<void> {
    this.komfConfigured = await hasStoredKomfConfiguration();
  }

  private async load(): Promise<void> {
    try {
      this.series = await catalogRepository.seriesDetail(this.seriesId);
      this.title = this.series.metadata.title;
      this.titleSort = this.series.metadata.titleSort;
      this.summary = this.series.metadata.summary;
      this.publisher = this.series.metadata.publisher;
      this.language = this.series.metadata.language;
      this.status = this.series.metadata.status;
      this.genres = this.series.metadata.genres.join('、');
      this.tags = this.series.metadata.tags.join('、');
      this.readingDirection = this.series.metadata.readingDirection || 'LEFT_TO_RIGHT';
      this.ageRating = this.series.metadata.ageRating === null || this.series.metadata.ageRating === undefined ? '' :
        this.series.metadata.ageRating.toString();
      this.totalBookCount = this.series.metadata.totalBookCount === null ||
        this.series.metadata.totalBookCount === undefined ? '' : this.series.metadata.totalBookCount.toString();
      this.sharingLabels = (this.series.metadata.sharingLabels || []).join('、');
      this.links = formatWebLinks(this.series.metadata.links || []);
      this.alternateTitles = formatAlternativeTitles(this.series.metadata.alternateTitles || []);
      this.titleLock = this.series.metadata.titleLock || false;
      this.titleSortLock = this.series.metadata.titleSortLock || false;
      this.summaryLock = this.series.metadata.summaryLock || false;
      this.publisherLock = this.series.metadata.publisherLock || false;
      this.languageLock = this.series.metadata.languageLock || false;
      this.statusLock = this.series.metadata.statusLock || false;
      this.readingDirectionLock = this.series.metadata.readingDirectionLock || false;
      this.ageRatingLock = this.series.metadata.ageRatingLock || false;
      this.genresLock = this.series.metadata.genresLock || false;
      this.tagsLock = this.series.metadata.tagsLock || false;
      this.totalBookCountLock = this.series.metadata.totalBookCountLock || false;
      this.sharingLabelsLock = this.series.metadata.sharingLabelsLock || false;
      this.linksLock = this.series.metadata.linksLock || false;
      this.alternateTitlesLock = this.series.metadata.alternateTitlesLock || false;
    } catch (error) { this.message = (error as Error).message; }
  }

  private list(value: string): string[] {
    return splitMetadataValues(value);
  }

  private async save(): Promise<void> {
    this.saving = true;
    try {
      const request: SeriesMetadataUpdateRequest = {
        title: this.title.trim(), titleLock: this.titleLock,
        titleSort: this.titleSort.trim(), titleSortLock: this.titleSortLock,
        summary: this.summary.trim(), summaryLock: this.summaryLock,
        publisher: this.publisher.trim(), publisherLock: this.publisherLock,
        language: this.language.trim(), languageLock: this.languageLock,
        status: this.status.trim(), statusLock: this.statusLock,
        readingDirection: this.readingDirection, readingDirectionLock: this.readingDirectionLock,
        ageRating: this.ageRating.trim().length === 0 ? null : Number(this.ageRating), ageRatingLock: this.ageRatingLock,
        genres: this.list(this.genres), genresLock: this.genresLock,
        tags: this.list(this.tags), tagsLock: this.tagsLock,
        totalBookCount: this.totalBookCount.trim().length === 0 ? null : Number(this.totalBookCount),
        totalBookCountLock: this.totalBookCountLock,
        sharingLabels: this.list(this.sharingLabels), sharingLabelsLock: this.sharingLabelsLock,
        links: parseWebLinks(this.links), linksLock: this.linksLock,
        alternateTitles: parseAlternativeTitles(this.alternateTitles),
        alternateTitlesLock: this.alternateTitlesLock
      };
      await komgaClient.updateSeriesMetadata(this.seriesId, request);
      this.message = localizedString($r('app.string.series_information_saved'));
    } catch (error) { this.message = (error as Error).message; }
    finally { this.saving = false; }
  }

  private async action(kind: string): Promise<void> {
    try {
      await komgaClient.runSeriesAction(this.seriesId, kind);
      this.message = localizedString($r('app.string.task_submitted'));
    } catch (error) { this.message = (error as Error).message; }
  }

  private openKomfIdentify(): void {
    if (this.series === undefined) return;
    router.pushUrl({ url: 'pages/KomfIdentify', params: {
      seriesId: this.seriesId,
      libraryId: this.series.libraryId,
      seriesName: this.series.metadata.title || this.series.name
    } });
  }

  private openKomfReset(): void {
    if (this.series === undefined) return;
    router.pushUrl({ url: 'pages/KomfResetMetadata', params: {
      scope: 'series',
      seriesId: this.seriesId,
      libraryId: this.series.libraryId,
      displayName: this.series.metadata.title || this.series.name
    } });
  }

  private publicationStatusValues(): string[] {
    return seriesPublicationStatusValues(this.status);
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

  private publicationStatusOptions(): SelectOption[] {
    const values = this.publicationStatusValues();
    const options: SelectOption[] = [];
    values.forEach((value: string) => {
      const option: SelectOption = { value: this.publicationStatusLabel(value) };
      options.push(option);
    });
    return options;
  }

  private publicationStatusIndex(): number {
    const index = this.publicationStatusValues().indexOf(this.status.trim().toUpperCase());
    return index < 0 ? 0 : index;
  }

  private readingDirectionOptions(): SelectOption[] {
    return [
      { value: localizedString($r('app.string.left_to_right')) },
      { value: localizedString($r('app.string.right_to_left')) },
      { value: localizedString($r('app.string.vertical')) },
      { value: localizedString($r('app.string.webtoon')) }
    ];
  }

  private readingDirectionIndex(): number {
    const index = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT', 'VERTICAL', 'WEBTOON'].indexOf(this.readingDirection);
    return index < 0 ? 0 : index;
  }

  @Builder
  private field(label: ResourceStr, value: string, multiline: boolean,
    onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(label).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
      if (multiline) {
        TextArea({ text: value }).height(132).width('100%').onChange(onChange)
      } else {
        TextInput({ text: value }).height(48).width('100%').onChange(onChange)
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private lockRow(value: boolean, onChange: (value: boolean) => void) {
    Row({ space: 8 }) {
      Text($r('app.string.metadata_lock')).fontSize(13)
        .fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: value }).onChange(onChange)
    }.width('100%').constraintSize({ minHeight: 40 })
  }

  @Builder
  private publicationStatusField() {
    Column({ space: 6 }) {
      Text($r('app.string.publication_status')).fontSize(14)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Select(this.publicationStatusOptions())
        .selected(this.publicationStatusIndex())
        .value(this.status.trim().length === 0 ? '' : this.publicationStatusOptions()[this.publicationStatusIndex()].value)
        .width('100%').height(48)
        .onSelect((index: number) => {
          this.status = this.publicationStatusValues()[index];
        })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.edit_series')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 14 }) {
          this.field($r('app.string.title'), this.title, false, (value: string) => this.title = value)
          this.lockRow(this.titleLock, (value: boolean) => this.titleLock = value)
          this.field($r('app.string.sort_title'), this.titleSort, false, (value: string) => this.titleSort = value)
          this.lockRow(this.titleSortLock, (value: boolean) => this.titleSortLock = value)
          this.field($r('app.string.summary'), this.summary, true, (value: string) => this.summary = value)
          this.lockRow(this.summaryLock, (value: boolean) => this.summaryLock = value)
          this.field($r('app.string.publisher'), this.publisher, false, (value: string) => this.publisher = value)
          this.lockRow(this.publisherLock, (value: boolean) => this.publisherLock = value)
          this.field($r('app.string.language'), this.language, false, (value: string) => this.language = value)
          this.lockRow(this.languageLock, (value: boolean) => this.languageLock = value)
          this.publicationStatusField()
          this.lockRow(this.statusLock, (value: boolean) => this.statusLock = value)
          Column({ space: 6 }) {
            Text($r('app.string.reading_direction')).fontSize(14)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            Select(this.readingDirectionOptions()).selected(this.readingDirectionIndex())
              .value(this.readingDirectionOptions()[this.readingDirectionIndex()].value)
              .width('100%').height(48).onSelect((index: number) => {
              this.readingDirection = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT', 'VERTICAL', 'WEBTOON'][index];
            })
          }.width('100%')
          this.lockRow(this.readingDirectionLock, (value: boolean) => this.readingDirectionLock = value)
          this.field($r('app.string.age_rating'), this.ageRating, false, (value: string) => this.ageRating = value)
          this.lockRow(this.ageRatingLock, (value: boolean) => this.ageRatingLock = value)
          this.field($r('app.string.genres_separator_hint'), this.genres, false, (value: string) => this.genres = value)
          this.lockRow(this.genresLock, (value: boolean) => this.genresLock = value)
          this.field($r('app.string.tags_separator_hint'), this.tags, false, (value: string) => this.tags = value)
          this.lockRow(this.tagsLock, (value: boolean) => this.tagsLock = value)
          this.field($r('app.string.total_book_count'), this.totalBookCount, false,
            (value: string) => this.totalBookCount = value)
          this.lockRow(this.totalBookCountLock, (value: boolean) => this.totalBookCountLock = value)
          this.field($r('app.string.sharing_labels'), this.sharingLabels, false,
            (value: string) => this.sharingLabels = value)
          this.lockRow(this.sharingLabelsLock, (value: boolean) => this.sharingLabelsLock = value)
          this.field($r('app.string.web_links_hint'), this.links, true, (value: string) => this.links = value)
          this.lockRow(this.linksLock, (value: boolean) => this.linksLock = value)
          this.field($r('app.string.alternative_titles_hint'), this.alternateTitles, true,
            (value: string) => this.alternateTitles = value)
          this.lockRow(this.alternateTitlesLock, (value: boolean) => this.alternateTitlesLock = value)
          Text($r('app.string.maintenance_actions')).fontSize(17).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
          Flex({ wrap: FlexWrap.Wrap }) {
            Button($r('app.string.analyze')).height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('analyze'))
            Button($r('app.string.refresh_metadata')).height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('metadata/refresh'))
            if (this.komfConfigured) {
              Button($r('app.string.komf_identify')).height(44).margin({ right: 8, bottom: 8 })
                .enabled(this.series !== undefined).onClick(() => this.openKomfIdentify())
              Button($r('app.string.komf_reset')).height(44).margin({ right: 8, bottom: 8 })
                .enabled(this.series !== undefined).onClick(() => this.openKomfReset())
            }
          }.width('100%')
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
