import { LocalBookInspection, LocalBookRecord, LocalLibraryRecord, LocalScanResult, LocalSourceFile,
  localBookId, localBookNumber, localStableId, naturalNameCompare } from '../../model/LocalContentModels';

export interface LocalIndexStorage {
  books(libraryId: string): Promise<LocalBookRecord[]>;
  // Atomically replaces the index and deletes progress before removed book rows. Never deletes source files.
  replaceIndex(library: LocalLibraryRecord, books: LocalBookRecord[]): Promise<void>;
}

export interface LocalSourceReader {
  list(library: LocalLibraryRecord): Promise<LocalSourceFile[]>;
  inspect(file: LocalSourceFile): Promise<LocalBookInspection>;
}

export class LocalLibraryIndexer {
  private active: Map<string, Promise<LocalScanResult>> = new Map();
  private storage: LocalIndexStorage;
  private source: LocalSourceReader;

  constructor(storage: LocalIndexStorage, source: LocalSourceReader) {
    this.storage = storage;
    this.source = source;
  }

  scan(library: LocalLibraryRecord): Promise<LocalScanResult> {
    const current = this.active.get(library.id);
    if (current !== undefined) return current;
    const task = this.scanOnce(library);
    this.active.set(library.id, task);
    task.then(() => this.active.delete(library.id), () => this.active.delete(library.id));
    return task;
  }

  private async scanOnce(library: LocalLibraryRecord): Promise<LocalScanResult> {
    // Do not interpret an unreadable directory as an empty directory. Listing must complete before pruning.
    const files = (await this.source.list(library)).filter((file: LocalSourceFile): boolean =>
      !library.excludedPaths.includes(file.relativePath));
    files.sort((a: LocalSourceFile, b: LocalSourceFile): number => naturalNameCompare(a.relativePath, b.relativePath));
    const previous = await this.storage.books(library.id);
    const books: LocalBookRecord[] = [];
    const seen = new Set<string>();
    let imported = 0;
    let failed = 0;
    const now = Date.now();
    for (let index = 0; index < files.length; index++) {
      const file = files[index];
      const id = localBookId(library.id, file.relativePath);
      if (seen.has(id)) throw new Error('LOCAL_DUPLICATE_IDENTITY');
      seen.add(id);
      const old = previous.find((book: LocalBookRecord): boolean => book.id === id);
      if (old !== undefined && old.relativePath !== file.relativePath) throw new Error('LOCAL_IDENTITY_COLLISION');
      const number = localBookNumber(file.displayName, index + 1);
      if (old !== undefined && old.sizeBytes === file.sizeBytes && old.modifiedAt === file.modifiedAt) {
        const retained = JSON.parse(JSON.stringify(old)) as LocalBookRecord;
        retained.number = number;
        retained.sourceUri = file.sourceUri;
        books.push(retained);
        continue;
      }
      try {
        const media = await this.source.inspect(file);
        if (media.pageCount < 1) throw new Error('LOCAL_EMPTY_PUBLICATION');
        const slash = file.relativePath.lastIndexOf('/');
        const seriesPath = slash < 0 ? '' : file.relativePath.substring(0, slash);
        books.push({
          id: id, libraryId: library.id, seriesId: 'local-series-' + localStableId(`${library.id}/${seriesPath}`),
          seriesTitle: seriesPath.split('/').pop() || library.name, title: file.displayName.replace(/\.[^.]+$/, ''),
          relativePath: file.relativePath, sourceUri: file.sourceUri, number: number,
          sizeBytes: file.sizeBytes, modifiedAt: Math.max(0, file.modifiedAt), importedAt: old?.importedAt || now,
          mediaType: media.mediaType, pageCount: media.pageCount, pageEntries: media.pageEntries
        });
        imported++;
      } catch (_) {
        // A damaged/temporarily unavailable file must not erase its previous metadata or reading progress.
        if (old !== undefined) books.push(old);
        failed++;
      }
    }
    const kept = new Set(books.map((book: LocalBookRecord): string => book.id));
    const removed = previous.filter((book: LocalBookRecord): boolean => !kept.has(book.id)).length;
    const updated = JSON.parse(JSON.stringify(library)) as LocalLibraryRecord;
    updated.lastScannedAt = now;
    await this.storage.replaceIndex(updated, books);
    return { imported: imported, removed: removed, failed: failed };
  }
}
