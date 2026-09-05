import { router } from '@kit.ArkUI';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { KomeliaColors } from '../core/design/KomeliaDesign';
import { localizedString } from '../core/i18n/LocalizedResources';
import { LocalLibraryRecord } from '../model/LocalContentModels';
import { localLibraryRepository } from '../data/repository/LocalLibraryRepository';
import { localLibraryService } from '../domain/service/LocalLibraryService';

@Entry
@Component
struct LocalLibraries {
  @State libraries: LocalLibraryRecord[] = [];
  @State busy: boolean = false;
  @State message: string = '';
  aboutToAppear(): void { this.load().catch(() => {}); }
  private async load(): Promise<void> { this.libraries = await localLibraryRepository.libraries(); }

  private async perform(action: () => Promise<void>): Promise<void> {
    if (this.busy) return;
    this.busy = true; this.message = '';
    try { await action(); await this.load(); }
    catch (_) { this.message = localizedString($r('app.string.local_operation_failed')); }
    finally { this.busy = false; }
  }

  private remove(library: LocalLibraryRecord): void {
    this.getUIContext().showAlertDialog({ title: $r('app.string.local_remove_library'),
      message: $r('app.string.local_remove_warning'), buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.local_remove_library'), action: () => this.perform(() => localLibraryService.remove(library)) }
      ] });
  }

  @Builder
  private content() {
    Column({ space: 12 }) {
      Row({ space: 8 }) {
        Button() { Image($r('app.media.navigation_back')).width(24).height(24) }.width(48).height(48)
          .backgroundColor(Color.Transparent).accessibilityText($r('app.string.back')).onClick(() => router.back())
        Text($r('app.string.local_libraries')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
      }.height(64).width('100%')
      Text($r('app.string.local_sources_notice')).fontSize(13).width('100%')
      if (this.message.length > 0) Text(this.message).fontColor($r('app.color.error')).width('100%')
      List({ space: 12 }) {
        ForEach(this.libraries, (library: LocalLibraryRecord) => {
          ListItem() {
            Column({ space: 12 }) {
              Text(library.name).fontSize(18).fontWeight(FontWeight.Medium).width('100%')
              Row() {
                Text($r('app.string.local_scan_startup')).layoutWeight(1)
                Toggle({ type: ToggleType.Switch, isOn: library.scanOnStartup }).enabled(!this.busy)
                  .onChange(value => this.perform(async (): Promise<void> => {
                    library.scanOnStartup = value; await localLibraryService.update(library);
                  }))
              }.height(48).width('100%')
              Row() {
                Text($r('app.string.local_scan_hourly')).layoutWeight(1)
                Toggle({ type: ToggleType.Switch, isOn: library.scanIntervalHours > 0 }).enabled(!this.busy)
                  .onChange(value => this.perform(async (): Promise<void> => {
                    library.scanIntervalHours = value ? 1 : 0; await localLibraryService.update(library);
                  }))
              }.height(48).width('100%')
              ForEach(library.excludedPaths, (path: string) => {
                Row({ space: 8 }) {
                  Text(path).maxLines(2).layoutWeight(1)
                  Button($r('app.string.local_restore_excluded')).height(48).enabled(!this.busy)
                    .onClick(() => this.perform(() => localLibraryService.restoreExcluded(library.id, path)))
                }.width('100%')
              }, (path: string) => path)
              Button($r('app.string.local_remove_library')).height(48).enabled(!this.busy).width('100%')
                .onClick(() => this.remove(library))
            }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
          }
        }, (library: LocalLibraryRecord) => library.id)
      }.layoutWeight(1).width('100%')
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%').align(Alignment.Top)
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) { AppNavigationFrame({ content: () => { this.content(); } }) }
      .width('100%').height('100%')
  }
}
