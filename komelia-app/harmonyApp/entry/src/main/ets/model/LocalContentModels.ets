import { KomgaBook, ReadProgress } from './KomgaModels';

export const LOCAL_NAMESPACE = 'local-device:root';
export const LOCAL_BOOK_PREFIX = 'local-book-';

export enum LocalLibraryKind { DIRECTORY = 'DIRECTORY', FILE = 'FILE' }
export enum AvailableBookSource { ALL = 'ALL', LOCAL = 'LOCAL', DOWNLOADED = 'DOWNLOADED' }
export enum LocalBookSort { RECENTLY_ADDED = 'RECENTLY_ADDED', FILE_MODIFIED = 'FILE_MODIFIED', TITLE = 'TITLE' }

export interface LocalLibraryRecord {
  id: string;
  name: string;
  rootUri: string;
  kind: LocalLibraryKind;
  managedCopy: boolean;
  fileName?: string;
  scanOnStartup: boolean;
  scanIntervalHours: number;
  lastScannedAt: number;
  excludedPaths: string[];
}

export interface LocalSourceFile {
  relativePath: string;
  sourceUri: string;
  displayName: string;
  sizeBytes: number;
  modifiedAt: number;
}

export interface LocalBookRecord {
  id: string;
  libraryId: string;
  seriesId: string;
  seriesTitle: string;
  relativePath: string;
  sourceUri: string;
  title: string;
  number: number;
  sizeBytes: number;
  modifiedAt: number;
  importedAt: number;
  mediaType: string;
  pageCount: number;
  pageEntries: string[];
}

export interface LocalBookInspection { mediaType: string; pageCount: number; pageEntries: string[]; }
export interface LocalReadingProgress { bookId: string; page: number; completed: boolean; readDate: string; locatorJson: string; }
export interface LocalScanResult { imported: number; removed: number; failed: number; }
export interface AvailableBook { book: KomgaBook; namespace: string; source: AvailableBookSource; }
export interface AvailableBookPage { content: AvailableBook[]; page: number; totalPages: number; totalElements: number; }

export function isLocalBook(bookId: string): boolean { return bookId.startsWith(LOCAL_BOOK_PREFIX); }

export function localProgressPage(pageIndex: number, pageCount: number, completed: boolean): number {
  return completed ? Math.max(1, pageCount) : Math.max(1, Math.min(pageCount, Math.floor(pageIndex) + 1));
}

// FNV-1a 64, using two 32-bit halves so ArkTS and the Kotlin index share stable UTF-8 identities.
export function localStableId(value: string): string {
  const encoded = encodeURIComponent(value);
  let high = 0xcbf29ce4;
  let low = 0x84222325;
  for (let index = 0; index < encoded.length; index++) {
    let byte = encoded.charCodeAt(index);
    if (encoded[index] === '%') {
      byte = parseInt(encoded.substring(index + 1, index + 3), 16);
      index += 2;
    }
    low = (low ^ byte) >>> 0;
    const product = low * 0x1b3;
    high = (Math.imul(high, 0x1b3) + Math.imul(low, 0x100) + Math.floor(product / 0x100000000)) >>> 0;
    low = product >>> 0;
  }
  return high.toString(16) + low.toString(16).padStart(8, '0');
}

export function localBookId(libraryId: string, relativePath: string): string {
  return LOCAL_BOOK_PREFIX + localStableId(`${libraryId}/${relativePath}`);
}

export function localFileSupported(name: string): boolean {
  return /\.(cbz|zip|epub|pdf|jpe?g|png|webp|gif|bmp)$/i.test(name);
}

export function localImageFile(name: string): boolean { return /\.(jpe?g|png|webp|gif|bmp)$/i.test(name); }

export function naturalNameCompare(left: string, right: string): number {
  const a = left.toLowerCase().match(/\d+|\D+/g) || [];
  const b = right.toLowerCase().match(/\d+|\D+/g) || [];
  for (let index = 0; index < Math.min(a.length, b.length); index++) {
    const numeric = /^\d+$/.test(a[index]) && /^\d+$/.test(b[index]);
    const delta = numeric ? Number(a[index]) - Number(b[index]) : a[index].localeCompare(b[index]);
    if (delta !== 0) return delta;
  }
  return a.length - b.length || left.localeCompare(right);
}

export function localBookNumber(name: string, fallback: number): number {
  const stem = name.replace(/\.[^.]+$/, '');
  const labelled = stem.match(/(?:chapter|chap|ch|episode|ep|issue|book|volume|vol|v|#)\s*[._ -]*[0-9]+/ig);
  const numbers = (labelled && labelled.length > 0 ? labelled[labelled.length - 1] : stem).match(/\d+/g);
  return numbers && numbers.length > 0 ? Number(numbers[numbers.length - 1]) : fallback;
}

export function localBookToKomga(record: LocalBookRecord, progress?: LocalReadingProgress): KomgaBook {
  let readProgress: ReadProgress | undefined = undefined;
  if (progress !== undefined) {
    readProgress = { page: progress.page, completed: progress.completed, readDate: progress.readDate };
  }
  return {
    id: record.id, libraryId: record.libraryId, seriesId: record.seriesId, seriesTitle: record.seriesTitle,
    name: record.title, number: record.number, sizeBytes: record.sizeBytes, size: `${record.sizeBytes} B`,
    created: new Date(record.modifiedAt).toISOString(), lastModified: new Date(record.importedAt).toISOString(),
    fileLastModified: new Date(record.modifiedAt).toISOString(), url: `local://${record.relativePath}`,
    media: { status: 'READY', mediaType: record.mediaType, pagesCount: record.pageCount, comment: '',
      epubDivinaCompatible: false, epubIsKepub: false },
    metadata: { title: record.title, summary: '', number: `${record.number}`, numberSort: record.number,
      authors: [], tags: [], isbn: '' },
    readProgress: readProgress, oneshot: false
  };
}

export function availableBookKey(value: AvailableBook): string { return `${value.namespace}\n${value.book.id}`; }

export function availableBookPage(values: AvailableBook[], source: AvailableBookSource, query: string,
  sort: LocalBookSort, requestedPage: number = 0, pageSize: number = 20): AvailableBookPage {
  const text = query.trim().toLowerCase();
  const seen = new Set<string>();
  const filtered = values.filter((value: AvailableBook): boolean => {
    const key = availableBookKey(value);
    if (seen.has(key)) return false;
    seen.add(key);
    return (source === AvailableBookSource.ALL || value.source === source) &&
      (text.length === 0 || `${value.book.metadata.title}\n${value.book.seriesTitle}`.toLowerCase().includes(text));
  });
  filtered.sort((a: AvailableBook, b: AvailableBook): number => {
    if (sort === LocalBookSort.TITLE) return naturalNameCompare(a.book.metadata.title, b.book.metadata.title) ||
      availableBookKey(a).localeCompare(availableBookKey(b));
    const left = sort === LocalBookSort.FILE_MODIFIED ? a.book.fileLastModified || a.book.created : a.book.lastModified;
    const right = sort === LocalBookSort.FILE_MODIFIED ? b.book.fileLastModified || b.book.created : b.book.lastModified;
    return (right || '').localeCompare(left || '') || availableBookKey(a).localeCompare(availableBookKey(b));
  });
  const size = Math.max(1, Math.floor(pageSize));
  const totalPages = Math.max(1, Math.ceil(filtered.length / size));
  const page = Math.max(0, Math.min(Math.floor(requestedPage), totalPages - 1));
  return { content: filtered.slice(page * size, (page + 1) * size), page: page,
    totalPages: totalPages, totalElements: filtered.length };
}
