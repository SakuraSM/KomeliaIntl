@Builder
export function PageTitle(title: string, subtitle: string = '') {
  Column() {
    Text(title)
      .fontSize(30)
      .fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface'))
      .width('100%')
    if (subtitle.length > 0) {
      Text(subtitle)
        .fontSize(14)
        .fontColor($r('app.color.on_surface_variant'))
        .margin({ top: 4 })
        .width('100%')
    }
  }
  .alignItems(HorizontalAlign.Start)
  .width('100%')
}

@Builder
export function SectionTitle(title: string, count: number = -1) {
  Row() {
    Text(title)
      .fontSize(22)
      .fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.on_surface_variant'))
    if (count >= 0) {
      Text(`${count}`)
        .fontSize(13)
        .fontColor($r('app.color.on_surface_variant'))
        .backgroundColor($r('app.color.surface_container'))
        .padding({ left: 8, right: 8, top: 3, bottom: 3 })
        .borderRadius(10)
        .margin({ left: 8 })
    }
  }
  .width('100%')
  .alignItems(VerticalAlign.Center)
}

@Builder
export function EmptyState(message: string) {
  Column({ space: 12 }) {
    Text('◇')
      .fontSize(42)
      .fontColor($r('app.color.on_surface_variant'))
    Text(message)
      .fontSize(15)
      .fontColor($r('app.color.on_surface_variant'))
  }
  .width('100%')
  .height(220)
  .justifyContent(FlexAlign.Center)
}

@Builder
export function LoadingState() {
  Column({ space: 12 }) {
    LoadingProgress()
      .width(36)
      .height(36)
      .color($r('app.color.primary'))
    Text($r('app.string.loading'))
      .fontSize(14)
      .fontColor($r('app.color.on_surface_variant'))
  }
  .width('100%')
  .height(220)
  .justifyContent(FlexAlign.Center)
}
