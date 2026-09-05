import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { localizedString } from '../core/i18n/LocalizedResources';
import { adminRepository } from '../data/repository/DefaultRepositories';
import { KomgaLibrary } from '../model/KomgaModels';
import { AppState } from '../state/AppState';

@Entry
@Component
struct Management {
  @State message: string = '';
  @State running: boolean = false;
  private state: AppState = AppState.getInstance();

  private text(resource: Resource, ...args: Array<string | number>): string {
    return localizedString(resource, ...args);
  }

  private async executeLibraryAction(libraryId: string, action: string, deep: boolean = false): Promise<void> {
    if (action === 'scan') await adminRepository.scanLibrary(libraryId, deep);
    else if (action === 'analyze') await adminRepository.analyzeLibrary(libraryId);
    else await adminRepository.emptyLibraryTrash(libraryId);
  }

  private async libraryAction(libraryId: string, action: string, deep: boolean = false): Promise<void> {
    this.running = true;
    this.message = this.text($r('app.string.task_submitting'));
    try {
      await this.executeLibraryAction(libraryId, action, deep);
      this.message = this.text($r('app.string.task_submitted'));
    } catch (error) {
      this.message = (error as Error).message || this.text($r('app.string.task_submit_failed'));
    } finally {
      this.running = false;
    }
  }

  private async allLibrariesAction(action: string, deep: boolean = false): Promise<void> {
    this.running = true;
    this.message = this.text($r('app.string.all_library_tasks_submitting'));
    try {
      for (let index = 0; index < this.state.libraries.length; index += 1) {
        await this.executeLibraryAction(this.state.libraries[index].id, action, deep);
      }
      this.message = this.text($r('app.string.all_library_tasks_submitted'), this.state.libraries.length);
    } catch (error) {
      this.message = (error as Error).message || this.text($r('app.string.batch_task_submit_failed'));
    } finally {
      this.running = false;
    }
  }

  private confirmDanger(title: ResourceStr, message: ResourceStr, action: () => Promise<void>): void {
    this.getUIContext().showAlertDialog({
      title: title,
      message: message,
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.confirm'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          action().catch((error: Error) => this.message = error.message);
        } }
      ]
    });
  }

  private async clearTaskQueue(): Promise<void> {
    this.running = true;
    try {
      const count = await adminRepository.emptyTaskQueue();
      this.message = count === 0 ? this.text($r('app.string.no_pending_tasks')) :
        this.text($r('app.string.pending_tasks_cleared'), count);
    } catch (error) {
      this.message = (error as Error).message || this.text($r('app.string.task_queue_clear_failed'));
    } finally {
      this.running = false;
    }
  }

  @Builder
  private sectionTitle(title: ResourceStr, description: ResourceStr) {
    Column({ space: 3 }) {
      Text(title).fontSize(17).fontWeight(FontWeight.Bold).width('100%')
      Text(description).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
    }.alignItems(HorizontalAlign.Start).width('100%')
  }

  @Builder
  private actionButton(title: ResourceStr, onClick: () => void, danger: boolean = false) {
    Button(title).height(40).margin({ right: 8, bottom: 8 })
      .enabled(!this.running)
      .fontColor(danger ? $r('app.color.error') : $r('app.color.on_surface'))
      .backgroundColor(KomeliaColors.surfaceContainer())
      .onClick(onClick)
  }

  @Builder
  private libraryCard(library: KomgaLibrary) {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        Column({ space: 3 }) {
          Text(library.name).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
          Text(library.root).fontSize(12).fontColor($r('app.color.on_surface_variant'))
            .width('100%').maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        if (library.unavailable) {
          Text($r('app.string.unavailable')).fontSize(12).fontColor($r('app.color.error'))
            .padding({ left: 9, right: 9, top: 5, bottom: 5 })
            .backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(12)
        }
      }.width('100%')
      Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
        this.actionButton($r('app.string.scan'), () => this.libraryAction(library.id, 'scan'))
        this.actionButton($r('app.string.deep_scan'), () => this.libraryAction(library.id, 'scan', true))
        this.actionButton($r('app.string.analyze'), () => this.libraryAction(library.id, 'analyze'))
        this.actionButton($r('app.string.edit'), () => router.pushUrl({
          url: 'pages/LibraryEdit', params: { libraryId: library.id }
        }))
        this.actionButton($r('app.string.empty_trash'), () => this.confirmDanger($r('app.string.empty_trash'),
          this.text($r('app.string.empty_library_trash_confirmation'), library.name),
          () => this.libraryAction(library.id, 'empty-trash')), true)
      }.width('100%')
    }
    .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48)
          .fontColor($r('app.color.on_surface')).backgroundColor(Color.Transparent)
          .onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.media_and_tasks')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.add_library')).height(40).enabled(!this.running)
          .onClick(() => router.pushUrl({ url: 'pages/LibraryEdit' }))
      }.height(64).width('100%')

      List({ space: 12 }) {
        ListItem() {
          Column({ space: 12 }) {
            this.sectionTitle($r('app.string.all_libraries'), $r('app.string.all_libraries_task_description'))
            Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
              this.actionButton($r('app.string.scan_all'), () => this.allLibrariesAction('scan'))
              this.actionButton($r('app.string.deep_scan_all'), () => this.allLibrariesAction('scan', true))
              this.actionButton($r('app.string.analyze_all'), () => this.allLibrariesAction('analyze'))
              this.actionButton($r('app.string.empty_all_trash'), () => this.confirmDanger(
                $r('app.string.empty_all_trash'), $r('app.string.empty_all_trash_confirmation'),
                () => this.allLibrariesAction('empty-trash')), true)
            }.width('100%')
          }
          .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
        }

        ForEach(this.state.libraries, (library: KomgaLibrary) => {
          ListItem() { this.libraryCard(library) }
        }, (library: KomgaLibrary) => library.id)

        ListItem() {
          Column({ space: 12 }) {
            this.sectionTitle($r('app.string.task_queue'), $r('app.string.task_queue_description'))
            Button($r('app.string.clear_pending_tasks')).height(44).width('100%').enabled(!this.running)
              .fontColor($r('app.color.error')).backgroundColor(KomeliaColors.surfaceContainer())
              .onClick(() => this.confirmDanger($r('app.string.clear_task_queue'),
                $r('app.string.clear_task_queue_confirmation'), () => this.clearTaskQueue()))
          }
          .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
          .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
        }
      }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })

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
