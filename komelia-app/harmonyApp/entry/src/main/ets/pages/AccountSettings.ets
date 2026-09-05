import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaUser } from '../model/KomgaModels';
import { userRoleResource } from '../model/UserPresentation';
import { AppState } from '../state/AppState';

@Entry
@Component
struct AccountSettings {
  @State password: string = '';
  @State confirmation: string = '';
  @State message: string = '';
  @State savedSuccessfully: boolean = false;
  @State saving: boolean = false;
  private state: AppState = AppState.getInstance();

  private text(resource: Resource): string {
    return localizedString(resource);
  }

  private async savePassword(): Promise<void> {
    const user = this.state.user;
    this.savedSuccessfully = false;
    if (user === undefined) {
      this.message = this.text($r('app.string.current_account_unavailable'));
      return;
    }
    if (this.password.length < 8) {
      this.message = this.text($r('app.string.password_minimum'));
      return;
    }
    if (this.password !== this.confirmation) {
      this.message = this.text($r('app.string.password_mismatch'));
      return;
    }
    this.saving = true;
    this.message = '';
    try {
      await adminRepository.updateUserPassword(user.id, this.password);
      this.password = '';
      this.confirmation = '';
      this.savedSuccessfully = true;
      this.message = this.text($r('app.string.password_updated'));
    } catch (error) {
      this.message = (error as Error).message || this.text($r('app.string.password_update_failed'));
    } finally {
      this.saving = false;
    }
  }

  @Builder
  private roleChip(role: string) {
    Text(userRoleResource(role))
      .fontSize(13)
      .fontColor($r('app.color.primary'))
      .padding({ left: 12, right: 12, top: 7, bottom: 7 })
      .margin({ right: 8, bottom: 8 })
      .backgroundColor($r('app.color.primary_container'))
      .borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.my_account')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')

      Scroll() {
        Column({ space: 20 }) {
          Column({ space: 14 }) {
            Text($r('app.string.account_information')).fontSize(17).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            Row() {
              Text($r('app.string.email')).fontSize(14).fontColor($r('app.color.on_surface_variant')).width(92)
              Text(this.state.user?.email || '-').fontSize(16).fontWeight(FontWeight.Medium)
                .layoutWeight(1).maxLines(2)
            }.width('100%').alignItems(VerticalAlign.Top)
            Text($r('app.string.roles')).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
            Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
              ForEach(this.state.user?.roles || [], (role: string) => this.roleChip(role),
                (role: string) => role)
            }.width('100%')
          }
          .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)

          Column({ space: 12 }) {
            Text($r('app.string.change_password')).fontSize(17).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.on_surface_variant')).width('100%')
            Text($r('app.string.change_password_description'))
              .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
            TextInput({ text: this.password, placeholder: $r('app.string.new_password_placeholder') })
              .type(InputType.Password).height(52).width('100%')
              .onChange((value: string) => this.password = value)
            TextInput({ text: this.confirmation, placeholder: $r('app.string.repeat_new_password') })
              .type(InputType.Password).height(52).width('100%')
              .onChange((value: string) => this.confirmation = value)
            Button(this.saving ? $r('app.string.updating') : $r('app.string.update_password'))
              .height(48).width('100%').enabled(!this.saving)
              .onClick(() => this.savePassword())
            if (this.message.length > 0) {
              Text(this.message).fontSize(13)
                .fontColor(this.savedSuccessfully ? $r('app.color.primary') : $r('app.color.error'))
                .width('100%')
            }
          }
          .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
        }
        .width('100%').constraintSize({ maxWidth: 840 }).padding({ bottom: 32 })
      }.layoutWeight(1).width('100%').align(Alignment.Top)
    }
    .padding({ left: 12, right: 12 }).width('100%').height('100%').align(Alignment.Top)
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
