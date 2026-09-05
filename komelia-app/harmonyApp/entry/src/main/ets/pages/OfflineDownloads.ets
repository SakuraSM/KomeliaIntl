import { KomeliaColors } from '../core/design/KomeliaDesign';
import { AppNavigationFrame } from '../components/AppNavigationFrame';
import { router } from '@kit.ArkUI';
import { EmptyState, LoadingState } from '../components/CommonUi';
import { localizedString } from '../core/i18n/LocalizedResources';
import { TaskPhase } from '../core/model/UiState';
import { DownloadJobRecord, namespaceOf, OfflineBookRecord, OfflineLogLevel, OfflineLogRecord,
  OfflineMediaKind } from '../data/local/DatabaseModels';
import { downloadScheduler, offlineLogRepository, offlineRepository,
  sessionRepository } from '../data/repository/DefaultRepositories';
import { OfflineSessionProfile } from '../domain/repository/Repositories';
import { offlineDownloadService } from '../domain/service/OfflineDownloadService';
import { downloadRuntimeEvents, DownloadRuntimeEvent,
  DownloadRuntimeListener } from '../domain/service/DownloadRuntimeEvents';
import { ReaderCapabilities } from '../domain/service/ReaderCapabilities';
import { formatDateTime } from '../model/UserPresentation';
import { AppState } from '../state/AppState';

enum OfflineSettingsTab {
  ACCOUNTS = 0,
  DOWNLOADS = 1,
  LOGS = 2
}

@Entry
@Component
struct OfflineDownloads {
  @State selectedTab: OfflineSettingsTab = OfflineSettingsTab.ACCOUNTS;
  @State profiles: OfflineSessionProfile[] = [];
  @State books: OfflineBookRecord[] = [];
  @State jobs: DownloadJobRecord[] = [];
  @State logs: OfflineLogRecord[] = [];
  @State logLevel: OfflineLogLevel = OfflineLogLevel.ERROR;
  @State logPage: number = 0;
  @State totalLogPages: number = 0;
  @State loading: boolean = true;
  @State actionMessage: string = '';
  private namespace: string = '';
  private readonly logPageSize: number = 20;
  private readonly downloadListener: DownloadRuntimeListener = (event: DownloadRuntimeEvent): void => {
    this.applyDownloadEvent(event);
  };

  aboutToAppear(): void {
    this.updateNamespace();
    downloadRuntimeEvents.subscribe(this.downloadListener);
    this.load().catch(() => {});
  }

  aboutToDisappear(): void {
    downloadRuntimeEvents.unsubscribe(this.downloadListener);
  }

  private updateNamespace(): void {
    const key = AppState.getInstance().namespace;
    this.namespace = key === undefined ? '' : namespaceOf(key);
  }

  private applyDownloadEvent(event: DownloadRuntimeEvent): void {
    if (event.namespace !== undefined && event.namespace !== this.namespace) return;
    if (event.removed === true) {
      this.jobs = this.jobs.filter((job: DownloadJobRecord) => job.jobId !== event.jobId);
      return;
    }
    const index = this.jobs.findIndex((job: DownloadJobRecord) => job.jobId === event.jobId);
    if (index >= 0) {
      const current = this.jobs[index];
      const next: DownloadJobRecord = {
        title: event.title === undefined ? current.title : event.title,
        completedBytes: event.completedBytes === undefined ? current.completedBytes : event.completedBytes,
        totalBytes: event.totalBytes === undefined ? current.totalBytes : event.totalBytes,
        bytesPerSecond: event.bytesPerSecond === undefined ? current.bytesPerSecond : event.bytesPerSecond,
        namespace: current.namespace,
        jobId: current.jobId,
        bookId: event.bookId === undefined ? current.bookId : event.bookId,
        phase: event.phase === undefined ? current.phase : event.phase,
        completedParts: event.completedParts === undefined ? current.completedParts : event.completedParts,
        totalParts: event.totalParts === undefined ? current.totalParts : event.totalParts,
        attempts: current.attempts,
        errorMessage: event.errorMessage === undefined ? current.errorMessage : event.errorMessage,
        updatedAt: Date.now()
      };
      const jobs = this.jobs.slice();
      jobs[index] = next;
      this.jobs = jobs;
    } else if (event.namespace !== undefined && event.bookId !== undefined && event.phase !== undefined) {
      const job: DownloadJobRecord = {
        title: event.title || '',
        completedBytes: event.completedBytes || 0,
        totalBytes: event.totalBytes || 0,
        bytesPerSecond: event.bytesPerSecond || 0,
        namespace: event.namespace,
        jobId: event.jobId,
        bookId: event.bookId,
        phase: event.phase,
        completedParts: event.completedParts || 0,
        totalParts: event.totalParts || 0,
        attempts: 0,
        errorMessage: event.errorMessage || '',
        updatedAt: Date.now()
      };
      this.jobs = [job].concat(this.jobs);
    }
    if (event.record !== undefined) {
      const bookIndex = this.books.findIndex(
        (book: OfflineBookRecord) => book.bookId === event.record?.bookId);
      if (bookIndex >= 0) {
        const books = this.books.slice();
        books[bookIndex] = event.record;
        this.books = books;
      } else {
        this.books = [event.record].concat(this.books);
      }
    }
  }

  private async refreshDownloads(showLoading: boolean = false): Promise<void> {
    if (showLoading) this.loading = true;
    this.updateNamespace();
    try {
      const values = await Promise.all([
        offlineRepository.listBooks(this.namespace),
        downloadScheduler.jobs(this.namespace)
      ]);
      this.books = values[0];
      this.jobs = values[1];
    } finally {
      this.loading = false;
    }
  }

  private selectTab(tab: OfflineSettingsTab): void {
    if (this.selectedTab === tab) return;
    this.selectedTab = tab;
  }

  private async refreshSelectedTab(): Promise<void> {
    if (this.selectedTab === OfflineSettingsTab.ACCOUNTS) {
      this.profiles = await sessionRepository.offlineProfiles();
    } else if (this.selectedTab === OfflineSettingsTab.DOWNLOADS) {
      await this.refreshDownloads(true);
    } else {
      await this.loadLogs();
    }
  }

  private async load(showLoading: boolean = true): Promise<void> {
    if (showLoading) this.loading = true;
    this.updateNamespace();
    try {
      const values = await Promise.all([
        sessionRepository.offlineProfiles(),
        offlineRepository.listBooks(this.namespace),
        downloadScheduler.jobs(this.namespace),
        offlineLogRepository.page(this.namespace, this.logLevel, this.logPage, this.logPageSize)
      ]);
      this.profiles = values[0];
      this.books = values[1];
      this.jobs = values[2];
      this.logs = values[3].content;
      this.totalLogPages = values[3].totalPages;
      if (this.logPage > 0 && this.logPage >= this.totalLogPages) {
        this.logPage = Math.max(0, this.totalLogPages - 1);
        await this.loadLogs();
      }
    } finally {
      this.loading = false;
    }
  }

  private async loadLogs(): Promise<void> {
    const page = await offlineLogRepository.page(
      this.namespace, this.logLevel, this.logPage, this.logPageSize);
    this.logs = page.content;
    this.totalLogPages = page.totalPages;
  }

  private serverIds(): string[] {
    const result: string[] = [];
    for (let index = 0; index < this.profiles.length; index++) {
      const serverId = this.profiles[index].serverId;
      if (!result.includes(serverId)) result.push(serverId);
    }
    return result;
  }

  private profilesForServer(serverId: string): OfflineSessionProfile[] {
    return this.profiles.filter((profile: OfflineSessionProfile) => profile.serverId === serverId);
  }

  private isCurrentProfile(profile: OfflineSessionProfile): boolean {
    return profile.namespace === this.namespace;
  }

  private serverContainsCurrentProfile(serverId: string): boolean {
    return this.profilesForServer(serverId).some(
      (profile: OfflineSessionProfile) => this.isCurrentProfile(profile));
  }

  private async enterOffline(profile: OfflineSessionProfile): Promise<void> {
    this.actionMessage = localizedString($r('app.string.entering_offline'));
    try {
      await AppState.getInstance().signInOffline(profile.namespace);
      router.back();
    } catch (error) {
      this.actionMessage = (error as Error).message;
    }
  }

  private async goOnline(): Promise<void> {
    this.actionMessage = localizedString($r('app.string.reconnecting'));
    try {
      const connected = await AppState.getInstance().reconnectConfiguredNetwork();
      this.actionMessage = connected ? localizedString($r('app.string.online')) :
        localizedString($r('app.string.connection_failed'));
      if (connected) router.back();
    } catch (error) {
      this.actionMessage = (error as Error).message;
    }
  }

  private confirmDeleteProfile(profile: OfflineSessionProfile): void {
    if (this.isCurrentProfile(profile)) return;
    this.getUIContext().showAlertDialog({
      title: $r('app.string.offline_delete_user_title'),
      message: localizedString($r('app.string.offline_delete_user_message'), profile.user.email),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          this.deleteProfileData(profile).catch((error: Error) => this.actionMessage = error.message);
        } }
      ]
    });
  }

  private confirmDeleteServer(serverId: string): void {
    if (this.serverContainsCurrentProfile(serverId)) return;
    this.getUIContext().showAlertDialog({
      title: $r('app.string.offline_delete_server_title'),
      message: localizedString($r('app.string.offline_delete_server_message'), serverId),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.delete'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          this.deleteServerData(serverId).catch((error: Error) => this.actionMessage = error.message);
        } }
      ]
    });
  }

  private async deleteProfileData(profile: OfflineSessionProfile, reload: boolean = true): Promise<void> {
    const jobs = await downloadScheduler.jobs(profile.namespace);
    for (let index = 0; index < jobs.length; index++) {
      await offlineDownloadService.cancel(profile.namespace, jobs[index].bookId, jobs[index].jobId);
    }
    const books = await offlineRepository.listBooks(profile.namespace);
    for (let index = 0; index < books.length; index++) {
      await offlineDownloadService.delete(profile.namespace, books[index].bookId);
    }
    await offlineDownloadService.deleteNamespace(profile.namespace);
    await sessionRepository.deleteOfflineProfile(profile.namespace);
    this.actionMessage = localizedString($r('app.string.offline_user_deleted'));
    if (reload) await this.load(false);
  }

  private async deleteServerData(serverId: string): Promise<void> {
    const profiles = this.profilesForServer(serverId);
    for (let index = 0; index < profiles.length; index++) {
      await this.deleteProfileData(profiles[index], false);
    }
    this.actionMessage = localizedString($r('app.string.offline_server_deleted'));
    await this.load(false);
  }

  private formatBytes(value: number): string {
    if (value >= 1073741824) return `${(value / 1073741824).toFixed(1)} GiB`;
    if (value >= 1048576) return `${(value / 1048576).toFixed(1)} MiB`;
    return `${Math.max(0, value / 1024).toFixed(1)} KiB`;
  }

  private async deleteBook(bookId: string): Promise<void> {
    await offlineDownloadService.delete(this.namespace, bookId);
    this.books = this.books.filter((book: OfflineBookRecord) => book.bookId !== bookId);
  }

  private openBook(book: OfflineBookRecord): void {
    if (book.mediaKind === OfflineMediaKind.EPUB_FILE && !ReaderCapabilities.nativeEpubAvailable()) {
      this.getUIContext().showAlertDialog({
        title: $r('app.string.emulator_epub_unavailable'),
        message: $r('app.string.offline_epub_message'),
        buttons: [{ value: $r('app.string.got_it'), action: () => {} }]
      });
      return;
    }
    router.pushUrl({
      url: book.mediaKind === OfflineMediaKind.EPUB_FILE ? 'pages/EpubReader' : 'pages/ImageReader',
      params: { bookId: book.bookId, initialPage: 0, bookTitle: book.title, seriesTitle: '' }
    });
  }

  private phaseLabel(phase: TaskPhase): string {
    if (phase === TaskPhase.QUEUED) return localizedString($r('app.string.queued'));
    if (phase === TaskPhase.RUNNING) return localizedString($r('app.string.downloading'));
    if (phase === TaskPhase.PAUSED) return localizedString($r('app.string.paused'));
    if (phase === TaskPhase.RETRYING) return localizedString($r('app.string.retrying'));
    if (phase === TaskPhase.FAILED) return localizedString($r('app.string.failed'));
    if (phase === TaskPhase.CANCELLED) return localizedString($r('app.string.cancelled'));
    return localizedString($r('app.string.completed'));
  }

  private startTaskAction(job: DownloadJobRecord, action: string): void {
    this.actionMessage = action === 'pause' ? localizedString($r('app.string.pausing')) :
      action === 'cancel' ? localizedString($r('app.string.cancelling')) :
        localizedString($r('app.string.resuming'));
    this.taskAction(job, action).catch((error: Error) => {
      this.actionMessage = error.message;
    });
  }

  private async taskAction(job: DownloadJobRecord, action: string): Promise<void> {
    const expectedPhase = action === 'pause' ? TaskPhase.PAUSED :
      action === 'cancel' || action === 'discard' ? TaskPhase.CANCELLED : TaskPhase.QUEUED;
    for (let index = 0; index < this.jobs.length; index++) {
      if (this.jobs[index].jobId === job.jobId) this.jobs[index].phase = expectedPhase;
    }
    this.jobs = this.jobs.slice();
    try {
      if (action === 'pause') await offlineDownloadService.pause(job.jobId);
      else if (action === 'cancel') {
        await offlineDownloadService.cancel(this.namespace, job.bookId, job.jobId);
      } else {
        offlineDownloadService.resume(this.namespace, job.bookId, job.jobId).catch(() => {});
      }
      this.actionMessage = action === 'pause' ? localizedString($r('app.string.download_paused')) :
        action === 'cancel' ? localizedString($r('app.string.download_cancelled')) :
          localizedString($r('app.string.download_resumed'));
    } finally {
      await this.refreshDownloads();
    }
  }

  private confirmRemoveTask(job: DownloadJobRecord): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.download_remove_title'),
      message: $r('app.string.download_remove_message'),
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.download_remove_task'), action: () => { this.removeTask(job, false); } },
        { value: $r('app.string.download_remove_files'), fontColor: $r('app.color.error'),
          action: () => { this.removeTask(job, true); } }
      ]
    });
  }

  private async removeTask(job: DownloadJobRecord, deleteFiles: boolean): Promise<void> {
    try {
      await offlineDownloadService.removeTask(this.namespace, job.bookId, job.jobId, deleteFiles);
      await this.refreshDownloads();
      await AppState.getInstance().refreshAvailableContent();
    } catch (error) { this.actionMessage = (error as Error).message; }
  }

  private async selectLogLevel(level: OfflineLogLevel): Promise<void> {
    this.logLevel = level;
    this.logPage = 0;
    await this.loadLogs();
  }

  private async changeLogPage(page: number): Promise<void> {
    this.logPage = Math.max(0, Math.min(page, Math.max(0, this.totalLogPages - 1)));
    await this.loadLogs();
  }

  private confirmClearLogs(): void {
    this.getUIContext().showAlertDialog({
      title: $r('app.string.offline_clear_logs'),
      message: $r('app.string.offline_clear_logs_message'),
      autoCancel: true,
      buttons: [
        { value: $r('app.string.cancel'), action: () => {} },
        { value: $r('app.string.clear'), style: DialogButtonStyle.HIGHLIGHT,
          fontColor: $r('app.color.error'), action: () => {
          offlineLogRepository.deleteAll(this.namespace).then(() => {
            this.logPage = 0;
            return this.loadLogs();
          }).catch((error: Error) => this.actionMessage = error.message);
        } }
      ]
    });
  }

  @Builder
  private tabButton(tab: OfflineSettingsTab, label: ResourceStr) {
    Button(label)
      .height(44)
      .layoutWeight(1)
      .fontColor(this.selectedTab === tab ? $r('app.color.primary') :
        $r('app.color.on_surface_variant'))
      .backgroundColor(this.selectedTab === tab ? $r('app.color.primary_container') :
        Color.Transparent)
      .borderRadius(14)
      .onClick(() => this.selectTab(tab))
  }

  @Builder
  private accountContent() {
    List({ space: 10 }) {
      if (this.actionMessage.length > 0) {
        ListItem() {
          Text(this.actionMessage).fontSize(13).fontColor($r('app.color.on_surface_variant'))
            .padding({ left: 14, right: 14, top: 8, bottom: 8 }).width('100%')
        }
      }
      ListItem() {
        Column({ space: 8 }) {
          Text($r('app.string.current_account')).fontSize(14).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
          Text(AppState.getInstance().user?.email || '-')
            .fontSize(16).fontWeight(FontWeight.Medium).width('100%')
          Text(localizedString($r('app.string.offline_account_status'),
            AppState.getInstance().isOnline ? localizedString($r('app.string.online')) :
              localizedString($r('app.string.offline'))))
            .fontSize(13).fontColor($r('app.color.on_surface_variant')).width('100%')
          Text(AppState.getInstance().effectiveServerUrl || '-')
            .fontSize(13).fontColor($r('app.color.on_surface_variant')).maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
          if (AppState.getInstance().isOnline &&
            this.profiles.some((profile: OfflineSessionProfile) => this.isCurrentProfile(profile))) {
            Button($r('app.string.enter_offline')).height(40)
              .onClick(() => {
                const current = this.profiles.find(
                  (profile: OfflineSessionProfile) => this.isCurrentProfile(profile));
                if (current !== undefined) this.enterOffline(current);
              })
          } else if (!AppState.getInstance().isOnline) {
            Button($r('app.string.go_online')).height(40).onClick(() => this.goOnline())
          }
        }
        .padding(16).width('100%').alignItems(HorizontalAlign.Start)
        .backgroundColor(KomeliaColors.surfaceHigh())
        .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
      }
      if (this.profiles.length === 0) {
        ListItem() { EmptyState($r('app.string.no_offline_accounts')) }
      } else {
        ForEach(this.serverIds(), (serverId: string) => {
          ListItem() {
            Column({ space: 8 }) {
              Row({ space: 8 }) {
                Column({ space: 2 }) {
                  Text(serverId).fontSize(15).fontWeight(FontWeight.Medium).maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
                  Text(localizedString($r('app.string.offline_available'),
                    this.profilesForServer(serverId).length))
                    .fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
                }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                if (!this.serverContainsCurrentProfile(serverId)) {
                  Button($r('app.string.delete_server_data')).height(40)
                    .fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
                    .onClick(() => this.confirmDeleteServer(serverId))
                }
              }.width('100%')
              ForEach(this.profilesForServer(serverId), (profile: OfflineSessionProfile) => {
                Row({ space: 8 }) {
                  Column({ space: 2 }) {
                    Text(profile.user.email).fontSize(15).maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
                    if (this.isCurrentProfile(profile)) {
                      Text($r('app.string.current_account')).fontSize(12).fontColor($r('app.color.primary'))
                    }
                  }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                  if (!this.isCurrentProfile(profile)) {
                    Button($r('app.string.enter_offline')).height(40)
                      .onClick(() => this.enterOffline(profile))
                    Button($r('app.string.delete')).height(40)
                      .fontColor($r('app.color.error')).backgroundColor(Color.Transparent)
                      .onClick(() => this.confirmDeleteProfile(profile))
                  }
                }.width('100%').constraintSize({ minHeight: 52 })
                  .padding({ left: 10, right: 4, top: 4, bottom: 4 })
                  .backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(12)
              }, (profile: OfflineSessionProfile) => profile.namespace)
            }
            .padding(14).width('100%').alignItems(HorizontalAlign.Start)
            .backgroundColor(KomeliaColors.surfaceHigh())
            .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
          }
        }, (serverId: string) => serverId)
      }
    }.layoutWeight(1).width('100%')
  }

  @Builder
  private downloadsContent() {
    if (this.books.length === 0 && this.jobs.length === 0) {
      EmptyState($r('app.string.no_offline_content'))
    } else {
      List({ space: 8 }) {
        if (this.actionMessage.length > 0) {
          ListItem() {
            Text(this.actionMessage).fontSize(13).fontColor($r('app.color.on_surface_variant'))
              .padding({ left: 14, right: 14, top: 8, bottom: 8 }).width('100%')
          }
        }
        ForEach(this.jobs, (job: DownloadJobRecord) => {
          if (job.jobId.length > 0) {
            ListItem() {
              Column({ space: 6 }) {
                Row() {
                  Text(job.title || localizedString($r('app.string.download_task'), job.bookId))
                    .fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1)
                  Text(this.phaseLabel(job.phase)).fontSize(13).fontColor($r('app.color.primary'))
                }.width('100%')
                Progress({ value: job.completedParts, total: Math.max(1, job.totalParts),
                  type: ProgressType.Linear }).color($r('app.color.primary')).width('100%')
                Text(localizedString($r('app.string.page_progress'), job.completedParts,
                  job.totalParts > 0 ? `${job.totalParts}` : '…'))
                  .fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
                if (job.errorMessage.length > 0) {
                  Text(job.errorMessage).fontSize(12).fontColor($r('app.color.error'))
                }
                Text(`${this.formatBytes(job.completedBytes || 0)} / ` +
                  `${(job.totalBytes || 0) > 0 ? this.formatBytes(job.totalBytes || 0) : '—'}` +
                  `${job.phase === TaskPhase.RUNNING ? ' · ' + this.formatBytes(job.bytesPerSecond || 0) + '/s' : ''}`)
                  .fontSize(12).fontColor($r('app.color.on_surface_variant')).width('100%')
                Row({ space: 8 }) {
                  if (job.phase === TaskPhase.RUNNING) {
                    Button($r('app.string.pause')).height(40)
                      .onClick((): void => this.startTaskAction(job, 'pause'))
                  } else if (job.phase === TaskPhase.PAUSED || job.phase === TaskPhase.FAILED || job.phase === TaskPhase.CANCELLED) {
                    Button($r('app.string.resume')).height(40)
                      .onClick((): void => this.startTaskAction(job, 'resume'))
                  }
                  if (job.phase === TaskPhase.RUNNING || job.phase === TaskPhase.QUEUED || job.phase === TaskPhase.PAUSED) {
                    Button($r('app.string.cancel')).height(40).fontColor($r('app.color.error'))
                      .backgroundColor(Color.Transparent)
                      .onClick((): void => this.startTaskAction(job, 'cancel'))
                  }
                  Button($r('app.string.delete')).height(40).fontColor($r('app.color.error'))
                    .backgroundColor(Color.Transparent)
                    .onClick((): void => this.confirmRemoveTask(job))
                }.width('100%')
              }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
                .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
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
              Button($r('app.string.delete')).height(40).fontColor($r('app.color.error'))
                .backgroundColor(Color.Transparent).onClick(() => this.deleteBook(book.bookId))
            }.padding(16).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
              .onClick(() => this.openBook(book))
              .border({ width: 1, color: $r('app.color.outline') }).borderRadius(16)
          }
        }, (book: OfflineBookRecord) => book.bookId)
      }.layoutWeight(1).width('100%')
    }
  }

  @Builder
  private logsContent() {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        Button($r('app.string.errors')).height(40)
          .fontColor(this.logLevel === OfflineLogLevel.ERROR ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
          .backgroundColor(this.logLevel === OfflineLogLevel.ERROR ?
            $r('app.color.primary_container') : Color.Transparent)
          .onClick(() => this.selectLogLevel(OfflineLogLevel.ERROR))
        Button($r('app.string.information')).height(40)
          .fontColor(this.logLevel === OfflineLogLevel.INFO ? $r('app.color.primary') :
            $r('app.color.on_surface_variant'))
          .backgroundColor(this.logLevel === OfflineLogLevel.INFO ?
            $r('app.color.primary_container') : Color.Transparent)
          .onClick(() => this.selectLogLevel(OfflineLogLevel.INFO))
        Blank().layoutWeight(1)
        Button($r('app.string.clear')).height(40).fontColor($r('app.color.error'))
          .backgroundColor(Color.Transparent).onClick(() => this.confirmClearLogs())
      }.width('100%')
      if (this.totalLogPages > 1) {
        Row({ space: 8 }) {
          Button($r('app.string.previous')).height(40).enabled(this.logPage > 0)
            .onClick(() => this.changeLogPage(this.logPage - 1))
          Text(localizedString($r('app.string.page_of'), this.logPage + 1,
            Math.max(1, this.totalLogPages))).fontSize(13)
            .fontColor($r('app.color.on_surface_variant')).layoutWeight(1).textAlign(TextAlign.Center)
          Button($r('app.string.next')).height(40)
            .enabled(this.logPage + 1 < this.totalLogPages)
            .onClick(() => this.changeLogPage(this.logPage + 1))
        }.width('100%')
      }
      if (this.logs.length === 0) {
        EmptyState($r('app.string.no_offline_logs'))
      } else {
        List({ space: 8 }) {
          ForEach(this.logs, (record: OfflineLogRecord) => {
            ListItem() {
              Row({ space: 12 }) {
                Text(record.message).fontSize(13)
                  .fontColor(record.level === OfflineLogLevel.ERROR ? $r('app.color.error') :
                    $r('app.color.on_surface')).layoutWeight(1)
                Text(formatDateTime(new Date(record.timestamp).toISOString()))
                  .fontSize(12).fontColor($r('app.color.on_surface_variant'))
              }.padding(14).width('100%').backgroundColor(KomeliaColors.surfaceHigh())
                .border({ width: 1, color: $r('app.color.outline') }).borderRadius(14)
            }
          }, (record: OfflineLogRecord) => `${record.id}`)
        }.layoutWeight(1).width('100%')
      }
    }.layoutWeight(1).width('100%')
  }

  @Builder
  private pageContent() {
    Column() {
      Row({ space: 8 }) {
        Button({ type: ButtonType.Circle }) { Image($r('app.media.navigation_back')).width(24).height(24).fillColor($r('app.color.on_surface')) }.width(48).height(48).fontColor($r('app.color.on_surface'))
          .backgroundColor(Color.Transparent).onClick(() => router.back())
          .accessibilityText($r('app.string.back'))
        Column({ space: 2 }) {
          Text($r('app.string.downloads_and_offline')).fontSize(22).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.on_surface')).width('100%')
          Text($r('app.string.offline_settings_description')).fontSize(13)
            .fontColor($r('app.color.on_surface_variant')).width('100%')
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button($r('app.string.refresh')).height(40)
          .onClick(() => this.refreshSelectedTab().catch((error: Error) => this.actionMessage = error.message))
      }.height(68).width('100%')

      Row({ space: 6 }) {
        this.tabButton(OfflineSettingsTab.ACCOUNTS, $r('app.string.accounts'))
        this.tabButton(OfflineSettingsTab.DOWNLOADS, $r('app.string.downloads'))
        this.tabButton(OfflineSettingsTab.LOGS, $r('app.string.logs'))
      }.width('100%').padding(4).backgroundColor(KomeliaColors.surfaceContainer()).borderRadius(16)
        .margin({ bottom: 12 })

      if (this.loading) {
        LoadingState()
      } else if (this.selectedTab === OfflineSettingsTab.ACCOUNTS) {
        this.accountContent()
      } else if (this.selectedTab === OfflineSettingsTab.DOWNLOADS) {
        this.downloadsContent()
      } else {
        this.logsContent()
      }
    }
    .padding({ left: 12, right: 12, bottom: 12 }).width('100%').height('100%')
    .align(Alignment.Top)
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
