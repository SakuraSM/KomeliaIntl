import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { localizedString } from '../core/i18n/LocalizedResources';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { BookMetadataUpdateRequest, KomgaAuthor, KomgaBook } from '../model/KomgaModels';
import { authorsForRole, formatAuthorsForRole, formatWebLinks, parseWebLinks,
  splitMetadataValues } from '../model/MetadataTextCodec';
import { komgaClient } from '../network/KomgaClient';

interface BookEditParams { bookId: string; }

@Entry
@Component
struct BookEdit {
  @State book: KomgaBook | undefined = undefined;
  @State title: string = '';
  @State summary: string = '';
  @State number: string = '';
  @State numberSort: string = '';
  @State releaseDate: string = '';
  @State writers: string = '';
  @State pencillers: string = '';
  @State inkers: string = '';
  @State colorists: string = '';
  @State letterers: string = '';
  @State coverArtists: string = '';
  @State editors: string = '';
  @State translators: string = '';
  @State tags: string = '';
  @State isbn: string = '';
  @State links: string = '';
  @State titleLock: boolean = false;
  @State summaryLock: boolean = false;
  @State numberLock: boolean = false;
  @State numberSortLock: boolean = false;
  @State releaseDateLock: boolean = false;
  @State authorsLock: boolean = false;
  @State tagsLock: boolean = false;
  @State isbnLock: boolean = false;
  @State linksLock: boolean = false;
  @State message: string = '';
  @State saving: boolean = false;
  private bookId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as BookEditParams | undefined;
    this.bookId = requiredRouteId(params?.bookId);
    if (this.bookId.length > 0) this.load();
  }

  private async load(): Promise<void> {
    try {
      this.book = await catalogRepository.bookDetail(this.bookId);
      this.title = this.book.metadata.title;
      this.summary = this.book.metadata.summary;
      this.number = this.book.metadata.number;
      this.numberSort = this.book.metadata.numberSort.toString();
      this.releaseDate = this.book.metadata.releaseDate || '';
      this.writers = formatAuthorsForRole(this.book.metadata.authors, 'writer');
      this.pencillers = formatAuthorsForRole(this.book.metadata.authors, 'penciller');
      this.inkers = formatAuthorsForRole(this.book.metadata.authors, 'inker');
      this.colorists = formatAuthorsForRole(this.book.metadata.authors, 'colorist');
      this.letterers = formatAuthorsForRole(this.book.metadata.authors, 'letterer');
      this.coverArtists = formatAuthorsForRole(this.book.metadata.authors, 'cover');
      this.editors = formatAuthorsForRole(this.book.metadata.authors, 'editor');
      this.translators = formatAuthorsForRole(this.book.metadata.authors, 'translator');
      this.tags = this.book.metadata.tags.join('、');
      this.isbn = this.book.metadata.isbn;
      this.links = formatWebLinks(this.book.metadata.links || []);
      this.titleLock = this.book.metadata.titleLock || false;
      this.summaryLock = this.book.metadata.summaryLock || false;
      this.numberLock = this.book.metadata.numberLock || false;
      this.numberSortLock = this.book.metadata.numberSortLock || false;
      this.releaseDateLock = this.book.metadata.releaseDateLock || false;
      this.authorsLock = this.book.metadata.authorsLock || false;
      this.tagsLock = this.book.metadata.tagsLock || false;
      this.isbnLock = this.book.metadata.isbnLock || false;
      this.linksLock = this.book.metadata.linksLock || false;
    } catch (error) { this.message = (error as Error).message; }
  }

  private list(value: string): string[] {
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
    this.saving = true;
    try {
      const request: BookMetadataUpdateRequest = {
        title: this.title.trim(), titleLock: this.titleLock,
        summary: this.summary.trim(), summaryLock: this.summaryLock,
        number: this.number.trim(), numberLock: this.numberLock,
        numberSort: Number(this.numberSort) || 0, numberSortLock: this.numberSortLock,
        releaseDate: this.releaseDate.trim(), releaseDateLock: this.releaseDateLock,
        isbn: this.isbn.trim(), isbnLock: this.isbnLock,
        tags: this.list(this.tags), tagsLock: this.tagsLock,
        authors: this.allAuthors(), authorsLock: this.authorsLock,
        links: parseWebLinks(this.links), linksLock: this.linksLock
      };
      await komgaClient.updateBookMetadata(this.bookId, request);
      this.message = localizedString($r('app.string.book_information_saved'));
    } catch (error) { this.message = (error as Error).message; }
    finally { this.saving = false; }
  }

  private async action(kind: string): Promise<void> {
    try { await komgaClient.runBookAction(this.bookId, kind); this.message = localizedString($r('app.string.task_submitted')); }
    catch (error) { this.message = (error as Error).message; }
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
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.edit_book')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 14 }) {
          this.field($r('app.string.title'), this.title, false, (value: string) => this.title = value)
          this.lockRow(this.titleLock, (value: boolean) => this.titleLock = value)
          this.field($r('app.string.summary'), this.summary, true, (value: string) => this.summary = value)
          this.lockRow(this.summaryLock, (value: boolean) => this.summaryLock = value)
          this.field($r('app.string.volume_number'), this.number, false, (value: string) => this.number = value)
          this.lockRow(this.numberLock, (value: boolean) => this.numberLock = value)
          this.field($r('app.string.number_sort'), this.numberSort, false, (value: string) => this.numberSort = value)
          this.lockRow(this.numberSortLock, (value: boolean) => this.numberSortLock = value)
          this.field($r('app.string.release_date_hint'), this.releaseDate, false, (value: string) => this.releaseDate = value)
          this.lockRow(this.releaseDateLock, (value: boolean) => this.releaseDateLock = value)
          Text($r('app.string.author_roles')).fontSize(17).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
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
          this.field($r('app.string.tags_separator_hint'), this.tags, false, (value: string) => this.tags = value)
          this.lockRow(this.tagsLock, (value: boolean) => this.tagsLock = value)
          this.field('ISBN', this.isbn, false, (value: string) => this.isbn = value)
          this.lockRow(this.isbnLock, (value: boolean) => this.isbnLock = value)
          this.field($r('app.string.web_links_hint'), this.links, true, (value: string) => this.links = value)
          this.lockRow(this.linksLock, (value: boolean) => this.linksLock = value)
          Row({ space: 8 }) {
            Button($r('app.string.analyze')).height(44).onClick(() => this.action('analyze'))
            Button($r('app.string.refresh_metadata')).height(44).onClick(() => this.action('metadata/refresh'))
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
