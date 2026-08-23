import { router } from '@kit.ArkUI';
import { EmptyState, LoadingState } from '../components/CommonUi';
import { namespaceOf, DownloadJobRecord, OfflineBookRecord } from '../data/local/DatabaseModels';
import { downloadScheduler, offlineRepository } from '../data/repository/DefaultRepositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { AppState } from '../state/AppState';
import { TaskPhase } from '../core/model/UiState';

@Entry
@Component
struct OfflineDownloads {
  @State books: OfflineBookRecord[] = [];
  @State jobs: DownloadJobRecord[] = [];
  @State loading: boolean = true;
  private namespace: string = '';

  aboutToAppear(): void {
    const key = AppState.getInstance().namespace;
    this.namespace = key === undefined ? '' : namespaceOf(key);
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    try {
      const values = await Promise.all([
        offlineRepository.listBooks(this.namespace), downloadScheduler.jobs(this.namespace)
      ]);
      this.books = values[0]; this.jobs = values[1];
    } finally { this.loading = false; }
  }

  private formatBytes(value: number): string {
    if (value >= 1073741824) return `${(value / 1073741824).toFixed(1)} GiB`;
    if (value >= 1048576) return `${(value / 1048576).toFixed(1)} MiB`;
    return `${Math.max(0, value / 1024).toFixed(1)} KiB`;
  }

  private async deleteBook(bookId: string): Promise<void> {
    await offlineDownloadService.delete(this.namespace, bookId); await this.load();
  }

  private phaseLabel(phase: TaskPhase): string {
    if (phase === TaskPhase.QUEUED) return '等待中';
    if (phase === TaskPhase.RUNNING) return '下载中';
    if (phase === TaskPhase.PAUSED) return '已暂停';
    if (phase === TaskPhase.RETRYING) return '正在重试';
    if (phase === TaskPhase.FAILED) return '失败';
    if (phase === TaskPhase.CANCELLED) return '已取消';
    return '已完成';
  }

  private async taskAction(job: DownloadJobRecord, action: string): Promise<void> {
    if (action === 'pause') await downloadScheduler.pause(job.jobId);
    else if (action === 'cancel') await downloadScheduler.cancel(job.jobId);
    else {
      try { await offlineDownloadService.resume(this.namespace, job.bookId, job.jobId); } catch (_) {}
    }
    await this.load();
  }

  build() {
    Column() {
      Row({ space: 8 }) {
        Button('‹').width(44).height(44).fontSize(30).backgroundColor(Color.Transparent).onClick(() => router.back())
        Column({ space: 2 }) {
          Text('离线与下载').fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text(`${this.books.length} 本离线内容`).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button('刷新').height(40).onClick(() => this.load())
      }.height(64).width('100%')
      if (this.loading) { LoadingState() }
      else if (this.books.length === 0 && this.jobs.length === 0) { EmptyState('还没有离线内容') }
      else {
        List({ space: 8 }) {
          ForEach(this.jobs, (job: DownloadJobRecord) => {
            if (job.phase !== TaskPhase.COMPLETED) {
              ListItem() {
                Column({ space: 6 }) {
                  Row() {
                    Text(`任务 ${job.bookId}`).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
                    Text(this.phaseLabel(job.phase)).fontSize(13).fontColor($r('app.color.primary'))
                  }.width('100%')
                  Progress({ value: job.completedParts, total: Math.max(1, job.totalParts), type: ProgressType.Linear })
                    .color($r('app.color.primary')).width('100%')
                  if (job.errorMessage.length > 0) Text(job.errorMessage).fontSize(12).fontColor($r('app.color.error'))
                  Row({ space: 8 }) {
                    if (job.phase === TaskPhase.RUNNING) {
                      Button('暂停').height(40).onClick(() => this.taskAction(job, 'pause'))
                    } else if (job.phase === TaskPhase.PAUSED || job.phase === TaskPhase.FAILED) {
                      Button('继续').height(40).onClick(() => this.taskAction(job, 'resume'))
                    }
                    if (job.phase !== TaskPhase.CANCELLED) {
                      Button('取消').height(40).fontColor($r('app.color.error'))
                        .backgroundColor(Color.Transparent).onClick(() => this.taskAction(job, 'cancel'))
                    }
                  }.width('100%')
                }.padding(16).width('100%').backgroundColor($r('app.color.surface_high')).borderRadius(16)
              }
            }
          }, (job: DownloadJobRecord) => job.jobId)
          ForEach(this.books, (book: OfflineBookRecord) => {
            ListItem() {
              Row({ space: 12 }) {
                Column({ space: 4 }) {
                  Text(book.title).fontSize(16).fontWeight(FontWeight.Medium).maxLines(2).width('100%')
                  Text(`${book.downloadedPages}/${book.pageCount} · ${this.formatBytes(book.sizeBytes)}`)
                    .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
                }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                Button('删除').height(40).fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
                  .onClick(() => this.deleteBook(book.bookId))
              }.padding(16).width('100%').backgroundColor($r('app.color.surface_high'))
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
            }
          }, (book: OfflineBookRecord) => book.bookId)
        }.layoutWeight(1).width('100%')
      }
    }.padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
  }
}
