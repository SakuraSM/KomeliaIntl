import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { ErrorState, LoadingState, SectionTitle } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { requiredRouteId } from '../core/navigation/RouteParams';
import { komfRepository } from '../data/repository/DefaultRepositories';
import { configureStoredKomf } from '../domain/service/KomfSession';
import { KomfJobEvent, KomfJobEventSubscription, KomfMetadataJob } from '../model/KomfModels';

interface KomfJobDetailParams { jobId: string; }

@Entry
@Component
struct KomfJobDetail {
  @State loading: boolean = true;
  @State refreshing: boolean = false;
  @State job: KomfMetadataJob | undefined = undefined;
  @State events: KomfJobEvent[] = [];
  @State streamActive: boolean = false;
  @State errorMessage: string = '';
  @State streamMessage: string = '';
  private jobId: string = '';
  private active: boolean = false;
  private subscription: KomfJobEventSubscription | undefined = undefined;

  aboutToAppear(): void {
    this.active = true;
    const params = router.getParams() as KomfJobDetailParams | undefined;
    this.jobId = requiredRouteId(params?.jobId);
    if (this.jobId.length > 0) this.load(true);
  }

  aboutToDisappear(): void {
    this.active = false;
    this.stopStream();
  }

  private normalizedStatus(): string {
    return (this.job?.status || '').toLocaleUpperCase();
  }

  private isRunning(): boolean {
    const status = this.normalizedStatus();
    return status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING';
  }

  private statusLabel(status: string): ResourceStr {
    const normalized = status.toLocaleUpperCase();
    if (normalized === 'RUNNING') return $r('app.string.running');
    if (normalized === 'COMPLETED' || normalized === 'SUCCESS') return $r('app.string.completed');
    if (normalized === 'FAILED' || normalized === 'ERROR') return $r('app.string.failed');
    if (normalized === 'QUEUED' || normalized === 'PENDING') return $r('app.string.queued');
    return status;
  }

  private async load(observe: boolean): Promise<void> {
    if (this.jobId.length === 0) {
      this.loading = false;
      this.errorMessage = localizedString($r('app.string.job_not_found'));
      return;
    }
    if (this.job === undefined) this.loading = true;
    else this.refreshing = true;
    this.errorMessage = '';
    try {
      await configureStoredKomf();
      const job = await komfRepository.job(this.jobId);
      if (!this.active) return;
      this.job = job;
      if (observe && this.isRunning()) this.startStream();
      else if (!this.isRunning()) this.stopStream();
    } catch (error) {
      if (this.active) this.errorMessage = (error as Error).message;
    } finally {
      if (this.active) {
        this.loading = false;
        this.refreshing = false;
      }
    }
  }

  private startStream(): void {
    this.stopStream();
    this.streamActive = true;
    this.streamMessage = localizedString($r('app.string.waiting_for_job_events'));
    this.subscription = komfRepository.observeJobEvents(this.jobId,
      (event: KomfJobEvent) => this.handleEvent(event),
      () => {
        if (!this.active) return;
        this.streamActive = false;
        this.streamMessage = localizedString($r('app.string.job_event_stream_finished'));
        this.load(false);
      },
      (error: Error) => {
        if (!this.active) return;
        this.streamActive = false;
        this.streamMessage = error.message;
        this.load(false);
      });
  }

  private stopStream(): void {
    this.subscription?.close();
    this.subscription = undefined;
    this.streamActive = false;
  }

  private handleEvent(event: KomfJobEvent): void {
    if (!this.active) return;
    if (event.type === 'EventStreamNotFoundEvent') {
      this.streamActive = false;
      this.streamMessage = localizedString($r('app.string.job_event_stream_not_found'));
      this.load(false);
      return;
    }
    this.events = this.events.concat([event]).slice(-100);
    this.streamMessage = localizedString($r('app.string.job_events_received'), this.events.length);
  }

  private eventTitle(event: KomfJobEvent): ResourceStr {
    if (event.type === 'ProviderSeriesEvent') return $r('app.string.komf_retrieving_series');
    if (event.type === 'ProviderBookEvent') return $r('app.string.komf_retrieving_books');
    if (event.type === 'ProviderCompletedEvent') return $r('app.string.komf_provider_completed');
    if (event.type === 'ProviderErrorEvent') return $r('app.string.komf_provider_failed');
    if (event.type === 'PostProcessingStartEvent') return $r('app.string.komf_post_processing');
    if (event.type === 'ProcessingErrorEvent') return $r('app.string.komf_processing_failed');
    return $r('app.string.komf_unknown_event');
  }

  private eventDescription(event: KomfJobEvent): string {
    if (event.type === 'ProviderBookEvent' && event.totalBooks !== undefined && event.bookProgress !== undefined) {
      return localizedString($r('app.string.komf_book_progress'), event.bookProgress, event.totalBooks);
    }
    if ((event.message || '').length > 0) return event.message || '';
    if ((event.provider || '').length > 0) return event.provider || '';
    return event.type;
  }

  private eventIsError(event: KomfJobEvent): boolean {
    return event.type === 'ProviderErrorEvent' || event.type === 'ProcessingErrorEvent';
  }

  @Builder
  private infoRow(label: ResourceStr, value: ResourceStr) {
    Row({ space: 12 }) {
      Text(label).fontSize(14).fontColor($r('app.color.on_surface_variant')).width(112)
      Text(value).fontSize(14).fontColor($r('app.color.on_surface')).layoutWeight(1)
        .copyOption(CopyOptions.LocalDevice).maxLines(4).textOverflow({ overflow: TextOverflow.Ellipsis })
    }.width('100%').alignItems(VerticalAlign.Top)
  }

  @Builder
  private eventCard(event: KomfJobEvent) {
    Column({ space: 6 }) {
      Row({ space: 8 }) {
        Text(this.eventTitle(event)).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
          .fontColor(this.eventIsError(event) ? $r('app.color.error') : $r('app.color.on_surface'))
        if ((event.provider || '').length > 0) {
          Text(event.provider || '').fontSize(12).fontColor($r('app.color.primary'))
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .backgroundColor($r('app.color.primary_container')).borderRadius(10)
        }
      }.width('100%')
      Text(this.eventDescription(event)).fontSize(13).fontColor($r('app.color.on_surface_variant'))
        .width('100%').maxLines(4).textOverflow({ overflow: TextOverflow.Ellipsis })
      if (event.type === 'ProviderBookEvent' && event.totalBooks !== undefined && event.bookProgress !== undefined &&
        event.totalBooks > 0) {
        Progress({ value: event.bookProgress, total: event.totalBooks, type: ProgressType.Linear })
          .width('100%').height(4).color($r('app.color.primary'))
      }
    }
    .padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
    .border({ width: 1, color: this.eventIsError(event) ? $r('app.color.error') : $r('app.color.outline') })
    .borderRadius(16)
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back()).accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.komf_job_detail')).fontSize(22).fontWeight(FontWeight.Bold).width('100%')
          Text(this.jobId).fontSize(12).fontColor($r('app.color.on_surface_variant')).maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button(this.refreshing ? $r('app.string.loading') : $r('app.string.refresh')).height(40)
          .enabled(!this.loading && !this.refreshing).onClick(() => this.load(false))
      }.height(72).width('100%')

      if (this.loading) {
        LoadingState()
      } else if (this.errorMessage.length > 0 && this.job === undefined) {
        ErrorState(this.errorMessage, () => this.load(true))
      } else {
        Scroll() {
          Column({ space: 18 }) {
            if (this.job !== undefined) {
              Column({ space: 12 }) {
                this.infoRow($r('app.string.job_status'), this.statusLabel(this.job?.status || ''))
                this.infoRow($r('app.string.series'), this.job?.seriesId || '-')
                this.infoRow($r('app.string.job_started_at'), this.job?.startedAt || '-')
                this.infoRow($r('app.string.job_finished_at'), this.job?.finishedAt || '-')
                if ((this.job?.message || '').length > 0) {
                  this.infoRow($r('app.string.job_message'), this.job?.message || '')
                }
                Button($r('app.string.open_series')).height(44).width('100%')
                  .enabled((this.job?.seriesId || '').length > 0)
                  .onClick(() => router.pushUrl({ url: 'pages/SeriesDetail',
                    params: { seriesId: this.job?.seriesId || '' } }))
              }
              .padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
            }

            Column({ space: 10 }) {
              Row({ space: 8 }) {
                SectionTitle($r('app.string.job_event_stream'))
                if (this.streamActive) {
                  LoadingProgress().width(20).height(20).color($r('app.color.primary'))
                }
              }.width('100%')
              if (this.streamMessage.length > 0) {
                Text(this.streamMessage).fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
              }
              if (this.events.length === 0) {
                Text(this.isRunning() ? $r('app.string.waiting_for_job_events') :
                  $r('app.string.no_job_events')).fontSize(14)
                  .fontColor($r('app.color.on_surface_variant')).padding({ top: 8, bottom: 8 }).width('100%')
              } else {
                ForEach(this.events, (event: KomfJobEvent, index: number) => {
                  this.eventCard(event)
                }, (event: KomfJobEvent, index: number) => `${index}:${event.type}:${event.provider || ''}`)
              }
            }.width('100%')

            if (this.errorMessage.length > 0) {
              Text(this.errorMessage).fontSize(13).fontColor($r('app.color.error')).width('100%')
            }
          }.width('100%').constraintSize({ maxWidth: 840 }).padding({ bottom: 32 })
        }.layoutWeight(1).width('100%').align(Alignment.Top)
      }
    }
    .padding({ left: 12, right: 12 }).width('100%').height('100%').align(Alignment.Top)
    .justifyContent(FlexAlign.Start).backgroundColor(KomeliaColors.surface())
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      AppNavigationFrame({ content: () => { this.pageContent(); } })
    }
    .width('100%')
    .height('100%')
  }
}
