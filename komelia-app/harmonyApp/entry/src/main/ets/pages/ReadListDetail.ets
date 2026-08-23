import { router } from '@kit.ArkUI';
import { BookCard } from '../components/CoverCards';
import { EmptyState, LoadingState } from '../components/CommonUi';
import { KomgaBook } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface ReadListParams { readListId: string; title: string; }

@Entry
@Component
struct ReadListDetail {
  @State books: KomgaBook[] = [];
  @State loading: boolean = true;
  @State columns: number = 3;
  private readListId: string = '';
  private title: string = '';

  aboutToAppear(): void {
    const params = router.getParams() as ReadListParams;
    this.readListId = params.readListId; this.title = params.title; this.load();
  }
  private async load(): Promise<void> {
    try { this.books = (await komgaClient.getReadListBooks(this.readListId)).content; }
    finally { this.loading = false; }
  }
  private template(): string {
    if (this.columns === 5) return '1fr 1fr 1fr 1fr 1fr';
    if (this.columns === 4) return '1fr 1fr 1fr 1fr';
    return '1fr 1fr 1fr';
  }
  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).backgroundColor(Color.Transparent).fontSize(30).onClick(() => router.back())
        Text(this.title).fontSize(20).fontWeight(FontWeight.Bold).maxLines(1).layoutWeight(1)
      }.height(56).width('100%').padding({ left: 8, right: 12 })
      if (this.loading) { LoadingState() }
      else if (this.books.length === 0) { EmptyState('阅读列表中没有书籍') }
      else {
        Grid() {
          ForEach(this.books, (item: KomgaBook) => {
            GridItem() { BookCard({ book: item, supporting: true, onOpen: (id: string) =>
              router.pushUrl({ url: 'pages/BookDetail', params: { bookId: id } }) }) }
          }, (item: KomgaBook) => item.id)
        }.columnsTemplate(this.template()).columnsGap(12).rowsGap(12).layoutWeight(1).width('100%')
      }
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
    .onAreaChange((_, area) => this.columns = Number(area.width) >= 900 ? 5 : Number(area.width) >= 600 ? 4 : 3)
  }
}
