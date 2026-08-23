import { router } from '@kit.ArkUI';
import { AppState } from '../state/AppState';
import { PageTitle } from '../components/CommonUi';

@Component
export struct SettingsPage {
  @ObjectLink state: AppState;

  @Builder
  private settingRow(icon: string, title: string, description: string, route: string = '') {
    Row({ space: 14 }) {
      Text(icon).fontSize(22).fontColor($r('app.color.on_surface_variant')).width(32)
      Column({ space: 3 }) {
        Text(title).fontSize(16).fontColor($r('app.color.on_surface')).width('100%')
        Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).maxLines(2).width('100%')
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      if (route.length > 0) Text('›').fontSize(28).fontColor($r('app.color.on_surface_variant'))
    }.width('100%').minHeight(68).padding({ left: 16, right: 16, top: 10, bottom: 10 })
    .onClick(() => { if (route.length > 0) router.pushUrl({ url: route }); })
  }

  @Builder
  private separator() { Divider().color($r('app.color.outline')).margin({ left: 62 }) }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        PageTitle('设置', this.state.user?.email || '')
        Column() {
          this.settingRow('◐', '外观与语言', 'Light、Dark、OLED；系统、英文、简中', 'pages/AppearanceSettings')
          this.separator()
          this.settingRow('≡', '首页分组', '新增、编辑、排序和重置首页内容', 'pages/HomeGroupSettings')
          this.separator()
          this.settingRow('⌁', '网络与服务器', '管理 Komga 地址、局域网 HTTP 和连接状态', 'pages/ServerManagement')
          this.separator()
          this.settingRow('▣', '图像阅读器', '阅读模式、方向、缩放、颜色校正和 AI', 'pages/ImageReaderSettings')
          this.separator()
          this.settingRow('▤', 'EPUB 阅读器', '主题、字号、行高、边距和翻页模式', 'pages/EpubSettings')
          this.separator()
          this.settingRow('⇩', '离线与下载', '下载队列、离线书籍和空间占用', 'pages/OfflineDownloads')
        }.width('100%').backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

        Column() {
          this.settingRow('◆', '管理与活动', '书库任务、用户、登录活动和队列', 'pages/Management')
          this.separator()
          this.settingRow('K', 'Komf', '连接、Provider、处理、Job 与通知', 'pages/KomfSettings')
        }.width('100%').backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

        Column() {
          this.settingRow('i', '关于 Komelia', '版本 0.18.13 HarmonyOS Native')
          this.separator()
          this.settingRow('</>', '项目来源', 'SakuraSM/KomeliaIntl，基于 Snd-R/Komelia 修改；Apache-2.0')
        }.width('100%').backgroundColor($r('app.color.surface_high'))
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

        Button($r('app.string.logout')).width('100%').height(52)
          .fontColor($r('app.color.error')).backgroundColor($r('app.color.surface_high'))
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
          .onClick(() => this.state.signOut())
      }.width('100%').constraintSize({ maxWidth: 840 })
      .padding({ left: 12, right: 12, top: 16, bottom: 32 })
    }.width('100%').height('100%')
  }
}
