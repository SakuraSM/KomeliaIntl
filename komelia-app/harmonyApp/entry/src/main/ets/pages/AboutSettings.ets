import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { AppBuildInfo } from '../core/AppBuildInfo';
import { KomeliaAboutLayout, KomeliaColors } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';

const PROJECT_REPOSITORY_URL = 'https://github.com/SakuraSM/KomeliaIntl';
const UPSTREAM_REPOSITORY_URL = 'https://github.com/Snd-R/Komelia';

@Entry
@Component
struct AboutSettings {
  private text(resource: Resource, ...args: Array<string | number>): string {
    return localizedString(resource, ...args);
  }

  private openRepository(url: string): void {
    const context = getContext(this) as common.UIAbilityContext;
    context.openLink(url).catch(() => {});
  }

  @Builder
  private sectionTitle(title: ResourceStr) {
    Text(title)
      .width('100%')
      .fontSize(22)
      .fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.on_surface_variant'))
  }

  @Builder
  private repositoryRow(title: ResourceStr, url: string, componentId: string) {
    Row({ space: 12 }) {
      Image($r('app.media.about_code'))
        .width(24)
        .height(24)
        .fillColor($r('app.color.primary'))
      Column({ space: 2 }) {
        Text(title)
          .width('100%')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_surface'))
        Text(url.replace('https://', ''))
          .width('100%')
          .fontSize(12)
          .fontColor($r('app.color.on_surface_variant'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Image($r('app.media.about_open_in_new'))
        .width(24)
        .height(24)
        .fillColor($r('app.color.on_surface_variant'))
    }
    .id(componentId)
    .width('100%')
    .constraintSize({ minHeight: KomeliaAboutLayout.repositoryRowHeight })
    .padding(12)
    .alignItems(VerticalAlign.Center)
    .backgroundColor(KomeliaColors.surfaceContainerHigh())
    .borderRadius(12)
    .onClick(() => this.openRepository(url))
    .accessibilityText(url)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back'))
            .width(24)
            .height(24)
            .fillColor($r('app.color.on_surface'))
        }
        .width(48)
        .height(48)
        .backgroundColor(Color.Transparent)
        .onClick(() => router.back())
        .accessibilityText($r('app.string.back'))
        Text($r('app.string.about'))
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .layoutWeight(1)
      }
      .height(64)
      .width('100%')

      Scroll() {
        Column({ space: KomeliaAboutLayout.sectionSpacing }) {
          Column({ space: 8 }) {
            this.sectionTitle('Komelia Intl')
            Column({ space: 12 }) {
              Row({ space: 12 }) {
                Image($r('app.media.settings_info'))
                  .width(24)
                  .height(24)
                  .fillColor($r('app.color.primary'))
                Text(this.text($r('app.string.about_version'), AppBuildInfo.versionName))
                  .fontSize(16)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.on_surface'))
                  .layoutWeight(1)
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              Text($r('app.string.about_description'))
                .width('100%')
                .fontSize(14)
                .lineHeight(20)
                .fontColor($r('app.color.on_surface_variant'))
            }
            .id('about_product_card')
            .width('100%')
            .padding({ left: 12, right: 12, top: KomeliaAboutLayout.cardVerticalPadding,
              bottom: KomeliaAboutLayout.cardVerticalPadding })
            .backgroundColor(KomeliaColors.surfaceContainer())
            .border({ width: 1, color: $r('app.color.outline') })
            .borderRadius(16)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)

          Column({ space: 8 }) {
            this.sectionTitle($r('app.string.source_code'))
            Column({ space: 12 }) {
              this.repositoryRow($r('app.string.project_repository'), PROJECT_REPOSITORY_URL,
                'about_project_repository')
              this.repositoryRow($r('app.string.original_repository'), UPSTREAM_REPOSITORY_URL,
                'about_upstream_repository')
            }
            .id('about_source_card')
            .width('100%')
            .padding({ left: 12, right: 12, top: KomeliaAboutLayout.cardVerticalPadding,
              bottom: KomeliaAboutLayout.cardVerticalPadding })
            .backgroundColor(KomeliaColors.surfaceContainer())
            .border({ width: 1, color: $r('app.color.outline') })
            .borderRadius(16)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .id('about_settings_page')
        .width('100%')
        .constraintSize({ maxWidth: 720 })
        .padding({ left: 12, right: 12, top: 12, bottom: 32 })
      }
      .layoutWeight(1)
      .width('100%')
      .align(Alignment.Top)
    }
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
