import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { KomgaAuthenticationActivity } from '../model/KomgaModels';
import { formatDateTime } from '../model/UserPresentation';
import { komgaClient } from '../network/KomgaClient';

interface AuthenticationActivityParams { forMe?: boolean; }

@Entry
@Component
struct AuthenticationActivityPage {
  @State activities: KomgaAuthenticationActivity[] = [];
  @State loading: boolean = true;
  @State errorMessage: string = '';
  @State page: number = 0;
  @State totalPages: number = 0;
  private forMe: boolean = true;

  private text(resource: Resource): string {
    return localizedString(resource);
  }

  aboutToAppear(): void {
    const params = router.getParams() as AuthenticationActivityParams | undefined;
    this.forMe = params?.forMe !== false;
    this.load(0);
  }

  private async load(page: number): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      const result = await komgaClient.getAuthenticationActivity(this.forMe, page, 30);
      this.activities = result.content;
      this.page = result.number;
      this.totalPages = result.totalPages;
    } catch (error) {
      this.errorMessage = (error as Error).message || this.text($r('app.string.authentication_activity_load_failed'));
    } finally {
      this.loading = false;
    }
  }

  @Builder
  private activityCard(item: KomgaAuthenticationActivity) {
    Column({ space: 8 }) {
      Row({ space: 8 }) {
        Text(formatDateTime(item.dateTime)).fontSize(13).fontWeight(FontWeight.Medium).layoutWeight(1)
        Text(item.source || 'Komga').fontSize(12).fontColor($r('app.color.on_surface_variant'))
      }.width('100%')
      if (!this.forMe) {
        Text(item.email || '-').fontSize(14).fontColor($r('app.color.primary')).width('100%')
      }
      Row({ space: 8 }) {
        Text(`IP：${item.ip || '-'}`).fontSize(13).layoutWeight(1)
        Text(item.success ? $r('app.string.success') : $r('app.string.failed'))
          .fontSize(13).fontWeight(FontWeight.Bold)
          .fontColor(item.success ? $r('app.color.primary') : $r('app.color.error'))
      }.width('100%')
      if (!item.success && item.error.length > 0) {
        Text(item.error).fontSize(12).fontColor($r('app.color.error')).width('100%').maxLines(2)
      }
      Text(item.userAgent || '-').fontSize(12).fontColor($r('app.color.on_surface_variant'))
        .width('100%').maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text(this.forMe ? $r('app.string.my_authentication_activity') :
          $r('app.string.authentication_activity'))
          .fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load(this.page))
      } else if (this.activities.length === 0) {
        EmptyState($r('app.string.no_authentication_activity'))
      } else {
        List({ space: 10 }) {
          ForEach(this.activities, (item: KomgaAuthenticationActivity) => {
            ListItem() { this.activityCard(item) }
          }, (item: KomgaAuthenticationActivity) => `${item.userId}:${item.dateTime}:${item.ip}`)
          if (this.totalPages > 1) {
            ListItem() {
              Row({ space: 12 }) {
                Button($r('app.string.previous_page')).height(44).enabled(this.page > 0)
                  .onClick(() => this.load(this.page - 1))
                Text(`${this.page + 1} / ${this.totalPages}`).fontSize(14)
                  .fontColor($r('app.color.on_surface_variant'))
                Button($r('app.string.next_page')).height(44).enabled(this.page + 1 < this.totalPages)
                  .onClick(() => this.load(this.page + 1))
              }.width('100%').justifyContent(FlexAlign.Center).padding({ top: 6, bottom: 24 })
            }
          }
        }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })
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
