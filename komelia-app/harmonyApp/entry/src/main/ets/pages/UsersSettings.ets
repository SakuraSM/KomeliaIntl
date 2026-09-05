import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaAuthenticationActivity, KomgaUser } from '../model/KomgaModels';
import { formatDateTime, userRoleResource } from '../model/UserPresentation';
import { komgaClient } from '../network/KomgaClient';
import { AppState } from '../state/AppState';

@Entry
@Component
struct UsersSettings {
  @State users: KomgaUser[] = [];
  @State activities: KomgaAuthenticationActivity[] = [];
  @State loading: boolean = true;
  @State errorMessage: string = '';
  @State message: string = '';
  @State expandedUserId: string = '';
  @State addVisible: boolean = false;
  @State newUserEmail: string = '';
  @State newUserPassword: string = '';
  @State newUserAdmin: boolean = false;
  @State saving: boolean = false;
  private currentUserId: string = '';

  private text(resource: Resource, ...args: Array<string | number>): string {
    return localizedString(resource, ...args);
  }

  aboutToAppear(): void {
    this.currentUserId = AppState.getInstance().user?.id || '';
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      const values = await Promise.all([
        komgaClient.getUsers(), komgaClient.getAuthenticationActivity(false, 0, 100)
      ]);
      this.users = values[0];
      this.activities = values[1].content;
    } catch (error) {
      this.errorMessage = (error as Error).message || this.text($r('app.string.user_data_load_failed'));
    } finally {
      this.loading = false;
    }
  }

  private latestActivity(userId: string): string {
    const value = this.activities.find((item: KomgaAuthenticationActivity) => item.userId === userId);
    return value === undefined ? this.text($r('app.string.no_sign_in_record')) :
      this.text($r('app.string.latest_sign_in'), formatDateTime(value.dateTime));
  }

  private async addUser(): Promise<void> {
    if (this.newUserEmail.trim().length === 0 || this.newUserPassword.length < 8) {
      this.message = this.text($r('app.string.user_email_password_validation'));
      return;
    }
    this.saving = true;
    this.message = '';
    try {
      await adminRepository.addUser(this.newUserEmail.trim(), this.newUserPassword,
        this.newUserAdmin ? ['USER', 'ADMIN'] : ['USER']);
      this.newUserEmail = '';
      this.newUserPassword = '';
      this.newUserAdmin = false;
      this.addVisible = false;
      this.message = this.text($r('app.string.user_created'));
      await this.load();
    } catch (error) {
      this.message = (error as Error).message || this.text($r('app.string.user_create_failed'));
    } finally {
      this.saving = false;
    }
  }

  private confirmDelete(user: KomgaUser): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.delete_user'),
      message: this.text($r('app.string.delete_user_confirmation'), user.email),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          adminRepository.deleteUser(user.id).then(() => {
            this.message = this.text($r('app.string.user_deleted'));
            this.load();
          }).catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  @Builder
  private userCard(user: KomgaUser) {
    Column({ space: 10 }) {
      Row({ space: 12 }) {
        Image($r('app.media.settings_account')).width(22).height(22)
          .fillColor(user.roles.includes('ADMIN') ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
        Column({ space: 3 }) {
          Text(user.email).fontSize(15).fontWeight(FontWeight.Medium).width('100%').maxLines(2)
          Text(this.latestActivity(user.id)).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%').maxLines(1)
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Image($r('app.media.settings_chevron')).width(22).height(22)
          .fillColor($r('app.color.on_surface_variant'))
          .rotate({ angle: this.expandedUserId === user.id ? -90 : 90 })
      }
      .width('100%').constraintSize({ minHeight: 52 })
      .onClick(() => this.expandedUserId = this.expandedUserId === user.id ? '' : user.id)
      .accessibilityText(this.text($r('app.string.expand_user_actions'), user.email))

      if (this.expandedUserId === user.id) {
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          ForEach(user.roles, (role: string) => {
            Text(userRoleResource(role)).fontSize(12).fontColor($r('app.color.primary'))
              .padding({ left: 10, right: 10, top: 6, bottom: 6 })
              .margin({ right: 6, bottom: 6 }).backgroundColor($r('app.color.primary_container'))
              .borderRadius(14)
          }, (role: string) => role)
        }.width('100%')
        Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
          Button(user.id === this.currentUserId ? $r('app.string.change_password') :
            $r('app.string.edit_and_password'))
            .height(40).margin({ right: 8, bottom: 4 })
            .onClick(() => router.pushUrl({ url: 'pages/UserEdit', params: { userId: user.id } }))
          if (user.id !== this.currentUserId) {
            Button($r('app.string.delete')).height(40).margin({ right: 8, bottom: 4 })
              .fontColor($r('app.color.error')).backgroundColor(KomeliaColors.surfaceContainer())
              .onClick(() => this.confirmDelete(user))
          }
        }.width('100%')
      }
    }
    .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
  }

  @Builder
  private addUserCard() {
    Column({ space: 10 }) {
      TextInput({ text: this.newUserEmail, placeholder: $r('app.string.email') }).height(48).width('100%')
        .onChange((value: string) => this.newUserEmail = value)
      TextInput({ text: this.newUserPassword, placeholder: $r('app.string.initial_password_placeholder') })
        .type(InputType.Password).height(48).width('100%')
        .onChange((value: string) => this.newUserPassword = value)
      Row() {
        Column({ space: 2 }) {
          Text($r('app.string.administrator_role')).fontSize(15).width('100%')
          Text($r('app.string.administrator_role_description')).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Toggle({ type: ToggleType.Switch, isOn: this.newUserAdmin })
          .onChange((value: boolean) => this.newUserAdmin = value)
      }.width('100%').constraintSize({ minHeight: 48 })
      Row({ space: 8 }) {
        Button($r('app.string.cancel')).height(44).layoutWeight(1)
          .backgroundColor(KomeliaColors.surfaceContainer())
          .fontColor($r('app.color.on_surface')).onClick(() => this.addVisible = false)
        Button(this.saving ? $r('app.string.creating') : $r('app.string.create_user'))
          .height(44).layoutWeight(1)
          .enabled(!this.saving).onClick(() => this.addUser())
      }.width('100%')
    }
    .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Text(this.text($r('app.string.user_management_count'), this.users.length))
          .fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.addVisible ? $r('app.string.collapse') : $r('app.string.add'))
          .height(40).onClick(() => this.addVisible = !this.addVisible)
      }.height(64).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else {
        List({ space: 10 }) {
          if (this.addVisible) ListItem() { this.addUserCard() }
          ForEach(this.users, (user: KomgaUser) => {
            ListItem() { this.userCard(user) }
          }, (user: KomgaUser) => user.id)
        }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })
      }
      if (this.message.length > 0) {
        Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant'))
          .padding({ top: 8, bottom: 12 }).width('100%')
      }
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
