import { fileIo as fs, statfs } from '@kit.CoreFileKit';
import { TaskPhase } from '../../core/model/UiState';
import { localizedString } from '../../core/i18n/LocalizedResources';
import { AppContainer } from '../../core/di/AppContainer';
import { OfflineBookRecord, OfflineLogLevel, OfflineMediaKind, OfflinePageRecord } from '../../data/local/DatabaseModels';
import { downloadScheduler, offlineLogRepository, offlineRepository } from '../../data/repository/DefaultRepositories';
import { KomgaBook, KomgaBookPage } from '../../model/KomgaModels';
import { komgaClient } from '../../network/KomgaClient';
import { downloadBackgroundTask } from './DownloadBackgroundTask';
import { downloadRuntimeEvents, DownloadRuntimeEvent } from './DownloadRuntimeEvents';
import { isPdfFile } from './PageMediaDetector';
import { bytesToWholeMiB, hasDownloadSpace, requiredDownloadBytes } from './DownloadStoragePolicy';
import { acceptsDownloadProgress, downloadSpeed } from '../../model/DownloadProgressModels';

export interface DownloadResult {
  jobId: string;
  record: OfflineBookRecord;
}

export interface DownloadHandle {
  jobId: string;
  completion: Promise<DownloadResult>;
}

export class OfflineDownloadService {
  private activeJobs: Map<string, Promise<DownloadResult>> = new Map<string, Promise<DownloadResult>>();

  async download(namespace: string, bookId: string): Promise<DownloadResult> {
    const handle = await this.start(namespace, bookId);
    return handle.completion;
  }

  async start(namespace: string, bookId: string): Promise<DownloadHandle> {
    const downloaded = await offlineRepository.getBook(namespace, bookId);
    if (downloaded !== undefined) {
      const result: DownloadResult = { jobId: '', record: downloaded };
      return {
        jobId: '',
        completion: Promise.resolve(result)
      };
    }
    const jobId = await downloadScheduler.enqueue(namespace, bookId);
    const queuedEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: bookId,
      phase: TaskPhase.QUEUED,
      completedParts: 0,
      totalParts: 0,
      errorMessage: ''
    };
    downloadRuntimeEvents.emit(queuedEvent);
    return {
      jobId: jobId,
      completion: this.runTracked(namespace, bookId, jobId)
    };
  }

  async resume(namespace: string, bookId: string, jobId: string): Promise<DownloadResult> {
    const active = this.activeJobs.get(jobId);
    if (active !== undefined) return active;
    await downloadScheduler.resume(jobId);
    const event: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: bookId,
      phase: TaskPhase.QUEUED,
      errorMessage: ''
    };
    downloadRuntimeEvents.emit(event);
    return this.runTracked(namespace, bookId, jobId);
  }

  async pause(jobId: string): Promise<void> {
    await downloadScheduler.pause(jobId);
    const event: DownloadRuntimeEvent = { jobId: jobId, phase: TaskPhase.PAUSED };
    downloadRuntimeEvents.emit(event);
    komgaClient.cancelFileDownload(jobId);
    const active = this.activeJobs.get(jobId);
    if (active !== undefined) {
      try {
        await active;
      } catch (_) {
      }
    }
  }

  async cancel(namespace: string, bookId: string, jobId: string): Promise<void> {
    await downloadScheduler.cancel(jobId);
    const cancelledEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: bookId,
      phase: TaskPhase.CANCELLED
    };
    downloadRuntimeEvents.emit(cancelledEvent);
    komgaClient.cancelFileDownload(jobId);
    const active = this.activeJobs.get(jobId);
    if (active !== undefined) {
      try {
        await active;
      } catch (_) {
      }
    }
    // Cancellation is a retained task state; downloaded and partial files remain reusable.
  }

  async removeTask(namespace: string, bookId: string, jobId: string, deleteFiles: boolean): Promise<void> {
    if (this.activeJobs.has(jobId)) await this.cancel(namespace, bookId, jobId);
    if (deleteFiles) await this.clearPartial(namespace, bookId);
    await downloadScheduler.remove(jobId);
    const removedEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: bookId,
      removed: true
    };
    downloadRuntimeEvents.emit(removedEvent);
  }

  private async requireRunning(jobId: string): Promise<void> {
    if (!acceptsDownloadProgress(await downloadScheduler.phase(jobId))) {
      throw new Error(localizedString($r('app.string.download_paused')));
    }
  }

  private async byteProgress(namespace: string, jobId: string, book: KomgaBook, completedBytes: number,
    totalBytes: number, bytesPerSecond: number): Promise<void> {
    await downloadScheduler.updateBytes(jobId, book.metadata.title || book.name,
      completedBytes, totalBytes, bytesPerSecond);
    if (!acceptsDownloadProgress(await downloadScheduler.phase(jobId))) return;
    downloadRuntimeEvents.emit({ namespace: namespace, jobId: jobId, bookId: book.id,
      title: book.metadata.title || book.name, completedBytes: completedBytes,
      totalBytes: totalBytes, bytesPerSecond: bytesPerSecond });
  }

  private async transfer(namespace: string, jobId: string, book: KomgaBook, target: string,
    baseBytes: number, page?: number): Promise<void> {
    let writes: Promise<void> = Promise.resolve();
    let previousTime = Date.now();
    let previousBytes = page === undefined && fs.accessSync(target) ? fs.statSync(target).size : 0;
    const listener = (total: number, transferred: number): void => {
      const now = Date.now();
      if (now - previousTime < 500) return;
      const speed = downloadSpeed(previousBytes, transferred, now - previousTime);
      previousTime = now; previousBytes = transferred;
      // Page streams have no known aggregate byte size; do not display archive size as page-byte total.
      const aggregateTotal = page === undefined ? total : 0;
      writes = writes.then(() => this.byteProgress(namespace, jobId, book,
        baseBytes + transferred, aggregateTotal, speed)).catch(() => {});
    };
    try {
      if (page === undefined) await komgaClient.downloadBookFile(book.id, target, jobId, listener);
      else await komgaClient.downloadBookPage(book.id, page, target, jobId, listener);
    } finally { await writes; }
    await this.requireRunning(jobId);
  }

  private runTracked(namespace: string, bookId: string, jobId: string): Promise<DownloadResult> {
    const existing = this.activeJobs.get(jobId);
    if (existing !== undefined) return existing;
    const operation = this.run(namespace, bookId, jobId);
    this.activeJobs.set(jobId, operation);
    operation.then(() => this.activeJobs.delete(jobId)).catch(() => this.activeJobs.delete(jobId));
    return operation;
  }

  private async run(namespace: string, bookId: string, jobId: string): Promise<DownloadResult> {
    try {
      await downloadBackgroundTask.enter();
    } catch (_) {
      // A foreground download remains valid when the system declines a continuous task.
    }
    try {
      const book = await komgaClient.getBook(bookId);
      await this.ensureDownloadSpace(book.sizeBytes);
      const isEpub = (book.media.mediaType || '').toLowerCase().includes('epub');
      const result = isEpub
        ? await this.downloadEpub(namespace, jobId, book)
        : await this.downloadPages(namespace, jobId, book);
      await this.log(namespace, OfflineLogLevel.INFO,
        localizedString($r('app.string.offline_log_download_complete'), result.record.title));
      return result;
    } catch (error) {
      const phase = await downloadScheduler.phase(jobId);
      if (phase !== TaskPhase.PAUSED && phase !== TaskPhase.CANCELLED) {
        await downloadScheduler.fail(jobId, (error as Error).message);
        const event: DownloadRuntimeEvent = {
          namespace: namespace,
          jobId: jobId,
          bookId: bookId,
          phase: TaskPhase.FAILED,
          errorMessage: (error as Error).message
        };
        downloadRuntimeEvents.emit(event);
        await this.log(namespace, OfflineLogLevel.ERROR,
          localizedString($r('app.string.offline_log_download_failed'), (error as Error).message));
      }
      throw new Error((error as Error).message);
    } finally {
      try {
        await downloadBackgroundTask.leave();
      } catch (_) {
      }
    }
  }

  private async log(namespace: string, level: OfflineLogLevel, message: string): Promise<void> {
    try {
      await offlineLogRepository.append(namespace, level, message);
    } catch (_) {
    }
  }

  private async ensureDownloadSpace(expectedSizeBytes: number): Promise<void> {
    let freeSizeBytes: number;
    try {
      const directory = AppContainer.requireContext().filesDir;
      freeSizeBytes = await statfs.getFreeSize(directory);
    } catch (_) {
      // Capacity discovery is best effort on older or restricted devices.
      return;
    }
    if (!hasDownloadSpace(freeSizeBytes, expectedSizeBytes)) {
      throw new Error(localizedString($r('app.string.download_storage_insufficient'),
        bytesToWholeMiB(requiredDownloadBytes(expectedSizeBytes)), bytesToWholeMiB(freeSizeBytes)));
    }
  }

  async delete(namespace: string, bookId: string): Promise<void> {
    const record = await offlineRepository.getBook(namespace, bookId);
    if (record === undefined) return;
    if (record.mediaKind === OfflineMediaKind.EPUB_FILE && fs.accessSync(record.localPath)) {
      fs.unlinkSync(record.localPath);
    } else if (fs.accessSync(record.localPath)) {
      const files = fs.listFileSync(record.localPath);
      for (let index = 0; index < files.length; index++) {
        const path = `${record.localPath}/${files[index]}`;
        if (fs.accessSync(path)) fs.unlinkSync(path);
      }
    }
    const directory = record.mediaKind === OfflineMediaKind.EPUB_FILE
      ? record.localPath.substring(0, record.localPath.lastIndexOf('/')) : record.localPath;
    if (directory.length > 0 && fs.accessSync(directory)) fs.rmdirSync(directory);
    await offlineRepository.deleteBook(namespace, bookId);
  }

  async deleteNamespace(namespace: string): Promise<void> {
    const directory = this.namespaceDirectory(namespace);
    if (!fs.accessSync(directory)) return;
    const entries = fs.listFileSync(directory);
    for (let index = 0; index < entries.length; index++) {
      const entry = `${directory}/${entries[index]}`;
      if (!fs.accessSync(entry)) continue;
      const stat = fs.statSync(entry);
      if (stat.isDirectory()) {
        const files = fs.listFileSync(entry);
        for (let fileIndex = 0; fileIndex < files.length; fileIndex++) {
          const file = `${entry}/${files[fileIndex]}`;
          if (fs.accessSync(file)) fs.unlinkSync(file);
        }
        if (fs.accessSync(entry)) fs.rmdirSync(entry);
      } else {
        fs.unlinkSync(entry);
      }
    }
    if (fs.accessSync(directory)) fs.rmdirSync(directory);
  }

  private async clearPartial(namespace: string, bookId: string): Promise<void> {
    const directory = this.bookDirectory(namespace, bookId);
    if (fs.accessSync(directory)) {
      const files = fs.listFileSync(directory);
      for (let index = 0; index < files.length; index++) {
        const path = `${directory}/${files[index]}`;
        if (fs.accessSync(path)) fs.unlinkSync(path);
      }
      if (fs.accessSync(directory)) fs.rmdirSync(directory);
    }
    await offlineRepository.deleteBook(namespace, bookId);
  }

  private async downloadEpub(namespace: string, jobId: string, book: KomgaBook): Promise<DownloadResult> {
    await downloadScheduler.start(jobId, 1);
    await this.requireRunning(jobId);
    const runningEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: book.id,
      phase: TaskPhase.RUNNING,
      completedParts: 0,
      totalParts: 1,
      errorMessage: ''
    };
    downloadRuntimeEvents.emit(runningEvent);
    const directory = this.bookDirectory(namespace, book.id);
    this.ensureDirectory(directory);
    const target = `${directory}/book.epub`;
    const temporary = this.prepareTemporaryFile(target, true);
    await this.byteProgress(namespace, jobId, book, fs.accessSync(temporary) ? fs.statSync(temporary).size : 0,
      book.sizeBytes, 0);
    await this.transfer(namespace, jobId, book, temporary, 0);
    const sizeBytes = this.promoteTemporaryFile(temporary, target);
    await this.byteProgress(namespace, jobId, book, sizeBytes, sizeBytes, 0);
    const record: OfflineBookRecord = {
      namespace: namespace,
      bookId: book.id,
      mediaKind: OfflineMediaKind.EPUB_FILE,
      title: book.metadata.title || book.name,
      localPath: target,
      pageCount: book.media.pagesCount,
      downloadedPages: book.media.pagesCount,
      sizeBytes: sizeBytes,
      updatedAt: Date.now()
    };
    await offlineRepository.saveBook(record);
    await downloadScheduler.complete(jobId);
    if (await downloadScheduler.phase(jobId) !== TaskPhase.COMPLETED) throw new Error('DOWNLOAD_INTERRUPTED');
    const completedEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: book.id,
      phase: TaskPhase.COMPLETED,
      completedParts: 1,
      totalParts: 1,
      record: record
    };
    downloadRuntimeEvents.emit(completedEvent);
    return { jobId: jobId, record: record };
  }

  private async downloadPages(namespace: string, jobId: string, book: KomgaBook): Promise<DownloadResult> {
    const pages = await komgaClient.getBookPages(book.id);
    await downloadScheduler.start(jobId, pages.length);
    await this.requireRunning(jobId);
    const runningEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: book.id,
      phase: TaskPhase.RUNNING,
      completedParts: 0,
      totalParts: pages.length,
      errorMessage: ''
    };
    downloadRuntimeEvents.emit(runningEvent);
    const directory = this.bookDirectory(namespace, book.id);
    this.ensureDirectory(directory);
    const saved = await offlineRepository.listPages(namespace, book.id);
    const completed = new Set<number>();
    let totalBytes = 0;
    for (let index = 0; index < saved.length; index++) {
      if (saved[index].completed && fs.accessSync(saved[index].localPath)) {
        completed.add(saved[index].pageNumber);
        totalBytes += saved[index].sizeBytes;
      }
    }

    await this.byteProgress(namespace, jobId, book, totalBytes, 0, 0);
    await downloadScheduler.update(jobId, completed.size);
    for (let index = 0; index < pages.length; index++) {
      const page = pages[index];
      const phase = await downloadScheduler.phase(jobId);
      if (phase === TaskPhase.CANCELLED) throw new Error(localizedString($r('app.string.download_cancelled')));
      if (phase === TaskPhase.PAUSED) throw new Error(localizedString($r('app.string.download_paused')));
      if (completed.has(page.number)) continue;
      const declaredTarget = `${directory}/${page.number}.${this.extension(page)}`;
      const temporary = this.prepareTemporaryFile(declaredTarget);
      await this.transfer(namespace, jobId, book, temporary, totalBytes, page.number);
      const detectedPdf = isPdfFile(temporary);
      const target = detectedPdf ? `${directory}/${page.number}.pdf` : declaredTarget;
      const sizeBytes = this.promoteTemporaryFile(temporary, target);
      const pageRecord: OfflinePageRecord = {
        namespace: namespace,
        bookId: book.id,
        pageNumber: page.number,
        localPath: target,
        mediaType: detectedPdf ? 'application/pdf' : page.mediaType,
        sizeBytes: sizeBytes,
        completed: true
      };
      await offlineRepository.savePage(pageRecord);
      completed.add(page.number);
      totalBytes += sizeBytes;
      await downloadScheduler.update(jobId, completed.size);
      await this.byteProgress(namespace, jobId, book, totalBytes, 0, 0);
      await this.requireRunning(jobId);
      const progressEvent: DownloadRuntimeEvent = {
        namespace: namespace,
        jobId: jobId,
        bookId: book.id,
        phase: TaskPhase.RUNNING,
        completedParts: completed.size,
        totalParts: pages.length
      };
      downloadRuntimeEvents.emit(progressEvent);
    }

    const record: OfflineBookRecord = {
      namespace: namespace,
      bookId: book.id,
      mediaKind: OfflineMediaKind.PAGE_CACHE,
      title: book.metadata.title || book.name,
      localPath: directory,
      pageCount: pages.length,
      downloadedPages: completed.size,
      sizeBytes: totalBytes,
      updatedAt: Date.now()
    };
    await offlineRepository.saveBook(record);
    await this.byteProgress(namespace, jobId, book, totalBytes, totalBytes, 0);
    await downloadScheduler.complete(jobId);
    if (await downloadScheduler.phase(jobId) !== TaskPhase.COMPLETED) throw new Error('DOWNLOAD_INTERRUPTED');
    const completedEvent: DownloadRuntimeEvent = {
      namespace: namespace,
      jobId: jobId,
      bookId: book.id,
      phase: TaskPhase.COMPLETED,
      completedParts: pages.length,
      totalParts: pages.length,
      record: record
    };
    downloadRuntimeEvents.emit(completedEvent);
    return { jobId: jobId, record: record };
  }

  private bookDirectory(namespace: string, bookId: string): string {
    return `${this.namespaceDirectory(namespace)}/${bookId}`;
  }

  private namespaceDirectory(namespace: string): string {
    const safeNamespace = namespace.replace(/[^a-zA-Z0-9._-]/g, '_');
    return `${AppContainer.requireContext().filesDir}/offline/${safeNamespace}`;
  }

  private ensureDirectory(path: string): void {
    if (!fs.accessSync(path)) fs.mkdirSync(path, true);
  }

  private prepareTemporaryFile(target: string, preserveExisting: boolean = false): string {
    const temporary = `${target}.partial`;
    if (!preserveExisting && fs.accessSync(temporary)) fs.unlinkSync(temporary);
    return temporary;
  }

  private promoteTemporaryFile(temporary: string, target: string): number {
    if (!fs.accessSync(temporary)) {
      throw new Error(localizedString($r('app.string.download_file_missing')));
    }
    const sizeBytes = fs.statSync(temporary).size;
    if (sizeBytes <= 0) {
      fs.unlinkSync(temporary);
      throw new Error(localizedString($r('app.string.download_file_empty')));
    }
    if (fs.accessSync(target)) fs.unlinkSync(target);
    fs.renameSync(temporary, target);
    return sizeBytes;
  }

  private extension(page: KomgaBookPage): string {
    const mediaType = page.mediaType.toLowerCase();
    if (mediaType.includes('pdf')) return 'pdf';
    if (mediaType.includes('png')) return 'png';
    if (mediaType.includes('webp')) return 'webp';
    if (mediaType.includes('gif')) return 'gif';
    return 'jpg';
  }
}

export const offlineDownloadService = new OfflineDownloadService();
