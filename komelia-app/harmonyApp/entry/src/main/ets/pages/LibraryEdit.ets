import { router } from '@kit.ArkUI';
import { adminRepository, catalogRepository } from '../data/repository/DefaultRepositories';
import { KomgaLibrary } from '../model/KomgaModels';
import { AppState } from '../state/AppState';

interface LibraryEditParams { libraryId?: string; }

@Entry
@Component
struct LibraryEdit {
  @State name: string = '';
  @State root: string = '';
  @State message: string = '';
  @State saving: boolean = false;
  private libraryId: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as LibraryEditParams;
    this.libraryId = params.libraryId || '';
    if (this.libraryId.length > 0) {
      const value = AppState.getInstance().libraries.find((item: KomgaLibrary) => item.id === this.libraryId);
      this.name = value?.name || '';
      this.root = value?.root || '';
    }
  }

  private async save(): Promise<void> {
    if (this.name.trim().length === 0 || this.root.trim().length === 0) {
      this.message = '名称和根目录不能为空'; return;
    }
    this.saving = true;
    try {
      if (this.libraryId.length === 0) await adminRepository.addLibrary(this.name.trim(), this.root.trim());
      else await adminRepository.updateLibrary(this.libraryId, this.name.trim(), this.root.trim());
      AppState.getInstance().libraries = await catalogRepository.libraries();
      this.message = '书库已保存';
    } catch (error) { this.message = (error as Error).message; }
    finally { this.saving = false; }
  }

  private confirmDelete(): void {
    this.getUIContext().showAlertDialog({
      title: '删除书库',
      message: '此操作会从 Komga 删除书库配置，请确认服务端文件影响后再继续。',
      buttons: [
        { value: '取消' },
        { value: '删除', style: DialogButtonStyle.HIGHLIGHT, fontColor: $r('app.color.error'), action: () => {
          adminRepository.deleteLibrary(this.libraryId).then(async () => {
            AppState.getInstance().libraries = await catalogRepository.libraries();
            router.back();
          }).catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text(this.libraryId.length === 0 ? '新增书库' : '编辑书库').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? '保存中…' : '保存').height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Column({ space: 14 }) {
        Text('书库名称').fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
        TextInput({ text: this.name }).height(48).width('100%').onChange((value: string) => this.name = value)
        Text('服务端根目录').fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
        TextInput({ text: this.root, placeholder: '/media/books' }).height(48).width('100%')
          .onChange((value: string) => this.root = value)
        Text('新书库默认扫描 CBX、PDF 和 EPUB；编辑时不会覆盖其他扫描选项。')
          .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        if (this.libraryId.length > 0) {
          Button('删除书库').height(48).width('100%').fontColor($r('app.color.error'))
            .backgroundColor($r('app.color.surface_high')).onClick(() => this.confirmDelete())
        }
        if (this.message.length > 0) {
          Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }
      }.padding(16).width('100%').constraintSize({ maxWidth: 720 })
      Blank()
    }.padding({ left: 12, right: 12, bottom: 24 }).width('100%').height('100%')
      .backgroundColor($r('app.color.surface'))
  }
}
