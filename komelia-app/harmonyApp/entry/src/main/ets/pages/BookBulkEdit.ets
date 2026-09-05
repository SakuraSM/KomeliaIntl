import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { authorsForRole, splitMetadataValues } from '../model/MetadataTextCodec';
import { BookMetadataUpdateRequest, KomgaAuthor } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface BookBulkEditParams { bookIds: string[]; }

@Entry
@Component
struct BookBulkEdit {
  @State applyAuthors: boolean = false;
  @State applyTags: boolean = false;
  @State authorsLock: boolean = false;
  @State tagsLock: boolean = false;
  @State writers: string = '';
  @State pencillers: string = '';
  @State inkers: string = '';
  @State colorists: string = '';
  @State letterers: string = '';
  @State coverArtists: string = '';
  @State editors: string = '';
  @State translators: string = '';
  @State tags: string = '';
  @State saving: boolean = false;
  @State message: string = '';
  private bookIds: string[] = [];

  aboutToAppear(): void {
    const params = router.getParams() as BookBulkEditParams | undefined;
    this.bookIds = params?.bookIds || [];
  }

  private authors(): KomgaAuthor[] {
    return authorsForRole(this.writers, 'writer')
      .concat(authorsForRole(this.pencillers, 'penciller'))
      .concat(authorsForRole(this.inkers, 'inker'))
      .concat(authorsForRole(this.colorists, 'colorist'))
      .concat(authorsForRole(this.letterers, 'letterer'))
      .concat(authorsForRole(this.coverArtists, 'cover'))
      .concat(authorsForRole(this.editors, 'editor'))
      .concat(authorsForRole(this.translators, 'translator'));
  }

  private request(): BookMetadataUpdateRequest {
    const request: BookMetadataUpdateRequest = {};
    if (this.applyAuthors) { request.authors = this.authors(); request.authorsLock = this.authorsLock; }
    if (this.applyTags) { request.tags = splitMetadataValues(this.tags); request.tagsLock = this.tagsLock; }
    return request;
  }

  private async save(): Promise<void> {
    if (this.bookIds.length === 0) return;
    this.saving = true;
    this.message = '';
    try {
      const request = this.request();
      for (let index = 0; index < this.bookIds.length; index++) {
        await komgaClient.updateBookMetadata(this.bookIds[index], request);
      }
      this.message = localizedString($r('app.string.bulk_metadata_saved'), this.bookIds.length);
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.saving = false;
    }
  }

  @Builder
  private authorField(label: ResourceStr, value: string, onChange: (value: string) => void) {
    Column({ space: 5 }) {
      Text(label).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
      TextInput({ text: value }).height(48).width('100%').enabled(this.applyAuthors).onChange(onChange)
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.bulk_edit_metadata')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text(localizedString($r('app.string.selected_count'), this.bookIds.length)).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.saving && this.bookIds.length > 0).onClick(() => this.save())
      }.height(68).width('100%')
      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 10 }) {
            Row({ space: 10 }) {
              Text($r('app.string.author_roles')).fontSize(17).fontWeight(FontWeight.Bold).layoutWeight(1)
              Text($r('app.string.apply_this_field')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant'))
              Toggle({ type: ToggleType.Switch, isOn: this.applyAuthors })
                .onChange((value: boolean) => this.applyAuthors = value)
            }.width('100%').constraintSize({ minHeight: 44 })
            this.authorField($r('app.string.writer'), this.writers, (value: string) => this.writers = value)
            this.authorField($r('app.string.penciller'), this.pencillers, (value: string) => this.pencillers = value)
            this.authorField($r('app.string.inker'), this.inkers, (value: string) => this.inkers = value)
            this.authorField($r('app.string.colorist'), this.colorists, (value: string) => this.colorists = value)
            this.authorField($r('app.string.letterer'), this.letterers, (value: string) => this.letterers = value)
            this.authorField($r('app.string.cover_artist'), this.coverArtists,
              (value: string) => this.coverArtists = value)
            this.authorField($r('app.string.editor'), this.editors, (value: string) => this.editors = value)
            this.authorField($r('app.string.translator'), this.translators,
              (value: string) => this.translators = value)
            Row({ space: 10 }) {
              Text($r('app.string.metadata_lock')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
              Toggle({ type: ToggleType.Switch, isOn: this.authorsLock }).enabled(this.applyAuthors)
                .onChange((value: boolean) => this.authorsLock = value)
            }.width('100%').constraintSize({ minHeight: 40 })
          }.padding(14).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          Column({ space: 10 }) {
            Row({ space: 10 }) {
              Text($r('app.string.tags_separator_hint')).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
              Text($r('app.string.apply_this_field')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant'))
              Toggle({ type: ToggleType.Switch, isOn: this.applyTags })
                .onChange((value: boolean) => this.applyTags = value)
            }.width('100%').constraintSize({ minHeight: 44 })
            TextInput({ text: this.tags }).height(48).width('100%').enabled(this.applyTags)
              .onChange((value: string) => this.tags = value)
            Row({ space: 10 }) {
              Text($r('app.string.metadata_lock')).fontSize(12)
                .fontColor($r('app.color.on_surface_variant')).layoutWeight(1)
              Toggle({ type: ToggleType.Switch, isOn: this.tagsLock }).enabled(this.applyTags)
                .onChange((value: boolean) => this.tagsLock = value)
            }.width('100%').constraintSize({ minHeight: 40 })
          }.padding(14).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
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
