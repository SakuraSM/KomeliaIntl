import { router } from '@kit.ArkUI';
import { catalogRepository } from '../data/repository/DefaultRepositories';
import { BookMetadataUpdateRequest, KomgaAuthor, KomgaBook } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface BookEditParams { bookId: string; }

@Entry
@Component
struct BookEdit {
  @State book?: KomgaBook;
  @State title: string = '';
  @State summary: string = '';
  @State number: string = '';
  @State releaseDate: string = '';
  @State authors: string = '';
  @State tags: string = '';
  @State isbn: string = '';
  @State message: string = '';
  @State saving: boolean = false;
  private bookId: string = '';

  aboutToAppear(): void {
    this.bookId = (router.getParams() as BookEditParams).bookId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      this.book = await catalogRepository.bookDetail(this.bookId);
      this.title = this.book.metadata.title;
      this.summary = this.book.metadata.summary;
      this.number = this.book.metadata.number;
      this.releaseDate = this.book.metadata.releaseDate || '';
      this.authors = this.book.metadata.authors.map((item: KomgaAuthor) => item.name).join('、');
      this.tags = this.book.metadata.tags.join('、');
      this.isbn = this.book.metadata.isbn;
    } catch (error) { this.message = (error as Error).message; }
  }

  private list(value: string): string[] {
    return value.split(/[、,，]/).map((item: string) => item.trim()).filter((item: string) => item.length > 0);
  }

  private async save(): Promise<void> {
    this.saving = true;
    try {
      const request: BookMetadataUpdateRequest = {
        title: this.title.trim(), summary: this.summary.trim(), number: this.number.trim(),
        numberSort: Number(this.number) || 0, releaseDate: this.releaseDate.trim(), isbn: this.isbn.trim(),
        tags: this.list(this.tags), authors: this.list(this.authors).map((name: string) => ({ name: name, role: 'writer' }))
      };
      await komgaClient.updateBookMetadata(this.bookId, request);
      this.message = '书籍信息已保存';
    } catch (error) { this.message = (error as Error).message; }
    finally { this.saving = false; }
  }

  private async action(kind: string): Promise<void> {
    try { await komgaClient.runBookAction(this.bookId, kind); this.message = '服务端任务已提交'; }
    catch (error) { this.message = (error as Error).message; }
  }

  @Builder
  private field(label: string, value: string, multiline: boolean,
    onChange: (value: string) => void) {
    Column({ space: 6 }) {
      Text(label).fontSize(14).fontColor($r('app.color.on_surface_variant')).width('100%')
      if (multiline) {
        TextArea({ text: value }).height(132).width('100%').onChange(onChange)
      } else {
        TextInput({ text: value }).height(48).width('100%').onChange(onChange)
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Text('编辑书籍').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? '保存中…' : '保存').height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 14 }) {
          this.field('标题', this.title, false, (value: string) => this.title = value)
          this.field('简介', this.summary, true, (value: string) => this.summary = value)
          this.field('册号', this.number, false, (value: string) => this.number = value)
          this.field('发布日期（YYYY-MM-DD）', this.releaseDate, false, (value: string) => this.releaseDate = value)
          this.field('作者（顿号或逗号分隔）', this.authors, false, (value: string) => this.authors = value)
          this.field('标签（顿号或逗号分隔）', this.tags, false, (value: string) => this.tags = value)
          this.field('ISBN', this.isbn, false, (value: string) => this.isbn = value)
          Row({ space: 8 }) {
            Button('分析').height(44).onClick(() => this.action('analyze'))
            Button('刷新元数据').height(44).onClick(() => this.action('metadata/refresh'))
          }.width('100%')
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%')
    }.width('100%').height('100%').backgroundColor($r('app.color.surface'))
  }
}
