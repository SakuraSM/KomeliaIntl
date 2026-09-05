import { KomeliaColors } from '../core/design/KomeliaDesign';
import { router } from '@kit.ArkUI';
import { AppState } from '../state/AppState';
import { KomeliaLayout, KomeliaLayoutSpec, WindowClass } from '../core/design/KomeliaDesign';

@Component
export struct SettingsPage {
  @ObjectLink state: AppState;
  @State windowClass: WindowClass = WindowClass.COMPACT;

  private layout(): KomeliaLayoutSpec {
    return KomeliaLayout.spec(this.windowClass);
  }

  private confirmLogout(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.logout'),
      message: $r('app.string.logout_confirmation'),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        {
          value: $r('app.string.logout'),
          style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'),
          action: () => {
            this.state.signOut();
          }
        }
      ]
    });
  }

  @Builder
  private settingRow(icon: Resource, title: Resource, route: string, componentId: string,
    forMe: boolean = true) {
    Row({ space: 12 }) {
      Image(icon).width(22).height(22).fillColor($r('app.color.on_surface_variant'))
      Text(title).fontSize(16).lineHeight(23).fontColor($r('app.color.on_surface')).maxLines(1).layoutWeight(1)
      Image($r('app.media.settings_chevron')).width(22).height(22)
        .fillColor($r('app.color.on_surface_variant'))
    }
    .width('100%').height(this.layout().minimumTouchTarget).padding({ left: 10, right: 10 })
    .id(componentId)
    .onClick(() => router.pushUrl({ url: route, params: { forMe: forMe } }))
    .accessibilityText(title)
  }

  @Builder
  private separator() {
    Divider().color($r('app.color.outline')).margin({ left: 44 })
  }

  @Builder
  private sectionTitle(title: Resource) {
    Text(title).fontSize(14).lineHeight(20).fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface_variant')).padding({ left: 4 }).width('100%')
  }

  build() {
    Scroll() {
      Column({ space: this.layout().controlSpacing }) {
        Text($r('app.string.settings')).fontSize(22).lineHeight(27).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface'))
          .padding({ left: 4, top: 4 }).width('100%')

        Column({ space: 16 }) {
          Column({ space: 6 }) {
            this.sectionTitle($r('app.string.application_settings'))
            Column() {
              this.settingRow($r('app.media.settings_appearance'), $r('app.string.appearance'),
                'pages/AppearanceSettings', 'settings_appearance')
              this.separator()
              this.settingRow($r('app.media.settings_tune'), $r('app.string.home_groups'),
                'pages/HomeGroupSettings', 'settings_home_groups')
              this.separator()
              this.settingRow($r('app.media.settings_network'), $r('app.string.network_connection'),
                'pages/ServerManagement', 'settings_network')
              this.separator()
              this.settingRow($r('app.media.settings_image'), $r('app.string.image_reader'),
                'pages/ImageReaderSettings', 'settings_image_reader')
              this.separator()
              this.settingRow($r('app.media.nav_library'), $r('app.string.epub_reader'),
                'pages/EpubSettings', 'settings_epub_reader')
              this.separator()
              this.settingRow($r('app.media.settings_download'), $r('app.string.offline_mode'),
                'pages/OfflineDownloads', 'settings_offline')
              this.separator()
              this.settingRow($r('app.media.nav_library'), $r('app.string.local_libraries'),
                'pages/LocalLibraries', 'settings_local_libraries')
              this.separator()
              this.settingRow($r('app.media.settings_info'), $r('app.string.about'),
                'pages/AboutSettings', 'settings_about')
            }
            .width('100%').backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16).clip(true)
          }.width('100%')

          if (this.state.isOnline) {
            Column({ space: 6 }) {
              this.sectionTitle($r('app.string.user_settings'))
              Column() {
                this.settingRow($r('app.media.settings_account'), $r('app.string.my_account'),
                  'pages/AccountSettings', 'settings_account')
                this.separator()
                this.settingRow($r('app.media.settings_history'), $r('app.string.my_authentication_activity'),
                  'pages/AuthenticationActivity', 'settings_authentication_activity_me')
              }
              .width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16).clip(true)
            }.width('100%')
          }

          if (this.state.canManageServer()) {
            Column({ space: 6 }) {
              this.sectionTitle($r('app.string.server_settings'))
              Column() {
                this.settingRow($r('app.media.settings_server'), $r('app.string.server_general_settings'),
                  'pages/ServerSettings', 'settings_server')
                this.separator()
                this.settingRow($r('app.media.settings_account'), $r('app.string.user_management'),
                  'pages/UsersSettings', 'settings_users')
                this.separator()
                this.settingRow($r('app.media.settings_history'), $r('app.string.authentication_activity'),
                  'pages/AuthenticationActivity', 'settings_authentication_activity_all', false)
                this.separator()
                this.settingRow($r('app.media.settings_image'), $r('app.string.media_analysis'),
                  'pages/MediaAnalysis', 'settings_media_analysis')
                this.separator()
                this.settingRow($r('app.media.settings_server'), $r('app.string.media_management'),
                  'pages/Management', 'settings_management')
                this.separator()
                this.settingRow($r('app.media.settings_info'), $r('app.string.announcements'),
                  'pages/Announcements', 'settings_announcements')
              }
              .width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16).clip(true)
            }.width('100%')

            Column({ space: 6 }) {
              this.sectionTitle($r('app.string.komf_settings'))
              Column() {
                this.settingRow($r('app.media.settings_server'), $r('app.string.komf_connection_processing'),
                  'pages/KomfSettings', 'settings_komf')
              }
              .width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16).clip(true)
            }.width('100%')
          }

          Row({ space: 12 }) {
            Image($r('app.media.settings_logout')).width(22).height(22).fillColor($r('app.color.error'))
            Text($r('app.string.logout')).fontSize(16).fontColor($r('app.color.error')).layoutWeight(1)
          }
          .width('100%').height(this.layout().minimumTouchTarget).padding({ left: 10, right: 10 })
          .backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
          .id('settings_logout')
          .accessibilityText($r('app.string.logout'))
          .onClick(() => this.confirmLogout())
        }
        .width('100%')
      }
      .width('100%').constraintSize({ maxWidth: 840 })
      .padding({
        left: this.layout().pageHorizontalPadding,
        right: this.layout().pageHorizontalPadding,
        top: this.layout().pageVerticalPadding,
        bottom: this.layout().gridBottomPadding
      })
      .alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.Start)
    }.width('100%').height('100%').id('settings_page').align(Alignment.Top)
    .onAreaChange((_, area) => this.windowClass = KomeliaLayout.windowClass(Number(area.width)))
  }
}
