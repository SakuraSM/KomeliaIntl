import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { AppPreferences } from '../persistence/AppPreferences';
import { defaultHomeGroups, HomeGroup, homeGroupLabel, parseHomeGroups } from '../model/HomeGroupModels';

@Entry
@Component
struct HomeGroupSettings {
  @State groups: HomeGroup[] = defaultHomeGroups();
  aboutToAppear(): void { this.restore(); }
  private async restore(): Promise<void> {
    const raw = await AppPreferences.getString('homeGroups');
    this.groups = parseHomeGroups(raw);
  }
  private async save(values: HomeGroup[]): Promise<void> {
    this.groups = values; await AppPreferences.putString('homeGroups', JSON.stringify(values));
  }
  private move(index: number, delta: number): void {
    const target = index + delta;
    if (target < 0 || target >= this.groups.length) return;
    const values = this.groups.slice(); const item = values[index];
    values[index] = values[target]; values[target] = item; this.save(values);
  }
  private toggle(index: number, enabled: boolean): void {
    const values = this.groups.slice();
    values[index] = { id: values[index].id, label: values[index].label, enabled: enabled }; this.save(values);
  }
  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Text($r('app.string.home_groups')).fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button($r('app.string.reset')).height(40).onClick(() => this.save(defaultHomeGroups()))
      }.height(64).width('100%')
      List({ space: 8 }) {
        ForEach(this.groups, (item: HomeGroup, index: number) => {
          ListItem() {
            Row({ space: 8 }) {
              Column({ space: 2 }) {
                Text(homeGroupLabel(item.id)).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
                Text($r('app.string.display_in_top_order')).fontSize(12)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Button('↑').width(40).height(40).enabled(index > 0).onClick(() => this.move(index, -1))
                .accessibilityText($r('app.string.move_up'))
              Button('↓').width(40).height(40).enabled(index < this.groups.length - 1)
                .onClick(() => this.move(index, 1)).accessibilityText($r('app.string.move_down'))
              Toggle({ type: ToggleType.Switch, isOn: item.enabled }).onChange((value: boolean) => this.toggle(index, value))
            }.padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh()).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          }
        }, (item: HomeGroup) => item.id)
      }.layoutWeight(1).width('100%')
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
      .align(Alignment.Top).alignItems(HorizontalAlign.Start)
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
