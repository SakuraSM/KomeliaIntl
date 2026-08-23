import { fileIo as fs } from '@kit.CoreFileKit';
import { TaskPhase } from '../../core/model/UiState';
import { AppContainer } from '../../core/di/AppContainer';
import { OfflineBookRecord, OfflineMediaKind, OfflinePageRecord } from '../../data/local/DatabaseModels';
import { downloadScheduler, offlineRepository } from '../../data/repository/DefaultRepositories';
import { KomgaBook, KomgaBookPage } from '../../model/KomgaModels';
import { komgaClient } from '../../network/KomgaClient';
import { downloadBackgroundTask } from './DownloadBackgroundTask';

export interface DownloadResult {
  jobId: string;
  record: OfflineBookRecord;
}

export class OfflineDownloadService {
  async download(namespace: string, bookId: string): Promise<DownloadResult> {
    const jobId = await downloadScheduler.enqueue(namespace, bookId);
    return this.run(namespace, bookId, jobId);
  }

  async resume(namespace: string, bookId: string, jobId: string): Promise<DownloadResult> {
    await downloadScheduler.resume(jobId);
    return this.run(namespace, bookId, jobId);
  }

  private async run(namespace: string, bookId: string, jobId: string): Promise<DownloadResult> {
    try {
      await downloadBackgroundTask.enter();
    } catch (_) {
      // A foreground download remains valid when the system declines a continuous task.
    }
    try {
      const book = await komgaClient.getBook(bookId);
      const isEpub = (book.media.mediaType || '').toLowerCase().includes('epub');
      return isEpub
        ? await this.downloadEpub(namespace, jobId, book)
        : await this.downloadPages(namespace, jobId, book);
    } catch (error) {
      const phase = await downloadScheduler.phase(jobId);
      if (phase !== TaskPhase.PAUSED && phase !== TaskPhase.CANCELLED) {
        await downloadScheduler.fail(jobId, (error as Error).message);
      }
      throw error;
    } finally {
      try {
        await downloadBackgroundTask.leave();
      } catch (_) {
      }
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

  private async downloadEpub(namespace: string, jobId: string, book: KomgaBook): Promise<DownloadResult> {
    await downloadScheduler.start(jobId, 1);
    const directory = this.bookDirectory(namespace, book.id);
    this.ensureDirectory(directory);
    const target = `${directory}/book.epub`;
    const bytes = await komgaClient.getBookFile(book.id);
    this.writeAtomically(target, bytes);
    const record: OfflineBookRecord = {
      namespace: namespace,
      bookId: book.id,
      mediaKind: OfflineMediaKind.EPUB_FILE,
      title: book.metadata.title || book.name,
      localPath: target,
      pageCount: book.media.pagesCount,
      downloadedPages: book.media.pagesCount,
      sizeBytes: bytes.byteLength,
      updatedAt: Date.now()
    };
    await offlineRepository.saveBook(record);
    await downloadScheduler.complete(jobId);
    return { jobId: jobId, record: record };
  }

  private async downloadPages(namespace: string, jobId: string, book: KomgaBook): Promise<DownloadResult> {
    const pages = await komgaClient.getBookPages(book.id);
    await downloadScheduler.start(jobId, pages.length);
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

    for (let index = 0; index < pages.length; index++) {
      const page = pages[index];
      const phase = await downloadScheduler.phase(jobId);
      if (phase === TaskPhase.CANCELLED) throw new Error('下载已取消');
      if (phase === TaskPhase.PAUSED) throw new Error('下载已暂停');
      if (completed.has(page.number)) continue;
      const bytes = await komgaClient.getBookPage(book.id, page.number);
      const target = `${directory}/${page.number}.${this.extension(page)}`;
      this.writeAtomically(target, bytes);
      const pageRecord: OfflinePageRecord = {
        namespace: namespace,
        bookId: book.id,
        pageNumber: page.number,
        localPath: target,
        mediaType: page.mediaType,
        sizeBytes: bytes.byteLength,
        completed: true
      };
      await offlineRepository.savePage(pageRecord);
      completed.add(page.number);
      totalBytes += bytes.byteLength;
      await downloadScheduler.update(jobId, completed.size);
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
    await downloadScheduler.complete(jobId);
    return { jobId: jobId, record: record };
  }

  private bookDirectory(namespace: string, bookId: string): string {
    const safeNamespace = namespace.replace(/[^a-zA-Z0-9._-]/g, '_');
    return `${AppContainer.requireContext().filesDir}/offline/${safeNamespace}/${bookId}`;
  }

  private ensureDirectory(path: string): void {
    if (!fs.accessSync(path)) fs.mkdirSync(path, true);
  }

  private writeAtomically(target: string, bytes: ArrayBuffer): void {
    const temporary = `${target}.partial`;
    const file = fs.openSync(temporary, fs.OpenMode.WRITE_ONLY | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, bytes);
    } finally {
      fs.closeSync(file);
    }
    if (fs.accessSync(target)) fs.unlinkSync(target);
    fs.renameSync(temporary, target);
  }

  private extension(page: KomgaBookPage): string {
    const mediaType = page.mediaType.toLowerCase();
    if (mediaType.includes('png')) return 'png';
    if (mediaType.includes('webp')) return 'webp';
    if (mediaType.includes('gif')) return 'gif';
    return 'jpg';
  }
}

export const offlineDownloadService = new OfflineDownloadService();
