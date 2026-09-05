import { describe, it, expect } from '@ohos/hypium';
import { AvailableBook, AvailableBookSource, LocalBookInspection, LocalBookRecord, LocalBookSort,
  LocalLibraryKind, LocalLibraryRecord, LocalSourceFile, LOCAL_NAMESPACE, availableBookPage,
  localBookNumber, localBookToKomga, localProgressPage, localStableId, naturalNameCompare } from '../../../main/ets/model/LocalContentModels';
import { LocalIndexStorage, LocalLibraryIndexer, LocalSourceReader } from '../../../main/ets/domain/service/LocalLibraryIndexer';
import { LocalZipEntry, comicArchivePages, localCrc32, parseZipDirectory, safeArchivePath,
  zipDirectoryLocation } from '../../../main/ets/domain/service/LocalArchiveIndex';
import { mergedSearchPage, mergeHomeWithAvailable, localSeriesFromBooks } from '../../../main/ets/model/UnifiedContentModels';
import { emptyHomeFeed, KomgaBook } from '../../../main/ets/model/KomgaModels';
import { TaskPhase } from '../../../main/ets/core/model/UiState';
import { acceptsDownloadProgress, downloadSpeed } from '../../../main/ets/model/DownloadProgressModels';
import { calculateEpubViewport, normalizedEpubTopSpacing } from '../../../main/ets/domain/service/EpubReaderLayout';

function centralEntry(name: string, size: number = 12): ArrayBuffer {
  const bytes = new Uint8Array(46 + name.length);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, 0x02014b50, true); view.setUint16(10, 8, true);
  view.setUint32(20, 10, true); view.setUint32(24, size, true); view.setUint16(28, name.length, true);
  for (let index = 0; index < name.length; index++) bytes[46 + index] = name.charCodeAt(index);
  return bytes.buffer as ArrayBuffer;
}

function archiveRejected(bytes: ArrayBuffer): boolean {
  try { parseZipDirectory(bytes, 1, 100); return false; } catch (_) { return true; }
}

class MemoryIndex implements LocalIndexStorage {
  values: LocalBookRecord[] = [];
  writes: number = 0;
  books(_libraryId: string): Promise<LocalBookRecord[]> { return Promise.resolve(this.values); }
  replaceIndex(_library: LocalLibraryRecord, books: LocalBookRecord[]): Promise<void> {
    this.values = books;
    this.writes++;
    return Promise.resolve();
  }
}

class FixtureSource implements LocalSourceReader {
  files: LocalSourceFile[] = [{ relativePath: 'series/vol 2.cbz', sourceUri: 'fixture://book-2',
    displayName: 'vol 2.cbz', sizeBytes: 100, modifiedAt: 1000 }];
  listingFails: boolean = false;
  inspectionFails: boolean = false;
  inspections: number = 0;
  async list(_library: LocalLibraryRecord): Promise<LocalSourceFile[]> {
    if (this.listingFails) throw new Error('PERMISSION_REVOKED');
    return this.files;
  }
  async inspect(_file: LocalSourceFile): Promise<LocalBookInspection> {
    this.inspections++;
    if (this.inspectionFails) throw new Error('INVALID_ARCHIVE');
    return { mediaType: 'application/zip', pageCount: 2, pageEntries: ['1.jpg', '2.jpg'] };
  }
}

function fixtureLibrary(): LocalLibraryRecord {
  return { id: 'local-library-fixture', rootUri: 'fixture://folder', name: 'Fixture',
    kind: LocalLibraryKind.DIRECTORY, managedCopy: false, scanOnStartup: true, scanIntervalHours: 1,
    lastScannedAt: 0, excludedPaths: [] };
}

export default function localContentTest(): void {
  describe('LocalContent', () => {
    it('stores local reading progress one-based and clamps completion to page count', 0, () => {
      expect(localProgressPage(0, 10, false)).assertEqual(1);
      expect(localProgressPage(4, 10, false)).assertEqual(5);
      expect(localProgressPage(9, 10, true)).assertEqual(10);
      expect(localProgressPage(99, 10, false)).assertEqual(10);
    });
    it('rejects traversal and absolute archive paths', 0, () => {
      expect(safeArchivePath('../page.jpg')).assertFalse();
      expect(safeArchivePath('folder/../page.jpg')).assertFalse();
      expect(safeArchivePath('/page.jpg')).assertFalse();
      expect(safeArchivePath('C:/page.jpg')).assertFalse();
      expect(safeArchivePath('folder\\page.jpg')).assertFalse();
      expect(safeArchivePath('folder/page.jpg')).assertTrue();
    });
    it('parses central records and rejects oversized or encrypted entries', 0, () => {
      const valid = centralEntry('page.jpg');
      expect(parseZipDirectory(valid, 1, 100)[0].name).assertEqual('page.jpg');
      expect(archiveRejected(centralEntry('../p.jpg'))).assertTrue();
      expect(archiveRejected(centralEntry('page.jpg', 33 * 1024 * 1024))).assertTrue();
      new DataView(valid).setUint16(8, 1, true);
      expect(archiveRejected(valid)).assertTrue();
      expect(archiveRejected(new ArrayBuffer(10))).assertTrue();
    });
    it('locates bounded ZIP directories and rejects split archives', 0, () => {
      const bytes = new ArrayBuffer(22); const view = new DataView(bytes);
      view.setUint32(0, 0x06054b50, true); view.setUint16(8, 1, true); view.setUint16(10, 1, true);
      view.setUint32(12, 50, true); view.setUint32(16, 100, true);
      expect(zipDirectoryLocation(bytes, 172).offset).assertEqual(100);
      view.setUint16(4, 1, true);
      let rejected = false;
      try { zipDirectoryLocation(bytes, 172); } catch (_) { rejected = true; }
      expect(rejected).assertTrue();
    });
    it('verifies CRC and ignores archive metadata while ordering comic pages', 0, () => {
      expect(localCrc32(new Uint8Array([49, 50, 51, 52, 53, 54, 55, 56, 57]).buffer as ArrayBuffer))
        .assertEqual(0xcbf43926);
      const names = ['10.jpg', '__MACOSX/1.jpg', '2.jpg', '.hidden.jpg', 'text.txt'];
      const entries = names.map((name: string): LocalZipEntry =>
        ({ name: name, method: 0, compressedSize: 1, size: 1, offset: 0, crc: 0 }));
      expect(comicArchivePages(entries).join(',')).assertEqual('2.jpg,10.jpg');
    });
    it('merges ordered search prefixes with deduplication and stable page boundaries', 0, () => {
      const first = mergedSearchPage([1, 3], 4, [2, 3, 6], 0, 2, value => `${value}`, (a, b) => a - b);
      const second = mergedSearchPage([1, 3, 4, 5], 4, [2, 3, 6], 1, 2, value => `${value}`, (a, b) => a - b);
      expect(first.content.join(',')).assertEqual('1,2');
      expect(second.content.join(',')).assertEqual('3,4');
      expect(second.totalElements).assertEqual(6); expect(second.totalPages).assertEqual(3);
      expect(mergedSearchPage([], 0, [1, 2], 99, 1, value => `${value}`, (a, b) => a - b).number).assertEqual(1);
    });
    it('builds local home progress groups and selects the next unread volume', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      await new LocalLibraryIndexer(storage, source).scan(fixtureLibrary());
      const first = localBookToKomga(storage.values[0], { bookId: storage.values[0].id, page: 2,
        completed: true, readDate: '2026-01-01', locatorJson: '' });
      const next = JSON.parse(JSON.stringify(first)) as KomgaBook;
      next.id = 'local-book-next'; next.number = 3; next.readProgress = undefined;
      const values: AvailableBook[] = [first, next].map((book: KomgaBook): AvailableBook =>
        ({ book: book, namespace: LOCAL_NAMESPACE, source: AvailableBookSource.LOCAL }));
      const feed = mergeHomeWithAvailable(emptyHomeFeed(), values);
      expect(feed.onDeck[0].id).assertEqual(next.id); expect(feed.recentlyRead.length).assertEqual(1);
      expect(feed.latestBooks.length).assertEqual(2); expect(feed.recentlyAddedSeries.length).assertEqual(1);
      expect(localSeriesFromBooks([first, next])[0].booksReadCount).assertEqual(1);
    });
    it('does not accept late byte progress for paused cancelled or completed tasks', 0, () => {
      expect(acceptsDownloadProgress(TaskPhase.RUNNING)).assertTrue();
      [TaskPhase.PAUSED, TaskPhase.CANCELLED, TaskPhase.COMPLETED, TaskPhase.FAILED].forEach(phase => {
        expect(acceptsDownloadProgress(phase)).assertFalse();
      });
      expect(acceptsDownloadProgress(undefined)).assertFalse();
      expect(downloadSpeed(100, 600, 500)).assertEqual(1000);
      expect(downloadSpeed(600, 100, 500)).assertEqual(0);
      expect(downloadSpeed(0, 100, 0)).assertEqual(0);
    });
    it('applies default EPUB spacing and subtracts it once from the reader viewport', 0, () => {
      expect(normalizedEpubTopSpacing(-1)).assertEqual(8);
      expect(normalizedEpubTopSpacing(0)).assertEqual(0);
      const viewport = calculateEpubViewport(400, 800, 16, 2, 1, 800, 1600, 8);
      expect(viewport.widthPixels).assertEqual(736); expect(viewport.heightPixels).assertEqual(1584);
    });
    it('matches upstream FNV identities', 0, () => {
      expect(localStableId('')).assertEqual('cbf29ce484222325');
      expect(localStableId('hello')).assertEqual('a430d84680aabd0b');
      expect(localStableId('a')).assertEqual('af63dc4c8601ec8c');
    });
    it('orders numbered files naturally and extracts labelled volume numbers', 0, () => {
      expect(naturalNameCompare('page 2.jpg', 'page 10.jpg') < 0).assertTrue();
      expect(localBookNumber('Title vol 12 (2024).cbz', 1)).assertEqual(12);
    });
    it('keeps identities and skips unchanged files on refresh', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      await indexer.scan(fixtureLibrary());
      const first = storage.values[0];
      await indexer.scan(fixtureLibrary());
      expect(source.inspections).assertEqual(1);
      expect(storage.values[0].id).assertEqual(first.id);
      expect(storage.values[0].importedAt).assertEqual(first.importedAt);
    });
    it('does not prune an index after a permission or listing failure', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      await indexer.scan(fixtureLibrary()); source.listingFails = true;
      let failed = false;
      try { await indexer.scan(fixtureLibrary()); } catch (_) { failed = true; }
      expect(failed).assertTrue(); expect(storage.values.length).assertEqual(1); expect(storage.writes).assertEqual(1);
    });
    it('retains an existing damaged book while reporting the inspection failure', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      await indexer.scan(fixtureLibrary()); source.files[0].sizeBytes = 200; source.inspectionFails = true;
      const result = await indexer.scan(fixtureLibrary());
      expect(result.failed).assertEqual(1); expect(result.removed).assertEqual(0);
      expect(storage.values[0].sizeBytes).assertEqual(100);
    });
    it('removes externally deleted files only after a complete listing', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      await indexer.scan(fixtureLibrary()); source.files = [];
      expect((await indexer.scan(fixtureLibrary())).removed).assertEqual(1);
      expect(storage.values.length).assertEqual(0);
    });
    it('coalesces overlapping scans', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      await Promise.all([indexer.scan(fixtureLibrary()), indexer.scan(fixtureLibrary())]);
      expect(source.inspections).assertEqual(1); expect(storage.writes).assertEqual(1);
    });
    it('excludes a source path and restores it on the next scan', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      const indexer = new LocalLibraryIndexer(storage, source);
      const library = fixtureLibrary(); library.excludedPaths = ['series/vol 2.cbz'];
      await indexer.scan(library); expect(storage.values.length).assertEqual(0);
      library.excludedPaths = []; await indexer.scan(library); expect(storage.values.length).assertEqual(1);
    });
    it('filters local/downloaded sources and deduplicates by namespace, not bare book ID', 0, async () => {
      const storage = new MemoryIndex(); const source = new FixtureSource();
      await new LocalLibraryIndexer(storage, source).scan(fixtureLibrary());
      const local: AvailableBook = { book: localBookToKomga(storage.values[0]), namespace: LOCAL_NAMESPACE,
        source: AvailableBookSource.LOCAL };
      const downloaded: AvailableBook = { book: local.book, namespace: 'server:user', source: AvailableBookSource.DOWNLOADED };
      const values = [local, local, downloaded];
      expect(availableBookPage(values, AvailableBookSource.ALL, '', LocalBookSort.TITLE).totalElements).assertEqual(2);
      expect(availableBookPage(values, AvailableBookSource.LOCAL, 'vol 2', LocalBookSort.TITLE).totalElements).assertEqual(1);
      expect(availableBookPage(values, AvailableBookSource.DOWNLOADED, 'absent', LocalBookSort.TITLE).totalElements).assertEqual(0);
      expect(availableBookPage(values, AvailableBookSource.ALL, '', LocalBookSort.TITLE, 99, 1).page).assertEqual(1);
    });
  });
}
