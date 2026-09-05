import { KomeliaColors } from '../core/design/KomeliaDesign';

/** Android-compatible split read control with an explicit incognito action. */
@Builder
export function BookReadControl(enabled: boolean, idPrefix: string,
  onRead: () => void, onIncognitoRead: () => void) {
  Row() {
    Button() {
      Row({ space: 10 }) {
        Image($r('app.media.nav_library')).width(24).height(24)
          .fillColor($r('app.color.on_tertiary'))
        Text($r('app.string.read')).fontSize(14).fontColor($r('app.color.on_tertiary'))
      }.height('100%').justifyContent(FlexAlign.Center)
    }
    .id(`${idPrefix}_read`)
    .height(40).width(80).padding(0)
    .borderRadius({ topLeft: 20, bottomLeft: 20, topRight: 0, bottomRight: 0 })
    .backgroundColor($r('app.color.tertiary_container'))
    .enabled(enabled).onClick(onRead)
    .responseRegion({ x: 0, y: -4, width: '100%', height: 48 })

    Divider().vertical(true).height(28).width(1).color(KomeliaColors.surfaceContainer())

    Button({ type: ButtonType.Normal }) {
      Image($r('app.media.library_expand_more')).width(24).height(24)
        .fillColor($r('app.color.on_tertiary'))
    }
    .id(`${idPrefix}_read_menu`)
    .height(40).width(39).padding(0)
    .borderRadius({ topLeft: 0, bottomLeft: 0, topRight: 20, bottomRight: 20 })
    .backgroundColor($r('app.color.tertiary_container'))
    .enabled(enabled)
    .accessibilityText($r('app.string.read_options'))
    .responseRegion({ x: 0, y: -4, width: '100%', height: 48 })
    .bindMenu([
      { value: $r('app.string.read_incognito'), action: onIncognitoRead }
    ])
  }
  .height(48)
  .alignItems(VerticalAlign.Center)
}
