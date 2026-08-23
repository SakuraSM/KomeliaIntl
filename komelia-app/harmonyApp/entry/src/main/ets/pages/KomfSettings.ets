import { router } from '@kit.ArkUI';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { komfClient } from '../network/KomfClient';
import { AppPreferences } from '../persistence/AppPreferences';

@Entry
@Component
struct KomfSettings {
  @State origin: string = '';
  @State status: string = '未连接';
  @State configText: string = '';
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.origin = await AppPreferences.getString('komfOrigin');
    if (this.origin.length > 0) await this.connect();
  }
  private async connect(): Promise<void> {
    this.status = '连接中…';
    try {
      komfRepository.configure(this.origin);
      if (!await komfRepository.ping()) throw new Error('Komf 无响应');
      await AppPreferences.putString('komfOrigin', this.origin);
      this.configText = JSON.stringify(await komfClient.getConfig(), undefined, 2);
      this.status = '已连接';
    } catch (error) { this.status = (error as Error).message; }
  }
  private async runJobs(): Promise<void> {
    try { await komfClient.runAllJobs(); this.status = '全部 Job 已提交'; }
    catch (error) { this.status = (error as Error).message; }
  }
  build() {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 8 }) {
          Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
          Text('Komf').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        }.height(64).width('100%')
        TextInput({ text: this.origin, placeholder: 'https://komf.example.com' })
          .height(52).width('100%').onChange((value: string) => this.origin = value)
        Row({ space: 8 }) {
          Button('连接').height(48).onClick(() => this.connect())
          Button('运行全部 Job').height(48).onClick(() => this.runJobs())
        }.width('100%')
        Text(this.status).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
        if (this.configText.length > 0) {
          Text('当前配置').fontSize(17).fontWeight(FontWeight.Bold).width('100%')
          Text(this.configText).fontSize(12).fontFamily('monospace').width('100%').padding(16)
            .backgroundColor($r('app.color.surface_high')).borderRadius(16)
        }
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
    }.width('100%').height('100%')
  }
}
