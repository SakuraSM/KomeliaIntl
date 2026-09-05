import { router } from '@kit.ArkUI';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';

interface KomfResetMetadataParams {
  scope: string;
  libraryId: string;
  seriesId?: string;
  displayName?: string;
}

@Entry
@Component
struct KomfResetMetadata {
  @State removeComicInfo: boolean = false;
  @State submitting: boolean = false;
  @State message: string = '';
  private scope: string = 'series';
  private libraryId: string = '';
  private seriesId: string = '';
  private displayName: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as KomfResetMetadataParams | undefined;
    this.scope = params?.scope === 'library' ? 'library' : 'series';
    this.libraryId = requiredRouteId(params?.libraryId);
    this.seriesId = params?.seriesId?.trim() || '';
    this.displayName = params?.displayName?.trim() || '';
  }

  private isLibrary(): boolean { return this.scope === 'library'; }

  private warning(): ResourceStr {
    return this.isLibrary() ? $r('app.string.komf_reset_library_warning') :
      $r('app.string.komf_reset_series_warning');
  }

  private async reset(): Promise<void> {
    if (this.submitting || this.libraryId.length === 0 || (!this.isLibrary() && this.seriesId.length === 0)) return;
    this.submitting = true;
    this.message = '';
    try {
      await configureStoredKomf();
      if (this.isLibrary()) await komfRepository.resetLibrary(this.libraryId, this.removeComicInfo);
      else await komfRepository.resetSeries(this.libraryId, this.seriesId, this.removeComicInfo);
      this.getUIContext().getPromptAction().showToast({ message: $r('app.string.komf_reset_submitted') });
      router.back();
    } catch (error) {
      this.message = (error as Error).message;
    } finally {
      this.submitting = false;
    }
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.isLibrary() ? $r('app.string.komf_reset_library_title') :
          $r('app.string.komf_reset_series_title')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      Scroll() {
        Column({ space: 16 }) {
          Column({ space: 14 }) {
            if (this.displayName.length > 0) {
              Text(this.displayName).fontSize(18).fontWeight(FontWeight.Medium).width('100%')
            }
            Text(this.warning()).fontSize(14).lineHeight(22)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            Row({ space: 12 }) {
              Column({ space: 3 }) {
                Text($r('app.string.komf_remove_comic_info')).fontSize(15).fontWeight(FontWeight.Medium)
                  .width('100%')
                Text($r('app.string.komf_remove_comic_info_description')).fontSize(12)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Toggle({ type: ToggleType.Switch, isOn: this.removeComicInfo })
                .onChange((value: boolean) => this.removeComicInfo = value)
            }.width('100%').constraintSize({ minHeight: 56 }).alignItems(VerticalAlign.Center)
          }.padding(16).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          Button(this.submitting ? $r('app.string.loading') : $r('app.string.komf_confirm_reset'))
            .height(48).width('100%').enabled(!this.submitting)
            .fontColor(Color.White).backgroundColor($r('app.color.error')).onClick(() => this.reset())
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.error')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%').align(Alignment.Top)
    }.width('100%').height('100%').align(Alignment.Top).id('komf_reset_metadata_page')
      .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }.width('100%').height('100%')
  }
}
