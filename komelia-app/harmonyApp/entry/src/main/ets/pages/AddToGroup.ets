import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { KomgaCollection, KomgaReadList } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

enum GroupKind {
  COLLECTION = 'collection',
  READ_LIST = 'readList'
}

interface AddToGroupParams {
  kind: GroupKind;
  targetId?: string;
  targetIds?: string[];
}

@Entry
@Component
struct AddToGroup {
  @State kind: GroupKind = GroupKind.COLLECTION;
  @State targetIds: string[] = [];
  @State query: string = '';
  @State collections: KomgaCollection[] = [];
  @State readLists: KomgaReadList[] = [];
  @State loading: boolean = true;
  @State saving: boolean = false;
  @State errorMessage: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as AddToGroupParams | undefined;
    this.kind = params?.kind === GroupKind.READ_LIST ? GroupKind.READ_LIST : GroupKind.COLLECTION;
    const values = params?.targetIds || (params?.targetId ? [params.targetId] : []);
    this.targetIds = values.filter((value: string, index: number) =>
      value.length > 0 && values.indexOf(value) === index);
    if (this.targetIds.length === 0) {
      this.loading = false;
      this.errorMessage = localizedString($r('app.string.target_item_missing'));
      return;
    }
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      if (this.kind === GroupKind.COLLECTION) {
        this.collections = (await komgaClient.getCollections('', 0, 1000)).content
          .sort((first: KomgaCollection, second: KomgaCollection) =>
            second.lastModifiedDate.localeCompare(first.lastModifiedDate));
      } else {
        this.readLists = (await komgaClient.getReadLists('', 0, 1000)).content
          .sort((first: KomgaReadList, second: KomgaReadList) =>
            second.lastModifiedDate.localeCompare(first.lastModifiedDate));
      }
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private filteredCollections(): KomgaCollection[] {
    const query = this.query.trim().toLocaleLowerCase();
    return query.length === 0 ? this.collections : this.collections.filter((item: KomgaCollection) =>
      item.name.toLocaleLowerCase().includes(query));
  }

  private filteredReadLists(): KomgaReadList[] {
    const query = this.query.trim().toLocaleLowerCase();
    return query.length === 0 ? this.readLists : this.readLists.filter((item: KomgaReadList) =>
      item.name.toLocaleLowerCase().includes(query));
  }

  private containsEvery(values: string[]): boolean {
    for (let index = 0; index < this.targetIds.length; index += 1) {
      if (!values.includes(this.targetIds[index])) return false;
    }
    return true;
  }

  private mergeTargets(values: string[]): string[] {
    const merged = values.slice();
    for (let index = 0; index < this.targetIds.length; index += 1) {
      if (!merged.includes(this.targetIds[index])) merged.push(this.targetIds[index]);
    }
    return merged;
  }

  private async create(): Promise<void> {
    const name = this.query.trim();
    if (name.length === 0 || this.saving) return;
    this.saving = true;
    this.errorMessage = '';
    try {
      if (this.kind === GroupKind.COLLECTION) {
        await komgaClient.createCollection({ name: name, ordered: false, seriesIds: this.targetIds });
      } else {
        await komgaClient.createReadList({ name: name, summary: '', ordered: true, bookIds: this.targetIds });
      }
      router.back();
    } catch (error) {
      this.errorMessage = (error as Error).message;
      this.saving = false;
    }
  }

  private async addCollection(item: KomgaCollection): Promise<void> {
    if (this.containsEvery(item.seriesIds) || this.saving) return;
    this.saving = true;
    try {
      await komgaClient.updateCollection(item.id, {
        name: item.name, ordered: item.ordered, seriesIds: this.mergeTargets(item.seriesIds)
      });
      router.back();
    } catch (error) {
      this.errorMessage = (error as Error).message;
      this.saving = false;
    }
  }

  private async addReadList(item: KomgaReadList): Promise<void> {
    if (this.containsEvery(item.bookIds) || this.saving) return;
    this.saving = true;
    try {
      await komgaClient.updateReadList(item.id, {
        name: item.name, summary: item.summary, ordered: item.ordered,
        bookIds: this.mergeTargets(item.bookIds)
      });
      router.back();
    } catch (error) {
      this.errorMessage = (error as Error).message;
      this.saving = false;
    }
  }

  @Builder
  private collectionRow(item: KomgaCollection) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(item.name).fontSize(16).fontWeight(FontWeight.Medium).maxLines(2).width('100%')
        Text(localizedString($r('app.string.series_count'), item.seriesIds.length)).fontSize(13)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Text(this.containsEvery(item.seriesIds) ? '✓' : '＋').fontSize(22)
        .fontColor(this.containsEvery(item.seriesIds) ?
          $r('app.color.primary') : $r('app.color.on_surface_variant'))
    }.constraintSize({ minHeight: 64 }).padding({ left: 16, right: 16, top: 10, bottom: 10 }).width('100%')
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      .border({ width: 1, color: $r('app.color.outline') })
      .enabled(!this.containsEvery(item.seriesIds) && !this.saving)
      .onClick(() => this.addCollection(item))
  }

  @Builder
  private readListRow(item: KomgaReadList) {
    Row({ space: 12 }) {
      Column({ space: 3 }) {
        Text(item.name).fontSize(16).fontWeight(FontWeight.Medium).maxLines(2).width('100%')
        Text(localizedString($r('app.string.books_count'), item.bookIds.length)).fontSize(13)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      Text(this.containsEvery(item.bookIds) ? '✓' : '＋').fontSize(22)
        .fontColor(this.containsEvery(item.bookIds) ?
          $r('app.color.primary') : $r('app.color.on_surface_variant'))
    }.constraintSize({ minHeight: 64 }).padding({ left: 16, right: 16, top: 10, bottom: 10 }).width('100%')
      .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
      .border({ width: 1, color: $r('app.color.outline') })
      .enabled(!this.containsEvery(item.bookIds) && !this.saving)
      .onClick(() => this.addReadList(item))
  }

  @Builder
  private pageContent() {
    Column({ space: 12 }) {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.kind === GroupKind.COLLECTION ? $r('app.string.add_to_collection') :
          $r('app.string.add_to_read_list')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      Row({ space: 8 }) {
        TextInput({ text: this.query, placeholder: this.kind === GroupKind.COLLECTION ?
          $r('app.string.search_or_create_collection') : $r('app.string.search_or_create_read_list') })
          .height(48).layoutWeight(1).onChange((value: string) => this.query = value)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.create')).height(48)
          .enabled(this.query.trim().length > 0 && !this.saving).onClick(() => this.create())
      }.width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else if ((this.kind === GroupKind.COLLECTION && this.filteredCollections().length === 0) ||
        (this.kind === GroupKind.READ_LIST && this.filteredReadLists().length === 0)) {
        EmptyState($r('app.string.no_filter_results'))
      } else {
        List({ space: 8 }) {
          if (this.kind === GroupKind.COLLECTION) {
            ForEach(this.filteredCollections(), (item: KomgaCollection) => {
              ListItem() { this.collectionRow(item) }
            }, (item: KomgaCollection) => item.id)
          } else {
            ForEach(this.filteredReadLists(), (item: KomgaReadList) => {
              ListItem() { this.readListRow(item) }
            }, (item: KomgaReadList) => item.id)
          }
        }.layoutWeight(1).width('100%')
      }
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
      .align(Alignment.Top).justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
