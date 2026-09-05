import { LocalBookRecord, LocalLibraryRecord, LocalScanResult, localBookToKomga } from '../../model/LocalContentModels';
import { KomgaBook, KomgaBookPage } from '../../model/KomgaModels';
import { localLibraryRepository } from '../../data/repository/LocalLibraryRepository';
import { LocalLibraryIndexer } from './LocalLibraryIndexer';
import { localFilePlatform } from './LocalFilePlatform';
import { SerialTaskGate } from './ReaderRequestToken';

export class LocalLibraryService {
  indexRevision: number = 0;
  private indexer = new LocalLibraryIndexer(localLibraryRepository, localFilePlatform);
  private gate: SerialTaskGate = new SerialTaskGate();
  private listeners: Array<() => void> = [];

  observe(listener: () => void): () => void {
    this.listeners.push(listener);
    return (): void => { this.listeners = this.listeners.filter(value => value !== listener); };
  }

  changed(): void { this.listeners.forEach(listener => listener()); }

  private indexChanged(): void { this.indexRevision++; this.changed(); }

  async addSelected(folder: boolean): Promise<LocalScanResult> {
    const selected = await localFilePlatform.select(folder);
    await this.gate.enter();
    try {
      const total: LocalScanResult = { imported: 0, removed: 0, failed: 0 };
      const existing = await localLibraryRepository.libraries();
      for (let index = 0; index < selected.length; index++) {
        const library = existing.find(value => value.id === selected[index].id) || selected[index];
        await localLibraryRepository.saveLibrary(library);
        const result = await this.indexer.scan(library);
        total.imported += result.imported; total.removed += result.removed; total.failed += result.failed;
      }
      return total;
    } finally { this.gate.leave(); this.indexChanged(); }
  }

  async scanAll(dueOnly: boolean = false, startup: boolean = false): Promise<LocalScanResult> {
    await this.gate.enter();
    let scanned = false;
    const total: LocalScanResult = { imported: 0, removed: 0, failed: 0 };
    try {
      const libraries = await localLibraryRepository.libraries();
      for (let index = 0; index < libraries.length; index++) {
        const library = libraries[index];
        if (startup && !library.scanOnStartup) continue;
        if (dueOnly && (library.scanIntervalHours <= 0 ||
          Date.now() - library.lastScannedAt < library.scanIntervalHours * 3600000)) continue;
        scanned = true;
        try {
          const result = await this.indexer.scan(library);
          total.imported += result.imported; total.removed += result.removed; total.failed += result.failed;
        } catch (_) { total.failed++; }
      }
      return total;
    } finally { this.gate.leave(); if (scanned) this.indexChanged(); }
  }

  async update(library: LocalLibraryRecord): Promise<void> {
    await this.gate.enter();
    try {
      const current = await this.library(library.id);
      current.name = library.name;
      current.scanOnStartup = library.scanOnStartup;
      current.scanIntervalHours = library.scanIntervalHours;
      await localLibraryRepository.saveLibrary(current);
    }
    finally { this.gate.leave(); this.changed(); }
  }

  async remove(library: LocalLibraryRecord): Promise<void> {
    await this.gate.enter();
    try {
      await localLibraryRepository.removeLibrary(library.id);
      await localFilePlatform.release(library);
    } finally { this.gate.leave(); this.indexChanged(); }
  }

  async exclude(bookId: string): Promise<void> {
    await this.gate.enter();
    try {
      const book = await this.requireBook(bookId);
      const library = await this.library(book.libraryId);
      if (!library.excludedPaths.includes(book.relativePath)) library.excludedPaths.push(book.relativePath);
      // Atomic index replacement also persists the exclusion, with progress-before-book cleanup.
      const retained = (await localLibraryRepository.books(library.id)).filter(value => value.id !== bookId);
      await localLibraryRepository.replaceIndex(library, retained);
    } finally { this.gate.leave(); this.indexChanged(); }
  }

  async restoreExcluded(libraryId: string, relativePath: string): Promise<void> {
    await this.gate.enter();
    try {
      const library = await this.library(libraryId);
      library.excludedPaths = library.excludedPaths.filter(value => value !== relativePath);
      await localLibraryRepository.saveLibrary(library);
      await this.indexer.scan(library);
    } finally { this.gate.leave(); this.indexChanged(); }
  }

  private async library(id: string): Promise<LocalLibraryRecord> {
    const library = (await localLibraryRepository.libraries()).find(value => value.id === id);
    if (library === undefined) throw new Error('LOCAL_LIBRARY_MISSING');
    return library;
  }

  async requireBook(bookId: string): Promise<LocalBookRecord> {
    const book = await localLibraryRepository.book(bookId);
    if (book === undefined) throw new Error('LOCAL_BOOK_MISSING');
    return book;
  }

  async readableBook(bookId: string): Promise<LocalBookRecord> {
    const book = await this.requireBook(bookId);
    await localFilePlatform.activate(await this.library(book.libraryId));
    return book;
  }

  async book(bookId: string): Promise<KomgaBook> {
    return localBookToKomga(await this.requireBook(bookId), await localLibraryRepository.progress(bookId));
  }

  async pages(bookId: string): Promise<KomgaBookPage[]> {
    const book = await this.readableBook(bookId);
    const pages: KomgaBookPage[] = [];
    for (let index = 0; index < book.pageCount; index++) {
      pages.push({ number: index, fileName: book.pageEntries[index] || `${index + 1}`, mediaType: book.mediaType });
    }
    return pages;
  }
}

export const localLibraryService = new LocalLibraryService();
