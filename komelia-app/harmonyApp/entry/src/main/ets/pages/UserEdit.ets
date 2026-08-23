import { router } from '@kit.ArkUI';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaUser } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface UserEditParams { userId: string; }

@Entry
@Component
struct UserEdit {
  @State user?: KomgaUser;
  @State administrator: boolean = false;
  @State password: string = '';
  @State message: string = '';
  private userId: string = '';

  aboutToAppear(): void {
    this.userId = (router.getParams() as UserEditParams).userId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      this.user = (await komgaClient.getUsers()).find((item: KomgaUser) => item.id === this.userId);
      this.administrator = (this.user?.roles || []).includes('ADMIN');
    } catch (error) { this.message = (error as Error).message; }
  }

  private async saveRoles(): Promise<void> {
    try {
      await adminRepository.updateUserRoles(this.userId, this.administrator ? ['USER', 'ADMIN'] : ['USER']);
      this.message = '权限已保存';
    } catch (error) { this.message = (error as Error).message; }
  }

  private async savePassword(): Promise<void> {
    if (this.password.length < 8) { this.message = '密码至少需要 8 位'; return; }
    try {
      await adminRepository.updateUserPassword(this.userId, this.password);
      this.password = '';
      this.message = '密码已更新';
    } catch (error) { this.message = (error as Error).message; }
  }

  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('编辑用户').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      Column({ space: 16 }) {
        Text(this.user?.email || '加载中…').fontSize(18).fontWeight(FontWeight.Medium).width('100%')
        Row() {
          Column({ space: 3 }) {
            Text('管理员权限').fontSize(16).width('100%')
            Text('允许管理用户、书库和服务端任务').fontSize(13)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
          }.layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.administrator })
            .onChange((value: boolean) => this.administrator = value)
        }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
        Button('保存权限').height(44).width('100%').onClick(() => this.saveRoles())
        Text('重置密码').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface_variant')).width('100%')
        TextInput({ text: this.password, placeholder: '至少 8 位' }).type(InputType.Password)
          .height(48).width('100%').onChange((value: string) => this.password = value)
        Button('更新密码').height(44).width('100%').onClick(() => this.savePassword())
        if (this.message.length > 0) {
          Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }
      }.padding(16).width('100%').constraintSize({ maxWidth: 720 })
      Blank()
    }.padding({ left: 12, right: 12, bottom: 24 }).width('100%').height('100%')
      .backgroundColor($r('app.color.surface'))
  }
}
