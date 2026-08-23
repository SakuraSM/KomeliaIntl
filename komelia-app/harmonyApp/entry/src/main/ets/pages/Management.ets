import { router } from '@kit.ArkUI';
import { LoadingState } from '../components/CommonUi';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaAuthenticationActivity, KomgaLibrary, KomgaUser } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

@Entry
@Component
struct Management {
  @State users: KomgaUser[] = [];
  @State activities: KomgaAuthenticationActivity[] = [];
  @State loading: boolean = true;
  @State message: string = '';
  @State newUserEmail: string = '';
  @State newUserPassword: string = '';
  @State newUserAdmin: boolean = false;
  private state: AppState = AppState.getInstance();

  aboutToAppear(): void { this.load(); }
  private isAdmin(): boolean { return (this.state.user?.roles || []).includes('ADMIN'); }
  private async load(): Promise<void> {
    this.loading = true;
    try {
      if (this.isAdmin()) this.users = await komgaClient.getUsers();
      this.activities = (await komgaClient.getAuthenticationActivity(!this.isAdmin())).content;
    } finally { this.loading = false; }
  }
  private async libraryAction(libraryId: string, action: string): Promise<void> {
    this.message = '正在提交任务…';
    try {
      if (action === 'scan') await adminRepository.scanLibrary(libraryId, false);
      else if (action === 'analyze') await adminRepository.analyzeLibrary(libraryId);
      else await adminRepository.emptyLibraryTrash(libraryId);
      this.message = '任务已提交';
    } catch (error) { this.message = (error as Error).message; }
  }

  private async addUser(): Promise<void> {
    if (this.newUserEmail.trim().length === 0 || this.newUserPassword.length < 8) {
      this.message = '请输入邮箱，并使用至少 8 位密码'; return;
    }
    try {
      const roles = this.newUserAdmin ? ['USER', 'ADMIN'] : ['USER'];
      await adminRepository.addUser(this.newUserEmail.trim(), this.newUserPassword, roles);
      this.newUserEmail = ''; this.newUserPassword = ''; this.message = '用户已创建'; await this.load();
    } catch (error) { this.message = (error as Error).message; }
  }

  private confirmDanger(title: string, message: string, action: () => Promise<void>): void {
    this.getUIContext().showAlertDialog({
      title: title,
      message: message,
      autoCancel: true,
      buttons: [
        { value: '取消' },
        { value: '确认', style: DialogButtonStyle.HIGHLIGHT, fontColor: $r('app.color.error'), action: () => {
          action().then(() => this.load()).catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }
  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('管理与活动').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      if (this.loading) { LoadingState() }
      else {
        List({ space: 12 }) {
          if (this.isAdmin()) {
            ListItemGroup({ header: this.groupHeader('新增用户') }) {
              ListItem() {
                Column({ space: 10 }) {
                  TextInput({ text: this.newUserEmail, placeholder: '邮箱' }).height(48).width('100%')
                    .onChange((value: string) => this.newUserEmail = value)
                  TextInput({ text: this.newUserPassword, placeholder: '初始密码' }).type(InputType.Password)
                    .height(48).width('100%').onChange((value: string) => this.newUserPassword = value)
                  Row() {
                    Text('管理员权限').fontSize(15).layoutWeight(1)
                    Toggle({ type: ToggleType.Switch, isOn: this.newUserAdmin })
                      .onChange((value: boolean) => this.newUserAdmin = value)
                  }.width('100%')
                  Button('创建用户').height(44).width('100%').onClick(() => this.addUser())
                }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
              }
            }
            ListItemGroup({ header: this.groupHeader('书库任务') }) {
              ListItem() {
                Button('新增书库').height(44).width('100%').onClick(() => router.pushUrl({ url: 'pages/LibraryEdit' }))
              }
              ForEach(this.state.libraries, (library: KomgaLibrary) => {
                ListItem() {
                  Column({ space: 10 }) {
                    Text(library.name).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
                    Row({ space: 8 }) {
                      Button('扫描').height(40).onClick(() => this.libraryAction(library.id, 'scan'))
                      Button('分析').height(40).onClick(() => this.libraryAction(library.id, 'analyze'))
                      Button('编辑').height(40).onClick(() => router.pushUrl({
                        url: 'pages/LibraryEdit', params: { libraryId: library.id }
                      }))
                      Button('清空回收站').height(40).fontColor($r('app.color.error'))
                        .onClick(() => this.confirmDanger('清空回收站', `将永久清空“${library.name}”的回收站。`,
                          () => adminRepository.emptyLibraryTrash(library.id)))
                    }.width('100%')
                  }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
                }
              }, (library: KomgaLibrary) => library.id)
            }
            ListItemGroup({ header: this.groupHeader(`用户（${this.users.length}）`) }) {
              ForEach(this.users, (user: KomgaUser) => {
                ListItem() {
                  Row() {
                    Text(user.email).fontSize(15).layoutWeight(1)
                    Text(user.roles.join(' · ')).fontSize(12).fontColor($r('app.color.on_surface_variant'))
                    Button('编辑').height(40).backgroundColor(Color.Transparent).onClick(() => router.pushUrl({
                      url: 'pages/UserEdit', params: { userId: user.id }
                    }))
                    if (user.id !== this.state.user?.id) {
                      Button('删除').height(40).fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
                        .onClick(() => this.confirmDanger('删除用户', `确认删除 ${user.email}？`,
                          () => adminRepository.deleteUser(user.id)))
                    }
                  }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
                }
              }, (user: KomgaUser) => user.id)
            }
            ListItemGroup({ header: this.groupHeader('任务队列') }) {
              ListItem() {
                Row() {
                  Text('清空等待中的服务端任务').fontSize(15).layoutWeight(1)
                  Button('清空').height(40).fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
                    .onClick(() => this.confirmDanger('清空任务队列', '仅清空等待执行的任务，正在运行的任务不会中断。',
                      async () => { const count = await adminRepository.emptyTaskQueue(); this.message = `已清空 ${count} 个任务`; }))
                }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
              }
            }
          }
          ListItemGroup({ header: this.groupHeader(`登录活动（${this.activities.length}）`) }) {
            ForEach(this.activities, (item: KomgaAuthenticationActivity) => {
              ListItem() {
                Column({ space: 3 }) {
                  Text(`${item.email} · ${item.ip}`).fontSize(14).width('100%')
                  Text(`${item.dateTime} · ${item.success ? '成功' : '失败'}`)
                    .fontSize(12).fontColor(item.success ? $r('app.color.on_surface_variant') : $r('app.color.error')).width('100%')
                }.padding(14).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
              }
            }, (item: KomgaAuthenticationActivity) => `${item.email}:${item.dateTime}:${item.ip}`)
          }
        }.layoutWeight(1).width('100%')
      }
      if (this.message.length > 0) Text(this.message).fontSize(13).padding(12).width('100%')
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
  }

  @Builder
  private groupHeader(title: string) {
    Text(title).fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface_variant'))
      .padding({ top: 12, bottom: 8 }).width('100%')
  }
}
