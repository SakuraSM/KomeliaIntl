import { image } from '@kit.ImageKit';
import { ProgressOutboxRecord } from '../../data/local/DatabaseModels';
import { offlineRepository, progressOutbox } from '../../data/repository/DefaultRepositories';
import { komgaClient } from '../../network/KomgaClient';

export class ReaderEngine {
  async pageSource(namespace: string, bookId: string, page: number): Promise<PixelMap> {
    const local = await offlineRepository.getPage(namespace, bookId, page);
    const source = local === undefined || !local.completed
      ? image.createImageSource(await komgaClient.getBookPage(bookId, page))
      : image.createImageSource(local.localPath);
    if (source === undefined) throw new Error('无法解码阅读页');
    try {
      return await source.createPixelMap({ editable: false });
    } finally {
      source.release();
    }
  }

  async updateProgress(namespace: string, bookId: string, page: number, completed: boolean): Promise<void> {
    try {
      await komgaClient.updateReadProgress(bookId, page, completed);
    } catch (_) {
      const record: ProgressOutboxRecord = {
        namespace: namespace,
        bookId: bookId,
        page: page,
        completed: completed,
        readDate: new Date().toISOString(),
        attempts: 0
      };
      await progressOutbox.enqueue(record);
    }
  }
}

export const readerEngine = new ReaderEngine();
