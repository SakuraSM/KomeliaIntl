import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { localizedString } from '../core/i18n/LocalizedResources';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaUser } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface UserEditParams { userId: string; }

@Entry
@Component
struct UserEdit {
  @State user: KomgaUser | undefined = undefined;
  @State administrator: boolean = false;
  @State password: string = '';
  @State message: string = '';
  private userId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as UserEditParams | undefined;
    this.userId = requiredRouteId(params?.userId);
    if (this.userId.length > 0) this.load();
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
      this.message = localizedString($r('app.string.permissions_saved'));
    } catch (error) { this.message = (error as Error).message; }
  }

  private async savePassword(): Promise<void> {
    if (this.password.length < 8) { this.message = localizedString($r('app.string.password_minimum')); return; }
    try {
      await adminRepository.updateUserPassword(this.userId, this.password);
      this.password = '';
      this.message = localizedString($r('app.string.password_updated'));
    } catch (error) { this.message = (error as Error).message; }
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) {
          Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface'))
        }.width(48).height(48).backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text($r('app.string.edit_user')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      Column({ space: 16 }) {
        Text(this.user?.email || localizedString($r('app.string.loading')))
          .fontSize(18).fontWeight(FontWeight.Medium).width('100%')
        Row() {
          Column({ space: 3 }) {
            Text($r('app.string.administrator_role')).fontSize(16).width('100%')
            Text($r('app.string.administrator_role_description')).fontSize(13)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
          }.layoutWeight(1)
          Toggle({ type: ToggleType.Switch, isOn: this.administrator })
            .onChange((value: boolean) => this.administrator = value)
        }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
        Button($r('app.string.save_permissions')).height(44).width('100%').onClick(() => this.saveRoles())
        Text($r('app.string.reset_password')).fontSize(17).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_surface_variant')).width('100%')
        TextInput({ text: this.password, placeholder: $r('app.string.password_minimum') }).type(InputType.Password)
          .height(48).width('100%').onChange((value: string) => this.password = value)
        Button($r('app.string.update_password')).height(44).width('100%').onClick(() => this.savePassword())
        if (this.message.length > 0) {
          Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }
      }.padding(16).width('100%').constraintSize({ maxWidth: 720 })
      Blank()
    }.padding({ left: 12, right: 12, bottom: 24 }).width('100%').height('100%')
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
