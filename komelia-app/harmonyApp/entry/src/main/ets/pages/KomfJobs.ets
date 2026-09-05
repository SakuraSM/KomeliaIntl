import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, ErrorState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { KomfMetadataJob } from '../model/KomfModels';

const JOB_FILTERS: string[] = ['', 'RUNNING', 'COMPLETED', 'FAILED'];

@Entry
@Component
struct KomfJobs {
  @State loading: boolean = true;
  @State jobs: KomfMetadataJob[] = [];
  @State errorMessage: string = '';
  @State message: string = '';
  @State filterIndex: number = 0;
  @State totalElements: number = 0;

  aboutToAppear(): void {
    this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    try {
      await configureStoredKomf();
      const page = await komfRepository.jobs(1, 100, JOB_FILTERS[this.filterIndex]);
      this.jobs = page.content;
      this.totalElements = page.content.length;
    } catch (error) {
      this.errorMessage = (error as Error).message;
    } finally {
      this.loading = false;
    }
  }

  private filterLabel(index: number): ResourceStr {
    if (index === 1) return $r('app.string.running');
    if (index === 2) return $r('app.string.completed');
    if (index === 3) return $r('app.string.failed');
    return $r('app.string.all');
  }

  private statusLabel(status: string): ResourceStr {
    const normalized = status.toLocaleUpperCase();
    if (normalized === 'RUNNING') return $r('app.string.running');
    if (normalized === 'COMPLETED' || normalized === 'SUCCESS') return $r('app.string.completed');
    if (normalized === 'FAILED' || normalized === 'ERROR') return $r('app.string.failed');
    if (normalized === 'QUEUED' || normalized === 'PENDING') return $r('app.string.queued');
    return status;
  }

  private confirmClear(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.clear_history'),
      message: $r('app.string.clear_komf_jobs_confirmation'),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.confirm'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => this.clear().catch(() => {}) }
      ]
    });
  }

  private async clear(): Promise<void> {
    try {
      await komfRepository.deleteAllJobs();
      this.jobs = [];
      this.totalElements = 0;
      this.message = localizedString($r('app.string.job_history_cleared'));
    } catch (error) {
      this.message = (error as Error).message;
    }
  }

  @Builder
  private filterButton(index: number) {
    Button(this.filterLabel(index)).height(40).margin({ right: 8 })
      .fontColor(this.filterIndex === index ? $r('app.color.primary') : $r('app.color.on_surface_variant'))
      .backgroundColor(this.filterIndex === index ? $r('app.color.primary_container') :
        KomeliaColors.surfaceContainer())
      .onClick(() => {
        this.filterIndex = index;
        this.load();
      })
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.komf_job_history')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text(localizedString($r('app.string.jobs_count'), this.totalElements)).fontSize(12)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button($r('app.string.clear_history')).height(40).fontColor($r('app.color.error'))
          .backgroundColor(Color.Transparent).enabled(this.totalElements > 0).onClick(() => this.confirmClear())
      }.height(72).width('100%')

      Row() {
        this.filterButton(0)
        this.filterButton(1)
        this.filterButton(2)
        this.filterButton(3)
      }.height(52).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0) {
        ErrorState(this.errorMessage, () => this.load())
      } else if (this.jobs.length === 0) {
        EmptyState($r('app.string.no_jobs'))
      } else {
        List({ space: 10 }) {
          ForEach(this.jobs, (job: KomfMetadataJob) => {
            ListItem() {
              Column({ space: 6 }) {
                Row({ space: 8 }) {
                  Text(this.statusLabel(job.status)).fontSize(12).fontColor($r('app.color.primary'))
                    .padding({ left: 9, right: 9, top: 5, bottom: 5 })
                    .backgroundColor($r('app.color.primary_container')).borderRadius(12)
                  Text(job.seriesId).fontSize(14).fontWeight(FontWeight.Medium).layoutWeight(1)
                    .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                }.width('100%')
                if ((job.message || '').length > 0) {
                  Text(job.message).fontSize(13).fontColor($r('app.color.on_surface_variant'))
                    .maxLines(3).width('100%')
                }
                Text(job.finishedAt || job.startedAt || job.id).fontSize(11)
                  .fontColor($r('app.color.on_surface_variant')).width('100%')
              }
              .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
              .onClick(() => router.pushUrl({ url: 'pages/KomfJobDetail', params: { jobId: job.id } }))
              .accessibilityText($r('app.string.open_job_detail'))
            }
          }, (job: KomfMetadataJob) => job.id)
        }.layoutWeight(1).width('100%').constraintSize({ maxWidth: 840 })
      }

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
