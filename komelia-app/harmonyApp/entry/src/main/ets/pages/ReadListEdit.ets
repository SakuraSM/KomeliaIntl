import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { KomgaReadList } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface ReadListEditParams {
  readListId?: string;
}

@Entry
@Component
struct ReadListEdit {
  @State name: string = '';
  @State summary: string = '';
  @State ordered: boolean = true;
  @State bookIds: string[] = [];
  @State loading: boolean = false;
  @State saving: boolean = false;
  @State message: string = '';
  private readListId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as ReadListEditParams | undefined;
    this.readListId = requiredRouteId(params?.readListId);
    if (this.readListId.length > 0) this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.message = '';
    try {
      const value: KomgaReadList = await komgaClient.getReadList(this.readListId);
      this.name = value.name;
      this.summary = value.summary;
      this.ordered = value.ordered;
      this.bookIds = value.bookIds;
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private async save(): Promise<void> {
    const name = this.name.trim();
    if (name.length === 0) {
      this.message = localizedString($r('app.string.name_required'));
      return;
    }
    this.saving = true;
    this.message = '';
    try {
      if (this.readListId.length > 0) {
        await komgaClient.updateReadList(this.readListId, {
          name: name, summary: this.summary.trim(), ordered: this.ordered, bookIds: this.bookIds
        });
      } else {
        await komgaClient.createReadList({
          name: name, summary: this.summary.trim(), ordered: this.ordered, bookIds: []
        });
      }
      router.back();
    } catch (error) {
      this.message = (error as Error).message;
      this.saving = false;
    }
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.readListId.length > 0 ? $r('app.string.edit_read_list') :
          $r('app.string.create_read_list')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? $r('app.string.saving') : $r('app.string.save_changes'))
          .height(44).enabled(!this.loading && !this.saving).onClick(() => this.save())
      }.height(64).width('100%')

      if (this.loading) {
        LoadingState()
      } else {
        Scroll() {
          Column({ space: 14 }) {
            Text($r('app.string.name')).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
            TextInput({ text: this.name, placeholder: $r('app.string.read_list_name') })
              .height(52).width('100%').onChange((value: string) => this.name = value)
            Text($r('app.string.summary')).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
            TextArea({ text: this.summary, placeholder: $r('app.string.read_list_summary') })
              .height(120).width('100%').onChange((value: string) => this.summary = value)
            Row() {
              Column({ space: 3 }) {
                Text($r('app.string.ordered_read_list')).fontSize(16).width('100%')
                Text($r('app.string.ordered_read_list_description')).fontSize(13)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Toggle({ type: ToggleType.Switch, isOn: this.ordered })
                .onChange((value: boolean) => this.ordered = value)
            }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
            if (this.readListId.length > 0) {
              Text(localizedString($r('app.string.read_list_preserves_books'), this.bookIds.length))
                .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
            }
            if (this.message.length > 0) {
              Text(this.message).fontSize(13).fontColor($r('app.color.error')).width('100%')
            }
          }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 720 })
        }.layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }.width('100%').height('100%').align(Alignment.Top).justifyContent(FlexAlign.Start)
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
