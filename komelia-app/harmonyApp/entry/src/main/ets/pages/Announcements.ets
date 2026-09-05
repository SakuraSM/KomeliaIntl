import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { KomgaAnnouncement } from '../model/KomgaModels';
import { formatDateTime } from '../model/UserPresentation';
import { komgaClient } from '../network/KomgaClient';

function announcementText(item: KomgaAnnouncement): string {
  const value = item.summary || item.contentHtml || '';
  return value
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\s+\n/g, '\n')
    .replace(/\n\s+/g, '\n')
    .replace(/[ \t]+/g, ' ')
    .trim();
}

@Entry
@Component
struct AnnouncementsPage {
  @State announcements: KomgaAnnouncement[] = [];
  @State loading: boolean = true;
  @State errorMessage: string = '';

  aboutToAppear(): void {
    this.load();
  }

  private text(resource: Resource): string {
    return localizedString(resource);
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      this.announcements = (await komgaClient.getAnnouncements()).items || [];
    } catch (error) {
      this.errorMessage = (error as Error).message || this.text($r('app.string.announcements_load_failed'));
    } finally {
      this.loading = false;
    }
  }

  @Builder
  private title(item: KomgaAnnouncement) {
    if ((item.url || '').length > 0) {
      Hyperlink(item.url || '', item.title || this.text($r('app.string.announcement_untitled')))
    } else {
      Text(item.title || $r('app.string.announcement_untitled'))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.on_surface'))
        .maxLines(2)
    }
  }

  @Builder
  private announcementCard(item: KomgaAnnouncement) {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        Column() { this.title(item) }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        if (item._komga?.read === false) {
          Text($r('app.string.unread'))
            .fontSize(12)
            .fontColor($r('app.color.primary'))
            .padding({ left: 9, right: 9, top: 4, bottom: 4 })
            .backgroundColor($r('app.color.primary_container'))
            .borderRadius(12)
        }
      }.width('100%').alignItems(VerticalAlign.Top)
      if ((item.dateModified || '').length > 0) {
        Text(formatDateTime(item.dateModified || ''))
          .fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .width('100%')
      }
      if (announcementText(item).length > 0) {
        Text(announcementText(item))
          .fontSize(14)
          .fontColor($r('app.color.on_surface_variant'))
          .lineHeight(21)
          .width('100%')
      }
    }
    .padding(16)
    .width('100%')
    .backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') })
    .borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.announcements')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text($r('app.string.announcements_description')).fontSize(13)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      }.height(72).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else if (this.announcements.length === 0) {
        EmptyState($r('app.string.announcements_empty'))
      } else {
        List({ space: 12 }) {
          ForEach(this.announcements, (item: KomgaAnnouncement) => {
            ListItem() { this.announcementCard(item) }
          }, (item: KomgaAnnouncement) => item.id)
        }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })
      }
    }
    .padding({ left: 12, right: 12 })
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
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
