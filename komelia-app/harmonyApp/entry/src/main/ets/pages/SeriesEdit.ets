import { router } from '@kit.ArkUI';
import { catalogRepository, komfRepository } from '../data/repository/DefaultRepositories';
import { KomgaSeries, SeriesMetadataUpdateRequest } from '../model/KomgaModels';
import { komgaClient } from '../network/KomgaClient';

interface SeriesEditParams { seriesId: string; }

@Entry
@Component
struct SeriesEdit {
  @State series?: KomgaSeries;
  @State title: string = '';
  @State titleSort: string = '';
  @State summary: string = '';
  @State publisher: string = '';
  @State language: string = '';
  @State status: string = '';
  @State genres: string = '';
  @State tags: string = '';
  @State message: string = '';
  @State saving: boolean = false;
  private seriesId: string = '';

  aboutToAppear(): void {
    this.seriesId = (router.getParams() as SeriesEditParams).seriesId;
    this.load();
  }

  private async load(): Promise<void> {
    try {
      this.series = await catalogRepository.seriesDetail(this.seriesId);
      this.title = this.series.metadata.title;
      this.titleSort = this.series.metadata.titleSort;
      this.summary = this.series.metadata.summary;
      this.publisher = this.series.metadata.publisher;
      this.language = this.series.metadata.language;
      this.status = this.series.metadata.status;
      this.genres = this.series.metadata.genres.join('、');
      this.tags = this.series.metadata.tags.join('、');
    } catch (error) { this.message = (error as Error).message; }
  }

  private list(value: string): string[] {
    return value.split(/[、,，]/).map((item: string) => item.trim()).filter((item: string) => item.length > 0);
  }

  private async save(): Promise<void> {
    this.saving = true;
    try {
      const request: SeriesMetadataUpdateRequest = {
        title: this.title.trim(), titleSort: this.titleSort.trim(), summary: this.summary.trim(),
        publisher: this.publisher.trim(), language: this.language.trim(), status: this.status.trim(),
        genres: this.list(this.genres), tags: this.list(this.tags)
      };
      await komgaClient.updateSeriesMetadata(this.seriesId, request);
      this.message = '系列信息已保存';
    } catch (error) { this.message = (error as Error).message; }
    finally { this.saving = false; }
  }

  private async action(kind: string): Promise<void> {
    try {
      if (kind === 'identify') {
        if (this.series === undefined) return;
        await komfRepository.identifySeries(this.series.libraryId, this.seriesId);
        this.message = 'Komf 识别任务已提交';
      } else if (kind === 'reset') {
        if (this.series === undefined) return;
        await komfRepository.resetSeries(this.series.libraryId, this.seriesId, false);
        this.message = 'Komf 元数据重置已提交';
      } else {
        await komgaClient.runSeriesAction(this.seriesId, kind);
        this.message = '服务端任务已提交';
      }
    } catch (error) { this.message = (error as Error).message; }
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
        Text('编辑系列').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1)
        Button(this.saving ? '保存中…' : '保存').height(44).enabled(!this.saving).onClick(() => this.save())
      }.height(64).width('100%')
      Scroll() {
        Column({ space: 14 }) {
          this.field('标题', this.title, false, (value: string) => this.title = value)
          this.field('排序标题', this.titleSort, false, (value: string) => this.titleSort = value)
          this.field('简介', this.summary, true, (value: string) => this.summary = value)
          this.field('出版社', this.publisher, false, (value: string) => this.publisher = value)
          this.field('语言', this.language, false, (value: string) => this.language = value)
          this.field('出版状态', this.status, false, (value: string) => this.status = value)
          this.field('类型（顿号或逗号分隔）', this.genres, false, (value: string) => this.genres = value)
          this.field('标签（顿号或逗号分隔）', this.tags, false, (value: string) => this.tags = value)
          Text('维护操作').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.on_surface_variant')).width('100%')
          Flex({ wrap: FlexWrap.Wrap }) {
            Button('分析').height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('analyze'))
            Button('刷新元数据').height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('metadata/refresh'))
            Button('Komf 识别').height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('identify'))
            Button('Komf 重置').height(44).margin({ right: 8, bottom: 8 }).onClick(() => this.action('reset'))
          }.width('100%')
          if (this.message.length > 0) {
            Text(this.message).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          }
        }.padding({ left: 12, right: 12, bottom: 32 }).width('100%').constraintSize({ maxWidth: 840 })
      }.layoutWeight(1).width('100%')
    }.width('100%').height('100%').backgroundColor($r('app.color.surface'))
  }
}
