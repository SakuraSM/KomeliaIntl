import { router } from '@kit.ArkUI';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { KomfMediaServerLibrary, KomfMetadataJob } from '../model/KomfModels';
import { AppPreferences } from '../persistence/AppPreferences';

@Entry
@Component
struct KomfSettings {
  @State origin: string = '';
  @State status: string = '未连接';
  @State configText: string = '';
  @State providers: string[] = [];
  @State jobs: KomfMetadataJob[] = [];
  @State libraries: KomfMediaServerLibrary[] = [];
  @State discordTemplates: string = '';
  @State appriseTemplates: string = '';
  @State allowHttp: boolean = false;
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    this.origin = await AppPreferences.getString('komfOrigin');
    this.allowHttp = await AppPreferences.getBoolean('komfAllowHttp');
    if (this.origin.length > 0) await this.connect();
  }
  private async connect(): Promise<void> {
    this.status = '连接中…';
    try {
      komfRepository.configure(this.origin, this.allowHttp);
      if (!await komfRepository.ping()) throw new Error('Komf 无响应');
      await AppPreferences.putString('komfOrigin', this.origin);
      await AppPreferences.putBoolean('komfAllowHttp', this.allowHttp);
      const values = await Promise.all([
        komfRepository.config(), komfRepository.providers(), komfRepository.jobs(),
        komfRepository.mediaServerLibraries(), komfRepository.discordTemplates(),
        komfRepository.appriseTemplates()
      ]);
      this.configText = JSON.stringify(values[0], undefined, 2);
      this.providers = values[1];
      this.jobs = values[2].content;
      this.libraries = values[3];
      this.discordTemplates = JSON.stringify(values[4], undefined, 2);
      this.appriseTemplates = JSON.stringify(values[5], undefined, 2);
      this.status = '已连接';
    } catch (error) { this.status = (error as Error).message; }
  }
  private async saveConfig(): Promise<void> {
    try {
      await komfRepository.updateConfig(JSON.parse(this.configText) as Object);
      this.status = '配置已保存';
    } catch (error) { this.status = `配置无效：${(error as Error).message}`; }
  }
  private async deleteJobs(): Promise<void> {
    try { await komfRepository.deleteAllJobs(); this.jobs = []; this.status = 'Job 历史已清空'; }
    catch (error) { this.status = (error as Error).message; }
  }
  private async saveNotificationTemplates(): Promise<void> {
    try {
      await Promise.all([
        komfRepository.updateDiscordTemplates(JSON.parse(this.discordTemplates) as Object),
        komfRepository.updateAppriseTemplates(JSON.parse(this.appriseTemplates) as Object)
      ]);
      this.status = '通知模板已保存';
    } catch (error) { this.status = `通知模板无效：${(error as Error).message}`; }
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
        Row() {
          Column({ space: 3 }) {
            Text('允许局域网 HTTP').fontSize(15).width('100%')
            Text('连接未加密，仅在可信局域网中启用').fontSize(12)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
          }.layoutWeight(1).alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.allowHttp })
            .onChange((value: boolean) => this.allowHttp = value)
        }.padding(14).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
        Row({ space: 8 }) {
          Button('连接').height(48).onClick(() => this.connect())
          Button('保存配置').height(48).onClick(() => this.saveConfig())
        }.width('100%')
        Text(this.status).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
        if (this.configText.length > 0) {
          Text('Provider').fontSize(17).fontWeight(FontWeight.Bold).width('100%')
          Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start, alignItems: ItemAlign.Center }) {
            ForEach(this.providers, (provider: string) => {
              Text(provider).fontSize(13).padding({ left: 12, right: 12, top: 7, bottom: 7 })
                .margin({ right: 8, bottom: 8 }).backgroundColor($r('app.color.primary_container')).borderRadius(16)
            }, (provider: string) => provider)
          }.width('100%')
          Text(`Komga 书库（${this.libraries.length}）`).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
          ForEach(this.libraries, (library: KomfMediaServerLibrary) => {
            Text(library.name).fontSize(14).padding(12).width('100%')
              .backgroundColor($r('app.color.surface_high')).borderRadius(12)
          }, (library: KomfMediaServerLibrary) => library.id)
          Row() {
            Text(`Job（${this.jobs.length}）`).fontSize(17).fontWeight(FontWeight.Bold).layoutWeight(1)
            Button('清空历史').height(40).fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
              .onClick(() => this.deleteJobs())
          }.width('100%')
          ForEach(this.jobs, (job: KomfMetadataJob) => {
            Column({ space: 4 }) {
              Text(`${job.status} · ${job.seriesId}`).fontSize(14).fontWeight(FontWeight.Medium).width('100%')
              Text(job.message || job.id).fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
            }.padding(12).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(12)
          }, (job: KomfMetadataJob) => job.id)
          Text('高级配置 JSON').fontSize(17).fontWeight(FontWeight.Bold).width('100%')
          TextArea({ text: this.configText, placeholder: '{}' }).fontSize(12).fontFamily('monospace')
            .height(420).width('100%').padding(12).backgroundColor($r('app.color.surface_high')).borderRadius(16)
            .onChange((value: string) => this.configText = value)
          Row() {
            Text('通知模板').fontSize(17).fontWeight(FontWeight.Bold).layoutWeight(1)
            Button('保存模板').height(40).onClick(() => this.saveNotificationTemplates())
          }.width('100%')
          Text('Discord').fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
          TextArea({ text: this.discordTemplates, placeholder: '{}' }).fontSize(12).fontFamily('monospace')
            .height(220).width('100%').padding(12).backgroundColor($r('app.color.surface_high')).borderRadius(16)
            .onChange((value: string) => this.discordTemplates = value)
          Text('Apprise').fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
          TextArea({ text: this.appriseTemplates, placeholder: '{}' }).fontSize(12).fontFamily('monospace')
            .height(220).width('100%').padding(12).backgroundColor($r('app.color.surface_high')).borderRadius(16)
            .onChange((value: string) => this.appriseTemplates = value)
        }
      }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
    }.width('100%').height('100%')
  }
}
