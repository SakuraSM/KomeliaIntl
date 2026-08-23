import { AppState } from '../state/AppState';
import { PageTitle } from '../components/CommonUi';

@Component
export struct SettingsPage {
  @ObjectLink state: AppState;

  @Builder
  private settingRow(icon: string, title: string, description: string) {
    Row({ space: 14 }) {
      Text(icon)
        .fontSize(24)
        .fontColor($r('app.color.on_surface_variant'))
        .width(32)
      Column({ space: 3 }) {
        Text(title)
          .fontSize(16)
          .fontColor($r('app.color.on_surface'))
          .width('100%')
        Text(description)
          .fontSize(13)
          .fontColor($r('app.color.on_surface_variant'))
          .maxLines(2)
          .width('100%')
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .minHeight(68)
    .padding({ left: 16, right: 16, top: 10, bottom: 10 })
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        PageTitle('设置', this.state.user?.email || '')

        Column() {
          this.settingRow('◐', '外观', '跟随系统深浅色；OLED 模式将在后续原生阶段开放')
          Divider().color($r('app.color.outline')).margin({ left: 62 })
          this.settingRow('⌁', '网络连接', `当前服务器：${this.state.user === undefined ? '-' : '已连接'}`)
          Divider().color($r('app.color.outline')).margin({ left: 62 })
          this.settingRow('⇩', '离线模式', '原生下载队列与文件管理正在迁移')
        }
        .width('100%')
        .backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') })
        .borderRadius(16)

        Column() {
          this.settingRow('ⓘ', '关于 Komelia', '版本 0.18.13 HarmonyOS Preview')
          Divider().color($r('app.color.outline')).margin({ left: 62 })
          this.settingRow('</>', '项目来源',
            'SakuraSM/KomeliaIntl，基于 Snd-R/Komelia 修改；Apache-2.0')
        }
        .width('100%')
        .backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') })
        .borderRadius(16)

        Button($r('app.string.logout'))
          .width('100%')
          .height(52)
          .fontColor($r('app.color.error'))
          .backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') })
          .borderRadius(16)
          .onClick(() => this.state.signOut())
      }
      .width('100%')
      .constraintSize({ maxWidth: 840 })
      .padding({ left: 12, right: 12, top: 16, bottom: 32 })
    }
    .width('100%')
    .height('100%')
  }
}
