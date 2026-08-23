import { router } from '@kit.ArkUI';
import { AppPreferences } from '../persistence/AppPreferences';
import { defaultHomeGroups, HomeGroup, parseHomeGroups } from '../model/HomeGroupModels';

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
  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('首页分组').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button('重置').height(40).onClick(() => this.save(defaultHomeGroups()))
      }.height(64).width('100%')
      List({ space: 8 }) {
        ForEach(this.groups, (item: HomeGroup, index: number) => {
          ListItem() {
            Row({ space: 8 }) {
              Column({ space: 2 }) {
                Text(item.label).fontSize(16).fontWeight(FontWeight.Medium).width('100%')
                Text('按顶部顺序显示').fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Button('↑').width(40).height(40).enabled(index > 0).onClick(() => this.move(index, -1))
              Button('↓').width(40).height(40).enabled(index < this.groups.length - 1).onClick(() => this.move(index, 1))
              Toggle({ type: ToggleType.Switch, isOn: item.enabled }).onChange((value: boolean) => this.toggle(index, value))
            }.padding(14).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
            .border({ width: 1, color: $r('app.color.outline') })
          }
        }, (item: HomeGroup) => item.id)
      }.layoutWeight(1).width('100%')
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
  }
}
