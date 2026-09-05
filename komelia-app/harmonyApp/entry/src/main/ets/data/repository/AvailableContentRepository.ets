import { HarmonyDatabase } from '../local/HarmonyDatabase';
import { AvailableBook, AvailableBookSource, LOCAL_NAMESPACE, localBookToKomga } from '../../model/LocalContentModels';
import { KomgaBook, KomgaSeries } from '../../model/KomgaModels';
import { SearchCoverage, UnifiedSearchResult, localSeriesFromBooks, mergedSearchPage } from '../../model/UnifiedContentModels';
import { localLibraryRepository } from './LocalLibraryRepository';
import { offlineRepository, progressOutbox } from './DefaultRepositories';
import { ProgressOutboxOperation } from '../local/DatabaseModels';
import { komgaClient } from '../../network/KomgaClient';
import { AppPreferences } from '../../persistence/AppPreferences';

export class AvailableContentRepository {
  private async cache(namespace: string, type: string, id: string, value: Object): Promise<void> {
    if (namespace.length === 0 || namespace === LOCAL_NAMESPACE) return;
    await HarmonyDatabase.replace('CATALOG_ENTITY', { 'NAMESPACE': namespace, 'ENTITY_TYPE': type,
      'ENTITY_ID': id, 'PAYLOAD': JSON.stringify(value), 'UPDATED_AT': Date.now() });
  }
  async books(namespace: string): Promise<AvailableBook[]> {
    const records = await localLibraryRepository.books();
    const progress = await localLibraryRepository.allProgress();
    const books: AvailableBook[] = records.map((record): AvailableBook => ({ book: localBookToKomga(record,
      progress.find(value => value.bookId === record.id)), namespace: LOCAL_NAMESPACE, source: AvailableBookSource.LOCAL }));
    if (namespace.length === 0 || namespace === LOCAL_NAMESPACE) return books;
    const downloaded = (await offlineRepository.listBooks(namespace)).filter(record =>
      record.pageCount > 0 && record.downloadedPages >= record.pageCount);
    const cache = await HarmonyDatabase.query(
      'SELECT ENTITY_ID, PAYLOAD FROM CATALOG_ENTITY WHERE NAMESPACE = ? AND ENTITY_TYPE = ?', [namespace, 'book']);
    const metadata = new Map<string, KomgaBook>();
    try {
      while (cache.goToNextRow()) metadata.set(cache.getString(0), JSON.parse(cache.getString(1)) as KomgaBook);
    } finally { cache.close(); }
    for (let index = 0; index < downloaded.length; index++) {
      const record = downloaded[index];
      const book = metadata.get(record.bookId) || localBookToKomga({
        id: record.bookId, libraryId: '', seriesId: record.bookId, seriesTitle: '', title: record.title,
        relativePath: '', sourceUri: '', number: 1, sizeBytes: record.sizeBytes, modifiedAt: record.updatedAt,
        importedAt: record.updatedAt, mediaType: record.mediaKind === 'EPUB_FILE' ? 'application/epub+zip' : 'image/*',
        pageCount: record.pageCount, pageEntries: []
      });
      const pending = await progressOutbox.pending(namespace, book.id);
      if (pending !== undefined) book.readProgress = pending.operation === ProgressOutboxOperation.DELETE ? undefined :
        { page: pending.page, completed: pending.completed, readDate: pending.readDate };
      books.push({ book: book, namespace: namespace, source: AvailableBookSource.DOWNLOADED });
    }
    return books;
  }

  async searchBooks(namespace: string, online: boolean, query: string, page: number,
    size: number): Promise<UnifiedSearchResult<KomgaBook>> {
    let local: KomgaBook[] = [];
    let localOk = true;
    try {
      local = (await this.books(namespace)).map(value => value.book).filter(book =>
        `${book.metadata.title}\n${book.seriesTitle}`.toLowerCase().includes(query.toLowerCase()));
    } catch (_) { localOk = false; }
    let remote: KomgaBook[] = [];
    let total = 0;
    let remoteOk = false;
    if (online) {
      try {
        if (await AppPreferences.getString('activeNamespace') !== namespace) throw new Error('SEARCH_SESSION_CHANGED');
        const response = await komgaClient.searchBooks(query, 0, (page + 1) * size, 'lastModifiedDate,desc');
        remote = response.content; total = response.totalElements; remoteOk = true;
        for (let index = 0; index < remote.length; index++) {
          const book = remote[index];
          await this.cache(namespace, 'book', book.id, book);
          const pending = await progressOutbox.pending(namespace, book.id);
          if (pending !== undefined) book.readProgress = pending.operation === ProgressOutboxOperation.DELETE ? undefined :
            { page: pending.page, completed: pending.completed, readDate: pending.readDate };
        }
      } catch (_) { }
    }
    if (!localOk && !remoteOk) throw new Error('UNIFIED_SEARCH_UNAVAILABLE');
    return { page: mergedSearchPage(remote, total, local, page, size, value => value.id,
      (a, b) => (b.lastModified || '').localeCompare(a.lastModified || '') || a.id.localeCompare(b.id)),
      coverage: !remoteOk ? SearchCoverage.OFFLINE_ONLY : !localOk ? SearchCoverage.REMOTE_ONLY : SearchCoverage.COMPLETE };
  }

  async searchSeries(namespace: string, online: boolean, query: string, page: number,
    size: number): Promise<UnifiedSearchResult<KomgaSeries>> {
    let local: KomgaSeries[] = [];
    let localOk = true;
    try {
      local = localSeriesFromBooks((await this.books(namespace)).map(value => value.book)
        .filter(book => book.seriesTitle.length > 0))
        .filter(series => series.metadata.title.toLowerCase().includes(query.toLowerCase()));
    } catch (_) { localOk = false; }
    let remote: KomgaSeries[] = [];
    let total = 0;
    let remoteOk = false;
    if (online) {
      try {
        if (await AppPreferences.getString('activeNamespace') !== namespace) throw new Error('SEARCH_SESSION_CHANGED');
        const response = await komgaClient.searchSeries(query, 0, (page + 1) * size, { sort: 'lastModifiedDate,desc' });
        remote = response.content; total = response.totalElements; remoteOk = true;
        for (let index = 0; index < remote.length; index++) {
          await this.cache(namespace, 'series', remote[index].id, remote[index]);
        }
      } catch (_) { }
    }
    if (!localOk && !remoteOk) throw new Error('UNIFIED_SEARCH_UNAVAILABLE');
    return { page: mergedSearchPage(remote, total, local, page, size, value => value.id,
      (a, b) => (b.lastModified || '').localeCompare(a.lastModified || '') || a.id.localeCompare(b.id)),
      coverage: !remoteOk ? SearchCoverage.OFFLINE_ONLY : !localOk ? SearchCoverage.REMOTE_ONLY : SearchCoverage.COMPLETE };
  }
}

export const availableContentRepository = new AvailableContentRepository();
