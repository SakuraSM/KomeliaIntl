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
            ListItemGroup({ header: this.groupHeader('书库任务') }) {
              ForEach(this.state.libraries, (library: KomgaLibrary) => {
                ListItem() {
                  Column({ space: 10 }) {
                    Text(library.name).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
                    Row({ space: 8 }) {
                      Button('扫描').height(40).onClick(() => this.libraryAction(library.id, 'scan'))
                      Button('分析').height(40).onClick(() => this.libraryAction(library.id, 'analyze'))
                      Button('清空回收站').height(40).fontColor($r('app.color.error'))
                        .onClick(() => this.libraryAction(library.id, 'trash'))
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
                  }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(14)
                }
              }, (user: KomgaUser) => user.id)
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
