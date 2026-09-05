import { image } from '@kit.ImageKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { localizedString } from '../../core/i18n/LocalizedResources';
import { OfflineLogLevel, ProgressOutboxOperation, ProgressOutboxRecord } from
  '../../data/local/DatabaseModels';
import { offlineLogRepository, offlineRepository, progressOutbox } from '../../data/repository/DefaultRepositories';
import { ReadiumLocator } from '../../model/KomgaModels';
import { komgaClient } from '../../network/KomgaClient';
import { AppPreferences } from '../../persistence/AppPreferences';
import { isPdfBytes, isPdfFile, isPdfMediaType } from './PageMediaDetector';
import { pdfPageRenderer } from './PdfPageRenderer';
import { ConcurrentTaskGate, ReaderRequestToken } from './ReaderRequestToken';
import { LatestAsyncTask } from './LatestAsyncTask';
import { progressSyncCoordinator, progressSyncKey } from './ProgressSyncCoordinator';
import { readerPageByteCache, readerPageByteCacheKey } from './ReaderPageCache';
import { isLocalBook, localProgressPage } from '../../model/LocalContentModels';
import { localLibraryRepository } from '../../data/repository/LocalLibraryRepository';
import { localPublicationReader } from './LocalPublicationReader';
import { localLibraryService } from './LocalLibraryService';

const readerPrefetchGate = new ConcurrentTaskGate(2);

interface PageProgressUpdate {
  namespace: string;
  bookId: string;
  page: number;
  completed: boolean;
}

interface EpubProgressUpdate {
  namespace: string;
  bookId: string;
  page: number;
  locator: ReadiumLocator;
}

export class ReaderEngine {
  private pageProgressWriters: Map<string, LatestAsyncTask<PageProgressUpdate>> = new Map();
  private epubProgressWriters: Map<string, LatestAsyncTask<EpubProgressUpdate>> = new Map();

  async pageSource(namespace: string, bookId: string, page: number,
    token?: ReaderRequestToken, editable: boolean = false): Promise<PixelMap> {
    token?.throwIfCancelled();
    if (isLocalBook(bookId)) return localPublicationReader.page(bookId, page, token, editable);
    try {
      const local = await offlineRepository.getPage(namespace, bookId, page);
      token?.throwIfCancelled();
      if (local !== undefined && local.completed) {
        if (isPdfMediaType(local.mediaType) || isPdfFile(local.localPath)) {
          return await pdfPageRenderer.renderFile(local.localPath, token);
        }
        const localSource = image.createImageSource(local.localPath);
        if (localSource !== undefined) {
          try {
            const localPixelMap = await localSource.createPixelMap({
              editable: editable,
              desiredPixelFormat: image.PixelMapFormat.RGBA_8888
            });
            if (token?.isCancelled()) {
              localPixelMap.release();
              throw new Error(localizedString($r('app.string.reader_request_cancelled')));
            }
            return localPixelMap;
          } finally {
            localSource.release();
          }
        }
      }
    } catch (_) {
      // A stale or unavailable offline cache must not block the online reader.
    }
    token?.throwIfCancelled();

    const cacheKey = readerPageByteCacheKey(komgaClient.getImageCacheScope(), namespace, bookId, page);
    let bytes: ArrayBuffer | undefined = undefined;
    try {
      try {
        bytes = await readerPageByteCache.acquire(cacheKey, () => komgaClient.getBookPage(bookId, page));
      } catch (error) {
        const reason = error as BusinessError;
        throw new Error(localizedString($r('app.string.page_request_failed'),
          reason.code || 0, reason.message || localizedString($r('app.string.unknown_error'))));
      }
      token?.throwIfCancelled();
      if (bytes === undefined) throw new Error(localizedString($r('app.string.reader_page_decode_failed')));
      if (isPdfBytes(bytes)) {
        try {
          return await pdfPageRenderer.render(bytes, page, token);
        } catch (error) {
          throw new Error(localizedString($r('app.string.pdf_render_failed'), (error as Error).message));
        }
      }
      const source = image.createImageSource(bytes);
      if (source === undefined) throw new Error(localizedString($r('app.string.reader_page_decode_failed')));
      try {
        let width: number = 0;
        let height: number = 0;
        try {
          const info = await source.getImageInfo();
          width = info.size.width;
          height = info.size.height;
        } catch (error) {
          const reason = error as BusinessError;
          const header = new Uint8Array(bytes);
          throw new Error(localizedString($r('app.string.page_info_failed'), bytes.byteLength,
            `${header[0] || 0},${header[1] || 0},${header[2] || 0},${header[3] || 0}; ` +
            `${reason.code || 0}: ${reason.message || localizedString($r('app.string.unknown_error'))}`));
        }
        try {
          const longestSide = Math.max(width, height);
          token?.throwIfCancelled();
          if (longestSide > 2048) {
            const scale = 2048 / longestSide;
            const scaled = await source.createPixelMap({
              editable: editable,
              desiredPixelFormat: image.PixelMapFormat.RGBA_8888,
              desiredSize: {
                width: Math.max(1, Math.round(width * scale)),
                height: Math.max(1, Math.round(height * scale))
              }
            });
            if (token?.isCancelled()) {
              scaled.release();
              throw new Error(localizedString($r('app.string.reader_request_cancelled')));
            }
            return scaled;
          }
          const pixelMap = await source.createPixelMap({
            editable: editable,
            desiredPixelFormat: image.PixelMapFormat.RGBA_8888
          });
          if (token?.isCancelled()) {
            pixelMap.release();
            throw new Error(localizedString($r('app.string.reader_request_cancelled')));
          }
          return pixelMap;
        } catch (error) {
          const reason = error as BusinessError;
          throw new Error(localizedString($r('app.string.page_decode_failed'), width, height,
            reason.code || 0, reason.message || localizedString($r('app.string.unknown_error'))));
        }
      } finally {
        source.release();
      }
    } finally {
      if (bytes !== undefined) readerPageByteCache.release(cacheKey, bytes);
    }
  }

  async prefetchPages(namespace: string, bookId: string, pages: number[]): Promise<void> {
    if (isLocalBook(bookId)) return;
    const uniquePages = pages.filter((page: number, index: number, values: number[]): boolean =>
      page >= 0 && values.indexOf(page) === index);
    await Promise.all(uniquePages.map(async (page: number): Promise<void> => {
      const key = readerPageByteCacheKey(komgaClient.getImageCacheScope(), namespace, bookId, page);
      await readerPrefetchGate.enter();
      try {
        const bytes = await readerPageByteCache.acquire(key, () => komgaClient.getBookPage(bookId, page));
        readerPageByteCache.release(key, bytes);
      } catch (_) {
      } finally {
        readerPrefetchGate.leave();
      }
    }));
  }

  updateProgress(namespace: string, bookId: string, page: number, completed: boolean): Promise<void> {
    const key = `${namespace}\n${bookId}`;
    let writer = this.pageProgressWriters.get(key);
    if (writer === undefined) {
      writer = new LatestAsyncTask<PageProgressUpdate>((value: PageProgressUpdate) => this.sendPageProgress(value));
      this.pageProgressWriters.set(key, writer);
    }
    writer.submit({ namespace: namespace, bookId: bookId, page: page, completed: completed });
    const completion = writer.flush();
    completion.then(() => {
      if (writer?.isIdle() && this.pageProgressWriters.get(key) === writer) this.pageProgressWriters.delete(key);
    });
    return completion;
  }

  private async sendPageProgress(update: PageProgressUpdate): Promise<void> {
    return progressSyncCoordinator.run(progressSyncKey(update.namespace, update.bookId),
      () => this.sendPageProgressSerialized(update));
  }

  private async sendPageProgressSerialized(update: PageProgressUpdate): Promise<void> {
    if (isLocalBook(update.bookId)) {
      const book = await localLibraryRepository.book(update.bookId);
      if (book === undefined) return;
      await localLibraryRepository.saveProgress({ bookId: update.bookId,
        page: localProgressPage(update.page, book.pageCount, update.completed),
        completed: update.completed, readDate: new Date().toISOString(), locatorJson: '' });
      localLibraryService.changed();
      return;
    }
    try {
      await komgaClient.updateReadProgress(update.bookId, update.page, update.completed);
      await progressOutbox.discard(update.namespace, update.bookId);
    } catch (_) {
      const record: ProgressOutboxRecord = {
        namespace: update.namespace,
        bookId: update.bookId,
        operation: ProgressOutboxOperation.UPDATE,
        page: update.page,
        completed: update.completed,
        readDate: new Date().toISOString(),
        attempts: 0
      };
      await progressOutbox.enqueue(record);
      await this.logQueuedProgress(update.namespace);
    }
  }

  async clearProgress(namespace: string, bookId: string): Promise<void> {
    if (isLocalBook(bookId)) {
      const key = `${namespace}\n${bookId}`;
      await this.pageProgressWriters.get(key)?.flush();
      await this.epubProgressWriters.get(key)?.flush();
      await progressSyncCoordinator.run(progressSyncKey(namespace, bookId),
        () => localLibraryRepository.clearProgress(bookId));
      localLibraryService.changed();
      return;
    }
    await progressSyncCoordinator.run(progressSyncKey(namespace, bookId), async (): Promise<void> => {
      try {
        await komgaClient.deleteReadProgress(bookId);
        await progressOutbox.discard(namespace, bookId);
      } catch (_) {
        await progressOutbox.enqueue({
          namespace: namespace,
          bookId: bookId,
          operation: ProgressOutboxOperation.DELETE,
          page: 0,
          completed: false,
          readDate: new Date().toISOString(),
          attempts: 0
        });
        await this.logQueuedProgress(namespace);
      }
    });
    try {
      await Promise.all([
        AppPreferences.putBoolean(`epub.${bookId}.saved`, false),
        AppPreferences.putString(`epub.${bookId}.resource`, '0'),
        AppPreferences.putString(`epub.${bookId}.domPos`, ''),
        AppPreferences.putString(`epub.${bookId}.readDate`, '')
      ]);
    } catch (_) {
    }
  }

  updateEpubProgress(namespace: string, bookId: string, page: number,
    locator: ReadiumLocator): Promise<void> {
    const key = `${namespace}\n${bookId}`;
    let writer = this.epubProgressWriters.get(key);
    if (writer === undefined) {
      writer = new LatestAsyncTask<EpubProgressUpdate>((value: EpubProgressUpdate) => this.sendEpubProgress(value));
      this.epubProgressWriters.set(key, writer);
    }
    writer.submit({ namespace: namespace, bookId: bookId, page: page, locator: locator });
    const completion = writer.flush();
    completion.then(() => {
      if (writer?.isIdle() && this.epubProgressWriters.get(key) === writer) this.epubProgressWriters.delete(key);
    });
    return completion;
  }

  private async sendEpubProgress(update: EpubProgressUpdate): Promise<void> {
    return progressSyncCoordinator.run(progressSyncKey(update.namespace, update.bookId),
      () => this.sendEpubProgressSerialized(update));
  }

  private async sendEpubProgressSerialized(update: EpubProgressUpdate): Promise<void> {
    if (isLocalBook(update.bookId)) {
      await localLibraryRepository.saveProgress({ bookId: update.bookId, page: update.page,
        completed: (update.locator.locations?.totalProgression || 0) >= 1, readDate: new Date().toISOString(),
        locatorJson: JSON.stringify(update.locator) });
      localLibraryService.changed();
      return;
    }
    try {
      await komgaClient.updateReadiumProgression(update.bookId, update.locator);
      await progressOutbox.discard(update.namespace, update.bookId);
    } catch (_) {
      await progressOutbox.enqueue({
        namespace: update.namespace,
        bookId: update.bookId,
        operation: ProgressOutboxOperation.UPDATE,
        page: update.page,
        completed: false,
        readDate: new Date().toISOString(),
        attempts: 0,
        locatorJson: JSON.stringify(update.locator)
      });
      await this.logQueuedProgress(update.namespace);
    }
  }

  private async logQueuedProgress(namespace: string): Promise<void> {
    try {
      await offlineLogRepository.append(namespace, OfflineLogLevel.INFO,
        localizedString($r('app.string.offline_log_progress_queued')));
    } catch (_) {
    }
  }
}

export const readerEngine = new ReaderEngine();
