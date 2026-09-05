import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { BookMetadataUpdateRequest, KomgaAuthor, KomgaBook, KomgaSeries,
  SeriesMetadataUpdateRequest } from '../model/KomgaModels';
import { authorsForRole, formatAlternativeTitles, formatAuthorsForRole, formatWebLinks,
  parseAlternativeTitles, parseWebLinks, splitMetadataValues } from '../model/MetadataTextCodec';
import { komgaClient } from '../network/KomgaClient';
import { router } from '@kit.ArkUI';

interface OneshotEditParams { seriesId: string; bookId?: string; }

@Entry
@Component
struct OneshotEdit {
  @State series: KomgaSeries | undefined = undefined;
  @State book: KomgaBook | undefined = undefined;
  @State tab: number = 0;
  @State title: string = '';
  @State titleSort: string = '';
  @State summary: string = '';
  @State publisher: string = '';
  @State language: string = '';
  @State readingDirection: string = 'LEFT_TO_RIGHT';
  @State ageRating: string = '';
  @State releaseDate: string = '';
  @State isbn: string = '';
  @State writers: string = '';
  @State pencillers: string = '';
  @State inkers: string = '';
  @State colorists: string = '';
  @State letterers: string = '';
  @State coverArtists: string = '';
  @State editors: string = '';
  @State translators: string = '';
  @State genres: string = '';
  @State tags: string = '';
  @State sharingLabels: string = '';
  @State links: string = '';
  @State alternateTitles: string = '';
  @State titleLock: boolean = false;
  @State titleSortLock: boolean = false;
  @State summaryLock: boolean = false;
  @State publisherLock: boolean = false;
  @State languageLock: boolean = false;
  @State readingDirectionLock: boolean = false;
  @State ageRatingLock: boolean = false;
  @State releaseDateLock: boolean = false;
  @State isbnLock: boolean = false;
  @State authorsLock: boolean = false;
  @State genresLock: boolean = false;
  @State tagsLock: boolean = false;
  @State sharingLabelsLock: boolean = false;
  @State linksLock: boolean = false;
  @State alternateTitlesLock: boolean = false;
  @State message: string = '';
  @State saving: boolean = false;
  private seriesId: string = '';
  private bookId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as OneshotEditParams | undefined;
    this.seriesId = requiredRouteId(params?.seriesId);
    this.bookId = params?.bookId || '';
    if (this.seriesId.length > 0) this.load();
  }

  private async load(): Promise<void> {
    try {
      this.series = await catalogRepository.seriesDetail(this.seriesId);
      if (this.bookId.length > 0) {
        this.book = await catalogRepository.bookDetail(this.bookId);
      } else {
        const books = await catalogRepository.seriesBooks(this.seriesId);
        this.book = books.length > 0 ? books[0] : undefined;
      }
      if (this.book === undefined) throw new Error(localizedString($r('app.string.book_detail_load_failed')));
      this.bookId = this.book.id;
      this.title = this.book.metadata.title;
      this.titleSort = this.series.metadata.titleSort;
      this.summary = this.book.metadata.summary;
      this.publisher = this.series.metadata.publisher;
      this.language = this.series.metadata.language;
      this.readingDirection = this.series.metadata.readingDirection || 'LEFT_TO_RIGHT';
      this.ageRating = this.series.metadata.ageRating === null || this.series.metadata.ageRating === undefined ? '' :
        this.series.metadata.ageRating.toString();
      this.releaseDate = this.book.metadata.releaseDate || '';
      this.isbn = this.book.metadata.isbn;
      this.writers = formatAuthorsForRole(this.book.metadata.authors, 'writer');
      this.pencillers = formatAuthorsForRole(this.book.metadata.authors, 'penciller');
      this.inkers = formatAuthorsForRole(this.book.metadata.authors, 'inker');
      this.colorists = formatAuthorsForRole(this.book.metadata.authors, 'colorist');
      this.letterers = formatAuthorsForRole(this.book.metadata.authors, 'letterer');
      this.coverArtists = formatAuthorsForRole(this.book.metadata.authors, 'cover');
      this.editors = formatAuthorsForRole(this.book.metadata.authors, 'editor');
      this.translators = formatAuthorsForRole(this.book.metadata.authors, 'translator');
      this.genres = this.series.metadata.genres.join('、');
      this.tags = this.book.metadata.tags.join('、');
      this.sharingLabels = (this.series.metadata.sharingLabels || []).join('、');
      this.links = formatWebLinks(this.book.metadata.links || []);
      this.alternateTitles = formatAlternativeTitles(this.series.metadata.alternateTitles || []);
      this.titleLock = this.series.metadata.titleLock || false;
      this.titleSortLock = this.series.metadata.titleSortLock || false;
      this.summaryLock = this.book.metadata.summaryLock || false;
      this.publisherLock = this.series.metadata.publisherLock || false;
      this.languageLock = this.series.metadata.languageLock || false;
      this.readingDirectionLock = this.series.metadata.readingDirectionLock || false;
      this.ageRatingLock = this.series.metadata.ageRatingLock || false;
      this.releaseDateLock = this.book.metadata.releaseDateLock || false;
      this.isbnLock = this.book.metadata.isbnLock || false;
      this.authorsLock = this.book.metadata.authorsLock || false;
      this.genresLock = this.series.metadata.genresLock || false;
      this.tagsLock = this.book.metadata.tagsLock || false;
      this.sharingLabelsLock = this.series.metadata.sharingLabelsLock || false;
      this.linksLock = this.book.metadata.linksLock || false;
      this.alternateTitlesLock = this.series.metadata.alternateTitlesLock || false;
    } catch (error) {
      this.message = (error as Error).message || localizedString($r('app.string.book_detail_load_failed'));
    }
  }

  private values(value: string): string[] {
    return splitMetadataValues(value);
  }

  private allAuthors(): KomgaAuthor[] {
    return authorsForRole(this.writers, 'writer')
      .concat(authorsForRole(this.pencillers, 'penciller'))
      .concat(authorsForRole(this.inkers, 'inker'))
      .concat(authorsForRole(this.colorists, 'colorist'))
      .concat(authorsForRole(this.letterers, 'letterer'))
      .concat(authorsForRole(this.coverArtists, 'cover'))
      .concat(authorsForRole(this.editors, 'editor'))
      .concat(authorsForRole(this.translators, 'translator'));
  }

  private async save(): Promise<void> {
    if (this.series === undefined || this.book === undefined) return;
    this.saving = true;
    try {
      const seriesRequest: SeriesMetadataUpdateRequest = {
        title: this.title.trim(), titleLock: this.titleLock,
        titleSort: this.titleSort.trim(), titleSortLock: this.titleSortLock,
        summary: this.series.metadata.summary, summaryLock: this.series.metadata.summaryLock || false,
        publisher: this.publisher.trim(), publisherLock: this.publisherLock,
        language: this.language.trim(), languageLock: this.languageLock,
        status: this.series.metadata.status, statusLock: this.series.metadata.statusLock || false,
        readingDirection: this.readingDirection, readingDirectionLock: this.readingDirectionLock,
        ageRating: this.ageRating.trim().length === 0 ? null : Number(this.ageRating),
        ageRatingLock: this.ageRatingLock,
        genres: this.values(this.genres), genresLock: this.genresLock,
        tags: this.series.metadata.tags, tagsLock: this.series.metadata.tagsLock || false,
        totalBookCount: this.series.metadata.totalBookCount,
        totalBookCountLock: this.series.metadata.totalBookCountLock || false,
        sharingLabels: this.values(this.sharingLabels), sharingLabelsLock: this.sharingLabelsLock,
        links: this.series.metadata.links || [], linksLock: this.series.metadata.linksLock || false,
        alternateTitles: parseAlternativeTitles(this.alternateTitles),
        alternateTitlesLock: this.alternateTitlesLock
      };
      const bookRequest: BookMetadataUpdateRequest = {
        title: this.title.trim(), titleLock: this.book.metadata.titleLock || false,
        summary: this.summary.trim(), summaryLock: this.summaryLock,
        number: this.book.metadata.number, numberLock: this.book.metadata.numberLock || false,
        numberSort: this.book.metadata.numberSort, numberSortLock: this.book.metadata.numberSortLock || false,
        releaseDate: this.releaseDate.trim(), releaseDateLock: this.releaseDateLock,
        authors: this.allAuthors(), authorsLock: this.authorsLock,
        tags: this.values(this.tags), tagsLock: this.tagsLock,
        isbn: this.isbn.trim(), isbnLock: this.isbnLock,
        links: parseWebLinks(this.links), linksLock: this.linksLock
      };
      await komgaClient.updateSeriesMetadata(this.seriesId, seriesRequest);
      await komgaClient.updateBookMetadata(this.bookId, bookRequest);
      this.message = localizedString($r('app.string.book_information_saved'));
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
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
      if (multiline) TextArea({ text: value }).height(132).width('100%').onChange(onChange)
      else TextInput({ text: value }).height(48).width('100%').onChange(onChange)
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
  private tabButton(label: ResourceStr, index: number) {
    Button(label).height(44).layoutWeight(1).borderRadius(14)
      .backgroundColor(this.tab === index ? $r('app.color.primary_container') : KomeliaColors.surfaceContainer())
      .fontColor(this.tab === index ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .onClick(() => this.tab = index)
  }

  @Builder
  private generalTab() {
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
    Column({ space: 6 }) {
      Text($r('app.string.reading_direction')).fontSize(14)
        .fontColor($r('app.color.on_surface_variant')).width('100%')
      Select(this.readingDirectionOptions()).selected(this.readingDirectionIndex())
        .value(this.readingDirectionOptions()[this.readingDirectionIndex()].value)
        .width('100%').height(48).onSelect((index: number) =>
          this.readingDirection = ['LEFT_TO_RIGHT', 'RIGHT_TO_LEFT', 'VERTICAL', 'WEBTOON'][index])
    }.width('100%')
    this.lockRow(this.readingDirectionLock, (value: boolean) => this.readingDirectionLock = value)
    this.field($r('app.string.age_rating'), this.ageRating, false, (value: string) => this.ageRating = value)
    this.lockRow(this.ageRatingLock, (value: boolean) => this.ageRatingLock = value)
    this.field($r('app.string.release_date_hint'), this.releaseDate, false,
      (value: string) => this.releaseDate = value)
    this.lockRow(this.releaseDateLock, (value: boolean) => this.releaseDateLock = value)
    this.field('ISBN', this.isbn, false, (value: string) => this.isbn = value)
    this.lockRow(this.isbnLock, (value: boolean) => this.isbnLock = value)
  }

  @Builder
  private authorsTab() {
    this.field($r('app.string.writer'), this.writers, false, (value: string) => this.writers = value)
    this.field($r('app.string.penciller'), this.pencillers, false, (value: string) => this.pencillers = value)
    this.field($r('app.string.inker'), this.inkers, false, (value: string) => this.inkers = value)
    this.field($r('app.string.colorist'), this.colorists, false, (value: string) => this.colorists = value)
    this.field($r('app.string.letterer'), this.letterers, false, (value: string) => this.letterers = value)
    this.field($r('app.string.cover_artist'), this.coverArtists, false,
      (value: string) => this.coverArtists = value)
    this.field($r('app.string.editor'), this.editors, false, (value: string) => this.editors = value)
    this.field($r('app.string.translator'), this.translators, false, (value: string) => this.translators = value)
    this.lockRow(this.authorsLock, (value: boolean) => this.authorsLock = value)
  }

  @Builder
  private classificationTab() {
    this.field($r('app.string.genres_separator_hint'), this.genres, false, (value: string) => this.genres = value)
    this.lockRow(this.genresLock, (value: boolean) => this.genresLock = value)
    this.field($r('app.string.tags_separator_hint'), this.tags, false, (value: string) => this.tags = value)
    this.lockRow(this.tagsLock, (value: boolean) => this.tagsLock = value)
    this.field($r('app.string.sharing_labels'), this.sharingLabels, false,
      (value: string) => this.sharingLabels = value)
    this.lockRow(this.sharingLabelsLock, (value: boolean) => this.sharingLabelsLock = value)
    this.field($r('app.string.web_links_hint'), this.links, true, (value: string) => this.links = value)
    this.lockRow(this.linksLock, (value: boolean) => this.linksLock = value)
    this.field($r('app.string.alternative_titles_hint'), this.alternateTitles, true,
      (value: string) => this.alternateTitles = value)
    this.lockRow(this.alternateTitlesLock, (value: boolean) => this.alternateTitlesLock = value)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.edit_oneshot')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .id('oneshot_edit_save').height(44).enabled(!this.saving && this.book !== undefined)
          .onClick(() => this.save())
      }.height(64).width('100%')
      Row({ space: 8 }) {
        this.tabButton($r('app.string.edit_book'), 0)
        this.tabButton($r('app.string.author_roles'), 1)
        this.tabButton($r('app.string.tags'), 2)
      }.width('100%').padding({ left: 12, right: 12, bottom: 8 })
      Scroll() {
        Column({ space: 14 }) {
          if (this.tab === 0) this.generalTab()
          else if (this.tab === 1) this.authorsTab()
          else this.classificationTab()
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.id('oneshot_edit_scroll').layoutWeight(1).width('100%').align(Alignment.Top)
    }.id('oneshot_edit_page').width('100%').height('100%').align(Alignment.Top)
      .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }.width('100%').height('100%')
  }
}
